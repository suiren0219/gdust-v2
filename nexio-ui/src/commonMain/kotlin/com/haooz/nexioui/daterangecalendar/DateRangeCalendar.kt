package com.haooz.nexioui.daterangecalendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.nexioui.text.NexioText
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.shape.ContinuousCapsule
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber

/** 日历选择模式。 */
enum class CalendarMode {
    /** 点两下选一段。 */
    Range,

    /** 点一下选一天。 */
    Single,
}

/**
 * 通用日期区间日历。
 *
 * 区间模式：点第一下是起点（同时清掉上一次选择），点第二下完成区间；
 * 完成后再点任何日期，会以该日期为起点重新选择。
 * 单日模式：点一下直接返回该日期。
 *
 * @param selectedStart 已选起点，未选择时为 null。
 * @param selectedEnd 已选终点，区间未完成时为 null。
 * @param onRangeSelected 选择变化回调；区间未完成时第二项为 null。
 * @param modifier 外层修饰符。
 * @param mode 选择模式。
 * @param resetKey 变化时回到 [initialMonth] 并清空进行中的选择。
 * @param yearRange 允许翻到的年份范围。
 * @param initialMonth 初始展示月份，未指定时取已选日期所在月。
 * @param strings 文案与日期格式。
 */
@Composable
fun DateRangeCalendar(
    selectedStart: LocalDate?,
    selectedEnd: LocalDate?,
    onRangeSelected: (LocalDate, LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    mode: CalendarMode = CalendarMode.Range,
    resetKey: Any? = Unit,
    yearRange: IntRange = 1970..2100,
    initialMonth: LocalDate? = null,
    strings: CalendarStrings = CalendarStrings.Default,
) {
    val scheme = NexioTheme.colorScheme
    val anchor = initialMonth ?: selectedStart ?: selectedEnd
    var viewYear by remember(resetKey) { mutableIntStateOf(anchor?.year ?: 1970) }
    var viewMonth by remember(resetKey) { mutableIntStateOf(anchor?.monthNumber ?: 1) }
    var pendingStart by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(resetKey) { pendingStart = null }

    fun shiftMonth(delta: Int) {
        var targetYear = viewYear
        var targetMonth = viewMonth + delta
        if (targetMonth < 1) {
            targetMonth = 12
            targetYear -= 1
        }
        if (targetMonth > 12) {
            targetMonth = 1
            targetYear += 1
        }
        if (targetYear in yearRange) {
            viewYear = targetYear
            viewMonth = targetMonth
        }
    }

    fun handleDayClick(date: LocalDate) {
        if (mode == CalendarMode.Single) {
            pendingStart = null
            onRangeSelected(date, date)
            return
        }
        val start = pendingStart
        if (start == null) {
            pendingStart = date
            onRangeSelected(date, null)
        } else {
            pendingStart = null
            onRangeSelected(minOf(start, date), maxOf(start, date))
        }
    }

    CalendarFrame(
        year = viewYear,
        month = viewMonth,
        strings = strings,
        onShiftMonth = { shiftMonth(it) },
        modifier = modifier,
    ) { cellYear, cellMonth ->
        MonthGrid(
            year = cellYear,
            month = cellMonth,
            weekdayLabels = strings.weekdayLabels,
            styleOf = { date ->
                val start = selectedStart
                val end = selectedEnd
                val band = scheme.primary.copy(alpha = DateRangeCalendarDefaults.RangeBandAlpha)
                when {
                    start == null -> DayStyle(content = scheme.onSurface)
                    date == start && date == end -> DayStyle(
                        fill = scheme.primary,
                        content = scheme.onPrimary,
                    )
                    date == start -> DayStyle(
                        band = if (end != null) band else null,
                        bandHalf = BandHalf.Trailing,
                        fill = scheme.primary,
                        content = scheme.onPrimary,
                    )
                    date == end -> DayStyle(
                        band = band,
                        bandHalf = BandHalf.Leading,
                        fill = scheme.primary,
                        content = scheme.onPrimary,
                    )
                    end != null && date > start && date < end -> DayStyle(
                        band = band,
                        bandHalf = BandHalf.Full,
                        content = scheme.primary,
                    )
                    else -> DayStyle(content = scheme.onSurface)
                }
            },
            onDayClick = { handleDayClick(it) },
        )
    }
}

/**
 * 双色双日期日历：一次选两个互不相关的日期。
 *
 * [primaryDate] 用主标记色，[secondaryDate] 用次标记色；点下面的日期胶囊切换
 * 「下一次点击改哪一个」，两边都选好后自动切到还空着的那一侧。
 *
 * @param primaryDate 主日期。
 * @param secondaryDate 次日期，未选择时为 null。
 * @param onPrimaryChange 主日期变化回调。
 * @param onSecondaryChange 次日期变化回调。
 * @param modifier 外层修饰符。
 * @param resetKey 变化时回到 [primaryDate] 所在月并重置编辑目标。
 * @param yearRange 允许翻到的年份范围。
 * @param primaryColor 主日期标记色，未指定时取主题主色。
 * @param secondaryColor 次日期标记色。
 * @param startWithSecondary 首次进入时默认编辑哪一侧；true 为次日期。
 * @param strings 文案与日期格式。
 */
@Composable
fun DualDateCalendar(
    primaryDate: LocalDate,
    secondaryDate: LocalDate?,
    onPrimaryChange: (LocalDate) -> Unit,
    onSecondaryChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    resetKey: Any? = Unit,
    yearRange: IntRange = 1970..2100,
    primaryColor: Color = Color.Unspecified,
    secondaryColor: Color = DateRangeCalendarDefaults.SecondaryMarkColor,
    startWithSecondary: Boolean = false,
    strings: CalendarStrings = CalendarStrings.Default,
) {
    val scheme = NexioTheme.colorScheme
    val resolvedPrimaryColor =
        if (primaryColor == Color.Unspecified) scheme.primary else primaryColor
    var viewYear by remember(resetKey) { mutableIntStateOf(primaryDate.year) }
    var viewMonth by remember(resetKey) { mutableIntStateOf(primaryDate.monthNumber) }
    // true = 下一次点击改主日期，false = 改次日期
    var editingPrimary by remember(resetKey) { mutableStateOf(!startWithSecondary) }

    fun shiftMonth(delta: Int) {
        var targetYear = viewYear
        var targetMonth = viewMonth + delta
        if (targetMonth < 1) {
            targetMonth = 12
            targetYear -= 1
        }
        if (targetMonth > 12) {
            targetMonth = 1
            targetYear += 1
        }
        if (targetYear in yearRange) {
            viewYear = targetYear
            viewMonth = targetMonth
        }
    }

    CalendarFrame(
        year = viewYear,
        month = viewMonth,
        strings = strings,
        onShiftMonth = { shiftMonth(it) },
        modifier = modifier,
        footer = {
            DualDateRoleRow(
                primaryDate = primaryDate,
                secondaryDate = secondaryDate,
                editingPrimary = editingPrimary,
                primaryColor = resolvedPrimaryColor,
                secondaryColor = secondaryColor,
                strings = strings,
                onSelectPrimary = { editingPrimary = true },
                onSelectSecondary = { editingPrimary = false },
            )
        },
    ) { cellYear, cellMonth ->
        MonthGrid(
            year = cellYear,
            month = cellMonth,
            weekdayLabels = strings.weekdayLabels,
            styleOf = { date ->
                when (date) {
                    primaryDate -> DayStyle(fill = resolvedPrimaryColor, content = Color.White)
                    secondaryDate -> DayStyle(fill = secondaryColor, content = Color.White)
                    else -> DayStyle(content = scheme.onSurface)
                }
            },
            onDayClick = { date ->
                if (editingPrimary) {
                    onPrimaryChange(date)
                    // 另一侧还空着：顺手把编辑目标切过去，少点一次
                    if (secondaryDate == null) editingPrimary = false
                } else {
                    onSecondaryChange(date)
                }
            },
        )
    }
}

/** 双色日期的角色行：点一下决定下次改哪一侧。 */
@Composable
private fun DualDateRoleRow(
    primaryDate: LocalDate,
    secondaryDate: LocalDate?,
    editingPrimary: Boolean,
    primaryColor: Color,
    secondaryColor: Color,
    strings: CalendarStrings,
    onSelectPrimary: () -> Unit,
    onSelectSecondary: () -> Unit,
) {
    val scheme = NexioTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateRoleChip(
            label = strings.formatDate(primaryDate),
            color = primaryColor,
            active = editingPrimary,
            onClick = onSelectPrimary,
        )
        NexioText(
            text = strings.dateSeparator,
            modifier = Modifier.padding(horizontal = 8.dp),
            style = NexioTheme.textStyles.footnote1,
            fontSize = 13.sp,
            color = scheme.onSurfaceVariantSummary,
        )
        DateRoleChip(
            label = secondaryDate?.let(strings.formatDate) ?: strings.pickDateLabel,
            color = secondaryColor,
            active = !editingPrimary,
            onClick = onSelectSecondary,
        )
    }
}

