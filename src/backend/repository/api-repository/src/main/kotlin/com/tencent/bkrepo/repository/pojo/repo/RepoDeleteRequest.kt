package com.tencent.bkrepo.repository.pojo.repo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("更新删除请求")
data class RepoDeleteRequest(
    @ApiModelProperty("所属项目id", required = true)
    override val projectId: String,
    @ApiModelProperty("仓库名称", required = true)
    override val name: String,
    @ApiModelProperty("是否强制删除（忽略存在的文件）", required = false)
    val forced: Boolean = false,
    @ApiModelProperty("操作用户", required = true)
    val operator: String
) : RepoRequest
