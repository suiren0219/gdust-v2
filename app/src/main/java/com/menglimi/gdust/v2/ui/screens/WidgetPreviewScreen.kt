package com.menglimi.gdust.v2.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.TodoGroups
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.screens.PageTitle
import com.menglimi.gdust.v2.widget.WidgetData
import com.menglimi.gdust.v2.widget.WidgetDataSource
import com.menglimi.gdust.v2.widget.WidgetPreset
import com.menglimi.gdust.v2.widget.WidgetPresets
import com.menglimi.gdust.v2.widget.WidgetPresentation
import com.menglimi.gdust.v2.widget.WidgetStyle
import com.menglimi.gdust.v2.widget.pinWidget
import kotlinx.coroutines.launch

/**
 * 桌面小部件预览子页面：九款按真实缓存数据渲染迷你预览，页内一键添加。
 * 预览与桌面渲染共用 WidgetPresentation 文案与 WidgetStyle 走势图，所见即所得。
 */
@Composable
fun WidgetPreviewScreen(onBack: () -> Unit) {
    val ds = LocalDs.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler { onBack() }

    val data = remember { WidgetDataSource.load(context) }
    val groups = remember { WidgetDataSource.todos(context) }
    val days = remember { WidgetDataSource.powerRangeDays(context) }

    Column(
        Modifier
            .fillMaxSize()
            .background(ds.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        PageTitle(title = "桌面小部件", onBack = onBack)
        M3Text(
            "九款均为玻璃卡画风，跟随系统深浅色；预览使用你本机的真实数据。",
            fontSize = 13.sp, color = ds.sub, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
        )

        SectionLabel("课表数据")
        PresetRow(WidgetPresets.ALL[0]) { SchedulePreview(data, WidgetPresentation.Shape.WIDE) }
        PresetRow(WidgetPresets.ALL[1]) { SchedulePreview(data, WidgetPresentation.Shape.MEDIUM) }
        PresetRow(WidgetPresets.ALL[2]) { SchedulePreview(data, WidgetPresentation.Shape.TALL) }

        SectionLabel("宿舍电量")
        PresetRow(WidgetPresets.ALL[3]) { PowerPreview(data, days, WidgetPresentation.Shape.WIDE) }
        PresetRow(WidgetPresets.ALL[4]) { PowerPreview(data, days, WidgetPresentation.Shape.MEDIUM) }
        PresetRow(WidgetPresets.ALL[5]) { PowerPreview(data, days, WidgetPresentation.Shape.TALL) }

        SectionLabel("待办条子")
        PresetRow(WidgetPresets.ALL[6]) { TodoPreview(groups, WidgetPresentation.Shape.WIDE) }
        PresetRow(WidgetPresets.ALL[7]) { TodoPreview(groups, WidgetPresentation.Shape.MEDIUM) }
        PresetRow(WidgetPresets.ALL[8]) { TodoPreview(groups, WidgetPresentation.Shape.TALL) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(title: String) {
    val ds = LocalDs.current
    M3Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = ds.text, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
}

@Composable
private fun PresetRow(preset: WidgetPreset, preview: @Composable () -> Unit) {
    val ds = LocalDs.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ds.card)
            .clickable {
                scope.launch {
                    val ok = pinWidget(context, preset)
                    Toast.makeText(
                        context,
                        if (ok) "请在桌面确认添加" else "当前桌面不支持一键添加，请长按桌面手动添加",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            .padding(12.dp),
    ) {
        preview()
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            M3Text(preset.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ds.text)
            Spacer(Modifier.weight(1f))
            M3Text("点按添加 ›", fontSize = 12.sp, color = ds.accent)
        }
    }
    Spacer(Modifier.height(10.dp))
}

// ── 预览卡容器：玻璃底与桌面渲染同一张画法 ──────────────────────────────────

@Composable
private fun PreviewCard(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = WidgetStyle.isDark(context)
    val density = context.resources.displayMetrics.density
    val bmp = remember(width, height, dark) {
        WidgetStyle.glassCard(context, (width.value * density).toInt(), (height.value * density).toInt(), WidgetStyle.radius.value * density)
    }
    Box(
        Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(WidgetStyle.radius)),
    ) {
        Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
        Box(Modifier.padding(10.dp)) { content() }
    }
}

private fun wtc(dark: Boolean) = WidgetStyle.text(dark)
private fun wsc(dark: Boolean) = WidgetStyle.sub(dark)
private fun wac(dark: Boolean) = Color(WidgetStyle.accent(dark))

@Composable
private fun MiniBadge(text: String, dark: Boolean, width: androidx.compose.ui.unit.Dp? = null) {
    var m = Modifier.clip(RoundedCornerShape(9.dp)).background(wac(dark).copy(alpha = if (dark) 0.26f else 0.15f))
    if (width != null) m = m.width(width)
    Box(m.padding(horizontal = 7.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
        M3Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = wac(dark))
    }
}

// ── 课表预览 ────────────────────────────────────────────────────────────────

@Composable
fun SchedulePreview(data: WidgetData, shape: WidgetPresentation.Shape) {
    val dark = WidgetStyle.isDark(LocalContext.current)
    val size = when (shape) {
        WidgetPresentation.Shape.WIDE -> 340.dp to 170.dp
        WidgetPresentation.Shape.MEDIUM -> 180.dp to 180.dp
        WidgetPresentation.Shape.TALL -> 96.dp to 192.dp
        else -> 340.dp to 74.dp
    }
    PreviewCard(size.first, size.second) {
        when (shape) {
            WidgetPresentation.Shape.WIDE -> Column {
                Row(Modifier.fillMaxWidth()) {
                    M3Text(WidgetPresentation.scheduleTitle(data), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = wtc(dark))
                    Spacer(Modifier.weight(1f))
                    M3Text(WidgetPresentation.scheduleSummary(data), fontSize = 10.sp, color = wsc(dark))
                }
                Spacer(Modifier.height(6.dp))
                data.lessons.take(3).forEach { b ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        MiniBadge(WidgetPresentation.sectionLabel(b), dark, 36.dp)
                        Spacer(Modifier.width(8.dp))
                        M3Text(b.name, fontSize = 12.sp, color = wtc(dark), maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        M3Text(b.room, fontSize = 10.sp, color = wsc(dark), maxLines = 1)
                    }
                }
            }
            WidgetPresentation.Shape.MEDIUM -> Column(Modifier.fillMaxSize()) {
                val next = data.lessons.firstOrNull()
                M3Text(
                    if (data.scope == WidgetData.Scope.TOMORROW) "下一节 · 明天"
                    else next?.let { "下一节 · " + com.menglimi.gdust.v2.ui.schedule.ScheduleTimes.start(it.startSection) } ?: "下一节",
                    fontSize = 10.sp, color = wsc(dark),
                )
                Spacer(Modifier.height(6.dp))
                M3Text(next?.name ?: WidgetPresentation.scheduleEmpty(data), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = wtc(dark))
                Spacer(Modifier.weight(1f))
                if (next != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MiniBadge(WidgetPresentation.sectionLabel(next), dark)
                        Spacer(Modifier.width(8.dp))
                        M3Text(next.room, fontSize = 10.sp, color = wsc(dark), maxLines = 1)
                    }
                    M3Text(WidgetPresentation.nextCountdown(data.scope, next, data.now.toLocalTime()), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = wac(dark))
                }
            }
            WidgetPresentation.Shape.TALL -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                MiniBadge(data.week?.let { "第 $it 周" } ?: "课表", dark)
                Spacer(Modifier.weight(1f))
                M3Text("${data.lessons.size}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = wtc(dark))
                M3Text(if (data.scope == WidgetData.Scope.TOMORROW) "明天课程" else "剩余课程", fontSize = 9.sp, color = wsc(dark))
                Spacer(Modifier.weight(1f))
                M3Text(WidgetPresentation.tallFootnote(data), fontSize = 9.sp, color = wsc(dark), maxLines = 2)
            }
            else -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                val next = data.lessons.firstOrNull()
                if (next != null) {
                    MiniBadge(WidgetPresentation.sectionLabel(next), dark)
                    Spacer(Modifier.width(8.dp))
                    M3Text(next.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = wtc(dark), maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    M3Text(WidgetPresentation.nextCountdown(data.scope, next, data.now.toLocalTime()), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = wac(dark))
                } else {
                    M3Text(WidgetPresentation.scheduleEmpty(data), fontSize = 12.sp, color = wsc(dark))
                }
            }
        }
    }
}

// ── 电量预览 ────────────────────────────────────────────────────────────────

@Composable
fun PowerPreview(data: WidgetData, days: Int, shape: WidgetPresentation.Shape) {
    val context = LocalContext.current
    val dark = WidgetStyle.isDark(context)
    val density = context.resources.displayMetrics.density
    val samples = remember(days) { WidgetDataSource.trend(context, days) }
    val stats = WidgetPresentation.trendStats(samples, data.lastSampleAt, data.now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
    val low = WidgetPresentation.powerLow(data.odd, data.warnTh)
    val valueColor = if (low) Color(WidgetStyle.DANGER) else wac(dark)
    val size = when (shape) {
        WidgetPresentation.Shape.WIDE -> 340.dp to 170.dp
        WidgetPresentation.Shape.MEDIUM -> 180.dp to 180.dp
        WidgetPresentation.Shape.TALL -> 96.dp to 192.dp
        else -> 340.dp to 74.dp
    }
    PreviewCard(size.first, size.second) {
        when (shape) {
            WidgetPresentation.Shape.WIDE -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth()) {
                    M3Text(data.roomName.ifEmpty { "宿舍电量" }, fontSize = 10.sp, color = wsc(dark))
                    Spacer(Modifier.weight(1f))
                    MiniBadge(if (days == 7) "七天" else "半月", dark)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    M3Text(WidgetPresentation.powerValue(data.odd), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = valueColor)
                    M3Text(" 度", fontSize = 11.sp, color = wsc(dark))
                    Spacer(Modifier.weight(1f))
                    M3Text(WidgetPresentation.rateLabel(stats), fontSize = 10.sp, color = wsc(dark))
                }
                Spacer(Modifier.weight(1f))
                if (samples.size >= 2) {
                    val bmp = remember(samples, days) {
                        WidgetStyle.trend(context, samples, (320 * density).toInt(), (72 * density).toInt(), days, data.warnTh)
                    }
                    bmp?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().height(72.dp)) }
                }
            }
            WidgetPresentation.Shape.MEDIUM -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth()) {
                    M3Text(data.roomName.ifEmpty { "宿舍电量" }, fontSize = 10.sp, color = wsc(dark))
                    Spacer(Modifier.weight(1f))
                    M3Text(stats?.updatedLabel.orEmpty(), fontSize = 9.sp, color = wsc(dark))
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    M3Text(WidgetPresentation.powerValue(data.odd), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = valueColor)
                    M3Text(" 度", fontSize = 10.sp, color = wsc(dark))
                }
                Spacer(Modifier.weight(1f))
                if (samples.size >= 2) {
                    val bmp = remember(samples, days) {
                        WidgetStyle.trend(context, samples, (160 * density).toInt(), (34 * density).toInt(), days, data.warnTh)
                    }
                    bmp?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().height(34.dp)) }
                }
            }
            WidgetPresentation.Shape.TALL -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                MiniBadge("电量", dark)
                Spacer(Modifier.weight(1f))
                M3Text(WidgetPresentation.powerValue(data.odd), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = valueColor)
                M3Text("度 · " + data.roomName.ifEmpty { "宿舍" }, fontSize = 8.sp, color = wsc(dark), maxLines = 2)
                Spacer(Modifier.weight(1f))
                M3Text(if (days == 7) "近 7 天" else "近半月", fontSize = 8.sp, color = wsc(dark))
            }
            else -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                MiniBadge("电量", dark)
                Spacer(Modifier.width(8.dp))
                M3Text(WidgetPresentation.powerValue(data.odd), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = valueColor)
                M3Text(" 度", fontSize = 10.sp, color = wsc(dark))
                Spacer(Modifier.weight(1f))
                M3Text(if (days == 7) "近 7 天" else "近半月", fontSize = 9.sp, color = wsc(dark))
            }
        }
    }
}

