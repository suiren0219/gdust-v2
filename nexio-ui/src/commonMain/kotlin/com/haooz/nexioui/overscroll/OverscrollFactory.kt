// 派生自 compose-miuix-ui（Apache-2.0），已按 NexioUI 的 API 与包结构重写。
// 原实现的下拉刷新联动（MiuixPullToRefresh）在 NexioUI 中不存在，相关分支已移除。

package com.haooz.nexioui.overscroll

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 与 [overScrollVertical] / [overScrollHorizontal] 同一套弹簧物理的 [OverscrollFactory]。
 *
 * 由 [com.haooz.nexioui.theme.NexioTheme] 注入 `LocalOverscrollFactory`，让未显式挂
 * `overScroll*` 的 `LazyColumn` / `scrollable` 也拥有相同的越界回弹。
 */
object NexioOverscrollFactory : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = NexioOverscrollEffect()

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = 1
}

/**
 * 复用 [OverscrollMath] 与 [SpringEngine] 的越界回弹效果，X / Y 轴独立处理。
 */
class NexioOverscrollEffect : OverscrollEffect {
    private val offsetThreshold = 1f

    // 位移按整像素放置，只有整像素变化时才重新 layout
    private var lastPlacedOffsetX = 0f
    internal var offsetX = 0f
        private set(value) {
            if (field != value) {
                field = value
                val rounded = round(value)
                if (rounded != lastPlacedOffsetX) {
                    lastPlacedOffsetX = rounded
                    invalidateNodePlacement?.invoke()
                }
            }
        }

    private var lastPlacedOffsetY = 0f
    internal var offsetY = 0f
        private set(value) {
            if (field != value) {
                field = value
                val rounded = round(value)
                if (rounded != lastPlacedOffsetY) {
                    lastPlacedOffsetY = rounded
                    invalidateNodePlacement?.invoke()
                }
            }
        }

    // 由 NexioOverscrollEffectNode 挂载时注入
    internal var invalidateNodePlacement: (() -> Unit)? = null

    private var rawTouchAccumulationX = 0f
    private var rawTouchAccumulationY = 0f

    internal var scrollRangeV = 0f
    internal var scrollRangeH = 0f

    private val springEngineX = SpringEngine()
    private val springEngineY = SpringEngine()
    private var animationJobX: Job? = null
    private var animationJobY: Job? = null

    // 由 NexioOverscrollEffectNode 挂载时注入
    internal var launchAnimation: ((suspend CoroutineScope.() -> Unit) -> Job)? = null
    internal var getOverScrollState: (() -> OverScrollState?)? = null

    override val isInProgress: Boolean
        get() = abs(offsetX) > offsetThreshold || abs(offsetY) > offsetThreshold

    override val node: DelegatableNode = NexioOverscrollEffectNode(this)

    internal fun resetAll() {
        offsetX = 0f
        offsetY = 0f
        rawTouchAccumulationX = 0f
        rawTouchAccumulationY = 0f
        getOverScrollState?.invoke()?.let { state ->
            if (state.isOverScrollActive) state.isOverScrollActive = false
        }
    }

    private fun resetStateX() {
        offsetX = 0f
        rawTouchAccumulationX = 0f
    }

    private fun resetStateY() {
        offsetY = 0f
        rawTouchAccumulationY = 0f
    }

    private fun applyDragX(delta: Float) {
        if (delta == 0f || scrollRangeH == 0f) return
        rawTouchAccumulationX += delta
        rawTouchAccumulationX = rawTouchAccumulationX.coerceIn(-scrollRangeH, scrollRangeH)
        offsetX = OverscrollMath.dampedDistance(rawTouchAccumulationX, scrollRangeH)
    }

    private fun applyDragY(delta: Float) {
        if (delta == 0f || scrollRangeV == 0f) return
        rawTouchAccumulationY += delta
        rawTouchAccumulationY = rawTouchAccumulationY.coerceIn(-scrollRangeV, scrollRangeV)
        offsetY = OverscrollMath.dampedDistance(rawTouchAccumulationY, scrollRangeV)
    }

    // 阻尼曲线反函数：拖拽接管弹簧时，从当前位移反推等效手指位移
    private fun syncRawAccumulationFromOffsetX() {
        rawTouchAccumulationX = OverscrollMath.rawDistance(offsetX, scrollRangeH)
    }

    private fun syncRawAccumulationFromOffsetY() {
        rawTouchAccumulationY = OverscrollMath.rawDistance(offsetY, scrollRangeV)
    }

    private fun startSpringAnimationX(initialVelocity: Float = 0f) {
        if (abs(offsetX) <= offsetThreshold && initialVelocity == 0f) {
            resetStateX()
            return
        }
        animationJobX?.cancel()
        animationJobX = launchAnimation?.invoke {
            springEngineX.runSettleAnimation(
                startValue = offsetX,
                initialVelocity = initialVelocity,
                onFrame = { currentPos -> offsetX = currentPos },
                onSettle = { if (abs(offsetX) <= offsetThreshold) resetStateX() },
            )
        }
    }

    private fun startSpringAnimationY(initialVelocity: Float = 0f) {
        if (abs(offsetY) <= offsetThreshold && initialVelocity == 0f) {
            resetStateY()
            return
        }
        animationJobY?.cancel()
        animationJobY = launchAnimation?.invoke {
            springEngineY.runSettleAnimation(
                startValue = offsetY,
                initialVelocity = initialVelocity,
                onFrame = { currentPos -> offsetY = currentPos },
                onSettle = { if (abs(offsetY) <= offsetThreshold) resetStateY() },
            )
        }
    }

