// 派生自 compose-miuix-ui（Apache-2.0），已按 NexioUI 的 API 与包结构重写。

package com.haooz.nexioui.button

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.haooz.nexioui.text.NexioText
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.shape.ContinuousRoundedRectangle

/**
 * 带文字标签的文本按钮：极浅的底色 + 中等字重文字。
 *
 * 与 [Button] 的区别是不吃主题 `secondaryVariant` 那层明显底色，只叠一层低透明度底。
 *
 * @param textStyle 覆盖默认按钮字号。
 * @param textColor 覆盖默认文字色（仅可用态）。
 */
@Composable
fun FlatTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = ButtonDefaults.CornerRadius,
    minWidth: Dp = ButtonDefaults.MinWidth,
    minHeight: Dp = ButtonDefaults.MinHeight,
    colors: TextButtonColors = ButtonDefaults.textButtonColors(),
    insideMargin: PaddingValues = ButtonDefaults.InsideMargin,
    textStyle: TextStyle? = null,
    textColor: Color? = null,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = LocalIndication.current,
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val bgColor = colors.color.copy(alpha = colors.alpha)
    val disabledBgColor = colors.disabledColor
    val containerColor = if (enabled) bgColor else disabledBgColor
    val contentColor = if (enabled) textColor ?: colors.textColor else colors.disabledTextColor
    val rowModifier = remember(minWidth, minHeight, insideMargin) {
        Modifier
            .defaultMinSize(minWidth = minWidth, minHeight = minHeight)
            .padding(insideMargin)
    }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = modifier
                .semantics { role = Role.Button }
                .background(color = containerColor, shape = ContinuousRoundedRectangle(cornerRadius))
                .clip(ContinuousRoundedRectangle(cornerRadius))
                .clickable(
                    interactionSource = interactionSource,
                    indication = indication,
                    enabled = enabled,
                    onClick = onClick,
                ),
            propagateMinConstraints = true,
        ) {
            Row(
                modifier = rowModifier,
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NexioText(
                    text = text,
                    style = (textStyle ?: NexioTheme.textStyles.button).copy(fontWeight = FontWeight.Medium),
                    color = contentColor,
                )
            }
        }
    }
}
