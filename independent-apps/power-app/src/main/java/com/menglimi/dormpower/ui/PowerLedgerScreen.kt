package com.menglimi.dormpower.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.dormpower.power.*
import com.menglimi.ksu.ui.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

@Composable
internal fun PowerLedgerScreen(state: PowerUiState, actions: PowerActions, now: Long, preview: Boolean) {
    val today = PowerLedger.date(now, state.config.zone)
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selectedText by rememberSaveable { mutableStateOf(today.toString()) }
    var view by rememberSaveable { mutableIntStateOf(0) }
    var recharges by rememberSaveable { mutableStateOf(false) }
    var inYuan by rememberSaveable { mutableStateOf(true) }
    var editor by remember { mutableStateOf<PowerLedgerEntry?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<PowerLedgerEntry?>(null) }
    LaunchedEffect(state.bindingRevision) { editor = null; adding = false; removing = null }
    val month = YearMonth.parse(monthText)
    val selected = LocalDate.parse(selectedText)
    val entries = state.ledger.entries.filterNot { it.ignored }.filter { YearMonth.from(PowerLedger.date(it.time, state.config.zone)) == month }
    val days = state.ledger.days.filter { it.date.startsWith(monthText) }
    val observed = entries.filter { it.detected && it.paidYuan == null }
    val recorded = entries.filterNot { it.detected && it.paidYuan == null }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 124.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("电费账本", if (preview) "合成预览 · 不写入真实账本" else "月历、充值记录与室友记账") }
        item {
            KCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { monthText = month.minusMonths(1).toString(); selectedText = month.minusMonths(1).atDay(1).toString() }) { Text("上月") }
                    Text(monthText, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { monthText = month.plusMonths(1).toString(); selectedText = month.plusMonths(1).atDay(1).toString() }) { Text("下月") }
                }
                Text("已记充值 ¥${PowerLedger.money(recorded.fold(BigDecimal.ZERO) { sum, e -> sum + PowerLedger.amount(e) })}",
                    style = MaterialTheme.typography.headlineSmall)
                Text("疑似充值折算 ¥${PowerLedger.money(observed.fold(BigDecimal.ZERO) { sum, e -> sum + PowerLedger.amount(e) })} · ${observed.size} 笔待核实",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("采样用电约 ¥${PowerLedger.money(days.fold(BigDecimal.ZERO) { sum, d -> sum + d.yuan.toBigDecimal() })}",
                    modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
                Text("当前 ${state.ledger.unitPrice} 元/度 · 历史账目保留当时电价",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("月历", "明细", "按人").forEachIndexed { index, title ->
                    FilterChip(selected = view == index, onClick = { view = index }, label = { Text(title) })
                }
            }
        }
        if (view == 0) item {
            KCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!recharges, { recharges = false }, label = { Text("用电") })
                    FilterChip(recharges, { recharges = true }, label = { Text("充值") })
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { inYuan = !inYuan }) { Text(if (inYuan) "单位：元 ⇄" else "单位：度 ⇄") }
                }
                LedgerCalendar(month, selected, today, entries, days, state.config.zone, recharges, inYuan) { selectedText = it.toString() }
                Text(if (recharges) "● 已记充值 · ○ 疑似充值；金额含估算，实付见明细" else "● 有充值 · 每格为采样用电；空白不代表零消费", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            }
        }
        if (state.config.binding == null) item {
            EmptyCard("先绑定宿舍", "账本与当前宿舍独立保存；不会补造绑定前的充值。", Glyph.BOOK)
        } else item {
            Button(onClick = { editor = null; adding = true }, enabled = !state.ledgerBusy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("补记一笔充值") }
            Text("已有疑似充值请点开补全实付，勿再补记同一笔。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.ledgerError?.let { item { MessageCard(it) } }
        if (view == 2) {
            item { SectionTitle("本月按人汇总", "仅已记充值") }
            recorded.groupBy { it.payer.ifBlank { "未填写充值人" } }.forEach { (payer, records) -> item {
                KCard(Modifier.fillMaxWidth()) {
                    Text(payer, style = MaterialTheme.typography.titleMedium)
                    Text("¥${PowerLedger.money(records.fold(BigDecimal.ZERO) { sum, e -> sum + PowerLedger.amount(e) })} · ${records.size} 笔")
                }
            } }
            if (recorded.isEmpty()) item { EmptyCard("暂无已记充值", "填写实付金额与充值人后，会按人汇总。疑似充值不计入此处。", Glyph.BOOK) }
        } else {
            val visible = if (view == 0) entries.filter { PowerLedger.date(it.time, state.config.zone) == selected } else entries
            item { SectionTitle(if (view == 0) "$selected" else "本月充值明细", "${visible.size} 笔") }
            if (view == 0) days.find { it.date == selectedText }?.let { day -> item {
                Text("采样观察 ${powerValue(day.kwh)} 度 ≈ ¥${PowerLedger.money(day.yuan.toBigDecimal())}",
                    style = MaterialTheme.typography.bodyMedium)
            } }
            if (visible.isEmpty()) item { EmptyCard("暂无充值记录", "成功查询后会识别余额上涨，也可以手动补记实付。", Glyph.BOOK) }
            visible.sortedByDescending { it.time }.forEach { entry -> item(key = entry.id) {
                KCard(Modifier.fillMaxWidth(), onClick = { editor = entry; adding = false }) {
                    Text("${if (entry.detected && entry.paidYuan == null) "疑似充值 ≈" else "已记充值"} ¥${PowerLedger.money(PowerLedger.amount(entry))}",
                        style = MaterialTheme.typography.titleMedium)
                    Text("${PowerLedger.date(entry.time, state.config.zone)} · ${entry.payer.ifBlank { "充值人待补充" }}",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                    Text("${if (entry.detected) "余额净增" else "购电"} ${powerValue(entry.kwh)} 度 · ${entry.unitPrice} 元/度",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (entry.note.isNotBlank()) Text(entry.note, modifier = Modifier.padding(top = 8.dp))
                    if (entry.detected) Text("发现于两次采样之间，可能包含读数修正；净增无法还原期间用电或多次充值。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {
                        TextButton(onClick = { editor = entry; adding = false }) { Text("编辑 / 谁充的") }
                        TextButton(onClick = { removing = entry }) { Text(if (entry.detected) "忽略" else "删除") }
                    }
                }
            } }
        }
    }
    if (adding || editor != null) LedgerEntryDialog(editor, selected, state, actions) { adding = false; editor = null }
    removing?.let { entry -> AlertDialog(onDismissRequest = { removing = null }, title = { Text(if (entry.detected) "忽略这笔识别？" else "删除这笔记录？") },
        text = { Text("不会修改电量采样或其他账目。") }, confirmButton = {
            TextButton(onClick = { actions.removeEntry(entry.id, state.bindingRevision); removing = null }) { Text("确认") }
        }, dismissButton = { TextButton(onClick = { removing = null }) { Text("取消") } }) }
}

@Composable
private fun LedgerCalendar(month: YearMonth, selected: LocalDate, today: LocalDate, entries: List<PowerLedgerEntry>,
    days: List<PowerDayCost>, zone: java.time.ZoneId, recharges: Boolean, inYuan: Boolean, onDay: (LocalDate) -> Unit) {
    Row { listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center) } }
    val offset = month.atDay(1).dayOfWeek.value - 1
    val rows = (offset + month.lengthOfMonth() + 6) / 7
    repeat(rows) { week -> Row(Modifier.fillMaxWidth()) {
        repeat(7) { weekday ->
            val number = week * 7 + weekday - offset + 1
            val day = number.takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay)
            val dailyEntries = entries.filter { day != null && PowerLedger.date(it.time, zone) == day }
            val hasRecharge = dailyEntries.isNotEmpty()
            val pending = dailyEntries.any { it.detected && it.paidYuan == null }
            val cost = days.find { it.date == day?.toString() }
            val value = if (recharges) {
                if (dailyEntries.isEmpty()) "" else if (inYuan) "${if (pending) "≈" else ""}¥${PowerLedger.money(dailyEntries.fold(BigDecimal.ZERO) { sum, e -> sum + PowerLedger.amount(e) })}"
                else "${powerValue(dailyEntries.sumOf { it.kwh })}度"
            } else cost?.let { if (inYuan) "¥${PowerLedger.money(it.yuan.toBigDecimal())}" else "${powerValue(it.kwh)}度" }.orEmpty()
            Column(Modifier.weight(1f).heightIn(min = 48.dp)
                .background(if (day == selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                    RoundedCornerShape(12.dp))
                .then(if (day != null) Modifier.clickable { onDay(day) }.semantics {
                    contentDescription = "$day ${if (recharges) "充值" else "采样用电"} $value${if (pending) " 含待核实充值" else ""}"
                } else Modifier).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(day?.dayOfMonth?.toString().orEmpty(), color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Text(if (hasRecharge) if (pending) "○" else "●" else "", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, lineHeight = 12.sp)
                Text(value, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, modifier = Modifier.semantics { contentDescription = value })
            }
        }
    } }
}

@Composable
private fun LedgerEntryDialog(entry: PowerLedgerEntry?, date: LocalDate, state: PowerUiState, actions: PowerActions, close: () -> Unit) {
    var day by remember { mutableStateOf(entry?.let { PowerLedger.date(it.time, state.config.zone).toString() } ?: date.toString()) }
    var kwh by remember { mutableStateOf(entry?.kwh?.let(java.math.BigDecimal::valueOf)?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    var paid by remember { mutableStateOf(entry?.paidYuan.orEmpty()) }
    var payer by remember { mutableStateOf(entry?.payer.orEmpty()) }
    var note by remember { mutableStateOf(entry?.note.orEmpty()) }
    var submitted by remember { mutableStateOf(false) }
    val initialSavedVersion = remember { state.ledgerSavedVersion }
    val price = entry?.unitPrice ?: state.ledger.unitPrice
    val amount = PowerLedger.payment(paid)
    val units = if (entry?.detected == true) entry.kwh else kwh.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
        ?: amount?.takeIf { it > BigDecimal.ZERO }?.divide(PowerLedger.price(price), 8, java.math.RoundingMode.HALF_UP)?.toDouble()
    val editorRevision = remember { state.bindingRevision }
    val parsedDate = runCatching { LocalDate.parse(day) }.getOrNull()?.takeIf { !it.isAfter(LocalDate.now(state.config.zone)) && it.year >= 1971 }
    val valid = units != null && units > 0 && units <= 10_000_000 && parsedDate != null &&
        (kwh.isBlank() || kwh.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true || entry?.detected == true) &&
        (paid.isBlank() || amount != null) && payer.length <= 40 && note.length <= 500 && editorRevision == state.bindingRevision
    LaunchedEffect(state.ledgerSavedVersion, state.ledgerError, submitted) {
        if (submitted && state.ledgerSavedVersion > initialSavedVersion) close()
        else if (submitted && state.ledgerError != null && !state.ledgerBusy) submitted = false
    }
    PowerDialog(if (entry == null) "补记充值" else "谁充的电？", close, dismissEnabled = !state.ledgerBusy, confirm = {
        Button(enabled = valid && !state.ledgerBusy && !submitted, onClick = {
            val saved = (entry ?: PowerLedgerEntry(time = parsedDate!!.atStartOfDay(state.config.zone).toInstant().toEpochMilli(), kwh = units!!, unitPrice = price))
                .copy(time = if (entry?.detected == true) entry.time else parsedDate!!.atStartOfDay(state.config.zone).toInstant().toEpochMilli(),
                    kwh = units!!, paidYuan = paid.trim().takeIf { it.isNotEmpty() }, payer = payer.trim(), note = note.trim())
            submitted = true; actions.saveEntry(saved, editorRevision)
        }) { Text(if (state.ledgerBusy) "保存中…" else "保存") }
    }) {
        Text(if (entry?.detected == true) "余额净增 ${powerValue(entry.kwh)} 度 ≈ ¥${PowerLedger.yuan(entry.kwh, price)}。请按付款凭据补填实付，无法仅凭净增精确还原充值。"
            else "可填实付金额，或购电度数；只填金额时按 $price 元/度换算。")
        OutlinedTextField(day, { day = it }, enabled = entry?.detected != true, label = { Text("日期 YYYY-MM-DD") }, singleLine = true)
        if (entry?.detected != true) OutlinedTextField(kwh, { kwh = it }, label = { Text("购电度数（可选）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(paid, { paid = it }, label = { Text("实付金额 / 元（可选）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = paid.isNotBlank() && amount == null)
        OutlinedTextField(payer, { payer = it.take(40) }, label = { Text("谁充的电？") }, placeholder = { Text("填写姓名或称呼") }, singleLine = true)
        OutlinedTextField(note, { note = it.take(500) }, label = { Text("备注") }, placeholder = { Text("例如：本月宿舍共同电费") }, maxLines = 4)
        state.ledgerError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
