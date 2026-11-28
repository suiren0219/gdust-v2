package com.menglimi.kedian.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.menglimi.kedian.power.PowerUiState
import com.menglimi.power.PowerHistory
import com.menglimi.power.PowerQueryPolicy
import com.menglimi.power.PowerRoom
import com.menglimi.power.PowerSample
import java.time.ZoneId
import java.util.Locale

@Composable
fun PowerScreen(
    state: PowerUiState,
    onRefresh: () -> Unit,
    onQueryRooms: (String, Int) -> Unit,
    onBind: (String, Int, PowerRoom) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var bindingOpen by rememberSaveable { mutableStateOf(false) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    val bound = state.config.binding?.isValid == true
    val busy = state.busy || state.roomBusy
    val reading = state.reading.takeIf { bound }
    val odd = reading?.detail?.odd?.takeIf { it.isFinite() }
    val roomName = state.roomName.ifBlank { "已绑定宿舍" }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            PageHeader("宿舍电量", if (bound) roomName else "绑定宿舍，查看电量与用电记录",
                onRefresh = if (bound) onRefresh else null, loading = busy)
        }
        item {
            HeroCard(
                tags = listOf("监控 · ${if (bound) state.config.preset.title else "未启用"}",
                    if (reading != null) "本机缓存" else "暂无缓存"),
                title = if (bound) "${powerValue(odd)} 度" else "未绑定",
                subtitle = if (bound) "剩余电量 · $roomName\n数据时间：${displayTime(reading?.updatedAt ?: 0)}"
                    else "选择电费账号下的宿舍后，查看真实读数。",
                glyph = Glyph.BOLT, warning = odd != null && odd <= state.config.effectiveLowThreshold,
            )
        }
        state.error?.let { message -> item { MessageCard(message, onClearError) } }
        if (!bound) {
            item { EmptyCard("还没有绑定宿舍", "填写电费账号与学校代码，查询并选择房间。取消不会保存输入。", Glyph.LINK) }
        } else {
            item {
                val estimate = PowerQueryPolicy.estimate(state.samples, System.currentTimeMillis())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(powerValue(reading?.detail?.todayUse), "今日耗电 / 度 · 服务端")
                    StatCard(powerValue(estimate.daysLeft), "估算可用 / 天")
                }
                Text(estimate.state.explanation, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
            }
            item { SectionTitle("本机电量趋势", "有限采样") }
            item { PowerTrend(state.samples, busy) }
            item {
                TextButton(onClick = { historyOpen = true }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    KIcon(Glyph.CLOCK, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp)); Text("查看本机历史")
                }
            }
            item { SectionTitle("服务端近七日用电", "独立统计") }
            item {
                KCard(Modifier.fillMaxWidth()) {
                    Text("服务端返回的用电统计，不计入本机采样历史。", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val week = reading?.detail?.weekUse.orEmpty()
                    if (week.isEmpty()) Text("暂无服务端用电记录", modifier = Modifier.padding(top = 12.dp))
                    week.forEach { (label, use) ->
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Text(label.ifBlank { "未标注日期" }, Modifier.weight(1f))
                            Text("${powerValue(use)} 度")
                        }
                    }
                }
            }
        }
        item {
            Button(onClick = { bindingOpen = true }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (bound) "更换宿舍" else "绑定宿舍")
            }
        }
    }
    if (bindingOpen) PowerBindingDialog(state, onQueryRooms, onBind, onClearError) { bindingOpen = false }
    if (historyOpen && bound) PowerHistoryDialog(state.samples, state.config.zone) { historyOpen = false }
}

