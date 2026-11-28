package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.nexioui.arrowpreference.ArrowPreference
import com.haooz.nexioui.basiccomponent.BasicComponent
import com.haooz.nexioui.basiccomponent.BasicComponentDefaults
import com.haooz.nexioui.button.Button
import com.haooz.nexioui.button.ButtonDefaults
import com.haooz.nexioui.button.FlatIconButton
import com.haooz.nexioui.button.FlatTextButton
import com.haooz.nexioui.button.TextButtonColors
import com.haooz.nexioui.divider.HorizontalDivider
import com.haooz.nexioui.icon.NexioIcons
import com.haooz.nexioui.icon.extended.Back
import com.haooz.nexioui.overlaydialog.OverlayDialog
import com.haooz.nexioui.segmentedcontrol.SegmentedControl
import com.haooz.nexioui.smalltitle.SmallTitle
import com.haooz.nexioui.switchpreference.SwitchPreference
import com.haooz.nexioui.text.NexioText as Text
import com.haooz.nexioui.textfield.TextField as NexioTextField
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.shape.ContinuousCapsule
import com.menglimi.gdust.v2.ui.AppCard
import com.menglimi.gdust.v2.ui.AppUiTokens
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.currentPageBackdrop
import com.menglimi.gdust.v2.ui.hasActiveWallpaper
import com.menglimi.gdust.v2.ui.wallpaperTextSurface

@Composable
fun nexioPagePadding(): Dp = if (LocalConfiguration.current.screenWidthDp >= 600) 20.dp else AppUiTokens.PagePadding

/** 兼容旧调用的页头；新页面优先使用 [com.menglimi.gdust.v2.ui.NexioPageScaffold] 自带的可折叠大标题顶栏。 */
@Composable
fun PageTitle(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    val ds = LocalDs.current
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        if (onBack != null) {
            FlatIconButton(
                onClick = onBack,
                backgroundColor = ds.card,
                minWidth = 42.dp,
                minHeight = 42.dp,
            ) {
                Icon(NexioIcons.Back, "返回", Modifier.size(22.dp), tint = ds.text)
            }
            Spacer(Modifier.height(10.dp))
        }
        Text(
            text = title,
            fontSize = AppUiTokens.TitleSize,
            fontWeight = AppUiTokens.TitleWeight,
            color = ds.text,
            modifier = Modifier.semantics { heading() },
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = ds.sub,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    SmallTitle(
        text = text,
        textColor = if (hasActiveWallpaper()) LocalDs.current.text else LocalDs.current.sub,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier.padding(top = 6.dp).wallpaperTextSurface().semantics { heading() },
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
    val ds = LocalDs.current
    val titleColor = if (danger) {
        BasicComponentDefaults.titleColor(color = ds.danger)
    } else {
        BasicComponentDefaults.titleColor(color = ds.text)
    }
    if (switch != null && onSwitch != null) {
        SwitchPreference(
            checked = switch,
            onCheckedChange = onSwitch,
            title = title,
            titleColor = titleColor,
            summary = subtitle?.takeIf { it.isNotBlank() },
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            endActions = {
                if (detail != null) {
                    Text(
                        text = detail,
                        color = ds.sub,
                        fontSize = 13.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.widthIn(max = 120.dp),
                    )
                }
            },
        )
    } else {
        PreferenceContent(title, subtitle, detail, danger, onClick, null)
    }
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
    val titleColor = if (danger) {
        BasicComponentDefaults.titleColor(color = ds.danger)
    } else {
        BasicComponentDefaults.titleColor(color = ds.text)
    }
    if (trailing == null && onClick != null) {
        ArrowPreference(
            title = title,
            titleColor = titleColor,
            summary = subtitle?.takeIf { it.isNotBlank() },
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            onClick = onClick,
            endActions = {
                if (detail != null) {
                    Text(
                        text = detail,
                        color = ds.sub,
                        fontSize = 13.sp,
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 132.dp),
                    )
                }
            },
        )
    } else {
        BasicComponent(
            title = title,
            titleColor = titleColor,
            summary = subtitle?.takeIf { it.isNotBlank() },
            onClick = onClick,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            endActions = if (detail != null || trailing != null) {
                {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (detail != null) {
                            Text(
                                text = detail,
                                color = ds.sub,
                                fontSize = 13.sp,
                                textAlign = TextAlign.End,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 132.dp),
                            )
                        }
                        trailing?.invoke()
                    }
                }
            } else {
                null
            },
        )
    }
}

/** NexioUI 标准细分隔线。 */
@Composable
fun SettingDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = NexioTheme.colorScheme.dividerLine.copy(alpha = 0.65f),
    )
}

