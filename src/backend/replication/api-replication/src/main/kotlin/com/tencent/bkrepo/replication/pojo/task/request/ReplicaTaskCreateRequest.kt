/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.replication.pojo.task.request

import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.`object`.PackageConstraint
import com.tencent.bkrepo.replication.pojo.task.`object`.PathConstraint
import com.tencent.bkrepo.replication.pojo.task.setting.ReplicaSetting
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("同步任务创建请求")
data class ReplicaTaskCreateRequest(
    @ApiModelProperty("任务名称", required = true)
    val name: String,
    @ApiModelProperty("本地所属项目", required = true)
    val projectId: String,
    @ApiModelProperty("远程所属项目", required = true)
    val remoteProjectId: String = projectId,
    @ApiModelProperty("所属仓库信息", required = true)
    val repoInfo: Set<ReplicaRepoInfo>,
    @ApiModelProperty("同步类型", required = true)
    val replicaType: ReplicaType = ReplicaType.SCHEDULED,
    @ApiModelProperty("任务设置", required = true)
    val setting: ReplicaSetting,
    @ApiModelProperty("远程集群集合", required = true)
    val remoteClusterIds: Set<String>,
    @ApiModelProperty("包限制条件", required = false)
    val packageConstraints: Set<PackageConstraint>? = null,
    @ApiModelProperty("路径限制条件，包限制点路径限制都为空则同步整个仓库数据", required = false)
    val pathConstraints: Set<PathConstraint>? = null,
    @ApiModelProperty("是否启用", required = true)
    var enabled: Boolean = true,
    @ApiModelProperty("任务描述", required = false)
    val description: String? = null,
    @ApiModelProperty("操作用户", required = false)
    val operator: String = SYSTEM_USER
)
