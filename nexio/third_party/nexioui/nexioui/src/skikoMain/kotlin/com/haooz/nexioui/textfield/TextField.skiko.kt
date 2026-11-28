/** 输入框的 skiko 实现（JVM 桌面 / iOS）：BasicTextField + 共享外壳。 */
package com.haooz.nexioui.textfield

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.nexioui.text.NexioText
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.shape.continuousBackground
import com.haooz.softglass.shape.continuousBorder

private enum class LabelAnimState { Hidden, Placeholder, Normal, Floating }

@Composable
actual fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    insideMargin: DpSize,
    cornerRadius: Dp,
    label: String,
    useLabelAsPlaceholder: Boolean,
    enabled: Boolean,
    readOnly: Boolean,
    textStyle: TextStyle,
    leadingIcon: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
    singleLine: Boolean,
    maxLines: Int,
    minLines: Int,
    maxLength: Int,
    requestFocus: Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val isFocused = remember { mutableStateOf(false) }
    val lastRequestFocus = remember { mutableStateOf(requestFocus) }

    // true 时聚焦（键盘随焦点由平台输入法管理）；由 true 变为 false 视为关闭并清除焦点。
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
        } else if (lastRequestFocus.value) {
            focusManager.clearFocus()
        }
        lastRequestFocus.value = requestFocus
    }
    DisposableEffect(Unit) {
        onDispose { focusManager.clearFocus() }
    }

    val themeColor = NexioTheme.colorScheme.primary
    val baseTextColor = textStyle.color.takeIf { it.isSpecified }
        ?: NexioTheme.colorScheme.onSurface
    val textColor = if (enabled) baseTextColor else NexioTheme.colorScheme.disabledOnSurface
    val hintColor = if (enabled) {
        NexioTheme.colorScheme.onSurfaceVariantSummary
    } else {
        NexioTheme.colorScheme.disabledOnSurface
    }

    TextFieldShell(
        text = value,
        modifier = modifier,
        insideMargin = insideMargin,
        cornerRadius = cornerRadius,
        label = label,
        useLabelAsPlaceholder = useLabelAsPlaceholder,
        enabled = enabled,
        isFocused = isFocused.value,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
    ) {
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(if (it.length > maxLength) it.take(maxLength) else it) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused.value = it.isFocused },
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle.copy(
                color = textColor,
                fontWeight = textStyle.fontWeight ?: FontWeight.Medium,
            ),
            cursorBrush = SolidColor(themeColor),
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.TopStart) {
                    if (value.isEmpty() && useLabelAsPlaceholder && label.isNotEmpty()) {
                        NexioText(
                            text = label,
                            style = textStyle.copy(
                                color = hintColor,
                                fontWeight = textStyle.fontWeight ?: FontWeight.Medium,
                            ),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
actual fun TextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier,
    insideMargin: DpSize,
    cornerRadius: Dp,
    label: String,
    useLabelAsPlaceholder: Boolean,
    enabled: Boolean,
    readOnly: Boolean,
    textStyle: TextStyle,
    leadingIcon: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
    singleLine: Boolean,
    maxLines: Int,
    minLines: Int,
    requestFocus: Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val isFocused = remember { mutableStateOf(false) }
    val lastRequestFocus = remember { mutableStateOf(requestFocus) }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
        } else if (lastRequestFocus.value) {
            focusManager.clearFocus()
        }
        lastRequestFocus.value = requestFocus
    }
    DisposableEffect(Unit) {
        onDispose { focusManager.clearFocus() }
    }

    val themeColor = NexioTheme.colorScheme.primary
    val baseTextColor = textStyle.color.takeIf { it.isSpecified }
        ?: NexioTheme.colorScheme.onSurface
    val textColor = if (enabled) baseTextColor else NexioTheme.colorScheme.disabledOnSurface
    val hintColor = if (enabled) {
        NexioTheme.colorScheme.onSurfaceVariantSummary
    } else {
        NexioTheme.colorScheme.disabledOnSurface
    }

    TextFieldShell(
        text = value.text,
        modifier = modifier,
        insideMargin = insideMargin,
        cornerRadius = cornerRadius,
        label = label,
        useLabelAsPlaceholder = useLabelAsPlaceholder,
        enabled = enabled,
        isFocused = isFocused.value,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused.value = it.isFocused },
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle.copy(
                color = textColor,
                fontWeight = textStyle.fontWeight ?: FontWeight.Medium,
            ),
            cursorBrush = SolidColor(themeColor),
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            decorationBox = { innerTextField ->
                // 占位符贴顶，与 Android 原生 hint 位置一致
                Box(contentAlignment = Alignment.TopStart) {
                    if (value.text.isEmpty() && useLabelAsPlaceholder && label.isNotEmpty()) {
                        NexioText(
                            text = label,
                            style = textStyle.copy(
                                color = hintColor,
                                fontWeight = textStyle.fontWeight ?: FontWeight.Medium,
                            ),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

/**
 * 共享外壳：连续圆角背景 + 聚焦主色描边动画 + 浮动标签 + 前后图标。
 * 标签状态机与布局结构与 Android 实现保持一致，仅输入区由调用方提供。
 */
@Composable
private fun TextFieldShell(
    text: String,
    modifier: Modifier,
    insideMargin: DpSize,
    cornerRadius: Dp,
    label: String,
    useLabelAsPlaceholder: Boolean,
    enabled: Boolean,
    isFocused: Boolean,
    leadingIcon: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val themeColor = NexioTheme.colorScheme.primary
    val backgroundColor = if (enabled) {
        NexioTheme.colorScheme.surfaceVariant
    } else {
        NexioTheme.colorScheme.disabledSecondary
    }
    val labelColor = if (enabled) {
        NexioTheme.colorScheme.onSurfaceVariantSummary
    } else {
        NexioTheme.colorScheme.disabledOnSurface
    }

    val labelState = remember(text, label, useLabelAsPlaceholder) {
        when {
            label.isEmpty() -> LabelAnimState.Hidden
            useLabelAsPlaceholder && text.isNotEmpty() -> LabelAnimState.Placeholder
            text.isNotEmpty() -> LabelAnimState.Floating
            else -> LabelAnimState.Normal
        }
    }

    val borderColor by animateColorAsState(if (isFocused) themeColor else Color.Transparent)
    val borderWidth by animateDpAsState(if (isFocused) 2.dp else 0.dp)

    val labelAnim by animateDpAsState(
        when (labelState) {
            LabelAnimState.Floating -> -insideMargin.height / 2
            LabelAnimState.Placeholder, LabelAnimState.Normal -> 0.dp
            LabelAnimState.Hidden -> 0.dp
        },
    )
    val labelFontSize by animateDpAsState(
        when (labelState) {
            LabelAnimState.Floating -> 10.dp
            else -> 17.dp
        },
    )

    Box(
        modifier = modifier
            .continuousBackground(color = backgroundColor, cornerRadius = cornerRadius)
            .continuousBorder(width = borderWidth, color = borderColor, cornerRadius = cornerRadius),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon?.invoke()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = insideMargin.width,
                        end = insideMargin.width,
                        top = insideMargin.height,
                        bottom = insideMargin.height,
                    ),
                contentAlignment = Alignment.TopStart,
            ) {
                if (labelState == LabelAnimState.Floating ||
                    (labelState == LabelAnimState.Normal && !useLabelAsPlaceholder)
                ) {
                    NexioText(
                        text = label,
                        fontSize = labelFontSize.value.sp,
                        color = labelColor,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.offset { IntOffset(0, labelAnim.roundToPx()) },
                        textAlign = TextAlign.Start,
                    )
                }
                Box(
                    modifier = Modifier.offset(
                        y = if (labelState == LabelAnimState.Floating) insideMargin.height / 2 else 0.dp,
                    ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    content()
                }
            }
            trailingIcon?.invoke()
        }
    }
}
