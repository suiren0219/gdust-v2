package com.haooz.nexioui.pressfeedback

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.unit.Constraints
import com.haooz.nexioui.interaction.HoldDownInteraction
import kotlinx.coroutines.launch

/**
 * 按压缩小反馈：按下缩到 [sinkAmount]，松开弹回 1。
 *
 * 作为 `clickable(indication = ...)` 使用，与水波纹互斥；也响应 [HoldDownInteraction]，
 * 长按态（手指滑出边界仍算按住）与普通按下共用同一份缩放。
 */
@Stable
data class SinkFeedback(
    val sinkAmount: Float = 0.94f,
    val animationSpec: AnimationSpec<Float> = spring(0.8f, 600f),
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        SinkFeedbackNode(interactionSource, sinkAmount, animationSpec)

    private class SinkFeedbackNode(
        var interactionSource: InteractionSource,
        var sinkAmount: Float,
        var animationSpec: AnimationSpec<Float>,
    ) : Modifier.Node(),
        LayoutModifierNode {

        private val animatedScale = Animatable(1f)
        private var isPressed = false
        private var isHoldDown = false

        private fun updateState() {
            val target = if (isPressed || isHoldDown) sinkAmount else 1f
            coroutineScope.launch { animatedScale.animateTo(target, animationSpec) }
        }

        override fun onAttach() {
            coroutineScope.launch {
                interactionSource.interactions.collect { interaction: Interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> isPressed = true
                        is PressInteraction.Release, is PressInteraction.Cancel -> isPressed = false
                        is HoldDownInteraction.HoldDown -> isHoldDown = true
                        is HoldDownInteraction.Release -> isHoldDown = false
                        else -> return@collect
                    }
                    updateState()
                }
            }
        }

        override fun MeasureScope.measure(
            measurable: Measurable,
            constraints: Constraints,
        ): MeasureResult {
            val placeable = measurable.measure(constraints)
            return layout(placeable.width, placeable.height) {
                placeable.placeWithLayer(0, 0) {
                    scaleX = animatedScale.value
                    scaleY = animatedScale.value
                }
            }
        }
    }
}
