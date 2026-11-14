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
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.menglimi.gdust.v2.data.TodoGroups
import com.menglimi.gdust.v2.data.Todo
import com.menglimi.gdust.v2.data.TodoKind
import com.menglimi.gdust.v2.ui.schedule.ScheduleTimes

/** 画风共享：CZeroX 实底卡 bitmap 背景 + 深浅色自适应文字色；stateDefinition 统一。 */
abstract class GdustGlanceWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
}

// ── 渲染小工具 ──────────────────────────────────────────────────────────────

private fun textStyle(size: TextUnit, bold: Boolean, color: ColorProvider) = TextStyle(
    fontSize = size,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    color = color,
)

private fun c(day: Color, night: Color) = ColorProvider(day, night)

@Composable
private fun dark() = WidgetStyle.isDark(LocalContext.current)

@Composable
private fun WText(
    text: String,
    size: TextUnit,
    bold: Boolean,
    color: ColorProvider,
    maxLines: Int = 1,
    modifier: GlanceModifier = GlanceModifier,
) = Text(text, modifier = modifier, style = textStyle(size, bold, color), maxLines = maxLines)

private fun accentSoftBg(dark: Boolean) = c(WidgetStyle.accentSoft(dark), WidgetStyle.accentSoft(dark))
private fun accentFg(dark: Boolean) = c(Color(WidgetStyle.accent(dark)), Color(WidgetStyle.accent(dark)))
private fun textColor(dark: Boolean) = c(WidgetStyle.text(dark), WidgetStyle.text(dark))
private fun subColor(dark: Boolean) = c(WidgetStyle.sub(dark), WidgetStyle.sub(dark))
private fun dangerColor() = c(Color(WidgetStyle.danger(false)), Color(WidgetStyle.danger(true)))

/** 实底卡容器（保留旧命名）；openTab 提供时整卡可点，进入对应页面（1 课表 / 2 电费 / 3 待办）。 */
@Composable
private fun GlassCard(padding: Dp = 12.dp, openTab: Int? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density
    val bmp = WidgetStyle.glassCard(
        context,
        (size.width.value * density).toInt(),
        (size.height.value * density).toInt(),
        WidgetStyle.radius.value * density,
    )
    var modifier = GlanceModifier.fillMaxSize()
        .background(ImageProvider(bmp), ContentScale.FillBounds)
        .cornerRadius(WidgetStyle.radius)
        .padding(padding)
    if (openTab != null) modifier = modifier.clickable(widgetOpenAction(context, openTab))
    Box(modifier = modifier) { content() }
}

/** 节次 / 类型徽章胶囊；fixedWidth 为空时按文本包裹。 */
@Composable
private fun Badge(text: String, bg: ColorProvider, fg: ColorProvider, fixedWidth: Dp? = null) {
    var modifier = GlanceModifier.cornerRadius(9.dp).background(bg)
        .padding(horizontal = 7.dp, vertical = 3.dp)
    if (fixedWidth != null) modifier = modifier.width(
        fixedWidth * LocalContext.current.resources.configuration.fontScale.coerceAtLeast(1f),
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) { WText(text, 11.sp, true, fg) }
}

/** 渲染时观察刷新版本：活跃 session 的 state 更新也会重新读取缓存。 */
@Composable
private fun RefreshAware(content: @Composable () -> Unit) {
    val refresh = currentState<Preferences>()[WIDGET_REFRESH_VERSION] ?: 0L
    key(refresh) { content() }
}

// ── 课表数据：4×2 今日条子 / 2×2 下一节 / 1×2 竖条 / 横拉单行 ────────────────

