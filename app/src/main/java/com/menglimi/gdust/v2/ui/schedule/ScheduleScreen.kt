package com.menglimi.gdust.v2.ui.schedule

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.menglimi.gdust.v2.data.Repository
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.CalendarInfo
import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.LocalWallpaperActive
import java.time.LocalDate
import java.time.LocalTime

private val ROW_H = 56.dp
private val BREAK_H = 24.dp
private val SCALE_W = 40.dp

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
) {
    val ds = LocalDs.current
    val currentWeek = calendar?.week ?: 1
    var offset by remember { mutableIntStateOf(0) }
    val displayWeek = (currentWeek + offset).coerceAtLeast(1)

    // 卡片显示设置（Nexio 式：字号 / 教师 / 地点）
    var textScale by remember { mutableStateOf(repo.store.courseTextScale) }
    var showTeacher by remember { mutableStateOf(repo.store.showTeacherOnCard) }
    var showClassroom by remember { mutableStateOf(repo.store.showClassroomOnCard) }
    var showDisplaySettings by remember { mutableStateOf(false) }
    var rowHeightDp by remember { mutableStateOf(repo.store.courseRowHeight) }
    var cornerDp by remember { mutableStateOf(repo.store.courseCorner) }
    var alignCenter by remember { mutableStateOf(repo.store.cardAlignCenter) }
    var textMono by remember { mutableStateOf(repo.store.cardTextMono) }
    var showTermSwitch by remember { mutableStateOf(false) }

    // 横向滑动翻周：跟手 + 松手惯性判定 + 平滑回弹（不再硬跳）
    var dragPx by remember { mutableStateOf(0f) }
    val rebound = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dragThreshold = with(density) { 56.dp.toPx() }
    val flingVelocity = with(density) { 320.dp.toPx() }
    val enterOffset = with(density) { 110.dp.toPx() }
    val tracker = remember { VelocityTracker() }

    // 点课表格子弹详情
    var selected by remember { mutableStateOf<CourseBlock?>(null) }
    selected?.let { block ->
        // 同一时间格可能有多门课（不同周次）：聚合后一起展示
        val cellLessons = remember(block, blocks) {
            blocks.filter {
                it.day == block.day && it.startSection <= block.endSection && it.endSection >= block.startSection
            }.groupBy { Triple(it.name, it.room, it.teacher) }
                .map { (key, list) ->
                    CellLesson(
                        name = key.first,
                        room = key.second,
                        teacher = key.third,
                        startSection = list.minOf { it.startSection },
                        endSection = list.maxOf { it.endSection },
                        weeks = list.map { it.week }.distinct().sorted(),
                    )
                }
                .sortedBy { it.startSection }
        }
        LessonSheet(
            lessons = cellLessons,
            week = displayWeek,
            allWeek = calendar?.allWeek ?: 20,
            onDismiss = { selected = null },
        )
    }
    if (showTermSwitch) {
        TermSwitchDialog(
            year = calendar?.year.orEmpty(),
            current = calendar?.semester.orEmpty(),
            onPick = { showTermSwitch = false; onSwitchTerm(it) },
            onDismiss = { showTermSwitch = false },
        )
    }
    if (showDisplaySettings) {
        LessonDisplayDialog(
            textScale = textScale,
            showTeacher = showTeacher,
            showClassroom = showClassroom,
            rowHeight = rowHeightDp,
            corner = cornerDp,
            alignCenter = alignCenter,
            textMono = textMono,
            onScale = { textScale = it; repo.store.courseTextScale = it },
            onTeacher = { showTeacher = it; repo.store.showTeacherOnCard = it },
            onClassroom = { showClassroom = it; repo.store.showClassroomOnCard = it },
            onRowHeight = { rowHeightDp = it; repo.store.courseRowHeight = it },
            onCorner = { cornerDp = it; repo.store.courseCorner = it },
            onAlignCenter = { alignCenter = it; repo.store.cardAlignCenter = it },
            onTextMono = { textMono = it; repo.store.cardTextMono = it },
            onDismiss = { showDisplaySettings = false },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // ── 顶部：居中周次 + 右上切换课表/外观（Nexio 式，无左右箭头） ──
        Box(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp)) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("第 $displayWeek 周", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = ds.text)
                if (displayWeek == currentWeek) Text("本周", fontSize = 11.sp, color = ds.accent)
            }
            Row(
                Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 切换课表（整个学期）
                Box(
                    Modifier
                        .size(34.dp)
                        .background(ds.card.copy(alpha = if (LocalWallpaperActive.current) 0.55f else 0.8f), CircleShape)
                        .clickable { showTermSwitch = true },
                    contentAlignment = Alignment.Center,
                ) { Text("⇄", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ds.accent) }
                // 外观设置
                Box(
                    Modifier
                        .size(34.dp)
                        .background(ds.card.copy(alpha = if (LocalWallpaperActive.current) 0.55f else 0.8f), CircleShape)
                        .clickable { showDisplaySettings = true },
                    contentAlignment = Alignment.Center,
                ) { Text("Aa", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ds.accent) }
            }
        }

        if (syncError != null) {
            Text(
                text = "同步失败：$syncError（设置里重试）",
                fontSize = 12.sp,
                color = ds.danger,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        } else if (blocks.isEmpty()) {
            Text(
                text = "尚未获取课表，请到「设置 → 教务学期导入」读取实际排课。",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = ds.sub,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        val weekBlocks = remember(blocks, displayWeek) { blocks.filter { it.week == displayWeek } }
        if (weekBlocks.isEmpty() && blocks.isNotEmpty()) {
            Text(
                if (fullTermImported) "教务当前响应中，本周没有排课；后续调课请重新导入核对。"
                else "第 $displayWeek 周尚未获取。门户仅返回部分周次，请使用教务学期导入。",
                fontSize = 12.sp, color = ds.sub,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        WeekGrid(
            week = displayWeek,
            calendar = calendar,
            blocks = weekBlocks,
            textScale = textScale,
            showTeacher = showTeacher,
            showClassroom = showClassroom,
            rowHeight = rowHeightDp.dp,
            cornerRadius = cornerDp.dp,
            alignCenter = alignCenter,
            textMono = textMono,
            smartHideWeekend = repo.store.smartHideWeekend,
            onLessonClick = { selected = it },
            modifier = Modifier
                .offset {
                    androidx.compose.ui.unit.IntOffset((dragPx + rebound.value).toInt(), 0)
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            dragPx = 0f
                        },
                        onDragEnd = {
                            val vx = runCatching { tracker.calculateVelocity().x }.getOrDefault(0f)
                            val goNext = dragPx <= -dragThreshold || vx <= -flingVelocity
                            val goPrev = dragPx >= dragThreshold || vx >= flingVelocity
                            val canPrev = currentWeek + offset > 1
                            val moved = (goNext || (goPrev && canPrev))
                            if (goNext) offset += 1 else if (goPrev && canPrev) offset -= 1
                            dragPx = 0f
                            // 切周后新内容从滑动方向轻推入位再缓动归零；没切成就原地平滑回弹
                            val start = when {
                                goNext -> enterOffset
                                goPrev && canPrev -> -enterOffset
                                else -> 0f
                            }
                            scope.launch {
                                rebound.snapTo(start)
                                rebound.animateTo(0f, tween(if (moved) 210 else 150, easing = FastOutSlowInEasing))
                            }
                        },
                        onDragCancel = {
                            dragPx = 0f
                            scope.launch { rebound.animateTo(0f, tween(120)) }
                        },
                    ) { change, dragAmount ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        // 右滑=上一周（offset-1），左滑=下一周（offset+1）；不越出学期边界
                        val atStart = currentWeek + offset <= 1 && dragAmount > 0
                        if (!atStart) dragPx += dragAmount
                        change.consume()
                    }
                },
        )
    }
}

@Composable
private fun WeekGrid(
    week: Int,
    calendar: CalendarInfo?,
    blocks: List<CourseBlock>,
    textScale: Float,
    showTeacher: Boolean,
    showClassroom: Boolean,
    rowHeight: androidx.compose.ui.unit.Dp,
    cornerRadius: androidx.compose.ui.unit.Dp,
    alignCenter: Boolean,
    textMono: Boolean,
    smartHideWeekend: Boolean,
    onLessonClick: (CourseBlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ds = LocalDs.current
    val today = LocalDate.now()
    val now = LocalTime.now()

    // 智能周末：开=无课则隐藏周六日；关=始终 7 天
    val days = remember(blocks, smartHideWeekend) {
        if (!smartHideWeekend) (1..7).toList()
        else (1..7).filter { it <= 5 || blocks.any { b -> b.day == it } }
    }

    val monday = remember(calendar) {
        calendar?.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    val ROW_H = rowHeight
    fun sectionY(section: Int) =
        ROW_H * (section - 1) +
            (if (section >= 5) BREAK_H else 0.dp) +
            (if (section >= 9) BREAK_H else 0.dp)

    val gridH = sectionY(ScheduleTimes.TOTAL_SECTIONS) + ROW_H

    Column(modifier.fillMaxSize()) {
        // 表头
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Spacer(Modifier.width(SCALE_W))
            days.forEach { day ->
                // startDate = 学期第一周周一（门户校历语义），不是当前周周一
                val date = monday?.plusDays((week - 1) * 7L + (day - 1))
                val isToday = date == today
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        ScheduleTimes.dayNames[day - 1],
                        fontSize = 13.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) ds.accent else ds.text,
                    )
                    Text(
                        date?.let { "${it.monthValue}/${it.dayOfMonth}" } ?: "",
                        fontSize = 10.sp,
                        color = if (isToday) ds.accent else ds.sub,
                    )
                }
            }
        }

        // 网格
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Box(Modifier.fillMaxWidth().height(gridH)) {
                Row(Modifier.fillMaxWidth().height(gridH)) {
                    // 刻度
                    Box(Modifier.width(SCALE_W).height(gridH)) {
                        val cur = ScheduleTimes.currentSection(now)
                        for (s in 1..ScheduleTimes.TOTAL_SECTIONS) {
                            // 刻度与指示条用 Box 叠加：指示条不占布局高度（占位会把节号顶下去）
                            Box(
                                Modifier.offset(y = sectionY(s)).width(SCALE_W).height(ROW_H),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (s == cur) {
                                    Box(
                                        Modifier
                                            .align(Alignment.CenterStart)
                                            .width(3.dp)
                                            .height(ROW_H - 10.dp)
                                            .background(ds.accent, RoundedCornerShape(2.dp)),
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "$s",
                                        fontSize = 12.sp,
                                        lineHeight = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ds.text,
                                        textAlign = TextAlign.Center,
                                    )
                                    if (ScheduleTimes.start(s).isNotEmpty()) {
                                        Text(ScheduleTimes.start(s), fontSize = 8.sp, lineHeight = 10.sp, color = ds.sub)
                                        Text(ScheduleTimes.end(s), fontSize = 8.sp, lineHeight = 10.sp, color = ds.sub)
                                    }
                                }
                            }
                        }
                    }

                    // 日列
                    days.forEach { day ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(gridH)
                                .padding(horizontal = 1.dp),
                        ) {
                            blocks.filter { it.day == day }.forEach { b ->
                                LessonCard(
                                    block = b,
                                    textScale = textScale,
                                    showTeacher = showTeacher,
                                    showClassroom = showClassroom,
                                    cornerRadius = cornerRadius,
                                    alignCenter = alignCenter,
                                    textMono = textMono,
                                    onClick = { onLessonClick(b) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .offset(y = sectionY(b.startSection) + 2.dp)
                                        .height((ROW_H * (b.endSection - b.startSection + 1)) - 4.dp)
                                        .padding(horizontal = 1.dp),
                                )
                            }
                        }
                    }
                }

                // 午休 / 晚休
                BreakBand("午休", Modifier.offset(y = sectionY(5) - BREAK_H).fillMaxWidth().padding(horizontal = 6.dp))
                BreakBand("晚休", Modifier.offset(y = sectionY(9) - BREAK_H).fillMaxWidth().padding(horizontal = 6.dp))
            }
        }
    }
}

