package com.tencent.bkrepo.auth.repository

import com.tencent.bkrepo.auth.model.TRole
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface RoleRepository : MongoRepository<TRole, String> {
    fun deleteByTypeAndRIdAndProjectId(type: RoleType, rId: String, projectId: String): Long
    fun findByType(type: RoleType): List<TRole>
    fun findByProjectId(projectId: String): List<TRole>
    fun findByTypeAndProjectId(type: RoleType, projectId: String): List<TRole>
    fun findOneByRIdAndProjectId(rid: String, projectId: String): TRole?
    fun findOneById(Id: String): TRole?
    fun findOneByIdAndProjectIdAndType(Id: String, projectId: String ,type:RoleType): TRole?
    fun findOneByRIdAndProjectIdAndRepoName(Id: String, projectId: String ,repoName:String): TRole?
    fun findOneByIdAndProjectIdAndTypeAndRepoName(Id: String, projectId: String ,type:RoleType,repoName:String): TRole?
}
