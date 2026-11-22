package com.menglimi.gdust.v2.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.menglimi.gdust.v2.App
import com.menglimi.gdust.v2.R
import com.menglimi.gdust.v2.data.AuthFailed
import com.menglimi.gdust.v2.widget.WidgetRefresher
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** 无自动电量任务时的 4 小时课表同步兜底；其余时候复用电量 chain。 */
class ScheduleSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        try {
            syncIfDue(applicationContext)
        } finally {
            if (!isStopped) PowerWorker.scheduleAfterRun(applicationContext, powerChain = false,
                generation = inputData.getString(GENERATION))
        }
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "gdust_schedule_sync"
        private const val CHAIN = "gdust_schedule_sync_chain"
        private const val LEGACY_PERIODIC = "gdust_schedule_sync"
        private const val GENERATION = "generation"
        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        private fun preferences(ctx: Context) = ctx.getSharedPreferences(CHANNEL, Context.MODE_PRIVATE)

        /** 前台、独立 Worker 与电量 Worker 共用；网络失败静默，不影响电量采样。 */
        suspend fun syncIfDue(ctx: Context) {
            try {
                val repo = (ctx.applicationContext as App).repo
                if (repo.syncTermSilentlyIfDue() != null) {
                    preferences(ctx).edit().remove("auth_notified").apply()
                    WidgetRefresher.refreshAll(ctx)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: AuthFailed) {
                notifyAuthenticationRequired(ctx)
            } catch (_: Exception) {
                // Repository 保留已有课表，并记录失败退避；不叠加 WorkManager 短周期重试。
            }
        }

        private fun notifyAuthenticationRequired(ctx: Context) {
            try {
                val prefs = preferences(ctx)
                if (prefs.getBoolean("auth_notified", false)) return
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "课表自动同步", NotificationManager.IMPORTANCE_LOW),
                )
                if (!nm.areNotificationsEnabled()) return
                val pi = android.app.PendingIntent.getActivity(
                    ctx, 1, android.content.Intent(ctx, com.menglimi.gdust.v2.MainActivity::class.java),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                )
                val text = "学校会话已失效。打开 App，重新完成一次学校扫码登录即可恢复自动同步；本地课表仍保留。"
                nm.notify(11, NotificationCompat.Builder(ctx, CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setContentTitle("课表自动同步暂停").setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(pi).setAutoCancel(true).setOnlyAlertOnce(true).build())
                prefs.edit().putBoolean("auth_notified", true).apply()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // 无通知权限或发送失败不能让学校同步、电量采样失败。
            }
        }

        // 公共入口只向电量调度器委托；内部独立任务接口不再反调，避免 schedule/cancel 递归。
        fun schedule(ctx: Context) = PowerWorker.schedule(ctx)

        /** 调用方先关闭自动同步或清除学校会话，再重排；原电量计划保留。 */
        fun cancel(ctx: Context) = PowerWorker.schedule(ctx)

        internal fun isCurrentRun(ctx: Context, generation: String?): Boolean = generation != null &&
            generation == preferences(ctx).getString(GENERATION, null)

        /** 仅由电量调度器持有 scheduleLock 时调用，独立 chain 按 Repository 的 4h/退避到期点续接。 */
        internal fun scheduleStandalone(ctx: Context, policy: ExistingWorkPolicy): Long? {
            val wm = WorkManager.getInstance(ctx)
            wm.cancelUniqueWork(LEGACY_PERIODIC)
            val now = System.currentTimeMillis()
            val at = (ctx.applicationContext as App).repo.nextAutomaticSyncAt(now)
            if (at == null) {
                cancelStandalone(ctx)
                return null
            }
            val prefs = preferences(ctx)
            val generation = prefs.getString(GENERATION, null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(GENERATION, it).apply()
            }
            wm.enqueueUniqueWork(CHAIN, policy, OneTimeWorkRequestBuilder<ScheduleSyncWorker>()
                .setInputData(workDataOf(GENERATION to generation))
                .setInitialDelay((at - now).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .setConstraints(constraints).build())
            return at
        }

        internal fun cancelStandalone(ctx: Context) {
            val wm = WorkManager.getInstance(ctx)
            wm.cancelUniqueWork(CHAIN)
            wm.cancelUniqueWork(LEGACY_PERIODIC)
            preferences(ctx).edit().remove(GENERATION).apply()
        }
    }
}
