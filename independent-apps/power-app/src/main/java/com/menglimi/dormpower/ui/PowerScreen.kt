package com.menglimi.dormpower.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.menglimi.dormpower.power.PowerUiState
import com.menglimi.dormpower.power.ReadingStatus
import com.menglimi.dormpower.power.powerValue
import com.menglimi.dormpower.power.readingStatus
import com.menglimi.dormpower.power.schoolCode
import com.menglimi.ksu.ui.*
import com.menglimi.power.PowerQueryPolicy
import com.menglimi.power.PowerRoom

@Composable
internal fun PowerScreen(state: PowerUiState, actions: PowerActions, now: Long, preview: Boolean) {
    var bindingOpen by rememberSaveable { mutableStateOf(false) }
    val bound = state.config.binding?.isValid == true
    val reading = state.reading.takeIf { bound }
    val status = readingStatus(state, now)
    val stale = status == ReadingStatus.STALE || status == ReadingStatus.ERROR
    val low = reading?.detail?.odd?.let { it <= state.config.effectiveLowThreshold } == true
    val roomName = state.roomName.ifBlank { "已绑定宿舍" }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 124.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("宿舍电量", if (preview) "合成预览 · 所有数据与操作仅在内存中" else "独立查询 · 关心宿舍的每一度电",
            onRefresh = if (bound) actions.refresh else null, loading = state.busy || state.localLoading) }
        item {
            HeroCard(
                tags = listOf(status.title, if (preview) "合成预览" else if (bound) state.config.preset.title else "无需密码"),
                title = if (bound) "${powerValue(reading?.detail?.odd)} 度" else "未绑定宿舍",
                subtitle = if (bound) "剩余电量 · $roomName\n数据时间：${displayTime(reading?.updatedAt ?: 0)}" +
                    if (stale && reading != null) "\n上次成功读数已过期，请刷新确认当前余额。" else ""
                    else "填写电费账号与学校代码，查询并选择房间后即可查看。",
                glyph = Glyph.BOLT, warning = stale || low,
            ) {
                if (bound && !state.busy) TextButton(onClick = actions.refresh, modifier = Modifier.heightIn(min = 48.dp)) {
                    KIcon(Glyph.REFRESH, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp)); Text("刷新电量")
                }
            }
        }
        if (state.busy) item {
            KCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("正在获取电量…", Modifier.weight(1f).padding(horizontal = 12.dp))
                    TextButton(onClick = actions.cancelRefresh) { Text("取消") }
                }
                Text("历史与设置仍可使用。", style = MaterialTheme.typography.bodySmall)
            }
        }
        state.error?.let { message -> item {
            MessageCard(message, actions.clearError)
            TextButton(onClick = if (bound) actions.refresh else actions.reload,
                enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("重试") }
        } }
        state.message?.let { message -> item {
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        if (!bound) {
            item { EmptyCard("你的宿舍，独立查看", "不需要学校门户登录，不索要密码。仅在确认选择后保存绑定；取消查询不会绑定宿舍。", Glyph.LINK) }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(powerValue(reading?.detail?.todayUse), "今日用电 / 度 · 服务端")
                    StatCard(powerValue(reading?.detail?.monthUse), "上月用电 / 度 · 服务端")
                }
                if (stale) Text("以上为上次成功返回的统计，尚未验证最新数据。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp))
            }
            item { SectionTitle("还能用多久", "本机样本估算") }
            item {
                val estimate = PowerQueryPolicy.estimate(state.samples, now)
                KCard(Modifier.fillMaxWidth()) {
                    Text("${powerValue(estimate.daysLeft)} 天", style = MaterialTheme.typography.headlineMedium)
                    Text("估算日均 ${powerValue(estimate.dailyUse)} 度", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp))
                    Text(estimate.state.explanation, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                    if (stale) Text("当前缓存已过期，估算仅供参考。", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
            item { SectionTitle("近七日用电", "服务端统计") }
            item {
                KCard(Modifier.fillMaxWidth()) {
                    Text("直接展示服务端返回值，不用余额差冒充耗电。", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val week = reading?.detail?.weekUse.orEmpty()
                    if (week.isEmpty()) Text("— · 服务端暂无近七日统计", modifier = Modifier.padding(top = 16.dp))
                    week.forEach { (label, use) ->
                        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(label.ifBlank { "未标注日期" }, Modifier.weight(1f))
                            Text("${powerValue(use)} 度")
                        }
                    }
                    Text("累计购电 ${powerValue(reading?.detail?.sumBuy)} 度", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 18.dp))
                }
            }
        }
        item {
            Button(onClick = { actions.cancelRooms(); bindingOpen = true }, enabled = !state.localLoading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (bound) "更换宿舍" else "绑定宿舍") }
        }
    }
    if (bindingOpen) PowerBindingDialog(state, actions) { bindingOpen = false }
}

