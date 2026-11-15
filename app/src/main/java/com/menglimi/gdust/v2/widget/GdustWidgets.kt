package com.menglimi.gdust.v2.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.menglimi.gdust.v2.data.TodoGroups
import com.menglimi.gdust.v2.ui.schedule.ScheduleTimes
import com.menglimi.gdust.v2.widget.WidgetStyle.Space
import com.menglimi.gdust.v2.widget.WidgetStyle.Type

/** 画风共享：CZeroX 实底卡 bitmap 背景 + 深浅色自适应文字色；stateDefinition 统一。 */
abstract class GdustGlanceWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
}

// ── 渲染小工具 ──────────────────────────────────────────────────────────────

private fun c(day: Color, night: Color) = ColorProvider(day, night)

@Composable
private fun dark() = WidgetStyle.isDark(LocalContext.current)

@Composable
private fun widgetLayout(): WidgetPresentation.Layout {
    val size = LocalSize.current
    return WidgetPresentation.layout(size.width, size.height, LocalContext.current.resources.configuration.fontScale)
}

@Composable
private fun WText(
    text: String,
    size: TextUnit,
    bold: Boolean,
    color: ColorProvider,
    maxLines: Int = 1,
    modifier: GlanceModifier = GlanceModifier,
    align: TextAlign = TextAlign.Start,
) = Text(text, modifier = modifier, style = TextStyle(
    fontSize = size,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    color = color,
    textAlign = align,
), maxLines = maxLines)

@Composable
private fun CenteredText(text: String, size: TextUnit, bold: Boolean, color: ColorProvider, maxLines: Int = 2) =
    WText(text, size, bold, color, maxLines, GlanceModifier.fillMaxWidth(), TextAlign.Center)

private fun accentSoftBg(dark: Boolean) = c(WidgetStyle.accentSoft(dark), WidgetStyle.accentSoft(dark))
private fun accentFg(dark: Boolean) = c(Color(WidgetStyle.accentText(dark)), Color(WidgetStyle.accentText(dark)))
private fun textColor(dark: Boolean) = c(WidgetStyle.text(dark), WidgetStyle.text(dark))
private fun subColor(dark: Boolean) = c(WidgetStyle.sub(dark), WidgetStyle.sub(dark))
private fun dangerColor() = c(Color(WidgetStyle.dangerText(false)), Color(WidgetStyle.dangerText(true)))

/** 实底卡容器；整卡进入对应页面，内部待办完成与电量切换保留自己的点击行为。 */
@Composable
private fun SolidCard(openTab: Int, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density
    val bmp = WidgetStyle.glassCard(
        context,
        (size.width.value * density).toInt(),
        (size.height.value * density).toInt(),
        WidgetStyle.radius.value * density,
    )
    Box(modifier = GlanceModifier.fillMaxSize()
        .background(ImageProvider(bmp), ContentScale.FillBounds)
        .cornerRadius(WidgetStyle.radius)
        .padding(widgetLayout().padding)
        .clickable(widgetOpenAction(context, openTab)),
    ) { content() }
}

/** 节次 / 类型徽章胶囊；固定列宽与系统字体一起增长。 */
@Composable
private fun Badge(text: String, bg: ColorProvider, fg: ColorProvider, fixedWidth: Dp? = null) {
    var modifier: GlanceModifier = GlanceModifier
    if (fixedWidth != null) modifier = modifier.width(fixedWidth * widgetLayout().fontScale)
    modifier = modifier.cornerRadius(9.dp).background(bg).padding(horizontal = 7.dp, vertical = 3.dp)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        WText(text, Type.caption.sp, true, fg, align = TextAlign.Center)
    }
}

/** 渲染时观察刷新版本：活跃 session 的 state 更新也会重新读取缓存。 */
@Composable
private fun RefreshAware(content: @Composable () -> Unit) {
    val refresh = currentState<Preferences>()[WIDGET_REFRESH_VERSION] ?: 0L
    key(refresh) { content() }
}