class ScheduleWidget : GdustGlanceWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                RefreshAware {
                    val data = WidgetDataSource.load(LocalContext.current)
                    val size = LocalSize.current
                    when (WidgetPresentation.shapeOf(size.width, size.height)) {
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
    GlassCard(openTab = 1) {
        Column {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                WText(WidgetPresentation.scheduleTitle(data), 12.sp, true, textColor(dark), modifier = GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(8.dp))
                WText(WidgetPresentation.scheduleSummary(data), 11.sp, false, subColor(dark))
            }
            Spacer(GlanceModifier.height(6.dp))
            val shown = data.lessons.take(4)
            if (shown.isEmpty()) {
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    WText(WidgetPresentation.scheduleEmpty(data), 12.sp, false, subColor(dark))
                }
            } else {
                shown.forEach { b ->
                    val ongoing = ScheduleTimes.isOngoing(b, data.termStart, data.now)
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Badge(
                            WidgetPresentation.sectionLabel(b),
                            bg = accentSoftBg(dark),
                            fg = accentFg(dark),
                            fixedWidth = 36.dp,
                        )
                        Spacer(GlanceModifier.width(8.dp))
                        WText(b.name, 12.sp, false, textColor(dark), modifier = GlanceModifier.defaultWeight())
                        if (ongoing) {
                            Spacer(GlanceModifier.width(6.dp))
                            WText("上课中", 11.sp, true, accentFg(dark))
                        }
                        Spacer(GlanceModifier.width(6.dp))
                        WText(b.room, 11.sp, false, subColor(dark), modifier = GlanceModifier.width(76.dp))
                    }
                }
                if (data.lessons.size > 4) {
                    Spacer(GlanceModifier.height(2.dp))
                    WText("还有 ${data.lessons.size - 4} 堂…", 11.sp, false, subColor(dark))
                }
            }
        }
    }
}

@Composable
private fun ScheduleMedium(data: WidgetData) {
    val dark = dark()
    val next = data.lessons.firstOrNull()
    GlassCard(openTab = 1) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            WText(
                if (data.scope == WidgetData.Scope.TOMORROW) "下一节 · 明天"
                else next?.let { "下一节 · " + ScheduleTimes.start(it.startSection) } ?: "下一节",
                11.sp, false, subColor(dark),
            )
            Spacer(GlanceModifier.height(6.dp))
            WText(next?.name ?: WidgetPresentation.scheduleEmpty(data), 15.sp, true, textColor(dark), maxLines = 2)
            Spacer(GlanceModifier.defaultWeight())
            if (next != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(WidgetPresentation.sectionLabel(next), bg = accentSoftBg(dark), fg = accentFg(dark))
                    Spacer(GlanceModifier.width(8.dp))
                    WText(next.room, 11.sp, false, subColor(dark), modifier = GlanceModifier.defaultWeight())
                }
                Spacer(GlanceModifier.height(4.dp))
                WText(WidgetPresentation.nextCountdown(data.scope, next, data.now.toLocalTime()), 11.sp, true, accentFg(dark))
            } else {
                WText(WidgetPresentation.scheduleSummary(data), 11.sp, false, subColor(dark))
            }
        }
    }
}

@Composable
private fun ScheduleTall(data: WidgetData) {
    val dark = dark()
    val isTomorrow = data.scope == WidgetData.Scope.TOMORROW
    GlassCard(padding = 10.dp, openTab = 1) {
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Badge(
                data.week?.let { "第 $it 周" } ?: "课表",
                bg = accentSoftBg(dark), fg = accentFg(dark),
            )
            Spacer(GlanceModifier.defaultWeight())
            if (data.scope == WidgetData.Scope.NO_DATA) {
                WText("—", 26.sp, true, textColor(dark))
                WText("待导入", 11.sp, false, subColor(dark))
            } else if (data.lessons.isEmpty()) {
                WText("无课", 18.sp, true, textColor(dark))
            } else {
                WText("${data.lessons.size}", 26.sp, true, textColor(dark))
                WText(if (isTomorrow) "明天课程" else "剩余课程", 11.sp, false, subColor(dark))
            }
            Spacer(GlanceModifier.defaultWeight())
            WText(WidgetPresentation.tallFootnote(data), 11.sp, false, subColor(dark), maxLines = 2)
        }
    }
}

/** 横矮条（1×2 横拉 / 4×1）：紧凑课程信息，倒计时保留独立宽度。 */
@Composable
private fun ScheduleRow(data: WidgetData) {
    val dark = dark()
    val next = data.lessons.firstOrNull()
    GlassCard(padding = 10.dp, openTab = 1) {
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (next != null) {
                Badge(WidgetPresentation.sectionLabel(next), bg = accentSoftBg(dark), fg = accentFg(dark))
                Spacer(GlanceModifier.width(8.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    WText(next.name, 12.sp, true, textColor(dark))
                    WText(next.room, 11.sp, false, subColor(dark))
                }
                Spacer(GlanceModifier.width(8.dp))
                WText(
                    WidgetPresentation.nextCountdown(data.scope, next, data.now.toLocalTime()),
                    11.sp, true, accentFg(dark), maxLines = 2,
                    modifier = GlanceModifier.width(if (LocalSize.current.width < 240.dp) 64.dp else 104.dp),
                )
            } else {
                WText(WidgetPresentation.scheduleEmpty(data), 12.sp, false, subColor(dark))
            }
        }
    }
}

