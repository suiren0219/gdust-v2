package com.menglimi.ksu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppearanceCard(appearance: Appearance, onAppearance: (Appearance) -> Unit) {
    KCard(Modifier.fillMaxWidth()) {
        Text("显示模式", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(selected = appearance.mode == mode,
                    onClick = { onAppearance(appearance.copy(mode = mode)) }, label = { Text(mode.title) })
            }
        }
        SettingRow("动态配色", "跟随系统壁纸，关闭后使用绿色主题", Glyph.SETTINGS) {
            Switch(checked = appearance.dynamic, onCheckedChange = { onAppearance(appearance.copy(dynamic = it)) })
        }
        SettingRow("纯黑背景", "深色模式下使用 AMOLED 纯黑", Glyph.MOON) {
            Switch(checked = appearance.amoled, onCheckedChange = { onAppearance(appearance.copy(amoled = it)) })
        }
    }
}
