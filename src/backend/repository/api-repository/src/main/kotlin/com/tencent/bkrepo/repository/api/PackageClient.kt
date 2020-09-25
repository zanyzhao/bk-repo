package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.repository.constant.SERVICE_NAME
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import io.swagger.annotations.ApiOperation
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Primary
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Primary
@FeignClient(SERVICE_NAME, contextId = "PackageClient")
@RequestMapping("/service")
interface PackageClient {

    @ApiOperation("查询包信息")
    @GetMapping("/package/info/{projectId}/{repoName}")
    fun findPackageByKey(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam packageKey: String
    ): Response<PackageSummary?>

    @ApiOperation("查询版本信息")
    @GetMapping("/version/info/{projectId}/{repoName}")
    fun findVersionByName(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam packageKey: String,
        @RequestParam version: String
    ): Response<PackageVersion?>

    @ApiOperation("创建包版本")
    @PostMapping("/version/create")
    fun createVersion(
        @RequestBody request: PackageVersionCreateRequest
    ): Response<Void>

    @ApiOperation("删除包")
    @DeleteMapping("/package/delete/{projectId}/{repoName}")
    fun deletePackage(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam packageKey: String
    ): Response<Void>

    @ApiOperation("删除版本")
    @DeleteMapping("/version/delete/{projectId}/{repoName}")
    fun deleteVersion(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam packageKey: String,
        @RequestParam version: String
    ): Response<Void>

    @ApiOperation("搜搜包")
    @DeleteMapping("/package/search")
    fun searchPackage(
        @RequestBody queryModel: QueryModel
    ): Response<Page<MutableMap<*, *>>>
}