// ── 电量：4×2 走势与切换 / 2×2 大字迷你走势 / 1×2 竖条 / 横拉单行 ────────────

class PowerWidget : GdustGlanceWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val days = WidgetDataSource.powerRangeDays(context)
        provideContent {
            GlanceTheme {
                RefreshAware {
                    val data = WidgetDataSource.load(LocalContext.current)
                    val size = LocalSize.current
                    when (WidgetPresentation.shapeOf(size.width, size.height)) {
                        WidgetPresentation.Shape.WIDE -> PowerWide(data, days)
                        WidgetPresentation.Shape.TALL -> PowerTall(data, days)
                        WidgetPresentation.Shape.ROW -> PowerRow(data, days)
                        WidgetPresentation.Shape.MEDIUM -> PowerMedium(data, days)
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
    val inactiveFg = subColor(dark)
    Row {
        listOf(7 to "七天", 15 to "半月").forEach { (days, label) ->
            val active = current == days
            Box(
                modifier = GlanceModifier.padding(start = 4.dp)
                    .cornerRadius(10.dp)
                    .background(if (active) accentSoftBg(dark) else inactiveBg)
                    .clickable(
                        actionRunCallback<PowerRangeToggleAction>(
                            actionParametersOf(PowerRangeToggleAction.TARGET to days),
                        ),
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                WText(label, 11.sp, active, if (active) accentFg(dark) else inactiveFg)
            }
        }
    }
}

@Composable
private fun powerValueText(data: WidgetData, dark: Boolean, size: TextUnit) {
    val low = WidgetPresentation.powerLow(data.odd, data.warnTh)
    WText(WidgetPresentation.powerValue(data.odd), size, true, if (low) dangerColor() else textColor(dark))
}

private fun nowMillis(data: WidgetData): Long =
    data.now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

@Composable
private fun PowerWide(data: WidgetData, days: Int) {
    val context = LocalContext.current
    val dark = dark()
    val density = context.resources.displayMetrics.density
    val samples = WidgetDataSource.trend(context, days)
    val stats = WidgetPresentation.trendStats(samples, data.lastSampleAt, nowMillis(data))
    val trendBmp = WidgetStyle.trend(
        context, samples,
        ((LocalSize.current.width.value - 24) * density).toInt(), (72 * density).toInt(), days, data.warnTh,
    )
    GlassCard(openTab = 2) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                WText(data.roomName.ifEmpty { "宿舍电量" }, 11.sp, false, subColor(dark), modifier = GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(6.dp))
                RangeSwitch(days)
            }
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                powerValueText(data, dark, 28.sp)
                Spacer(GlanceModifier.width(3.dp))
                WText("度", 11.sp, false, subColor(dark))
                Spacer(GlanceModifier.defaultWeight())
                WText(WidgetPresentation.rateLabel(stats), 11.sp, false, subColor(dark))
            }
            Spacer(GlanceModifier.defaultWeight())
            if (samples.size >= 2 && trendBmp != null) {
                Image(
                    provider = ImageProvider(trendBmp),
                    contentDescription = "电量走势",
                    modifier = GlanceModifier.fillMaxWidth().height(72.dp),
                    contentScale = ContentScale.FillBounds,
                )
            } else {
                Box(modifier = GlanceModifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                    WText(
                        "等待采样积累走势数据" + (stats?.updatedLabel?.let { " · $it" } ?: ""),
                        11.sp, false, subColor(dark),
                    )
                }
            }
        }
    }
}

