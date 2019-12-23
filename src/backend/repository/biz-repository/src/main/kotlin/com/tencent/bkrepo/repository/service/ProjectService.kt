package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.repository.model.TProject
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.repository.ProjectRepository
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service

@Service
class ProjectService @Autowired constructor(
    private val projectRepository: ProjectRepository,
    private val mongoTemplate: MongoTemplate
) {
    fun query(name: String): ProjectInfo? {
        return convert(queryProject(name))
    }

    fun list(): List<ProjectInfo> {
        return projectRepository.findAll().map { convert(it)!! }
    }

    fun exist(name: String): Boolean {
        return queryProject(name) != null
    }

    fun create(request: ProjectCreateRequest) {
        validateParameter(request)
        with(request) {
            if (exist(name)) {
                throw ErrorCodeException(ArtifactMessageCode.PROJECT_EXISTED, name)
            }
            projectRepository.insert(
                TProject(
                    name = name,
                    displayName = displayName,
                    description = description,

                    createdBy = operator,
                    createdDate = LocalDateTime.now(),
                    lastModifiedBy = operator,
                    lastModifiedDate = LocalDateTime.now()
                )
            )
        }
        logger.info("Create project [$request] success.")
    }

    fun checkProject(name: String) {
        if (!exist(name)) throw ErrorCodeException(ArtifactMessageCode.PROJECT_NOT_FOUND, name)
    }

    private fun queryProject(name: String): TProject? {
        if (name.isBlank()) return null

        val criteria = Criteria.where(TProject::name.name).`is`(name)
        return mongoTemplate.findOne(Query(criteria), TProject::class.java)
    }

    private fun validateParameter(request: ProjectCreateRequest) {
        request.takeIf { it.name.isNotBlank() } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "name")
        request.takeIf { it.displayName.isNotBlank() } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "displayName")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ProjectService::class.java)

        private fun convert(tProject: TProject?): ProjectInfo? {
            return tProject?.let {
                ProjectInfo(
                    name = it.name,
                    displayName = it.displayName,
                    description = it.description
                )
            }
        }
    }
}