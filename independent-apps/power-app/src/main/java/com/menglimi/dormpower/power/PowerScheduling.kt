package com.menglimi.dormpower.power

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.menglimi.power.PowerMonitor
import com.menglimi.power.PowerQueryPreset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object PowerScheduling {
    private const val WORK_NAME = "dorm_power_periodic_v1"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val schedulingMutex = Mutex()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()

    /** Application 可直接调用；只在 IO 上加载配置并补排任务，不请求电费服务。 */
    fun ensure(context: Context) {
        val application = context.applicationContext
        scope.launch {
            try {
                schedulingMutex.withLock {
            val manager = WorkManager.getInstance(application)
            val operation = if (config.binding?.isValid != true || config.preset == PowerQueryPreset.MANUAL) {
                manager.cancelUniqueWork(WORK_NAME)
            } else {
                val request = PeriodicWorkRequestBuilder<PowerWorker>(15, TimeUnit.MINUTES)
                    .setInitialDelay(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
                // 固定巡检周期；实际查询间隔由 core policy 决定，不因开 App 而重新计时。
                manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }
            operation.result.get()
            val manual = readPrefs.getBoolean("widget_refresh_running", false)
            if (manual && now - readPrefs.getLong("widget_refresh_started_at", 0L) >= MANUAL_REFRESH_STALE_MS) {
                workManager.cancelUniqueWork(MANUAL_REFRESH_WORK)
                readPrefs.edit().putBoolean("widget_refresh_running", false)
                    .putString("widget_refresh_status", "刷新任务超时，请手动重试")
                    .remove("widget_refresh_started_at").remove("widget_refresh_request_id").apply()
            }
            mutableError.value = null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 保留配置与历史，不记录绑定/异常正文；设置页可明确重试。
                mutableError.value = "后台任务更新失败，请重试。手动刷新仍可使用。"
            }
        }
    }
}

class PowerWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            currentCoroutineContext().ensureActive()
            val store = PowerStore(applicationContext)
            val config = store.config()
            if (config.binding?.isValid != true || config.preset == PowerQueryPreset.MANUAL) return@withContext Result.success()
            val notifications = PowerNotifications(applicationContext)
            val jobContext = currentCoroutineContext()
            PowerMonitor(store.monitorStore(background = true), onWarning = { warning ->
                jobContext.ensureActive()
                notifications.send(warning)
            }).query(background = true)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // core 已记录请求时间；等待下一次周期巡检，由 policy 节流失败请求。
            Result.success()
        }
    }
}
