package com.menglimi.gdust.v2.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
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
import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.data.Todo
import com.menglimi.gdust.v2.data.TodoGroups
import com.menglimi.gdust.v2.data.TodoKind
import com.menglimi.gdust.v2.ui.schedule.ScheduleTimes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 统一刷新入口：课表 / 电量 / 待办三类小部件（各含 4×2 / 2×2 / 1×2 三款）。 */
object WidgetRefresher {
    fun refreshAll(context: Context) {
        CoroutineScope(Dispatchers.Default).launch {
            ScheduleWidget().updateAll(context)
            PowerWidget().updateAll(context)
            TodoWidget().updateAll(context)
        }
    }
}

/** 画风共享：玻璃卡 bitmap 背景 + 深浅色自适应文字色。 */
abstract class GdustGlanceWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
}

// ── 小工具 ─────────────────────────────────────────────────────────────────

private fun textStyle(size: TextUnit, bold: Boolean, color: ColorProvider) = TextStyle(
    fontSize = size,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    color = color,
)

private fun c(day: Color, night: Color) = ColorProvider(day, night)

@Composable
private fun dark() = WidgetStyle.isDark(LocalContext.current)

@Composable
private fun WText(text: String, size: TextUnit, bold: Boolean, color: ColorProvider, maxLines: Int = 1) =
    Text(text, style = textStyle(size, bold, color), maxLines = maxLines)

/** 玻璃卡容器：所有小部件的最外层（半透明柔光底 + 扫光 + 细描边）。 */
@Composable
private fun GlassCard(padding: Dp = 12.dp, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val size = LocalSize.current
    val dark = dark()
    val density = context.resources.displayMetrics.density
    val bmp = WidgetStyle.glassCard(
        context,
        (size.width.value * density).toInt(),
        (size.height.value * density).toInt(),
        WidgetStyle.radius.value * density,
    )
    Box(
        modifier = GlanceModifier.fillMaxSize()
            .background(ImageProvider(bmp), ContentScale.FillBounds)
            .cornerRadius(WidgetStyle.radius)
            .padding(padding),
    ) { content() }
}

/** 节次 / 类型徽章胶囊；fixedWidth 为空时按文本包裹。 */
@Composable
private fun Badge(text: String, bg: ColorProvider, fg: ColorProvider, fixedWidth: Dp? = null) {
    var modifier = GlanceModifier.cornerRadius(9.dp).background(bg)
        .padding(horizontal = 7.dp, vertical = 3.dp)
    if (fixedWidth != null) modifier = modifier.width(fixedWidth)
    Box(modifier = modifier, contentAlignment = Alignment.Center) { WText(text, 10.sp, true, fg) }
}

private fun accentSoftBg(dark: Boolean) = c(WidgetStyle.accentSoft(dark), WidgetStyle.accentSoft(dark))
private fun accentFg(dark: Boolean) = c(Color(WidgetStyle.accent(dark)), Color(WidgetStyle.accent(dark)))
private fun textColor(dark: Boolean) = c(WidgetStyle.text(dark), WidgetStyle.text(dark))
private fun subColor(dark: Boolean) = c(WidgetStyle.sub(dark), WidgetStyle.sub(dark))

// ── 课表数据 widget：4×2 今日条子 / 2×2 下一节 / 1×2 竖条进度 ───────────────

class ScheduleWidget : GdustGlanceWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataSource.load(context)
        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                when {
                    size.height > size.width -> ScheduleTall(data)
                    size.width >= 240.dp -> ScheduleWide(data)
                    else -> ScheduleMedium(data)
                }
            }
        }
    }
}

