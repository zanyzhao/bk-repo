/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.analyst.service.impl

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.LocaleMessageUtils.getLocalizedMessage
import com.tencent.bkrepo.analyst.component.ScannerPermissionCheckHandler
import com.tencent.bkrepo.analyst.component.manager.ScanExecutorResultManager
import com.tencent.bkrepo.analyst.component.manager.ScannerConverter
import com.tencent.bkrepo.analyst.dao.ArchiveSubScanTaskDao
import com.tencent.bkrepo.analyst.dao.FileScanResultDao
import com.tencent.bkrepo.analyst.dao.PlanArtifactLatestSubScanTaskDao
import com.tencent.bkrepo.analyst.dao.ScanPlanDao
import com.tencent.bkrepo.analyst.dao.ScanTaskDao
import com.tencent.bkrepo.analyst.dao.SubScanTaskDao
import com.tencent.bkrepo.analyst.event.ScanTaskStatusChangedEvent
import com.tencent.bkrepo.analyst.event.SubtaskStatusChangedEvent
import com.tencent.bkrepo.analyst.event.listener.ScanTaskStatusChangedEventListener
import com.tencent.bkrepo.analyst.message.ScannerMessageCode
import com.tencent.bkrepo.analyst.metrics.ScannerMetrics
import com.tencent.bkrepo.analyst.model.TArchiveSubScanTask
import com.tencent.bkrepo.analyst.model.TPlanArtifactLatestSubScanTask
import com.tencent.bkrepo.analyst.model.TScanPlan
import com.tencent.bkrepo.analyst.model.TScanTask
import com.tencent.bkrepo.analyst.model.TSubScanTask
import com.tencent.bkrepo.analyst.pojo.ScanTask
import com.tencent.bkrepo.analyst.pojo.ScanTaskStatus
import com.tencent.bkrepo.analyst.pojo.ScanTriggerType
import com.tencent.bkrepo.analyst.pojo.SubScanTask
import com.tencent.bkrepo.analyst.pojo.TaskMetadata
import com.tencent.bkrepo.analyst.pojo.TaskMetadata.Companion.TASK_METADATA_BUILD_NUMBER
import com.tencent.bkrepo.analyst.pojo.TaskMetadata.Companion.TASK_METADATA_KEY_BID
import com.tencent.bkrepo.analyst.pojo.TaskMetadata.Companion.TASK_METADATA_KEY_PID
import com.tencent.bkrepo.analyst.pojo.TaskMetadata.Companion.TASK_METADATA_PIPELINE_NAME
import com.tencent.bkrepo.analyst.pojo.TaskMetadata.Companion.TASK_METADATA_PLUGIN_NAME
import com.tencent.bkrepo.analyst.pojo.request.PipelineScanRequest
import com.tencent.bkrepo.analyst.pojo.request.ReportResultRequest
import com.tencent.bkrepo.analyst.pojo.request.ScanRequest
import com.tencent.bkrepo.analyst.service.ScanPlanService
import com.tencent.bkrepo.analyst.service.ScanQualityService
import com.tencent.bkrepo.analyst.service.ScanService
import com.tencent.bkrepo.analyst.service.ScannerService
import com.tencent.bkrepo.analyst.task.ScanTaskScheduler
import com.tencent.bkrepo.analyst.utils.Converter
import com.tencent.bkrepo.analyst.utils.RuleConverter
import com.tencent.bkrepo.analyst.utils.RuleUtil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Service
class ScanServiceImpl @Autowired constructor(
    private val scanTaskDao: ScanTaskDao,
    private val subScanTaskDao: SubScanTaskDao,
    private val scanPlanService: ScanPlanService,
    private val scanPlanDao: ScanPlanDao,
    private val planArtifactLatestSubScanTaskDao: PlanArtifactLatestSubScanTaskDao,
    private val archiveSubScanTaskDao: ArchiveSubScanTaskDao,
    private val fileScanResultDao: FileScanResultDao,
    private val scannerService: ScannerService,
    private val scanTaskScheduler: ScanTaskScheduler,
    private val scanExecutorResultManagers: Map<String, ScanExecutorResultManager>,
    private val scannerConverters: Map<String, ScannerConverter>,
    private val scannerMetrics: ScannerMetrics,
    private val permissionCheckHandler: ScannerPermissionCheckHandler,
    private val publisher: ApplicationEventPublisher,
    private val scanTaskStatusChangedEventListener: ScanTaskStatusChangedEventListener,
    private val scanQualityService: ScanQualityService
) : ScanService {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var self: ScanServiceImpl

    @Transactional(rollbackFor = [Throwable::class])
    override fun scan(scanRequest: ScanRequest, triggerType: ScanTriggerType, userId: String?): ScanTask {
        val task = createTask(scanRequest, triggerType, userId)
        scanTaskScheduler.schedule(task)
        return task
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun pipelineScan(pipelineScanRequest: PipelineScanRequest): ScanTask {
        with(pipelineScanRequest) {
            val defaultScanPlan = scanPlanService.getOrCreateDefaultPlan(projectId, planType, scanner)
            val metadata = if (pid != null && bid != null) {
                val data = ArrayList<TaskMetadata>()
                pid?.let { data.add(TaskMetadata(key = TASK_METADATA_KEY_PID, value = it)) }
                bid?.let { data.add(TaskMetadata(key = TASK_METADATA_KEY_BID, value = it)) }
                pluginName?.let { data.add(TaskMetadata(key = TASK_METADATA_PLUGIN_NAME, value = it)) }
                buildNo?.let { data.add(TaskMetadata(key = TASK_METADATA_BUILD_NUMBER, value = it)) }
                pipelineName?.let { data.add(TaskMetadata(key = TASK_METADATA_PIPELINE_NAME, value = it)) }
                data
            } else {
                emptyList()
            }
            val scanRequest = ScanRequest(rule = rule, planId = defaultScanPlan.id!!, metadata = metadata)
            val task = createTask(scanRequest, ScanTriggerType.PIPELINE, SecurityUtils.getUserId())

            weworkBotUrl?.let { scanTaskStatusChangedEventListener.setWeworkBotUrl(task.taskId, it, chatIds) }

            scanTaskScheduler.schedule(task)
            return task
        }
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun stopByPlanArtifactLatestSubtaskId(projectId: String, subtaskId: String): Boolean {
        val subtask = planArtifactLatestSubScanTaskDao.find(projectId, subtaskId)
            ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND)
        return subtask.latestSubScanTaskId?.let { stopSubtask(subtask.projectId, it) } ?: false
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun stopSubtask(projectId: String, subtaskId: String): Boolean {
        val subtask = subScanTaskDao.find(projectId, subtaskId)
            ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND)
        val userId = SecurityUtils.getUserId()
        return updateScanTaskResult(subtask, SubScanTaskStatus.STOPPED.name, emptyMap(), userId)
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun stopTask(projectId: String, taskId: String): Boolean {
        val task = scanTaskDao.findByProjectIdAndId(projectId, taskId)
            ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND)
        if (ScanTaskStatus.finishedStatus(task.status)) {
            return false
        }

        // 设置停止中的标记，中止提交子任务
        if (scanTaskDao.updateStatus(task.id!!, ScanTaskStatus.STOPPING, task.lastModifiedDate).modifiedCount == 1L) {
            val userId = SecurityUtils.getUserId()
            scannerMetrics.taskStatusChange(ScanTaskStatus.valueOf(task.status), ScanTaskStatus.STOPPING)
            // 延迟停止任务，让剩余子任务提交完
            val command = {
                subScanTaskDao.findByParentId(task.id).forEach {
                    updateScanTaskResult(it, SubScanTaskStatus.STOPPED.name, emptyMap(), userId)
                }
                scanTaskDao.updateStatus(task.id, ScanTaskStatus.STOPPED)
                scannerMetrics.taskStatusChange(ScanTaskStatus.valueOf(task.status), ScanTaskStatus.STOPPED)
            }
            scheduler.schedule(command, STOP_TASK_DELAY_SECONDS, TimeUnit.SECONDS)
            return true
        }
        return false
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun stopScanPlan(projectId: String, planId: String): Boolean {
        val unFinishedTasks = scanTaskDao.findUnFinished(projectId, planId)
        unFinishedTasks.forEach {
            stopTask(projectId, it.id!!)
        }
        return true
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun reportResult(reportResultRequest: ReportResultRequest) {
        with(reportResultRequest) {
            val subScanTask = subScanTaskDao.findById(subTaskId) ?: return
            logger.info("report result, parentTask[${subScanTask.parentScanTaskId}], subTask[$subTaskId]")
            val scanner = scannerService.get(subScanTask.scanner)
            // 对扫描结果去重
            scanExecutorResult?.distinctResult()
            val overview = scanExecutorResult?.let {
                scannerConverters[ScannerConverter.name(scanner.type)]!!.convertOverview(it)
            }
            // 更新扫描任务结果
            val updateScanTaskResultSuccess = updateScanTaskResult(
                subScanTask, scanStatus, overview ?: emptyMap()
            )

            // 没有扫描任务被更新或子扫描任务失败时直接返回
            if (!updateScanTaskResultSuccess || scanStatus != SubScanTaskStatus.SUCCESS.name) {
                return
            }

            // 统计任务耗时
            val now = LocalDateTime.now()
            val duration = Duration.between(subScanTask.startDateTime!!, now)
            scannerMetrics.record(subScanTask.fullPath, subScanTask.packageSize, subScanTask.scanner, duration)

            // 更新文件扫描结果
            fileScanResultDao.upsertResult(
                subScanTask.credentialsKey,
                subScanTask.sha256,
                subScanTask.parentScanTaskId,
                scanner,
                overview!!,
                subScanTask.startDateTime,
                now
            )

            // 保存详细扫描结果
            val resultManager = scanExecutorResultManagers[scanner.type]
            resultManager?.save(subScanTask.credentialsKey, subScanTask.sha256, scanner, scanExecutorResult!!)
        }
    }

    /**
     * 更新任务状态
     *
     * @return 是否更新成功
     */
    @Suppress("UNCHECKED_CAST")
    @Transactional(rollbackFor = [Throwable::class])
    fun updateScanTaskResult(
        subTask: TSubScanTask,
        resultSubTaskStatus: String,
        overview: Map<String, Any?>,
        modifiedBy: String? = null
    ): Boolean {
        val subTaskId = subTask.id!!
        val parentTaskId = subTask.parentScanTaskId
        // 任务已扫描过，重复上报直接返回
        if (subScanTaskDao.deleteById(subTaskId).deletedCount != 1L) {
            return false
        }
        // 子任务执行结束后唤醒项目另一个子任务
        scanTaskScheduler.notify(subTask.projectId)

        // 质量规则检查结果
        val planId = subTask.planId
        if (logger.isDebugEnabled) {
            logger.debug("planId:$planId, overview:${overview.toJsonString()}")
        }
        val qualityPass = if (planId != null && overview.isNotEmpty()) {
            scanQualityService.checkScanQualityRedLine(planId, overview as Map<String, Number>)
        } else {
            null
        }
        archiveSubScanTaskDao.save(
            TArchiveSubScanTask.from(
                subTask, resultSubTaskStatus, overview, qualityPass = qualityPass, modifiedBy = modifiedBy
            )
        )
        planArtifactLatestSubScanTaskDao.updateStatus(
            latestSubScanTaskId = subTaskId,
            subtaskScanStatus = resultSubTaskStatus,
            overview = overview,
            modifiedBy = modifiedBy,
            qualityPass = qualityPass
        )
        publisher.publishEvent(
            SubtaskStatusChangedEvent(
                SubScanTaskStatus.valueOf(subTask.status),
                TPlanArtifactLatestSubScanTask.convert(
                    subTask, resultSubTaskStatus, overview, qualityPass = qualityPass
                )
            )
        )

        scannerMetrics.subtaskStatusChange(
            SubScanTaskStatus.valueOf(subTask.status), SubScanTaskStatus.valueOf(resultSubTaskStatus)
        )
        logger.info("updating scan result, parentTask[$parentTaskId], subTask[$subTaskId][$resultSubTaskStatus]")

        // 更新父任务扫描结果
        val scanSuccess = resultSubTaskStatus == SubScanTaskStatus.SUCCESS.name
        val passCount = if (qualityPass == true) {
            1L
        } else {
            0L
        }
        scanTaskDao.updateScanResult(parentTaskId, 1, overview, scanSuccess, passCount = passCount)
        if (scanTaskDao.taskFinished(parentTaskId).modifiedCount == 1L) {
            scannerMetrics.incTaskCountAndGet(ScanTaskStatus.FINISHED)
            val scanPlan = planId?.let { scanPlanDao.findById(it) }
            val event = ScanTaskStatusChangedEvent(
                ScanTaskStatus.SCANNING_SUBMITTED,
                Converter.convert(scanTaskDao.findById(parentTaskId)!!, scanPlan, compatible = false)
            )
            publisher.publishEvent(event)
            logger.info("scan finished, task[$parentTaskId]")
        }
        return true
    }

    override fun pull(): SubScanTask? {
        return pullSubScanTask()?.let {
            val parentTask = scanTaskDao.findById(it.parentScanTaskId)!!
            return Converter.convert(it, scannerService.get(it.scanner), parentTask.metadata)
        }
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun updateSubScanTaskStatus(subScanTaskId: String, subScanTaskStatus: String): Boolean {
        if (subScanTaskStatus == SubScanTaskStatus.EXECUTING.name) {
            val subScanTask = subScanTaskDao.findById(subScanTaskId) ?: return false
            // 已经是正在执行的状态了，直接返回
            if (subScanTask.status == SubScanTaskStatus.EXECUTING.name) {
                return false
            }

            val oldStatus = SubScanTaskStatus.valueOf(subScanTask.status)
            val scanner = scannerService.get(subScanTask.scanner)
            val maxScanDuration = scanner.maxScanDuration(subScanTask.packageSize)
            // 多加1分钟，避免执行器超时后正在上报结果又被重新触发
            val timeoutDateTime = LocalDateTime.now().plus(maxScanDuration, ChronoUnit.MILLIS).plusMinutes(1L)
            val updateResult = subScanTaskDao.updateStatus(
                subScanTaskId, SubScanTaskStatus.EXECUTING, oldStatus, subScanTask.lastModifiedDate, timeoutDateTime
            )
            val modified = updateResult.modifiedCount == 1L
            if (modified) {
                archiveSubScanTaskDao.save(TArchiveSubScanTask.from(subScanTask, SubScanTaskStatus.EXECUTING.name))
                scannerMetrics.subtaskStatusChange(oldStatus, SubScanTaskStatus.EXECUTING)
                // 更新任务实际开始扫描的时间
                scanTaskDao.updateStartedDateTimeIfNotExists(subScanTask.parentScanTaskId, LocalDateTime.now())
                publisher.publishEvent(
                    SubtaskStatusChangedEvent(
                        SubScanTaskStatus.valueOf(subScanTask.status),
                        TPlanArtifactLatestSubScanTask.convert(subScanTask, SubScanTaskStatus.EXECUTING.name)
                    )
                )
            }
            return modified
        }
        return false
    }

    override fun get(subtaskId: String): SubScanTask {
        return subScanTaskDao.findById(subtaskId)?.let {
            Converter.convert(it, scannerService.get(it.scanner))
        } ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, subtaskId)
    }

    // TODO 添加消息队列后开启定时任务
    // @Scheduled(fixedDelay = FIXED_DELAY, initialDelay = FIXED_DELAY)
    fun enqueueTimeoutSubTask() {
        val subtask = pullSubScanTask() ?: return
        val enqueued = scanTaskScheduler.schedule(Converter.convert(subtask, scannerService.get(subtask.scanner)))
        if (!enqueued) {
            return
        }
        val oldStatus = SubScanTaskStatus.valueOf(subtask.status)
        val updateResult =
            subScanTaskDao.updateStatus(subtask.id!!, SubScanTaskStatus.ENQUEUED, oldStatus, subtask.lastModifiedDate)
        if (updateResult.modifiedCount == 1L) {
            archiveSubScanTaskDao.save(TArchiveSubScanTask.from(subtask, SubScanTaskStatus.ENQUEUED.name))
            scannerMetrics.subtaskStatusChange(oldStatus, SubScanTaskStatus.ENQUEUED)
        }
    }

    @Scheduled(fixedDelay = FIXED_DELAY, initialDelay = FIXED_DELAY)
    @Transactional(rollbackFor = [Throwable::class])
    fun enqueueTimeoutTask() {
        val task = scanTaskDao.timeoutTask(DEFAULT_TASK_EXECUTE_TIMEOUT_SECONDS) ?: return
        // 任务超时后移除所有子任务，重置状态后重新提交执行
        val resetTask = scanTaskDao.resetTask(task.id!!, task.lastModifiedDate)
        if (resetTask != null) {
            subScanTaskDao.deleteByParentTaskId(task.id)
            archiveSubScanTaskDao.deleteByParentTaskId(task.id)
            scannerMetrics.taskStatusChange(ScanTaskStatus.valueOf(task.status), ScanTaskStatus.PENDING)
            val plan = task.planId?.let { scanPlanDao.get(it) }
            scanTaskScheduler.schedule(Converter.convert(resetTask, plan, compatible = false))
        }
    }

    /**
     * 结束处于blocked状态超时的子任务
     */
    @Scheduled(fixedDelay = FIXED_DELAY, initialDelay = FIXED_DELAY)
    fun finishBlockTimeoutSubScanTask() {
        subScanTaskDao.blockedTimeoutTasks(DEFAULT_TASK_EXECUTE_TIMEOUT_SECONDS).records.forEach { subtask ->
            logger.info("subTask[${subtask.id}] of parentTask[${subtask.parentScanTaskId}] block timeout")
            self.updateScanTaskResult(subtask, SubScanTaskStatus.BLOCK_TIMEOUT.name, emptyMap())
        }
    }

    fun pullSubScanTask(): TSubScanTask? {
        var count = 0
        while (true) {
            // 优先返回待执行任务，再返回超时任务
            val task = subScanTaskDao.firstTaskByStatusIn(listOf(SubScanTaskStatus.CREATED.name))
                ?: subScanTaskDao.firstTimeoutTask(DEFAULT_TASK_EXECUTE_TIMEOUT_SECONDS)
                ?: return null

            // 处于执行中的任务，而且任务执行了最大允许的次数，直接设置为失败
            if (task.status == SubScanTaskStatus.EXECUTING.name && task.executedTimes >= DEFAULT_MAX_EXECUTE_TIMES) {
                logger.info("subTask[${task.id}] of parentTask[${task.parentScanTaskId}] exceed max execute times")
                self.updateScanTaskResult(task, SubScanTaskStatus.TIMEOUT.name, emptyMap())
                continue
            }

            // 更新任务，更新成功说明任务没有被其他扫描执行器拉取过，可以返回
            val oldStatus = SubScanTaskStatus.valueOf(task.status)
            val updateResult =
                subScanTaskDao.updateStatus(task.id!!, SubScanTaskStatus.PULLED, oldStatus, task.lastModifiedDate)
            if (updateResult.modifiedCount != 0L) {
                archiveSubScanTaskDao.save(TArchiveSubScanTask.from(task, SubScanTaskStatus.PULLED.name))
                scannerMetrics.subtaskStatusChange(oldStatus, SubScanTaskStatus.PULLED)
                return task
            }

            // 超过最大允许重试次数后说明当前冲突比较严重，有多个扫描器在拉任务，直接返回null
            if (++count >= MAX_RETRY_PULL_TASK_TIMES) {
                return null
            }
        }
    }

    private fun createTask(scanRequest: ScanRequest, triggerType: ScanTriggerType, userId: String?): ScanTask {
        with(scanRequest) {
            if (planId == null && (scanner == null || rule == null)) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID)
            }

            val plan = planId?.let { scanPlanDao.get(it) }
            val projectId = projectId(rule, plan)
            val repoNames = RuleUtil.getRepoNames(rule)

            // 校验权限
            if (userId != null) {
                if (repoNames.isEmpty()) {
                    permissionCheckHandler.checkProjectPermission(projectId, PermissionAction.MANAGE, userId)
                } else {
                    permissionCheckHandler.checkReposPermission(projectId, repoNames, PermissionAction.READ, userId)
                }
            }

            val rule = RuleConverter.convert(rule, plan?.type, projectId)
            val scanner = scannerService.get(scanner ?: plan!!.scanner)
            val now = LocalDateTime.now()
            val scanTask = scanTaskDao.save(
                TScanTask(
                    createdBy = userId ?: SecurityUtils.getUserId(),
                    createdDate = now,
                    lastModifiedBy = userId ?: SecurityUtils.getUserId(),
                    lastModifiedDate = now,
                    name = scanTaskName(triggerType, metadata),
                    rule = rule.toJsonString(),
                    triggerType = triggerType.name,
                    planId = plan?.id,
                    projectId = projectId,
                    status = ScanTaskStatus.PENDING.name,
                    total = 0L,
                    scanning = 0L,
                    failed = 0L,
                    scanned = 0L,
                    passed = 0L,
                    scanner = scanner.name,
                    scannerType = scanner.type,
                    scannerVersion = scanner.version,
                    scanResultOverview = emptyMap(),
                    metadata = metadata
                )
            ).run { Converter.convert(this, plan, force, false) }
            plan?.id?.let { scanPlanDao.updateLatestScanTaskId(it, scanTask.taskId) }
            scannerMetrics.incTaskCountAndGet(ScanTaskStatus.PENDING)
            logger.info("create scan task[${scanTask.taskId}] success")
            return scanTask
        }
    }

    private fun scanTaskName(triggerType: ScanTriggerType, metadata: List<TaskMetadata>): String {
        return when (triggerType) {
            ScanTriggerType.PIPELINE -> {
                val metadataMap = metadata.associateBy { it.key }
                val pipelineName = metadataMap[TASK_METADATA_PIPELINE_NAME]?.value ?: ""
                val buildNo = metadataMap[TASK_METADATA_BUILD_NUMBER]?.value ?: ""
                val pluginName = metadataMap[TASK_METADATA_PLUGIN_NAME]?.value ?: ""
                "$pipelineName-$buildNo-$pluginName"
            }

            ScanTriggerType.MANUAL_SINGLE -> getLocalizedMessage(ScannerMessageCode.SCAN_TASK_NAME_SINGLE_SCAN)
            else -> getLocalizedMessage(ScannerMessageCode.SCAN_TASK_NAME_BATCH_SCAN)
        }
    }

    private fun projectId(rule: Rule?, plan: TScanPlan?): String {
        // 尝试从rule取projectId，不存在时从plan中取projectId
        val projectIds = RuleUtil.getProjectIds(rule)
        return if (projectIds.size == 1) {
            projectIds.first()
        } else if (projectIds.isEmpty() && plan != null) {
            plan.projectId
        } else {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID)
        }
    }

    companion object {
        private val scheduler = Executors.newScheduledThreadPool(200)

        /**
         * 延迟停止任务时间
         */
        private const val STOP_TASK_DELAY_SECONDS = 2L

        /**
         * 默认任务最长执行时间，超过后会触发重试
         */
        private const val DEFAULT_TASK_EXECUTE_TIMEOUT_SECONDS = 1200L

        /**
         * 最大允许重复执行次数
         */
        private const val DEFAULT_MAX_EXECUTE_TIMES = 3

        /**
         * 最大允许的拉取任务重试次数
         */
        private const val MAX_RETRY_PULL_TASK_TIMES = 3

        /**
         * 定时扫描超时任务入队
         */
        private const val FIXED_DELAY = 3000L
    }
}
