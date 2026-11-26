package com.haooz.nexioui.button

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.theme.NexioTheme

/** [Button] 与 [FlatTextButton] 的尺寸和配色默认值。 */
object ButtonDefaults {

    /** 默认最小宽度（可用 `Modifier.widthIn` 覆盖）。 */
    val MinWidth = 58.dp

    /** 默认最小高度（可用 `Modifier.heightIn` 覆盖）。 */
    val MinHeight = 40.dp

    /** 默认圆角。 */
    val CornerRadius = 16.dp

    /** 默认内边距。 */
    val InsideMargin = PaddingValues(horizontal = 16.dp, vertical = 13.dp)

    /** 普通填充按钮配色。 */
    @Composable
    fun buttonColors(
        color: Color = NexioTheme.colorScheme.secondaryVariant,
        disabledColor: Color = NexioTheme.colorScheme.disabledSecondaryVariant,
        contentColor: Color = NexioTheme.colorScheme.onSecondaryVariant,
        disabledContentColor: Color = NexioTheme.colorScheme.disabledOnSecondaryVariant,
    ): ButtonColors = remember(color, disabledColor, contentColor, disabledContentColor) {
        ButtonColors(
            color = color,
            disabledColor = disabledColor,
            contentColor = contentColor,
            disabledContentColor = disabledContentColor,
        )
    }

    /** 主色填充按钮配色。 */
    @Composable
    fun buttonColorsPrimary(
        color: Color = NexioTheme.colorScheme.primary,
        disabledColor: Color = NexioTheme.colorScheme.disabledPrimaryButton,
        contentColor: Color = NexioTheme.colorScheme.onPrimary,
        disabledContentColor: Color = NexioTheme.colorScheme.disabledOnPrimaryButton,
    ): ButtonColors = remember(color, disabledColor, contentColor, disabledContentColor) {
        ButtonColors(
            color = color,
            disabledColor = disabledColor,
            contentColor = contentColor,
            disabledContentColor = disabledContentColor,
        )
    }

    /** 普通文本按钮配色。 */
    @Composable
    fun textButtonColors(
        // 取主题自身背景的明暗，而不是宿主的「是否深色」：宿主用强制深色覆盖主题时，
        // 按钮文字色会与背景对不上。
        color: Color = if (NexioTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color.Black,
        disabledColor: Color = NexioTheme.colorScheme.disabledSecondaryVariant,
        textColor: Color = NexioTheme.colorScheme.onSecondaryVariant,
        disabledTextColor: Color = NexioTheme.colorScheme.disabledOnSecondaryVariant,
        alpha: Float = 0.06f,
    ): TextButtonColors = remember(color, disabledColor, textColor, disabledTextColor, alpha) {
        TextButtonColors(
            color = color,
            disabledColor = disabledColor,
            textColor = textColor,
            disabledTextColor = disabledTextColor,
            alpha = alpha,
        )
    }

    /** 主色文本按钮配色。 */
    @Composable
    fun textButtonColorsPrimary(
        color: Color = NexioTheme.colorScheme.primary,
        disabledColor: Color = NexioTheme.colorScheme.disabledPrimaryButton,
        textColor: Color = NexioTheme.colorScheme.onPrimary,
        disabledTextColor: Color = NexioTheme.colorScheme.disabledOnPrimaryButton,
        alpha: Float = 0.84f,
    ): TextButtonColors = remember(color, disabledColor, textColor, disabledTextColor, alpha) {
        TextButtonColors(
            color = color,
            disabledColor = disabledColor,
            textColor = textColor,
            disabledTextColor = disabledTextColor,
            alpha = alpha,
        )
    }
}
