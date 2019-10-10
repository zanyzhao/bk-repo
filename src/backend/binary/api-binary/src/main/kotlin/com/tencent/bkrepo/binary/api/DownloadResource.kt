package com.tencent.bkrepo.binary.api

import com.tencent.bkrepo.binary.pojo.FileInfo
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_USER_ID
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_USER_ID_DEFAULT_VALUE
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 下载接口
 *
 * @author: carrypan
 * @date: 2019-09-28
 */
@Api("下载接口")
@RequestMapping("/download")
interface DownloadResource {

    @ApiOperation("简单下载")
    @GetMapping("/simple/{projectId}/{repoName}/{fullPath}")
    fun simpleDownload(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目id", required = true)
        @PathVariable
        projectId: String,
        @ApiParam("仓库名称", required = true)
        @PathVariable
        repoName: String,
        @ApiParam("完整路径", required = true)
        @PathVariable
        fullPath: String
    )

    @ApiOperation("查询文件信息")
    @GetMapping("/info/{projectId}/{repoName}/{fullPath}")
    fun info(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目id", required = true)
        @PathVariable
        projectId: String,
        @ApiParam("仓库名称", required = true)
        @PathVariable
        repoName: String,
        @ApiParam("完整路径", required = true)
        @PathVariable
        fullPath: String
    ): Response<FileInfo>

    @ApiOperation("分块下载")
    @GetMapping("/block/{projectId}/{repoName}/{fullPath}")
    fun blockDownload(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目id", required = true)
        @PathVariable
        projectId: String,
        @ApiParam("仓库名称", required = true)
        @PathVariable
        repoName: String,
        @ApiParam("完整路径", required = true)
        @PathVariable
        fullPath: String,
        @ApiParam("分块序号", required = true)
        sequence: Int
    ): Response<FileInfo>
}