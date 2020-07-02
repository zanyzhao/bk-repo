package com.tencent.bkrepo.common.storage.filesystem

import com.tencent.bkrepo.common.storage.filesystem.cleanup.CleanupFileVisitor
import com.tencent.bkrepo.common.storage.filesystem.cleanup.CleanupResult
import com.tencent.bkrepo.common.storage.util.createFile
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * 本地文件存储客户端
 *
 * @author: carrypan
 * @date: 2019-09-18
 */
class FileSystemClient(private val root: String) {

    fun touch(dir: String, filename: String): File {
        val filePath = Paths.get(this.root, dir, filename)
        try {
            filePath.createFile()
        } catch (exception: FileAlreadyExistsException) {
            // ignore
        }
        return filePath.toFile()
    }

    fun store(dir: String, filename: String, inputStream: InputStream, size: Long, overwrite: Boolean = false): File {
        val filePath = Paths.get(this.root, dir, filename)
        if (overwrite) {
            Files.deleteIfExists(filePath)
        }
        if (!Files.exists(filePath)) {
            val file = filePath.createFile()
            FileLockExecutor.executeInLock(inputStream) { input ->
                FileLockExecutor.executeInLock(file) { output ->
                    transfer(input, output, size)
                }
            }
        }
        return filePath.toFile()
    }

    fun store(dir: String, filename: String, file: File, overwrite: Boolean = false): File {
        val source = file.toPath()
        val target = Paths.get(this.root, dir, filename)
        if (overwrite) {
            Files.deleteIfExists(target)
        }
        if (!Files.exists(target)) {
            val targetFile = target.createFile()
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
            } catch (ex: IOException) {
                // ignore and let the Files.copy, outside
                // this if block, take over and attempt to copy it
                logger.warn("Failed to store file by Files.move(source, target), fallback to use file channel: ${ex.message}")
                FileLockExecutor.executeInLock(file.inputStream()) { input ->
                    FileLockExecutor.executeInLock(targetFile) { output ->
                        transfer(input, output, file.length())
                    }
                }
            }
        }
        return target.toFile()
    }

    fun delete(dir: String, filename: String) {
        val filePath = Paths.get(this.root, dir, filename)
        if (Files.exists(filePath)) {
            if (Files.isRegularFile(filePath)) {
                FileLockExecutor.executeInLock(filePath.toFile()) {
                    Files.delete(filePath)
                }
            } else {
                throw IllegalArgumentException("[$filePath] is not a regular file.")
            }
        }
    }

    fun load(dir: String, filename: String): File? {
        val filePath = Paths.get(this.root, dir, filename)
        return if (Files.isRegularFile(filePath)) filePath.toFile() else null
    }

    fun exist(dir: String, filename: String): Boolean {
        val filePath = Paths.get(this.root, dir, filename)
        return Files.isRegularFile(filePath)
    }

    fun append(dir: String, filename: String, inputStream: InputStream, size: Long): Long {
        val filePath = Paths.get(this.root, dir, filename)
        if (!Files.isRegularFile(filePath)) {
            throw IllegalArgumentException("[$filePath] is not a regular file.")
        }
        val file = filePath.toFile()
        FileLockExecutor.executeInLock(inputStream) { input ->
            FileLockExecutor.executeInLock(file) { output ->
                transfer(input, output, size, true)
            }
        }
        return Files.size(filePath)
    }

    fun createDirectory(dir: String, name: String) {
        val dirPath = Paths.get(this.root, dir, name)
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath)
        }
    }

    fun deleteDirectory(dir: String, name: String) {
        val filePath = Paths.get(this.root, dir, name)
        if (Files.isDirectory(filePath)) {
            filePath.toFile().deleteRecursively()
        } else {
            throw IllegalArgumentException("[$filePath] is not a directory.")
        }
    }

    fun checkDirectory(dir: String): Boolean {
        return Files.isDirectory(Paths.get(this.root, dir))
    }

    fun listFiles(path: String, extension: String): Collection<File> {
        return FileUtils.listFiles(File(this.root, path), arrayOf(extension.trim('.')), false)
    }

    fun mergeFiles(fileList: List<File>, outputFile: File): File {
        if (!outputFile.exists()) {
            if (!outputFile.createNewFile()) {
                throw IOException("Failed to create file [$outputFile]!")
            }
        }

        FileLockExecutor.executeInLock(outputFile) { output ->
            fileList.forEach { file ->
                FileLockExecutor.executeInLock(file.inputStream()) { input ->
                    transfer(input, output, file.length(), true)
                }
            }
        }
        return outputFile
    }

    /**
     * 遍历文件
     */
    fun walk(visitor: FileVisitor<in Path>) {
        val rootPath = Paths.get(root)
        Files.walkFileTree(rootPath, visitor)
    }

    /**
     * 清理文件
     */
    fun cleanUp(expireDays: Int): CleanupResult {
        return if (expireDays <= 0) {
            CleanupResult()
        } else {
            val rootPath = Paths.get(root)
            val visitor = CleanupFileVisitor(rootPath, expireDays)
            Files.walkFileTree(rootPath, visitor)
            visitor.cleanupResult
        }
    }

    fun transfer(input: ReadableByteChannel, output: FileChannel, size: Long, append: Boolean = false) {
        val startPosition: Long = if (append) output.size() else 0L
        var bytesCopied: Long
        var totalCopied = 0L
        var count: Long
        while (totalCopied < size) {
            val remain = size - totalCopied
            count = if (remain > FILE_COPY_BUFFER_SIZE) FILE_COPY_BUFFER_SIZE else remain
            bytesCopied = output.transferFrom(input, startPosition + totalCopied, count)
            if (bytesCopied == 0L) { // can happen if file is truncated after caching the size
                break
            }
            totalCopied += bytesCopied
        }
        if (totalCopied != size) {
            throw IOException("Failed to copy full contents. Expected length: $size, Actual: $totalCopied")
        }
    }

    companion object {
        /**
         * OpenJdk中FileChannelImpl.java限定了单次传输大小:
         * private static final long MAPPED_TRANSFER_SIZE = 8L*1024L*1024L;
         *
         * 防止不同jdk版本的不同实现，这里限定一下大小
         */
        private const val FILE_COPY_BUFFER_SIZE = 64 * 1024 * 1024L

        private val logger = LoggerFactory.getLogger(FileSystemClient::class.java)
    }
}
