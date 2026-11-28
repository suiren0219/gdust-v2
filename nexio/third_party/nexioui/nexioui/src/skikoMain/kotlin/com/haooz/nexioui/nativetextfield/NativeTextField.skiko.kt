package com.haooz.nexioui.nativetextfield

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import com.haooz.nexioui.theme.NexioTheme

/**
 * skiko（JVM 桌面 / iOS / wasmJs）兜底实现。
 *
 * 与 Android actual 的对齐点：主题色光标与选中高亮、hint 颜色、默认 Medium 字重、
 * `textStyle.color` 未指定时回退 `onSurface`、IME Done。
 * 平台固有差异：没有系统长按菜单与原生选择手柄。
 */
@Composable
actual fun NativeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    hint: String,
    textStyle: TextStyle,
    singleLine: Boolean,
    maxLines: Int,
    maxLength: Int,
    textAlign: TextAlign,
    enabled: Boolean,
) {
    val themeColor = NexioTheme.colorScheme.primary
    val textColor = textStyle.color.takeIf { it.isSpecified }
        ?: NexioTheme.colorScheme.onSurface
    val hintColor = NexioTheme.colorScheme.onSurfaceVariantActions

    BasicTextField(
        value = value,
        onValueChange = { input ->
            // 与 Android 的 InputFilter.LengthFilter 等价：输入侧截断
            onValueChange(if (input.length > maxLength) input.substring(0, maxLength) else input)
        },
        modifier = modifier,
        enabled = enabled,
        textStyle = textStyle.copy(
            color = textColor,
            fontWeight = textStyle.fontWeight ?: FontWeight.Medium,
            textAlign = textAlign,
        ),
        cursorBrush = SolidColor(themeColor),
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        decorationBox = { innerTextField ->
            // hint 与输入区同位叠放，等价 EditText 的内部 hint（有内容即隐藏）。
            // 两处都必须 fillMaxWidth()：textAlign 仅在有多余宽度时生效。
            // Box 的 contentAlignment 也要随 textAlign 走：innerTextField 若按 wrap-content
            // 测量，仅靠 textStyle.textAlign 对齐不到 EditText 的表现。
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = when (textAlign) {
                    TextAlign.Center -> Alignment.Center
                    TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
                    else -> Alignment.CenterStart
                },
            ) {
                if (value.isEmpty() && hint.isNotEmpty()) {
                    Text(
                        text = hint,
                        modifier = Modifier.fillMaxWidth(),
                        style = textStyle.copy(
                            color = hintColor,
                            fontWeight = textStyle.fontWeight ?: FontWeight.Medium,
                            textAlign = textAlign,
                        ),
                    )
                }
                innerTextField()
            }
        },
    )
}
