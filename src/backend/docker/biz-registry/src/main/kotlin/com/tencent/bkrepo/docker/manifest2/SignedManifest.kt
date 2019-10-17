package com.tencent.bkrepo.docker.manifest2

data class SignedManifest(
    var schemaVersion: Int = 1,
    var mediaType: String,
    var tag: String,
    var architecture: String,
    var layers: List<FSLayer>,
    var history: List<History>
)