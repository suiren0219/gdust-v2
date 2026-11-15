package com.menglimi.gdust.v2.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.data.Store
import com.menglimi.gdust.v2.data.Todo
import com.menglimi.gdust.v2.data.TodoGroups
import com.menglimi.gdust.v2.ui.schedule.ScheduleTimes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 小部件展示层的纯函数集合：形状分档、标题口径、截止与倒计时文案、走势统计。
 * 不依赖 Android 运行时（时间由调用方注入），可被 JUnit 直接覆盖。
 */
object WidgetPresentation {

    // ── 形状分档：竖条 / 横矮条 / 横宽卡 / 方卡，1×2 拉横会落到 ROW ────────────

    enum class Shape { WIDE, MEDIUM, TALL, ROW }

    fun shapeOf(width: Dp, height: Dp): Shape = when {
        height > width * 1.05f -> Shape.TALL
        height < 92.dp && width > height -> Shape.ROW
        width >= height * 1.9f || width >= 240.dp -> Shape.WIDE
        else -> Shape.MEDIUM
    }

    /** 尺寸预算使用字体放大后的行高；空间不够时隐藏次要内容，不把正文缩到 11sp。 */
    data class Layout(
        val shape: Shape,
        val padding: Dp,
        val contentWidth: Float,
        val contentHeight: Float,
        val fontScale: Float,
    ) {
        fun line(sp: Float): Float = sp * fontScale * 1.3f
        val headerHeight: Float get() = line(WidgetStyle.Type.title) + WidgetStyle.Space.smallGap
        val rowHeight: Dp get() = maxOf(WidgetStyle.Space.row, line(WidgetStyle.Type.body) + 12f).dp
        val narrow: Boolean get() = contentWidth < 72f * fontScale
        val showHeaderSummary: Boolean get() = contentWidth >= 245f * fontScale
        val showColumns: Boolean get() = contentWidth >= 254f * fontScale
        val showRangeSwitch: Boolean get() = contentWidth >= 250f * fontScale
        val showRowDetail: Boolean get() = contentWidth >= 225f * fontScale
        val numberSp: Float get() = if (contentWidth < 110f * fontScale) WidgetStyle.Type.compactNumber else WidgetStyle.Type.number
        val showTallFooter: Boolean get() = !narrow && contentHeight >= line(numberSp) + line(WidgetStyle.Type.label) * 5 + 16f
        val showTallLabel: Boolean get() = contentHeight >= line(numberSp) + line(WidgetStyle.Type.label) * 2 + 8f

        fun visibleRows(total: Int): Int {
            if (total <= 0) return 0
            val available = contentHeight - headerHeight - WidgetStyle.Space.gap
            return minOf(total, 4, (available / rowHeight.value).toInt().coerceAtLeast(0))
        }

        fun showMore(total: Int): Boolean = total > visibleRows(total) &&
            contentHeight - headerHeight - WidgetStyle.Space.gap - visibleRows(total) * rowHeight.value >= line(WidgetStyle.Type.caption)

        /** 中卡先保住标题、数字/课程名，再按剩余空间给一行或两行详情。 */
        val detailLines: Int get() = ((contentHeight - headerHeight - line(WidgetStyle.Type.number) - 12f) /
            (line(WidgetStyle.Type.body) + WidgetStyle.Space.smallGap)).toInt().coerceIn(0, 2)
        val showExtraDetail: Boolean get() = detailLines == 2 && contentHeight >=
            headerHeight + line(WidgetStyle.Type.number) + line(WidgetStyle.Type.body) * 3 + 20f

        /** 图轴至少留 12sp 刻度和 24dp 绘图区；放不下就保留大字与更新时间。 */
        val chartHeight: Dp get() {
            val available = contentHeight - headerHeight - line(WidgetStyle.Type.number) - WidgetStyle.Space.gap
            val minimum = line(WidgetStyle.Type.caption) * 2 + 24f
            return if (contentWidth < 128f * fontScale || available < minimum) 0.dp
            else minOf(available, 96f).dp
        }
    }

    fun layout(width: Dp, height: Dp, fontScale: Float): Layout {
        val scale = fontScale.takeIf { it.isFinite() && it > 0f }?.coerceAtLeast(1f) ?: 1f
        val shape = shapeOf(width, height)
        val padding = when {
            width.value < 80f * scale -> WidgetStyle.Space.narrowCard
            shape == Shape.TALL || shape == Shape.ROW || height < 150.dp || width < 170.dp -> WidgetStyle.Space.compactCard
            else -> WidgetStyle.Space.card
        }
        return Layout(shape, padding.dp, (width.value - padding * 2).coerceAtLeast(1f),
            (height.value - padding * 2).coerceAtLeast(1f), scale)
    }