@Composable
private fun ScheduleWide(data: WidgetData) {
    val dark = dark()
    GlassCard {
        Column {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                WText(data.title(), 12.sp, true, textColor(dark))
                Spacer(GlanceModifier.defaultWeight())
                WText(scheduleSummary(data), 10.sp, false, subColor(dark))
            }
            Spacer(GlanceModifier.height(6.dp))
            val shown = data.lessons.take(4)
            if (shown.isEmpty()) {
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    WText(emptyText(data), 12.sp, false, subColor(dark))
                }
            } else {
                shown.forEach { b ->
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Badge(
                            sectionLabel(b),
                            bg = accentSoftBg(dark),
                            fg = accentFg(dark),
                            fixedWidth = 36.dp,
                        )
                        Spacer(GlanceModifier.width(8.dp))
                        WText(b.name, 12.sp, false, textColor(dark))
                        Spacer(GlanceModifier.defaultWeight())
                        WText(b.room, 10.sp, false, subColor(dark))
                    }
                }
                if (data.lessons.size > 4) {
                    Spacer(GlanceModifier.height(2.dp))
                    WText("还有 ${data.lessons.size - 4} 节…", 10.sp, false, subColor(dark))
                }
            }
        }
    }
}

private fun sectionLabel(b: CourseBlock): String =
    if (b.endSection > b.startSection) "${b.startSection}-${b.endSection}" else "${b.startSection}节"

@Composable
private fun ScheduleMedium(data: WidgetData) {
    val dark = dark()
    val next = data.lessons.firstOrNull()
    GlassCard {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            WText(if (data.scope == WidgetData.Scope.TOMORROW) "下一节 · 明天" else "下一节", 10.sp, false, subColor(dark))
            Spacer(GlanceModifier.height(6.dp))
            WText(next?.name ?: emptyText(data), 15.sp, true, textColor(dark), maxLines = 2)
            Spacer(GlanceModifier.defaultWeight())
            if (next != null) {
                WText(
                    sectionLabel(next) + " · " + next.room,
                    10.sp, false, subColor(dark),
                )
                Spacer(GlanceModifier.height(4.dp))
                WText(nextCountdown(data, next), 11.sp, true, accentFg(dark))
            } else {
                WText(scheduleSummary(data), 10.sp, false, subColor(dark))
            }
        }
    }
}

@Composable
private fun ScheduleTall(data: WidgetData) {
    val dark = dark()
    val isTomorrow = data.scope == WidgetData.Scope.TOMORROW
    GlassCard(padding = 10.dp) {
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Badge("第 ${data.week} 周", bg = accentSoftBg(dark), fg = accentFg(dark))
            Spacer(GlanceModifier.defaultWeight())
            if (data.lessons.isEmpty()) {
                WText("无课", 18.sp, true, textColor(dark))
            } else {
                WText("${data.lessons.size}", 26.sp, true, textColor(dark))
                WText(if (isTomorrow) "明天节数" else "剩余节数", 9.sp, false, subColor(dark))
            }
            Spacer(GlanceModifier.defaultWeight())
            WText(tallFootnote(data), 9.sp, false, subColor(dark), maxLines = 2)
        }
    }
}

// ── 电量 widget：4×2 带七天/半月切换与底部走势数据线 ────────────────────────

class PowerWidget : GdustGlanceWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataSource.load(context)
        val days = WidgetDataSource.powerRangeDays(context)
        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                when {
                    size.height > size.width -> PowerTall(data, days)
                    size.width >= 240.dp -> PowerWide(data, days)
                    else -> PowerMedium(data, days)
                }
            }
        }
    }
}

/** 七天 / 半月切换：写入共享偏好后重渲全部电量款，走势范围随之变化。 */
class PowerRangeToggleAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val target = parameters[TARGET] ?: 7
        context.getSharedPreferences("gdust_cache", Context.MODE_PRIVATE)
            .edit().putInt("widget_power_range", target).apply()
        WidgetRefresher.refreshAll(context)
    }

    companion object {
        val TARGET = ActionParameters.Key<Int>("target")
    }
}

@Composable
private fun RangeSwitch(current: Int) {
    val dark = dark()
    val inactiveBg = c(WidgetStyle.sub(dark).copy(alpha = 0.16f), WidgetStyle.sub(dark).copy(alpha = 0.16f))
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
                WText(label, 10.sp, active, if (active) accentFg(dark) else inactiveFg)
            }
        }
    }
}

