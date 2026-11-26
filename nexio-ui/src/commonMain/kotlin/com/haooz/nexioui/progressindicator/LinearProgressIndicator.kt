package com.haooz.nexioui.progressindicator

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

/**
 * 线性进度指示器。`progress == null` 时为不确定态（三段循环滑动）。
 *
 * @param progress 0.0–1.0；null 表示不确定进度。
 */
@Composable
fun LinearProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    colors: ProgressIndicatorColors = ProgressIndicatorDefaults.progressIndicatorColors(),
    height: Dp = ProgressIndicatorDefaults.DefaultLinearProgressIndicatorHeight,
) {
    val currentBackgroundColor = colors.backgroundColor()
    val currentForegroundColor = colors.foregroundColor(true)

    if (progress == null) {
        val transition = rememberInfiniteTransition()
        val animatedValue by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1250, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        )

        Canvas(
            modifier = modifier
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
                .fillMaxWidth()
                .height(height),
        ) {
            drawRoundRect(
                color = currentBackgroundColor,
                size = size,
                cornerRadius = CornerRadius(size.height / 2),
            )

            for (i in 0 until 3) {
                drawIndeterminateSegment(animatedValue, i, currentForegroundColor)
            }
        }
    } else {
        val progressValue = progress.coerceIn(0f, 1f)

        Canvas(
            modifier = modifier
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progressValue, 0f..1f) }
                .fillMaxWidth()
                .height(height),
        ) {
            val cornerRadius = size.height / 2

            drawRoundRect(
                color = currentBackgroundColor,
                size = size,
                cornerRadius = CornerRadius(cornerRadius),
            )

            val minWidth = cornerRadius * 2
            val progressWidth = minWidth + (size.width - minWidth) * progressValue

            drawRoundRect(
                color = currentForegroundColor,
                size = Size(progressWidth, size.height),
                cornerRadius = CornerRadius(cornerRadius),
            )
        }
    }
}

/** 不确定态的单段动画。 */
private fun DrawScope.drawIndeterminateSegment(
    animatedValue: Float,
    segmentIndex: Int,
    color: Color,
) {
    val position = animatedValue - segmentIndex * (0.45f + 0.55f)
    val adjustedPos = (position % 1f + 1f) % 1f
    val cornerRadius = CornerRadius(size.height / 2)

    if (adjustedPos < 1f - 0.45f) {
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * adjustedPos, 0f),
            size = Size(size.width * 0.45f, size.height),
            cornerRadius = cornerRadius,
        )
    } else {
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * adjustedPos, 0f),
            size = Size(size.width * (1f - adjustedPos), size.height),
            cornerRadius = cornerRadius,
        )
        val remainingWidth = adjustedPos + 0.45f - 1f
        if (remainingWidth > 0) {
            drawRoundRect(
                color = color,
                size = Size(size.width * remainingWidth, size.height),
                cornerRadius = cornerRadius,
            )
        }
    }
}
