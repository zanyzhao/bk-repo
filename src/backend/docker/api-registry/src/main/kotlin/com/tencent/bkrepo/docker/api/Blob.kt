package com.tencent.bkrepo.docker.api

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam

import javax.ws.rs.core.Response
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.http.HttpHeaders

/**
 *  docker image blob 文件处理接口
 *
 * @author: owenlxu
 * @date: 2019-10-13
 */
@Api("docker镜像blob文件处理接口")
@RequestMapping("/v2/")
interface Blob {

    @ApiOperation("上传blob文件")
    @PutMapping("{projectId}/{repoName}/{name}/blobs/{digest}")
    fun putBlob(
        @PathVariable
        @ApiParam(value = "projectId", required = true)
        projectId: String,
        @PathVariable
        @ApiParam(value = "repoName", required = true)
        repoName: String,
        @PathVariable
        @ApiParam(value = "name", required = true)
        name: String,
        @PathVariable
        @ApiParam(value = "digest", required = true)
        digest: String
    ): Response

    @ApiOperation("检查blob文件是否存在")
    @RequestMapping(method = [RequestMethod.HEAD], value = ["{projectId}/{repoName}/{name}/blobs/{digest}"])
    fun isBlobExists(
        @PathVariable
        @ApiParam(value = "projectId", required = true)
        projectId: String,
        @PathVariable
        @ApiParam(value = "repoName", required = true)
        repoName: String,
        @PathVariable
        @ApiParam(value = "name", required = true)
        name: String,
        @PathVariable
        @ApiParam(value = "digest", required = true)
        digest: String
    ): Response

    @ApiOperation("获取blob文件")
    @RequestMapping(method = [RequestMethod.GET], value = ["{projectId}/{repoName}/{name}/blobs/{digest}"])
    fun getBlob(
        @PathVariable
        @ApiParam(value = "projectId", required = true)
        projectId: String,
        @PathVariable
        @ApiParam(value = "repoName", required = true)
        repoName: String,
        @PathVariable
        @ApiParam(value = "name", required = true)
        name: String,
        @PathVariable
        @ApiParam(value = "digest", required = true)
        digest: String
    ): Response

    @ApiOperation("获取blob文件")
    @RequestMapping(method = [RequestMethod.POST], value = ["{projectId}/{repoName}/{name}/blobs/uploads"])
    fun startBlobUpload(
            @RequestHeader
            headers : HttpHeaders,
            @PathVariable
            @ApiParam(value = "projectId", required = true)
            projectId: String,
            @PathVariable
            @ApiParam(value = "repoName", required = true)
            repoName: String,
            @PathVariable
            @ApiParam(value = "name", required = true)
            name: String,
            @RequestParam
            @ApiParam(value = "mount", required = false)
            mount: String?
    ): Response
}