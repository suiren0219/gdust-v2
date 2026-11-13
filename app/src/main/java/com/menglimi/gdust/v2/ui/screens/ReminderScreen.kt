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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    // 普通 prefs 不是 Composition 状态：用 revision 触发整页重读，否则点了界面不刷新
    val revision = androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    // 测试提醒的点击反馈：发出后显示时间戳，权限缺失时引导去系统设置
    var testMsg by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("点一下") }

    fun save(block: () -> Unit) {
        block()
        // 任何开关/参数变化都重算未来 48h 的提醒并重置闹钟
        com.menglimi.gdust.v2.work.LessonReminderAlarms.reschedule(ctx, store)
        revision.intValue++
    }

    fun openSystem(action: String, data: Uri? = null) {
        runCatching {
            ctx.startActivity(Intent(action).apply {
                if (data != null) this.data = data
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    SettingSubPage("课程提醒", "课前 · 次日 · 课中，通知可尝试上岛", onBack) {
        androidx.compose.runtime.key(revision.intValue) {
        // ── 精确定时诊断 ──
        SectionHeader("精确定时")
        SettingGroup {
            SettingOption(
                title = "闹钟和提醒权限",
                subtitle = com.menglimi.gdust.v2.work.LessonReminderAlarms.status(ctx, store),
                detail = if (com.menglimi.gdust.v2.work.LessonReminderAlarms.canScheduleExact(ctx)) "已授权" else "去授权",
                onClick = { com.menglimi.gdust.v2.work.LessonReminderAlarms.openExactAlarmSettings(ctx) },
            )
        }
        Spacer(Modifier.height(10.dp))

        // ── 总开关 ──
        SettingGroup {
            SettingOption(
                title = "开启提醒",
                subtitle = "总开关；关闭后所有课程提醒都不发",
                switch = store.reminderEnabled,
                onSwitch = { on ->
                    save {
                        store.reminderEnabled = on
                        if (on) LessonReminderWorker.schedule(ctx) else LessonReminderWorker.cancel(ctx)
                    }
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
                    save {
                        if (on) {
                            store.reminderLead = 20
                            store.reminderEnabled = true
                            LessonReminderWorker.schedule(ctx)
                        } else {
                            store.reminderEnabled = false
                            LessonReminderWorker.cancel(ctx)
                        }
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
                    onSelect = { save { store.reminderLead = it } },
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
                onSwitch = { on ->
                    save {
                        store.nextDayReminder = on
                        if (on && !store.reminderEnabled) {
                            store.reminderEnabled = true
                            LessonReminderWorker.schedule(ctx)
                        }
                    }
                },
            )
            SettingDivider()
            SettingOption(title = "提醒时间", detail = "${store.nextDayHour}:00", onClick = { })
            SettingEditor {
                SegmentedChoice(
                    options = listOf(20 to "20:00", 21 to "21:00", 22 to "22:00", 23 to "23:00"),
                    selected = listOf(20, 21, 22, 23).minBy { kotlin.math.abs(it - store.nextDayHour) },
                    onSelect = { save { store.nextDayHour = it } },
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
                onSwitch = { on ->
                    save {
                        store.inClassReminder = on
                        if (on && !store.reminderEnabled) {
                            store.reminderEnabled = true
                            LessonReminderWorker.schedule(ctx)
                        }
                    }
                },
            )
            SettingDivider()
            SettingOption(title = "提醒时机", detail = "距下课 ${store.inClassLead} 分钟", onClick = { })
            SettingEditor {
                SegmentedChoice(
                    options = listOf(5 to "5 分", 10 to "10 分", 15 to "15 分"),
                    selected = listOf(5, 10, 15).minBy { kotlin.math.abs(it - store.inClassLead) },
                    onSelect = { save { store.inClassLead = it } },
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // ── 超级岛 ──
        SectionHeader("小米超级岛")
        val focusProtocol = androidx.compose.runtime.remember {
            runCatching { Settings.System.getInt(ctx.contentResolver, "notification_focus_protocol", 0) }.getOrDefault(0)
        }
        val islandFeature = androidx.compose.runtime.remember {
            runCatching {
                Class.forName("android.os.SystemProperties")
                    .getMethod("get", String::class.java, String::class.java)
                    .invoke(null, "persist.sys.feature.island", "") as String
            }.getOrDefault("")
        }
        SettingGroup {
            SettingOption(
                title = "超级岛样式提醒",
                subtitle = "按 HyperOS 标准 V3 岛协议发通知；是否上岛取决于系统版本与白名单",
                switch = store.islandEnabled,
                onSwitch = { on -> save { store.islandEnabled = on } },
            )
            SettingDivider()
            SettingOption(
                title = "系统支持",
                subtitle = buildString {
                    append("焦点协议 v$focusProtocol")
                    append(" · 超级岛特性 ${if (islandFeature == "1") "已开启" else "未检测到"}")
                    if (focusProtocol < 3) append("（v3 才有岛）")
                },
                detail = "诊断",
            )
            SettingDivider()
            SettingOption(
                title = "白名单",
                subtitle = "HyperOS 只允许白名单 App 上岛；用 HyperCeiler 等模块加白时，注意其若进入安全模式会整组失效",
                detail = "说明",
            )
            SettingDivider()
            // 岛显示选项（Nexio 课程提醒同款）：改动写 prefs 后由下一条提醒生效
            val islandModes = listOf(0 to "课程名称", 1 to "上课地点", 2 to "倒计时")
            SettingOption(title = "超级岛左侧", detail = islandModes.first { it.first == store.islandLeftMode }.second, onClick = { })
            SettingEditor {
                SegmentedChoice(islandModes, store.islandLeftMode, onSelect = { save { store.islandLeftMode = it } })
            }
            SettingDivider()
            SettingOption(title = "超级岛右侧", detail = islandModes.first { it.first == store.islandRightMode }.second, onClick = { })
            SettingEditor {
                SegmentedChoice(islandModes, store.islandRightMode, onSelect = { save { store.islandRightMode = it } })
            }
            SettingDivider()
            SettingOption(title = "课中岛右侧", detail = islandModes.first { it.first == store.islandInClassRightMode }.second, onClick = { })
            SettingEditor {
                SegmentedChoice(islandModes, store.islandInClassRightMode, onSelect = { save { store.islandInClassRightMode = it } })
            }
            SettingDivider()
            SettingOption(
                title = "息屏显示",
                detail = if (store.islandAodMode == 1) "上课地点" else "课程名称",
                onClick = { },
            )
            SettingEditor {
                SegmentedChoice(
                    options = listOf(0 to "课程名称", 1 to "上课地点"),
                    selected = store.islandAodMode,
                    onSelect = { save { store.islandAodMode = it } },
                )
            }
            SettingDivider()
            SettingOption(
                title = "测试提醒",
                subtitle = "发一条示例通知，看看能不能上岛",
                detail = testMsg,
                onClick = {
                    // Android 13+ 通知是运行时权限：没授权时 notify() 是静默无效的，必须先引导授权
                    val granted = android.os.Build.VERSION.SDK_INT < 33 ||
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            ctx, android.Manifest.permission.POST_NOTIFICATIONS,
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        save {
                            testMsg = "通知权限未授权：已打开系统设置，允许后回来再点一次"
                            runCatching {
                                ctx.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    },
                                )
                            }
                        }
                    } else {
                        LessonReminderWorker.sendTest(ctx, store.islandEnabled)
                        save {
                            testMsg = "已发出（" +
                                java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                                    .format(java.util.Date()) +
                                "）· 没收到就查勿扰模式或系统通知设置"
                        }
                    }
                },
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
            "提醒按本机课表算出精确时刻，用系统闹钟触发（不再 15 分钟轮询）；另有 6 小时兜底补漏，同一节课只提醒一次。\n" +
                "HyperOS 需允许自启动与精确定时；超级岛由系统按白名单决定是否显示。",
            fontSize = 11.sp, lineHeight = 16.sp, color = ds.sub,
            modifier = Modifier,
        )
        }
    }
}
