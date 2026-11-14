// Course grouping and paging adapted from NexioSchedule revision 28a99a3.
// CZeroX solid surfaces preserve GDUST school times, preferences and date-based status.
package com.menglimi.gdust.v2.ui.schedule

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.CalendarInfo
import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.AppCard
import com.menglimi.gdust.v2.ui.AppUiTokens
import com.menglimi.gdust.v2.ui.LocalDs
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text

internal object ScheduleLayout {
    val RowHeight = 54.dp
    val SectionWidth = 36.dp
    val DateHeight = 40.dp
    val BreakHeight = 24.dp
    val CardGap = 2.dp
    val BottomClearance = 100.dp

    fun sectionY(section: Int, rowHeight: Dp): Dp = rowHeight * (section - 1) +
        (if (section >= 5) BreakHeight else 0.dp) + (if (section >= 9) BreakHeight else 0.dp)
}

@Suppress("UNUSED_PARAMETER") // Legacy week callbacks remain source-compatible; the pager owns week navigation.
@Composable
fun ScheduleScreen(
    repo: Repository,
    calendar: CalendarInfo?,
    blocks: List<CourseBlock>,
    syncError: String?,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSwitchTerm: (String) -> Unit = {},
    fullTermImported: Boolean = false,
    onImport: () -> Unit = {},
) {
    // One snapshot drives dates, week navigation, section markers and course status.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val tick = LocalDateTime.now()
            now = tick
            delay((60_000L - tick.second * 1_000L - tick.nano / 1_000_000L + 50L).coerceAtLeast(50L))
        }
    }
    val termStart = remember(calendar?.startDate) { ScheduleTimes.parseDate(calendar?.startDate) }
    val validBlocks = remember(blocks) {
        blocks.filter { it.week > 0 && it.day in 1..7 && it.startSection > 0 && it.endSection >= it.startSection }
            .distinct()
    }
    val totalWeeks = maxOf(calendar?.allWeek?.takeIf { it > 0 } ?: 20, validBlocks.maxOfOrNull { it.week } ?: 1)
    val currentWeek = termStart?.let { ScheduleTimes.weekAt(it, now.toLocalDate()) }
        ?: calendar?.week ?: 1
    val initialWeek = currentWeek.coerceIn(1, totalWeeks)
    val context = LocalContext.current
    val stored = remember(context) { context.getSharedPreferences("gdust_cache", Context.MODE_PRIVATE) }
    var textScale by remember(repo) { mutableStateOf(repo.store.courseTextScale) }
    var showTeacher by remember(repo) { mutableStateOf(repo.store.showTeacherOnCard) }
    var showClassroom by remember(repo) { mutableStateOf(repo.store.showClassroomOnCard) }
    // Shared storage still defaults to 56dp/top-start. Use Nexio defaults only for unset keys.
    var rowHeight by remember(repo) {
        mutableStateOf(if (stored.contains("course_row_h")) repo.store.courseRowHeight else ScheduleLayout.RowHeight.value.toInt())
    }
    var corner by remember(repo) { mutableStateOf(repo.store.courseCorner) }
    var alignCenter by remember(repo) {
        mutableStateOf(if (stored.contains("card_align_center")) repo.store.cardAlignCenter else true)
    }
    var smartWeekend by remember(repo, repo.store.smartHideWeekend) { mutableStateOf(repo.store.smartHideWeekend) }
    val systemFontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    var showDisplaySettings by remember { mutableStateOf(false) }
    var showTermSwitch by remember { mutableStateOf(false) }

    // Switching terms/start dates resets paging and selection even when the parent keeps this screen alive.
    key(calendar?.year, calendar?.semester, calendar?.startDate) {
        val pager = rememberPagerState(initialPage = initialWeek - 1, pageCount = { totalWeeks })
        val scope = rememberCoroutineScope()
        var selected by remember { mutableStateOf<CourseBlock?>(null) }
        var showLesson by remember { mutableStateOf(false) }
        var showWeekPicker by remember { mutableStateOf(false) }
        val displayWeek = pager.currentPage + 1
        var lastCurrentWeek by remember { mutableStateOf(currentWeek) }
        LaunchedEffect(currentWeek, totalWeeks) {
            // Follow midnight/week rollover only when the user was looking at the previous current week.
            if (pager.currentPage + 1 == lastCurrentWeek && currentWeek in 1..totalWeeks) {
                pager.scrollToPage(currentWeek - 1)
            } else if (pager.currentPage >= totalWeeks) {
                pager.scrollToPage(totalWeeks - 1)
            }
            lastCurrentWeek = currentWeek
        }
        val jumpToWeek: (Int) -> Unit = { week ->
            scope.launch { pager.animateScrollToPage((week - 1).coerceIn(0, totalWeeks - 1)) }
        }
        val weekCounts = remember(validBlocks) { validBlocks.groupingBy { it.week }.eachCount() }
        val blocksByWeek = remember(validBlocks) { validBlocks.groupBy { it.week } }
        val ds = LocalDs.current

        Column(Modifier.fillMaxSize().background(ds.bg).statusBarsPadding()) {
            ScheduleHeader(
                week = displayWeek,
                currentWeek = currentWeek,
                totalWeeks = totalWeeks,
                weekStart = ScheduleTimes.dateAt(termStart, displayWeek, 1),
                onChooseWeek = { showWeekPicker = true },
                onCurrentWeek = { jumpToWeek(currentWeek) },
                onSwitchTerm = { showTermSwitch = true },
                onDisplaySettings = { showDisplaySettings = true },
                onImport = onImport,
            )
            if (syncError != null) {
                AppCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = AppUiTokens.PagePadding, vertical = 6.dp),
                    insideMargin = PaddingValues(AppUiTokens.CardPadding),
                    onClick = onImport,
                ) {
                    Text("同步失败", color = ds.danger, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "$syncError · 点击重新导入",
                        color = ds.sub,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                key = { it },
            ) { page ->
                val week = page + 1
                val weekBlocks = blocksByWeek[week].orEmpty()
                WeekGrid(
                    week = week,
                    termStart = termStart,
                    blocks = weekBlocks,
                    allBlocks = validBlocks,
                    now = now,
                    textScale = textScale,
                    showTeacher = showTeacher,
                    showClassroom = showClassroom,
                    rowHeight = rowHeight.dp * systemFontScale,
                    cornerRadius = corner.coerceIn(6, 14).dp,
                    alignCenter = alignCenter,
                    smartHideWeekend = smartWeekend,
                    emptyTitle = when {
                        validBlocks.isEmpty() && !fullTermImported -> "还没有课表"
                        fullTermImported -> "第 $week 周暂无课程"
                        else -> "第 $week 周尚未导入"
                    },
                    emptyMessage = if (fullTermImported) "教务当前返回的课表中没有本周排课。调课后可重新导入核对。"
                        else "导入教务学期课表，查看实际排课与完整周次。",
                    onImport = onImport,
                    onLessonClick = { selected = it; showLesson = true },
                )
            }
        }
        WeekPickerDialog(
            show = showWeekPicker,
            selectedWeek = displayWeek,
            currentWeek = currentWeek,
            totalWeeks = totalWeeks,
            weekCounts = weekCounts,
            fullTermImported = fullTermImported,
            onPick = { showWeekPicker = false; jumpToWeek(it) },
            onDismiss = { showWeekPicker = false },
        )
        val selectedBlock = selected
        val lessons = remember(selectedBlock, validBlocks) {
            if (selectedBlock == null) emptyList() else aggregateCellLessons(selectedBlock, validBlocks)
        }
        ScheduleLessonSheet(
            show = showLesson,
            lessons = lessons,
            week = selectedBlock?.week ?: displayWeek,
            allWeeks = totalWeeks,
            onDismiss = { showLesson = false },
            onDismissFinished = { selected = null },
        )
    }
    ScheduleDisplayDialog(
        show = showDisplaySettings,
        textScale = textScale,
        showTeacher = showTeacher,
        showClassroom = showClassroom,
        rowHeight = rowHeight,
        corner = corner,
        alignCenter = alignCenter,
        smartWeekend = smartWeekend,
        onScale = { textScale = it; repo.store.courseTextScale = it },
        onTeacher = { showTeacher = it; repo.store.showTeacherOnCard = it },
        onClassroom = { showClassroom = it; repo.store.showClassroomOnCard = it },
        onRowHeight = { rowHeight = it; repo.store.courseRowHeight = it },
        onCorner = { corner = it; repo.store.courseCorner = it },
        onAlignCenter = { alignCenter = it; repo.store.cardAlignCenter = it },
        onSmartWeekend = { smartWeekend = it; repo.store.smartHideWeekend = it },
        onDismiss = { showDisplaySettings = false },
    )
    ScheduleTermDialog(
        show = showTermSwitch,
        year = calendar?.year.orEmpty(),
        current = calendar?.semester.orEmpty(),
        onPick = { showTermSwitch = false; onSwitchTerm(it) },
        onDismiss = { showTermSwitch = false },
    )
}

