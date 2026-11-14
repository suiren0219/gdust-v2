// Course timing and grouping are GDUST local logic; presentation follows CZeroX.
package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.menglimi.gdust.v2.data.CalendarInfo
import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.ui.AppCard
import com.menglimi.gdust.v2.ui.AppUiTokens
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.schedule.ScheduleTimes
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@Composable
fun TodayScreen(
    calendar: CalendarInfo?,
    blocks: List<CourseBlock>,
    onOpenNotices: () -> Unit = {},
    unreadCount: Int = 0,
    onOpenSchedule: () -> Unit = {},
    onImport: () -> Unit = {},
    hasImportedSchedule: Boolean = blocks.isNotEmpty(),
) {
    val now by rememberTodayMinuteClock()
    val today = now.toLocalDate()
    val currentWeek = remember(calendar, today) {
        parseScheduleDate(calendar?.startDate.orEmpty())?.let {
            // floorDiv keeps the week before term start at 0, rather than incorrectly at 1.
            Math.floorDiv(ChronoUnit.DAYS.between(it, today), 7L).toInt() + 1
        } ?: calendar?.week?.takeIf { it > 0 }
    }
    val datedBlocks = remember(blocks) { blocks.map { it to parseScheduleDate(it.date) } }
    val todayCourses = remember(datedBlocks, currentWeek, today) {
        datedBlocks.filter { (block, date) ->
            if (date != null) date == today
            else currentWeek != null && block.week == currentWeek && block.day == today.dayOfWeek.value
        }.map { (block, _) -> TodayCourse(block) }.sortedBy { it.block.startSection }
    }
    val missingDates = currentWeek == null && datedBlocks.any { it.second == null }
    val imported = hasImportedSchedule || blocks.isNotEmpty()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val ds = LocalDs.current
    val weekText = when {
        currentWeek == null -> if (imported) "按课程日期展示" else "导入课表，安排今天"
        currentWeek <= 0 -> "学期尚未开始"
        calendar != null && calendar.allWeek > 0 && currentWeek > calendar.allWeek -> "本学期已结束"
        else -> "第 $currentWeek 周"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(ds.bg).statusBarsPadding(),
        contentPadding = PaddingValues(
            start = AppUiTokens.PagePadding,
            end = AppUiTokens.PagePadding,
            top = 24.dp,
            bottom = 104.dp + bottomInset,
        ),
        verticalArrangement = Arrangement.spacedBy(AppUiTokens.CardGap),
    ) {
        item(key = "today-header") {
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "今日",
                        fontSize = AppUiTokens.TitleSize,
                        fontWeight = AppUiTokens.TitleWeight,
                        color = ds.text,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    TodayNoticeButton(unreadCount, onOpenNotices)
                }
                Text(
                    text = "${today.year}年${today.monthValue}月${today.dayOfMonth}日 · ${ScheduleTimes.dayNames[today.dayOfWeek.value - 1]}",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = ds.sub,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(weekText, fontSize = 13.sp, lineHeight = 20.sp, color = ds.sub)
            }
        }
        when {
            !imported -> item(key = "schedule-not-imported") {
                TodayEmptyCard(
                    title = "还没有导入课表",
                    message = "导入本学期课表后，今日课程和下一课会显示在这里。",
                    action = "导入课表",
                    onAction = onImport,
                )
            }
            todayCourses.isEmpty() -> item(key = "today-empty") {
                TodayEmptyCard(
                    title = if (missingDates) "课表日期待确认" else "今天没有课程",
                    message = if (missingDates) "部分课程缺少日期，请检查课表中的学期设置。"
                    else "已导入的课表中没有今日安排，可以查看其他日期的课程。",
                    action = "查看课表",
                    onAction = onOpenSchedule,
                )
            }
            else -> item(key = "today-assistant") {
                TodayAssistantPanel(todayCourses, now.toLocalTime(), missingDates)
            }
        }
        if (imported) {
            item(key = "today-overview") {
                val completed = todayCourses.count { it.statusAt(now.toLocalTime()) == TodayCourseStatus.Ended }
                val ongoing = todayCourses.count { it.statusAt(now.toLocalTime()) == TodayCourseStatus.Ongoing }
                val pending = todayCourses.count { it.statusAt(now.toLocalTime()) == TodayCourseStatus.NotStarted }
                val unknown = todayCourses.size - completed - ongoing - pending
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppUiTokens.CardGap)) {
                        TodayMetricCard("今日课程", todayCourses.size, Modifier.weight(1f))
                        TodayMetricCard("待上课程", pending, Modifier.weight(1f))
                    }
                    Text(
                        buildList {
                            add("已结束 $completed")
                            if (ongoing > 0) add("进行中 $ongoing")
                            if (unknown > 0) add("时间待确认 $unknown")
                        }.joinToString(" · "),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = ds.sub,
                    )
                }
            }
        }
        if (todayCourses.isNotEmpty()) {
            listOf("上午" to 1..4, "下午" to 5..8, "晚上" to 9..Int.MAX_VALUE).forEach { (label, sections) ->
                val courses = todayCourses.filter { it.block.startSection in sections }
                if (courses.isNotEmpty()) {
                    item(key = "today-period-$label") { TodayPeriodTitle(label) }
                    courses.forEachIndexed { index, course ->
                        item(key = "today-$label-$index") {
                            TodayTimelineCard(course, now.toLocalTime(), onOpenSchedule)
                        }
                    }
                }
            }
            val unspecified = todayCourses.filter { it.block.startSection < 1 }
            if (unspecified.isNotEmpty()) {
                item(key = "today-period-unknown") { TodayPeriodTitle("节次待确认") }
                unspecified.forEachIndexed { index, course ->
                    item(key = "today-unknown-$index") { TodayTimelineCard(course, now.toLocalTime(), onOpenSchedule) }
                }
            }
            item(key = "today-schedule-action") {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(AppUiTokens.CardPadding),
                    onClick = onOpenSchedule,
                ) {
                    Text("查看完整课表", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ds.text)
                    Text("查看本周及其他日期的课程安排", fontSize = 13.sp, lineHeight = 20.sp,
                        color = ds.sub, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun TodayNoticeButton(unreadCount: Int, onClick: () -> Unit) {
    val ds = LocalDs.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape)
                .background(if (pressed) ds.text.copy(alpha = 0.06f).compositeOver(ds.bg) else ds.bg)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = if (unreadCount > 0) "学校公告，$unreadCount 条未读" else "学校公告"
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(MiuixIcons.Info, contentDescription = null, tint = ds.text, modifier = Modifier.size(24.dp))
        }
        if (unreadCount > 0) {
            Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(7.dp).background(ds.danger, CircleShape))
        }
    }
}

