package com.haooz.nexioui.divider

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.theme.NexioTheme

/**
 * 水平分割线。
 *
 * @param thickness 线宽；传 [Dp.Hairline] 得到与密度无关的一像素线。
 * @param color 线色。
 */
@Composable
@NonRestartableComposable
fun HorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = DividerDefaults.Thickness,
    color: Color = DividerDefaults.DividerColor,
) = Canvas(modifier.fillMaxWidth().height(thickness)) {
    drawLine(
        color = color,
        strokeWidth = thickness.toPx(),
        start = Offset(0f, thickness.toPx() / 2),
        end = Offset(size.width, thickness.toPx() / 2),
    )
}

/**
 * 垂直分割线。
 *
 * @param thickness 线宽；传 [Dp.Hairline] 得到与密度无关的一像素线。
 * @param color 线色。
 */
@Composable
@NonRestartableComposable
fun VerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = DividerDefaults.Thickness,
    color: Color = DividerDefaults.DividerColor,
) = Canvas(modifier.fillMaxHeight().width(thickness)) {
    drawLine(
        color = color,
        strokeWidth = thickness.toPx(),
        start = Offset(thickness.toPx() / 2, 0f),
        end = Offset(thickness.toPx() / 2, size.height),
    )
}

object DividerDefaults {
    val Thickness = 0.75.dp

    val DividerColor @Composable get() = NexioTheme.colorScheme.dividerLine
}