@Composable
private fun DateRoleChip(
    label: String,
    color: Color,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(ContinuousCapsule())
            .background(if (active) color else color.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        NexioText(
            text = label,
            style = NexioTheme.textStyles.footnote1,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (active) Color.White else color,
        )
    }
}

private enum class BandHalf { None, Leading, Trailing, Full }

private data class DayStyle(
    val band: Color? = null,
    val bandHalf: BandHalf = BandHalf.None,
    val fill: Color? = null,
    val content: Color,
)

/** 卡片外框：统一的四边内间距 + 月份标题（固定）+ 可平移的月视图 + 可选脚注。 */
@Composable
private fun CalendarFrame(
    year: Int,
    month: Int,
    strings: CalendarStrings,
    onShiftMonth: (Int) -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
    monthContent: @Composable (year: Int, month: Int) -> Unit,
) {
    Column(
        modifier = modifier.padding(DateRangeCalendarDefaults.ContentPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DateRangeCalendarDefaults.HeaderHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonthNavButton(
                imageVector = DateRangeCalendarDefaults.ChevronBackward,
                contentDescription = strings.previousMonthContentDescription,
                onClick = { onShiftMonth(-1) },
            )
            NexioText(
                text = strings.monthTitle(year, month),
                modifier = Modifier.weight(1f),
                style = NexioTheme.textStyles.main,
                textAlign = TextAlign.Center,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = NexioTheme.colorScheme.onSurface,
            )
            MonthNavButton(
                imageVector = DateRangeCalendarDefaults.ChevronForward,
                contentDescription = strings.nextMonthContentDescription,
                onClick = { onShiftMonth(1) },
            )
        }

        // 翻月整块平移：日期格（连同星期头）一起滑走/滑入，标题不跟着抖
        AnimatedContent(
            targetState = year * 12 + (month - 1),
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                val forward = targetState > initialState
                val slide = tween<IntOffset>(durationMillis = 240, easing = FastOutSlowInEasing)
                val fade = tween<Float>(durationMillis = 200)
                if (forward) {
                    (slideInHorizontally(slide) { it } + fadeIn(fade)) togetherWith
                        (slideOutHorizontally(slide) { -it } + fadeOut(fade))
                } else {
                    (slideInHorizontally(slide) { -it } + fadeIn(fade)) togetherWith
                        (slideOutHorizontally(slide) { it } + fadeOut(fade))
                }
            },
            contentAlignment = Alignment.TopCenter,
            label = "calendar-month",
        ) { monthKey ->
            monthContent(monthKey / 12, monthKey % 12 + 1)
        }

        footer()
    }
}

