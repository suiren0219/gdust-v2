package com.menglimi.gdust.v2.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.menglimi.gdust.v2.data.Store
import com.menglimi.gdust.v2.ui.schedule.ScheduleTimes
import kotlinx.coroutines.CancellationException
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** 本机缓存心跳与课程边界共用任务；刷新不依赖联网或提醒开关。 */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!WidgetRefresher.hasWidgets(applicationContext)) {
            WidgetRefreshScheduler.ensureScheduled(applicationContext)
            return Result.success()
        }
        return try {
            if (inputData.getBoolean("clock_changed", false)) {
                WidgetRefreshScheduler.ensureScheduled(applicationContext, timeChanged = true)
            }
            WidgetRefresher.updateAll(applicationContext)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

object WidgetRefreshScheduler {
    private const val WORK_NAME = "gdust_widget_cache_refresh"
    private const val BOUNDARY_TAG = "gdust_widget_boundary"

    /** 周期心跳保留；下一次课程边界按时间去重，运行中的任务不会取消自己。 */
    fun ensureScheduled(context: Context, timeChanged: Boolean = false) {
        val app = context.applicationContext
        val workManager = WorkManager.getInstance(app)
        if (timeChanged) workManager.cancelAllWorkByTag(BOUNDARY_TAG)
        if (WidgetRefresher.hasWidgets(app)) {
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES)
                    .setInitialDelay(15, TimeUnit.MINUTES)
                    .build(),
            )
            val store = Store(app)
            val termStart = ScheduleTimes.parseDate(runCatching { store.loadCalendar()?.startDate }.getOrNull())
                ?: ScheduleTimes.parseDate(app.getSharedPreferences("gdust_cache", Context.MODE_PRIVATE).getString("term_start", null))
            val now = LocalDateTime.now()
            val next = WidgetRefreshPlanner.nextRefreshAt(
                runCatching { store.loadBlocks().orEmpty() }.getOrDefault(emptyList()), termStart, now,
            ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            workManager.enqueueUniqueWork(
                "${BOUNDARY_TAG}_$next", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                    .addTag(BOUNDARY_TAG)
                    .setInitialDelay((next - System.currentTimeMillis()).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                    .build(),
            )
        } else {
            workManager.cancelUniqueWork(WORK_NAME)
            workManager.cancelAllWorkByTag(BOUNDARY_TAG)
        }
    }
}

/** 开机、升级、日期或时区变化后立即重读缓存，并重排本机刷新。 */
class WidgetClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED)) return
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "gdust_widget_clock_refresh", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setInputData(workDataOf("clock_changed" to true))
                .build(),
        )
    }
}
