package com.menglimi.gdust.v2.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.menglimi.gdust.v2.MainActivity
import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.ui.schedule.ScheduleTimes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 三个格式共用：数据来自 WidgetDataSource（本机缓存），刷新入口统一 updateAll。 */
abstract class BaseGdustWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    fun refreshAll(context: Context) {
        CoroutineScope(Dispatchers.Default).launch {
            TodayWidget().updateAll(context)
            NextClassWidget().updateAll(context)
            PowerWidget().updateAll(context)
        }
    }
}

/** 在桌面壁纸上使用实色承载文字；surface / onSurface 成对保证深浅色可读性。 */
@Composable
private fun WidgetSurface(padding: Dp = 10.dp, content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier.fillMaxSize()
            .background(GlanceTheme.colors.surface).cornerRadius(20.dp)
            .clickable(actionStartActivity<MainActivity>()).padding(padding),
    ) { content() }
}

@Composable
private fun widgetTextStyle(size: Int, bold: Boolean = false, secondary: Boolean = false) = TextStyle(
    fontSize = size.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    color = if (secondary) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface,
)

private fun lessonTime(block: CourseBlock): String =
    ScheduleTimes.start(block.startSection).ifEmpty { "第${block.startSection}节" }

/** 标准款：按可用高度显示 1–4 条，宽屏补充电量，大尺寸补充教室。 */
class TodayWidget : BaseGdustWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = WidgetDataSource.load(context)
        provideContent {
            val data by remember { WidgetDataSource.observe(context) }.collectAsState(initial)
            GlanceTheme {
                val size = LocalSize.current
                val expanded = size.height >= 180.dp
                val rowHeight = if (expanded) 48.dp else 28.dp
                val limit = ((size.height.value - 70f) / rowHeight.value).toInt().coerceIn(1, 4)
                WidgetSurface {
                    Column(modifier = GlanceModifier.fillMaxSize()) {
                        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                data.title(),
                                modifier = GlanceModifier.defaultWeight(),
                                style = widgetTextStyle(13, bold = true),
                                maxLines = 1,
                            )
                            if (size.width >= 260.dp && data.odd != null) {
                                Spacer(GlanceModifier.width(8.dp))
                                Text("电量 %.0f 度".format(data.odd), style = widgetTextStyle(12), maxLines = 1)
                            }
                        }
                        Spacer(GlanceModifier.height(6.dp))
                        if (data.lessons.isEmpty()) {
                            Column(
                                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(data.emptyTitle(), style = widgetTextStyle(14, bold = true), maxLines = 1)
                                if (size.height >= 140.dp) {
                                    Spacer(GlanceModifier.height(4.dp))
                                    Text("点击打开应用，同步后查看", style = widgetTextStyle(12, secondary = true), maxLines = 2)
                                }
                            }
                        } else {
                            data.lessons.take(limit).forEach { block ->
                                Row(
                                    modifier = GlanceModifier.fillMaxWidth().height(rowHeight),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        if (block in data.ongoingLessons) "上课中" else lessonTime(block),
                                        modifier = GlanceModifier.width(48.dp),
                                        style = widgetTextStyle(12, bold = true),
                                        maxLines = 1,
                                    )
                                    Spacer(GlanceModifier.width(6.dp))
                                    Column(modifier = GlanceModifier.defaultWeight()) {
                                        Text(block.name, style = widgetTextStyle(14, bold = true), maxLines = 1)
                                        if (expanded) {
                                            Text(
                                                block.room.ifBlank { "教室待定" },
                                                style = widgetTextStyle(12, secondary = true),
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(GlanceModifier.defaultWeight())
                        }
                        Spacer(GlanceModifier.height(4.dp))
                        val remaining = (data.lessons.size - limit).coerceAtLeast(0)
                        Text(
                            (if (remaining > 0) "另 $remaining 节 · " else "") +
                                if (size.width >= 260.dp) data.sourceLabel else "本机缓存 · 点击打开",
                            style = widgetTextStyle(11, secondary = true),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** 紧凑款 2x1：当前 / 下一节课；最小高度仅保留课程名、日期和时间。 */
class NextClassWidget : BaseGdustWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = WidgetDataSource.load(context)
        provideContent {
            val data by remember { WidgetDataSource.observe(context) }.collectAsState(initial)
            val next = data.lessons.firstOrNull()
            val ongoing = next != null && next in data.ongoingLessons
            val whenText = when {
                next == null -> "点击打开应用同步"
                ongoing -> "上课中 · ${ScheduleTimes.end(next.endSection)} 下课"
                data.scope == WidgetData.Scope.TOMORROW -> "明天 ${lessonTime(next)}"
                else -> "今天 ${lessonTime(next)}"
            }
            GlanceTheme {
                val size = LocalSize.current
                val compact = size.height < 96.dp
                val showSource = size.height >= 136.dp
                WidgetSurface(padding = if (compact) 4.dp else 10.dp) {
                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!compact) {
                            Text(data.nextTitle(), style = widgetTextStyle(12, secondary = true), maxLines = 1)
                            Spacer(GlanceModifier.height(4.dp))
                        }
                        Text(
                            next?.name ?: data.emptyTitle(),
                            style = widgetTextStyle(if (compact) 14 else 17, bold = true),
                            maxLines = if (size.height >= 180.dp) 2 else 1,
                        )
                        if (!compact) Spacer(GlanceModifier.height(4.dp))
                        Text(
                            whenText + when {
                                next == null -> ""
                                showSource -> " · ${next.room.ifBlank { "教室待定" }}"
                                else -> " · 缓存"
                            },
                            style = widgetTextStyle(if (compact) 11 else 12),
                            maxLines = if (showSource) 2 else 1,
                        )
                        if (showSource) {
                            Spacer(GlanceModifier.height(6.dp))
                            Text(data.sourceLabel, style = widgetTextStyle(11, secondary = true), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** 电量款 2x2：剩余电量大字 + 房间名，明确标注本机采样时间。 */
class PowerWidget : BaseGdustWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = WidgetDataSource.load(context)
        provideContent {
            val data by remember { WidgetDataSource.observe(context) }.collectAsState(initial)
            GlanceTheme {
                val expanded = LocalSize.current.height >= 180.dp && LocalSize.current.width >= 160.dp
                WidgetSurface(padding = if (expanded) 10.dp else 8.dp) {
                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            data.roomName.ifBlank { "宿舍电量" },
                            style = widgetTextStyle(if (expanded) 14 else 12, secondary = true),
                            maxLines = 1,
                        )
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            data.odd?.let { "%.1f".format(it) } ?: "--",
                            style = widgetTextStyle(if (expanded) 42 else 28, bold = true),
                            maxLines = 1,
                        )
                        Text(
                            if (data.odd == null) "尚无电量记录" else "剩余电量（度）",
                            style = widgetTextStyle(12),
                            maxLines = 1,
                        )
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            data.powerSourceLabel(),
                            style = widgetTextStyle(11, secondary = true),
                            maxLines = if (expanded) 2 else 1,
                        )
                        if (expanded && data.odd != null) {
                            Text("本机缓存 · 点击打开", style = widgetTextStyle(11, secondary = true), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

class NextClassWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextClassWidget()
}

class PowerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PowerWidget()
}
