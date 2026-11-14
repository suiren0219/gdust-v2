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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.LocalDs
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import java.time.ZoneId
import java.time.Instant
import java.time.format.DateTimeFormatter
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
    repo: Repository,
    calendar: CalendarInfo?,
    blocks: List<CourseBlock>,
    unreadCount: Int,
    noticesLoading: Boolean,
    noticesError: String?,
    noticesUpdatedAt: Long,
    onOpenSchedule: () -> Unit,
    onImport: () -> Unit,
    onServices: () -> Unit,
    onService: (CampusService) -> Unit,
    onSettings: () -> Unit,
    onLogin: () -> Unit,
    signedIn: Boolean,
    onSync: () -> Unit,
    syncing: Boolean,
    syncError: String?,
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
    val weekCovered = repo.store.scheduleSource == "zf_term" ||
        (currentWeek != null && blocks.any { it.week == currentWeek })
    val store = repo.store
    var todos by remember(store) { mutableStateOf(store.loadTodos()) }
    DisposableEffect(store) {
        val stop = store.observeTodos { todos = store.loadTodos() }
        onDispose { stop() }
    }
    val nowMs = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val pending = todos.filterNot { it.done }
    val overdue = pending.count { it.dueAt in 1 until nowMs }
    val nextDue = pending.filter { it.dueAt >= nowMs }.minByOrNull { it.dueAt }
    val hasPower = store.hasPowerBinding
    val ds = LocalDs.current
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
                        text = "今日",
                        fontSize = AppUiTokens.TitleSize,
                        fontWeight = AppUiTokens.TitleWeight,
                        color = ds.text,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                        Icon(MiuixIcons.Settings, "设置", tint = ds.text, modifier = Modifier.size(24.dp))
                    }
                }
                Text(
                    text = "${today.monthValue}月${today.dayOfMonth}日 · ${ScheduleTimes.dayNames[today.dayOfWeek.value - 1]} · $weekText",
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = secondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        item(key = "today-status") {
            when {
                !imported -> TodayEmptyCard(
                    title = "从今天开始，安排校园生活",
                    message = "导入课表后，这里会显示下一课、教室与上课时间。",
                    action = "导入课表", onAction = onImport,
                )
                todayCourses.isEmpty() -> TodayEmptyCard(
                    title = when {
                        missingDates -> "课表日期待确认"
                        !weekCovered -> "尚未取得本周课程"
                        else -> "今天没有课程"
                    },
                    message = when {
                        missingDates -> "部分课程缺少日期，请检查学期设置。"
                        !weekCovered -> "当前缓存未覆盖本周，导入整学期课表后再确认。"
                        else -> "今日 0 门课，其他日期的安排可在完整课表查看。"
                    },
                    action = if (!weekCovered) "导入整学期" else "查看课表",
                    onAction = if (!weekCovered) onImport else onOpenSchedule,
                )
                else -> TodayAssistantPanel(todayCourses, now.toLocalTime(), missingDates, onOpenSchedule)
            }
        }
        item(key = "today-metrics") {
            AdaptiveDashboardPair(
                first = { modifier ->
                    TodayMetricCard(
                        title = "待完成事项", value = pending.size.toString(), unit = "项",
                        description = when {
                            overdue > 0 -> "$overdue 项已到期 · 查看待办"
                            nextDue != null -> "最近到期 ${formatDashboardTime(nextDue.dueAt)}"
                            pending.isEmpty() -> "添加作业、考试或日常事项"
                            else -> "查看作业、考试与日常事项"
                        },
                        warning = overdue > 0, modifier = modifier,
                        onClick = { onService(CampusService.TODO) },
                    )
                },
                second = { modifier ->
                    TodayMetricCard(
                        title = "宿舍电量", value = if (hasPower) "已绑定" else "未绑定",
                        unit = "",
                        description = if (hasPower) "${store.powerRoomName.ifBlank { "宿舍" }} · 点击查询"
                            else "绑定宿舍，查看电量与走势",
                        warning = false,
                        modifier = modifier, onClick = { onService(CampusService.POWER) },
                    )
                },
            )
        }
        item(key = "today-services-title") {
            DashboardSectionTitle("常用服务", "全部服务", onServices)
        }
        item(key = "today-services") {
            DashboardCard {
                ServiceActionRow("学期成绩", "成绩、学分与绩点", { onService(CampusService.GRADES) })
                ServiceActionRow("学习通课程", "课程列表与任务点进度", { onService(CampusService.CHX_COURSES) },
                    detail = if (store.hasChxSession) "登录信息已保存，进入后验证" else "首次使用需登录学习通")
                ServiceActionRow("桌面小部件", "九款样式，把课程、电量与待办放到桌面", { onService(CampusService.WIDGETS) })
            }
        }
        item(key = "today-notices") {
            DashboardCard {
                ServiceActionRow(
                    title = "学校公告",
                    description = when {
                        noticesLoading -> "正在刷新公告…"
                        noticesError != null -> "更新未完成 · 进入查看并重试"
                        noticesUpdatedAt <= 0 -> "查看门户通知、教务处公告与官网新闻"
                        unreadCount > 0 -> "当前来源 $unreadCount 条未读 · 查看公告"
                        else -> "当前来源已读完 · 查看其他来源"
                    },
                    onClick = { onService(CampusService.NOTICES) },
                )
            }
        }
        if (todayCourses.isNotEmpty()) {
            item(key = "today-schedule-title") {
                DashboardSectionTitle("今日安排 · ${todayCourses.size} 门课", "完整课表", onOpenSchedule)
            }
            listOf("上午" to 1..4, "下午" to 5..8, "晚上" to 9..Int.MAX_VALUE).forEach { (label, sections) ->
                val courses = todayCourses.filter { it.block.startSection in sections }
                if (courses.isNotEmpty()) {
                    item(key = "today-period-$label") { TodayPeriodTitle(label) }
                    courses.forEachIndexed { index, course ->
                        item(key = "today-$label-$index") { TodayTimelineCard(course, now.toLocalTime(), onOpenSchedule) }
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
        }
        item(key = "today-sync") {
            DashboardCard(insideMargin = PaddingValues(20.dp)) {
                Text(if (signedIn) "课表同步" else "登录校园账号", fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold, color = ds.text)
                Text(
                    when {
                        syncing -> "正在更新，已保存的课表仍可查看"
                        syncError != null -> syncError
                        !signedIn -> "登录后可同步学校数据，本地待办和宿舍电量可独立使用。"
                        store.lastSyncAt > 0 -> "最近同步 ${formatDashboardTime(store.lastSyncAt)}"
                        else -> "使用当前账号更新课程，或从校园服务导入整学期课表。"
                    },
                    fontSize = 13.sp, lineHeight = 20.sp,
                    color = if (syncError != null) ds.danger else ds.sub,
                    modifier = Modifier.padding(top = 8.dp),
                )
                TextButton(
                    text = if (!signedIn) "去登录" else if (syncing) "同步中…" else "刷新课表",
                    enabled = !syncing, onClick = if (signedIn) onSync else onLogin,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp), minHeight = 48.dp,
                )
            }
        }
    }
}

@Composable
private fun AdaptiveDashboardPair(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 320.dp || fontScale > 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                first(Modifier.weight(1f).fillMaxHeight())
                second(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun TodayMetricCard(
    title: String, value: String, unit: String, description: String,
    modifier: Modifier, warning: Boolean, onClick: () -> Unit,
) {
    val ds = LocalDs.current
    DashboardCard(modifier, insideMargin = PaddingValues(20.dp), onClick = onClick) {
        Text(title, fontSize = 13.sp, color = ds.sub)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) {
            Text(value, fontSize = if (value.toIntOrNull() != null) 34.sp else 26.sp,
                fontWeight = FontWeight.Bold, color = if (warning) ds.danger else ds.text)
            if (unit.isNotEmpty()) Text(unit, fontSize = 13.sp, color = ds.sub, modifier = Modifier.padding(bottom = 5.dp))
        }
        Text(description, fontSize = 12.sp, lineHeight = 18.sp, color = if (warning) ds.danger else ds.sub)
    }
}

@Composable
private fun DashboardSectionTitle(title: String, action: String, onAction: () -> Unit) {
    val ds = LocalDs.current
    Row(Modifier.fillMaxWidth().pageTextBacking().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ds.text,
            modifier = Modifier.weight(1f).semantics { heading() })
        TextButton(text = action, onClick = onAction, minHeight = 48.dp)
    }
}

private fun formatDashboardTime(time: Long): String = Instant.ofEpochMilli(time)
    .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))

