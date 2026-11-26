package com.haooz.nexioui.blurbottomsheet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** 弹窗吸附档位。 */
internal enum class BlurBottomSheetDetent {
    CONTENT,
    EXPANDED,
    DISMISS,
}

/** 根据当前展开比例、收起位移与速度决定落点。 */
internal fun resolveBlurBottomSheetDetent(
    expansionFraction: Float,
    dismissOffsetPx: Float,
    velocityY: Float,
    velocityThresholdPx: Float,
    dismissThresholdPx: Float,
): BlurBottomSheetDetent {
    if (dismissOffsetPx > dismissThresholdPx) return BlurBottomSheetDetent.DISMISS
    if (expansionFraction <= 0.001f && velocityY > velocityThresholdPx) {
        return BlurBottomSheetDetent.DISMISS
    }
    if (velocityY < -velocityThresholdPx) return BlurBottomSheetDetent.EXPANDED
    if (velocityY > velocityThresholdPx) return BlurBottomSheetDetent.CONTENT
    return if (expansionFraction >= 0.5f) {
        BlurBottomSheetDetent.EXPANDED
    } else {
        BlurBottomSheetDetent.CONTENT
    }
}

/** 内容当前是否处于展开（全屏）状态。 */
val LocalBlurBottomSheetContentExpanded = compositionLocalOf { false }

/**
 * 弹窗的展开 / 收起状态机。
 *
 * 拖动时累加位移，松手后由弹簧吸附到内容高度或全屏高度，超过阈值则关闭。
 */
@Stable
class BlurBottomSheetDetentState {
    private val dragExpansion = mutableFloatStateOf(0f)
    private val settleExpansion = Animatable(0f)
    private val dragDismissOffset = mutableFloatStateOf(0f)
    private val settleDismissOffset = Animatable(0f)

    val collapsedHeightPx = mutableIntStateOf(0)
    private var expansionRangePx = 0

    val expansionFraction: Float
        get() = (dragExpansion.floatValue + settleExpansion.value).coerceIn(0f, 1f)

    val dismissOffsetPx: Float
        get() = (dragDismissOffset.floatValue + settleDismissOffset.value).coerceAtLeast(0f)

    fun updateCollapsedHeight(heightPx: Int) {
        if (
            expansionFraction <= 0.001f && heightPx > 0 && collapsedHeightPx.intValue != heightPx
        ) {
            collapsedHeightPx.intValue = heightPx
        }
    }

    fun updateExpansionRange(maxHeightPx: Int) {
        expansionRangePx = (maxHeightPx - collapsedHeightPx.intValue).coerceAtLeast(0)
    }

    suspend fun reset() {
        settleExpansion.stop()
        settleDismissOffset.stop()
        settleExpansion.snapTo(0f)
        settleDismissOffset.snapTo(0f)
        dragExpansion.floatValue = 0f
        dragDismissOffset.floatValue = 0f
        collapsedHeightPx.intValue = 0
        expansionRangePx = 0
    }

    suspend fun beginDrag() {
        val pendingExpansion = expansionFraction
        val pendingDismissOffset = dismissOffsetPx
        settleExpansion.stop()
        settleDismissOffset.stop()
        settleExpansion.snapTo(0f)
        settleDismissOffset.snapTo(0f)
        dragExpansion.floatValue = pendingExpansion
        dragDismissOffset.floatValue = pendingDismissOffset
    }

    fun dragBy(deltaY: Float) {
        var remaining = deltaY
        var expansion = expansionFraction
        var dismissOffset = dismissOffsetPx
        val range = expansionRangePx.toFloat()

        if (remaining < 0f) {
            val restoredOffset = minOf(-remaining, dismissOffset)
            dismissOffset -= restoredOffset
            remaining += restoredOffset
            if (range > 0f) expansion = (expansion - remaining / range).coerceIn(0f, 1f)
        } else if (remaining > 0f) {
            if (range > 0f) {
                val collapsedDistance = expansion * range
                val collapseDistance = minOf(remaining, collapsedDistance)
                expansion = (expansion - collapseDistance / range).coerceIn(0f, 1f)
                remaining -= collapseDistance
            }
            dismissOffset += remaining
        }

        dragExpansion.floatValue = expansion
        dragDismissOffset.floatValue = dismissOffset
    }

    suspend fun settle(
        velocityY: Float,
        velocityThresholdPx: Float,
        dismissThresholdPx: Float,
        onDismiss: () -> Unit,
    ) {
        val target = resolveBlurBottomSheetDetent(
            expansionFraction = expansionFraction,
            dismissOffsetPx = dismissOffsetPx,
            velocityY = velocityY,
            velocityThresholdPx = velocityThresholdPx,
            dismissThresholdPx = dismissThresholdPx,
        )
        if (target == BlurBottomSheetDetent.DISMISS) {
            onDismiss()
            return
        }

        val startExpansion = expansionFraction
        val startDismissOffset = dismissOffsetPx
        val targetExpansion = if (target == BlurBottomSheetDetent.EXPANDED) 1f else 0f
        val initialVelocity = if (expansionRangePx > 0) {
            (-velocityY / expansionRangePx).coerceIn(-4f, 4f)
        } else {
            0f
        }
        dragExpansion.floatValue = 0f
        dragDismissOffset.floatValue = 0f
        settleExpansion.snapTo(startExpansion)
        settleDismissOffset.snapTo(startDismissOffset)
        coroutineScope {
            launch {
                settleExpansion.animateTo(
                    targetValue = targetExpansion,
                    animationSpec = spring(
                        dampingRatio = 0.9f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    initialVelocity = initialVelocity,
                )
            }
            launch {
                settleDismissOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.9f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }
        }
    }
}

/** 按展开比例在内容高度与可用最大高度之间取当前高度。 */
fun Modifier.blurBottomSheetDetentHeight(
    state: BlurBottomSheetDetentState,
): Modifier = layout { measurable, constraints ->
    val maxHeight = constraints.maxHeight
    state.updateExpansionRange(maxHeight)
    val collapsedHeight = state.collapsedHeightPx.intValue
    val expansionFraction = state.expansionFraction
    if (collapsedHeight == 0 || expansionFraction <= 0.001f) {
        val placeable = measurable.measure(constraints.copy(minHeight = 0))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    } else {
        val boundedCollapsedHeight = collapsedHeight.coerceAtMost(maxHeight)
        val targetHeight = (
            boundedCollapsedHeight +
                (maxHeight - boundedCollapsedHeight) * expansionFraction
            ).roundToInt().coerceIn(0, maxHeight)
        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = targetHeight))
        layout(placeable.width, targetHeight) { placeable.place(0, 0) }
    }
}
