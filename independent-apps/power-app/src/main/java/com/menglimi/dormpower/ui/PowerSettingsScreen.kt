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
import com.menglimi.dormpower.power.PowerUiState
import com.menglimi.dormpower.power.powerValue
import com.menglimi.ksu.ui.*
import com.menglimi.power.PowerQueryPolicy
import com.menglimi.power.PowerQueryPreset

@Composable
internal fun PowerSettingsScreen(
    appearance: Appearance, onAppearance: (Appearance) -> Unit, state: PowerUiState, actions: PowerActions,
    notificationsEnabled: Boolean, onNotifications: () -> Unit, onTestNotification: () -> Unit,
    notificationMessage: String?,
    schedulingError: String?, onRetryScheduling: () -> Unit, preview: Boolean,
) {
    var unbindDialog by rememberSaveable { mutableStateOf(false) }
    var thresholdDialog by rememberSaveable { mutableStateOf(false) }
    var intervalDialog by rememberSaveable { mutableStateOf(false) }
    val config = state.config
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 124.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("设置", if (preview) "合成预览 · 设置仅在当前预览内生效" else "查询节奏、提醒和外观") }
        state.settingsError?.let { item { MessageCard(it, actions.clearSettingsError) } }
        item { SectionTitle("宿舍监控") }
        item {
            KCard(Modifier.fillMaxWidth()) {
                Text("查询节奏", style = MaterialTheme.typography.titleMedium)
                PowerQueryPreset.entries.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { preset ->
                            FilterChip(selected = config.preset == preset, onClick = { actions.preset(preset) },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp), label = { Text(preset.title) })
                        }
                    }
                }
                Text(config.preset.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
                if (config.preset == PowerQueryPreset.CUSTOM) {
                    SettingRow("自定间隔", "每 ${config.customIntervalMinutes} 分钟 · 支持 15–120 分钟", Glyph.CLOCK) {
                        TextButton(onClick = { intervalDialog = true }) { Text("修改") }
                    }
                }
                val plan = PowerQueryPolicy.plan(config.preset, config.customIntervalMinutes, state.samples,
                    System.currentTimeMillis(), config.effectiveLowThreshold, config.effectiveDropRateThreshold)
                Text(if (config.binding == null) "尚未绑定宿舍，后台任务未启动。"
                    else "${plan.intervalMinutes?.let { "当前约每 $it 分钟查询" } ?: "后台查询已关闭"} · ${plan.reason}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                SettingRow("预警阈值", "电量 ≤ ${powerValue(config.effectiveLowThreshold)} 度\n掉电 ≥ ${powerValue(config.effectiveDropRateThreshold)} 度/小时") {
                    TextButton(onClick = { thresholdDialog = true }) { Text("调整") }
                }
                Text("后台由系统调度，省电策略可能延后查询；同类预警至少间隔 12 小时。手动模式仍可在刷新时检查预警。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        schedulingError?.let { message -> item {
            MessageCard(message)
            TextButton(onClick = onRetryScheduling, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("重试后台任务") }
        } }
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
                Text("电费账号、房间绑定、读数和历史只保存在本应用的加密存储中。换绑或解绑会清除旧宿舍缓存与历史。",
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
    if (unbindDialog) AlertDialog(onDismissRequest = { unbindDialog = false }, title = { Text("解除宿舍绑定？") },
        text = { Text("将删除本机保存的宿舍绑定、读数、采样历史与预警冷却记录，并停止后台监控。此操作不能恢复历史。") },
        confirmButton = { TextButton(onClick = { unbindDialog = false; actions.unbind() }) { Text("确认解绑") } },
        dismissButton = { TextButton(onClick = { unbindDialog = false }) { Text("取消") } })
    if (thresholdDialog) ThresholdDialog(config.effectiveLowThreshold, config.effectiveDropRateThreshold,
        onDismiss = { thresholdDialog = false }, onSave = { low, drop -> actions.thresholds(low, drop); thresholdDialog = false })
    if (intervalDialog) IntervalDialog(config.customIntervalMinutes, { intervalDialog = false }) {
        actions.interval(it); intervalDialog = false
    }
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

@Composable
private fun IntervalDialog(minutes: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf(minutes.toString()) }
    val value = text.toIntOrNull()?.takeIf { it in 15..120 }
    PowerDialog("自定查询间隔", onDismiss, confirm = {
        Button(onClick = { value?.let(onSave) }, enabled = value != null) { Text("保存") }
    }) {
        OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("间隔 / 分钟") },
            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = value == null, supportingText = { Text("输入 15–120 分钟的整数，实际执行可能受系统延后。") })
    }
}