// ── 待办预览 ────────────────────────────────────────────────────────────────

@Composable
fun TodoPreview(groups: TodoGroups, shape: WidgetPresentation.Shape) {
    val dark = WidgetStyle.isDark(LocalContext.current)
    val items = remember(groups) { WidgetPresentation.pendingTodos(groups, System.currentTimeMillis()) }
    val danger = Color(WidgetStyle.DANGER)
    val size = when (shape) {
        WidgetPresentation.Shape.WIDE -> 340.dp to 170.dp
        WidgetPresentation.Shape.MEDIUM -> 180.dp to 180.dp
        WidgetPresentation.Shape.TALL -> 96.dp to 192.dp
        else -> 340.dp to 74.dp
    }
    PreviewCard(size.first, size.second) {
        when (shape) {
            WidgetPresentation.Shape.WIDE -> Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    M3Text("待办", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = wtc(dark))
                    if (groups.overdue.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        MiniBadge("${groups.overdue.size} 逾期", dark)
                    }
                    Spacer(Modifier.weight(1f))
                    M3Text("共 ${items.size} 项", fontSize = 10.sp, color = wsc(dark))
                }
                Spacer(Modifier.height(4.dp))
                items.take(4).forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        M3Text(if (item.overdue) "◆" else "○", fontSize = 11.sp, color = if (item.overdue) danger else wac(dark))
                        Spacer(Modifier.width(8.dp))
                        M3Text(item.todo.text, fontSize = 12.sp, color = wtc(dark), maxLines = 1, modifier = Modifier.weight(1f))
                        M3Text(item.dueLabel, fontSize = 10.sp, color = if (item.overdue) danger else wsc(dark))
                    }
                }
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        M3Text("没有待办，好好休息 ☕", fontSize = 12.sp, color = wsc(dark))
                    }
                }
            }
            WidgetPresentation.Shape.MEDIUM -> Column(Modifier.fillMaxSize()) {
                M3Text("待办", fontSize = 10.sp, color = wsc(dark))
                Row(verticalAlignment = Alignment.Bottom) {
                    M3Text("${items.size}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = wtc(dark))
                    M3Text(" 项", fontSize = 10.sp, color = wsc(dark))
                }
                if (groups.overdue.isNotEmpty()) M3Text("${groups.overdue.size} 项逾期", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = danger)
                Spacer(Modifier.weight(1f))
                items.firstOrNull()?.let { n ->
                    M3Text(n.todo.text, fontSize = 10.sp, color = wtc(dark), maxLines = 1)
                    M3Text(n.dueLabel, fontSize = 9.sp, color = if (n.overdue) danger else wsc(dark))
                } ?: M3Text("暂无待办", fontSize = 10.sp, color = wsc(dark))
            }
            WidgetPresentation.Shape.TALL -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                M3Text("待办", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = wtc(dark))
                Spacer(Modifier.weight(1f))
                M3Text("${items.size}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = wtc(dark))
                M3Text("条待办", fontSize = 9.sp, color = wsc(dark))
                Spacer(Modifier.weight(1f))
                if (groups.overdue.isNotEmpty()) M3Text("${groups.overdue.size} 逾期", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = danger)
                else M3Text(if (groups.today.size > 0) "今日 ${groups.today.size} 项" else "无逾期", fontSize = 9.sp, color = wsc(dark))
            }
            else -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                MiniBadge("${items.size}", dark, 30.dp)
                Spacer(Modifier.width(8.dp))
                items.firstOrNull()?.let { n ->
                    M3Text(n.todo.text, fontSize = 12.sp, color = wtc(dark), maxLines = 1, modifier = Modifier.weight(1f))
                    M3Text(n.dueLabel, fontSize = 10.sp, color = if (n.overdue) danger else wsc(dark))
                } ?: M3Text("暂无待办", fontSize = 12.sp, color = wsc(dark))
            }
        }
    }
}