@Composable
fun SettingEditor(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/**
 * 分段选择器：2–4 项时直接使用 NexioUI SoftGlass 拖拽胶囊 [SegmentedControl]，
 * 5 项以上使用连续圆角分段条避免挤字。
 */
@Composable
fun <T> SegmentedChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop = currentPageBackdrop(),
) {
    if (options.isEmpty()) return
    val selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    if (options.size in 2..4) {
        SegmentedControl(
            options = options.map { it.second },
            selectedIndex = selectedIndex,
            onSelect = { idx -> options.getOrNull(idx)?.let { onSelect(it.first) } },
            backdrop = backdrop,
            modifier = modifier.fillMaxWidth(),
            containerHeight = 42.dp,
        )
        return
    }
    val ds = LocalDs.current
    val haptic = LocalHapticFeedback.current
    val capsule = remember { ContinuousCapsule() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(capsule)
            .background(ds.breakBar)
            .selectableGroup()
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val picked = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(capsule)
                    .background(if (picked) ds.card else Color.Transparent)
                    .selectable(
                        selected = picked,
                        role = Role.RadioButton,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            onSelect(value)
                        },
                    )
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (picked) ds.text else ds.sub,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 堆叠式 NexioUI 原生浮动标签输入框（基于连续圆角与聚焦主色描边）。 */
@Composable
fun NexioInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 6,
    minLines: Int = 1,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    NexioTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        insideMargin = DpSize(16.dp, 14.dp),
        cornerRadius = 18.dp,
        label = label,
        useLabelAsPlaceholder = true,
        enabled = enabled,
        readOnly = readOnly,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
    )
}

/** 偏好行内联输入项（左标签 + 右输入），用于设置卡片内单行配置。 */
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
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = placeholder,
            modifier = Modifier.weight(1f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) ds.text else ds.sub,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 42.dp)
                .semantics { contentDescription = placeholder },
            textStyle = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) ds.text else ds.sub,
                textAlign = TextAlign.End,
            ),
            singleLine = true,
            cursorBrush = SolidColor(ds.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visual,
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    if (value.isEmpty()) {
                        Text(hint, fontSize = 14.sp, color = ds.sub, textAlign = TextAlign.End)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
fun NexioActionButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val ds = LocalDs.current
    val haptic = LocalHapticFeedback.current
    val colors = ButtonDefaults.buttonColorsPrimary(
        color = if (danger) ds.danger else ds.accent,
        disabledColor = ds.breakBar,
        contentColor = Color.White,
        disabledContentColor = ds.sub,
    )
    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            onClick()
        },
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        cornerRadius = 100.dp,
        minHeight = 44.dp,
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        colors = colors,
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) colors.contentColor else colors.disabledContentColor,
        )
    }
}

/** 悬浮液态玻璃主/次动作按钮（直接采样当前页面 SoftGlass backdrop）。 */
@Composable
fun NexioGlassActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
    backdrop: GlassBackdrop = currentPageBackdrop(),
) {
    com.haooz.nexioui.textbutton.TextButton(
        text = text,
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier,
        enabled = enabled,
        primary = primary,
    )
}

@Composable
fun SettingActionText(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 44.dp,
    cornerRadius: Dp = 100.dp,
    insideMargin: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
    textStyle: TextStyle = TextStyle(fontSize = 14.sp),
    colors: TextButtonColors = ButtonDefaults.textButtonColors(),
) {
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

/** NexioUI 柔光玻璃信息弹窗（手机贴底 / 平板居中，统一托管在根 Scaffold 的 NexioPopupHost）。 */
@Composable
fun InfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    confirmText: String = "知道了",
) {
    OverlayDialog(
        show = true,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        val ds = LocalDs.current
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = message,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = ds.sub,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 20.dp),
            )
            NexioActionButton(
                text = confirmText,
                modifier = Modifier.fillMaxWidth(),
                onClick = onDismiss,
            )
        }
    }
}

/** NexioUI 柔光玻璃确认弹窗（主操作 + 取消/辅助操作）。 */
@Composable
fun NexioConfirmDialog(
    show: Boolean,
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String = "取消",
    dangerConfirm: Boolean = false,
    extraActionText: String? = null,
    onExtraAction: (() -> Unit)? = null,
) {
    OverlayDialog(
        show = show,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        val ds = LocalDs.current
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = message,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = ds.sub,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
            )
            if (extraActionText != null && onExtraAction != null) {
                SettingActionText(
                    text = extraActionText,
                    onClick = onExtraAction,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingActionText(
                    text = dismissText,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                NexioActionButton(
                    text = confirmText,
                    danger = dangerConfirm,
                    modifier = Modifier.weight(1f),
                    onClick = onConfirm,
                )
            }
        }
    }
}

@Composable
fun NetworkDownDialog(visible: Boolean, onDismiss: () -> Unit, message: String? = null) {
    if (visible) {
        InfoDialog(
            title = "学校官网无法访问",
            message = message ?: "连不上学校服务器（教务/水电），可能是校园网波动或学校维护中。\n检查手机网络后稍后再试。",
            onDismiss = onDismiss,
        )
    }
}
