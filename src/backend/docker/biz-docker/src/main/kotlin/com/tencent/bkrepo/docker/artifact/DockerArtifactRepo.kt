package com.tencent.bkrepo.docker.artifact

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.StringPool.EMPTY
import com.tencent.bkrepo.common.api.constant.StringPool.SLASH
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.docker.constant.DOCKER_CREATE_BY
import com.tencent.bkrepo.docker.constant.DOCKER_MANIFEST
import com.tencent.bkrepo.docker.constant.DOCKER_NODE_FULL_PATH
import com.tencent.bkrepo.docker.constant.DOCKER_NODE_NAME
import com.tencent.bkrepo.docker.constant.DOCKER_NODE_PATH
import com.tencent.bkrepo.docker.constant.DOCKER_NODE_SIZE
import com.tencent.bkrepo.docker.constant.DOCKER_PROJECT_ID
import com.tencent.bkrepo.docker.constant.DOCKER_REPO_NAME
import com.tencent.bkrepo.docker.constant.LAST_MODIFIED_BY
import com.tencent.bkrepo.docker.constant.LAST_MODIFIED_DATE
import com.tencent.bkrepo.docker.constant.REPO_TYPE
import com.tencent.bkrepo.docker.constant.STAGE_TAG
import com.tencent.bkrepo.docker.context.DownloadContext
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.context.UploadContext
import com.tencent.bkrepo.docker.exception.DockerFileReadFailedException
import com.tencent.bkrepo.docker.exception.DockerFileSaveFailedException
import com.tencent.bkrepo.docker.exception.DockerMoveFileFailedException
import com.tencent.bkrepo.docker.exception.DockerRepoNotFoundException
import com.tencent.bkrepo.docker.pojo.DockerImage
import com.tencent.bkrepo.docker.pojo.DockerTag
import com.tencent.bkrepo.repository.api.MetadataClient
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import com.tencent.bkrepo.repository.pojo.node.service.NodeCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.InputStream

/**
 * docker repo storage interface
 * to work with storage module
 */
