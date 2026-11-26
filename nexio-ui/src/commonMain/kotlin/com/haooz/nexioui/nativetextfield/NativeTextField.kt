package com.haooz.nexioui.nativetextfield

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign

/**
 * 裸输入区（无底色/圆角/标签）：Android 用 `AndroidView(EditText)` 保留系统长按菜单与手柄着色，skiko 用 `BasicTextField` 兜底。
 *
 * @param value 文本内容
 * @param onValueChange 内容变化回调
 * @param hint 空内容时的提示文字
 * @param textStyle 文字样式；`color` 未指定时回退到主题 `onSurface`
 * @param singleLine 单行
 * @param maxLines 最大行数
 * @param maxLength 最大长度（skiko 按输入截断；Android 走 `InputFilter`）
 * @param textAlign 文本对齐
 * @param enabled 是否可用
 */
@Composable
expect fun NativeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    textStyle: TextStyle = TextStyle.Default,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    maxLength: Int = Int.MAX_VALUE,
    textAlign: TextAlign = TextAlign.Start,
    enabled: Boolean = true,
)
