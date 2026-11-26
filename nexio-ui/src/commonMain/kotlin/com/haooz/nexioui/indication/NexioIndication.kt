package com.haooz.nexioui.indication

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import com.haooz.nexioui.interaction.HoldDownInteraction
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI

private const val HOVER_ALPHA_DELTA = 0.06f
private const val FOCUS_ALPHA_DELTA = 0.08f
private const val PRESS_ALPHA_DELTA = 0.10f
private const val HOLD_DOWN_ALPHA_DELTA = 0.10f

private fun responseSpring(damping: Float, response: Float): SpringSpec<Float> {
    val stiffness = (2f * PI.toFloat() / response).let { it * it }
    return spring(dampingRatio = damping, stiffness = stiffness, visibilityThreshold = 0.001f)
}

private val PressEnterSpring = responseSpring(damping = 1.0f, response = 0.2f)
private val PressExitSpring = responseSpring(damping = 0.95f, response = 0.35f)
private val HoverEnterSpring = responseSpring(damping = 1.0f, response = 0.6f)
private val HoverExitSpring = responseSpring(damping = 0.96f, response = 0.2f)

/**
 * 默认按压反馈：按下时整块覆盖一层淡色，而不是 Material 的水波纹。
 *
 * 移植自 miuix 的 `MiuixIndication`，由 [com.haooz.nexioui.theme.NexioTheme] 通过
 * `LocalIndication` 提供，`Modifier.clickable` 未显式指定 indication 时即使用此效果。
 */
@Immutable
class NexioIndication(
    private val color: Color = Color.Black,
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        NexioIndicationInstance(interactionSource, color)

    override fun hashCode(): Int = color.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NexioIndication) return false
        return color == other.color
    }

    private class NexioIndicationInstance(
        private val interactionSource: InteractionSource,
        private val color: Color,
    ) : Modifier.Node(), DrawModifierNode {

        private var isPressed = false
        private var isHovered = false
        private var isFocused = false
        private var isHoldDown = false
        private val animatedAlpha = Animatable(0f)
        private var pressedAnimation: Job? = null
        private var restingAnimation: Job? = null

        private fun targetAlpha(): Float {
            var target = 0f
            if (isHovered) target += HOVER_ALPHA_DELTA
            if (isFocused) target += FOCUS_ALPHA_DELTA
            if (isPressed && !isHoldDown) target += PRESS_ALPHA_DELTA
            if (isHoldDown) target += HOLD_DOWN_ALPHA_DELTA
            return target
        }

        private fun animateOverlay(spring: SpringSpec<Float>, fromPressRelease: Boolean) {
            val target = targetAlpha()
            if (fromPressRelease || target == 0f) {
                restingAnimation?.cancel()
                restingAnimation = coroutineScope.launch {
                    pressedAnimation?.join()
                    animatedAlpha.animateTo(targetValue = target, animationSpec = spring)
                }
            } else {
                pressedAnimation?.cancel()
                restingAnimation?.cancel()
                pressedAnimation = coroutineScope.launch {
                    animatedAlpha.animateTo(targetValue = target, animationSpec = spring)
                }
            }
        }

        override fun onAttach() {
            coroutineScope.launch {
                interactionSource.interactions.collect { interaction ->
                    val previousPressed = isPressed
                    val previousHovered = isHovered
                    val previousFocused = isFocused
                    val previousHoldDown = isHoldDown

                    when (interaction) {
                        is PressInteraction.Press -> isPressed = true
                        is PressInteraction.Release, is PressInteraction.Cancel -> isPressed = false
                        is HoverInteraction.Enter -> isHovered = true
                        is HoverInteraction.Exit -> isHovered = false
                        is FocusInteraction.Focus -> isFocused = true
                        is FocusInteraction.Unfocus -> isFocused = false
                        is HoldDownInteraction.HoldDown -> isHoldDown = true
                        is HoldDownInteraction.Release -> isHoldDown = false
                        else -> return@collect
                    }

                    val spring = when {
                        previousPressed != isPressed ->
                            if (isPressed) PressEnterSpring else PressExitSpring
                        previousHoldDown != isHoldDown ->
                            if (isHoldDown) PressEnterSpring else PressExitSpring
                        previousHovered != isHovered ->
                            if (isHovered) HoverEnterSpring else HoverExitSpring
                        previousFocused != isFocused ->
                            if (isFocused) HoverEnterSpring else HoverExitSpring
                        else -> return@collect
                    }
                    val fromPressRelease =
                        (previousPressed && !isPressed) || (previousHoldDown && !isHoldDown)
                    animateOverlay(spring, fromPressRelease)
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            val alpha = animatedAlpha.value
            if (alpha > 0f) {
                drawRect(color = color, alpha = alpha, size = size)
            }
        }
    }
}