@Service
class DockerArtifactRepo @Autowired constructor(
    val repositoryClient: RepositoryClient,
    private val nodeClient: NodeClient,
    private val storageService: StorageService,
    private val metadataService: MetadataClient,
    private val permissionManager: PermissionManager
) {

    lateinit var userId: String

    /**
     * start a append upload
     * @param context the request context params
     * @return String append Id
     */
    fun startAppend(context: RequestContext): String {
        // check repository
        val repository = repositoryClient.getRepoDetail(context.projectId, context.repoName, REPO_TYPE).data ?: run {
            logger.warn("user [$userId]  download file [$context] repository not found")
            throw DockerRepoNotFoundException(context.repoName)
        }
        logger.debug("user [$userId] start to append file ")
        return storageService.createAppendId(repository.storageCredentials)
    }

    /**
     * write data in a append upload
     * @param context the request context params
     * @param uuid the append id
     * @param artifactFile file object
     * @return Long  the size of the file
     */
    fun writeAppend(context: RequestContext, uuid: String, artifactFile: ArtifactFile): Long {
        // check repository
        val repository = repositoryClient.getRepoDetail(context.projectId, context.repoName, REPO_TYPE).data ?: run {
            logger.warn("user [$userId] append file [$context] repository not found")
            throw DockerRepoNotFoundException(context.repoName)
        }
        logger.debug("user [$userId]  append file id [$uuid]")
        return this.storageService.append(uuid, artifactFile, repository.storageCredentials)
    }

    /**
     * finish a append upload
     * @param context the upload context params
     * @param uuid the append id
     * @return Boolean is the file upload success
     */
    fun finishAppend(context: UploadContext, uuid: String): Boolean {
        // check repository
        val repository = repositoryClient.getRepoDetail(context.projectId, context.repoName, REPO_TYPE).data ?: run {
            logger.warn("user[$userId]  finish append file  [$context] not found")
            throw DockerRepoNotFoundException(context.repoName)
        }
        val file = this.storageService.finishAppend(uuid, repository.storageCredentials)
        val node = NodeCreateRequest(
            projectId = context.projectId,
            repoName = context.repoName,
            folder = false,
            fullPath = context.fullPath,
            size = file.size,
            sha256 = file.sha256,
            md5 = file.md5,
            operator = userId,
            metadata = emptyMap(),
            overwrite = true
        )
        // save node
        val result = nodeClient.create(node)
        if (result.isNotOk()) {
            logger.error("user [$userId] finish append file  [${context.fullPath}] failed: [$result]")
            throw DockerFileSaveFailedException(context.fullPath)
        }
        logger.debug("user [$userId] finish append file  [${context.fullPath} , ${file.sha256}] success")
        return true
    }

    /**
     * download a file
     * @param downloadContext the download context params
     * @return InputStream the file download inputstream
     */
    fun download(downloadContext: DownloadContext): InputStream {
        // check repository
        val context = downloadContext.context
        val repository = repositoryClient.getRepoDetail(context.projectId, context.repoName, REPO_TYPE).data ?: run {
            logger.warn("user [$userId]  download file [$context] repository not found")
            throw DockerRepoNotFoundException(context.repoName)
        }
        logger.debug("load file  [$downloadContext]")
        // load file from storage
        return storageService.load(
            downloadContext.sha256,
            Range.full(downloadContext.length),
            repository.storageCredentials
        ) ?: run {
            logger.error("user [$userId] fail to load data [$downloadContext] from storage  ")
            throw DockerFileReadFailedException(context.repoName)
        }
    }

    /**
     * upload a file
     * @param uploadContext the upload context params
     * @return Boolean is the file upload success
     */
    fun upload(uploadContext: UploadContext): Boolean {
        // check repository
        val repository =
            repositoryClient.getRepoDetail(uploadContext.projectId, uploadContext.repoName, REPO_TYPE).data ?: run {
                logger.warn("user [$userId]  upload file  [$uploadContext] repository not found")
                throw DockerRepoNotFoundException(uploadContext.repoName)
            }

        logger.debug("user [$userId] start to store file [${uploadContext.sha256}]")
        // store the file
        storageService.store(uploadContext.sha256, uploadContext.artifactFile!!, repository.storageCredentials)
        // save the node
        val result = nodeClient.create(
            NodeCreateRequest(
                projectId = uploadContext.projectId,
                repoName = uploadContext.repoName,
                folder = false,
                fullPath = uploadContext.fullPath,
                size = uploadContext.artifactFile!!.getSize(),
                sha256 = uploadContext.artifactFile!!.getFileSha256(),
                md5 = uploadContext.artifactFile!!.getFileMd5(),
                operator = userId,
                metadata = uploadContext.metadata,
                overwrite = true
            )
        )
        if (result.isNotOk()) {
            logger.error("user [$userId]  upload file [${uploadContext.fullPath}] failed: [$result]")
            throw DockerFileSaveFailedException(uploadContext.fullPath)
        }
        logger.debug("user [$userId]  upload file [${uploadContext.fullPath}] success")
        return true
    }

    /**
     * copy a file from source path to destination path
     * @param context the upload context params
     * @param srcPath the source path of file
     * @param destPath the destination file of file
     * @return Boolean is the file upload success
     */
    fun copy(context: RequestContext, srcPath: String, destPath: String): Boolean {
        with(context) {
            val copyRequest = NodeCopyRequest(
                srcProjectId = projectId,
                srcRepoName = repoName,
                srcFullPath = srcPath,
                destProjectId = projectId,
                destRepoName = repoName,
                destFullPath = destPath,
                overwrite = true,
                operator = userId
            )
            val result = nodeClient.copy(copyRequest)
            if (result.isNotOk()) {
                logger.error("user [$userId] request [$copyRequest] copy file fail")
                throw DockerMoveFileFailedException("$srcPath->$destPath")
            }
            return true
        }
    }

    /**
     * move a file from source path to destination path
     * @param context the upload context params
     * @param from the source path of file
     * @param to the destination file of file
     * @return Boolean is the file upload success
     */
    fun move(context: RequestContext, from: String, to: String): Boolean {
        with(context) {
            val renameRequest = NodeRenameRequest(projectId, repoName, from, to, userId)
            logger.debug("move request [$renameRequest]")
            val result = nodeClient.rename(renameRequest)
            if (result.isNotOk()) {
                logger.error("user [$userId] request [$renameRequest] move file fail")
                throw DockerMoveFileFailedException("$from->$to")
            }
            return true
        }
    }

    /**
     * set attributes of a file
     * @param projectId project of the repo
     * @param repoName name of the repo
     * @param fullPath the path of file
     * @param data metadata
     */
    fun setAttributes(projectId: String, repoName: String, fullPath: String, data: Map<String, String>) {
        logger.info("set attributes request [$projectId,$repoName,$fullPath,$data]")
        val result = metadataService.save(MetadataSaveRequest(projectId, repoName, fullPath, data))
        if (result.isNotOk()) {
            logger.error("set attribute [$projectId,$repoName,$fullPath,$data] fail")
            throw DockerFileSaveFailedException("set attribute fail")
        }
    }

    /**
     * get attributes of a file
     * @param projectId project of the repo
     * @param repoName name of the repo
     * @param fullPath full path of file
     * @param key metadata
     * @return String is the value of the metadata
     */
    fun getAttribute(projectId: String, repoName: String, fullPath: String, key: String): String? {
        val result = metadataService.query(projectId, repoName, fullPath).data!!
        logger.debug("get attribute params : [$projectId,$repoName,$fullPath,$key] ,result: [$result]")
        return result[key] as String
    }

    /**
     * check is the node is exist
     * @param projectId project of the repo
     * @param repoName name of the repo
     * @param fullPath full path of file
     * @return Boolean is the file exist
     */
    fun exists(projectId: String, repoName: String, fullPath: String): Boolean {
        return nodeClient.exist(projectId, repoName, fullPath).data ?: return false
    }

    /**
     * check is the node can be read
     * @param context the request context
     * @return Boolean is the file can be read
     */
    fun canRead(context: RequestContext): Boolean {
        try {
            permissionManager.checkPermission(
                userId,
                ResourceType.PROJECT,
                PermissionAction.WRITE,
                context.projectId,
                context.repoName
            )
        } catch (e: PermissionException) {
            logger.debug("user: [$userId] ,check read permission fail [$context]")
            return false
        }
        return true
    }

    /**
     * check is the node can be write
     * @param context the request context
     * @return Boolean is the file can be write
     */
    fun canWrite(context: RequestContext): Boolean {
        try {
            permissionManager.checkPermission(
                userId,
                ResourceType.PROJECT,
                PermissionAction.WRITE,
                context.projectId,
                context.repoName
            )
        } catch (e: PermissionException) {
            logger.debug("user: [$userId] ,check write permission fail [$context]")
            return false
        }
        return true
    }

    // get docker  artifact
    fun getArtifact(projectId: String, repoName: String, fullPath: String): DockerArtifact? {
        val node = nodeClient.detail(projectId, repoName, fullPath).data ?: run {
            logger.warn("get artifact detail failed: [$projectId, $repoName, $fullPath] found no artifact")
            return null
        }
        node.sha256 ?: run {
            logger.error("get artifact detail failed: [$projectId, $repoName, $fullPath] found no artifact")
            return null
        }
        return DockerArtifact(projectId, repoName, fullPath).sha256(node.sha256!!)
            .length(node.size)
    }

    // get node detail
    fun getNodeDetail(context: RequestContext, fullPath: String): NodeDetail? {
        with(context) {
            return nodeClient.detail(projectId, repoName, fullPath).data ?: run<RequestContext, NodeDetail> {
                logger.warn("get artifact detail failed: [$projectId, $repoName, $fullPath] found no artifact")
                return null
            }
        }
    }

    // get artifact list by name
    fun getArtifactListByName(projectId: String, repoName: String, fileName: String): List<Map<String, Any?>> {
        val projectRule = Rule.QueryRule(DOCKER_PROJECT_ID, projectId)
        val repoNameRule = Rule.QueryRule(DOCKER_REPO_NAME, repoName)
        val nameRule = Rule.QueryRule(DOCKER_NODE_NAME, fileName)
        val rule = Rule.NestedRule(mutableListOf(projectRule, repoNameRule, nameRule))
        val queryModel = QueryModel(
            page = PageLimit(DOCKER_SEARCH_INDEX, DOCKER_SEARCH_LIMIT_SMALL),
            sort = Sort(listOf(DOCKER_NODE_FULL_PATH), Sort.Direction.ASC),
            select = mutableListOf(DOCKER_NODE_FULL_PATH, DOCKER_NODE_PATH, DOCKER_NODE_SIZE),
            rule = rule
        )

        val result = nodeClient.query(queryModel).data ?: run {
            logger.warn("find artifact list failed: [$projectId, $repoName, $fileName] found no node")
            return emptyList()
        }
        return result.records
    }

    // get docker image list
    fun getDockerArtifactList(
        projectId: String,
        repoName: String,
        pageNumber: Int,
        pageSize: Int,
        name: String?
    ): List<DockerImage> {
        var queryModel = NodeQueryBuilder().select(
            DOCKER_NODE_FULL_PATH,
            DOCKER_NODE_PATH,
            DOCKER_NODE_SIZE,
            LAST_MODIFIED_BY,
            LAST_MODIFIED_DATE
        ).sortByAsc(DOCKER_NODE_FULL_PATH).projectId(projectId).repoName(repoName).name(DOCKER_MANIFEST)
        name?.let {
            queryModel.path("*$name*", OperationType.MATCH)
        }
        val result = nodeClient.query(queryModel.build()).data ?: run {
            logger.warn("find repo list failed: [$projectId, $repoName] ")
            return emptyList()
        }
        var data = mutableListOf<DockerImage>()
        var repoList = mutableListOf<String>()
        var downloadCountList = mutableMapOf<String, Long>()
        result.records.forEach {
            val path = it[DOCKER_NODE_PATH] as String
            val name = path.removeSuffix(SLASH).replaceAfterLast(SLASH, EMPTY).removeSuffix(SLASH).removePrefix(SLASH)
            val lastModifiedBy = it[LAST_MODIFIED_BY] as String
            val lastModifiedDate = it[LAST_MODIFIED_DATE] as String
            var downLoadCount = 0L
            data.add(DockerImage(name, lastModifiedBy, lastModifiedDate, downLoadCount, "", ""))
            repoList.add(name)
        }
        var repoInfo = mutableListOf<DockerImage>()
        repoList.distinct().forEach { its ->
            var downloadCount = 0L
            var lastModifiedBy = EMPTY
            var lastModifiedDate = EMPTY
            data.forEach {
                if (its == it.name) {
                    downloadCount += it.downloadCount
                    lastModifiedBy = it.lastModifiedBy
                    lastModifiedDate = it.lastModifiedDate
                }
            }
            repoInfo.add(DockerImage(its, lastModifiedBy, lastModifiedDate, downloadCount, "", ""))
        }
        return repoInfo
    }

    // get repo tag list
    fun getRepoTagList(context: RequestContext, pageNumber: Int, pageSize: Int, tag: String?): List<DockerTag> {
        with(context) {
            var queryModel = NodeQueryBuilder().select(
                DOCKER_NODE_FULL_PATH,
                DOCKER_NODE_PATH,
                DOCKER_NODE_SIZE,
                DOCKER_CREATE_BY,
                LAST_MODIFIED_BY,
                LAST_MODIFIED_DATE,
                STAGE_TAG
            ).sortByAsc(DOCKER_NODE_FULL_PATH).page(pageNumber, pageSize).projectId(projectId).repoName(repoName)
            queryModel = if (tag == null) {
                queryModel.name(DOCKER_MANIFEST).path("/$artifactName/", OperationType.PREFIX)
            } else {
                queryModel.name(DOCKER_MANIFEST).path("/$artifactName/*$tag*", OperationType.MATCH)
            }

            val result = nodeClient.query(queryModel.build()).data ?: run {
                logger.warn("find artifacts failed: [$projectId, $repoName] found no node")
                return emptyList()
            }
            val data = mutableListOf<DockerTag>()
            result.records.forEach {
                var path = it[DOCKER_NODE_PATH] as String
                val tag = path.removePrefix("/$artifactName/").removeSuffix(SLASH)
                val lastModifiedBy = it[LAST_MODIFIED_BY] as String
                val lastModifiedDate = it[LAST_MODIFIED_DATE] as String
                val size = it[DOCKER_NODE_SIZE] as Int
                val downLoadCount = 0L
                val stageTag = ""//it[STAGE_TAG] as String
                data.add(DockerTag(tag, stageTag, size, lastModifiedBy, lastModifiedDate, downLoadCount))
            }
            return data
        }
    }

    fun getRepoTagCount(context: RequestContext, tag: String?): Long {
        with(context) {
            var queryModel = NodeQueryBuilder().select(
                DOCKER_NODE_FULL_PATH,
                DOCKER_NODE_PATH,
                DOCKER_NODE_SIZE,
                DOCKER_CREATE_BY,
                LAST_MODIFIED_BY,
                LAST_MODIFIED_DATE,
                STAGE_TAG
            ).sortByAsc(DOCKER_NODE_FULL_PATH).projectId(projectId).repoName(repoName)
            queryModel = if (tag == null) {
                queryModel.name(DOCKER_MANIFEST).path("/$artifactName/", OperationType.PREFIX)
            } else {
                queryModel.name(DOCKER_MANIFEST).path("/$artifactName/*$tag*", OperationType.MATCH)
            }

            val result = nodeClient.query(queryModel.build()).data ?: run {
                logger.warn("find artifacts failed: [$projectId, $repoName] found no node")
                return 0L
            }
            return result.totalRecords
        }
    }

    // get blob list by digest
    fun getBlobListByDigest(projectId: String, repoName: String, digestName: String): List<Map<String, Any?>>? {
        val projectRule = Rule.QueryRule(DOCKER_PROJECT_ID, projectId)
        val repoNameRule = Rule.QueryRule(DOCKER_REPO_NAME, repoName)
        val nameRule = Rule.QueryRule(DOCKER_NODE_NAME, digestName)
        val rule = Rule.NestedRule(mutableListOf(projectRule, repoNameRule, nameRule))
        val queryModel = QueryModel(
            page = PageLimit(DOCKER_SEARCH_INDEX, DOCKER_SEARCH_LIMIT),
            sort = Sort(listOf(DOCKER_NODE_PATH), Sort.Direction.ASC),
            select = mutableListOf(DOCKER_NODE_PATH, DOCKER_NODE_SIZE),
            rule = rule
        )
        val result = nodeClient.query(queryModel).data ?: run {
            logger.warn("find artifacts failed:  $digestName found no node")
            return null
        }
        return result.records
    }

    // to delete docker image tag
    fun deleteByTag(projectId: String, repoName: String, name: String, tag: String): Boolean {
        val fullPath = "/$name/$tag/$DOCKER_MANIFEST"
        val deleteNodeRequest = NodeDeleteRequest(projectId, repoName, fullPath, userId)
        return nodeClient.delete(deleteNodeRequest).isOk()
    }

    companion object {
        private const val DOCKER_SEARCH_INDEX = 0
        private const val DOCKER_SEARCH_LIMIT = 9999999
        private const val DOCKER_SEARCH_LIMIT_SMALL = 10
        private val logger = LoggerFactory.getLogger(DockerArtifactRepo::class.java)
    }
}
