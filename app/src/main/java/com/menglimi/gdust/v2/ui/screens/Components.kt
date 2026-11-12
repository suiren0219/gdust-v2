package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.ui.LocalDs
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward

/** Shared page chrome adapted from Nexio's document and preference pages. */
@Composable
fun PageTitle(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    val ds = LocalDs.current
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        if (onBack != null) {
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.CenterStart,
            ) { Icon(MiuixIcons.Back, "返回", Modifier.size(26.dp), tint = ds.text) }
            Spacer(Modifier.height(8.dp))
        }
        Text(title, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = ds.text)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, fontSize = 14.sp, lineHeight = 20.sp, color = ds.sub, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = LocalDs.current.sub,
        modifier = modifier.padding(start = 12.dp, bottom = 10.dp, top = 16.dp))
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    detail: String? = null,
    danger: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Card(Modifier.fillMaxWidth()) {
        PreferenceContent(title, subtitle, detail, danger, onClick, trailing)
    }
}

@Composable
fun SettingGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), cornerRadius = 20.dp, content = content)
    Spacer(Modifier.height(12.dp))
}

@Composable
fun SettingOption(
    title: String,
    subtitle: String? = null,
    detail: String? = null,
    danger: Boolean = false,
    switch: Boolean? = null,
    onSwitch: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    PreferenceContent(
        title, subtitle, detail, danger,
        onClick = onClick ?: if (switch != null && onSwitch != null) ({ onSwitch(!switch) }) else null,
        trailing = if (switch != null && onSwitch != null) ({ Switch(switch, onSwitch) }) else null,
    )
}

@Composable
private fun PreferenceContent(
    title: String,
    subtitle: String?,
    detail: String?,
    danger: Boolean,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)?,
) {
    val ds = LocalDs.current
    BasicComponent(
        onClick = onClick,
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        endActions = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (detail != null) Text(detail, color = ds.sub, fontSize = 13.sp,
                    textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 128.dp))
                if (trailing != null) trailing()
                else if (onClick != null) Icon(MiuixIcons.ChevronForward, null, Modifier.size(18.dp), tint = ds.sub)
            }
        },
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (danger) ds.danger else ds.text)
        if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 12.sp, lineHeight = 17.sp,
            color = ds.sub, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun SettingDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(0.5.dp)
        .background(LocalDs.current.cardStroke))
}

@Composable
fun SettingEditor(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
fun <T> SegmentedChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ds = LocalDs.current
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(ds.sub.copy(alpha = 0.06f))
        .border(1.dp, ds.sub.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
        .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            val picked = value == selected
            Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
                .background(if (picked) ds.accentSoft else Color.Transparent)
                .border(1.dp, if (picked) ds.accent.copy(alpha = 0.55f) else ds.sub.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                .selectable(picked, role = Role.RadioButton, onClick = { onSelect(value) })
                .padding(horizontal = 2.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(label, fontSize = 13.sp, fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (picked) ds.accent else ds.sub, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun InfoDialog(title: String, message: String, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(24.dp)) {
            val ds = LocalDs.current
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ds.text)
            Text(message, fontSize = 14.sp, lineHeight = 21.sp, color = ds.sub,
                modifier = Modifier.padding(top = 14.dp, bottom = 24.dp))
            Button("知道了", onClick = onDismiss)
        }
    }
}

@Composable
fun NetworkDownDialog(visible: Boolean, onDismiss: () -> Unit) {
    if (visible) InfoDialog(
        "学校官网无法访问",
        "连不上学校服务器（教务/水电），可能是校园网波动或学校维护中。\n检查手机网络后稍后再试。",
        onDismiss,
    )
}
