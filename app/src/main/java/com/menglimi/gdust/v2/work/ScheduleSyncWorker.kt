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
        // 没有任何登录/会话就别打扰
        val hasDirect = repo.store.casTicket.isNotBlank()
        if (!hasDirect && !repo.hasTeachingSession()) return Result.success()

        return runCatching {
            if (hasDirect) {
                // 直连：先复用 Cookie，必要时用加密票据续租
                repo.syncTermWithLogin()
            } else {
                // 只有网页登录会话：走 WebView CookieManager 的会话
                repo.syncTermWithCookies()
            }
            com.menglimi.gdust.v2.widget.TodayWidget().refreshAll(applicationContext)
            Result.success()
        }.getOrElse { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            // 只有认证类失败才提醒；网络波动静默，下个周期再试
            val msg = e.message.orEmpty()
            if (e is com.menglimi.gdust.v2.data.AuthFailed ||
                msg.contains("重新登录") || msg.contains("会话已失效")
            ) {
                notify("课表自动同步暂停", "教务会话已过期（${msg.take(60)}）。打开 App，用账号密码重登或网页登录一次即可恢复自动同步。")
            }
            Result.success()
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
