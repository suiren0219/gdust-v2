package com.menglimi.dormpower.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.menglimi.dormpower.BuildConfig
import com.menglimi.dormpower.power.PowerBackgroundStatus
import com.menglimi.dormpower.power.PowerLedgerState
import com.menglimi.dormpower.power.PowerUiState
import com.menglimi.dormpower.power.powerValue
import com.menglimi.ksu.ui.*

@Composable
internal fun PowerSettingsScreen(
    appearance: Appearance, onAppearance: (Appearance) -> Unit, state: PowerUiState, actions: PowerActions,
    notificationsEnabled: Boolean, onNotifications: () -> Unit, onTestNotification: () -> Unit,
    notificationMessage: String?, schedulingError: String?, onRetryScheduling: () -> Unit, preview: Boolean,
    backgroundStatus: PowerBackgroundStatus = PowerBackgroundStatus(),
    bootQueryEnabled: Boolean = false, onBootQueryEnabled: (Boolean) -> Unit = {},
) {
    if (state.localError != null) {
        LocalPowerDataNotice(state, actions)
        return
    }
    var unbindDialog by rememberSaveable { mutableStateOf(false) }
    var thresholdDialog by rememberSaveable { mutableStateOf(false) }
    var priceDialog by rememberSaveable { mutableStateOf(false) }
    val config = state.config
    val ledger = state.ledger ?: PowerLedgerState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 124.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("设置", if (preview) "合成预览 · 设置仅在当前预览内生效" else "提醒、自启动与外观") }
        state.settingsError?.let { item { MessageCard(it, actions.clearSettingsError) } }
        item { SectionTitle("电价与记账") }
        item {
            KCard(Modifier.fillMaxWidth()) {
                SettingRow("电费单价", "${ledger.unitPrice} 元/度 · 默认 0.6 元/度", Glyph.BOLT) {
                    TextButton(onClick = { priceDialog = true }) { Text("修改") }
                }
                Text("新电价用于之后的采样与充值识别；旧账保留当时电价。余额折算和服务端用量按当前电价估算，实际扣费以服务为准。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.ledgerError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        item { SectionTitle("宿舍监控") }
        item { BootQueryCard(bootQueryEnabled, preview, onBootQueryEnabled) }
        item {
            KCard(Modifier.fillMaxWidth()) {
                Text("后台启动记录", style = MaterialTheme.typography.titleMedium)
                Text(if (preview) "合成预览" else backgroundStatus.lastOutcome.description,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
                Text("最近一次：${displayTime(backgroundStatus.lastRunAt)}\n" +
                    "最近成功：${displayTime(backgroundStatus.lastSuccessAt)}",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
                if (backgroundStatus.durationMs > 0) Text("上次查询处理 ${backgroundStatus.durationMs} ms",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                Text(backgroundStatus.outcomeExplanation.ifBlank { "开机查询开关默认关闭，开启后只在下次开机或更新时查询一次。" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp))
                TextButton(onClick = onRetryScheduling, enabled = !preview,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("检查开机查询设置") }
            }
        }
        schedulingError?.let { message -> item { MessageCard(message) } }
        item {
            KCard(Modifier.fillMaxWidth()) {
                SettingRow("电量预警通知", if (notificationsEnabled) "通知权限与预警渠道已启用" else "通知尚未开启，查询与历史仍可使用", Glyph.BELL) {
                    TextButton(onClick = onNotifications) { Text(if (notificationsEnabled) "管理" else "开启") }
                    TextButton(enabled = notificationsEnabled, onClick = onTestNotification) { Text("测试") }
                }
                Text("通知被拒绝或渠道被关闭时，不记为已发送，也不启动预警冷却。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                notificationMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp)) }
            }
        }
        item { SectionTitle("预警") }
        item {
            KCard(Modifier.fillMaxWidth()) {
                SettingRow("预警阈值", "电量 ≤ ${powerValue(config.effectiveLowThreshold)} 度\n掉电 ≥ ${powerValue(config.effectiveDropRateThreshold)} 度/小时") {
                    TextButton(onClick = { thresholdDialog = true }) { Text("调整") }
                }
                Text("仅开机自动查询或手动刷新时检查预警；设备长时间不开机，不会自动巡检。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { SectionTitle("外观") }
        item { AppearanceCard(appearance, onAppearance) }
        item { SectionTitle("桌面名称与图标") }
        item { DesktopAppearanceCard(preview) }
        item { SectionTitle("绑定与数据") }
        item {
            KCard(Modifier.fillMaxWidth()) {
                SettingRow("宿舍绑定", state.roomName.ifBlank { "尚未绑定宿舍" }, Glyph.BOLT) {
                    TextButton(onClick = { unbindDialog = true }, enabled = config.binding != null) { Text("解绑") }
                }
                Text("电费账号、房间绑定、读数、历史和电费账本只保存在本应用的加密存储中。换绑或解绑会清除旧宿舍缓存与历史。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp)) }
            }
        }
        item { SectionTitle("关于宿舍电量") }
        item { AboutProjectCard(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, "dorm-power", preview) }
        item {
            KCard(Modifier.fillMaxWidth()) {
                Text("宿舍电量", style = MaterialTheme.typography.titleMedium)
                Text("独立、非官方的宿舍电量客户端。与学校及完美校园无官方关联；电量与服务端统计由设备直接请求电费服务。",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
                Text("不索要账号密码，不接入学校登录。估算和本机采样仅供参考，充值与当前余额请以电费服务为准。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp))
                Text("AGPL-3.0 · 自由软件 · 不提供担保", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 16.dp))
                Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
    if (priceDialog) {
        var price by remember { mutableStateOf(state.ledger?.unitPrice ?: "0.6") }
        var submitted by remember { mutableStateOf(false) }
        val initialSavedVersion = remember { state.ledgerSavedVersion }
        val editorRevision = remember { state.bindingRevision }
        LaunchedEffect(state.bindingRevision) { if (state.bindingRevision != editorRevision) priceDialog = false }
        LaunchedEffect(state.ledgerSavedVersion, state.ledgerError, submitted) {
            if (submitted && state.ledgerSavedVersion > initialSavedVersion) priceDialog = false
            else if (submitted && state.ledgerError != null && !state.ledgerBusy) submitted = false
        }
        PowerDialog("电费单价", { priceDialog = false }, dismissEnabled = !state.ledgerBusy, confirm = {
            Button(enabled = com.menglimi.dormpower.power.PowerLedger.price(price) != null && !state.ledgerBusy && !submitted,
                onClick = { submitted = true; actions.price(price, editorRevision) }) { Text("保存") }
        }) {
            OutlinedTextField(price, { price = it }, label = { Text("人民币 / 度") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Text("默认 0.6 元/度，支持最多四位小数。不会重算旧账。")
            state.ledgerError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (unbindDialog) AlertDialog(onDismissRequest = { unbindDialog = false }, title = { Text("解除宿舍绑定？") },
        text = { Text("将删除本机保存的宿舍绑定、读数、采样历史与预警冷却记录，并停止开机查询。此操作不能恢复历史。") },
        confirmButton = { TextButton(onClick = { unbindDialog = false; actions.unbind() }) { Text("确认解绑") } },
        dismissButton = { TextButton(onClick = { unbindDialog = false }) { Text("取消") } })
    if (thresholdDialog) ThresholdDialog(config.effectiveLowThreshold, config.effectiveDropRateThreshold,
        onDismiss = { thresholdDialog = false }, onSave = { low, drop -> actions.thresholds(low, drop); thresholdDialog = false })
}

@Composable
private fun ThresholdDialog(low: Double, drop: Double, onDismiss: () -> Unit, onSave: (Double, Double) -> Unit) {
    var lowText by rememberSaveable { mutableStateOf(powerValue(low)) }
    var dropText by rememberSaveable { mutableStateOf(powerValue(drop)) }
    val lowValue = lowText.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    val dropValue = dropText.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    PowerDialog("调整预警阈值", onDismiss, confirm = {
        Button(enabled = lowValue != null && dropValue != null, onClick = { onSave(lowValue!!, dropValue!!) }) { Text("保存") }
    }) {
        OutlinedTextField(lowText, { lowText = it }, label = { Text("低电量阈值（度）") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = lowValue == null, supportingText = { Text(if (lowValue == null) "请输入不小于 0 的数值" else "剩余电量不高于此值时提醒") })
        OutlinedTextField(dropText, { dropText = it }, label = { Text("掉电速率（度/小时）") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = dropValue == null, supportingText = { Text(if (dropValue == null) "请输入大于 0 的数值" else "至少间隔 15 分钟的有效采样用于判断") })
    }
}
