// 电量历史：本机采样按天聚合的日历视图。数据全部来自本机缓存，不打网络。
package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.data.Store
import com.menglimi.gdust.v2.ui.AppCard
import com.menglimi.gdust.v2.ui.AppUiTokens
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.secondaryTextColor
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 电量采样按天聚合：末值=当天最后一条剩余电量；日耗=当天末值-当天首值（充值为负显示）。 */
internal object PowerHistory {
    data class DayStat(
        val date: LocalDate,
        val samples: List<Store.Sample>,
        val lastOdd: Double,
        val dayChange: Double,
    )

    fun group(samples: List<Store.Sample>): Map<LocalDate, DayStat> {
        val zone = ZoneId.systemDefault()
        return samples.filter { it.odd > 0 }
            .groupBy { Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() }
            .mapValues { (date, list) ->
                val sorted = list.sortedBy { it.time }
                DayStat(
                    date = date,
                    samples = sorted,
                    lastOdd = sorted.last().odd,
                    dayChange = sorted.last().odd - sorted.first().odd,
                )
            }
    }
}

private val monthTitle = DateTimeFormatter.ofPattern("yyyy 年 M 月", Locale.CHINA)
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

/** 电量历史记录：月份日历（每天末值）+ 选中日的采样明细。 */
@Composable
fun PowerHistoryScreen(repo: Repository, onBack: () -> Unit) {
    val ds = LocalDs.current
    val secondary = secondaryTextColor()
    val samples = remember(repo) { repo.store.loadSamples() }
    val byDay = remember(samples) { PowerHistory.group(samples) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember {
        mutableStateOf(byDay.keys.maxOrNull() ?: LocalDate.now())
    }
    val today = LocalDate.now()

    SettingSubPage("电量历史", "本机采样按天汇总 · 最多保留 30 天", onBack) {
        AppCard(Modifier.fillMaxWidth(), insideMargin = androidx.compose.foundation.layout.PaddingValues(AppUiTokens.CardPadding)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MonthArrow("上个月") { month = month.minusMonths(1) }
                Text(
                    month.format(monthTitle),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ds.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                MonthArrow("下个月", enabled = month < YearMonth.now()) { month = month.plusMonths(1) }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                    Text(
                        label,
                        fontSize = 11.sp,
                        color = secondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            CalendarGrid(
                month = month,
                today = today,
                selected = selected,
                stats = byDay,
                onSelect = { selected = it },
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendDot(ds.accent, "选中")
                LegendDot(ds.text.copy(alpha = 0.18f), "有采样")
            }
        }

        Spacer(Modifier.height(12.dp))
        val stat = byDay[selected]
        AppCard(Modifier.fillMaxWidth(), insideMargin = androidx.compose.foundation.layout.PaddingValues(AppUiTokens.CardPadding)) {
            Text("${selected.monthValue} 月 ${selected.dayOfMonth} 日", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = ds.text)
            if (stat == null) {
                Text("这一天没有本机采样记录。", fontSize = 13.sp, color = secondary, modifier = Modifier.padding(top = 6.dp))
            } else {
                Text(
                    "末次 %.1f 度 · 当日变化 %+.1f 度 · %d 个采样点".format(stat.lastOdd, stat.dayChange, stat.samples.size),
                    fontSize = 13.sp, lineHeight = 19.sp, color = secondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(8.dp))
                stat.samples.takeLast(40).forEachIndexed { index, sample ->
                    val prev = stat.samples.takeLast(40).getOrNull(index - 1)
                    val delta = prev?.let { sample.odd - it.odd }
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Instant.ofEpochMilli(sample.time).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormat),
                            fontSize = 13.sp, color = secondary, modifier = Modifier.weight(1f),
                        )
                        Text(
                            "%.1f 度".format(sample.odd),
                            fontSize = 13.sp, color = ds.text,
                        )
                        Text(
                            delta?.let { " %+.1f".format(it) } ?: "",
                            fontSize = 13.sp,
                            color = if ((delta ?: 0.0) < 0) ds.danger else secondary,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "数据由本机按采样间隔记录（设置 → 电量采样间隔），仅保留最近 30 天；" +
                "换机、重装或清数据后历史随之清空。学校接口自带的近 7 日用电在电量页单独展示。",
            fontSize = 13.sp, lineHeight = 17.sp, color = secondary,
        )
    }
}

@Composable
private fun CalendarGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    stats: Map<LocalDate, PowerHistory.DayStat>,
    onSelect: (LocalDate) -> Unit,
) {
    val ds = LocalDs.current
    val firstOffset = month.atDay(1).dayOfWeek.value - 1 // 周一为 0
    val days = month.lengthOfMonth()
    val cells = firstOffset + days
    val rows = (cells + 6) / 7
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { col ->
                    val dayIndex = row * 7 + col - firstOffset + 1
                    if (dayIndex in 1..days) {
                        val date = month.atDay(dayIndex)
                        DayCell(
                            date = date,
                            isToday = date == today,
                            isSelected = date == selected,
                            odd = stats[date]?.lastOdd,
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    odd: Double?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ds = LocalDs.current
    val background = when {
        isSelected -> ds.accent.copy(alpha = 0.16f)
        odd != null -> ds.text.copy(alpha = 0.06f)
        else -> Color.Transparent
    }
    Column(
        modifier
            .height(60.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f))
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                selected = isSelected
                contentDescription = "${date.monthValue}月${date.dayOfMonth}日" +
                    (odd?.let { "，%.1f 度".format(it) } ?: "，无采样")
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${date.dayOfMonth}",
            fontSize = 13.sp,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = ds.text,
        )
        Text(
            odd?.let { "%.0f".format(it) } ?: "",
            fontSize = 11.sp,
            color = secondaryTextColor(),
            maxLines = 1,
        )
    }
}

@Composable
private fun MonthArrow(description: String, enabled: Boolean = true, onClick: () -> Unit) {
    val ds = LocalDs.current
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .background(ds.text.copy(alpha = if (enabled) 0.08f else 0.03f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(if (description == "上个月") "‹" else "›", fontSize = 20.sp,
            color = if (enabled) ds.text else ds.sub)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, fontSize = 13.sp, color = secondaryTextColor(), modifier = Modifier.padding(start = 4.dp))
    }
}
