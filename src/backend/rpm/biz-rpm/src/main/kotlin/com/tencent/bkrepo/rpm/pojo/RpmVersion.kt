package com.tencent.bkrepo.rpm.pojo

data class RpmVersion(
    val name: String,
    val arch: String,
    val epoch: String,
    val ver: String,
    val rel: String
)