@Composable
private fun BreakBand(text: String, modifier: Modifier) {
    Box(
        modifier
            .height(BREAK_H)
            .padding(vertical = 4.dp)
            .background(LocalDs.current.breakBar, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 10.sp, color = LocalDs.current.sub)
    }
}

@Composable
private fun LessonCard(
    block: CourseBlock,
    textScale: Float,
    showTeacher: Boolean,
    showClassroom: Boolean,
    cornerRadius: androidx.compose.ui.unit.Dp,
    alignCenter: Boolean,
    textMono: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val ds = LocalDs.current
    // Nexio 视觉：tint 低透明度底 + tint 原色文字（CourseCard.kt 规则）
    val tint = CourseColors.tint(block.name)
    val bg = CourseColors.bg(block.name, ds.isDark)
    // 单色模式：文字跟随主题，不吃课程色
    val fg = if (textMono) ds.text else tint

    // 上课中：右下角状态点
    val now = LocalTime.now()
    val start = ScheduleTimes.start(block.startSection)
    val end = ScheduleTimes.end(block.endSection)
    val ongoing = start.isNotEmpty() && end.isNotEmpty() &&
        !now.isBefore(LocalTime.parse(start)) && !now.isAfter(LocalTime.parse(end))

    val onWallpaper = com.menglimi.gdust.v2.ui.LocalWallpaperActive.current
    val cardBg = if (onWallpaper) {
        if (ds.isDark) Color.Black.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.66f)
    } else bg
    Box(
        modifier
            .background(cardBg, RoundedCornerShape(cornerRadius))
            .then(
                if (onWallpaper) Modifier.border(
                    1.dp,
                    Color.White.copy(alpha = if (ds.isDark) 0.10f else 0.7f),
                    RoundedCornerShape(cornerRadius),
                ) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 5.dp),
    ) {
        Column(
            horizontalAlignment = if (alignCenter) Alignment.CenterHorizontally else Alignment.Start,
            modifier = if (alignCenter) Modifier.fillMaxWidth() else Modifier,
        ) {
            val scale = textScale.coerceIn(0.7f, 1.4f)
            Text(
                block.name,
                fontSize = (12.7f * scale).sp,
                lineHeight = (14.2f * scale).sp,
                fontWeight = FontWeight.Bold,
                color = fg,
                maxLines = if (scale > 1.2f) 3 else 4,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (alignCenter) TextAlign.Center else TextAlign.Start,
            )
            if (showClassroom && block.room.isNotEmpty()) {
                Text(
                    "@${block.room}",
                    fontSize = (11f * scale).sp,
                    lineHeight = (12f * scale).sp,
                    color = fg,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showTeacher && block.teacher.isNotEmpty()) {
                Text(
                    block.teacher,
                    fontSize = (11f * scale).sp,
                    lineHeight = (12f * scale).sp,
                    color = fg,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (ongoing) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(6.dp)
                    .background(Color(0xFFE8A23C), CircleShape),
            )
        }
    }
}

/** 同一时间格里的一门课（聚合后）。 */
private data class CellLesson(
    val name: String,
    val room: String,
    val teacher: String,
    val startSection: Int,
    val endSection: Int,
    val weeks: List<Int>,
)

/** 把周次列表压成「1-8周、10周、12-14周」这种紧凑描述。 */
private fun formatWeeks(weeks: List<Int>, allWeek: Int): String {
    if (weeks.isEmpty()) return ""
    if (weeks.size >= allWeek) return "每周"
    val sorted = weeks.distinct().sorted()
    val sb = StringBuilder()
    var start = sorted.first()
    var prev = start
    fun flush() {
        if (sb.isNotEmpty()) sb.append("、")
        if (prev == start) sb.append("${start}周") else sb.append("$start-$prev 周")
    }
    sorted.drop(1).forEach { w ->
        if (w == prev + 1) {
            prev = w
        } else {
            flush(); start = w; prev = w
        }
    }
    flush()
    return sb.toString()
}

/** 课程详情：底部面板（Nexio 式），列出该时间格的所有课 + 周次徽章。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LessonSheet(
    lessons: List<CellLesson>,
    week: Int,
    allWeek: Int,
    onDismiss: () -> Unit,
) {
    val ds = LocalDs.current
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ds.card,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .background(ds.sub.copy(alpha = 0.35f), RoundedCornerShape(2.dp)),
                )
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 26.dp),
        ) {
            Text("课程详情", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ds.text)
            Text(
                "第 $week 周 · 这个时间格共 ${lessons.size} 门课",
                fontSize = 11.sp, color = ds.sub, modifier = Modifier.padding(top = 3.dp),
            )
            Spacer(Modifier.height(14.dp))
            lessons.forEach { l ->
                val tint = CourseColors.tint(l.name)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .background(
                            if (ds.isDark) tint.copy(alpha = 0.14f) else tint.copy(alpha = 0.10f),
                            RoundedCornerShape(14.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            l.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = tint,
                            maxLines = 2,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            formatWeeks(l.weeks, allWeek),
                            fontSize = 10.sp,
                            color = ds.sub,
                            maxLines = 1,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .background(ds.sub.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    val start = ScheduleTimes.start(l.startSection)
                    val end = ScheduleTimes.end(l.endSection)
                    Text(
                        buildString {
                            append("第 ${l.startSection}-${l.endSection} 节")
                            if (start.isNotEmpty() && end.isNotEmpty()) append("（$start-$end）")
                            if (l.room.isNotBlank()) append(" · ${l.room}")
                        },
                        fontSize = 12.sp, color = ds.text,
                    )
                    if (l.teacher.isNotBlank()) {
                        Text(l.teacher, fontSize = 12.sp, color = ds.sub, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(ds.accent, RoundedCornerShape(12.dp))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("知道了", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

/** 课表显示设置：字号档位 + 地点/教师开关（Nexio 式，立即生效并持久化）。 */
@Composable
private fun LessonDisplayDialog(
    textScale: Float,
    showTeacher: Boolean,
    showClassroom: Boolean,
    rowHeight: Int,
    corner: Int,
    alignCenter: Boolean,
    textMono: Boolean,
    onScale: (Float) -> Unit,
    onTeacher: (Boolean) -> Unit,
    onClassroom: (Boolean) -> Unit,
    onRowHeight: (Int) -> Unit,
    onCorner: (Int) -> Unit,
    onAlignCenter: (Boolean) -> Unit,
    onTextMono: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val ds = LocalDs.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(ds.card, RoundedCornerShape(20.dp))
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("课表显示", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ds.text)
            Spacer(Modifier.height(14.dp))
            Text("卡片字号", fontSize = 13.sp, color = ds.sub)
            Spacer(Modifier.height(8.dp))
            com.menglimi.gdust.v2.ui.screens.SegmentedChoice(
                options = listOf(
                    0.85f to "小",
                    1.0f to "标准",
                    1.15f to "大",
                    1.3f to "特大",
                ),
                selected = listOf(0.85f, 1.0f, 1.15f, 1.3f).minBy { kotlin.math.abs(it - textScale) },
                onSelect = onScale,
            )
            Spacer(Modifier.height(14.dp))
            Text("卡片高度", fontSize = 13.sp, color = ds.sub)
            Spacer(Modifier.height(8.dp))
            com.menglimi.gdust.v2.ui.screens.SegmentedChoice(
                options = listOf(48 to "紧凑", 56 to "标准", 64 to "宽松"),
                selected = listOf(48, 56, 64).minBy { kotlin.math.abs(it - rowHeight) },
                onSelect = onRowHeight,
            )
            Spacer(Modifier.height(14.dp))
            Text("卡片圆角", fontSize = 13.sp, color = ds.sub)
            Spacer(Modifier.height(8.dp))
            com.menglimi.gdust.v2.ui.screens.SegmentedChoice(
                options = listOf(6 to "小", 10 to "中", 14 to "大"),
                selected = listOf(6, 10, 14).minBy { kotlin.math.abs(it - corner) },
                onSelect = onCorner,
            )
            Spacer(Modifier.height(14.dp))
            Text("内容对齐", fontSize = 13.sp, color = ds.sub)
            Spacer(Modifier.height(8.dp))
            com.menglimi.gdust.v2.ui.screens.SegmentedChoice(
                options = listOf(false to "左对齐", true to "居中"),
                selected = alignCenter,
                onSelect = onAlignCenter,
            )
            Spacer(Modifier.height(14.dp))
            Text("文字颜色", fontSize = 13.sp, color = ds.sub)
            Spacer(Modifier.height(8.dp))
            com.menglimi.gdust.v2.ui.screens.SegmentedChoice(
                options = listOf(false to "彩色", true to "单色"),
                selected = textMono,
                onSelect = onTextMono,
            )
            Spacer(Modifier.height(16.dp))
            DisplayToggleRow("显示地点", showClassroom, onClassroom)
            Spacer(Modifier.height(10.dp))
            DisplayToggleRow("显示教师", showTeacher, onTeacher)
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(ds.accent, RoundedCornerShape(12.dp))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("完成", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun DisplayToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val ds = LocalDs.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = ds.text, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = ds.accent),
        )
    }
}

/** 切换课表（学期）：切换后清空当前展示并尝试重新同步该学期。 */
@Composable
private fun TermSwitchDialog(
    year: String,
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val ds = LocalDs.current
    val options = listOf("3" to "第一学期", "12" to "第二学期")
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(ds.card, RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            Text("切换课表", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ds.text)
            Text(
                if (year.isNotBlank()) "$year 学年" else "选择学期",
                fontSize = 12.sp, color = ds.sub, modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(14.dp))
            options.forEach { (code, label) ->
                val picked = code == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (picked) ds.accent.copy(alpha = 0.14f) else Color.Transparent,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { if (!picked) onPick(code) else onDismiss() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, fontSize = 15.sp, color = ds.text, modifier = Modifier.weight(1f))
                    if (picked) Text("当前", fontSize = 11.sp, color = ds.accent)
                }
                Spacer(Modifier.height(6.dp))
            }
            Text(
                "切换后会清空当前展示并尝试同步该学期课表；失败时可回到设置用网页导入。",
                fontSize = 11.sp, lineHeight = 15.sp, color = ds.sub,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(ds.sub.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text("取消", fontSize = 14.sp, color = ds.sub) }
        }
    }
}
