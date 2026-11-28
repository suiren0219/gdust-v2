// Course timing and grouping are GDUST local logic; presentation uses NexioUI.
package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.ProgressBarRangeInfo
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
import com.haooz.nexioui.cardexpand.CardExpand
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.All
import com.haooz.nexioui.icon.extended.Info
import com.haooz.nexioui.overscroll.overScrollVertical
import com.haooz.nexioui.scrollhaptic.scrollEndHaptic
import com.haooz.nexioui.text.NexioText as Text
import com.menglimi.gdust.v2.data.CalendarInfo
import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.AppCard
import com.menglimi.gdust.v2.ui.AppUiTokens
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.NexioPageScaffold
import com.menglimi.gdust.v2.ui.NexioTopBarGlassIconButton
import com.menglimi.gdust.v2.ui.schedule.ScheduleTimes
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@Composable
fun TodayScreen(
    repo: Repository,
    calendar: CalendarInfo?,
    blocks: List<CourseBlock>,
    onOpenNotices: () -> Unit = {},
    unreadCount: Int = 0,
    onOpenSchedule: () -> Unit = {},
    onImport: () -> Unit = {},
    onOpenServices: () -> Unit = {},
    onService: (CampusService) -> Unit = {},
    hasImportedSchedule: Boolean = blocks.isNotEmpty(),
    serviceSummary: String = "",
    onServices: (() -> Unit)? = null,
    scheduleSource: String = "legacy",
) {
    val now by rememberTodayMinuteClock()
    var shortcutTarget by remember { mutableStateOf<CampusService?>(null) }
    LaunchedEffect(shortcutTarget) {
        shortcutTarget?.let(onService)
        shortcutTarget = null
    }
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
        }.map { it.first }
            .distinctBy { listOf(it.name.trim(), it.room.trim(), it.teacher.trim(), it.startSection, it.endSection) }
            .map { TodayCourse(it) }
            .sortedBy { it.block.startSection }
    }
    val missingDates = currentWeek == null && datedBlocks.any { it.second == null }
    val imported = hasImportedSchedule || blocks.isNotEmpty()
    val todayWeekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val cachedWeekCoversToday = datedBlocks.any { (block, date) ->
        if (date != null) date.minusDays((date.dayOfWeek.value - 1).toLong()) == todayWeekStart
        else currentWeek != null && block.week == currentWeek
    }
    // 整学期导入可以确认空课日；单周缓存只能说明它实际覆盖的那一周。
    val coverageMissing = imported && scheduleSource != "zf_term" && !cachedWeekCoversToday
    val store = remember(repo) { repo.store }
    var todos by remember(store) { mutableStateOf(store.loadTodos()) }
    DisposableEffect(store) {
        val stop = store.observeTodos { todos = store.loadTodos() }
        onDispose { stop() }
    }
    val nowMs = now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val pendingTodos = todos.filterNot { it.done }
    val overdueTodos = pendingTodos.count { it.dueAt > 0 && it.dueAt < nowMs }
    val nextDue = pendingTodos.filter { it.dueAt >= nowMs }.minByOrNull { it.dueAt }
    val hasPower = store.hasPowerBinding
    val ds = LocalDs.current
    val weekText = when {
        currentWeek == null -> if (imported) "按课程日期展示" else "导入课表，安排今天"
        currentWeek <= 0 -> "学期尚未开始"
        calendar != null && calendar.allWeek > 0 && currentWeek > calendar.allWeek -> "本学期已结束"
        else -> "第 $currentWeek 周"
    }
    val dateSubtitle = "${today.year}年${today.monthValue}月${today.dayOfMonth}日 · ${ScheduleTimes.dayNames[today.dayOfWeek.value - 1]} · $weekText"

    NexioPageScaffold(
        title = "今日",
        hasBottomBar = true,
        endAction = { backdrop, bAlpha, sAlpha ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    NexioTopBarGlassIconButton(
                        icon = NexioIcons.Info,
                        contentDescription = if (unreadCount > 0) "学校公告，$unreadCount 条未读" else "学校公告",
                        onClick = onOpenNotices,
                        backdrop = backdrop,
                        backdropAlpha = bAlpha,
                        shadowAlpha = sAlpha,
                    )
                    if (unreadCount > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 6.dp, end = 6.dp)
                                .size(8.dp)
                                .background(ds.danger, CircleShape),
                        )
                    }
                }
                NexioTopBarGlassIconButton(
                    icon = NexioIcons.All,
                    contentDescription = "全部校园服务",
                    onClick = onOpenServices,
                    backdrop = backdrop,
                    backdropAlpha = bAlpha,
                    shadowAlpha = sAlpha,
                )
            }
        },
    ) { scaffoldPadding, _, _ ->
        val pagePad = nexioPagePadding()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .scrollEndHaptic(),
            contentPadding = PaddingValues(
                start = pagePad,
                end = pagePad,
                top = scaffoldPadding.calculateTopPadding(),
                bottom = scaffoldPadding.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(AppUiTokens.CardGap),
        ) {
            item(key = "today-date-subtitle") {
                Text(
                    text = dateSubtitle,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = ds.sub,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            if (serviceSummary.isNotBlank() && onServices != null) {
                item(key = "service-connections") {
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(AppUiTokens.CardPadding),
                        onClick = onServices,
                    ) {
                        Text("服务连接", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ds.text)
                        Text(
                            serviceSummary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = ds.sub,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            "查看状态或单独补连",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = ds.accent,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            item(key = "today-shortcuts-title") {
                SectionHeader("校园概览")
            }
            item(key = "today-shortcuts") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppUiTokens.CardGap)) {
                    CampusMetricCard(
                        label = "待完成事项",
                        value = pendingTodos.size.toString(),
                        modifier = Modifier.weight(1f),
                        description = when {
                            overdueTodos > 0 -> "$overdueTodos 项已到期"
                            nextDue != null -> "最近到期 ${dashboardTime(nextDue.dueAt)}"
                            else -> "作业、考试与日常事项"
                        },
                        warning = overdueTodos > 0,
                        onClick = { shortcutTarget = CampusService.TODO },
                    )
                    CampusMetricCard(
                        label = "宿舍电量",
                        value = if (hasPower) "已绑定" else "未绑定",
                        modifier = Modifier.weight(1f),
                        description = if (hasPower) store.powerRoomName.ifBlank { "查看电量与走势" } else "绑定宿舍后查看电量",
                        warning = false,
                        onClick = { shortcutTarget = CampusService.POWER },
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
                        title = when {
                            missingDates -> "课表日期待确认"
                            coverageMissing -> "今日课表尚未同步"
                            else -> "今天没有课程"
                        },
                        message = when {
                            missingDates -> "部分课程缺少日期，请检查课表中的学期设置。"
                            coverageMissing && scheduleSource == "portal_week" -> "门户单周缓存未覆盖今天，请导入整学期课表后确认。"
                            coverageMissing -> "现有缓存未覆盖今天，暂时无法确认今日课程。"
                            else -> "已导入的课表中没有今日安排。"
                        },
                        action = if (coverageMissing && !missingDates) "导入课表" else "查看课表",
                        onAction = if (coverageMissing && !missingDates) onImport else onOpenSchedule,
                    )
                }
                else -> item(key = "today-assistant") {
                    TodayAssistantPanel(todayCourses, now.toLocalTime(), missingDates)
                }
            }
            if (todayCourses.isNotEmpty()) {
                item(key = "today-overview") {
                    val pending = todayCourses.count { it.statusAt(now.toLocalTime()) == TodayCourseStatus.NotStarted }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "今日安排 · ${todayCourses.size} 门",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = ds.sub,
                            modifier = Modifier.weight(1f).semantics { heading() },
                        )
                        Text("待上 $pending 门", fontSize = 13.sp, color = ds.sub)
                    }
                }
                listOf("上午" to 1..4, "下午" to 5..8, "晚上" to 9..Int.MAX_VALUE).forEach { (label, sections) ->
                    val courses = todayCourses.filter { it.block.startSection in sections }
                    if (courses.isNotEmpty()) {
                        item(key = "today-period-$label") { SectionHeader(label) }
                        courses.forEachIndexed { index, course ->
                            item(key = "today-$label-$index") {
                                TodayTimelineCard(course, now.toLocalTime(), onOpenSchedule)
                            }
                        }
                    }
                }
                val unspecified = todayCourses.filter { it.block.startSection < 1 }
                if (unspecified.isNotEmpty()) {
                    item(key = "today-period-unknown") { SectionHeader("节次待确认") }
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
                        Text("查看完整课表", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ds.text)
                        Text(
                            "查看本周及其他日期的课程安排",
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = ds.sub,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CampusMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    description: String,
    warning: Boolean,
    onClick: () -> Unit,
) {
    val ds = LocalDs.current
    AppCard(modifier = modifier, insideMargin = PaddingValues(AppUiTokens.CardPadding), onClick = onClick) {
        Text(label, fontSize = 13.sp, color = ds.sub)
        Text(
            value,
            fontSize = if (value.length > 4) 22.sp else 30.sp,
            fontWeight = FontWeight.Bold,
            color = if (warning) ds.danger else ds.text,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            description,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = ds.sub,
            modifier = Modifier.padding(top = 6.dp),
            maxLines = 2,
        )
    }
}

private fun dashboardTime(time: Long): String = java.time.Instant.ofEpochMilli(time)
    .atZone(java.time.ZoneId.systemDefault())
    .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))

@Composable
private fun TodayAssistantPanel(courses: List<TodayCourse>, now: LocalTime, missingDates: Boolean) {
    val ds = LocalDs.current
    val current = courses.firstOrNull { it.statusAt(now) == TodayCourseStatus.Ongoing }
    val next = courses.firstOrNull { it.statusAt(now) == TodayCourseStatus.NotStarted }
    val active = current ?: next
    val unknown = courses.count { it.statusAt(now) == TodayCourseStatus.Unknown }
    val statusColor = ds.accent.copy(alpha = 0.10f).compositeOver(ds.card)
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
        CardExpand(visible = active != null) {
            active?.let { activeCourse ->
                Column {
                    val boundary = if (current != null) activeCourse.end else activeCourse.start
                    Text(
                        text = boundary?.let { remainingTime(now, it, current != null) }.orEmpty(),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = ds.sub,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        activeCourse.block.name,
                        fontSize = 17.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ds.text,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        activeCourse.block.metadata(),
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = ds.sub,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        val progress = current?.let { course ->
            val start = course.start ?: return@let null
            val end = course.end ?: return@let null
            (Duration.between(start, now).toMillis().toFloat() / Duration.between(start, end).toMillis()).coerceIn(0f, 1f)
        }
        CardExpand(visible = progress != null) {
            progress?.let { progressValue ->
                Column {
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(ds.accent.copy(alpha = 0.14f).compositeOver(statusColor))
                            .semantics {
                                contentDescription = "本节课程进度"
                                progressBarRangeInfo = ProgressBarRangeInfo(progressValue, 0f..1f)
                            },
                    ) {
                        if (progressValue > 0f) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(progressValue).background(ds.accent))
                        }
                    }
                }
            }
        }
        val hint = when {
            unknown > 0 || missingDates -> "部分课程的日期或时间待确认，请以完整课表为准。"
            current != null && next != null -> "下一课：${next.block.name}。"
            current != null && courses.count { it.statusAt(now) == TodayCourseStatus.Ongoing } > 1 -> "当前有多门课程时间重叠，请查看下方课程安排。"
            current != null -> "这是今天最后一门课。"
            next != null -> null
            else -> "今日 ${courses.size} 门课已全部结束，接下来安排自己的时间。"
        }
        CardExpand(visible = hint != null) {
            hint?.let {
                Text(
                    text = it,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = ds.sub,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun TodayTimelineCard(course: TodayCourse, now: LocalTime, onOpenSchedule: () -> Unit) {
    val ds = LocalDs.current
    val status = course.statusAt(now)
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(AppUiTokens.CardPadding),
        onClick = onOpenSchedule,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                course.block.sectionLabel(),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = ds.sub,
                modifier = Modifier.weight(1f),
            )
            Text(
                status.label,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                fontWeight = if (status == TodayCourseStatus.Ongoing) FontWeight.SemiBold else FontWeight.Normal,
                color = if (status == TodayCourseStatus.Ongoing) ds.accent else ds.sub,
            )
        }
        Text(
            course.block.name,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = ds.text,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            course.timeRange,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Medium,
            color = ds.text,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            course.block.room.ifBlank { "教室待定" },
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = ds.text,
            modifier = Modifier.padding(top = 4.dp),
        )
        CardExpand(visible = course.block.teacher.isNotBlank()) {
            Text(
                course.block.teacher,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = ds.sub,
                modifier = Modifier.padding(top = 4.dp),
            )
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
        Text(title, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = ds.text)
        Text(message, fontSize = 14.sp, lineHeight = 21.sp, color = ds.sub, modifier = Modifier.padding(top = 8.dp))
        Text(
            action,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = ds.accent,
            modifier = Modifier.padding(top = 14.dp),
        )
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