@Composable
private fun PowerWide(data: WidgetData, days: Int) {
    val context = LocalContext.current
    val dark = dark()
    val density = context.resources.displayMetrics.density
    val samples = WidgetDataSource.trend(context, days)
    val trendBmp = WidgetStyle.trend(
        context, samples,
        ((LocalSize.current.width.value - 24) * density).toInt(), (44 * density).toInt(), days,
    )
    GlassCard {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    WText(data.roomName.ifEmpty { "宿舍电量" }, 10.sp, false, subColor(dark))
                    Row(verticalAlignment = Alignment.Bottom) {
                        WText(powerValue(data), 28.sp, true, c(powerColor(data, dark), powerColor(data, dark)))
                        Spacer(GlanceModifier.width(3.dp))
                        WText("度", 11.sp, false, subColor(dark))
                    }
                }
                RangeSwitch(days)
            }
            Spacer(GlanceModifier.defaultWeight())
            if (trendBmp != null) {
                Image(
                    provider = ImageProvider(trendBmp),
                    contentDescription = "电量走势",
                    modifier = GlanceModifier.fillMaxWidth().height(44.dp),
                    contentScale = ContentScale.FillBounds,
                )
            } else {
                Box(modifier = GlanceModifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                    WText("等待采样积累走势数据…", 10.sp, false, subColor(dark))
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
    val trendBmp = WidgetStyle.trend(
        context, samples,
        ((LocalSize.current.width.value - 24) * density).toInt(), (26 * density).toInt(), days,
    )
    GlassCard {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            WText(data.roomName.ifEmpty { "宿舍电量" }, 10.sp, false, subColor(dark))
            Row(verticalAlignment = Alignment.Bottom) {
                WText(powerValue(data), 26.sp, true, c(powerColor(data, dark), powerColor(data, dark)))
                Spacer(GlanceModifier.width(3.dp))
                WText("度", 10.sp, false, subColor(dark))
            }
            Spacer(GlanceModifier.defaultWeight())
            if (trendBmp != null) {
                Image(
                    provider = ImageProvider(trendBmp),
                    contentDescription = "电量走势",
                    modifier = GlanceModifier.fillMaxWidth().height(26.dp),
                    contentScale = ContentScale.FillBounds,
                )
            }
        }
    }
}

@Composable
private fun PowerTall(data: WidgetData, days: Int) {
    val dark = dark()
    GlassCard(padding = 10.dp) {
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Badge("电量", bg = accentSoftBg(dark), fg = accentFg(dark))
            Spacer(GlanceModifier.defaultWeight())
            WText(powerValue(data), 20.sp, true, c(powerColor(data, dark), powerColor(data, dark)))
            WText("度 · " + data.roomName.ifEmpty { "宿舍" }, 8.sp, false, subColor(dark), maxLines = 2)
            Spacer(GlanceModifier.defaultWeight())
            WText(if (days == 7) "近 7 天" else "近半月", 8.sp, false, subColor(dark))
        }
    }
}

// ── 待办条子 widget ─────────────────────────────────────────────────────────

class TodoWidget : GdustGlanceWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val groups = WidgetDataSource.todos(context)
        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                when {
                    size.height > size.width -> TodoTall(groups)
                    size.width >= 240.dp -> TodoWide(groups)
                    else -> TodoMedium(groups)
                }
            }
        }
    }
}

private fun pending(groups: TodoGroups) = groups.overdue + groups.today + groups.thisWeek + groups.later + groups.noDue

