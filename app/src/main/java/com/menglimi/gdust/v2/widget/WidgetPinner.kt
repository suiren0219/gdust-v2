package com.menglimi.gdust.v2.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** 桌面小部件款式（供设置页的"添加桌面小部件"入口列出）。 */
data class WidgetPreset(val label: String, val receiver: Class<out GlanceAppWidgetReceiver>)

object WidgetPresets {
    val ALL: List<WidgetPreset> = listOf(
        WidgetPreset("课表 · 今日条子 4×2", ScheduleWideReceiver::class.java),
        WidgetPreset("课表 · 下一节 2×2", ScheduleMediumReceiver::class.java),
        WidgetPreset("课表 · 周进度竖条 1×2", ScheduleTallReceiver::class.java),
        WidgetPreset("宿舍电量 · 走势与切换 4×2", PowerWideReceiver::class.java),
        WidgetPreset("宿舍电量 · 大字与迷你走势 2×2", PowerMediumReceiver::class.java),
        WidgetPreset("宿舍电量 · 竖条 1×2", PowerTallReceiver::class.java),
        WidgetPreset("待办 · 条子列表 4×2", TodoWideReceiver::class.java),
        WidgetPreset("待办 · 概览 2×2", TodoMediumReceiver::class.java),
        WidgetPreset("待办 · 计数竖条 1×2", TodoTallReceiver::class.java),
    )
}

/** 通过系统的 requestPinAppWidget 弹出"添加到主屏幕"确认框；launcher 不支持时返回 false。 */
suspend fun pinWidget(context: Context, preset: WidgetPreset): Boolean {
    val receiver = preset.receiver.getDeclaredConstructor().newInstance()
    val manager = GlanceAppWidgetManager(context)
    return runCatching { manager.requestPinGlanceAppWidget(preset.receiver, receiver.glanceAppWidget) }
        .getOrDefault(false)
}