@Composable
private fun PowerBindingDialog(state: PowerUiState, actions: PowerActions, close: () -> Unit) {
    var account by rememberSaveable { mutableStateOf(state.config.binding?.account.orEmpty()) }
    var codeText by rememberSaveable { mutableStateOf(state.config.binding?.code?.toString() ?: "1000145") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var requested by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var selected by remember { mutableStateOf<PowerRoom?>(null) }
    var submittedRevision by remember { mutableStateOf<Long?>(null) }
    val dismiss = { actions.cancelRooms(); close() }
    val cancelQuery by rememberUpdatedState(actions.cancelRooms)
    DisposableEffect(Unit) { onDispose { cancelQuery() } }
    LaunchedEffect(state.bindingRevision, state.roomError) {
        if (submittedRevision != null && state.bindingRevision != submittedRevision) close()
        else if (state.roomError != null) submittedRevision = null
    }
    val code = schoolCode(codeText)
    val matches = requested?.let { (a, c) -> a == account.trim() && c == code &&
        a == state.roomQueryAccount && c == state.roomQueryCode } == true
    val rooms = if (matches && !state.roomBusy && state.roomError == null) state.rooms else emptyList()
    val choice = selected?.takeIf { it in rooms }
    val saving = state.bindingBusy || submittedRevision != null
    val inputChanged: () -> Unit = { requested = null; selected = null; actions.cancelRooms() }
    PowerDialog("绑定宿舍", dismiss, dismissEnabled = !saving, confirm = {
        Button(enabled = choice != null && !state.roomBusy && !saving, onClick = {
            val room = choice ?: return@Button
            val validCode = code ?: return@Button
            submittedRevision = state.bindingRevision
            actions.bind(account.trim(), validCode, room)
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (saving) "正在保存…" else "确认绑定") }
    }) {
        Text("输入电费服务账号，不需要密码。查询不会保存绑定，请明确选择房间后再确认。",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(account, { account = it; inputChanged() }, label = { Text("电费账号") }, singleLine = true,
            enabled = !saving, modifier = Modifier.fillMaxWidth(), isError = attempted && account.isBlank(),
            supportingText = { if (attempted && account.isBlank()) Text("请输入电费账号") })
        OutlinedTextField(codeText, { codeText = it; inputChanged() }, label = { Text("学校代码（customerCode）") },
            singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = attempted && code == null,
            supportingText = { Text(if (attempted && code == null) "请输入有效的正整数学校代码"
                else "默认 1000145 为广科代码，可按你的学校修改。") })
        Button(enabled = !state.roomBusy && !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = {
            attempted = true
            val validCode = code ?: return@Button
            if (account.isBlank()) return@Button
            selected = null
            requested = account.trim() to validCode
            actions.queryRooms(account.trim(), validCode)
        }) { Text("查询房间") }
        if (state.roomBusy) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Text("正在查询房间…", Modifier.weight(1f).padding(start = 12.dp))
            TextButton(onClick = { requested = null; selected = null; actions.cancelRooms() }) { Text("取消查询") }
        }
        state.roomError?.let { MessageCard(it) }
        if (matches && !state.roomBusy && state.roomError == null) {
            if (rooms.isEmpty()) Text("未查到房间，请检查输入后重试。")
            else Column(Modifier.selectableGroup()) {
                Text("选择你的房间（${rooms.size} 个结果）", style = MaterialTheme.typography.titleSmall)
                rooms.forEach { room ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = selected == room,
                        enabled = !saving, role = Role.RadioButton, onClick = { selected = room }),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected == room, onClick = null, enabled = !saving)
                        Text(room.name, Modifier.weight(1f).padding(vertical = 8.dp))
                    }
                }
                if (state.config.binding != null) Text("确认更换后将清除旧宿舍的缓存、历史与预警冷却记录。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun PowerDialog(title: String, onDismiss: () -> Unit, dismissEnabled: Boolean = true,
    confirm: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = { if (dismissEnabled) onDismiss() }, properties = DialogProperties(
        dismissOnBackPress = dismissEnabled, dismissOnClickOutside = dismissEnabled,
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
    )) {
        Box(Modifier.fillMaxWidth().safeDrawingPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 680.dp),
                shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
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
