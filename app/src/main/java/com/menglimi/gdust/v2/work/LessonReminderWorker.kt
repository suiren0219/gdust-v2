package com.menglimi.gdust.v2.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.menglimi.gdust.v2.MainActivity
import com.menglimi.gdust.v2.data.Store
import com.menglimi.gdust.v2.ui.schedule.ScheduleTimes
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * 课程提醒通知构建与发送（v8 起由精确闹钟/兜底 Worker 调用，不再做 15 分钟轮询）。
 * 通知附带 HyperOS 超级岛 V3 参数，系统不支持则退化为普通通知。
 */
object LessonReminderWorker {

    const val CHANNEL = "gdust_lesson_reminder"

    /** 按 [ReminderEvent] 发一条提醒，写去重键，刷新桌面小组件。 */
    internal fun emit(ctx: Context, store: Store, event: ReminderEvent) {
        val now = LocalTime.now()
        when (event.kind) {
            ReminderKind.BEFORE_CLASS -> {
                val b = event.block ?: return
                val minutes = ChronoUnit.MINUTES.between(now, LocalTime.parse(event.startText))
                    .coerceAtLeast(0)
                post(
                    ctx, 21,
                    title = "${minutes} 分钟后上课 · ${b.name}",
                    text = buildString {
                        append("${event.startText} 第 ${b.startSection} 节")
                        if (b.room.isNotBlank()) append(" · ${b.room}")
                        if (b.teacher.isNotBlank()) append(" · ${b.teacher}")
                    },
                    ticker = "${b.name} ${event.startText}",
                    island = store.islandEnabled,
                    islandExtras = islandExtras(
                        ticker = "${b.name} ${event.startText}",
                        aodTitle = "${minutes} 分钟后上课",
                        timeoutSec = (minutes + 5).toInt() * 60,
                        bigLeftFrontTitle = "第 ${b.startSection} 节",
                        bigLeftTitle = "${minutes} 分钟",
                        bigLeftContent = "后上课",
                        bigRightTitle = b.name,
                        bigRightContent = listOfNotNull(event.startText, b.room.takeIf { it.isNotBlank() }).joinToString(" · "),
                    ),
                )
            }
            ReminderKind.IN_CLASS -> {
                val b = event.block ?: return
                val left = ChronoUnit.MINUTES.between(now, LocalTime.parse(event.endText))
                    .coerceAtLeast(0)
                post(
                    ctx, 23,
                    title = "${left} 分钟后下课 · ${b.name}",
                    text = "第 ${b.startSection} 节 ${event.endText} 结束" +
                        if (b.room.isNotBlank()) " · ${b.room}" else "",
                    ticker = "${b.name} 还有 ${left} 分钟下课",
                    island = store.islandEnabled,
                    islandExtras = islandExtras(
                        ticker = "${b.name} 还有 ${left} 分钟下课",
                        aodTitle = "${b.name} 快下课",
                        timeoutSec = (left + 5).toInt() * 60,
                        bigLeftFrontTitle = "第 ${b.startSection} 节",
                        bigLeftTitle = "${left} 分钟",
                        bigLeftContent = "后下课",
                        bigRightTitle = b.name,
                        bigRightContent = b.room,
                    ),
                )
            }
            ReminderKind.NEXT_DAY -> {
                val lessons = event.nextDayBlocks
                if (lessons.isEmpty()) return
                val summary = lessons.joinToString("\n") { b ->
                    "${ScheduleTimes.start(b.startSection).ifEmpty { "${b.startSection}节" }} ${b.name}" +
                        if (b.room.isNotBlank()) " @${b.room}" else ""
                }
                post(
                    ctx, 22,
                    title = "明天 ${lessons.size} 节课",
                    text = summary,
                    ticker = "明天 ${lessons.first().name} 等 ${lessons.size} 节课",
                    island = store.islandEnabled,
                    islandExtras = islandExtras(
                        ticker = "明天 ${lessons.size} 节课",
                        aodTitle = "明天 ${lessons.size} 节课",
                        timeoutSec = 12 * 3600,
                        bigLeftFrontTitle = null,
                        bigLeftTitle = "${lessons.size} 节",
                        bigLeftContent = "明天的课",
                        bigRightTitle = lessons.first().name,
                        bigRightContent = "等 ${lessons.size} 门课",
                    ),
                )
            }
        }
        store.lastReminderKey = event.key
        runCatching { com.menglimi.gdust.v2.widget.TodayWidget().refreshAll(ctx) }
    }