@Composable
private fun PowerMedium(data: WidgetData, days: Int) {
    val context = LocalContext.current
    val dark = dark()
    val density = context.resources.displayMetrics.density
    val samples = WidgetDataSource.trend(context, days)
    val updated = WidgetPresentation.trendStats(samples, data.lastSampleAt, nowMillis(data))?.updatedLabel.orEmpty()
    val trendBmp = WidgetStyle.trend(
        context, samples,
        ((LocalSize.current.width.value - 24) * density).toInt(), (48 * density).toInt(), days, data.warnTh,
    )
    GlassCard(openTab = 2) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                WText(data.roomName.ifEmpty { "宿舍电量" }, 11.sp, false, subColor(dark), modifier = GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(6.dp))
                WText(updated, 11.sp, false, subColor(dark))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                powerValueText(data, dark, 26.sp)
                Spacer(GlanceModifier.width(3.dp))
                WText("度", 11.sp, false, subColor(dark))
            }
            Spacer(GlanceModifier.defaultWeight())
            if (samples.size >= 2 && trendBmp != null) {
                Image(
                    provider = ImageProvider(trendBmp),
                    contentDescription = "电量走势",
                    modifier = GlanceModifier.fillMaxWidth().height(48.dp),
                    contentScale = ContentScale.FillBounds,
                )
            }
        }
    }
}

@Composable
private fun PowerTall(data: WidgetData, days: Int) {
    val dark = dark()
    GlassCard(padding = 10.dp, openTab = 2) {
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Badge("电量", bg = accentSoftBg(dark), fg = accentFg(dark))
            Spacer(GlanceModifier.defaultWeight())
            powerValueText(data, dark, 20.sp)
            WText("度 · " + data.roomName.ifEmpty { "宿舍" }, 11.sp, false, subColor(dark), maxLines = 2)
            Spacer(GlanceModifier.defaultWeight())
            WText(if (days == 7) "近 7 天" else "近半月", 11.sp, false, subColor(dark))
        }
    }
}

@Composable
private fun PowerRow(data: WidgetData, days: Int) {
    val dark = dark()
    GlassCard(padding = 10.dp, openTab = 2) {
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Badge("电量", bg = accentSoftBg(dark), fg = accentFg(dark))
            Spacer(GlanceModifier.width(8.dp))
            powerValueText(data, dark, 16.sp)
            Spacer(GlanceModifier.width(3.dp))
            WText("度", 11.sp, false, subColor(dark))
            Spacer(GlanceModifier.defaultWeight())
            WText(if (days == 7) "近 7 天" else "近半月", 11.sp, false, subColor(dark))
        }
    }
}

// ── 待办条子：4×2 可勾选列表 / 2×2 概览 / 1×2 计数竖条 / 横拉单行 ────────────

class TodoWidget : GdustGlanceWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                RefreshAware {
                    val groups = WidgetDataSource.todos(LocalContext.current)
                    val size = LocalSize.current
                    when (WidgetPresentation.shapeOf(size.width, size.height)) {
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
    val items = WidgetPresentation.pendingTodos(groups, System.currentTimeMillis())
    GlassCard(openTab = 3) {
        Column {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WText("待办", 12.sp, true, textColor(dark))
                if (groups.overdue.isNotEmpty()) {
                    Spacer(GlanceModifier.width(6.dp))
                    Badge(
                        "${groups.overdue.size} 逾期",
                        bg = c(Color(WidgetStyle.danger(false)).copy(alpha = 0.12f), Color(WidgetStyle.danger(true)).copy(alpha = 0.12f)),
                        fg = dangerColor(),
                    )
                }
                Spacer(GlanceModifier.defaultWeight())
                WText("共 ${items.size} 项", 11.sp, false, subColor(dark))
            }
            Spacer(GlanceModifier.height(4.dp))
            if (items.isEmpty()) {
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    WText("没有待办，好好休息 ☕", 12.sp, false, subColor(dark))
                }
            } else {
                items.take(4).forEach { item ->
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)
                            .clickable(widgetOpenAction(context, 3, item.todo.id)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val bmp = WidgetStyle.checkbox(context, false, WidgetStyle.dp2px(context, 20))
                        if (bmp != null) {
                            Image(
                                provider = ImageProvider(bmp),
                                contentDescription = "完成待办",
                                modifier = GlanceModifier.width(20.dp).height(20.dp)
                                    .clickable(
                                        actionRunCallback<CompleteWidgetTodoAction>(
                                            actionParametersOf(CompleteWidgetTodoAction.TODO_ID to item.todo.id),
                                        ),
                                    ),
                            )
                            Spacer(GlanceModifier.width(6.dp))
                        }
                        WText(item.todo.text, 12.sp, false, textColor(dark), modifier = GlanceModifier.defaultWeight())
                        Spacer(GlanceModifier.width(6.dp))
                        WText(item.dueLabel, 11.sp, item.overdue, if (item.overdue) dangerColor() else subColor(dark))
                    }
                }
                if (items.size > 4) {
                    Spacer(GlanceModifier.height(2.dp))
                    WText("还有 ${items.size - 4} 条…", 11.sp, false, subColor(dark))
                }
            }
        }
    }
}

