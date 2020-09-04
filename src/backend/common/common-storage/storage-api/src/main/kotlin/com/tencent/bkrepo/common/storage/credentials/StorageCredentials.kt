package com.tencent.bkrepo.common.storage.credentials

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.tencent.bkrepo.common.storage.config.CacheProperties
import com.tencent.bkrepo.common.storage.config.UploadProperties

/**
 * 存储身份信息
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = FileSystemCredentials::class, name = FileSystemCredentials.type),
    JsonSubTypes.Type(value = InnerCosCredentials::class, name = InnerCosCredentials.type),
    JsonSubTypes.Type(value = HDFSCredentials::class, name = HDFSCredentials.type)
)
abstract class StorageCredentials(
    open var key: String? = null,
    open var cache: CacheProperties,
    open var upload: UploadProperties
)
