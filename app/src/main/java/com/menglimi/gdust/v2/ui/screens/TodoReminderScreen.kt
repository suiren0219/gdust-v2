package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.work.LessonReminderAlarms
import com.menglimi.gdust.v2.work.TodoReminder

/** 待办提醒设置：独立于课程提醒的开关、提前量、类型范围、测试按钮与权限诊断。 */
@Composable
fun TodoReminderScreen(repo: Repository, onBack: () -> Unit) {
    val ds = LocalDs.current
    val store = repo.store
    val ctx = LocalContext.current
    // 普通 prefs 不是 Composition 状态：本地 revision 强制重读
    var revision by remember { mutableIntStateOf(0) }
    // 测试提醒的点击反馈：没有反馈的按钮等于坏的
    var testMsg by remember { androidx.compose.runtime.mutableStateOf("点一下") }
    fun save(block: () -> Unit) {
        block()
        LessonReminderAlarms.rescheduleTodo(ctx, store)
        if (store.todoReminderEnabled) LessonReminderAlarms.scheduleFallback(ctx)
        revision++
    }

    androidx.compose.runtime.key(revision) {
        SettingSubPage("待办提醒", "作业 · 考试 · 自定义事项的到期提醒", onBack) {
            SettingGroup {
                SettingOption(
                    title = "开启待办提醒",
                    subtitle = "按截止时间提前提醒；只提醒设置了时间的待办",
                    switch = store.todoReminderEnabled,
                    onSwitch = { on -> save { store.todoReminderEnabled = on } },
                )
            }
            Spacer(Modifier.height(10.dp))
            SectionHeader("提醒规则")
            SettingGroup {
                SettingOption(title = "提前提醒时间",
                    detail = when (store.todoReminderLead) {
                        0 -> "准时"; 1440 -> "提前 1 天"; else -> "提前 ${store.todoReminderLead} 分钟"
                    }, onClick = { })
                SettingEditor {
                    SegmentedChoice(
                        options = listOf(0 to "准时", 10 to "10 分", 30 to "30 分", 60 to "1 小时", 1440 to "1 天"),
                        selected = store.todoReminderLead,
                        onSelect = { save { store.todoReminderLead = it } },
                    )
                }
                SettingDivider()
                SettingOption(
                    title = "只提醒作业与考试",
                    subtitle = "关闭后，带截止时间的普通事项也会提醒",
                    switch = store.todoReminderOnlyDatedTypes,
                    onSwitch = { on -> save { store.todoReminderOnlyDatedTypes = on } },
                )
            }
            Spacer(Modifier.height(10.dp))
            SectionHeader("权限与测试")
            SettingGroup {
                SettingOption(
                    title = "精确闹钟权限",
                    subtitle = if (LessonReminderAlarms.canScheduleExact(ctx)) "已授权" else "未授权，可能延迟送达",
                    detail = if (LessonReminderAlarms.canScheduleExact(ctx)) "已授权" else "去授权",
                    onClick = { LessonReminderAlarms.openExactAlarmSettings(ctx) },
                )
                SettingDivider()
                SettingOption(
                    title = "通知权限",
                    subtitle = "用于接收待办到期提醒",
                    detail = "去设置",
                    onClick = {
                        runCatching {
                            ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                )
                SettingDivider()
                SettingOption(
                    title = "测试提醒",
                    subtitle = "发一条示例待办通知",
                    detail = testMsg,
                    onClick = {
                        val granted = TodoReminder.sendTest(ctx, store)
                        testMsg = if (granted) {
                            "已发出（" + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                                .format(java.util.Date()) + "）· 没收到就查勿扰/通知渠道"
                        } else {
                            "通知权限未授权：去上面的「通知权限」允许后重试"
                        }
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "截止时间由作业/考试导入或手动设置；当前待办页支持文本里写「作业」「考试」自动分类。\n" +
                    "HyperOS 需允许自启动与精确定时，闹钟才不会被系统清理。",
                fontSize = 12.sp, lineHeight = 17.sp, color = ds.sub,
            )
            Spacer(Modifier.height(96.dp))
        }
    }
}
