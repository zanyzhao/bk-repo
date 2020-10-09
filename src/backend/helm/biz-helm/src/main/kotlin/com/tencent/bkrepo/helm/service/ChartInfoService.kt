package com.tencent.bkrepo.helm.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.constants.CHART_NOT_FOUND
import com.tencent.bkrepo.helm.constants.FULL_PATH
import com.tencent.bkrepo.helm.constants.INDEX_CACHE_YAML
import com.tencent.bkrepo.helm.constants.NAME
import com.tencent.bkrepo.helm.constants.NODE_FULL_PATH
import com.tencent.bkrepo.helm.constants.NODE_METADATA
import com.tencent.bkrepo.helm.constants.NODE_METADATA_NAME
import com.tencent.bkrepo.helm.constants.NODE_METADATA_VERSION
import com.tencent.bkrepo.helm.constants.NO_CHART_NAME_FOUND
import com.tencent.bkrepo.helm.constants.PROJECT_ID
import com.tencent.bkrepo.helm.constants.REPO_NAME
import com.tencent.bkrepo.helm.constants.VERSION
import com.tencent.bkrepo.helm.pojo.IndexEntity
import com.tencent.bkrepo.helm.utils.YamlUtils
import com.tencent.bkrepo.repository.api.NodeClient
import org.apache.http.HttpStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.InputStream
import java.time.LocalDateTime

@Service
class ChartInfoService {

    @Autowired
    private lateinit var nodeClient: NodeClient

    @Autowired
    private lateinit var chartRepositoryService: ChartRepositoryService

    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun allChartsList(artifactInfo: HelmArtifactInfo, startTime: LocalDateTime? = null): Map<String, *> {
        if (startTime != null) {
            val nodeList = chartRepositoryService.queryNodeList(artifactInfo, lastModifyTime = startTime)
            val indexEntity = chartRepositoryService.initIndexEntity()
            if (nodeList.isNotEmpty()) {
                chartRepositoryService.generateIndexFile(nodeList, indexEntity, artifactInfo)
            }
            return indexEntity.entries
        }
        chartRepositoryService.freshIndexFile(artifactInfo)
        val context = ArtifactQueryContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        // context.contextAttributes[FULL_PATH] = INDEX_CACHE_YAML
        context.putAttribute(FULL_PATH, INDEX_CACHE_YAML)
        val inputStream = repository.query(context) as ArtifactInputStream
        return inputStream.use {
            searchJson(it, artifactInfo.getArtifactFullPath())
        }
    }

    private fun searchJson(inputStream: InputStream, urls: String): Map<String, *> {
        val jsonStr = YamlUtils.yaml2Json(inputStream)
        val indexEntity = JsonUtils.objectMapper.readValue(jsonStr, IndexEntity::class.java)
        val urlList = urls.removePrefix("/").split("/").filter { it.isNotBlank() }
        return when (urlList.size) {
            // Without name and version
            0 -> {
                indexEntity.entries
            }
            // query with name
            1 -> {
                val chartName = urlList[0]
                val chartList = indexEntity.entries[chartName]
                chartList?.let { mapOf(chartName to chartList) } ?: CHART_NOT_FOUND
            }
            // query with name and version
            2 -> {
                val chartName = urlList[0]
                val chartVersion = urlList[1]
                val chartList = indexEntity.entries[chartName] ?: return NO_CHART_NAME_FOUND
                val chartVersionList = chartList.filter { chartVersion == it[VERSION] }.toList()
                if (chartVersionList.isNotEmpty()) {
                    mapOf(chartName to chartVersionList)
                } else {
                    mapOf("error" to "no chart version found for $chartName-$chartVersion")
                }
            }
            else -> {
                // ERROR_NOT_FOUND
                CHART_NOT_FOUND
            }
        }
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun isExists(artifactInfo: HelmArtifactInfo) {
        val response = HttpContextHolder.getResponse()
        val status: Int = with(artifactInfo) {
            val projectId = Rule.QueryRule(PROJECT_ID, projectId)
            val repoName = Rule.QueryRule(REPO_NAME, repoName)
            val urlList = this.getArtifactFullPath().removePrefix("/").split("/").filter { it.isNotBlank() }
            val rule: Rule? = when (urlList.size) {
                // query with name
                1 -> {
                    val name = Rule.QueryRule(NODE_METADATA_NAME, urlList[0])
                    Rule.NestedRule(mutableListOf(repoName, projectId, name))
                }
                // query with name and version
                2 -> {
                    val name = Rule.QueryRule(NODE_METADATA_NAME, urlList[0])
                    val version = Rule.QueryRule(NODE_METADATA_VERSION, urlList[1])
                    Rule.NestedRule(mutableListOf(repoName, projectId, name, version))
                }
                else -> {
                    null
                }
            }
            if (rule != null) {
                val queryModel = QueryModel(
                    page = PageLimit(CURRENT_PAGE, SIZE),
                    sort = Sort(listOf(NAME), Sort.Direction.ASC),
                    select = mutableListOf(PROJECT_ID, REPO_NAME, NODE_FULL_PATH, NODE_METADATA),
                    rule = rule
                )
                val nodeList: List<Map<String, Any?>>? = nodeClient.query(queryModel).data?.records
                if (nodeList.isNullOrEmpty()) HttpStatus.SC_NOT_FOUND else HttpStatus.SC_OK
            } else {
                HttpStatus.SC_NOT_FOUND
            }
        }
        response.status = status
    }

    companion object {
        const val CURRENT_PAGE = 0
        const val SIZE = 5
    }
}