// ── 课表：宽卡对齐列 / 中卡下一节 / 竖卡计数 / 横拉单行 ─────────────────────

class ScheduleWidget : GdustGlanceWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                RefreshAware {
                    val data = WidgetDataSource.load(LocalContext.current)
                    when (widgetLayout().shape) {
                        WidgetPresentation.Shape.WIDE -> ScheduleWide(data)
                        WidgetPresentation.Shape.TALL -> ScheduleTall(data)
                        WidgetPresentation.Shape.ROW -> ScheduleRow(data)
                        WidgetPresentation.Shape.MEDIUM -> ScheduleMedium(data)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleWide(data: WidgetData) {
    val dark = dark()
    val layout = widgetLayout()
    val shown = data.lessons.take(layout.visibleRows(data.lessons.size))
    SolidCard(openTab = 1) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WText(WidgetPresentation.scheduleTitle(data), Type.title.sp, true, textColor(dark), modifier = GlanceModifier.defaultWeight())
                if (layout.showHeaderSummary) {
                    Spacer(GlanceModifier.width(Space.gap.dp))
                    WText(WidgetPresentation.scheduleSummary(data), Type.label.sp, false, subColor(dark), align = TextAlign.End)
                }
            }
            Spacer(GlanceModifier.height(Space.gap.dp))
            if (shown.isEmpty()) {
                Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                    WText(if (data.lessons.isEmpty()) WidgetPresentation.scheduleEmpty(data) else WidgetPresentation.scheduleSummary(data),
                        Type.body.sp, false, subColor(dark), maxLines = 2, align = TextAlign.Center)
                }
            } else {
                shown.forEach { b ->
                    val ongoing = ScheduleTimes.isOngoing(b, data.termStart, data.now)
                    Row(modifier = GlanceModifier.fillMaxWidth().height(layout.rowHeight), verticalAlignment = Alignment.CenterVertically) {
                        Badge(WidgetPresentation.sectionLabel(b), accentSoftBg(dark), accentFg(dark), 44.dp)
                        Spacer(GlanceModifier.width(Space.gap.dp))
                        WText(b.name, Type.body.sp, ongoing, if (ongoing) accentFg(dark) else textColor(dark), modifier = GlanceModifier.defaultWeight())
                        if (layout.showColumns) {
                            Spacer(GlanceModifier.width(Space.gap.dp))
                            WText(b.room, Type.label.sp, false, subColor(dark),
                                modifier = GlanceModifier.width(92.dp * layout.fontScale), align = TextAlign.End)
                        }
                    }
                }
                if (layout.showMore(data.lessons.size)) {
                    WText("还有 ${data.lessons.size - shown.size} 堂", Type.caption.sp, false, subColor(dark),
                        modifier = GlanceModifier.fillMaxWidth(), align = TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun ScheduleMedium(data: WidgetData) {
    val dark = dark()
    val layout = widgetLayout()
    val next = data.lessons.firstOrNull()
    SolidCard(openTab = 1) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            WText(if (data.scope == WidgetData.Scope.TOMORROW) "明天下一节"
                else if (layout.contentWidth >= 145f * layout.fontScale && next != null) "下一节 · " + ScheduleTimes.start(next.startSection)
                else "下一节", Type.title.sp, true, textColor(dark))
            Spacer(GlanceModifier.height(Space.gap.dp))
            WText(next?.name ?: WidgetPresentation.scheduleEmpty(data), Type.headline.sp, true, textColor(dark),
                maxLines = if (layout.detailLines >= 2) 2 else 1)
            Spacer(GlanceModifier.defaultWeight())
            if (next != null) {
                if (layout.detailLines >= 1) {
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (layout.contentWidth >= 132f * layout.fontScale) {
                            Badge(WidgetPresentation.sectionLabel(next), accentSoftBg(dark), accentFg(dark))
                            Spacer(GlanceModifier.width(Space.gap.dp))
                        }
                        WText(next.room, Type.label.sp, false, subColor(dark), modifier = GlanceModifier.defaultWeight(), align = TextAlign.End)
                    }
                }
                if (layout.detailLines >= 2) {
                    Spacer(GlanceModifier.height(Space.smallGap.dp))
                    WText(WidgetPresentation.nextCountdown(data.scope, next, data.now.toLocalTime()), Type.label.sp, true, accentFg(dark),
                        maxLines = if (layout.showExtraDetail) 2 else 1)
                }
            } else if (layout.detailLines > 0) {
                WText(WidgetPresentation.scheduleSummary(data), Type.label.sp, false, subColor(dark))
            }
        }
    }
}

@Composable
private fun ScheduleTall(data: WidgetData) {
    val dark = dark()
    val layout = widgetLayout()
    val value = when {
        data.scope == WidgetData.Scope.NO_DATA -> "—"
        data.lessons.isEmpty() -> "无课"
        else -> "${data.lessons.size}"
    }
    val metric = WidgetPresentation.metric(value, layout)
    val showHeader = layout.contentHeight >= layout.line(metric.sizeSp) + layout.line(Type.label) * 2 + 8f
    SolidCard(openTab = 1) {
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (showHeader) {
                if (layout.narrow) CenteredText("课表", Type.label.sp, true, textColor(dark), 1)
                else Badge(data.week?.let { "第 $it 周" } ?: "课表", accentSoftBg(dark), accentFg(dark))
            }
            Spacer(GlanceModifier.defaultWeight())
            CenteredText(metric.text, metric.sizeSp.sp, true, textColor(dark), 1)
            if (data.lessons.isNotEmpty()) {
                CenteredText(if (layout.narrow) "堂" else if (data.scope == WidgetData.Scope.TOMORROW) "明天课程" else "剩余课程",
                    Type.label.sp, false, subColor(dark))
            } else if (data.scope == WidgetData.Scope.NO_DATA && layout.showTallLabel) {
                CenteredText("待导入", Type.label.sp, false, subColor(dark))
            }
            Spacer(GlanceModifier.defaultWeight())
            if (layout.showTallFooter) CenteredText(WidgetPresentation.tallFootnote(data), Type.caption.sp, false, subColor(dark))
        }
    }
}

