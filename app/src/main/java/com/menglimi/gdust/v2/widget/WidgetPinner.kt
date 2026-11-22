package com.menglimi.gdust.v2.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** 分类和尺寸由预览页与添加入口共用，避免按固定数量分组。 */
data class WidgetPreset(
    val label: String,
    val receiver: Class<out GlanceAppWidgetReceiver>,
    val category: String,
    val previewSize: DpSize,
)

object WidgetPresets {
    val ALL: List<WidgetPreset> = listOf(
        WidgetPreset("课表 · 今日课程 4×2", ScheduleWideReceiver::class.java, "上课课表", DpSize(340.dp, 170.dp)),
        WidgetPreset("课表 · 下一节 2×2", ScheduleMediumReceiver::class.java, "上课课表", DpSize(180.dp, 180.dp)),
        WidgetPreset("课表 · 课程计数 1×2", ScheduleTallReceiver::class.java, "上课课表", DpSize(96.dp, 192.dp)),
        WidgetPreset("课表 · 课程明细 1×4", ScheduleLongReceiver::class.java, "上课课表", DpSize(96.dp, 340.dp)),
        WidgetPreset("宿舍电量 · 走势 4×2", PowerWideReceiver::class.java, "宿舍电量", DpSize(340.dp, 170.dp)),
        WidgetPreset("宿舍电量 · 大字 2×2", PowerMediumReceiver::class.java, "宿舍电量", DpSize(180.dp, 180.dp)),
        WidgetPreset("宿舍电量 · 读数 1×2", PowerTallReceiver::class.java, "宿舍电量", DpSize(96.dp, 192.dp)),
        WidgetPreset("宿舍电量 · 状态明细 1×4", PowerLongReceiver::class.java, "宿舍电量", DpSize(96.dp, 340.dp)),
        WidgetPreset("待办 · 列表 4×2", TodoWideReceiver::class.java, "待办事项", DpSize(340.dp, 170.dp)),
        WidgetPreset("待办 · 概览 2×2", TodoMediumReceiver::class.java, "待办事项", DpSize(180.dp, 180.dp)),
        WidgetPreset("待办 · 计数 1×2", TodoTallReceiver::class.java, "待办事项", DpSize(96.dp, 192.dp)),
    )
}

/** 系统确认添加到主屏幕；不支持时返回 false。 */
suspend fun pinWidget(context: Context, preset: WidgetPreset): Boolean {
    val receiver = preset.receiver.getDeclaredConstructor().newInstance()
    val manager = GlanceAppWidgetManager(context)
    return runCatching { manager.requestPinGlanceAppWidget(preset.receiver, receiver.glanceAppWidget) }
        .getOrDefault(false)
}
