package com.menglimi.dormpower.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.menglimi.dormpower.power.PowerNotifications
import com.menglimi.dormpower.power.PowerStore
import com.menglimi.power.PowerMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * 小部件手动刷新：只由用户点按触发，不受"后台自动采集"授权影响。
 * 强制查询走 PowerMonitor 的单次详情请求；同一 JVM 内的前台刷新会与它共享 queryMutex，不会重复请求。
 */
class PowerWidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext
        try {
            currentCoroutineContext().ensureActive()
            val store = PowerStore(app)
            if (store.config().binding?.isValid != true) {
                PowerWidgetState.markMessage(app, "先绑定宿舍")
                return@withContext Result.success()
            }
            val notifications = PowerNotifications(app)
            val jobContext = currentCoroutineContext()
            PowerMonitor(store.monitorStore(background = false), onWarning = { warning ->
                jobContext.ensureActive()
                notifications.send(warning)
            }).query(force = true)
            PowerWidgetState.clear(app)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 失败保留上次读数与采样；小部件只提示刷新失败，不冒充新数据。
            PowerWidgetState.markFailed(app)
            Result.success()
        } finally {
            PowerWidgetUpdater.updateAll(app)
        }
    }

    companion object {
        private const val WORK_NAME = "power_widget_manual_refresh"

        /** 已绑定才排队；重复点按复用同一个任务，避免高频请求电费服务。 */
        fun request(context: Context): Boolean {
            val app = context.applicationContext
            val bound = runCatching { PowerStore(app).config().binding?.isValid == true }.getOrDefault(false)
            if (!bound) {
                PowerWidgetState.markMessage(app, "先绑定宿舍")
                return false
            }
            PowerWidgetState.markRunning(app)
            val work = OneTimeWorkRequestBuilder<PowerWidgetRefreshWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(app).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, work)
            return true
        }
    }
}