    private fun updateOverScrollState() {
        val state = getOverScrollState?.invoke() ?: return
        val isActive = isInProgress
        if (state.isOverScrollActive != isActive) {
            state.isOverScrollActive = isActive
        }
    }

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        if (source != NestedScrollSource.UserInput) {
            val consumed = performScroll(delta)
            updateOverScrollState()
            return consumed
        }

        // 拖拽接管前，先把弹簧停下并回写等效手指位移
        if (animationJobY?.isActive == true) syncRawAccumulationFromOffsetY()
        animationJobY?.cancel()
        if (animationJobX?.isActive == true) syncRawAccumulationFromOffsetX()
        animationJobX?.cancel()

        // 往中心回拖时优先吃掉越界位移
        var performScrollDeltaY = delta.y
        var extraConsumedY = 0f
        if (abs(offsetY) > offsetThreshold && delta.y != 0f && sign(delta.y) != sign(rawTouchAccumulationY)) {
            val canConsumeY = if (abs(rawTouchAccumulationY) <= abs(delta.y)) {
                -rawTouchAccumulationY
            } else {
                delta.y
            }
            if (abs(rawTouchAccumulationY) <= abs(delta.y)) {
                resetStateY()
                performScrollDeltaY = delta.y - canConsumeY
                extraConsumedY = canConsumeY
            } else {
                applyDragY(canConsumeY)
                performScrollDeltaY = 0f
                extraConsumedY = delta.y
            }
        }

        var performScrollDeltaX = delta.x
        var extraConsumedX = 0f
        if (abs(offsetX) > offsetThreshold && delta.x != 0f && sign(delta.x) != sign(rawTouchAccumulationX)) {
            val canConsumeX = if (abs(rawTouchAccumulationX) <= abs(delta.x)) {
                -rawTouchAccumulationX
            } else {
                delta.x
            }
            if (abs(rawTouchAccumulationX) <= abs(delta.x)) {
                resetStateX()
                performScrollDeltaX = delta.x - canConsumeX
                extraConsumedX = canConsumeX
            } else {
                applyDragX(canConsumeX)
                performScrollDeltaX = 0f
                extraConsumedX = delta.x
            }
        }

        val adjustedDelta = Offset(performScrollDeltaX, performScrollDeltaY)
        val scrollConsumed = performScroll(adjustedDelta)
        val scrollRemaining = adjustedDelta - scrollConsumed

        if (scrollRemaining.y != 0f) applyDragY(scrollRemaining.y)
        if (scrollRemaining.x != 0f) applyDragX(scrollRemaining.x)

        updateOverScrollState()

        return Offset(
            x = extraConsumedX + scrollConsumed.x + scrollRemaining.x,
            y = extraConsumedY + scrollConsumed.y + scrollRemaining.y,
        )
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        val isActiveY = abs(offsetY) > offsetThreshold
        val isActiveX = abs(offsetX) > offsetThreshold

        animationJobY?.cancel()
        animationJobX?.cancel()

        var performVelocity = velocity

        if (isActiveY && velocity.y != 0f) {
            if (sign(velocity.y) == sign(offsetY)) {
                // 同向 fling：速度全给弹簧，滚动拿 0
                startSpringAnimationY(velocity.y)
                performVelocity = Velocity(performVelocity.x, 0f)
            } else {
                startSpringAnimationY(velocity.y)
                performVelocity = Velocity(performVelocity.x, velocity.y / 2.13333f)
            }
        }

        if (isActiveX && velocity.x != 0f) {
            if (sign(velocity.x) == sign(offsetX)) {
                startSpringAnimationX(velocity.x)
                performVelocity = Velocity(0f, performVelocity.y)
            } else {
                startSpringAnimationX(velocity.x)
                performVelocity = Velocity(velocity.x / 2.13333f, performVelocity.y)
            }
        }

        val consumed = performFling(performVelocity)
        val remaining = performVelocity - consumed

        // 与 onPostFling 一致：剩余速度衰减后再进弹簧
        startSpringAnimationY(OverscrollMath.settleVelocity(remaining.y))
        startSpringAnimationX(OverscrollMath.settleVelocity(remaining.x))

        updateOverScrollState()
    }
}

private class NexioOverscrollEffectNode(
    val effect: NexioOverscrollEffect,
) : Modifier.Node(),
    CompositionLocalConsumerModifierNode,
    LayoutModifierNode {

    override fun onAttach() {
        super.onAttach()
        updateScrollRange()
        effect.launchAnimation = { block -> coroutineScope.launch(block = block) }
        effect.getOverScrollState = { currentValueOf(LocalOverScrollState) }
        effect.invalidateNodePlacement = { invalidatePlacement() }
    }

    override fun onDetach() {
        super.onDetach()
        effect.launchAnimation = null
        effect.getOverScrollState = null
        effect.invalidateNodePlacement = null
        effect.resetAll()
    }

    private var cachedDensity: Density? = null
    private var cachedWindowInfo: WindowInfo? = null

    private fun updateScrollRange() {
        val density = currentValueOf(LocalDensity)
        val windowInfo = currentValueOf(LocalWindowInfo)
        if (density == cachedDensity && windowInfo == cachedWindowInfo) return
        cachedDensity = density
        cachedWindowInfo = windowInfo
        with(density) {
            effect.scrollRangeV = windowInfo.containerDpSize.height.toPx()
            effect.scrollRangeH = windowInfo.containerDpSize.width.toPx()
        }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        updateScrollRange()
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                translationX = round(effect.offsetX)
                translationY = round(effect.offsetY)
                clip = true
            }
        }
    }
}
