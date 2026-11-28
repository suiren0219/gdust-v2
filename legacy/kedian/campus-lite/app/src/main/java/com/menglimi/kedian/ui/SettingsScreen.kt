package com.menglimi.kedian.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.kedian.BuildConfig
import com.menglimi.kedian.learning.LearningUiState
import com.menglimi.kedian.power.PowerUiState
import com.menglimi.power.PowerQueryPreset

@Composable
fun SettingsScreen(
    appearance: Appearance,
    learning: LearningUiState,
    power: PowerUiState,
    notificationsEnabled: Boolean,
    onAppearance: (Appearance) -> Unit,
    onNotifications: () -> Unit,
    onPreset: (PowerQueryPreset) -> Unit,
    onInterval: (Int) -> Unit,
    onThresholds: (Double, Double) -> Unit,
    onDisconnect: () -> Unit,
    onUnbind: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    var thresholdDialog by rememberSaveable { mutableStateOf(false) }
    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("设置", "按你的习惯，刚刚好") }
        item { SectionTitle("外观") }
        item {
            KCard(Modifier.fillMaxWidth()) {
                Text("显示模式", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(selected = appearance.mode == mode, onClick = { onAppearance(appearance.copy(mode = mode)) },
                            label = { Text(mode.title, fontSize = 12.sp) })
                    }
                }
                SettingRow("动态配色", "跟随系统壁纸的色彩", Glyph.SETTINGS) {
                    Switch(checked = appearance.dynamic, onCheckedChange = { onAppearance(appearance.copy(dynamic = it)) })
                }
                SettingRow("纯黑背景", "深色模式下使用 AMOLED 纯黑", Glyph.MOON) {
                    Switch(checked = appearance.amoled, onCheckedChange = { onAppearance(appearance.copy(amoled = it)) })
                }
            }
        }
        item { SectionTitle("宿舍监控") }
        item {
            KCard(Modifier.fillMaxWidth()) {
                SettingRow("电量预警通知", if (notificationsEnabled) "已允许通知" else "允许后可接收低电量和掉电提醒", Glyph.BELL) {
                    TextButton(onClick = onNotifications) { Text(if (notificationsEnabled) "管理" else "开启") }
                }
                Text("查询节奏", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                Column {
                    PowerQueryPreset.entries.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { preset ->
                                FilterChip(selected = power.config.preset == preset, onClick = { onPreset(preset) },
                                    label = { Text(preset.title) })
                            }
                        }
                    }
                }
                Text(when (power.config.preset) {
                    PowerQueryPreset.MANUAL -> "仅手动查询，不运行后台采样；查询时仍检查低电量和掉电预警。"
                    PowerQueryPreset.BALANCED -> "通常每 120 分钟查询；电量偏低、估算不足 2 天或近期掉电较快时改为 60 分钟。"
                    PowerQueryPreset.LOW_POWER -> "通常每 60 分钟查询；电量偏低、估算不足 2 天或近期掉电较快时改为 30 分钟。"
                    PowerQueryPreset.CUSTOM -> "按所选间隔查询，保留低电量和掉电预警。"
                }, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp, lineHeight = 19.sp)
                if (power.config.preset == PowerQueryPreset.CUSTOM) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15, 30, 60, 120).forEach { minutes ->
                            FilterChip(selected = power.config.customIntervalMinutes == minutes, onClick = { onInterval(minutes) },
                                label = { Text("${minutes}分", fontSize = 12.sp) })
                        }
                    }
                }
                SettingRow("预警阈值", "低于 ${number(power.config.lowThreshold)} 度 · 每小时掉电 ${number(power.config.dropRateThreshold)} 度") {
                    TextButton(onClick = { thresholdDialog = true }) { Text("调整") }
                }
                Text("后台更新可能受系统省电策略延后；同类预警间隔至少 12 小时。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 19.sp)
            }
        }
        item { SectionTitle("连接与数据") }
        item {
            KCard(Modifier.fillMaxWidth()) {
                SettingRow("学习通", if (learning.connected) "已连接 · 最近更新 ${displayTime(learning.updatedAt)}" else "尚未连接", Glyph.BOOK) {
                    TextButton(onClick = { confirm = "learning" }, enabled = learning.connected || learning.updatedAt > 0) { Text("退出") }
                }
                SettingRow("宿舍绑定", power.roomName.ifBlank { "尚未绑定" }, Glyph.BOLT) {
                    TextButton(onClick = { confirm = "power" }, enabled = power.config.binding != null) { Text("解绑") }
                }
            }
        }
        item { SectionTitle("关于课电") }
        item {
            KCard(Modifier.fillMaxWidth()) {
                Text("学习通作业 · 宿舍电量", style = MaterialTheme.typography.titleMedium)
                Text("只关心作业和电量。数据由本机直接读取服务，账号密码不保存到应用。",
                    fontSize = 13.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, bottom = 14.dp))
                Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (thresholdDialog) ThresholdDialog(power.config.lowThreshold, power.config.dropRateThreshold,
        onDismiss = { thresholdDialog = false }, onSave = { low, drop -> onThresholds(low, drop); thresholdDialog = false })
    confirm?.let { type ->
        AlertDialog(onDismissRequest = { confirm = null },
            title = { Text(if (type == "learning") "退出学习通？" else "解除宿舍绑定？") },
            text = { Text(if (type == "learning") "将清除本机学习通登录会话和作业缓存。宿舍电量不受影响。"
                else "将清除此宿舍的本机采样记录，并停止后台电量监控。") },
            confirmButton = { TextButton(onClick = { confirm = null; if (type == "learning") onDisconnect() else onUnbind() }) { Text("确认") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("取消") } })
    }
}

private fun number(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

@Composable
private fun ThresholdDialog(low: Double, drop: Double, onDismiss: () -> Unit, onSave: (Double, Double) -> Unit) {
    var lowText by rememberSaveable { mutableStateOf(number(low)) }
    var dropText by rememberSaveable { mutableStateOf(number(drop)) }
    val lowValue = lowText.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    val dropValue = dropText.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("调整预警阈值") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(lowText, { lowText = it }, singleLine = true, label = { Text("低电量阈值（度）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = lowValue == null,
                supportingText = { if (lowValue == null) Text("请输入不小于 0 的数值") })
            OutlinedTextField(dropText, { dropText = it }, singleLine = true, label = { Text("掉电速率（度/小时）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = dropValue == null,
                supportingText = { if (dropValue == null) Text("请输入大于 0 的数值") })
        }
    }, confirmButton = { Button(enabled = lowValue != null && dropValue != null, onClick = { onSave(lowValue!!, dropValue!!) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
