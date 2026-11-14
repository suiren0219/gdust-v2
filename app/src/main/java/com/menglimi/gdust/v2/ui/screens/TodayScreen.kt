// Adapted from NexioSchedule 1.7, revision 28a99a3.
// Sources under ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/activities/:
// today/TodayScreen.kt (date header / period groups), today/TodayAssistantCard.kt
// (status badge / countdown / full-width name / rule bar), today/TodayTimelineView.kt
// (36dp axis / 16dp cards / 4dp accent bar), and main/CourseScheduleChrome.kt.
// The 30sp expanded title comes from miuix/src/commonMain/kotlin/top/yukonga/miuix/kmp/layout/CollapsibleTopAppBar.kt.
// Only presentation is adapted: Gdust CourseBlock, ScheduleTimes and navigation remain local.
package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.menglimi.gdust.v2.data.CalendarInfo
import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.ui.AppCard
import com.menglimi.gdust.v2.ui.AppUiTokens
import com.menglimi.gdust.v2.ui.LocalWallpaperActive
import com.menglimi.gdust.v2.ui.pageBackground
import com.menglimi.gdust.v2.ui.pageTextBacking
import com.menglimi.gdust.v2.ui.schedule.ScheduleTimes
import com.menglimi.gdust.v2.ui.secondaryTextColor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
    val colors = MiuixTheme.colorScheme
    val secondary = secondaryTextColor()
    val onWallpaper = LocalWallpaperActive.current
    val weekText = when {
        currentWeek == null -> if (imported) "按课程日期展示" else "导入课表，安排今天"
        currentWeek <= 0 -> "学期尚未开始"
        calendar != null && calendar.allWeek > 0 && currentWeek > calendar.allWeek -> "本学期已结束"
        else -> "第 $currentWeek 周"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(pageBackground()).statusBarsPadding(),
        contentPadding = PaddingValues(start = AppUiTokens.PagePadding, end = AppUiTokens.PagePadding, top = 24.dp, bottom = 104.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "today-header") {
            Column(
                Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    .pageTextBacking().padding(if (onWallpaper) 12.dp else 0.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "今天是${ScheduleTimes.dayNames[today.dayOfWeek.value - 1]}",
                        fontSize = AppUiTokens.TitleSize,
                        fontWeight = AppUiTokens.TitleWeight,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    TodayNoticeButton(unreadCount, onOpenNotices)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "${today.year}年${today.monthValue}月${today.dayOfMonth}日",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                Text(
                    text = weekText,
                    fontSize = 14.sp,
                    color = secondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
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
            else -> {
                item(key = "today-overview") {
                    val completed = todayCourses.count { it.statusAt(now.toLocalTime()) == TodayCourseStatus.Ended }
                    val ongoing = todayCourses.count { it.statusAt(now.toLocalTime()) == TodayCourseStatus.Ongoing }
                    val pending = todayCourses.count { it.statusAt(now.toLocalTime()) == TodayCourseStatus.NotStarted }
                    val unknown = todayCourses.size - completed - ongoing - pending
                    AppCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = PaddingValues(16.dp)) {
                        Text(
                            "今日 ${todayCourses.size} 门课",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.onSurface,
                        )
                        Text(
                            buildList {
                                add("已结束 $completed")
                                if (ongoing > 0) add("进行中 $ongoing")
                                add("未开始 $pending")
                                if (unknown > 0) add("时间待确认 $unknown")
                            }.joinToString("  /  "),
                            fontSize = 14.sp,
                            color = secondary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                item(key = "today-assistant") {
                    TodayAssistantPanel(todayCourses, now.toLocalTime(), missingDates)
                }
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
                    TextButton(text = "查看完整课表", onClick = onOpenSchedule,
                        modifier = Modifier.fillMaxWidth().pageTextBacking(cornerRadius = ButtonDefaults.CornerRadius), minHeight = 48.dp)
                }
            }
        }
    }
}

@Composable
private fun TodayNoticeButton(unreadCount: Int, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Box {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp).semantics {
                contentDescription = if (unreadCount > 0) "学校公告，$unreadCount 条未读" else "学校公告"
            },
            backgroundColor = colors.surfaceContainer,
            minWidth = 48.dp,
            minHeight = 48.dp,
        ) {
            Icon(MiuixIcons.Info, contentDescription = null, tint = colors.primary, modifier = Modifier.size(23.dp))
        }
        if (unreadCount > 0) {
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(8.dp).background(colors.error, CircleShape))
        }
    }
}

