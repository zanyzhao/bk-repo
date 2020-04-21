package com.tencent.bkrepo.repository.job

import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.storage.core.StorageService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * 文件同步任务
 * @author: carrypan
 * @date: 2019/12/24
 */
@Component
class FileSynchronizeJob {

    @Autowired
    private lateinit var storageService: StorageService

    // @Scheduled(cron = "* 0/1 * * * ?")
    @Async
    @SchedulerLock(name = "FileSynchronizeJob", lockAtMostFor = "P7D")
    fun run() {
        try {
            logger.info("Starting to synchronize file.")
            val startTimeMillis = System.currentTimeMillis()
            val result = storageService.synchronizeFile()
            val elapseSeconds = (System.currentTimeMillis() - startTimeMillis) / 1000
            logger.info("Synchronize file success. Walked [${result.totalCount}] files totally, synchronized[${result.synchronizedCount}]," +
                " error[${result.errorCount}], ignored[${result.ignoredCount}]" +
                ", [${result.totalSize}] bytes totally, elapse [$elapseSeconds] s.")
        } catch (exception: Exception) {
            logger.error("Synchronize file failed.", exception)
        }
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
    }
}