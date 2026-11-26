package com.haooz.nexioui.internal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * 柔光玻璃控件的路径阴影：形状来自 `Shape.createOutline`，不能退化成圆角矩形参数。
 *
 * @param path 阴影形状（填充语义）。
 * @param color 阴影颜色（含 alpha）。
 * @param blurRadius 模糊半径（px）。
 */
expect fun DrawScope.drawBlurredPathShadow(
    path: Path,
    color: Color,
    blurRadius: Float,
)
