/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.rpm.util.RpmVersionUtils.toRpmPackagePojo
import com.tencent.bkrepo.rpm.util.xStream.XStreamUtil
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.lang.StringBuilder

class RpmVersionUtilsTest {
    @Test
    fun resolverRpmVersionTest() {
        val str = "httpd-2.4.6-93.el7.centos.x86_64.rpm"
        Assertions.assertEquals(str, RpmVersionUtils.resolverRpmVersion(str).toString())
    }

    @Test
    fun toRpmPackagePojoTest() {
        val str = "/7/httpd-2.4.6-93.el7.centos.x86_64.rpm"
        Assertions.assertEquals("7", str.toRpmPackagePojo().path)
        Assertions.assertEquals("httpd", str.toRpmPackagePojo().name)
        Assertions.assertEquals("2.4.6-93.el7.centos.x86_64", str.toRpmPackagePojo().version)
    }

    @Test
    fun checkFormat() {
        val file = File("/Users/weaving/Downloads/24c23c2606313a9578ab80e809ffcfef015eae5d-primary.xml")
        var line: String?
        val string = StringBuilder()
        BufferedReader(InputStreamReader(FileInputStream(file))).use { br ->
            while (br.readLine().also { line = it } != null) {
                string.append(line)
            }
        }

        val xml = string.toString()
        val primary = XStreamUtil.xmlToObject(xml)
    }
}
