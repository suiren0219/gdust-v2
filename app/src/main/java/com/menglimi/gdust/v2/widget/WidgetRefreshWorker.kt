package com.menglimi.gdust.v2.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** 单一本地缓存心跳；无网络约束、无网络请求，不申请闹钟或其他权限。 */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!WidgetRefresher.hasWidgets(applicationContext)) {
            WidgetRefreshScheduler.ensureScheduled(applicationContext)
            return Result.success()
        }
        return try {
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

    /** KEEP 保留已有心跳，避免每次写缓存或新增一款时重置 15 分钟周期。 */
    fun ensureScheduled(context: Context) {
        val app = context.applicationContext
        val workManager = WorkManager.getInstance(app)
        if (WidgetRefresher.hasWidgets(app)) {
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES)
                    .setInitialDelay(15, TimeUnit.MINUTES)
                    .build(),
            )
        } else {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }
}
