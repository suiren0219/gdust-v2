package com.menglimi.gdust.v2.ui.schedule

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.remember
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
    calendar: CalendarInfo?,
    blocks: List<CourseBlock>,
    syncError: String?,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    val ds = LocalDs.current
    val currentWeek = calendar?.week ?: 1
    var offset by remember { mutableIntStateOf(0) }
    val displayWeek = (currentWeek + offset).coerceAtLeast(1)

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
            Spacer(Modifier.width(28.dp))
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
                text = "还没有课表数据。先登录，再到「设置 → 同步课表」拉一次；全学期周次可在「学期设置」里校正。",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = ds.sub,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        val weekBlocks = remember(blocks, displayWeek) { blocks.filter { it.week == displayWeek } }
        WeekGrid(
            week = displayWeek,
            calendar = calendar,
            blocks = weekBlocks,
        )
    }
}

@Composable
private fun WeekGrid(week: Int, calendar: CalendarInfo?, blocks: List<CourseBlock>) {
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

    Column(Modifier.fillMaxSize()) {
        // 表头
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Spacer(Modifier.width(SCALE_W))
            days.forEach { day ->
                val date = monday?.plusDays(((week - (calendar?.week ?: week)) * 7L + (day - 1)))
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
private fun LessonCard(block: CourseBlock, modifier: Modifier) {
    val ds = LocalDs.current
    val bg = Color(if (ds.isDark) CourseColors.bgDark(block.name) else CourseColors.bg(block.name))
    val fg = if (ds.isDark) ds.text else Color(0xFF3A3A3C)

    // 上课中：右下角状态点
    val now = LocalTime.now()
    val start = ScheduleTimes.start(block.startSection)
    val end = ScheduleTimes.end(block.endSection)
    val ongoing = start.isNotEmpty() && end.isNotEmpty() &&
        !now.isBefore(LocalTime.parse(start)) && !now.isAfter(LocalTime.parse(end))

    Box(
        modifier
            .background(bg, RoundedCornerShape(10.dp))
            .padding(horizontal = 5.dp, vertical = 5.dp),
    ) {
        Column {
            Text(
                block.name,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                color = fg,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (block.room.isNotEmpty()) {
                Text(
                    "@${block.room}",
                    fontSize = 9.sp,
                    color = fg.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (block.endSection - block.startSection >= 1 && block.teacher.isNotEmpty()) {
                Text(
                    block.teacher,
                    fontSize = 8.sp,
                    color = fg.copy(alpha = 0.65f),
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