@Composable
private fun ScheduleRow(data: WidgetData) {
    val dark = dark()
    val layout = widgetLayout()
    val next = data.lessons.firstOrNull()
    SolidCard(openTab = 1) {
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (next != null) {
                if (layout.contentWidth >= 180f * layout.fontScale) {
                    Badge(WidgetPresentation.sectionLabel(next), accentSoftBg(dark), accentFg(dark))
                    Spacer(GlanceModifier.width(Space.gap.dp))
                }
                Column(modifier = GlanceModifier.defaultWeight()) {
                    WText(next.name, Type.body.sp, true, textColor(dark))
                    if (layout.contentHeight >= layout.line(Type.body) + layout.line(Type.label)) {
                        WText(next.room, Type.label.sp, false, subColor(dark))
                    }
                }
                if (layout.showRowDetail) {
                    Spacer(GlanceModifier.width(Space.gap.dp))
                    WText(WidgetPresentation.nextCountdown(data.scope, next, data.now.toLocalTime()), Type.caption.sp, true, accentFg(dark),
                        maxLines = if (layout.contentHeight >= layout.line(Type.caption) * 2) 2 else 1,
                        modifier = GlanceModifier.width(104.dp * layout.fontScale), align = TextAlign.End)
                }
            } else WText(WidgetPresentation.scheduleEmpty(data), Type.body.sp, false, subColor(dark), maxLines = 2)
        }
    }
}

// ── 电量：大字与自适应走势 / 居中竖卡 / 横拉单行 ─────────────────────────────

