package com.menglimi.dormpower.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.menglimi.ksu.ui.Glyph
import com.menglimi.ksu.ui.KCard
import com.menglimi.ksu.ui.SettingRow

@Composable
internal fun BootQueryCard(
    enabled: Boolean,
    preview: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var autoStartFeedback by remember { mutableStateOf<String?>(null) }
    val manufacturer = remember(context) { android.os.Build.MANUFACTURER.orEmpty() }
    KCard(Modifier.fillMaxWidth()) {
        Text("开机后查询一次", style = MaterialTheme.typography.titleMedium)
        SettingRow("开机自动查询", when {
            preview -> "合成预览，不更改系统设置"
            enabled -> "已开启：下次开机或覆盖更新后查询一次，完成后退出"
            else -> "已关闭：不会在开机时查询"
        }, Glyph.BOLT) {
            Switch(checked = if (preview) false else enabled, enabled = !preview,
                onCheckedChange = { if (!preview) onEnabledChange(it) })
        }
        val autoStartLabel = when (manufacturer.lowercase(java.util.Locale.ROOT)) {
            "xiaomi", "redmi", "poco" -> "小米/红米/POCO：允许自启动"
            "huawei", "honor" -> "华为/HONOR：允许开机启动"
            "oppo", "realme", "oneplus" -> "OPPO/realme/OnePlus：允许开机启动"
            "vivo", "iqoo" -> "vivo/iQOO：允许后台自启动"
            "samsung" -> "Samsung：检查电池后台限制"
            else -> "如果系统提供自启动管理，请允许本应用开机启动"
        }
        SettingRow("厂商自启动", "$autoStartLabel；应用无法读取该开关是否已开启。", Glyph.CLOCK) {
            TextButton(enabled = !preview, onClick = {
                autoStartFeedback = if (com.menglimi.dormpower.power.PowerBackgroundAccess.openAutoStart(context))
                    "已打开系统设置。请手动确认允许自启动；返回本页不会把它误判为已授权。"
                else "无法打开厂商页面，请在系统的应用自启动设置中手动开启。"
            }) { Text("去设置") }
        }
        autoStartFeedback?.let { Text(it, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
        Text("开机/覆盖更新广播只排入一个联网单次任务，查询结束后退出；不常驻、不按时间间隔轮询。未连接网络时系统可能延迟该任务。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp))
        if (enabled) Text("提示：应用“强行停止”后，Android 会屏蔽开机广播；需手动打开应用一次，后续开机查询才会恢复。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp))
    }
}