@Composable
private fun TodayAssistantPanel(courses: List<TodayCourse>, now: LocalTime, missingDates: Boolean, onOpen: () -> Unit) {
    val ds = LocalDs.current
    val colors = MiuixTheme.colorScheme
    val secondary = secondaryTextColor()
    val current = courses.firstOrNull { it.statusAt(now) == TodayCourseStatus.Ongoing }
    val next = courses.firstOrNull { it.statusAt(now) == TodayCourseStatus.NotStarted }
    val active = current ?: next
    val unknown = courses.count { it.statusAt(now) == TodayCourseStatus.Unknown }
    DashboardCard(
        insideMargin = PaddingValues(20.dp),
        color = if (current != null) {
            if (ds.isDark) Color(0xFF203A29) else Color(0xFFDEFAE4)
        } else ds.card,
        onClick = onOpen,
    ) {
        if (active != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (current != null) "正在上课" else "下一课",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = ds.sub,
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
    val ds = LocalDs.current
    DashboardCard(insideMargin = PaddingValues(20.dp), onClick = onAction) {
        Text(title, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, color = ds.text)
        Text(message, fontSize = 13.sp, lineHeight = 20.sp, color = ds.sub, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(action, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ds.accent)
            Icon(MiuixIcons.ChevronForward, null, Modifier.size(16.dp), tint = ds.accent)
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
