package com.haooz.nexioui.slider

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 触感事件：到达两端或进入档位 / 锚点。 */
internal enum class SliderHapticEvent { Edge, Step }

/**
 * 滑块交互状态机。
 *
 * 逻辑值 [value] 与绘制位置 [position] 分离：拖动时 thumb 贴手，
 * 磁吸、外部赋值与惯性走弹簧，因此调用方回写不会打断手势。
 *
 * 模式优先级：锚点（[keyPointFractions] 非空）> 分段（[section]）> 连续。
 *
 * @param range 取值区间。
 * @param steps 等分档位数，不含两端。
 * @param section 是否启用分段吸附。
 * @param density 屏幕密度，用于 dp 阈值换算。
 * @param initialValue 初始值。
 * @param scope 动画作用域。
 * @param onValueChange 值变化回调。
 * @param onValueChangeFinished 手势结束回调。
 * @param feedback 触感回调。
 * @param hapticEffect 触感策略。
 * @param keyPointFractions 归一化到 0..1 的自定义锚点。
 * @param magnetThreshold 磁吸阈值下限（fraction），实际半径另有 8dp 像素兜底。
 * @param settleToKeyPoints 锚点即唯一合法值：抬手、甩动、点按都落到最近锚点。
 */
internal class SliderMotion(
    private val range: ClosedFloatingPointRange<Float>,
    private val steps: Int,
    private val section: Boolean,
    private val density: Float,
    initialValue: Float,
    private val scope: CoroutineScope,
    private val onValueChange: (Float) -> Unit,
    private val onValueChangeFinished: () -> Unit,
    private val feedback: (SliderHapticEvent) -> Unit,
    private val hapticEffect: SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
    private val keyPointFractions: List<Float> = emptyList(),
    private val magnetThreshold: Float = 0f,
    private val settleToKeyPoints: Boolean = false,
) {
    private val useKeyPoints = keyPointFractions.isNotEmpty()
    private val intervals = steps + 1
    private val span = range.endInclusive - range.start
    private val settleResponse = if (useKeyPoints) 0.35f else 0.3f
    private val settleBounce = if (useKeyPoints) 0.04f else 0f

    // 手势与分段拖动状态
    private var pressed = false
    private var dragging = false
    private var pendingRelease = false
    private var fastRelease = false
    private var bumpingEnd = false
    private var downX = 0f
    private var anchorSection = -1
    private var anchorX = 0f
    private var direction = 0
    private var lastFingerX = 0f
    private var fingerOffset = 0f
    private var dragAnimValue = 0f
    private var dragAnimStart = 0f
    private var dragAnimEnd = Float.NaN
    private var lastExternalValue = initialValue
    private var magnetZone = magnetThreshold

    // 触感状态
    private var edgeFeedbackTriggered = false
    private var lastStepValue = initialValue
    private var isAtKeyPoint = false

    private var transitionJob: Job? = null
    private var dragSnapJob: Job? = null
    private var flingJob: Job? = null

    /** 轨道加厚进度（0..100）。 */
    val height: SliderSpring = SliderSpring(0f, scope) {
        // 系统动画关闭时首帧即到终点，此时不能再重定向，交给结束监听收尾。
        if (pendingRelease && heightFraction >= 0.5f && height.value < 100f) {
            scope.launch { consumePendingRelease() }
        }
    }

    /** thumb 半径（dp）。 */
    val thumbRadius = SliderSpring(9f, scope)

    /** thumb 纹理层显现进度（0..100）。 */
    val texture = SliderSpring(0f, scope)

    /** thumb 白色覆盖层显现进度（0..100）。 */
    val thumbCover = SliderSpring(100f, scope)

    /** thumb 绘制位置（像素）。 */
    val position = SliderSpring(0f, scope)

    /** 到达末端时的闪光强度。 */
    val glitter = SliderSpring(0f, scope)

    /** 当前逻辑值。 */
    var value by mutableFloatStateOf(initialValue.coerceIn(range))
        private set

    /** 可用轨道长度（像素）。 */
    var length = 0f
        private set

    /** 轨道加厚进度，归一化到 0..1。 */
    val heightFraction: Float get() = (height.value / 100f).coerceIn(0f, 1f)

    /** 逻辑值对应的归一化位置。 */
    val fraction: Float get() = normalize(value)

    /** thumb 位置对应的归一化位置，供绘制进度与刻度使用。 */
    val visualFraction: Float get() = if (length > 0f) (position.value / length).coerceIn(0f, 1f) else fraction

    /** 响应外部赋值；值未变时只补一次 thumb 落位，避免量化回写反复重启动画。 */
    fun syncValue(externalValue: Float) {
        if (externalValue == lastExternalValue) return
        lastExternalValue = externalValue
        val next = externalValue.coerceIn(range)
        if (next == value || steps > 0 && index(normalize(next)) == index(fraction)) {
            val target = fraction * length
            if (!pressed && flingJob?.isActive != true && !position.isRunning &&
                length > 0f && abs(position.value - target) > 0.5f
            ) {
                position.animateTo(target, settleResponse, settleBounce)
            }
            return
        }
        cancelGesture()
        stopPositionAnimations()
        value = next
        position.animateTo(fraction * length, settleResponse, settleBounce)
    }

    /** 更新可用轨道长度，并重算磁吸半径。 */
    fun resize(nextLength: Float) {
        if (nextLength == length) return
        cancelGesture()
        stopPositionAnimations()
        length = nextLength.coerceAtLeast(0f)
        magnetZone = when {
            magnetThreshold <= 0f -> 0f
            length <= 0f -> magnetThreshold
            else -> maxOf(magnetThreshold, MagnetRadius * density / length).coerceAtMost(0.15f)
        }
        position.snapTo(fraction * length)
    }

    /** 按下：展开轨道、放大 thumb 并重置吸附状态。 */
    fun press(fingerX: Float) {
        stopPositionAnimations()
        resetAnchor()
        resetHaptic(value)
        pressed = true
        dragging = false
        pendingRelease = false
        bumpingEnd = false
        downX = fingerX
        height.animateTo(100f, 0.5f, onEnd = ::consumePendingRelease)
        thumbRadius.animateTo(10f, 0.5f, 0.3f, minimumChange = 1f / density)
        texture.animateTo(100f, 0.3f)
        thumbCover.snapTo(100f)
        thumbCover.animateTo(0f, 0.5f)
    }

    /** 拖动：锚点模式按磁力软吸附，分段模式按段吸附，其余贴手。 */
    fun drag(fingerX: Float, velocity: Float) {
        if (!pressed || length <= 0f) return
        dragging = true
        val x = fingerX.coerceIn(0f, length)
        if (section) {
            dragSection(x)
        } else {
            val raw = x / length
            val anchorIndex = keyPointIndex(raw)
            if (anchorIndex >= 0) {
                // 软磁吸：越靠近锚点拉力越强，thumb 与数值一起被拉过去，阈值边缘自然过渡到跟手。
                val anchor = keyPointFractions[anchorIndex]
                val t = (abs(anchor - raw) / magnetZone).coerceIn(0f, 1f)
                val strength = (1f - t) * (1f - t)
                val blended = (raw + (anchor - raw) * strength).coerceIn(0f, 1f)
                setFraction(blended, vibrate = true, ignoreKeyPoint = true, magnetStrength = strength)
                position.snapTo(blended * length)
            } else {
                setFraction(raw, vibrate = true)
                position.snapTo(x)
            }
        }
        if (x < length) bumpingEnd = false
        if (!section && x >= length && velocity > 0f && !bumpingEnd) startGlitter(velocity)
    }

    /** 抬手：未拖动按点按处理，拖动后按模式落位。 */
    fun release(fingerX: Float, velocity: Float) {
        if (!pressed) return
        releaseAppearance()
        pressed = false
        if (!dragging) {
            val target = quantize(normalizePosition(fingerX))
            if (section) {
                setFraction(target, vibrate = true)
                animateSection(target * length, 200L)
            } else {
                // 点按过程中连续喂值，避免 thumb 移动时数字先跳到终点。
                position.animateTo(target * length, settleResponse, settleBounce, onFrame = {
                    setFraction(normalizePosition(it), vibrate = false, truncate = true)
                }) {
                    setFraction(target, vibrate = false)
                    onValueChangeFinished()
                }
            }
            return
        }
        dragging = false
        if (section) {
            releaseSection(fingerX)
            return
        }
        val raw = normalizePosition(fingerX)
        if (settleToKeyPoints && useKeyPoints) {
            // 锚点即全部可选值：按甩动投影直接落到最近锚点。
            val projected = (raw + velocity / FlingDecay / length).coerceIn(0f, 1f)
            settleTo(nearestKeyPoint(projected))
            return
        }
        if (steps > 0) {
            // 离散档位没有中间值：抬手立刻吸到最近档位，不保留惯性。
            settleTo(quantize(raw))
            return
        }
        val snapped = keyPointIndex(raw)
        setFraction(raw, vibrate = true)
        if (snapped >= 0) {
            settleTo(fraction)
        } else {
            position.snapTo(fingerX.coerceIn(0f, length))
            if (abs(velocity) >= 100f) startFling(velocity.coerceIn(-8000f, 8000f))
            else onValueChangeFinished()
        }
    }

    /** 取消手势并复原外观。 */
    fun cancelGesture() {
        if (pressed) releaseAppearance()
        pressed = false
        dragging = false
        fastRelease = false
        stopPositionAnimations()
        resetAnchor()
    }

    /** 响应无障碍 / 键盘等非触摸操作，值会量化到合法档位。 */
    fun setFromAction(nextValue: Float) {
        cancelGesture()
        setFraction(quantize(normalize(nextValue)), vibrate = true)
        position.snapTo(fraction * length)
        onValueChangeFinished()
    }

    /** 释放所有动画协程。 */
    fun dispose() {
        pressed = false
        pendingRelease = false
        stopPositionAnimations()
        listOf(height, thumbRadius, texture, thumbCover, position, glitter).forEach { it.cancel() }
    }

    private fun releaseAppearance() {
        if (height.isRunning && heightFraction < 0.5f) {
            pendingRelease = true
        } else {
            pendingRelease = false
            height.animateTo(0f, 0.5f)
        }
        thumbRadius.animateTo(9f, 0.5f, minimumChange = 1f / density)
        texture.animateTo(0f, 0.3f)
        thumbCover.animateTo(100f, 0.3f)
    }

    private fun consumePendingRelease() {
        if (!pendingRelease) return
        pendingRelease = false
        height.animateTo(0f, 0.5f)
    }

    private fun releaseSection(fingerX: Float) {
        if (dragSnapJob?.isActive == true) {
            fastRelease = true
            return
        }
        val segment = length / intervals
        val target = (index(fraction) + ((fingerX.coerceIn(0f, length) - fraction * length) / segment).toInt())
            .coerceIn(0, intervals)
        snapSection(target)
        resetAnchor()
    }

    /** thumb 弹到目标档位并结束手势；距离极小时直接落位。 */
    private fun settleTo(targetFraction: Float, response: Float = settleResponse, bounce: Float = settleBounce) {
        setFraction(targetFraction, vibrate = true)
        val target = fraction * length
        if (abs(position.value - target) < 0.5f) {
            position.snapTo(target)
            onValueChangeFinished()
        } else {
            position.animateTo(target, response, bounce) { onValueChangeFinished() }
        }
    }

    private fun dragSection(x: Float) {
        val segment = length / intervals
        if (anchorSection < 0) {
            anchorSection = index(normalizePosition(downX))
            anchorX = anchorSection * segment
            setFraction(anchorSection.toFloat() / intervals, vibrate = true)
            position.snapTo(anchorX)
            direction = if (x < anchorX) -1 else 1
            lastFingerX = x
            return
        }
        val nextDirection = when {
            x > lastFingerX + 0.5f -> 1
            x < lastFingerX - 0.5f -> -1
            else -> 0
        }
        if (nextDirection != 0 && nextDirection == -direction) {
            direction = nextDirection
            if (anchorSection != index(fraction)) {
                anchorSection = index(fraction)
                anchorX = anchorSection * segment
                dragAnimValue = 0f
            }
            dragSnapJob?.cancel()
        }
        fingerOffset = x - anchorX
        val crossed = ((fingerOffset + if (fingerOffset >= 0f) 0.1f else -0.1f) / segment).toInt()
        val offset = crossed * segment
        if ((anchorSection + crossed).coerceIn(0, intervals) != index(fraction)) {
            startDragSnap(anchorX + offset)
        } else if (dragSnapJob?.isActive != true) {
            position.snapTo((anchorX + offset + (fingerOffset - offset) * 0.6f).coerceIn(0f, length))
        }
        lastFingerX = x
    }

    private fun startDragSnap(target: Float) {
        if (position.value == target || dragSnapJob?.isActive == true && dragAnimEnd == target) return
        dragSnapJob?.cancel()
        dragAnimStart = anchorX
        dragAnimEnd = target
        val start = dragAnimValue
        dragSnapJob = scope.launch {
            animate(start, target - anchorX, animationSpec = tween(100, easing = DragEasing)) { animated, _ ->
                dragAnimValue = animated
                val x = (dragAnimStart + dragAnimValue * 0.4f + fingerOffset * 0.6f).coerceIn(0f, length)
                position.snapTo(x)
                val selected = if (dragAnimEnd > dragAnimStart) (x / (length / intervals)).roundToInt()
                else ceil(x / (length / intervals)).toInt()
                setFraction(selected.coerceIn(0, intervals).toFloat() / intervals, vibrate = true)
            }
            if (fastRelease) {
                fastRelease = false
                val targetSection = index(normalizePosition(lastFingerX))
                resetAnchor()
                snapSection(targetSection)
            }
        }
    }

    private fun snapSection(target: Int) {
        setFraction(target.toFloat() / intervals, vibrate = false)
        val x = fraction * length
        if (abs(position.value - x) < 0.5f) {
            position.snapTo(x)
            onValueChangeFinished()
        } else {
            animateSection(x, 150L)
        }
    }

    private fun animateSection(target: Float, durationMs: Long) {
        transitionJob?.cancel()
        val start = position.value
        transitionJob = scope.launch {
            // 与原厂 ValueAnimator 默认的 AccelerateDecelerate 插值器保持一致。
            animate(start, target, animationSpec = tween(durationMs.toInt(), easing = AccelerateDecelerateEasing)) { v, _ ->
                position.snapTo(v)
            }
            onValueChangeFinished()
        }
    }

    private fun startFling(initialVelocity: Float) {
        var velocity = initialVelocity
        var x = position.value
        flingJob?.cancel()
        flingJob = scope.launch {
            var previous = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                // 用真实帧间隔积分，60Hz / 120Hz 的时间域衰减一致。
                val dt = ((now - previous) / 1_000_000_000.0).toFloat().coerceIn(1f / 240f, 1f / 30f)
                previous = now
                // Oplus World.solve 的无约束单轴分支：v /= 1 + 15dt，总滑行距离 = 初速 / 15。
                velocity /= 1f + FlingDecay * dt
                x += velocity * dt
                val clamped = x.coerceIn(0f, length)
                if (x >= length && !bumpingEnd) startGlitter(velocity)
                position.snapTo(clamped)
                val current = normalizePosition(clamped)
                setFraction(current, vibrate = false, ignoreKeyPoint = true)
                // 投影落点已确定在磁吸范围内时立即收档，不必滑到完全停下。
                val anchor = keyPointIndex(current)
                if (anchor >= 0) {
                    val projected = normalizePosition((x + velocity / FlingDecay).coerceIn(0f, length))
                    if (keyPointIndex(projected) == anchor) {
                        captureAnchor(anchor, CaptureResponse, CaptureBounce)
                        return@launch
                    }
                }
                if (x != clamped || abs(velocity) < 0.1f) {
                    val endAnchor = keyPointIndex(normalizePosition(clamped))
                    if (endAnchor >= 0) captureAnchor(endAnchor) else onValueChangeFinished()
                    return@launch
                }
            }
        }
    }

    /** 惯性结束/提前落点时把 thumb 收回锚点。 */
    private fun captureAnchor(anchor: Int, response: Float = settleResponse, bounce: Float = settleBounce) {
        val target = keyPointFractions[anchor]
        setFraction(target, vibrate = true)
        position.animateTo(target * length, response, bounce) { onValueChangeFinished() }
    }

    private fun startGlitter(velocity: Float) {
        bumpingEnd = true
        glitter.animateTo(0f, 0.6f, velocity = abs(velocity))
    }

    private fun setFraction(
        next: Float,
        vibrate: Boolean,
        truncate: Boolean = false,
        ignoreKeyPoint: Boolean = false,
        magnetStrength: Float = -1f,
    ) {
        val raw = next.coerceIn(0f, 1f)
        val keyPoint = if (ignoreKeyPoint) -1 else keyPointIndex(raw)
        val selected = when {
            keyPoint >= 0 -> keyPointFractions[keyPoint]
            steps > 0 || section -> {
                val i = if (truncate) (raw * intervals).toInt() else index(raw)
                i.toFloat() / intervals
            }
            else -> raw
        }
        val nextValue = range.start + span * selected
        if (vibrate) handleHaptic(nextValue, selected, magnetStrength)
        if (value == nextValue) return
        value = nextValue
        onValueChange(nextValue)
    }

    private fun stopPositionAnimations() {
        fastRelease = false
        transitionJob?.cancel()
        transitionJob = null
        dragSnapJob?.cancel()
        dragSnapJob = null
        position.cancelComplete()
        flingJob?.cancel()
        flingJob = null
    }

    private fun resetAnchor() {
        fastRelease = false
        dragSnapJob?.cancel()
        anchorSection = -1
        anchorX = 0f
        direction = 0
        fingerOffset = 0f
        dragAnimValue = 0f
        dragAnimStart = 0f
        dragAnimEnd = Float.NaN
        lastFingerX = 0f
    }

    private fun normalize(next: Float): Float = if (span > 0f) ((next - range.start) / span).coerceIn(0f, 1f) else 0f

    private fun normalizePosition(x: Float): Float = if (length > 0f) (x / length).coerceIn(0f, 1f) else fraction

    private fun index(next: Float): Int = (next * intervals).roundToInt().coerceIn(0, intervals)

    private fun resetHaptic(currentValue: Float) {
        edgeFeedbackTriggered = false
        lastStepValue = currentValue
        isAtKeyPoint = false
    }

    /** 边缘震一次；锚点 / 档位带迟滞，进入时震一次、离开后失效。 */
    private fun handleHaptic(currentValue: Float, selected: Float, magnetStrength: Float = -1f) {
        if (hapticEffect == SliderHapticEffect.None) return
        val atEdge = selected <= 0f || selected >= 1f
        if (atEdge) {
            if (!edgeFeedbackTriggered) {
                feedback(SliderHapticEvent.Edge)
                edgeFeedbackTriggered = true
            }
        } else {
            edgeFeedbackTriggered = false
        }
        if (hapticEffect != SliderHapticEffect.Step) return
        if (useKeyPoints) {
            val atKeyPoint = if (magnetStrength >= 0f) {
                magnetStrength >= 0.85f
            } else {
                keyPointFractions.minOf { abs(it - normalize(currentValue)) } < 0.005f
            }
            if (atKeyPoint && !isAtKeyPoint && !atEdge) feedback(SliderHapticEvent.Step)
            isAtKeyPoint = atKeyPoint
        } else if ((steps > 0 || section) && currentValue != lastStepValue && !atEdge) {
            feedback(SliderHapticEvent.Step)
            lastStepValue = currentValue
        }
    }

    /** 距 [raw] 最近且落在磁吸半径内的锚点下标；未命中返回 -1。 */
    private fun keyPointIndex(raw: Float): Int {
        if (!useKeyPoints || magnetZone <= 0f) return -1
        val best = nearestKeyPointIndex(raw)
        return if (abs(keyPointFractions[best] - raw) <= magnetZone) best else -1
    }

    /** 距 [raw] 最近的锚点值；无锚点时原样返回。 */
    private fun nearestKeyPoint(raw: Float): Float {
        if (!useKeyPoints) return raw
        return keyPointFractions[nearestKeyPointIndex(raw)]
    }

    private fun nearestKeyPointIndex(raw: Float): Int {
        var best = 0
        var bestDistance = Float.MAX_VALUE
        for (i in keyPointFractions.indices) {
            val distance = abs(keyPointFractions[i] - raw)
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return best
    }

    private fun quantize(next: Float): Float {
        if (settleToKeyPoints && useKeyPoints) return nearestKeyPoint(next)
        val keyPoint = keyPointIndex(next)
        if (keyPoint >= 0) return keyPointFractions[keyPoint]
        return if (steps > 0 || section) index(next).toFloat() / intervals else next
    }

    private companion object {
        /** 磁吸半径下限：与取值范围、轨道长度无关的固定像素半径。 */
        const val MagnetRadius = 8f

        /** 惯性衰减率：`v /= 1 + 15dt`，总滑行距离 = 初速 / 15。 */
        const val FlingDecay = 15f

        /** 甩动中途收档的短促落位弹簧。 */
        const val CaptureResponse = 0.25f
        const val CaptureBounce = 0.06f

        /** 吸附缓动，等价 `PathInterpolator(0, 0, 0.25, 1)`。 */
        val DragEasing = CubicBezierEasing(0f, 0f, 0.25f, 1f)

        /** Android `ValueAnimator` 默认插值器。 */
        val AccelerateDecelerateEasing = object : Easing {
            override fun transform(fraction: Float): Float =
                (cos((fraction + 1f) * PI) / 2.0 + 0.5).toFloat()
        }
    }
}
