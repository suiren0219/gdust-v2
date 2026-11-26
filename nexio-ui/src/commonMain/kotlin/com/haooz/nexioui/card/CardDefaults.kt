package com.haooz.nexioui.card

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.theme.NexioTheme

/** [Card] 默认参数。 */
object CardDefaults {

    val CornerRadius = 20.dp

    val InsideMargin = PaddingValues(0.dp)

    /**
     * 卡片高度补间曲线。
     *
     * 行数变化、文字换行导致的高度差都走这一条 spring；首帧不播，首次测量直接定高。
     * `dampingRatio = 0.9` 只有极轻微回弹，`StiffnessMediumLow` 对常见行高变化约 260~320ms。
     */
    val SizeAnimation: FiniteAnimationSpec<IntSize> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntSize.VisibilityThreshold,
    )

    /** 默认卡片配色。 */
    @Composable
    fun defaultColors(
        color: Color = NexioTheme.colorScheme.surfaceContainer,
        contentColor: Color = NexioTheme.colorScheme.onSurfaceContainer,
    ): CardColors = remember(color, contentColor) {
        CardColors(color = color, contentColor = contentColor)
    }
}