@Composable
private fun MonthNavButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(DateRangeCalendarDefaults.NavButtonSize)
            .clip(ContinuousCapsule())
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(DateRangeCalendarDefaults.NavIconSize),
            tint = NexioTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun MonthGrid(
    year: Int,
    month: Int,
    weekdayLabels: List<String>,
    styleOf: (LocalDate) -> DayStyle,
    onDayClick: (LocalDate) -> Unit,
) {
    val firstOfMonth = LocalDate(year, month, 1)
    val leading = firstOfMonth.dayOfWeek.isoDayNumber - 1
    val daysInMonth = firstOfMonth.lengthOfMonth()
    val rowCount = (leading + daysInMonth + 6) / 7

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdayLabels.forEach { label ->
                NexioText(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = NexioTheme.textStyles.footnote2,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = NexioTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(rowCount) { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(7) { column ->
                        val day = row * 7 + column - leading + 1
                        if (day in 1..daysInMonth) {
                            val date = LocalDate(year, month, day)
                            CalendarDayCell(
                                date = date,
                                style = styleOf(date),
                                modifier = Modifier.weight(1f),
                                onClick = { onDayClick(date) },
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    style: DayStyle,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(DateRangeCalendarDefaults.CellHeight)
            // 网格点选不要水波纹：日期选中态自己就是反馈
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 区间条只画在圆之外：中间日铺满整格，起止日只铺朝区间内的半边
        val band = style.band
        if (band != null) {
            val base = Modifier
                .fillMaxHeight()
                .padding(vertical = 3.dp)
                .background(band)
            when (style.bandHalf) {
                BandHalf.Full -> Box(base.fillMaxWidth())
                BandHalf.Trailing -> Box(base.fillMaxWidth(0.5f).align(Alignment.CenterEnd))
                BandHalf.Leading -> Box(base.fillMaxWidth(0.5f).align(Alignment.CenterStart))
                BandHalf.None -> Unit
            }
        }

        Box(
            modifier = Modifier
                .size(DateRangeCalendarDefaults.MarkSize)
                .clip(ContinuousCapsule())
                .background(style.fill ?: Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            NexioText(
                text = date.dayOfMonth.toString(),
                style = NexioTheme.textStyles.body2,
                fontSize = 14.sp,
                fontWeight = if (style.fill != null) FontWeight.Medium else FontWeight.Normal,
                color = style.content,
            )
        }
    }
}

/** 本月天数：用「下个月 1 号 - 本月 1 号」算，避免引入平台日期库。 */
private fun LocalDate.lengthOfMonth(): Int {
    val first = LocalDate(year, monthNumber, 1)
    val next = if (monthNumber == 12) {
        LocalDate(year + 1, 1, 1)
    } else {
        LocalDate(year, monthNumber + 1, 1)
    }
    return first.daysUntil(next)
}