    data class Metric(val text: String, val sizeSp: Float)

    /** 只对核心数字做适配，保留完整小数与符号；不把 19.9 度悄悄取整成 20 度。 */
    fun metric(value: String, layout: Layout): Metric {
        val units = value.sumOf {
            when (it) {
                '.', ',' -> 0.30
                in '0'..'9' -> 0.62
                '-', '—' -> 0.65
                else -> 1.0
            }
        }.toFloat().coerceAtLeast(1f)
        val height = when (layout.shape) {
            Shape.TALL -> layout.contentHeight - layout.line(WidgetStyle.Type.label)
            Shape.ROW -> layout.contentHeight
            else -> layout.contentHeight - layout.headerHeight - WidgetStyle.Space.smallGap
        }
        val fitWidth = layout.contentWidth / (units * layout.fontScale)
        val fitHeight = height / (1.3f * layout.fontScale)
        return Metric(value, minOf(layout.numberSp, fitWidth, fitHeight).coerceAtLeast(WidgetStyle.Type.caption / layout.fontScale))
    }

    // ── 课表口径：以"堂"计数，标题随展示日期 ─────────────────────────────────

    /** 标题行：今天/明天各用各自日期的星期；周次未知（开学前/无校历）不硬编。 */
    fun scheduleTitle(data: WidgetData): String = when (data.scope) {
        WidgetData.Scope.TODAY_REST, WidgetData.Scope.TODAY_ALL ->
            "今天" + (data.week?.let { " · 第 $it 周" } ?: "")
        WidgetData.Scope.TOMORROW -> "明天 · ${ScheduleTimes.dayNames[data.scopeDate.dayOfWeek.value - 1]}"
        WidgetData.Scope.NO_LESSONS -> "暂无课程"
        WidgetData.Scope.NO_DATA -> "广科课表"
    }

    /** 右上角摘要：剩余按堂数（张卡），不再把一张多节卡当成多节。 */
    fun scheduleSummary(data: WidgetData): String = when (data.scope) {
        WidgetData.Scope.TODAY_REST -> "剩 ${data.lessons.size} 堂"
        WidgetData.Scope.TODAY_ALL -> "今日 ${data.lessons.size} 堂"
        WidgetData.Scope.TOMORROW -> "明天 ${data.lessons.size} 堂"
        WidgetData.Scope.NO_LESSONS -> "今明无课"
        WidgetData.Scope.NO_DATA -> "待导入"
    }

    /** 列表区空态：区分"没有课表数据"与"有课表但今明无课"。 */
    fun scheduleEmpty(data: WidgetData): String = when (data.scope) {
        WidgetData.Scope.NO_DATA -> "还没有课表数据"
        WidgetData.Scope.NO_LESSONS -> "今明两天没有课程"
        else -> "今天课程都结束啦"
    }

    fun tallFootnote(data: WidgetData): String = when (data.scope) {
        WidgetData.Scope.TOMORROW -> "今天已结束"
        WidgetData.Scope.TODAY_REST -> "今日剩 ${data.lessons.size} 堂"
        WidgetData.Scope.TODAY_ALL -> "今天 ${data.lessons.size} 堂"
        WidgetData.Scope.NO_LESSONS -> "今明两天无课"
        WidgetData.Scope.NO_DATA -> "导入课表后显示"
    }

    fun sectionLabel(b: CourseBlock): String =
        if (b.endSection > b.startSection) "${b.startSection}-${b.endSection}" else "${b.startSection}节"

