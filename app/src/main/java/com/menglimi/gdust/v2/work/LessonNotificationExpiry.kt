package com.menglimi.gdust.v2.work

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.menglimi.gdust.v2.data.Store
import java.util.UUID
import java.util.concurrent.TimeUnit

/** 与勿扰/Activity 无关的课末清理；系统超时 + 静态闹钟 + 持久 Worker。 */
object LessonNotificationExpiry {
    private const val PREFS = "gdust_lesson_notification_expiry"
    private const val ACTION = "com.menglimi.gdust.v2.LESSON_NOTIFICATION_EXPIRE"
    internal const val EXTRA_TOKEN = "gdust_lesson_token"
    private val ids = listOf(21, 22, 23, 29)
    private val gson = Gson()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun manager(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)
    private fun workName(id: Int) = "gdust_lesson_notification_expiry_$id"
    private fun lease(ctx: Context, id: Int): LessonNotificationLease? =
        decodeLessonNotificationLease(prefs(ctx).getString(id.toString(), null), gson)

    /** 锁内先持久化发布代次，再通知系统；崩溃恢复也能识别对应通知，不能误撤销新一节。 */
    @Synchronized
    internal fun post(ctx: Context, id: Int, notification: Notification, expiresAtMillis: Long, event: ReminderEvent? = null) {
        val now = System.currentTimeMillis()
        if (expiresAtMillis <= now) return
        val token = UUID.randomUUID().toString()
        notification.extras.putString(EXTRA_TOKEN, token)
        val lease = LessonNotificationLease(
            token, expiresAtMillis, event?.kind,
            event?.block?.let { listOf(it) } ?: event?.nextDayBlocks.orEmpty(),
            notification.extras.containsKey("miui.focus.param"),
        )
        check(prefs(ctx).edit().putString(id.toString(), encodeLessonNotificationLease(lease, gson)).commit())
        manager(ctx).notify(id, notification)
        schedule(ctx, id, lease, now)
    }

    /** 重启、课表/开关变化、恢复广播及兜底入口均可调用；不要求勿扰授权。 */
    @Synchronized
    fun reschedule(ctx: Context, store: Store = Store(ctx), nowMillis: Long = System.currentTimeMillis()) {
        val nm = manager(ctx)
        val active = nm.activeNotifications.filter { it.tag == null }.associateBy { it.id }
        val allowed = nm.areNotificationsEnabled() &&
            nm.getNotificationChannel(LessonReminderWorker.CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
        val blocks = store.loadBlocks()
        for (id in ids) {
            val lease = lease(ctx, id)
            val visible = active[id]?.notification
            if (lease == null) {
                // 旧版未登记的课程/测试岛也不能在升级后无限留存。
                if (visible?.channelId == LessonReminderWorker.CHANNEL) nm.cancel(id)
                forget(ctx, id)
                continue
            }
            val matches = visible?.extras?.getString(EXTRA_TOKEN) == lease.token
            val keep = keepLessonNotification(
                lease, nowMillis, allowed, store.reminderEnabled, store.inClassReminder,
                store.nextDayReminder, store.islandEnabled, blocks,
            )
            if (!keep) {
                // notify() 入系统队列后未必立刻出现在 activeNotifications；关闭/到期仍须取消队列内的本代次。
                if (matches || visible == null) nm.cancel(id)
                forget(ctx, id)
            } else {
                // 更新通知也可能短暂读到旧 token；发布锁内的持久代次才是当前截止任务来源。
                schedule(ctx, id, lease, nowMillis)
            }
        }
    }

    /** 旧代次到期任务不操作新通知；提前/过期广播同样按真实时钟校准。 */
    @Synchronized
    internal fun expire(ctx: Context, id: Int, token: String, fromWorker: Boolean = false) {
        if (id !in ids) return
        val lease = lease(ctx, id) ?: return
        if (lease.token != token) return
        val now = System.currentTimeMillis()
        val nm = manager(ctx)
        val visibleToken = nm.activeNotifications.firstOrNull { it.id == id && it.tag == null }
            ?.notification?.extras?.getString(EXTRA_TOKEN)
        if (now < lease.expiresAtMillis) {
            schedule(ctx, id, lease, now, fromWorker)
        } else {
            if (canExpireLessonNotification(lease, token, visibleToken, now) || visibleToken == null) nm.cancel(id)
            forget(ctx, id, cancelWork = !fromWorker)
        }
    }

    @Synchronized
    fun cancelAll(ctx: Context) {
        for (id in ids) {
            manager(ctx).cancel(id)
            forget(ctx, id)
        }
    }

    private fun schedule(ctx: Context, id: Int, lease: LessonNotificationLease, nowMillis: Long, fromWorker: Boolean = false) {
        val alarm = ctx.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(ctx, id, lease.token)
        alarm.cancel(pi)
        runCatching {
            if (LessonReminderAlarms.canScheduleExact(ctx)) {
                try {
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, lease.expiresAtMillis, pi)
                } catch (_: SecurityException) {
                    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, lease.expiresAtMillis, pi)
                }
            } else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, lease.expiresAtMillis, pi)
        }
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            workName(id), if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<LessonNotificationExpiryWorker>()
                .setInputData(androidx.work.workDataOf("id" to id, EXTRA_TOKEN to lease.token))
                .setInitialDelay((lease.expiresAtMillis - nowMillis).coerceAtLeast(0), TimeUnit.MILLISECONDS).build(),
        )
    }

    private fun forget(ctx: Context, id: Int, cancelWork: Boolean = true) {
        prefs(ctx).edit().remove(id.toString()).commit()
        ctx.getSystemService(AlarmManager::class.java).cancel(pendingIntent(ctx, id, ""))
        if (cancelWork) WorkManager.getInstance(ctx).cancelUniqueWork(workName(id))
    }

    private fun pendingIntent(ctx: Context, id: Int, token: String) = PendingIntent.getBroadcast(
        ctx, id, Intent(ctx, LessonNotificationExpiryReceiver::class.java).setAction(ACTION)
            .putExtra("id", id).putExtra(EXTRA_TOKEN, token),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

class LessonNotificationExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        LessonNotificationExpiry.expire(ctx, intent.getIntExtra("id", -1), intent.getStringExtra(LessonNotificationExpiry.EXTRA_TOKEN).orEmpty())
    }
}

class LessonNotificationExpiryWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        LessonNotificationExpiry.expire(
            applicationContext, inputData.getInt("id", -1), inputData.getString(LessonNotificationExpiry.EXTRA_TOKEN).orEmpty(), fromWorker = true,
        )
        return Result.success()
    }
}
