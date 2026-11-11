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
        LessonDetailDialog(block = block, week = displayWeek, calendar = calendar, onDismiss = { selected = null })
    }
    if (showDisplaySettings) {
        LessonDisplayDialog(
            textScale = textScale,
            showTeacher = showTeacher,
            showClassroom = showClassroom,
            onScale = { textScale = it; repo.store.courseTextScale = it },
            onTeacher = { showTeacher = it; repo.store.showTeacherOnCard = it },
            onClassroom = { showClassroom = it; repo.store.showClassroomOnCard = it },
            onDismiss = { showDisplaySettings = false },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // ── 顶部：翻周 ──
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(48.dp))
            Text(
                text = "<",
                fontSize = 22.sp,
                color = ds.sub,
                modifier = Modifier
                    .clickable { offset -= 1 }
                    .padding(horizontal = 12.dp),
            )
            AnimatedContent(
                targetState = displayWeek,
                transitionSpec = {
                    val forward = targetState > initialState
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(240)) { it / 3 * dir } + fadeIn(tween(180)))
                        .togetherWith(slideOutHorizontally(tween(240)) { -it / 3 * dir } + fadeOut(tween(180)))
                },
                modifier = Modifier.weight(1f),
                label = "week",
            ) { week ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("第 $week 周", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ds.text)
                    if (week == currentWeek) {
                        Text("本周", fontSize = 11.sp, color = ds.accent)
                    }
                }
            }
            Text(
                text = ">",
                fontSize = 22.sp,
                color = ds.sub,
                modifier = Modifier
                    .clickable { offset += 1 }
                    .padding(horizontal = 12.dp),
            )
            // 显示设置（字号 / 教师 / 地点），Nexio 式右上角入口
            Box(
                Modifier
                    .padding(start = 2.dp, end = 10.dp)
                    .size(34.dp)
                    .background(ds.card.copy(alpha = 0.75f), CircleShape)
                    .clickable { showDisplaySettings = true },
                contentAlignment = Alignment.Center,
            ) {
                Text("Aa", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ds.accent)
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
    onLessonClick: (CourseBlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ds = LocalDs.current
    val today = LocalDate.now()
    val now = LocalTime.now()

    // 智能周末：无课则隐藏周六日
    val days = remember(blocks) {
        (1..7).filter { it <= 5 || blocks.any { b -> b.day == it } }
    }

    val monday = remember(calendar) {
        calendar?.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

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
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val ds = LocalDs.current
    // Nexio 视觉：tint 低透明度底 + tint 原色文字（CourseCard.kt 规则）
    val tint = CourseColors.tint(block.name)
    val bg = CourseColors.bg(block.name, ds.isDark)
    val fg = tint

    // 上课中：右下角状态点
    val now = LocalTime.now()
    val start = ScheduleTimes.start(block.startSection)
    val end = ScheduleTimes.end(block.endSection)
    val ongoing = start.isNotEmpty() && end.isNotEmpty() &&
        !now.isBefore(LocalTime.parse(start)) && !now.isAfter(LocalTime.parse(end))

    Box(
        modifier
            .background(bg, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 5.dp),
    ) {
        Column {
            val scale = textScale.coerceIn(0.7f, 1.4f)
            Text(
                block.name,
                fontSize = (12.7f * scale).sp,
                lineHeight = (14.2f * scale).sp,
                fontWeight = FontWeight.Bold,
                color = fg,
                maxLines = if (scale > 1.2f) 3 else 4,
                overflow = TextOverflow.Ellipsis,
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

/** 课程详情弹窗：点格子查看完整信息；颜色沿用格子同源色。 */
@Composable
private fun LessonDetailDialog(block: CourseBlock, week: Int, calendar: CalendarInfo?, onDismiss: () -> Unit) {
    val ds = LocalDs.current
    val bg = CourseColors.bg(block.name, ds.isDark)
    val tint = CourseColors.tint(block.name)
    val start = ScheduleTimes.start(block.startSection)
    val end = ScheduleTimes.end(block.endSection)
    val date = calendar?.startDate?.let {
        runCatching { java.time.LocalDate.parse(it).plusDays((week - 1) * 7L + block.day - 1L).toString() }.getOrNull()
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(ds.card, RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            Box(
                Modifier
                    .background(bg, RoundedCornerShape(12.dp))
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(block.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = tint)
            }
            Spacer(Modifier.height(14.dp))
            listOf(
                "时间" to "周${ScheduleTimes.dayNames[block.day - 1]} · 第 ${block.startSection}–${block.endSection} 节" +
                    (if (start.isNotEmpty() && end.isNotEmpty()) "（$start–$end）" else ""),
                "教室" to block.room.ifEmpty { "未填写" },
                "教师" to block.teacher.ifEmpty { "未填写" },
                "周次" to "第 $week 周" + if (block.weekParity == 1) "（单周）" else if (block.weekParity == 2) "（双周）" else "",
                "日期" to (date ?: "未知（需校历）"),
            ).forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(k, fontSize = 13.sp, color = ds.sub, modifier = Modifier.width(44.dp))
                    Text(v, fontSize = 13.sp, color = ds.text, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(ds.accent, RoundedCornerShape(12.dp))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 10.dp),
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
    onScale: (Float) -> Unit,
    onTeacher: (Boolean) -> Unit,
    onClassroom: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val ds = LocalDs.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(ds.card, RoundedCornerShape(20.dp))
                .padding(20.dp),
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