class PowerWidget : GdustGlanceWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                RefreshAware {
                    val data = WidgetDataSource.load(LocalContext.current)
                    val days = WidgetDataSource.powerRangeDays(LocalContext.current)
                    when (widgetLayout().shape) {
                        WidgetPresentation.Shape.WIDE -> PowerCard(data, days, wide = true)
                        WidgetPresentation.Shape.TALL -> PowerTall(data, days)
                        WidgetPresentation.Shape.ROW -> PowerRow(data, days)
                        WidgetPresentation.Shape.MEDIUM -> PowerCard(data, days, wide = false)
                    }
                }
            }
        }
    }
}

/** 七天 / 半月切换：写入共享偏好后刷新电量款，走势范围随之变化。 */
class PowerRangeToggleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val target = parameters[TARGET] ?: 7
        context.getSharedPreferences("gdust_cache", Context.MODE_PRIVATE)
            .edit().putInt("widget_power_range", target).apply()
        WidgetRefresher.updatePower(context)
    }

    companion object {
        val TARGET = ActionParameters.Key<Int>("target")
    }
}

@Composable
private fun RangeSwitch(current: Int) {
    val dark = dark()
    val inactiveBg = c(Color(WidgetStyle.BACKGROUND_LIGHT), Color(WidgetStyle.BACKGROUND_DARK))
    Row {
        listOf(7 to "七天", 15 to "半月").forEach { (days, label) ->
            val active = current == days
            Box(modifier = GlanceModifier.padding(start = 4.dp).cornerRadius(10.dp)
                .background(if (active) accentSoftBg(dark) else inactiveBg)
                .clickable(actionRunCallback<PowerRangeToggleAction>(actionParametersOf(PowerRangeToggleAction.TARGET to days)))
                .padding(horizontal = 8.dp, vertical = 4.dp), contentAlignment = Alignment.Center,
            ) {
                WText(label, Type.caption.sp, active, if (active) accentFg(dark) else subColor(dark))
            }
        }
    }
}

private fun nowMillis(data: WidgetData): Long =
    data.now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

@Composable
private fun PowerCard(data: WidgetData, days: Int, wide: Boolean) {
    val context = LocalContext.current
    val dark = dark()
    val layout = widgetLayout()
    val density = context.resources.displayMetrics.density
    val samples = WidgetDataSource.trend(context, days)
    val stats = WidgetPresentation.trendStats(samples, data.lastSampleAt, nowMillis(data))
    val chartHeight = layout.chartHeight
    val trendBmp = if (chartHeight > 0.dp && samples.size >= 2) WidgetStyle.trend(context, samples,
        (layout.contentWidth * density).toInt(), (chartHeight.value * density).toInt(), days, data.warnTh) else null
    val valueWidth = layout.contentWidth - layout.line(Type.label) - 8f -
        if (wide && layout.showColumns) 90f * layout.fontScale else 0f
    val metric = WidgetPresentation.metric(WidgetPresentation.powerValue(data.odd), layout.copy(contentWidth = valueWidth.coerceAtLeast(1f)))
    SolidCard(openTab = 2) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val room = data.roomName.ifEmpty { "宿舍电量" }
                WText(if (wide) room else room.substringAfterLast('·').trim(), Type.title.sp, true, textColor(dark),
                    modifier = GlanceModifier.defaultWeight())
                if (wide && layout.showRangeSwitch) {
                    Spacer(GlanceModifier.width(Space.gap.dp))
                    RangeSwitch(days)
                }
            }
            Spacer(GlanceModifier.height(Space.smallGap.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                WText(metric.text, metric.sizeSp.sp, true, if (WidgetPresentation.powerLow(data.odd, data.warnTh)) dangerColor() else textColor(dark))
                Spacer(GlanceModifier.width(Space.smallGap.dp))
                WText("度", Type.label.sp, false, subColor(dark))
                Spacer(GlanceModifier.defaultWeight())
                if (wide && layout.showColumns) WText(WidgetPresentation.rateLabel(stats), Type.caption.sp, false, subColor(dark), align = TextAlign.End)
            }
            Spacer(GlanceModifier.defaultWeight())
            if (trendBmp != null) {
                Image(ImageProvider(trendBmp), "电量走势", modifier = GlanceModifier.fillMaxWidth().height(chartHeight), contentScale = ContentScale.FillBounds)
            } else if (layout.detailLines > 0) {
                // 宿舍名、更新时间各占一行；小尺寸与大字体时先保住读数。
                WText(stats?.updatedLabel?.takeIf { it.isNotEmpty() } ?: "等待采样积累走势", Type.caption.sp, false, subColor(dark), maxLines = 2)
            }
        }
    }
}

