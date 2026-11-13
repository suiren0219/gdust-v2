package com.menglimi.gdust.v2.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.menglimi.gdust.v2.MainActivity
import com.menglimi.gdust.v2.data.Store
import com.menglimi.gdust.v2.data.Todo
import com.menglimi.gdust.v2.data.TodoKind

/** 待办到期提醒：独立渠道、独立开关、独立去重键，复用精确闹钟链。 */
object TodoReminder {

    const val CHANNEL = "gdust_todo_reminder"
    private const val NOTIFICATION_ID = 41

    /** 未来最早一条需要提醒的待办（只挑有截止时间且未完成的）。 */
    internal fun next(store: Store, nowMillis: Long): Todo? {
        if (!store.todoReminderEnabled) return null
        val lead = store.todoReminderLead * 60_000L
        return store.loadTodos()
            .filter { t -> !t.done && t.dueAt > 0L && t.dueAt - lead > nowMillis &&
                (!store.todoReminderOnlyDatedTypes || t.kind != TodoKind.GENERAL) }
            .minByOrNull { it.dueAt - lead }
    }

    /** 下一次提醒时刻（epoch ms）；没有则 null。 */
    internal fun nextAt(store: Store, nowMillis: Long): Long? =
        next(store, nowMillis)?.let { it.dueAt - store.todoReminderLead * 60_000L }

    /** 发一条待办提醒（由闹钟 Receiver 直接调用，或「测试提醒」按钮）。 */
    fun fire(ctx: Context, store: Store, todo: Todo? = null) {
        val t = todo ?: store.loadTodos().firstOrNull { !it.done && it.dueAt > 0L } ?: return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "待办提醒", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "作业、考试与自定义待办的到期提醒" }
        )
        val pi = PendingIntent.getActivity(ctx, NOTIFICATION_ID, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val kindLabel = when (t.kind) { TodoKind.EXAM -> "考试"; TodoKind.HOMEWORK -> "作业"; TodoKind.GENERAL -> "待办" }
        val leadLabel = if (store.todoReminderLead >= 1440) "明天" else "${store.todoReminderLead} 分钟后"
        nm.notify(NOTIFICATION_ID, NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("${kindLabel}提醒 · ${t.course.ifBlank { "待办" }}")
            .setContentText("${t.text} · ${leadLabel}到期")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${t.text}\n${t.course} · ${leadLabel}到期"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build())
    }

    fun sendTest(ctx: Context, store: Store) = fire(ctx, store)
}
