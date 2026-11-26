package com.haooz.nexioui.nativetextfield

import android.content.Context
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import com.haooz.nexioui.textfield.applySelectionHandlesTint
import com.haooz.nexioui.textfield.applyWeightedTypeface
import com.haooz.nexioui.theme.NexioTheme

/** 原生 `EditText` 包装；使用系统长按菜单与原生选择手柄。 */
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
    val textColor = textStyle.color.takeIf { it != Color.Unspecified }
        ?: NexioTheme.colorScheme.onSurface
    val hintColor = NexioTheme.colorScheme.onSurfaceVariantActions
    val themeColor = NexioTheme.colorScheme.primary

    val textWatcher = remember(onValueChange) {
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.let { onValueChange(it) }
            }
        }
    }

    AndroidView(
        factory = { context ->
            EditText(context).apply {
                setText(value)
                this.hint = hint
                this.isSingleLine = singleLine
                this.maxLines = maxLines
                setTextColor(textColor.toArgb())
                setHintTextColor(hintColor.toArgb())
                textSize = textStyle.fontSize.value

                // 未指定字重时取 Medium，与 skiko 兜底实现保持一致
                val weight = textStyle.fontWeight?.weight ?: FontWeight.Medium.weight
                applyWeightedTypeface(typeface, weight)

                inputType = InputType.TYPE_CLASS_TEXT

                textAlignment = when (textAlign) {
                    TextAlign.Center -> EditText.TEXT_ALIGNMENT_CENTER
                    TextAlign.End, TextAlign.Right -> EditText.TEXT_ALIGNMENT_VIEW_END
                    else -> EditText.TEXT_ALIGNMENT_VIEW_START
                }

                if (maxLength < Int.MAX_VALUE) {
                    filters = arrayOf(InputFilter.LengthFilter(maxLength))
                }

                imeOptions = EditorInfo.IME_ACTION_DONE
                // 声明了 IME_ACTION_DONE 就必须自己收尾：不挂监听时「完成」按下去系统什么都不做。
                // 多行框保留 Enter 换行，只对单行生效。
                if (singleLine) {
                    setOnEditorActionListener { view, _, _ ->
                        view.finishEditing()
                        true
                    }
                }

                background = null
                setPadding(0, 0, 0, 0)

                applySelectionHandlesTint(themeColor.toArgb())
                highlightColor = themeColor.copy(alpha = 0.2f).toArgb()

                addTextChangedListener(textWatcher)
            }
        },
        update = { editText ->
            if (editText.text.toString() != value) {
                editText.removeTextChangedListener(textWatcher)
                editText.setText(value)
                editText.setSelection(editText.text.length)
                editText.addTextChangedListener(textWatcher)
            }
            editText.isEnabled = enabled
            editText.highlightColor = themeColor.copy(alpha = 0.2f).toArgb()
        },
        modifier = modifier,
    )
}

/** 单行输入框「完成」：失焦并收起键盘。 */
private fun View.finishEditing() {
    clearFocus()
    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
        ?.hideSoftInputFromWindow(windowToken, 0)
}