    /** 发一条提醒（课前/课中/次日/测试共用）。 */
    internal fun post(
        ctx: Context,
        id: Int,
        title: String,
        text: String,
        ticker: String,
        island: Boolean = true,
        islandExtras: Bundle? = null,
    ) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "课程提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "课前提醒与次日课程汇总"
            },
        )
        val pi = PendingIntent.getActivity(
            ctx, id, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pi)
            .setAutoCancel(true)
        // HyperOS 焦点通知/超级岛：标准 V3 协议，缺 business/aodTitle/param_island 时系统直接忽略。
        if (island && islandExtras != null) builder.addExtras(islandExtras)
        nm.notify(id, builder.build())
    }

    /**
     * 组装超级岛 V3 extras（官方协议：https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131）。
     * 字段名对齐 HyperNotification 的 FocusTemplateV3 序列化模板。
     */
    private fun islandExtras(
        ticker: String,
        aodTitle: String,
        timeoutSec: Int,
        bigLeftFrontTitle: String?,
        bigLeftTitle: String,
        bigLeftContent: String,
        bigRightTitle: String,
        bigRightContent: String,
    ): Bundle {
        fun textInfo(front: String?, title: String, content: String) = buildMap<String, Any> {
            if (!front.isNullOrBlank()) put("frontTitle", front)
            put("title", title)
            put("content", content)
        }
        val param = linkedMapOf<String, Any>(
            "enableFloat" to true,
            "ticker" to ticker,
            "aodTitle" to aodTitle,
            "business" to "course",
            "updatable" to false,
            "isShowNotification" to true,
            "baseInfo" to linkedMapOf("type" to 1, "title" to bigRightTitle, "content" to bigLeftTitle + bigLeftContent),
            "hintInfo" to linkedMapOf("type" to 2, "title" to bigRightTitle, "content" to bigRightContent),
            "param_island" to linkedMapOf<String, Any>(
                "islandProperty" to 1,
                "islandTimeout" to timeoutSec,
                "business" to "course",
                "bigIslandArea" to linkedMapOf(
                    "imageTextInfoLeft" to linkedMapOf("type" to 1, "textInfo" to textInfo(bigLeftFrontTitle, bigLeftTitle, bigLeftContent)),
                    "imageTextInfoRight" to linkedMapOf("type" to 2, "textInfo" to textInfo(null, bigRightTitle, bigRightContent)),
                ),
            ),
        )
        return Bundle().apply {
            putString("miui.focus.param", """{"param_v2":${com.google.gson.Gson().toJson(param)}}""")
        }
    }

    /** 设置页「测试提醒」按钮。 */
    fun sendTest(ctx: Context, island: Boolean) = post(
        ctx, 29,
        "测试提醒 · 高等数学",
        "还有 20 分钟上课 · 松 5-308 · 张老师",
        "高等数学 20 分钟后上课",
        island,
        islandExtras(
            ticker = "高等数学 20 分钟后上课",
            aodTitle = "20 分钟后上课",
            timeoutSec = 25 * 60,
            bigLeftFrontTitle = "第 1 节",
            bigLeftTitle = "20 分钟",
            bigLeftContent = "后上课",
            bigRightTitle = "高等数学",
            bigRightContent = "松 5-308 · 张老师",
        ),
    )

    /** 兼容旧调用：重排精确闹钟 + 6h 兜底。 */
    fun schedule(ctx: Context) {
        val store = com.menglimi.gdust.v2.data.Store(ctx)
        LessonReminderAlarms.reschedule(ctx, store)
        LessonReminderAlarms.scheduleFallback(ctx)
    }

    fun cancel(ctx: Context) {
        val store = com.menglimi.gdust.v2.data.Store(ctx)
        LessonReminderAlarms.reschedule(ctx, store)
        LessonReminderAlarms.cancelFallback(ctx)
    }
}
