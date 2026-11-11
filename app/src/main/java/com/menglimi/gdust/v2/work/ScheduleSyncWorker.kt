package com.menglimi.gdust.v2.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.menglimi.gdust.v2.App
import com.menglimi.gdust.v2.R
import java.util.concurrent.TimeUnit

/**
 * 定时同步课表：会话仍有效就直接重新拉整学期（调课、换教室都能跟上）；
 * 会话失效则发通知提示重登一次，不用旧数据冒充满新数据。
 */
class ScheduleSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val repo = (applicationContext as App).repo
        // 没登录过 / 没有任何票据就别打扰
        if (!repo.store.hasSession && repo.store.casTicket.isBlank()) return Result.success()

        val hasCookies = repo.teachingCookies.hasSessionFor("172.16.254.1") ||
            repo.teachingCookies.hasSessionFor("gkjx.gdust.edu.cn") ||
            repo.teachingCookies.hasSessionFor("webvpn.gdust.edu.cn")

        return runCatching {
            if (hasCookies) {
                runCatching { repo.syncTermWithCookies() }
                    .recoverCatching {
                        // 会话过期：再用票据重换一次
                        repo.syncTermWithLogin()
                    }
                    .getOrThrow()
            } else {
                repo.syncTermWithLogin()
            }
            com.menglimi.gdust.v2.widget.TodayWidget().refreshAll(applicationContext)
            Result.success()
        }.getOrElse { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            // 认证类失败才提醒用户；网络波动静默，下次再试
            if (e is com.menglimi.gdust.v2.data.AuthFailed ||
                e.message?.contains("重新登录") == true ||
                e.message?.contains("会话已失效") == true
            ) {
                notify("课表自动同步暂停", "教务会话已过期，打开 App 重新登录一次即可恢复自动同步。")
            }
            Result.success()  // 不重试，等下一个周期
        }
    }

    private fun notify(title: String, text: String) {
        val ctx = applicationContext
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "课表自动同步", NotificationManager.IMPORTANCE_LOW),
        )
        val pi = android.app.PendingIntent.getActivity(
            ctx, 1, android.content.Intent(ctx, com.menglimi.gdust.v2.MainActivity::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        nm.notify(11, NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi).setAutoCancel(true).build())
    }

    companion object {
        private const val CHANNEL = "gdust_schedule_sync"

        fun schedule(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "gdust_schedule_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ScheduleSyncWorker>(12, TimeUnit.HOURS).build(),
            )
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork("gdust_schedule_sync")
        }
    }
}
