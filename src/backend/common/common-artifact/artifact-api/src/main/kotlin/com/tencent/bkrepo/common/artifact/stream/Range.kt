package com.tencent.bkrepo.common.artifact.stream

import kotlin.math.min

/**
 * 文件范围
 * [startPosition]代表起始位置，从0开始
 * [endPosition]代表结束位置，最大值为[total]-1
 * [total]代表文件总长度
 */
class Range(startPosition: Long, endPosition: Long, val total: Long) {
    /**
     * 起始位置
     */
    val start: Long = if (startPosition < 0) 0 else startPosition

    /**
     * 结束位置，范围为[start, total-1]，如果超出返回则设置为[total] - 1
     */
    val end: Long = if (endPosition < 0) total - 1 else min(endPosition, total - 1)

    /**
     * 范围长度
     */
    val length: Long = end - start + 1

    init {
        require(total >= 0) { "Invalid total size: $total" }
        require(length >= 0) { "Invalid range length $length" }
    }

    /**
     * 是否为部分内容
     */
    fun isPartialContent(): Boolean {
        return length != total
    }

    /**
     * 是否为完整内容
     */
    fun isFullContent(): Boolean {
        return length == total
    }

    /**
     * 是否为空内容
     */
    fun isEmpty(): Boolean {
        return length == 0L
    }

    override fun toString(): String {
        return "$start-$end/$total"
    }

    companion object {
        @Deprecated(message = "Replace with Range.full", replaceWith = ReplaceWith("full"))
        fun ofFull(total: Long) = Range(0, total - 1, total)

        /**
         * 创建长度为[total]的完整范围
         */
        fun full(total: Long) = Range(0, total - 1, total)
    }
}
