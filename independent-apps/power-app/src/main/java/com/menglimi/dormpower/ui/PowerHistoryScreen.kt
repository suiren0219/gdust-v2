package com.menglimi.dormpower.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.dormpower.power.PowerUiState
import com.menglimi.dormpower.power.balanceChange
import com.menglimi.dormpower.power.powerValue
import com.menglimi.dormpower.power.trendAxis
import com.menglimi.dormpower.power.trendSamples
import com.menglimi.ksu.ui.*
import com.menglimi.power.PowerHistory
import com.menglimi.power.PowerSample
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun PowerHistoryScreen(state: PowerUiState, now: Long, preview: Boolean) {
    if (state.localError != null || state.localLoading && state.reading == null) {
        LocalPowerDataNotice(state)
        return
    }
    var range by rememberSaveable { mutableIntStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val samples = remember(state.samples, range, now) { trendSamples(state.samples, range.takeIf { it > 0 }, now) }
    val days = remember(samples, state.config.zone) {
        PowerHistory.group(samples, state.config.zone).values.sortedByDescending { it.date }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 124.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("电量历史", if (preview) "合成预览 · 示例采样含充值变化" else "本机记录 · 保留全部有效采样") }
        if (state.config.binding == null) item {
            EmptyCard("尚无宿舍记录", "请在电量页绑定宿舍。每次成功查询会留下一个真实读数，不补造此前的历史。", Glyph.CHART)
        } else {
            item {
                KCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 1, 7, 30).forEach { value ->
                            TextButton(onClick = { range = value; expanded = null },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                colors = ButtonDefaults.textButtonColors(containerColor =
                                    if (range == value) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)) {
                                Text(if (value == 0) "全部" else "${value}天")
                            }
                        }
                    }
                    Text("${if (range == 0) "全部记录" else "最近 $range 天"} · ${samples.size} 条有效采样", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                    BalanceTrend(samples, state.config.zone)
                }
            }
            item {
                KCard(Modifier.fillMaxWidth()) {
                    Text("图中展示剩余电量（度）", style = MaterialTheme.typography.titleSmall)
                    Text("余额上升可能是充值或读数修正。日内变化 = 当天末次余额 − 首次余额，不能直接当作耗电量。" +
                        "同一 15 分钟时段仅保留最后读数，缺失时段不补值。", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                }
            }
            item { SectionTitle("按日记录", "点击展开采样") }
            if (days.isEmpty()) item {
                EmptyCard("这段时间还没有记录", "成功刷新后会记录电量。你可以切换范围查看更早的本机采样。", Glyph.CLOCK)
            }
            items(days, key = { it.date.toString() }) { day ->
                val isExpanded = expanded == day.date.toString()
                KCard(Modifier.fillMaxWidth(), onClick = { expanded = if (isExpanded) null else day.date.toString() }) {
                    SectionTitle(day.date.toString(), "${day.samples.size} 条")
                    Text("末次余额 ${powerValue(day.lastOdd)} 度", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 6.dp))
                    Text("日内余额变化 ${balanceChange(day.samples)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    Text("${historyTime(day.samples.first().time, state.config.zone, "HH:mm")} — " +
                        historyTime(day.samples.last().time, state.config.zone, "HH:mm"), fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    Text(if (isExpanded) "收起记录" else "展开记录", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                    if (isExpanded) day.samples.asReversed().forEach { sample ->
                        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(historyTime(sample.time, state.config.zone, "HH:mm:ss"), fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall)
                            Text("${powerValue(sample.odd)} 度", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceTrend(points: List<PowerSample>, zone: ZoneId) {
    if (points.size < 2) {
        Text(if (points.isEmpty()) "— · 暂无采样" else "当前 ${powerValue(points.single().odd)} 度",
            style = MaterialTheme.typography.headlineSmall)
        Text("至少需要两个不同时间的样本才能绘制趋势。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
        return
    }
    val colors = MaterialTheme.colorScheme
    val axis = trendAxis(points)
    val first = points.first()
    val last = points.last()
    val midpoint = first.time + (last.time - first.time) / 2
    Text("剩余电量 / 度", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 12.dp))
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(66.dp).height(168.dp), verticalArrangement = Arrangement.SpaceBetween) {
            axis.ticks.forEach { tick -> Text(powerValue(tick), fontSize = 11.sp, color = colors.onSurfaceVariant,
                maxLines = 1, fontFamily = FontFamily.Monospace) }
        }
        Canvas(Modifier.weight(1f).height(168.dp).semantics {
            contentDescription = "剩余电量趋势，${points.size}个样本，从${powerValue(first.odd)}度到${powerValue(last.odd)}度。" +
                "纵轴${powerValue(axis.min)}至${powerValue(axis.max)}度，横轴${historyTime(first.time, zone)}至${historyTime(last.time, zone)}。"
        }) {
            val inset = 6.dp.toPx()
            val width = size.width - inset * 2
            val height = size.height - inset * 2
            fun position(sample: PowerSample): Offset {
                val x = (sample.time - first.time).toDouble() / (last.time - first.time)
                val y = (sample.odd - axis.min) / (axis.max - axis.min)
                return Offset(inset + width * x.toFloat(), inset + height * (1 - y.toFloat()))
            }
            repeat(3) { index ->
                val y = inset + height * index / 2
                val x = inset + width * index / 2
                drawLine(colors.outlineVariant, Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx())
                drawLine(colors.outlineVariant.copy(alpha = .45f), Offset(x, inset), Offset(x, size.height - inset), 1.dp.toPx())
            }
            val path = Path()
            points.forEachIndexed { index, sample ->
                val point = position(sample)
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(path, colors.primary, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(colors.primary, 3.5.dp.toPx(), position(last))
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 66.dp, top = 8.dp)) {
        listOf(first.time, midpoint, last.time).forEachIndexed { index, time ->
            Text(historyTime(time, zone, "MM-dd\nHH:mm"), fontSize = 10.sp, lineHeight = 14.sp,
                fontFamily = FontFamily.Monospace, color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f), textAlign = when (index) { 0 -> TextAlign.Start; 1 -> TextAlign.Center; else -> TextAlign.End })
        }
    }
}

private fun historyTime(time: Long, zone: ZoneId, pattern: String = "MM-dd HH:mm"): String =
    DateTimeFormatter.ofPattern(pattern).format(Instant.ofEpochMilli(time).atZone(zone))
