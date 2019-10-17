package com.tencent.bkrepo.docker.resource

import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class TagImpl {

    @GetMapping("/v2/tag")
    fun sayHello(
        @RequestParam
        @ApiParam(value = "姓名", required = true)
        name: String
    ): String {
        return "Hello, $name!"
    }
}