package com.haooz.nexioui.internal

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

/**
 * 按压下沉缩放：按住时缩到 [sinkAmount]，松手回弹。
 *
 * 只读取 [interactionSource] 上的按压状态，不参与点击与语义；用于给小型控件做下沉反馈。
 * [enabled] 为 false 时始终保持原尺寸。
 */
@Composable
internal fun rememberSinkScale(
    interactionSource: InteractionSource,
    enabled: Boolean,
    sinkAmount: Float = 0.85f,
): State<Float> {
    val isPressed by interactionSource.collectIsPressedAsState()
    val spec = remember { spring<Float>(dampingRatio = 0.99f, stiffness = 986.96f) }
    return animateFloatAsState(
        targetValue = if (enabled && isPressed) sinkAmount else 1f,
        animationSpec = spec,
        label = "SinkScale",
    )
}
