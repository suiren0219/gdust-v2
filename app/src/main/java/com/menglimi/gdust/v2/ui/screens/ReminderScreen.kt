package com.menglimi.gdust.v2.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.work.LessonReminderWorker

/**
 * 课程提醒（对齐 Nexio 结构）：开启提醒 / 下节课提醒 / 次日课程提醒 / 课中提醒 /
 * 超级岛（HyperOS 焦点通知）/ 权限清单 / 测试提醒。
 */
@Composable
fun ReminderScreen(repo: Repository, onBack: () -> Unit) {
    val ds = LocalDs.current
    val store = repo.store
    val ctx = LocalContext.current

    fun openSystem(action: String, data: Uri? = null) {
        runCatching {
            ctx.startActivity(Intent(action).apply {
                if (data != null) this.data = data
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    SettingSubPage("课程提醒", "课前 · 次日 · 课中，通知可尝试上岛", onBack) {
        // ── 总开关 ──
        SettingGroup {
            SettingOption(
                title = "开启提醒",
                subtitle = "总开关；关闭后所有课程提醒都不发",
                switch = store.reminderEnabled,
                onSwitch = { on ->
                    store.reminderEnabled = on
                    if (on) LessonReminderWorker.schedule(ctx) else LessonReminderWorker.cancel(ctx)
                },
            )
        }
        Spacer(Modifier.height(10.dp))

        // ── 下节课提醒 ──
        SectionHeader("下节课提醒")
        SettingGroup {
            SettingOption(
                title = "下节课提醒",
                subtitle = "距上课还有一段时间时提醒时间、地点",
                switch = store.reminderEnabled && store.reminderLead > 0,
                onSwitch = { on ->
                    if (on) {
                        store.reminderLead = 20
                        store.reminderEnabled = true
                        LessonReminderWorker.schedule(ctx)
                    } else {
                        store.reminderEnabled = false
                        LessonReminderWorker.cancel(ctx)
                    }
                },
            )
            SettingDivider()
            SettingOption(
                title = "提前提醒时间",
                detail = "${store.reminderLead} 分钟",
                onClick = { },
            )
            SettingEditor {
                SegmentedChoice(
                    options = listOf(5 to "5 分", 10 to "10 分", 20 to "20 分", 30 to "30 分"),
                    selected = listOf(5, 10, 20, 30).minBy { kotlin.math.abs(it - store.reminderLead) },
                    onSelect = { store.reminderLead = it },
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // ── 次日课程提醒 ──
        SectionHeader("次日课程提醒")
        SettingGroup {
            SettingOption(
                title = "次日课程提醒",
                subtitle = "每晚汇总明天的课程、节次、地点",
                switch = store.nextDayReminder,
                onSwitch = {
                    store.nextDayReminder = it
                    if (it && !store.reminderEnabled) {
                        store.reminderEnabled = true
                        LessonReminderWorker.schedule(ctx)
                    }
                },
            )
            SettingDivider()
            SettingOption(title = "提醒时间", detail = "${store.nextDayHour}:00", onClick = { })
            SettingEditor {
                SegmentedChoice(
                    options = listOf(20 to "20:00", 21 to "21:00", 22 to "22:00", 23 to "23:00"),
                    selected = listOf(20, 21, 22, 23).minBy { kotlin.math.abs(it - store.nextDayHour) },
                    onSelect = { store.nextDayHour = it },
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // ── 课中提醒 ──
        SectionHeader("课中提醒")
        SettingGroup {
            SettingOption(
                title = "课中提醒",
                subtitle = "快下课时提示剩余时间",
                switch = store.inClassReminder,
                onSwitch = {
                    store.inClassReminder = it
                    if (it && !store.reminderEnabled) {
                        store.reminderEnabled = true
                        LessonReminderWorker.schedule(ctx)
                    }
                },
            )
            SettingDivider()
            SettingOption(title = "提醒时机", detail = "距下课 ${store.inClassLead} 分钟", onClick = { })
            SettingEditor {
                SegmentedChoice(
                    options = listOf(5 to "5 分", 10 to "10 分", 15 to "15 分"),
                    selected = listOf(5, 10, 15).minBy { kotlin.math.abs(it - store.inClassLead) },
                    onSelect = { store.inClassLead = it },
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // ── 超级岛 ──
        SectionHeader("小米超级岛")
        SettingGroup {
            SettingOption(
                title = "超级岛样式提醒",
                subtitle = "通知附带 HyperOS 焦点通知字段；是否上岛取决于系统版本与通知权限",
                switch = store.islandEnabled,
                onSwitch = { store.islandEnabled = it },
            )
            SettingDivider()
            SettingOption(
                title = "测试提醒",
                subtitle = "发一条示例通知，看看能不能上岛",
                detail = "点一下",
                onClick = { LessonReminderWorker.sendTest(ctx, store.islandEnabled) },
            )
        }
        Spacer(Modifier.height(10.dp))

        // ── 权限 ──
        SectionHeader("权限")
        SettingGroup {
            SettingOption(
                title = "通知权限",
                subtitle = "用于接收课程提醒",
                detail = "去设置",
                onClick = {
                    openSystem(
                        Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                        Uri.parse("package:${ctx.packageName}"),
                    )
                },
            )
            SettingDivider()
            SettingOption(
                title = "电池优化",
                subtitle = "关闭电池优化可让提醒更准时",
                detail = "去设置",
                onClick = { openSystem(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) },
            )
            SettingDivider()
            SettingOption(
                title = "自启动 / 后台运行",
                subtitle = "HyperOS 需允许自启动，提醒才不会被清理",
                detail = "应用详情",
                onClick = {
                    openSystem(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${ctx.packageName}"),
                    )
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "后台每 15 分钟巡检本机课表；同一节课只提醒一次。\n" +
                "超级岛是小米 HyperOS 的焦点通知能力，第三方 App 只能「请求」上岛，是否显示由系统决定。",
            fontSize = 11.sp, lineHeight = 16.sp, color = ds.sub,
            modifier = Modifier,
        )
    }
}