@Composable
private fun PowerTrend(samples: List<PowerSample>, busy: Boolean) {
    var days by rememberSaveable { mutableStateOf(7) }
    val now = System.currentTimeMillis()
    val points = samples.filter { it.time > 0 && it.time in (now - days * 86_400_000L)..now && it.odd.isFinite() }
        .sortedBy { it.time }.distinctBy { it.time }
    val colors = MaterialTheme.colorScheme
    KCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(1, 7, 30).forEach { range ->
                TextButton(onClick = { days = range }, enabled = !busy,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (days == range) colors.secondaryContainer else Color.Transparent)) {
                    Text("${range}天")
                }
            }
        }
        Text("最近 $days 天 · ${points.size} 条有效采样 · 剩余度数", style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
        if (points.size < 2) {
            Text("至少需要两个不同时间的样本才能绘制趋势；刷新后会逐步积累记录。")
        } else {
            val first = points.first()
            val last = points.last()
            val low = points.minOf { it.odd }
            val high = points.maxOf { it.odd }
            Text("${powerValue(low)} ～ ${powerValue(high)} 度", style = MaterialTheme.typography.labelMedium)
            Canvas(Modifier.fillMaxWidth().height(156.dp).padding(vertical = 12.dp).semantics {
                contentDescription = "本机剩余电量趋势，${points.size}个样本，从${powerValue(first.odd)}度到${powerValue(last.odd)}度"
            }) {
                val inset = 4.dp.toPx()
                fun position(sample: PowerSample): Offset {
                    val x = (sample.time - first.time).toDouble() / (last.time - first.time)
                    val y = if (high == low) 0.5 else (sample.odd - low) / (high - low)
                    return Offset(inset + (size.width - 2 * inset) * x.toFloat(),
                        inset + (size.height - 2 * inset) * (1 - y.toFloat()))
                }
                repeat(3) { index ->
                    val y = inset + (size.height - 2 * inset) * index / 2
                    drawLine(colors.outlineVariant, Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx())
                }
                val path = Path()
                points.forEachIndexed { index, sample ->
                    val point = position(sample)
                    if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                drawPath(path, colors.primary, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(colors.primary, 3.dp.toPx(), position(last))
            }
            val pattern = if (days > 1 || displayTime(first.time, "yyyy-MM-dd") != displayTime(last.time, "yyyy-MM-dd"))
                "MM-dd HH:mm" else "HH:mm"
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(displayTime(first.time, pattern), style = MaterialTheme.typography.labelSmall)
                Text(displayTime(last.time, pattern), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PowerBindingDialog(
    state: PowerUiState, onQueryRooms: (String, Int) -> Unit,
    onBind: (String, Int, PowerRoom) -> Unit, onClearError: () -> Unit, onDismiss: () -> Unit,
) {
    // 优先用当前绑定；解绑或换绑时用上次查询条件预填，省去重复输入账号和学校代码。
    var account by rememberSaveable {
        mutableStateOf(state.config.binding?.account?.takeIf { it.isNotBlank() } ?: state.lastQueryAccount)
    }
    var codeText by rememberSaveable {
        mutableStateOf((state.config.binding?.code?.takeIf { it > 0 } ?: state.lastQueryCode)
            .takeIf { it > 0 }?.toString().orEmpty())
    }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var requested by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var selected by remember { mutableStateOf<PowerRoom?>(null) }
    var submitted by remember { mutableStateOf<Triple<String, Int, PowerRoom>?>(null) }
    var bindingBeforeSubmit by remember { mutableStateOf(state.config.binding) }
    val saved = submitted?.let { (a, c, room) ->
        val binding = state.config.binding
        binding?.account == a && binding.code == c && binding.verify == room.verify &&
            (binding != bindingBeforeSubmit || state.roomQueryAccount.isBlank())
    } == true
    LaunchedEffect(saved, state.error) {
        if (saved) onDismiss() else if (state.error != null) submitted = null
    }
    val submitting = submitted != null && state.error == null
    val code = codeText.trim().takeIf { it.isNotEmpty() && it.all { char -> char in '0'..'9' } }
        ?.toIntOrNull()?.takeIf { it > 0 }
    val busy = state.busy || state.roomBusy || submitting
    val matches = requested?.let { (a, c) ->
        a == account.trim() && c == code && a == state.roomQueryAccount && c == state.roomQueryCode
    } == true
    val rooms = if (matches && !busy && state.error == null) state.rooms else emptyList()
    val choice = selected?.takeIf { it in rooms && it.verify.isNotBlank() }
    PowerDialog("绑定宿舍", onDismiss, dismissEnabled = !submitting, confirm = {
        Button(enabled = choice != null && !busy, modifier = Modifier.heightIn(min = 48.dp), onClick = {
            val room = choice ?: return@Button
            val validCode = code ?: return@Button
            if (matches && !busy) {
                bindingBeforeSubmit = state.config.binding
                submitted = Triple(account.trim(), validCode, room)
                onBind(account.trim(), validCode, room)
            }
        }) { Text(if (submitting) "正在保存…" else "确认绑定") }
    }) {
        Text("使用学校电费服务的账号和学校代码查询。选择房间并确认后才会保存。",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = account, onValueChange = {
            account = it; requested = null; selected = null
        }, label = { Text("电费账号") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            isError = attempted && account.isBlank(), supportingText = {
                if (attempted && account.isBlank()) Text("请输入电费账号")
            })
        OutlinedTextField(value = codeText, onValueChange = {
            codeText = it; requested = null; selected = null
        }, label = { Text("学校代码") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = attempted && code == null,
            supportingText = { Text(if (attempted && code == null) "请输入有效的正整数学校代码" else "请填写学校提供的代码") })
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = {
            attempted = true
            val validCode = code ?: return@Button
            if (account.isBlank()) return@Button
            selected = null
            requested = account.trim() to validCode
            onQueryRooms(account.trim(), validCode)
        }) { Text("查询宿舍") }
        if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp)); Text(if (state.roomBusy) "正在查询房间…" else "正在处理…")
        }
        state.error?.let { message ->
            MessageCard(message) { requested = null; selected = null; onClearError() }
        }
        if (matches && !busy && state.error == null) {
            if (rooms.isEmpty()) Text("该账号在当前学校代码下未查到宿舍。请确认账号无误，或先在完美校园 App 绑定宿舍后再试。")
            else Column(Modifier.selectableGroup()) {
                rooms.forEach { room ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(
                        selected = selected == room, enabled = room.verify.isNotBlank(), role = Role.RadioButton,
                        onClick = { selected = room }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected == room, onClick = null, enabled = room.verify.isNotBlank())
                        Text(room.name.ifBlank { "未命名宿舍" }, Modifier.weight(1f).padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PowerHistoryDialog(samples: List<PowerSample>, zone: ZoneId, onDismiss: () -> Unit) {
    val days = remember(samples, zone) { PowerHistory.group(samples, zone).values.sortedByDescending { it.date } }
    PowerDialog("本机历史 · 按天汇总", onDismiss) {
        Text("仅展示本机采样。日内变化为末次减首次；正值可能是充值或读数修正，单条样本不代表全天用电。",
            style = MaterialTheme.typography.bodySmall)
        if (days.isEmpty()) Text("暂无本机历史，查询成功后会记录真实读数。")
        days.forEach { day ->
            KCard(Modifier.fillMaxWidth()) {
                SectionTitle(day.date.toString(), "${day.samples.size} 条")
                Text("末次余额 ${powerValue(day.lastOdd)} 度")
                Text("日内变化 ${if (day.dayChange > 0) "+" else ""}${powerValue(day.dayChange)} 度",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun PowerDialog(
    title: String, onDismiss: () -> Unit, dismissEnabled: Boolean = true,
    confirm: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = { if (dismissEnabled) onDismiss() }, properties = DialogProperties(
        dismissOnBackPress = dismissEnabled, dismissOnClickOutside = dismissEnabled,
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
    )) {
        Box(Modifier.fillMaxWidth().safeDrawingPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 640.dp), shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss, enabled = dismissEnabled, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (confirm == null) "关闭" else "取消")
                        }
                        confirm?.invoke()
                    }
                }
            }
        }
    }
}

private fun powerValue(value: Double?): String = value?.takeIf { it.isFinite() }
    ?.let { String.format(Locale.CHINA, "%.2f", it) } ?: "—"