@Composable
private fun WeekGrid(
    week: Int,
    termStart: LocalDate?,
    blocks: List<CourseBlock>,
    allBlocks: List<CourseBlock>,
    now: LocalDateTime,
    textScale: Float,
    showTeacher: Boolean,
    showClassroom: Boolean,
    rowHeight: Dp,
    cornerRadius: Dp,
    alignCenter: Boolean,
    smartHideWeekend: Boolean,
    emptyTitle: String,
    emptyMessage: String,
    onImport: () -> Unit,
    onLessonClick: (CourseBlock) -> Unit,
) {
    val ds = LocalDs.current
    val today = now.toLocalDate()
    val time = now.toLocalTime()
    val days = remember(blocks, smartHideWeekend) {
        (1..7).filter { !smartHideWeekend || it <= 5 || blocks.any { block -> block.day == it } }
    }
    val dates = remember(termStart, week, days) { days.associateWith { ScheduleTimes.dateAt(termStart, week, it) } }
    // 广科作息只有 10 节；11/12 节按 Nexio 保留刻度，本地没有课就不显示这两行。
    val maxCourseSection = blocks.maxOfOrNull { it.endSection } ?: 0
    val totalSections = maxOf(ScheduleTimes.slots.size, maxCourseSection)
        .coerceAtMost(ScheduleTimes.TOTAL_SECTIONS)
    val gridHeight = remember(rowHeight, totalSections) { ScheduleLayout.sectionY(totalSections, rowHeight) + rowHeight }
    val currentSection = remember(dates, today, time) {
        if (dates.values.any { it == today }) ScheduleTimes.currentSection(time) else null
    }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val systemFontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val sectionWidth = ScheduleLayout.SectionWidth * systemFontScale
    val dateHeight = ScheduleLayout.DateHeight * systemFontScale
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val dayWidth = (maxWidth - sectionWidth - 8.dp) / days.size
        if (dayWidth < 44.dp * systemFontScale) {
            CompactWeekAgenda(week, days, dates, blocks, termStart, now, showTeacher, showClassroom,
                bottomInset, emptyTitle, emptyMessage, onImport, onLessonClick)
            return@BoxWithConstraints
        }
    Column(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
        Row(Modifier.fillMaxWidth().height(dateHeight).padding(end = 2.dp)) {
            Box(Modifier.width(sectionWidth).height(dateHeight), contentAlignment = Alignment.Center) {
                Text(dates.values.firstOrNull()?.let { "${it.monthValue}月" }.orEmpty(), fontSize = 11.sp, color = ds.sub)
            }
            days.forEach { day ->
                val date = dates[day]
                val isToday = date == today
                Box(Modifier.weight(1f).height(dateHeight).padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.fillMaxSize().background(
                            if (isToday) ds.accent.copy(alpha = 0.14f).compositeOver(ds.bg) else ds.bg,
                            RoundedCornerShape(12.dp),
                        ),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            ScheduleTimes.dayNames[day - 1],
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                            color = ds.text,
                            maxLines = 1,
                        )
                        if (date != null) {
                            Text("${date.monthValue}/${date.dayOfMonth}", fontSize = 11.sp, lineHeight = 15.sp, color = ds.sub, maxLines = 1)
                        }
                    }
                }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(top = 2.dp, bottom = ScheduleLayout.BottomClearance + bottomInset),
        ) {
            if (blocks.isEmpty()) {
                ScheduleEmptyState(emptyTitle, emptyMessage, onImport)
            }
            Box(Modifier.fillMaxWidth().height(gridHeight).padding(end = 2.dp)) {
                Row(Modifier.fillMaxSize()) {
                    ScheduleSectionColumn(totalSections, currentSection, rowHeight, gridHeight, sectionWidth)
                    days.forEach { day ->
                        val dayBlocks = remember(blocks, day) { blocks.filter { it.day == day }.sortedBy { it.startSection } }
                        Box(Modifier.weight(1f).height(gridHeight)) {
                            // Keep every real section span. Only exactly matching slots share a card;
                            // partial overlaps get separate lanes rather than extending a representative.
                            val groups = remember(dayBlocks) { groupOverlappingLessons(dayBlocks) }
                            groups.forEach { group ->
                                val lanes = remember(group) { lessonLanes(group) }
                                Row(Modifier.fillMaxSize()) {
                                    lanes.forEach { lane ->
                                        Box(Modifier.weight(1f).height(gridHeight)) {
                                            lane.forEach { slot ->
                                                val block = slot.firstOrNull { ScheduleTimes.isOngoing(it, termStart, now) } ?: slot.first()
                                                val relatedCount = remember(block, allBlocks) { aggregateCellLessons(block, allBlocks).size }
                                                val segments = remember(block.startSection, block.endSection) {
                                                    ScheduleTimes.segments(block.startSection, block.endSection)
                                                }
                                                segments.forEachIndexed { index, segment ->
                                                    LessonCard(
                                                        block = block,
                                                        isPast = slot.all {
                                                            ScheduleTimes.isPast(it.copy(startSection = segment.first, endSection = segment.last), termStart, now)
                                                        },
                                                        isOngoing = slot.any {
                                                            ScheduleTimes.isOngoing(it.copy(startSection = segment.first, endSection = segment.last), termStart, now)
                                                        },
                                                        hasMultipleCourses = index == 0 && relatedCount > 1,
                                                        textScale = textScale,
                                                        showTeacher = showTeacher,
                                                        showClassroom = showClassroom,
                                                        cornerRadius = cornerRadius,
                                                        alignCenter = alignCenter,
                                                        cardHeight = rowHeight * (segment.last - segment.first + 1),
                                                        onClick = { onLessonClick(block) },
                                                        modifier = Modifier.offset(y = ScheduleLayout.sectionY(segment.first, rowHeight)),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                ScheduleBreakBand("午休", sectionWidth, Modifier.offset(y = ScheduleLayout.sectionY(5, rowHeight) - ScheduleLayout.BreakHeight))
                ScheduleBreakBand("晚休", sectionWidth, Modifier.offset(y = ScheduleLayout.sectionY(9, rowHeight) - ScheduleLayout.BreakHeight))
            }
        }
    }
    }
}

/** 列宽不足时用日程卡保留完整文字，日期条仍能访问一周的每一天。 */
@Composable
private fun CompactWeekAgenda(
    week: Int,
    days: List<Int>,
    dates: Map<Int, LocalDate?>,
    blocks: List<CourseBlock>,
    termStart: LocalDate?,
    now: LocalDateTime,
    showTeacher: Boolean,
    showClassroom: Boolean,
    bottomInset: Dp,
    emptyTitle: String,
    emptyMessage: String,
    onImport: () -> Unit,
    onLessonClick: (CourseBlock) -> Unit,
) {
    val ds = LocalDs.current
    var selectedDay by remember(week, days) {
        mutableStateOf(days.firstOrNull { dates[it] == now.toLocalDate() }
            ?: days.firstOrNull { day -> blocks.any { it.day == day } } ?: days.first())
    }
    val dayLessons = blocks.filter { it.day == selectedDay }.sortedBy { it.startSection }
    val dateScroll = rememberLazyListState(initialFirstVisibleItemIndex = days.indexOf(selectedDay).coerceAtLeast(0))
    Column(Modifier.fillMaxSize()) {
        LazyRow(Modifier.fillMaxWidth(), state = dateScroll,
            contentPadding = PaddingValues(horizontal = AppUiTokens.PagePadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(days, key = { it }) { day ->
                val picked = day == selectedDay
                AppCard(
                    modifier = Modifier.semantics { selected = picked },
                    color = if (picked) ds.accentSoft else ds.card,
                    cornerRadius = 20.dp,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    onClick = { selectedDay = day },
                ) {
                    Text(ScheduleTimes.dayNames[day - 1], fontSize = 15.sp,
                        fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal, color = ds.text)
                    dates[day]?.let { Text("${it.monthValue}/${it.dayOfMonth}", fontSize = 13.sp, color = ds.sub) }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = AppUiTokens.PagePadding)
            .padding(top = 8.dp, bottom = ScheduleLayout.BottomClearance + bottomInset),
            verticalArrangement = Arrangement.spacedBy(AppUiTokens.CardGap)) {
            Text("${ScheduleTimes.dayNames[selectedDay - 1]} · ${dayLessons.size} 堂课程", fontSize = 13.sp, color = ds.sub)
            if (blocks.isEmpty()) {
                ScheduleEmptyState(emptyTitle, emptyMessage, onImport)
            } else if (dayLessons.isEmpty()) {
                AppCard(Modifier.fillMaxWidth(), insideMargin = PaddingValues(AppUiTokens.CardPadding)) {
                    Text("今天没有课程", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ds.text)
                    Text("选择其他日期查看安排", fontSize = 13.sp, color = ds.sub, modifier = Modifier.padding(top = 6.dp))
                }
            }
            dayLessons.forEach { lesson ->
                AppCard(Modifier.fillMaxWidth(), insideMargin = PaddingValues(AppUiTokens.CardPadding),
                    onClick = { onLessonClick(lesson) }) {
                    val status = when {
                        ScheduleTimes.isOngoing(lesson, termStart, now) -> " · 上课中"
                        ScheduleTimes.isPast(lesson, termStart, now) -> " · 已结束"
                        else -> ""
                    }
                    Text("第 ${lesson.startSection}–${lesson.endSection} 节$status", fontSize = 13.sp, color = ds.sub)
                    Text(lesson.name, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold,
                        color = ds.text, modifier = Modifier.padding(top = 10.dp))
                    val start = ScheduleTimes.start(lesson.startSection)
                    val end = ScheduleTimes.end(lesson.endSection)
                    if (start.isNotBlank() && end.isNotBlank()) Text("$start–$end", fontSize = 15.sp,
                        color = ds.text, modifier = Modifier.padding(top = 12.dp))
                    if (showClassroom && lesson.room.isNotBlank()) Text(lesson.room, fontSize = 14.sp,
                        color = ds.text, modifier = Modifier.padding(top = 6.dp))
                    if (showTeacher && lesson.teacher.isNotBlank()) Text(lesson.teacher, fontSize = 13.sp,
                        color = ds.sub, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun ScheduleSectionColumn(totalSections: Int, currentSection: Int?, rowHeight: Dp, gridHeight: Dp, sectionWidth: Dp) {
    val ds = LocalDs.current
    Box(Modifier.width(sectionWidth).height(gridHeight)) {
        for (section in 1..totalSections) {
            val current = section == currentSection
            Column(
                Modifier.fillMaxWidth().offset(y = ScheduleLayout.sectionY(section, rowHeight)).height(rowHeight)
                    .padding(vertical = 2.dp)
                    .background(if (current) ds.card else ds.bg, RoundedCornerShape(10.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "$section", fontSize = 15.sp, lineHeight = 18.sp,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                    color = ds.text,
                )
                if (ScheduleTimes.start(section).isNotEmpty()) {
                    Text(ScheduleTimes.start(section), fontSize = 10.sp, lineHeight = 13.sp, color = ds.sub)
                    Text(ScheduleTimes.end(section), fontSize = 10.sp, lineHeight = 13.sp, color = ds.sub)
                }
            }
        }
    }
}

@Composable
private fun ScheduleBreakBand(text: String, sectionWidth: Dp, modifier: Modifier) {
    val ds = LocalDs.current
    Box(
        modifier.fillMaxWidth().height(ScheduleLayout.BreakHeight)
            .padding(start = sectionWidth + 2.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
            .background(ds.breakBar, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 10.sp, color = ds.sub) }
}

@Composable
private fun ScheduleEmptyState(title: String, message: String, onImport: () -> Unit) {
    val ds = LocalDs.current
    AppCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        insideMargin = PaddingValues(AppUiTokens.CardPadding),
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ds.text)
        Text(message, fontSize = 13.sp, lineHeight = 19.sp, color = ds.sub, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
        Button(onClick = onImport, colors = ButtonDefaults.buttonColorsPrimary(), insideMargin = PaddingValues(horizontal = 22.dp, vertical = 10.dp)) {
            Text("导入课表", fontSize = 14.sp)
        }
    }
}

@Composable
private fun LessonCard(
    block: CourseBlock,
    isPast: Boolean,
    isOngoing: Boolean,
    hasMultipleCourses: Boolean,
    textScale: Float,
    showTeacher: Boolean,
    showClassroom: Boolean,
    cornerRadius: Dp,
    alignCenter: Boolean,
    cardHeight: Dp,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val ds = LocalDs.current
    // 先与实底合成，卡片始终不透明；已结束的课程保持同样的可读性。
    val cardColor = CourseColors.tint(block.name)
        .copy(alpha = if (ds.isDark) 0.24f else 0.16f).compositeOver(ds.card)
    val foreground = CourseColors.readableText(ds.text, cardColor)
    val scale = textScale.coerceIn(0.7f, 1.4f)
    val nameSize = (12.7f * scale).sp
    val nameLine = (14.2f * scale).sp
    val infoSize = (11f * scale).sp
    val density = LocalDensity.current
    val availableLines = with(density) { ((cardHeight - 16.dp).value / nameLine.toDp().value).toInt().coerceAtLeast(1) }
    val roomLines = if (showClassroom && block.room.isNotBlank() && availableLines >= 3) 2 else 0
    val teacherLines = if (showTeacher && block.teacher.isNotBlank() && availableLines >= 5) 1 else 0
    val nameLines = (availableLines - roomLines - teacherLines).coerceIn(1, 4)
    val alignment = if (alignCenter) TextAlign.Center else TextAlign.Start
    AppCard(
        modifier = modifier.fillMaxWidth().height(cardHeight).padding(ScheduleLayout.CardGap),
        cornerRadius = cornerRadius,
        color = cardColor,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize().clipToBounds()) {
            Column(
                Modifier.fillMaxWidth()
                    .align(if (alignCenter) Alignment.Center else Alignment.TopStart)
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .clearAndSetSemantics {
                        contentDescription = buildString {
                            append(block.name)
                            if (block.room.isNotBlank()) append("，${block.room}")
                            if (block.teacher.isNotBlank()) append("，${block.teacher}")
                            append("，第 ${block.startSection} 至 ${block.endSection} 节")
                            if (isOngoing) append("，进行中") else if (isPast) append("，已结束")
                            if (hasMultipleCourses) append("，包含其他周次或重叠课程，点击查看")
                        }
                    },
                horizontalAlignment = if (alignCenter) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                Text(block.name, color = foreground, fontSize = nameSize, fontWeight = FontWeight.Bold,
                    lineHeight = nameLine, textAlign = alignment, maxLines = nameLines, overflow = TextOverflow.Ellipsis)
                if (roomLines > 0) {
                    Text(block.room, color = foreground, fontSize = infoSize, lineHeight = nameLine,
                        textAlign = alignment, maxLines = roomLines, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp))
                }
                if (teacherLines > 0) {
                    Text(block.teacher, color = foreground, fontSize = infoSize, lineHeight = nameLine,
                        textAlign = alignment, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (isOngoing) {
                Box(
                    Modifier.align(Alignment.TopCenter).padding(top = 2.dp)
                        .width(16.dp).height(3.dp).background(foreground, CircleShape),
                )
            }
            if (hasMultipleCourses) {
                Box(Modifier.align(Alignment.BottomEnd).padding(5.dp).size(6.dp).background(foreground, CircleShape))
            }
        }
    }
}
