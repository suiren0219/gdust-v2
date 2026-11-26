package com.haooz.nexioui.progressindicator

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 无限循环指示器：一圈细环 + 沿环公转的小圆点。
 *
 * @param color 环与小圆点的颜色。
 * @param size 圆环直径。
 * @param strokeWidth 圆环线宽。
 * @param orbitingDotSize 小圆点半径。
 */
@Composable
fun InfiniteProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Gray,
    size: Dp = ProgressIndicatorDefaults.DefaultInfiniteProgressIndicatorSize,
    strokeWidth: Dp = ProgressIndicatorDefaults.DefaultInfiniteProgressIndicatorStrokeWidth,
    orbitingDotSize: Dp = ProgressIndicatorDefaults.DefaultInfiniteProgressIndicatorOrbitingDotSize,
) {
    val transition = rememberInfiniteTransition()
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )

    Canvas(
        modifier = modifier.size(size),
    ) {
        val strokeWidthPx = strokeWidth.toPx()
        val orbitingDotSizePx = orbitingDotSize.toPx()
        val center = Offset(this.size.width / 2, this.size.height / 2)
        val radius = (size.toPx() - strokeWidthPx) / 2

        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style = Stroke(strokeWidthPx, cap = StrokeCap.Round),
        )

        val orbitRadius = radius - 2 * orbitingDotSizePx
        val angle = rotation * (PI / 180.0).toFloat()
        val dotCenter = center + Offset(
            x = orbitRadius * cos(angle),
            y = orbitRadius * sin(angle),
        )

        drawCircle(
            color = color,
            radius = orbitingDotSizePx,
            center = dotCenter,
        )
    }
}
