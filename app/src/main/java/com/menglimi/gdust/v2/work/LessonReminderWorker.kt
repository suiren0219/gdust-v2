package com.menglimi.gdust.v2.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.menglimi.gdust.v2.App
import com.menglimi.gdust.v2.MainActivity
import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.ui.schedule.ScheduleTimes
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * 课程提醒（每 15 分钟巡检一次本机课表缓存）：
 * - 课前提醒：距上课 ≤ reminderLead 分钟时发通知（同一节课只提醒一次）
 * - 次日提醒：每天 nextDayHour 点汇总明天的课
 * 通知附带 HyperOS 焦点通知（超级岛）字段，系统支持时会以岛样式展示，否则退化为普通通知。
 */
class LessonReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val repo = (applicationContext as App).repo
        val store = repo.store
        if (!store.reminderEnabled) return Result.success()
        val blocks = store.loadBlocks()
        val cal = store.loadCalendar() ?: return Result.success()
        if (blocks.isEmpty()) return Result.success()

        val today = LocalDate.now()
        val now = LocalTime.now()

        // 学期第一周周一（优先用户设置的开学日期）
        val termStart = store.termStartDate.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: runCatching { LocalDate.parse(cal.startDate) }.getOrNull()
        fun weekOf(date: LocalDate): Int = termStart?.let {
            (ChronoUnit.DAYS.between(it, date) / 7 + 1).toInt()
        } ?: cal.week

        // ① 课前提醒
        val todayBlocks = blocks.filter { it.week == weekOf(today) && it.day == today.dayOfWeek.value }
        val lead = store.reminderLead
        todayBlocks.forEach { b ->
            val startStr = ScheduleTimes.start(b.startSection)
            if (startStr.isEmpty()) return@forEach
            val start = runCatching { LocalTime.parse(startStr) }.getOrNull() ?: return@forEach
            val minutes = ChronoUnit.MINUTES.between(now, start)
            val key = "lesson-${today}-${b.startSection}-${b.name}"
            if (minutes in 0..lead.toLong() && store.lastReminderKey != key) {
                notify(
                    id = 21,
                    title = "${minutes} 分钟后上课 · ${b.name}",
                    text = buildString {
                        append("$startStr 第 ${b.startSection} 节")
                        if (b.room.isNotBlank()) append(" · ${b.room}")
                        if (b.teacher.isNotBlank()) append(" · ${b.teacher}")
                    },
                    ticker = "${b.name} $startStr",
                    island = store.islandEnabled,
                    islandExtras = islandExtras(
                        ticker = "${b.name} $startStr",
                        aodTitle = "${minutes} 分钟后上课",
                        timeoutSec = (minutes + 5).toInt() * 60,
                        bigLeftFrontTitle = "第 ${b.startSection} 节",
                        bigLeftTitle = "${minutes} 分钟",
                        bigLeftContent = "后上课",
                        bigRightTitle = b.name,
                        bigRightContent = listOfNotNull(startStr, b.room.takeIf { it.isNotBlank() }).joinToString(" · "),
                    ),
                )
                store.lastReminderKey = key
                return Result.success()
            }
        }

        // ② 课中提醒：正在上课且距下课 ≤ inClassLead 分钟
        if (store.inClassReminder) {
            todayBlocks.forEach { b ->
                val startStr = ScheduleTimes.start(b.startSection)
                val endStr = ScheduleTimes.end(b.endSection)
                if (startStr.isEmpty() || endStr.isEmpty()) return@forEach
                val start = runCatching { LocalTime.parse(startStr) }.getOrNull() ?: return@forEach
                val end = runCatching { LocalTime.parse(endStr) }.getOrNull() ?: return@forEach
                if (now.isBefore(start) || now.isAfter(end)) return@forEach
                val left = ChronoUnit.MINUTES.between(now, end)
                val key = "inclass-${today}-${b.startSection}-${b.name}"
                if (left in 0..store.inClassLead.toLong() && store.lastReminderKey != key) {
                    notify(
                        id = 23,
                        title = "${left} 分钟后下课 · ${b.name}",
                        text = "第 ${b.startSection} 节 $endStr 结束" +
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
                    store.lastReminderKey = key
                    return Result.success()
                }
            }
        }

        // ③ 次日课程提醒（每天指定小时的第一轮巡检触发）
        if (store.nextDayReminder && now.hour == store.nextDayHour && now.minute < 15) {
            val key = "nextday-$today"
            if (store.lastReminderKey != key) {
                val tomorrow = today.plusDays(1)
                val tomorrowBlocks = blocks
                    .filter { it.week == weekOf(tomorrow) && it.day == tomorrow.dayOfWeek.value }
                    .sortedBy { it.startSection }
                if (tomorrowBlocks.isNotEmpty()) {
                    val summary = tomorrowBlocks.joinToString("\n") { b ->
                        "${ScheduleTimes.start(b.startSection).ifEmpty { "${b.startSection}节" }} ${b.name}" +
                            if (b.room.isNotBlank()) " @${b.room}" else ""
                    }
                    notify(
                        id = 22,
                        title = "明天 ${tomorrowBlocks.size} 节课",
                        text = summary,
                        ticker = "明天 ${tomorrowBlocks.first().name} 等 ${tomorrowBlocks.size} 节课",
                        island = store.islandEnabled,
                        islandExtras = islandExtras(
                            ticker = "明天 ${tomorrowBlocks.size} 节课",
                            aodTitle = "明天 ${tomorrowBlocks.size} 节课",
                            timeoutSec = 12 * 3600,
                            bigLeftFrontTitle = null,
                            bigLeftTitle = "${tomorrowBlocks.size} 节",
                            bigLeftContent = "明天的课",
                            bigRightTitle = tomorrowBlocks.first().name,
                            bigRightContent = "等 ${tomorrowBlocks.size} 门课",
                        ),
                    )
                    store.lastReminderKey = key
                }
            }
        }
        return Result.success()
    }

    private fun notify(
        id: Int,
        title: String,
        text: String,
        ticker: String,
        island: Boolean = true,
        islandExtras: Bundle? = null,
    ) = post(applicationContext, id, title, text, ticker, island, islandExtras)

    private object IntentFactory {
        fun main(ctx: Context) = android.content.Intent(ctx, MainActivity::class.java)
    }

    companion object {
        const val CHANNEL = "gdust_lesson_reminder"

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
                ctx, id, IntentFactory.main(ctx),
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
            // HyperOS 焦点通知/超级岛：标准 V3 协议（HyperNotification 库序列化 param_v2）。
            // 旧实现只有 ticker 且缺 business/aodTitle/param_island 必填字段，系统直接忽略 → 超级岛无效。
            if (island && islandExtras != null) {
                builder.addExtras(islandExtras)
            }
            nm.notify(id, builder.build())
        }

        /**
         * 组装超级岛 V3 extras（官方协议：https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131）。
         * 结构对齐 HyperNotification 库的 FocusTemplateV3 序列化（Apache-2.0，字段名一致）：
         * param_v2 = { enableFloat, ticker, aodTitle, business, isShowNotification,
         *              baseInfo, hintInfo, param_island { islandProperty, islandTimeout, bigIslandArea } }
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
        ) : Bundle {
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
                "baseInfo" to linkedMapOf(
                    "type" to 1,
                    "title" to bigRightTitle,
                    "content" to bigLeftTitle + bigLeftContent,
                ),
                "hintInfo" to linkedMapOf(
                    "type" to 2,
                    "title" to bigRightTitle,
                    "content" to bigRightContent,
                ),
                "param_island" to linkedMapOf<String, Any>(
                    "islandProperty" to 1,
                    "islandTimeout" to timeoutSec,
                    "business" to "course",
                    "bigIslandArea" to linkedMapOf(
                        "imageTextInfoLeft" to linkedMapOf(
                            "type" to 1,
                            "textInfo" to textInfo(bigLeftFrontTitle, bigLeftTitle, bigLeftContent),
                        ),
                        "imageTextInfoRight" to linkedMapOf(
                            "type" to 2,
                            "textInfo" to textInfo(null, bigRightTitle, bigRightContent),
                        ),
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

        fun schedule(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "gdust_lesson_reminder",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<LessonReminderWorker>(15, TimeUnit.MINUTES).build(),
            )
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork("gdust_lesson_reminder")
        }
    }
}