@Composable
private fun PowerTall(data: WidgetData, days: Int) {
    val dark = dark()
    val layout = widgetLayout()
    val metric = WidgetPresentation.metric(WidgetPresentation.powerValue(data.odd), layout)
    val showHeader = layout.contentHeight >= layout.line(metric.sizeSp) + layout.line(Type.label) * 2 + 8f
    SolidCard(openTab = 2) {
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (showHeader) {
                if (layout.narrow) CenteredText("电量", Type.label.sp, true, textColor(dark), 1)
                else Badge("电量", accentSoftBg(dark), accentFg(dark))
            }
            Spacer(GlanceModifier.defaultWeight())
            CenteredText(metric.text, metric.sizeSp.sp, true,
                if (WidgetPresentation.powerLow(data.odd, data.warnTh)) dangerColor() else textColor(dark), 1)
            CenteredText("度", Type.label.sp, false, subColor(dark), 1)
            if (layout.showTallFooter) {
                Spacer(GlanceModifier.height(Space.smallGap.dp))
                CenteredText(data.roomName.ifEmpty { "宿舍" }, Type.caption.sp, false, subColor(dark))
            }
            Spacer(GlanceModifier.defaultWeight())
            if (layout.showTallFooter) CenteredText(if (days == 7) "近 7 天" else "近半月", Type.caption.sp, false, subColor(dark))
        }
    }
}

@Composable
private fun PowerRow(data: WidgetData, days: Int) {
    val dark = dark()
    val layout = widgetLayout()
    val showBadge = layout.contentWidth >= 180f * layout.fontScale
    val occupied = layout.line(Type.label) + 8f +
        (if (showBadge) Type.caption * 2 * layout.fontScale + 22f else 0f) +
        (if (layout.showRowDetail) Type.caption * 4 * layout.fontScale + 8f else 0f)
    val metric = WidgetPresentation.metric(WidgetPresentation.powerValue(data.odd),
        layout.copy(contentWidth = (layout.contentWidth - occupied).coerceAtLeast(1f)))
    SolidCard(openTab = 2) {
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (showBadge) {
                Badge("电量", accentSoftBg(dark), accentFg(dark))
                Spacer(GlanceModifier.width(Space.gap.dp))
            }
            WText(metric.text, minOf(metric.sizeSp, layout.contentHeight / (layout.fontScale * 1.3f)).sp, true,
                if (WidgetPresentation.powerLow(data.odd, data.warnTh)) dangerColor() else textColor(dark))
            Spacer(GlanceModifier.width(Space.smallGap.dp))
            WText("度", Type.label.sp, false, subColor(dark))
            Spacer(GlanceModifier.defaultWeight())
            if (layout.showRowDetail) WText(if (days == 7) "近 7 天" else "近半月", Type.caption.sp, false, subColor(dark))
        }
    }
}

// ── 待办：可勾选宽卡列表 / 中卡概览 / 竖卡计数 / 横拉单行 ─────────────────────