@Composable
private fun TodoWide(groups: TodoGroups) {
    val dark = dark()
    val danger = Color(WidgetStyle.DANGER)
    val dangerC = c(danger, danger)
    val items = pending(groups)
    GlassCard {
        Column {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WText("待办", 12.sp, true, textColor(dark))
                if (groups.overdue.isNotEmpty()) {
                    Spacer(GlanceModifier.width(6.dp))
                    Badge(
                        "${groups.overdue.size} 逾期",
                        bg = c(danger.copy(alpha = 0.15f), danger.copy(alpha = 0.22f)),
                        fg = dangerC,
                    )
                }
                Spacer(GlanceModifier.defaultWeight())
                WText("共 ${items.size} 项", 10.sp, false, subColor(dark))
            }
            Spacer(GlanceModifier.height(6.dp))
            if (items.isEmpty()) {
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    WText("没有待办，好好休息 ☕", 12.sp, false, subColor(dark))
                }
            } else {
                items.take(4).forEach { t ->
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KindBadge(t)
                        Spacer(GlanceModifier.width(8.dp))
                        WText(t.text, 12.sp, false, textColor(dark))
                        Spacer(GlanceModifier.defaultWeight())
                        val (label, warn) = dueText(t)
                        WText(label, 10.sp, warn, if (warn) dangerC else subColor(dark))
                    }
                }
                if (items.size > 4) {
                    Spacer(GlanceModifier.height(2.dp))
                    WText("还有 ${items.size - 4} 条…", 10.sp, false, subColor(dark))
                }
            }
        }
    }
}

@Composable
private fun KindBadge(t: Todo) {
    val dark = dark()
    val danger = Color(WidgetStyle.DANGER)
    val accent = Color(WidgetStyle.accent(dark))
    val (label, bg, fg) = when (t.kind) {
        TodoKind.EXAM -> Triple("考", danger.copy(alpha = if (dark) 0.22f else 0.14f), danger)
        TodoKind.HOMEWORK -> Triple("作", WidgetStyle.accentSoft(dark), accent)
        TodoKind.GENERAL -> Triple("事", WidgetStyle.sub(dark).copy(alpha = 0.16f), WidgetStyle.sub(dark))
    }
    Badge(label, bg = c(bg, bg), fg = c(fg, fg), fixedWidth = 26.dp)
}

@Composable
private fun TodoMedium(groups: TodoGroups) {
    val dark = dark()
    val danger = Color(WidgetStyle.DANGER)
    val items = pending(groups)
    val nearest = items.firstOrNull { it.dueAt > 0 } ?: items.firstOrNull()
    GlassCard {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            WText("待办", 10.sp, false, subColor(dark))
            Row(verticalAlignment = Alignment.Bottom) {
                WText("${items.size}", 26.sp, true, textColor(dark))
                Spacer(GlanceModifier.width(4.dp))
                WText("项", 10.sp, false, subColor(dark))
            }
            if (groups.overdue.isNotEmpty()) {
                Spacer(GlanceModifier.height(2.dp))
                WText("${groups.overdue.size} 项逾期", 10.sp, true, c(danger, danger))
            }
            Spacer(GlanceModifier.defaultWeight())
            if (nearest != null) {
                WText(nearest.text, 10.sp, false, textColor(dark))
                WText(dueText(nearest).first, 9.sp, false, subColor(dark))
            } else {
                WText("暂无待办", 10.sp, false, subColor(dark))
            }
        }
    }
}

@Composable
private fun TodoTall(groups: TodoGroups) {
    val dark = dark()
    val danger = Color(WidgetStyle.DANGER)
    val items = pending(groups)
    GlassCard(padding = 10.dp) {
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            WText("待办", 11.sp, true, textColor(dark))
            Spacer(GlanceModifier.defaultWeight())
            WText("${items.size}", 26.sp, true, textColor(dark))
            WText("条待办", 9.sp, false, subColor(dark))
            Spacer(GlanceModifier.defaultWeight())
            if (groups.overdue.isNotEmpty()) {
                WText("${groups.overdue.size} 逾期", 9.sp, true, c(danger, danger))
            } else {
                val today = groups.today.size
                WText(if (today > 0) "今日 $today 项" else "无逾期", 9.sp, false, subColor(dark))
            }
        }
    }
}

