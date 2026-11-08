package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.CalendarInfo
import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.ui.GlassCard
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.schedule.ScheduleTimes
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun TodayScreen(calendar: CalendarInfo?, blocks: List<CourseBlock>) {
    val ds = LocalDs.current
    val today = LocalDate.now()
    val now = LocalTime.now()
    val week = calendar?.week ?: 1
    val day = today.dayOfWeek.value

    val todayBlocks = remember(blocks, week, day) {
        blocks.filter { it.week == week && it.day == day }.sortedBy { it.startSection }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(ds.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        PageTitle(
            title = "今天是周${ScheduleTimes.dayNames[day - 1].removePrefix("周")}",
            subtitle = "${today.year}年${today.monthValue}月${today.dayOfMonth}日 · 第 $week 周",
        )

        if (todayBlocks.isEmpty()) {
            GlassCard {
                Text(
                    "今天没有课程，好好休息～",
                    fontSize = 15.sp,
                    color = ds.text,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                todayBlocks.forEach { b ->
                    TodayCard(b, now)
                }
            }
        }

        // 今日小结
        if (todayBlocks.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                "共 ${todayBlocks.size} 门课 · 最早 ${ScheduleTimes.start(todayBlocks.first().startSection)}",
                fontSize = 12.sp,
                color = ds.sub,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Spacer(Modifier.height(110.dp))
    }
}

@Composable
private fun TodayCard(b: CourseBlock, now: LocalTime) {
    val ds = LocalDs.current
    val start = ScheduleTimes.start(b.startSection)
    val end = ScheduleTimes.end(b.endSection)
    val status = when {
        start.isEmpty() || end.isEmpty() -> ""
        now.isBefore(LocalTime.parse(start)) -> "未开始"
        now.isAfter(LocalTime.parse(end)) -> "已结束"
        else -> "上课中"
    }
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.padding(end = 14.dp)) {
                Text(start, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ds.text)
                Text(end, fontSize = 11.sp, color = ds.sub)
            }
            Box(
                Modifier
                    .width(3.dp)
                    .height(38.dp)
                    .background(if (status == "上课中") ds.accent else ds.sub.copy(alpha = 0.35f), RoundedCornerShape(2.dp)),
            )
            Column(Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        b.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ds.text,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (status.isNotEmpty()) {
                        Text(
                            status,
                            fontSize = 10.sp,
                            color = if (status == "上课中") ds.accent else ds.sub,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                Text(
                    "第${b.startSection}${if (b.endSection > b.startSection) "-${b.endSection}" else ""}节 · ${b.room.ifEmpty { "待定" }} · ${b.teacher.ifEmpty { "-" }}",
                    fontSize = 12.sp,
                    color = ds.sub,
                )
            }
        }
    }
}
