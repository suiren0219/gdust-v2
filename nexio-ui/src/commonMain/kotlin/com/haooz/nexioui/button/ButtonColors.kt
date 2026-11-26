package com.haooz.nexioui.button

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** 填充按钮的配色。 */
@Immutable
data class ButtonColors(
    val color: Color,
    val disabledColor: Color,
    val contentColor: Color,
    val disabledContentColor: Color,
)

/** 文本按钮的配色：[alpha] 是叠加在 [color] 上的底色不透明度。 */
@Immutable
data class TextButtonColors(
    val color: Color,
    val disabledColor: Color,
    val textColor: Color,
    val disabledTextColor: Color,
    val alpha: Float = 1f,
)