@Composable
private fun TodayAssistantPanel(courses: List<TodayCourse>, now: LocalTime, missingDates: Boolean) {
    val colors = MiuixTheme.colorScheme
    val secondary = secondaryTextColor()
    val current = courses.firstOrNull { it.statusAt(now) == TodayCourseStatus.Ongoing }
    val next = courses.firstOrNull { it.statusAt(now) == TodayCourseStatus.NotStarted }
    val active = current ?: next
    val unknown = courses.count { it.statusAt(now) == TodayCourseStatus.Unknown }
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        insideMargin = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
    ) {
        if (active != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (current != null) "正在上课" else "下一课",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.primary,
                    modifier = Modifier.background(colors.primary.copy(alpha = 0.14f), CircleShape)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                )
                val boundary = if (current != null) active.end else active.start
                Text(
                    text = boundary?.let { remainingTime(now, it, current != null) }.orEmpty(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.primary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = active?.block?.name ?: if (unknown > 0) "课程时间待确认" else "今天的课程已结束",
            fontSize = 18.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
        )
        if (active != null) {
            Text(
                text = active.block.metadata(),
                fontSize = 13.sp,
                color = secondary,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        val progress = current?.let { course ->
            val start = course.start ?: return@let null
            val end = course.end ?: return@let null
            (Duration.between(start, now).toMillis().toFloat() / Duration.between(start, end).toMillis()).coerceIn(0f, 1f)
        }
        Box(
            Modifier.fillMaxWidth().height(if (progress != null) 6.dp else 0.5.dp).clip(CircleShape)
                .background(if (progress != null) colors.primary.copy(alpha = 0.08f) else colors.onSurfaceVariantActions.copy(alpha = 0.09f))
                .then(if (progress != null) Modifier.semantics {
                    contentDescription = "本节课程进度"
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                } else Modifier),
        ) {
            if (progress != null && progress > 0f) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(colors.primary))
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
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = secondary,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun TodayPeriodTitle(title: String) {
    val onWallpaper = LocalWallpaperActive.current
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = secondaryTextColor(),
        modifier = Modifier.padding(top = 8.dp).pageTextBacking()
            .padding(horizontal = if (onWallpaper) 12.dp else 0.dp, vertical = if (onWallpaper) 6.dp else 0.dp)
            .semantics { heading() },
    )
}

@Composable
private fun TodayTimelineCard(course: TodayCourse, now: LocalTime, onOpenSchedule: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val secondary = secondaryTextColor()
    val onWallpaper = LocalWallpaperActive.current
    val status = course.statusAt(now)
    val axisWidth = 36.dp * LocalDensity.current.fontScale.coerceAtLeast(1f) + if (onWallpaper) 8.dp else 0.dp
    // Adapted TodayTimelineCard: axis + accent bar + name/time/metadata. Natural-height
    // rows retain large-font readability; Gdust does not import Nexio's proportional grid.
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.width(axisWidth).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                course.start?.toString() ?: "待定",
                fontSize = 11.sp,
                color = if (status == TodayCourseStatus.Ongoing) colors.primary else secondary,
                modifier = Modifier.padding(top = 16.dp).pageTextBacking(cornerRadius = 8.dp)
                    .padding(horizontal = if (onWallpaper) 4.dp else 0.dp, vertical = if (onWallpaper) 3.dp else 0.dp),
            )
            Box(Modifier.padding(top = 8.dp).width(1.dp).weight(1f).background(colors.onSurfaceVariantActions.copy(alpha = 0.15f)))
        }
        AppCard(modifier = Modifier.weight(1f), cornerRadius = 16.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).height(IntrinsicSize.Min)
                    .clickable(role = Role.Button, onClickLabel = "查看${course.block.name}所在课表", onClick = onOpenSchedule)
                    .padding(start = 10.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                Box(
                    Modifier.width(4.dp).fillMaxHeight().clip(CircleShape)
                        .background(colors.primary.copy(alpha = if (status == TodayCourseStatus.Ended) 0.28f else 1f)),
                )
                Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(course.block.sectionLabel(), fontSize = 12.sp, color = secondary, modifier = Modifier.weight(1f))
                        Text(status.label, fontSize = 12.sp, color = if (status == TodayCourseStatus.Ongoing) colors.primary else secondary)
                    }
                    Text(
                        course.block.name,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(course.timeRange, fontSize = 12.5.sp, color = secondary)
                    Text(course.block.metadata(), fontSize = 12.5.sp, lineHeight = 18.sp, color = secondary)
                }
            }
        }
    }
}

@Composable
private fun TodayEmptyCard(title: String, message: String, action: String, onAction: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val secondary = secondaryTextColor()
    AppCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = PaddingValues(24.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(MiuixIcons.Months, contentDescription = null, tint = colors.primary, modifier = Modifier.padding(top = 12.dp).size(40.dp))
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
            Text(message, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center, color = secondary)
            TextButton(
                text = action,
                onClick = onAction,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                minHeight = 48.dp,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
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
