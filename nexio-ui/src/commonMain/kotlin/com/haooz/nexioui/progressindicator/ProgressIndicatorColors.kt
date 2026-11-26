package com.haooz.nexioui.progressindicator

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.theme.NexioTheme

/** 进度指示器的颜色三件套。 */
@Immutable
data class ProgressIndicatorColors(
    private val foregroundColor: Color,
    private val disabledForegroundColor: Color,
    private val backgroundColor: Color,
) {
    @Stable
    internal fun foregroundColor(enabled: Boolean): Color =
        if (enabled) foregroundColor else disabledForegroundColor

    @Stable
    internal fun backgroundColor(): Color = backgroundColor
}

object ProgressIndicatorDefaults {
    val DefaultLinearProgressIndicatorHeight = 6.dp
    val DefaultCircularProgressIndicatorStrokeWidth = 4.dp
    val DefaultCircularProgressIndicatorSize = 30.dp
    val DefaultInfiniteProgressIndicatorStrokeWidth = 2.dp
    val DefaultInfiniteProgressIndicatorOrbitingDotSize = 2.dp
    val DefaultInfiniteProgressIndicatorSize = 20.dp

    @Composable
    fun progressIndicatorColors(
        foregroundColor: Color = NexioTheme.colorScheme.primary,
        disabledForegroundColor: Color = NexioTheme.colorScheme.disabledPrimarySlider,
        backgroundColor: Color = NexioTheme.colorScheme.secondaryContainer,
    ): ProgressIndicatorColors = remember(foregroundColor, disabledForegroundColor, backgroundColor) {
        ProgressIndicatorColors(
            foregroundColor = foregroundColor,
            disabledForegroundColor = disabledForegroundColor,
            backgroundColor = backgroundColor,
        )
    }
}