// ── 文案 helper ─────────────────────────────────────────────────────────────

private fun powerValue(data: WidgetData): String = data.odd?.let { "%.1f".format(it) } ?: "--"

private fun powerColor(data: WidgetData, dark: Boolean): Color {
    val low = data.odd != null && data.odd < data.warnTh
    return if (low) Color(WidgetStyle.DANGER) else Color(WidgetStyle.accent(dark))
}

private fun scheduleSummary(data: WidgetData): String = when (data.scope) {
    WidgetData.Scope.TODAY_REST, WidgetData.Scope.TODAY_ALL -> "剩 ${data.lessons.size} 节"
    WidgetData.Scope.TOMORROW -> "明天 ${data.lessons.size} 节"
    WidgetData.Scope.EMPTY -> "暂无课表"
}

private fun emptyText(data: WidgetData): String = when (data.scope) {
    WidgetData.Scope.TOMORROW -> "明天没有课程"
    WidgetData.Scope.EMPTY -> "暂无课表数据"
    else -> "今天课程都结束啦"
}

private fun tallFootnote(data: WidgetData): String = when (data.scope) {
    WidgetData.Scope.TOMORROW -> "今天已结束"
    WidgetData.Scope.TODAY_REST -> "今日剩 ${data.lessons.size} 节"
    WidgetData.Scope.TODAY_ALL -> "今天 ${data.lessons.size} 节"
    WidgetData.Scope.EMPTY -> "导入课表后显示"
}

private fun nextCountdown(data: WidgetData, b: CourseBlock): String {
    if (data.scope == WidgetData.Scope.TOMORROW) return "明天见"
    val start = ScheduleTimes.start(b.startSection)
    val end = ScheduleTimes.end(b.endSection)
    val now = LocalTime.now()
    val startT = runCatching { LocalTime.parse(start) }.getOrNull()
    val endT = runCatching { LocalTime.parse(end) }.getOrNull()
    return when {
        startT == null -> "第 ${b.startSection} 节"
        now.isBefore(startT) -> {
            val mins = ChronoUnit.MINUTES.between(now, startT)
            if (mins >= 90) "%.1f 小时后".format(mins / 60.0) else "$mins 分钟后"
        }
        endT != null && now.isBefore(endT) -> "上课中 · ${ChronoUnit.MINUTES.between(now, endT)} 分后下课"
        else -> "上课中"
    }
}

/** 截止文案：逾期 / 今天 / 明天 / 周X / MM-dd；第二个值 = 是否逾期标红。 */
private fun dueText(t: Todo): Pair<String, Boolean> {
    if (t.dueAt <= 0L) return "无截止" to false
    val zone = ZoneId.systemDefault()
    val d = Instant.ofEpochMilli(t.dueAt).atZone(zone)
    val today = java.time.LocalDate.now(zone)
    val date = d.toLocalDate()
    val hm = "%02d:%02d".format(d.hour, d.minute)
    val diff = ChronoUnit.DAYS.between(today, date)
    return when {
        diff < 0 -> "逾期 ${-diff} 天" to true
        diff == 0L -> "今天 $hm" to false
        diff == 1L -> "明天 $hm" to false
        diff in 2..6 -> "${ScheduleTimes.dayNames[date.dayOfWeek.value - 1]} $hm" to false
        else -> "%02d/%02d %s".format(date.monthValue, date.dayOfMonth, hm) to false
    }
}

// ── Receiver：3 类 × 3 尺寸 ─────────────────────────────────────────────────

class ScheduleWideReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = ScheduleWidget() }
class ScheduleMediumReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = ScheduleWidget() }
class ScheduleTallReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = ScheduleWidget() }

class PowerWideReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = PowerWidget() }
class PowerMediumReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = PowerWidget() }
class PowerTallReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = PowerWidget() }

class TodoWideReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = TodoWidget() }
class TodoMediumReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = TodoWidget() }
class TodoTallReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = TodoWidget() }