@Composable
private fun KindBadge(t: Todo) {
    val dark = dark()
    val danger = Color(WidgetStyle.danger(dark))
    val accent = Color(WidgetStyle.accent(dark))
    val (label, bg, fg) = when (t.kind) {
        TodoKind.EXAM -> Triple("考", danger.copy(alpha = 0.12f), danger)
        TodoKind.HOMEWORK -> Triple("作", WidgetStyle.accentSoft(dark), accent)
        TodoKind.GENERAL -> Triple("事", WidgetStyle.sub(dark).copy(alpha = 0.18f), WidgetStyle.sub(dark))
    }
    Badge(label, bg = c(bg, bg), fg = c(fg, fg), fixedWidth = 26.dp)
}

@Composable
private fun TodoMedium(groups: TodoGroups) {
    val dark = dark()
    val items = WidgetPresentation.pendingTodos(groups, System.currentTimeMillis())
    val nearest = items.firstOrNull()
    GlassCard(openTab = 3) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            WText("待办", 11.sp, false, subColor(dark))
            Row(verticalAlignment = Alignment.Bottom) {
                WText("${items.size}", 26.sp, true, textColor(dark))
                Spacer(GlanceModifier.width(4.dp))
                WText("项", 11.sp, false, subColor(dark))
            }
            if (groups.overdue.isNotEmpty()) {
                Spacer(GlanceModifier.height(2.dp))
                WText("${groups.overdue.size} 项逾期", 11.sp, true, dangerColor())
            }
            Spacer(GlanceModifier.defaultWeight())
            if (nearest != null) {
                WText(nearest.todo.text, 11.sp, false, textColor(dark))
                WText(nearest.dueLabel, 11.sp, nearest.overdue, if (nearest.overdue) dangerColor() else subColor(dark))
            } else {
                WText("暂无待办", 11.sp, false, subColor(dark))
            }
        }
    }
}

@Composable
private fun TodoTall(groups: TodoGroups) {
    val dark = dark()
    val items = WidgetPresentation.pendingTodos(groups, System.currentTimeMillis())
    GlassCard(padding = 10.dp, openTab = 3) {
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            WText("待办", 11.sp, true, textColor(dark))
            Spacer(GlanceModifier.defaultWeight())
            WText("${items.size}", 26.sp, true, textColor(dark))
            WText("条待办", 11.sp, false, subColor(dark))
            Spacer(GlanceModifier.defaultWeight())
            if (groups.overdue.isNotEmpty()) {
                WText("${groups.overdue.size} 逾期", 11.sp, true, dangerColor())
            } else {
                val today = groups.today.size
                WText(if (today > 0) "今日 $today 项" else "无逾期", 11.sp, false, subColor(dark))
            }
        }
    }
}

@Composable
private fun TodoRow(groups: TodoGroups) {
    val dark = dark()
    val items = WidgetPresentation.pendingTodos(groups, System.currentTimeMillis())
    GlassCard(padding = 10.dp, openTab = 3) {
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Badge("${items.size}", bg = accentSoftBg(dark), fg = accentFg(dark), fixedWidth = 30.dp)
            Spacer(GlanceModifier.width(8.dp))
            val nearest = items.firstOrNull()
            if (nearest != null) {
                WText(nearest.todo.text, 12.sp, false, textColor(dark), modifier = GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(6.dp))
                WText(
                    nearest.dueLabel, 11.sp, nearest.overdue,
                    if (nearest.overdue) dangerColor() else subColor(dark), maxLines = 2,
                    modifier = GlanceModifier.width(if (LocalSize.current.width < 240.dp) 64.dp else 88.dp),
                )
            } else {
                WText("暂无待办", 12.sp, false, subColor(dark))
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