    /** 下一节倒计时；未知节次不宣称进行中，只给节次。 */
    fun nextCountdown(scope: WidgetData.Scope, b: CourseBlock, now: LocalTime): String {
        if (scope == WidgetData.Scope.TOMORROW) return "明天见"
        val startT = runCatching { LocalTime.parse(ScheduleTimes.start(b.startSection)) }.getOrNull()
        val endT = runCatching { LocalTime.parse(ScheduleTimes.end(b.endSection)) }.getOrNull()
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

    // ── 待办：排序 + 截止文案 ────────────────────────────────────────────────

    data class WidgetTodo(val todo: Todo, val dueLabel: String, val overdue: Boolean)

    /** 未完成按 逾期→今天→本周→以后→无截止 分组顺序展示，组内按截止时间。 */
    fun pendingTodos(groups: TodoGroups, nowMillis: Long): List<WidgetTodo> {
        val ordered = groups.overdue + groups.today + groups.thisWeek + groups.later + groups.noDue
        return ordered.map { t ->
            val (label, overdue) = dueText(t, nowMillis)
            WidgetTodo(t, label, overdue)
        }
    }

    /** 截止文案：逾期 / 今天 / 明天 / 周X / MM-dd。 */
    fun dueText(t: Todo, nowMillis: Long): Pair<String, Boolean> {
        if (t.dueAt <= 0L) return "无截止" to false
        val zone = ZoneId.systemDefault()
        val d = Instant.ofEpochMilli(t.dueAt).atZone(zone)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val date = d.toLocalDate()
        val hm = "%02d:%02d".format(d.hour, d.minute)
        val diff = ChronoUnit.DAYS.between(today, date)
        return when {
            diff < 0 -> "逾期 ${-diff} 天" to true
            // 同一天但时刻已过（今天 10:00 截止、现在 12:00）：仍显示今天，但标红提醒
            diff == 0L -> "今天 $hm" to (t.dueAt < nowMillis)
            diff == 1L -> "明天 $hm" to false
            diff in 2..6 -> "${ScheduleTimes.dayNames[date.dayOfWeek.value - 1]} $hm" to false
            else -> "%02d/%02d %s".format(date.monthValue, date.dayOfMonth, hm) to false
        }
    }

    // ── 电量走势统计：给图表刻度与说明行 ─────────────────────────────────────

    data class TrendStats(
        val lo: Double,
        val hi: Double,
        val endValue: Double,
        val points: Int,
        val coverageHours: Double,
        /** 每小时变化，负值 = 在下降。 */
        val ratePerHour: Double?,
        val startLabel: String,
        val endLabel: String,
        val updatedLabel: String,
    )

    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
    private val DATE_FMT = DateTimeFormatter.ofPattern("M/d")

    fun trendStats(
        samples: List<Store.Sample>,
        lastSampleAt: Long,
        nowMillis: Long,
    ): TrendStats? {
        if (samples.size < 2) return null
        val zone = ZoneId.systemDefault()
        val first = samples.first()
        val last = samples.last()
        val coverageHours = (last.time - first.time) / 3600_000.0
        val hours = coverageHours.coerceAtLeast(1.0)
        val rate = ((last.odd - first.odd) / hours).takeIf { kotlin.math.abs(it) >= 0.01 }
        val stamp: (Long) -> String = { t ->
            val ldt = Instant.ofEpochMilli(t).atZone(zone).toLocalDateTime()
            if (coverageHours > 48) DATE_FMT.format(ldt) else TIME_FMT.format(ldt)
        }
        val updated = if (lastSampleAt > 0) {
            val ageMin = ChronoUnit.MINUTES.between(
                Instant.ofEpochMilli(lastSampleAt).atZone(zone), Instant.ofEpochMilli(nowMillis).atZone(zone),
            )
            when {
                ageMin < 1 -> "刚刚更新"
                ageMin < 24 * 60 -> "更新于 " + TIME_FMT.format(Instant.ofEpochMilli(lastSampleAt).atZone(zone))
                else -> "更新于 " + DATE_FMT.format(Instant.ofEpochMilli(lastSampleAt).atZone(zone))
            }
        } else ""
        return TrendStats(
            lo = samples.minOf { it.odd },
            hi = samples.maxOf { it.odd },
            endValue = last.odd,
            points = samples.size,
            coverageHours = coverageHours,
            ratePerHour = rate,
            startLabel = stamp(first.time),
            endLabel = stamp(last.time),
            updatedLabel = updated,
        )
    }

    /** 速率一行文案，如「↓0.3 度/时」；无速率返回空。 */
    fun rateLabel(stats: TrendStats?): String = stats?.ratePerHour?.let { r ->
        (if (r < 0) "↓" else "↑") + "%.1f".format(kotlin.math.abs(r)) + " 度/时"
    } ?: ""

    /** 电力大字；样本缺失显示占位。 */
    fun powerValue(odd: Double?): String = odd?.let { "%.1f".format(it) } ?: "—"

    fun powerLow(odd: Double?, warnTh: Float): Boolean = odd != null && odd < warnTh

    /** LocalDateTime 快照，跨午夜不再二次读时钟。 */
    fun nowSnapshot(): LocalDateTime = LocalDateTime.now()
}
