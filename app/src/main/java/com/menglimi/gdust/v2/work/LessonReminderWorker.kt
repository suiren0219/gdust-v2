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
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 课程提醒通知（v9 起对齐 Nexio 课程提醒卡的字段结构）：
 * - 状态栏岛：`博 A201 | 01:00 上课`（左：地点，右：系统 Chronometer 倒计时）
 * - 锁屏/息屏卡：课程名 / 第 N-M 节｜地点 / 即将上课 · 倒计时 / 地点 / 「上课勿扰」
 * - 普通通知栏：课程名 + 节次｜地点，系统不支持焦点通知时仍可读
 *
 * 字段名与官方 V3 协议一致，参照 NexioSchedule 28a99a3 的
 * IslandNotificationHelper.buildFocusJson() 与 HyperNotification 的 FocusTemplateV3。
 */
object LessonReminderWorker {

    const val CHANNEL = "gdust_lesson_reminder"
    private const val BUSINESS_TAG = "course_reminder"

    /** 发提醒前恢复渠道重要性（「上课勿扰」按钮会把它降到 LOW）。 */
    private fun ensureAudibleChannel(ctx: Context, nm: NotificationManager) {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "课程提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "课前提醒与次日课程汇总"
            },
        )
    }

    /** 按 [ReminderEvent] 发一条提醒，写去重键，刷新桌面小组件。 */
    internal fun emit(ctx: Context, store: Store, event: ReminderEvent) {
        val now = LocalTime.now()
        when (event.kind) {
            ReminderKind.BEFORE_CLASS -> {
                val b = event.block ?: return
                val start = LocalTime.parse(event.startText)
                val minutes = ChronoUnit.MINUTES.between(now, start).coerceAtLeast(0)
                val startMillis = event.date.atTime(start).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val content = "第 ${b.startSection}-${b.endSection} 节 | ${b.room}"
                post(
                    ctx, 21,
                    title = b.name,
                    text = content,
                    ticker = "${store.shortClassroom(b.room)}  ${minutes}分钟后上课",
                    island = store.islandEnabled,
                    islandExtras = focusJson(
                        ctx = ctx, store = store,
                        courseName = b.name, content = content, classroom = b.room,
                        startMillis = startMillis, endMillis = null,
                        timeoutSec = (minutes + 5).toInt() * 60,
                        inClass = false,
                    ),
                )
            }
            ReminderKind.IN_CLASS -> {
                val b = event.block ?: return
                val end = LocalTime.parse(event.endText)
                val left = ChronoUnit.MINUTES.between(now, end).coerceAtLeast(0)
                val endMillis = event.date.atTime(end).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val content = "第 ${b.startSection}-${b.endSection} 节 | ${b.room}"
                post(
                    ctx, 23,
                    title = b.name,
                    text = "$content · 还有 $left 分钟下课",
                    ticker = "${store.shortClassroom(b.room)}  ${left}分钟后下课",
                    island = store.islandEnabled,
                    islandExtras = focusJson(
                        ctx = ctx, store = store,
                        courseName = b.name, content = content, classroom = b.room,
                        startMillis = null, endMillis = endMillis,
                        timeoutSec = (left + 5).toInt() * 60,
                        inClass = true,
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
                    islandExtras = focusJson(
                        ctx = ctx, store = store,
                        courseName = "明天 ${lessons.size} 节课",
                        content = lessons.first().name + " 等 ${lessons.size} 门课",
                        classroom = lessons.first().room,
                        startMillis = null, endMillis = null,
                        timeoutSec = 12 * 3600,
                        inClass = false,
                    ),
                )
            }
        }
        store.lastReminderKey = event.key
        runCatching { com.menglimi.gdust.v2.widget.WidgetRefresher.refreshAll(ctx) }
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
        ensureAudibleChannel(ctx, nm)
        val pi = PendingIntent.getActivity(
            ctx, id, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setTicker(ticker)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pi)
            .setAutoCancel(true)
        if (island && islandExtras != null) builder.addExtras(islandExtras)
        nm.notify(id, builder.build())
    }

    /**
     * 组装焦点通知/超级岛 V3 JSON（Nexio 同款字段）：
     * baseInfo{type:2,title,content} + hintInfo{timerInfo,subTitle,subContent,actionInfo}
     * + param_island{bigIslandArea{imageTextInfoLeft, sameWidthDigitInfo/timerInfo}, smallIslandArea}。
     */
    private fun focusJson(
        ctx: Context,
        store: Store,
        courseName: String,
        content: String,
        classroom: String,
        startMillis: Long?,
        endMillis: Long?,
        timeoutSec: Int,
        inClass: Boolean,
    ): Bundle {
        val now = System.currentTimeMillis()
        val shortRoom = store.shortClassroom(classroom)
        val shortName = store.shortCourse(courseName)
        val counting = (if (inClass) endMillis else startMillis)?.let { it > now } == true
        val timerTarget = if (inClass) endMillis else startMillis
        val leftMode = store.islandLeftMode
        val rightMode = if (inClass) store.islandInClassRightMode else store.islandRightMode
        val minutesShort = timerTarget?.let { ((it - now + 59_999L) / 60_000L).toString().padStart(2, '0') + "分钟" } ?: "--"

        fun modeText(mode: Int) = when (mode) {
            0 -> shortName
            1 -> shortRoom
            else -> minutesShort
        }

        fun timerInfo() = JSONObject().apply {
            put("timerType", if (counting) -1 else 0)
            put("timerWhen", timerTarget ?: 0L)
            put("timerTotal", 0L)
            put("timerSystemCurrent", now)
        }

        val actionIntent = "intent:#Intent;action=${ClassDndToggleReceiver.ACTION};" +
            "component=${ctx.packageName}/.work.ClassDndToggleReceiver;end"

        val paramV2 = JSONObject().apply {
            put("protocol", 1)
            put("business", BUSINESS_TAG)
            put("enableFloat", true)
            put("updatable", true)
            put("reopen", "reopen")
            put("sequence", now / 1000L)
            put("isShowNotification", true)
            put("aodTitle", if (store.islandAodMode == 1) shortRoom.ifEmpty { shortName } else shortName)
            put("ticker", "$shortRoom  ${if (inClass) "下课" else "上课"}")

            put("baseInfo", JSONObject().apply {
                put("type", 2)
                put("title", courseName)
                put("content", content)
                put("showDivider", true)
                put("showContentDivider", false)
                put("colorTitle", "#111111")
                put("colorTitleDark", "#ffffff")
                put("colorContent", "#333333")
                put("colorContentDark", "#cccccc")
            })

            put("hintInfo", JSONObject().apply {
                put("type", 2)
                put("content", if (inClass) "距下课" else "即将上课")
                put("title", "")
                put("timerInfo", timerInfo())
                put("subContent", "地点")
                put("subTitle", classroom)
                put("colorContent", "#666666")
                put("colorContentDark", "#aaaaaa")
                put("colorSubTitle", "#222222")
                put("colorSubTitleDark", "#eeeeee")
                put("actionInfo", JSONObject().apply {
                    put("actionTitle", "上课勿扰")
                    put("actionIntentType", 2)
                    put("actionIntent", actionIntent)
                })
            })

            put("param_island", JSONObject().apply {
                put("islandProperty", 1)
                put("islandTimeout", timeoutSec)
                put("bigIslandArea", JSONObject().apply {
                    put("templateNo", 2)
                    put("imageTextInfoLeft", JSONObject().apply {
                        put("type", 1)
                        put("textInfo", JSONObject().apply {
                            put("title", modeText(leftMode))
                            put("content", "")
                            put("showHighlightColor", false)
                            put("narrowFont", false)
                        })
                    })
                    if (rightMode == 2 && counting) {
                        put("sameWidthDigitInfo", JSONObject().apply {
                            put("content", if (inClass) "下课" else "上课")
                            put("showHighlightColor", false)
                            put("timerInfo", timerInfo())
                        })
                    } else {
                        put("textInfo", JSONObject().apply {
                            put("title", modeText(rightMode))
                            put("content", "")
                            put("showHighlightColor", false)
                            put("narrowFont", false)
                        })
                    }
                })
                put("smallIslandArea", JSONObject().apply {
                    put("picInfo", JSONObject().apply {
                        put("type", 1)
                        put("pic", "miui.focus.pic_small")
                        put("picDark", "miui.focus.pic_small_dark")
                    })
                })
            })
        }
        return Bundle().apply {
            putString("miui.focus.param", JSONObject().put("param_v2", paramV2).toString())
        }
    }

    /** 设置页「测试提醒」按钮：20 分钟后上课的大学英语 II。 */
    fun sendTest(ctx: Context, island: Boolean) {
        val store = Store(ctx)
        val startMillis = System.currentTimeMillis() + 20 * 60_000L
        post(
            ctx, 29,
            "大学英语II",
            "第 3-4 节 | 博 A201",
            "博 A201  20分钟后上课",
            island,
            focusJson(
                ctx = ctx, store = store,
                courseName = "大学英语II", content = "第 3-4 节 | 博 A201", classroom = "博 A201",
                startMillis = startMillis, endMillis = null,
                timeoutSec = 25 * 60,
                inClass = false,
            ),
        )
    }

    /** 兼容旧调用：重排精确闹钟 + 6h 兜底。 */
    fun schedule(ctx: Context) {
        val store = Store(ctx)
        LessonReminderAlarms.reschedule(ctx, store)
        LessonReminderAlarms.scheduleFallback(ctx)
    }

    fun cancel(ctx: Context) {
        val store = Store(ctx)
        LessonReminderAlarms.reschedule(ctx, store)
        LessonReminderAlarms.cancelFallback(ctx)
    }
}
