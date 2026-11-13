// Adapted from NexioSchedule 28a99a3 效果 (glass effect) page (AGPL-3.0 upstream,
// used in this closed-source project by arrangement with the author — see NOTICE.md).
// Six card-material controls only; theme/wallpaper rows stay in our own settings.
package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.LocalDs

/** 课表卡片玻璃材质调参（着色/不透明度/模糊/折射/边缘厚度/高光描边），改动即时生效并持久化。 */
@Composable
fun GlassEffectScreen(repo: Repository, onBack: () -> Unit) {
    val ds = LocalDs.current
    val store = repo.store
    // 普通 prefs 不触发重组：滑杆位置用本地镜像，写回即生效
    var tint by remember { mutableStateOf(store.glassTint) }
    var opacity by remember { mutableStateOf(store.glassOpacity) }
    var blur by remember { mutableStateOf(store.glassBlurPx) }
    var refraction by remember { mutableStateOf(store.glassRefraction) }
    var edge by remember { mutableStateOf(store.glassEdge) }
    var highlight by remember { mutableStateOf(store.glassHighlightEdge) }

    SettingSubPage(title = "课表效果", subtitle = "卡片玻璃材质 · 桌面同款调参", onBack = onBack) {
        SectionHeader("卡片材质")
        SettingGroup {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                EffectSliderRow(
                    label = "卡片着色程度",
                    value = "${(tint * 100).toInt()} %",
                    valueRange = 0f..0.5f,
                    current = tint,
                    onChange = { tint = it; store.glassTint = it },
                )
                Spacer(Modifier.height(16.dp))
                EffectSliderRow(
                    label = "卡片不透明度",
                    value = "${(opacity * 100).toInt()} %",
                    valueRange = 0f..1f,
                    current = opacity,
                    onChange = { opacity = it; store.glassOpacity = it },
                )
                Spacer(Modifier.height(16.dp))
                EffectSliderRow(
                    label = "卡片模糊",
                    value = "$blur px",
                    valueRange = 0f..20f,
                    current = blur.toFloat(),
                    onChange = { blur = it.toInt(); store.glassBlurPx = it.toInt() },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionHeader("卡片玻璃")
        SettingGroup {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                EffectSliderRow(
                    label = "卡片折射",
                    value = if (refraction <= 0f) "关闭" else "${(refraction * 100).toInt()} %",
                    valueRange = 0f..1f,
                    current = refraction,
                    onChange = { refraction = it; store.glassRefraction = it },
                )
            }
            SettingOption(
                title = "边缘厚度",
                detail = when (edge) { 0 -> "关闭"; 2 -> "细"; 3 -> "厚"; else -> "默认" },
            )
            SettingEditor {
                SegmentedChoice(
                    options = listOf(0 to "关闭", 2 to "细", 1 to "默认", 3 to "厚"),
                    selected = edge,
                    onSelect = { edge = it; store.glassEdge = it },
                )
            }
            SettingOption(
                title = "高光描边",
                subtitle = "折射开启时的边缘高亮（需先开卡片折射）",
                switch = highlight,
                onSwitch = { highlight = it; store.glassHighlightEdge = it },
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "材质只在「课表壁纸」开启时可见；无壁纸时卡片使用页底垫色。数值即时生效并保存在本机。",
            fontSize = 12.sp, lineHeight = 17.sp, color = ds.sub,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Spacer(Modifier.height(96.dp))
    }
}

/** Nexio 效果页的行式滑杆：标签左、数值右、滑杆占满下一行。 */
@Composable
private fun EffectSliderRow(
    label: String,
    value: String,
    valueRange: ClosedFloatingPointRange<Float>,
    current: Float,
    onChange: (Float) -> Unit,
) {
    val ds = LocalDs.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = ds.text, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, color = ds.sub)
    }
    Slider(
        value = current,
        onValueChange = onChange,
        valueRange = valueRange,
        colors = SliderDefaults.colors(thumbColor = ds.accent, activeTrackColor = ds.accent),
        modifier = Modifier.fillMaxWidth(),
    )
}
