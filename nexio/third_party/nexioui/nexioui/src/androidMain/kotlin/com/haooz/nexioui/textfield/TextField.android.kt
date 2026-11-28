/** 输入框的 Android 实现：原生 EditText + 系统选择控件。 */
@file:Suppress("DEPRECATION")

package com.haooz.nexioui.textfield

import android.content.Context
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.haooz.nexioui.text.NexioText
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.shape.continuousBackground
import com.haooz.softglass.shape.continuousBorder
import kotlinx.coroutines.delay

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
    val isFocused = remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val view = LocalView.current
    val editTextHolder = remember { mutableStateOf<EditText?>(null) }
    val lastRequestFocus = remember { mutableStateOf(requestFocus) }

    // requestFocus 变为 true 时聚焦并在 EditText 就绪后弹出键盘；
    // 由 true 变为 false 时视为容器关闭，收起键盘并清除焦点。
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            delay(150)
            editTextHolder.value?.let { editText ->
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
            }
        } else if (lastRequestFocus.value) {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
            focusManager.clearFocus()
        }
        lastRequestFocus.value = requestFocus
    }

    // 组件移除时收起键盘并清除焦点
    DisposableEffect(Unit) {
        onDispose {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val token = editTextHolder.value?.windowToken ?: view.windowToken
            imm.hideSoftInputFromWindow(token, 0)
            focusManager.clearFocus()
        }
    }

    val labelState = remember(value, label, useLabelAsPlaceholder) {
        when {
            label.isEmpty() -> LabelAnimState.Hidden
            useLabelAsPlaceholder && value.isNotEmpty() -> LabelAnimState.Placeholder
            value.isNotEmpty() -> LabelAnimState.Floating
            else -> LabelAnimState.Normal
        }
    }

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

    val borderColor by animateColorAsState(
        if (isFocused.value) themeColor else Color.Transparent
    )
    val borderWidth by animateDpAsState(if (isFocused.value) 2.dp else 0.dp)

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

    // 监听器必须稳定：内联回调每次重组都是新引用，会导致 EditText 上残留旧监听，
    // 连续输入时多个旧回调一起回写，光标来回跳。
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val textWatcher = remember {
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.let { currentOnValueChange(it) }
            }
        }
    }

    val baseTextColor = textStyle.color.takeIf { it != Color.Unspecified }
        ?: NexioTheme.colorScheme.onSurface
    val textColor = if (enabled) baseTextColor else NexioTheme.colorScheme.disabledOnSurface
    val hintColor = labelColor
    val weight = textStyle.fontWeight?.weight ?: FontWeight.Medium.weight

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .continuousBackground(color = backgroundColor, cornerRadius = cornerRadius)
            .continuousBorder(
                width = borderWidth,
                color = borderColor,
                cornerRadius = cornerRadius,
            )
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
                        y = if (labelState == LabelAnimState.Floating) insideMargin.height / 2 else 0.dp
                    ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    AndroidView(
                        factory = { ctx ->
                            EditText(ctx).apply {
                                setText(value)
                                hint = if (useLabelAsPlaceholder) label else ""
                                isSingleLine = singleLine
                                this.maxLines = maxLines
                                this.minLines = minLines
                                setTextColor(textColor.toArgb())
                                setHintTextColor(hintColor.toArgb())
                                textSize = textStyle.fontSize.value
                                applyWeightedTypeface(typeface, weight)

                                // 多行必须带 MULTI_LINE 标志，否则输入法只给「完成」不换行；
                                // 单行才用「完成」收起键盘。
                                inputType = if (singleLine) {
                                    InputType.TYPE_CLASS_TEXT
                                } else {
                                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                                }
                                imeOptions = if (singleLine) {
                                    EditorInfo.IME_ACTION_DONE
                                } else {
                                    EditorInfo.IME_ACTION_NONE
                                }

                                background = null
                                setPadding(0, 0, 0, 0)
                                // EditText 在高度大于一行时默认垂直居中，会把正文、光标与提示错开；
                                // 统一钉在左上角。
                                gravity = Gravity.TOP or Gravity.START

                                // 字数上限在输入层截断，避免回写状态后光标被重置到末尾。
                                if (maxLength != Int.MAX_VALUE) {
                                    filters = arrayOf(InputFilter.LengthFilter(maxLength))
                                }

                                // 光标与选择手柄着色（API 29+），选中高亮取主色 20% 透明度。
                                applySelectionHandlesTint(themeColor.toArgb())
                                highlightColor = themeColor.copy(alpha = 0.2f).toArgb()

                                setOnFocusChangeListener { _, focused ->
                                    isFocused.value = focused
                                }

                                addTextChangedListener(textWatcher)

                                editTextHolder.value = this
                            }
                        },
                        update = { editText ->
                            if (editText.text.toString() != value) {
                                // 外部回写时保留光标附近位置，不清空到末尾
                                val caret = editText.selectionStart.coerceIn(0, value.length)
                                editText.removeTextChangedListener(textWatcher)
                                editText.setText(value)
                                editText.setSelection(if (editText.hasFocus()) caret else value.length)
                                editText.addTextChangedListener(textWatcher)
                            }
                            editText.isEnabled = enabled
                            editText.isFocusable = !readOnly
                            editText.setTextColor(textColor.toArgb())
                            editText.setHintTextColor(hintColor.toArgb())
                            editText.highlightColor = themeColor.copy(alpha = 0.2f).toArgb()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            trailingIcon?.invoke()
        }
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
    val isFocused = remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val view = LocalView.current
    val editTextHolder = remember { mutableStateOf<EditText?>(null) }
    val lastRequestFocus = remember { mutableStateOf(requestFocus) }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            delay(150)
            editTextHolder.value?.let { editText ->
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
            }
        } else if (lastRequestFocus.value) {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
            focusManager.clearFocus()
        }
        lastRequestFocus.value = requestFocus
    }

    DisposableEffect(Unit) {
        onDispose {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val token = editTextHolder.value?.windowToken ?: view.windowToken
            imm.hideSoftInputFromWindow(token, 0)
            focusManager.clearFocus()
        }
    }

    val labelState = remember(value.text, label, useLabelAsPlaceholder) {
        when {
            label.isEmpty() -> LabelAnimState.Hidden
            useLabelAsPlaceholder && value.text.isNotEmpty() -> LabelAnimState.Placeholder
            value.text.isNotEmpty() -> LabelAnimState.Floating
            else -> LabelAnimState.Normal
        }
    }

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

    val borderColor by animateColorAsState(
        if (isFocused.value) themeColor else Color.Transparent
    )
    val borderWidth by animateDpAsState(if (isFocused.value) 2.dp else 0.dp)

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

    val baseTextColor = textStyle.color.takeIf { it != Color.Unspecified }
        ?: NexioTheme.colorScheme.onSurface
    val textColor = if (enabled) baseTextColor else NexioTheme.colorScheme.disabledOnSurface
    val hintColor = labelColor
    val weight = textStyle.fontWeight?.weight ?: FontWeight.Medium.weight

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .continuousBackground(color = backgroundColor, cornerRadius = cornerRadius)
            .continuousBorder(
                width = borderWidth,
                color = borderColor,
                cornerRadius = cornerRadius,
            )
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
                        y = if (labelState == LabelAnimState.Floating) insideMargin.height / 2 else 0.dp
                    ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    AndroidView(
                        factory = { ctx ->
                            EditText(ctx).apply {
                                setText(value.text)
                                hint = if (useLabelAsPlaceholder) label else ""
                                isSingleLine = singleLine
                                this.maxLines = maxLines
                                this.minLines = minLines
                                setTextColor(textColor.toArgb())
                                setHintTextColor(hintColor.toArgb())
                                textSize = textStyle.fontSize.value
                                applyWeightedTypeface(typeface, weight)

                                inputType = if (singleLine) {
                                    InputType.TYPE_CLASS_TEXT
                                } else {
                                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                                }
                                imeOptions = if (singleLine) {
                                    EditorInfo.IME_ACTION_DONE
                                } else {
                                    EditorInfo.IME_ACTION_NONE
                                }

                                background = null
                                setPadding(0, 0, 0, 0)
                                gravity = Gravity.TOP or Gravity.START

                                applySelectionHandlesTint(themeColor.toArgb())
                                highlightColor = themeColor.copy(alpha = 0.2f).toArgb()

                                setOnFocusChangeListener { _, focused ->
                                    isFocused.value = focused
                                }

                                addTextChangedListener(object : TextWatcher {
                                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                                    override fun afterTextChanged(s: Editable?) {
                                        s?.toString()?.let { newText ->
                                            if (newText != value.text) {
                                                onValueChange(value.copy(text = newText))
                                            }
                                        }
                                    }
                                })

                                editTextHolder.value = this
                            }
                        },
                        update = { editText ->
                            if (editText.text.toString() != value.text) {
                                editText.setText(value.text)
                                editText.setSelection(value.selection.start, value.selection.end)
                            }
                            editText.isEnabled = enabled
                            editText.isFocusable = !readOnly
                            editText.setTextColor(textColor.toArgb())
                            editText.setHintTextColor(hintColor.toArgb())
                            editText.highlightColor = themeColor.copy(alpha = 0.2f).toArgb()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            trailingIcon?.invoke()
        }
    }
}

private enum class LabelAnimState { Hidden, Placeholder, Normal, Floating }
