package com.menglimi.gdust.v2.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.menglimi.gdust.v2.data.GdustApi
import com.menglimi.gdust.v2.data.Store
import com.menglimi.gdust.v2.work.PowerCollectionPolicy
import kotlinx.coroutines.CancellationException

/** 小部件专用手动查询：不要求后台自动采集授权，但只由用户点击启动。 */
class PowerWidgetManualRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val prefs = context.getSharedPreferences("gdust_power", Context.MODE_PRIVATE)
        val store = Store(context)
        val requestId = inputData.getString(REQUEST_ID).orEmpty()
        if (requestId.isBlank() || prefs.getString(ACTIVE_REQUEST_ID, null) != requestId) return Result.success()
        try {
            val account = store.powerAccount
            val code = store.powerCode
            val verify = store.powerRoomVerify
            if (!PowerCollectionPolicy.hasBindingConfig(account, code, verify)) {
                setStatusIfActive(prefs, requestId, "请先在宿舍电量页绑定房间")
                return Result.success()
            }
            val detail = GdustApi().powerDetail(account, code, verify)
            if (prefs.getString(ACTIVE_REQUEST_ID, null) != requestId) return Result.success()
            val odd = detail.odd ?: run {
                setStatusIfActive(prefs, requestId, "暂时没有读到电量，请稍后重试")
                return Result.success()
            }
            val now = System.currentTimeMillis()
            store.addSample(odd, now)
            prefs.edit()
                .putFloat("prev_odd", odd.toFloat())
                .putLong("last_sample", now)
                .putString(STATUS, "")
                .apply()
            return Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            setStatusIfActive(prefs, requestId, "刷新失败，请稍后重试")
            return Result.success()
        } finally {
            if (prefs.getString(ACTIVE_REQUEST_ID, null) == requestId) {
                prefs.edit().remove(ACTIVE_REQUEST_ID).putBoolean(RUNNING, false).remove(STARTED_AT).apply()
                WidgetRefresher.updatePower(context)
            }
        }
    }

    companion object {
        const val STATUS = "widget_refresh_status"
        const val RUNNING = "widget_refresh_running"
        private const val STARTED_AT = "widget_refresh_started_at"
        private const val WORK_NAME = "gdust_power_widget_manual_refresh"
        private const val ACTIVE_REQUEST_ID = "widget_refresh_request_id"
        private const val STALE_AFTER_MS = 3 * 60 * 1000L
        private const val REQUEST_ID = "request_id"

        private fun setStatusIfActive(prefs: android.content.SharedPreferences, requestId: String, status: String) {
            if (prefs.getString(ACTIVE_REQUEST_ID, null) == requestId) {
                prefs.edit().putString(STATUS, status).apply()
            }
        }

        suspend fun request(context: Context) {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences("gdust_power", Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val running = prefs.getBoolean(RUNNING, false)
            val startedAt = prefs.getLong(STARTED_AT, 0L)
            val stale = running && now - startedAt >= STALE_AFTER_MS
            if (stale) WorkManager.getInstance(app).cancelUniqueWork(WORK_NAME)
            if (running && !stale) {
                prefs.edit().putString(STATUS, "正在刷新…").apply()
                WidgetRefresher.updatePower(app)
                return
            }
            val store = Store(app)
            if (!PowerCollectionPolicy.hasBindingConfig(store.powerAccount, store.powerCode, store.powerRoomVerify)) {
                prefs.edit().putString(STATUS, "请先在宿舍电量页绑定房间").apply()
                WidgetRefresher.updatePower(app)
                return
            }
            val requestId = java.util.UUID.randomUUID().toString()
            prefs.edit().putBoolean(RUNNING, true).putLong(STARTED_AT, now)
                .putString(ACTIVE_REQUEST_ID, requestId)
                .putString(STATUS, "正在刷新…").apply()
            val request = OneTimeWorkRequestBuilder<PowerWidgetManualRefreshWorker>()
                .setInputData(Data.Builder().putString(REQUEST_ID, requestId).build())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            try {
                WorkManager.getInstance(app).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            } catch (_: Exception) {
                if (prefs.getString(ACTIVE_REQUEST_ID, null) == requestId) {
                    prefs.edit().remove(ACTIVE_REQUEST_ID).putBoolean(RUNNING, false).remove(STARTED_AT)
                        .putString(STATUS, "刷新任务未能启动").apply()
                }
            }
            WidgetRefresher.updatePower(app)
        }
    }
}
