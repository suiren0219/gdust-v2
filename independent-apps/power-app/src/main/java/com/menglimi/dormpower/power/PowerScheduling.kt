package com.menglimi.dormpower.power

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.common.util.concurrent.ListenableFuture
import com.menglimi.power.PowerApi
import com.menglimi.power.PowerDetailSource
import com.menglimi.power.PowerMonitor
import com.menglimi.power.PowerWarning
import java.io.IOException
import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val BINDING_REVISION = "binding_revision"

/** 旧队列名只用于升级时取消，不再创建周期任务。 */
internal data class PowerWorkNames(
    val periodic: String = "dorm_power_periodic_v1",
    val catchUp: String = "dorm_power_catch_up_v1",
    val bootQuery: String = "dorm_power_boot_query_v1",
)

object PowerScheduling {
    private const val BOOT_QUERY_PREFERENCES = "power_boot_query_v1"
    private const val BOOT_QUERY_ENABLED = "enabled"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val schedulingMutex = Mutex()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()

    fun bootQueryEnabled(context: Context): Boolean = runCatching {
        context.applicationContext.getSharedPreferences(BOOT_QUERY_PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(BOOT_QUERY_ENABLED, false)
    }.getOrDefault(false)

    /** 普通启动只清理旧任务，不触发查询。 */
    fun ensure(context: Context): Job = scope.launch { synchronize(context.applicationContext) }

    /** 开关只保存用户选择，不立即发起查询。 */
    fun setBootQueryEnabled(context: Context, enabled: Boolean): Job = scope.launch {
        synchronize(context.applicationContext, preference = enabled)
    }

    /** 只由开机或本应用覆盖更新广播调用。 */
    internal suspend fun restore(context: Context) = synchronize(context.applicationContext, startupEvent = true)

    private suspend fun synchronize(context: Context, startupEvent: Boolean = false, preference: Boolean? = null) {
        try {
            withContext(Dispatchers.IO) {
                schedulingMutex.withLock {
                    if (preference != null && !context.getSharedPreferences(BOOT_QUERY_PREFERENCES, Context.MODE_PRIVATE)
                            .edit().putBoolean(BOOT_QUERY_ENABLED, preference).commit()) {
                        throw IOException("开机查询设置保存失败")
                    }
                    reconcileLocked(WorkManager.getInstance(context), PowerStore(context), PowerWorkNames(),
                        startupEvent, bootQueryEnabled(context))
                    mutableError.value = null
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableError.value = "无法更新开机查询设置，手动刷新仍可使用。"
        }
    }

    /** 测试以独立队列和仓库驱动同一调度路径。 */
    internal suspend fun reconcile(
        manager: WorkManager,
        repository: PowerRepository,
        names: PowerWorkNames,
        startupEvent: Boolean,
        enabled: Boolean,
    ) = withContext(Dispatchers.IO) {
        schedulingMutex.withLock { reconcileLocked(manager, repository, names, startupEvent, enabled) }
    }

    private suspend fun reconcileLocked(
        manager: WorkManager,
        repository: PowerRepository,
        names: PowerWorkNames,
        startupEvent: Boolean,
        enabled: Boolean,
    ) {
        manager.cancelUniqueWork(names.periodic).result.awaitResult()
        manager.cancelUniqueWork(names.catchUp).result.awaitResult()
        val snapshot = if (enabled) repository.snapshot() else null
        if (snapshot == null || !PowerBackgroundPolicy.isBootQueryEligible(snapshot.config)) {
            manager.cancelUniqueWork(names.bootQuery).result.awaitResult()
        } else if (startupEvent) {
            val request = OneTimeWorkRequestBuilder<PowerBootQueryWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(BINDING_REVISION to snapshot.bindingRevision))
                .build()
            manager.enqueueUniqueWork(names.bootQuery, ExistingWorkPolicy.KEEP, request).result.awaitResult()
        }
    }
}

/** 只执行一次查询；使用原有加密存储、绑定版本保护和查询互斥。 */
internal class PowerBackgroundRunner(
    private val repository: PowerRepository,
    private val source: PowerDetailSource,
    private val diagnostic: PowerBackgroundDiagnostics,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val onWarning: (PowerWarning) -> Boolean = { false },
    private val timeoutMs: Long = 120_000L,
) {
    suspend fun run(expectedRevision: Long, canQuery: () -> Boolean): PowerBackgroundOutcome {
        val startedAt = clock.millis()
        val startedNanos = System.nanoTime()
        fun finish(outcome: PowerBackgroundOutcome, reason: String): PowerBackgroundOutcome {
            val finishedAt = clock.millis()
            val duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos).coerceAtLeast(0L)
            diagnostic.safelyUpdate { previous ->
                previous.copy(lastRunAt = startedAt, lastOutcome = outcome, lastFinishedAt = finishedAt,
                    lastSuccessAt = if (outcome == PowerBackgroundOutcome.SUCCESS) finishedAt else previous.lastSuccessAt,
                    lastFailureAt = if (outcome == PowerBackgroundOutcome.FAILURE) finishedAt else previous.lastFailureAt,
                    lastSkippedAt = if (outcome == PowerBackgroundOutcome.SKIPPED) finishedAt else previous.lastSkippedAt,
                    durationMs = duration, outcomeExplanation = reason)
            }
            return outcome
        }
        try {
            if (!canQuery()) return finish(PowerBackgroundOutcome.SKIPPED, "开机查询已关闭")
            val snapshot = repository.snapshot()
            if (snapshot.bindingRevision != expectedRevision || !PowerBackgroundPolicy.isBootQueryEligible(snapshot.config)) {
                return finish(PowerBackgroundOutcome.SKIPPED, "宿舍未绑定或绑定已变更")
            }
            diagnostic.safelyUpdate { it.copy(lastRunAt = startedAt, lastOutcome = PowerBackgroundOutcome.RUNNING,
                durationMs = 0L, outcomeExplanation = "正在查询开机电量") }
            val result = withTimeoutOrNull(timeoutMs.coerceAtLeast(1L)) {
                val jobContext = currentCoroutineContext()
                val guardedSource = PowerDetailSource { binding ->
                    if (!canQuery() || repository.snapshot().bindingRevision != expectedRevision ||
                        binding != snapshot.config.binding) {
                        throw CancellationException("开机查询设置或绑定已变更")
                    }
                    val detail = source.detail(binding)
                    jobContext.ensureActive()
                    if (!canQuery()) throw CancellationException("开机查询已关闭")
                    detail
                }
                // 显式开机开关独立于旧的「手动」预设，复用手动刷新同样的绑定事务。
                PowerMonitor(repository.monitorStore(), guardedSource, clock, onWarning = {
                    jobContext.ensureActive()
                    if (canQuery()) onWarning(it) else false
                }).query(force = true)
            }
            currentCoroutineContext().ensureActive()
            return when {
                result == null -> finish(PowerBackgroundOutcome.FAILURE, "开机查询超时，保留上次成功记录")
                result.sampled -> finish(PowerBackgroundOutcome.SUCCESS, "已保存本次开机电量记录")
                else -> finish(PowerBackgroundOutcome.SKIPPED, "已有更新的读数，未重复采样")
            }
        } catch (cancelled: CancellationException) {
            finish(PowerBackgroundOutcome.CANCELLED, "开机查询已取消")
            throw cancelled
        } catch (_: Exception) {
            return finish(PowerBackgroundOutcome.FAILURE, "开机查询或保存失败，保留上次成功记录")
        }
    }
}

class PowerBootQueryWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val application = applicationContext
        if (!PowerScheduling.bootQueryEnabled(application)) return@withContext Result.success()
        val revision = inputData.getLong(BINDING_REVISION, -1L)
        if (revision < 0L) return@withContext Result.success()
        val notifications = PowerNotifications(application)
        PowerBackgroundRunner(PowerStore(application), PowerApi(), PowerBackgroundStatusStore.diagnostics(application),
            onWarning = notifications::send).run(revision) { PowerScheduling.bootQueryEnabled(application) }
        // 不安排下一次工作，也不要求 WorkManager 自动重试。
        Result.success()
    }
}

private suspend fun <T> ListenableFuture<T>.awaitResult(): T = withTimeoutOrNull(5_000L) {
    suspendCancellableCoroutine { continuation ->
        addListener({
            if (continuation.isActive) {
                try { continuation.resume(get()) }
                catch (failure: Exception) { continuation.resumeWithException(failure) }
            }
        }, { it.run() })
    }
} ?: throw IOException("后台任务数据库操作超时")
