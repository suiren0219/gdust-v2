package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material3.HorizontalDivider
import com.haooz.nexioui.smalltitle.SmallTitle
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.ui.AppCard
import com.menglimi.gdust.v2.ui.AppUiTokens
import com.menglimi.gdust.v2.ui.LocalDs
import com.haooz.nexioui.basiccomponent.BasicComponent
import com.haooz.nexioui.button.FlatTextButton
import com.haooz.nexioui.switch.Switch
import com.haooz.nexioui.text.NexioText as Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward

@Composable
fun nexioPagePadding() = if (LocalConfiguration.current.screenWidthDp >= 600) 20.dp else AppUiTokens.PagePadding

@Composable
fun PageTitle(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    val ds = LocalDs.current
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        if (onBack != null) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.CenterStart,
            ) { Icon(MiuixIcons.Back, "返回", Modifier.size(26.dp), tint = ds.text) }
            Spacer(Modifier.height(8.dp))
        }
        Text(title, fontSize = AppUiTokens.TitleSize, fontWeight = AppUiTokens.TitleWeight,
            color = ds.text, modifier = Modifier.semantics { heading() })
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, fontSize = 14.sp, lineHeight = 21.sp, color = ds.sub,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    SmallTitle(
        text = text,
        textColor = LocalDs.current.sub,
        modifier = modifier.padding(top = 8.dp, bottom = 6.dp).semantics { heading() },
    )
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
    AppCard(Modifier.fillMaxWidth()) {
        PreferenceContent(title, subtitle, detail, danger, onClick, trailing)
    }
}

@Composable
fun SettingGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    AppCard(modifier.fillMaxWidth(), content = content)
    Spacer(Modifier.height(AppUiTokens.CardGap))
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
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        endActions = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (detail != null) Text(detail, color = ds.sub, fontSize = 13.sp,
                    textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 128.dp))
                if (trailing != null) trailing()
                else if (onClick != null) Icon(MiuixIcons.ChevronForward, null, Modifier.size(18.dp), tint = ds.sub)
            }
        },
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = if (danger) ds.danger else ds.text)
        if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 14.sp, lineHeight = 20.sp,
            color = ds.sub, modifier = Modifier.padding(top = 4.dp))
    }
}

/** 卡内分隔线保持低对比，与偏好行左右对齐。 */
@Composable
fun SettingDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = LocalDs.current.sub.copy(alpha = 0.1f),
    )
}

@Composable
fun SettingEditor(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
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
    val haptic = LocalHapticFeedback.current
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(100.dp)).background(ds.breakBar)
        .selectableGroup().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, label) ->
            val picked = value == selected
            Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(100.dp))
                .background(if (picked) ds.card else Color.Transparent)
                .selectable(
                    picked, role = Role.RadioButton,
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.VirtualKey); onSelect(value) },
                )
                .padding(horizontal = 4.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(label, fontSize = 13.sp, fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (picked) ds.text else ds.sub, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun NexioField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
    visual: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true,
    hint: String = "请输入",
) {
    val ds = LocalDs.current
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(placeholder, modifier = Modifier.weight(1f), fontSize = 17.sp,
            fontWeight = FontWeight.Medium, color = if (enabled) ds.text else ds.sub,
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                .semantics { contentDescription = placeholder },
            textStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium,
                color = if (enabled) ds.text else ds.sub, textAlign = TextAlign.End),
            singleLine = true,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(ds.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visual,
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    if (value.isEmpty()) Text(hint, fontSize = 14.sp, color = ds.sub, textAlign = TextAlign.End)
                    inner()
                }
            },
        )
    }
}

@Composable
fun NexioActionButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val ds = LocalDs.current
    val haptic = LocalHapticFeedback.current
    val colors = com.haooz.nexioui.button.ButtonDefaults.buttonColorsPrimary(
        color = ds.accent, disabledColor = ds.breakBar,
        contentColor = Color.White, disabledContentColor = ds.sub,
    )
    com.haooz.nexioui.button.Button(
        onClick = { haptic.performHapticFeedback(HapticFeedbackType.Confirm); onClick() },
        enabled = enabled,
        modifier = Modifier.heightIn(min = 44.dp),
        cornerRadius = 100.dp,
        minHeight = 44.dp,
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        colors = colors,
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            color = if (enabled) colors.contentColor else colors.disabledContentColor)
    }
}

@Composable
fun SettingActionText(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: androidx.compose.ui.unit.Dp = 44.dp,
    cornerRadius: androidx.compose.ui.unit.Dp = 100.dp,
    insideMargin: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
    textStyle: TextStyle = TextStyle(fontSize = 14.sp),
    colors: com.haooz.nexioui.button.TextButtonColors = com.haooz.nexioui.button.ButtonDefaults.textButtonColors(),
) {
    val ds = LocalDs.current
    FlatTextButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = minHeight),
        cornerRadius = cornerRadius,
        minHeight = minHeight,
        insideMargin = insideMargin,
        colors = colors,
        textStyle = textStyle,
    )
}

@Composable
fun InfoDialog(title: String, message: String, onDismiss: () -> Unit, confirmText: String = "知道了") {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        AppCard(Modifier.fillMaxWidth(), insideMargin = PaddingValues(24.dp)) {
            val ds = LocalDs.current
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = ds.text)
            Text(message, fontSize = 14.sp, lineHeight = 22.sp, color = ds.sub,
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(top = 14.dp, bottom = 24.dp))
            NexioActionButton(confirmText, onClick = onDismiss)
        }
    }
}

@Composable
fun NetworkDownDialog(visible: Boolean, onDismiss: () -> Unit, message: String? = null) {
    if (visible) InfoDialog(
        "学校官网无法访问",
        message ?: "连不上学校服务器（教务/水电），可能是校园网波动或学校维护中。\n检查手机网络后稍后再试。",
        onDismiss,
    )
}