class TodoWidget : GdustGlanceWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                RefreshAware {
                    val groups = WidgetDataSource.todos(LocalContext.current)
                    when (widgetLayout().shape) {
                        WidgetPresentation.Shape.WIDE -> TodoWide(groups)
                        WidgetPresentation.Shape.TALL -> TodoTall(groups)
                        WidgetPresentation.Shape.ROW -> TodoRow(groups)
                        WidgetPresentation.Shape.MEDIUM -> TodoMedium(groups)
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoWide(groups: TodoGroups) {
    val context = LocalContext.current
    val dark = dark()
    val layout = widgetLayout()
    val items = WidgetPresentation.pendingTodos(groups, System.currentTimeMillis())
    val shown = items.take(layout.visibleRows(items.size))
    SolidCard(openTab = 3) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WText("待办", Type.title.sp, true, textColor(dark))
                if (groups.overdue.isNotEmpty() && layout.showHeaderSummary) {
                    Spacer(GlanceModifier.width(Space.gap.dp))
                    Badge("${groups.overdue.size} 逾期",
                        c(Color(WidgetStyle.danger(false)).copy(alpha = 0.12f), Color(WidgetStyle.danger(true)).copy(alpha = 0.12f)), dangerColor())
                }
                Spacer(GlanceModifier.defaultWeight())
                WText("共 ${items.size} 项", Type.label.sp, false, subColor(dark), align = TextAlign.End)
            }
            Spacer(GlanceModifier.height(Space.gap.dp))
            if (shown.isEmpty()) {
                Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                    WText(if (items.isEmpty()) "暂无待办，安心休息" else "点按查看待办", Type.body.sp, false, subColor(dark), maxLines = 2, align = TextAlign.Center)
                }
            } else {
                shown.forEach { item ->
                    Row(modifier = GlanceModifier.fillMaxWidth().height(layout.rowHeight).clickable(widgetOpenAction(context, 3, item.todo.id)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val bmp = WidgetStyle.checkbox(context, false, WidgetStyle.dp2px(context, 24))
                        if (bmp != null) {
                            Image(ImageProvider(bmp), "完成待办", modifier = GlanceModifier.width(24.dp).height(24.dp)
                                .clickable(actionRunCallback<CompleteWidgetTodoAction>(actionParametersOf(CompleteWidgetTodoAction.TODO_ID to item.todo.id))))
                            Spacer(GlanceModifier.width(Space.gap.dp))
                        }
                        WText(item.todo.text, Type.body.sp, false, textColor(dark), modifier = GlanceModifier.defaultWeight())
                        if (layout.showColumns) {
                            Spacer(GlanceModifier.width(Space.gap.dp))
                            WText(item.dueLabel, Type.caption.sp, item.overdue, if (item.overdue) dangerColor() else subColor(dark),
                                modifier = GlanceModifier.width(104.dp * layout.fontScale), align = TextAlign.End)
                        }
                    }
                }
                if (layout.showMore(items.size)) {
                    WText("还有 ${items.size - shown.size} 项", Type.caption.sp, false, subColor(dark),
                        modifier = GlanceModifier.fillMaxWidth(), align = TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun TodoMedium(groups: TodoGroups) {
    val dark = dark()
    val layout = widgetLayout()
    val items = WidgetPresentation.pendingTodos(groups, System.currentTimeMillis())
    val metric = WidgetPresentation.metric("${items.size}", layout)
    SolidCard(openTab = 3) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            WText("待办", Type.title.sp, true, textColor(dark))
            Spacer(GlanceModifier.height(Space.smallGap.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                WText(metric.text, metric.sizeSp.sp, true, textColor(dark))
                Spacer(GlanceModifier.width(Space.smallGap.dp))
                WText("项", Type.label.sp, false, subColor(dark))
            }
            if (groups.overdue.isNotEmpty() && layout.showExtraDetail) {
                WText("${groups.overdue.size} 项逾期", Type.label.sp, true, dangerColor())
            }
            Spacer(GlanceModifier.defaultWeight())
            if (layout.detailLines >= 1) {
                val nearest = items.firstOrNull()
                WText(nearest?.todo?.text ?: "暂无待办", Type.body.sp, false, if (nearest != null) textColor(dark) else subColor(dark))
                if (nearest != null && layout.detailLines >= 2) {
                    Spacer(GlanceModifier.height(Space.smallGap.dp))
                    WText(nearest.dueLabel, Type.label.sp, nearest.overdue, if (nearest.overdue) dangerColor() else subColor(dark))
                }
            }
        }
    }
}

@Composable
private fun TodoTall(groups: TodoGroups) {
    val dark = dark()
    val layout = widgetLayout()
    val items = WidgetPresentation.pendingTodos(groups, System.currentTimeMillis())
    val metric = WidgetPresentation.metric("${items.size}", layout)
    val showHeader = layout.contentHeight >= layout.line(metric.sizeSp) + layout.line(Type.label) * 2 + 8f
    SolidCard(openTab = 3) {
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (showHeader) CenteredText("待办", Type.label.sp, true, textColor(dark), 1)
            Spacer(GlanceModifier.defaultWeight())
            CenteredText(metric.text, metric.sizeSp.sp, true, textColor(dark), 1)
            CenteredText(if (layout.narrow) "项" else "项待办", Type.label.sp, false, subColor(dark))
            Spacer(GlanceModifier.defaultWeight())
            if (layout.showTallFooter) {
                CenteredText(if (groups.overdue.isNotEmpty()) "${groups.overdue.size} 项逾期"
                    else if (groups.today.isNotEmpty()) "今日 ${groups.today.size} 项" else "无逾期",
                    Type.caption.sp, groups.overdue.isNotEmpty(), if (groups.overdue.isNotEmpty()) dangerColor() else subColor(dark))
            }
        }
    }
}

@Composable
private fun TodoRow(groups: TodoGroups) {
    val dark = dark()
    val layout = widgetLayout()
    val items = WidgetPresentation.pendingTodos(groups, System.currentTimeMillis())
    SolidCard(openTab = 3) {
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (layout.contentWidth >= 180f * layout.fontScale) {
                Badge("${items.size}", accentSoftBg(dark), accentFg(dark), 36.dp)
                Spacer(GlanceModifier.width(Space.gap.dp))
            }
            val nearest = items.firstOrNull()
            WText(nearest?.todo?.text ?: "暂无待办", Type.body.sp, false,
                if (nearest != null) textColor(dark) else subColor(dark), modifier = GlanceModifier.defaultWeight())
            if (nearest != null && layout.showRowDetail) {
                Spacer(GlanceModifier.width(Space.gap.dp))
                WText(nearest.dueLabel, Type.caption.sp, nearest.overdue, if (nearest.overdue) dangerColor() else subColor(dark),
                    modifier = GlanceModifier.width(104.dp * layout.fontScale), align = TextAlign.End)
            }
        }
    }
}

// ── Receiver：3 类 × 3 尺寸，共用生命周期调度 ───────────────────────────────

class ScheduleWideReceiver : GdustWidgetReceiver() { override val glanceAppWidget = ScheduleWidget() }
class ScheduleMediumReceiver : GdustWidgetReceiver() { override val glanceAppWidget = ScheduleWidget() }
class ScheduleTallReceiver : GdustWidgetReceiver() { override val glanceAppWidget = ScheduleWidget() }

class PowerWideReceiver : GdustWidgetReceiver() { override val glanceAppWidget = PowerWidget() }
class PowerMediumReceiver : GdustWidgetReceiver() { override val glanceAppWidget = PowerWidget() }
class PowerTallReceiver : GdustWidgetReceiver() { override val glanceAppWidget = PowerWidget() }

class TodoWideReceiver : GdustWidgetReceiver() { override val glanceAppWidget = TodoWidget() }
class TodoMediumReceiver : GdustWidgetReceiver() { override val glanceAppWidget = TodoWidget() }
class TodoTallReceiver : GdustWidgetReceiver() { override val glanceAppWidget = TodoWidget() }
