// SPDX-License-Identifier: AGPL-3.0-only
package com.menglimi.gdust.v2.work

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.menglimi.gdust.v2.data.CalendarInfo
import com.menglimi.gdust.v2.data.Repository
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * 课程提醒的精确闹钟调度：从本机课表算出下一个待提醒时刻，用 `setExactAndAllowWhileIdle`
 * 定一个闹钟；触发后发通知并顺带排下一个。旧版 15 分钟轮询只在兜底心跳里保留（6 小时一次）。
 */
object LessonReminderAlarms {

    internal const val ACTION_FIRE = "com.menglimi.gdust.v2.LESSON_REMINDER_FIRE"
    internal const val WORK_FALLBACK = "gdust_lesson_reminder_fallback"
    private const val WORK_LEGACY = "gdust_lesson_reminder"
    private const val REQ_CODE = 7717

    /** 精确闹钟是否可用（Android 12+ 可被用户在系统设置里撤销）。 */
    fun canScheduleExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    /** 打开系统「闹钟和提醒」授权页。 */
    fun openExactAlarmSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canScheduleExact(ctx)) return
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(android.net.Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** 重新计算并重排下一个闹钟；课表/设置变化、开机、时间变更、闹钟触发后都调用。 */
    fun reschedule(ctx: Context, store: com.menglimi.gdust.v2.data.Store) {
        val alarm = ctx.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(ctx)
        alarm.cancel(pi)
        if (!store.reminderEnabled || !notificationAllowed(ctx)) return

        val cal = store.loadCalendar() ?: return
        val blocks = store.loadBlocks()
        if (blocks.isEmpty()) return
        val next = nextEvent(store, cal, blocks, System.currentTimeMillis()) ?: return
        runCatching {
            if (canScheduleExact(ctx)) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atMillis, pi)
            } else {
                // 用户撤销精确权限：退化为非精确闹钟，6h 兜底 Worker 仍会补齐
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atMillis, pi)
            }
        }
    }

    /** 未来 48 小时内最早的一条提醒（已发过的 key 不再排）。 */
    internal fun nextEvent(
        store: com.menglimi.gdust.v2.data.Store,
        cal: CalendarInfo,
        blocks: List<com.menglimi.gdust.v2.data.CourseBlock>,
        nowMillis: Long,
    ): ReminderEvent? {
        val rules = ReminderRules(
            termStart = (store.termStartDate.takeIf { it.isNotBlank() } ?: cal.startDate)
                .let { com.menglimi.gdust.v2.ui.schedule.ScheduleTimes.parseDate(it) },
            calendarWeek = cal.week,
            leadMinutes = store.reminderLead,
            inClassEnabled = store.inClassReminder,
            inClassLeadMinutes = store.inClassLead,
            nextDayEnabled = store.nextDayReminder,
            nextDayHour = store.nextDayHour,
        )
        return planReminders(blocks, rules, nowMillis)
            .firstOrNull { it.atMillis > nowMillis && it.key != store.lastReminderKey }
    }

    /** 处理一条到期提醒；返回 true 表示确实发了通知。 */
    internal fun fire(ctx: Context, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val store = com.menglimi.gdust.v2.data.Store(ctx)
        if (!store.reminderEnabled || !notificationAllowed(ctx)) return false
        val cal = store.loadCalendar() ?: return false
        val blocks = store.loadBlocks()
        if (blocks.isEmpty()) return false
        // 放宽到 ±10 分钟：非精确模式/系统延迟也能命中；去重键防止兜底 Worker 重发
        val event = planReminders(
            blocks,
            ReminderRules(
                termStart = (store.termStartDate.takeIf { it.isNotBlank() } ?: cal.startDate)
                    .let { com.menglimi.gdust.v2.ui.schedule.ScheduleTimes.parseDate(it) },
                calendarWeek = cal.week,
                leadMinutes = store.reminderLead,
                inClassEnabled = store.inClassReminder,
                inClassLeadMinutes = store.inClassLead,
                nextDayEnabled = store.nextDayReminder,
                nextDayHour = store.nextDayHour,
            ),
            nowMillis,
        ).firstOrNull {
            it.key != store.lastReminderKey && kotlin.math.abs(it.atMillis - nowMillis) <= 10 * 60_000L
        } ?: return false
        LessonReminderWorker.emit(ctx, store, event)
        return true
    }

    /** 兜底：精确闹钟被系统砍掉时，最多 6 小时补一次。 */
    fun scheduleFallback(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        wm.cancelUniqueWork(WORK_LEGACY) // 清掉 v7 及以前的 15 分钟周期轮询
        wm.enqueueUniquePeriodicWork(
            WORK_FALLBACK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<LessonReminderFallbackWorker>(6, TimeUnit.HOURS).build(),
        )
    }

    fun cancelFallback(ctx: Context) = WorkManager.getInstance(ctx).cancelUniqueWork(WORK_FALLBACK)

    private fun notificationAllowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun pendingIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, REQ_CODE,
        Intent(ctx, LessonReminderReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** 供设置页显示诊断。 */
    fun status(ctx: Context, store: com.menglimi.gdust.v2.data.Store): String = buildString {
        append(if (canScheduleExact(ctx)) "精确闹钟已授权" else "精确闹钟未授权")
        val next = store.loadCalendar()?.let { cal ->
            nextEvent(store, cal, store.loadBlocks(), System.currentTimeMillis())
        }
        if (next != null) append(" · 下次 ${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(next.atMillis))}")
    }
}

/** 静态广播：闹钟到期 + 开机/时间变更后重排。 */
class LessonReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            LessonReminderAlarms.ACTION_FIRE -> {
                LessonReminderAlarms.fire(ctx)
                LessonReminderAlarms.reschedule(ctx, com.menglimi.gdust.v2.data.Store(ctx))
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                LessonReminderAlarms.reschedule(ctx, com.menglimi.gdust.v2.data.Store(ctx))
        }
    }
}

/** 6 小时兜底：补发到期提醒 + 重排精确闹钟链。 */
class LessonReminderFallbackWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val store = com.menglimi.gdust.v2.data.Store(applicationContext)
        if (store.reminderEnabled) {
            LessonReminderAlarms.fire(applicationContext)
            LessonReminderAlarms.reschedule(applicationContext, store)
        }
        return Result.success()
    }
}