@Composable
private fun TodayMetricCard(label: String, value: Int, modifier: Modifier = Modifier) {
    val ds = LocalDs.current
    AppCard(modifier = modifier, insideMargin = PaddingValues(AppUiTokens.CardPadding)) {
        Text(label, fontSize = 13.sp, color = ds.sub)
        Text(value.toString(), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = ds.text,
            modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun TodayAssistantPanel(courses: List<TodayCourse>, now: LocalTime, missingDates: Boolean) {
    val ds = LocalDs.current
    val current = courses.firstOrNull { it.statusAt(now) == TodayCourseStatus.Ongoing }
    val next = courses.firstOrNull { it.statusAt(now) == TodayCourseStatus.NotStarted }
    val active = current ?: next
    val unknown = courses.count { it.statusAt(now) == TodayCourseStatus.Unknown }
    val success = Color(0xFF36D267)
    val statusColor = if (ds.isDark) success.copy(alpha = 0.16f).compositeOver(ds.card) else Color(0xFFDEFAE4)
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(AppUiTokens.CardPadding),
        color = statusColor,
    ) {
        Text(
            text = when {
                current != null -> "正在上课"
                next != null -> "下一课"
                unknown > 0 -> "课程时间待确认"
                else -> "今天的课程已结束"
            },
            fontSize = 22.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.SemiBold,
            color = ds.text,
        )
        if (active != null) {
            val boundary = if (current != null) active.end else active.start
            Text(
                text = boundary?.let { remainingTime(now, it, current != null) }.orEmpty(),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = ds.sub,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(active.block.name, fontSize = 18.sp, lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold, color = ds.text, modifier = Modifier.padding(top = 16.dp))
            Text(active.timeRange, fontSize = 15.sp, color = ds.text, modifier = Modifier.padding(top = 6.dp))
            Text(active.block.metadata(), fontSize = 14.sp, lineHeight = 21.sp,
                color = ds.sub, modifier = Modifier.padding(top = 4.dp))
        }
        val progress = current?.let { course ->
            val start = course.start ?: return@let null
            val end = course.end ?: return@let null
            (Duration.between(start, now).toMillis().toFloat() / Duration.between(start, end).toMillis()).coerceIn(0f, 1f)
        }
        if (progress != null) {
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.fillMaxWidth().height(5.dp).clip(CircleShape)
                    .background(success.copy(alpha = 0.12f).compositeOver(statusColor))
                    .semantics {
                        contentDescription = "本节课程进度"
                        progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                    },
            ) {
                if (progress > 0f) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(success))
                }
            }
        }
        Text(
            text = when {
                unknown > 0 || missingDates -> "部分课程的日期或时间待确认，请以完整课表为准。"
                current != null && next != null -> "下一课：${next.block.name}，${next.start} 开始。"
                current != null && courses.count { it.statusAt(now) == TodayCourseStatus.Ongoing } > 1 -> "当前有多门课程时间重叠，请查看下方课程安排。"
                current != null -> "这是今天最后一门课。"
                next != null -> "${next.timeRange}，${next.block.room.ifBlank { "教室待定" }}。"
                else -> "今日 ${courses.size} 门课已全部结束，接下来安排自己的时间。"
            },
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = ds.sub,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun TodayPeriodTitle(title: String) {
    val ds = LocalDs.current
    Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ds.text,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp).semantics { heading() })
}

@Composable
private fun TodayTimelineCard(course: TodayCourse, now: LocalTime, onOpenSchedule: () -> Unit) {
    val ds = LocalDs.current
    val status = course.statusAt(now)
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = AppUiTokens.CardCorner,
        insideMargin = PaddingValues(AppUiTokens.CardPadding),
        onClick = onOpenSchedule,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(course.block.sectionLabel(), fontSize = 13.sp, lineHeight = 20.sp,
                color = ds.sub, modifier = Modifier.weight(1f))
            Text(status.label, fontSize = 13.sp, lineHeight = 20.sp,
                color = if (status == TodayCourseStatus.Ongoing) ds.accent else ds.sub)
        }
        Text(course.block.name, fontSize = 18.sp, lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold, color = ds.text, modifier = Modifier.padding(top = 10.dp))
        Text(course.timeRange, fontSize = 15.sp, lineHeight = 22.sp,
            fontWeight = FontWeight.Medium, color = ds.text, modifier = Modifier.padding(top = 12.dp))
        Text(course.block.room.ifBlank { "教室待定" }, fontSize = 14.sp, lineHeight = 21.sp,
            color = ds.text, modifier = Modifier.padding(top = 4.dp))
        if (course.block.teacher.isNotBlank()) {
            Text(course.block.teacher, fontSize = 13.sp, lineHeight = 20.sp,
                color = ds.sub, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun TodayEmptyCard(title: String, message: String, action: String, onAction: () -> Unit) {
    val ds = LocalDs.current
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(AppUiTokens.CardPadding),
        onClick = onAction,
    ) {
        Text(title, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, color = ds.text)
        Text(message, fontSize = 13.sp, lineHeight = 20.sp, color = ds.sub, modifier = Modifier.padding(top = 8.dp))
        Text(action, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ds.accent,
            modifier = Modifier.padding(top = 16.dp))
    }
}

private enum class TodayCourseStatus(val label: String) {
    NotStarted("未开始"), Ongoing("进行中"), Ended("已结束"), Unknown("时间待确认"),
}

private data class TodayCourse(val block: CourseBlock) {
    val start: LocalTime? = runCatching { LocalTime.parse(ScheduleTimes.start(block.startSection)) }.getOrNull()
    val end: LocalTime? = runCatching { LocalTime.parse(ScheduleTimes.end(block.endSection)) }.getOrNull()
    private val validTime = start != null && end != null && end.isAfter(start)
    val timeRange: String get() = if (validTime) "$start - $end" else "上课时间待确认"

    fun statusAt(now: LocalTime): TodayCourseStatus = when {
        !validTime -> TodayCourseStatus.Unknown
        now.isBefore(start) -> TodayCourseStatus.NotStarted
        !now.isBefore(end) -> TodayCourseStatus.Ended // Start inclusive, end exclusive.
        else -> TodayCourseStatus.Ongoing
    }
}

private fun CourseBlock.sectionLabel(): String = when {
    startSection < 1 || endSection < startSection -> "节次待确认"
    startSection == endSection -> "第 $startSection 节"
    else -> "第 $startSection-$endSection 节"
}

private fun CourseBlock.metadata(): String = listOf(room.ifBlank { "教室待定" }, teacher).filter { it.isNotBlank() }.joinToString(" / ")

private fun parseScheduleDate(value: String): LocalDate? = runCatching {
    LocalDate.parse(value.trim().substringBefore('T').substringBefore(' ').replace('/', '-'))
}.getOrNull()

private fun remainingTime(now: LocalTime, boundary: LocalTime, inClass: Boolean): String {
    val minutes = ((Duration.between(now, boundary).toMillis() + 59_999L) / 60_000L).coerceAtLeast(1)
    val duration = when {
        minutes < 60 -> "$minutes 分钟"
        minutes % 60L == 0L -> "${minutes / 60} 小时"
        else -> "${minutes / 60} 小时 ${minutes % 60} 分钟"
    }
    return if (inClass) "距下课 $duration" else "距上课 $duration"
}

/** One clock for the page, initialized immediately; stops in background and refreshes on resume. */
@Composable
private fun rememberTodayMinuteClock(): State<LocalDateTime> {
    val owner = LocalLifecycleOwner.current
    val clock = remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (currentCoroutineContext().isActive) {
                clock.value = LocalDateTime.now()
                delay((60_000L - System.currentTimeMillis() % 60_000L).coerceAtLeast(1L))
            }
        }
    }
    return clock
}
