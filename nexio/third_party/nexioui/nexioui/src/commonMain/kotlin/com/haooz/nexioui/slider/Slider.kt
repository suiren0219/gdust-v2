package com.haooz.nexioui.slider

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.interaction.InteractiveHighlight
import com.haooz.nexioui.theme.NexioTheme
import kotlin.math.abs

/** 轨道左右各留 16dp，thumb 与光效不会被裁切。 */
private val TrackInset = 16.dp

/** 开始移动跟手光效所需的水平位移。 */
private val HighlightDragSlop = 2.dp

/**
 * 可吸附的跨平台滑块。
 *
 * 手势、物理动画与绘制全部在 commonMain，各平台共用；跟手光效复用 [InteractiveHighlight]。
 * 逻辑值与绘制位置分离：拖动时 thumb 贴手，吸附、外部赋值与惯性走弹簧，
 * 因此调用方在 [onValueChange] 中连续回写不会打断手势。
 *
 * @param value 当前值。
 * @param onValueChange 值变化回调，拖动过程中连续触发。
 * @param modifier 应用于整条滑条的修饰符。
 * @param enabled 是否可交互；false 时只读并置灰。
 * @param valueRange 取值区间。
 * @param steps 等分档位数（不含两端），0 表示连续；与 [keyPoints] 同时给出时以 [keyPoints] 为准。
 * @param onValueChangeFinished 手势结束回调。
 * @param showKeyPoints 是否绘制锚点刻度。
 * @param keyPoints 自定义锚点（[valueRange] 内的绝对值）。
 * @param magnetThreshold 磁吸阈值下限（占 [valueRange] 比例），实际半径另有 8dp 像素兜底。
 * @param settleToKeyPoints 锚点即唯一合法值：抬手、甩动、点按都直接落到最近锚点。
 * @param hapticEffect 触感策略。
 * @param colors 配色覆盖；null 时使用 [SliderDefaults.colors]。
 * @param shape 轨道形状，同时用于裁剪跟手光效。
 * @param dark 是否深色，影响跟手光效强度。
 */
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    showKeyPoints: Boolean = false,
    keyPoints: List<Float>? = null,
    magnetThreshold: Float = 0.02f,
    settleToKeyPoints: Boolean = false,
    hapticEffect: SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
    colors: SliderColors? = null,
    shape: Shape = SliderDefaults.TrackShape,
    dark: Boolean = NexioTheme.isDark,
) {
    val rangeStart = valueRange.start
    val rangeEnd = valueRange.endInclusive
    // 锚点先归一化到 0..1 交给磁吸状态机；不能折算成等分段数，否则单锚点会消失、多锚点会错位。
    val keyPointFractions = remember(keyPoints, rangeStart, rangeEnd) {
        val span = rangeEnd - rangeStart
        if (keyPoints.isNullOrEmpty() || span <= 0f) {
            emptyList()
        } else {
            keyPoints.mapNotNull { point ->
                val fraction = (point - rangeStart) / span
                if (fraction.isFinite() && fraction in 0f..1f) fraction else null
            }.distinct().sorted()
        }
    }
    SliderCore(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        colors = colors,
        dark = dark,
        shape = shape,
        valueRange = valueRange,
        steps = steps,
        showTickMarks = showKeyPoints,
        hapticEffect = hapticEffect,
        keyPointFractions = keyPointFractions,
        magnetThreshold = magnetThreshold,
        settleToKeyPoints = settleToKeyPoints,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished ?: {},
    )
}

/**
 * 滑条主体：装配手势、无障碍、键盘与绘制。
 *
 * 三种模式互斥：[keyPointFractions] 非空为锚点模式，[showTickMarks] 为分段模式，其余为连续模式。
 */
@Composable
private fun SliderCore(
    modifier: Modifier = Modifier,
    value: Float,
    onValueChange: (Float) -> Unit,
    colors: SliderColors? = null,
    dark: Boolean = NexioTheme.isDark,
    shape: Shape = SliderDefaults.TrackShape,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    showTickMarks: Boolean = false,
    hapticEffect: SliderHapticEffect = SliderDefaults.DefaultHapticEffect,
    keyPointFractions: List<Float> = emptyList(),
    magnetThreshold: Float = 0f,
    settleToKeyPoints: Boolean = false,
    enabled: Boolean = true,
    onValueChangeFinished: () -> Unit = {},
) {
    require(steps >= 0 && steps < Int.MAX_VALUE - 1) { "steps 超出可用范围" }
    require(value.isFinite()) { "value 必须是有限值" }
    require(
        valueRange.start.isFinite() && valueRange.endInclusive.isFinite() &&
            valueRange.endInclusive >= valueRange.start,
    ) { "valueRange 非法" }
    val useKeyPoints = keyPointFractions.isNotEmpty()
    val coreSteps = if (useKeyPoints) 0 else steps
    val useSection = !useKeyPoints && showTickMarks
    val interactive = enabled && valueRange.endInclusive > valueRange.start
    val resolvedColors = colors ?: SliderDefaults.colors(enabled)
    val density = LocalDensity.current.density
    val layoutDirection = LocalLayoutDirection.current
    val rtl = layoutDirection == LayoutDirection.Rtl
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val onChange by rememberUpdatedState(onValueChange)
    val onFinished by rememberUpdatedState(onValueChangeFinished)
    // 闭区间没有 equals，直接当 key 会让状态机每次重组都重建。
    val rangeStart = valueRange.start
    val rangeEnd = valueRange.endInclusive
    val motion = remember(
        rangeStart, rangeEnd, coreSteps, useSection, density, keyPointFractions, magnetThreshold,
        settleToKeyPoints, hapticEffect, scope,
    ) {
        SliderMotion(
            range = valueRange,
            steps = coreSteps,
            section = useSection,
            density = density,
            initialValue = value,
            scope = scope,
            onValueChange = { onChange(it) },
            onValueChangeFinished = { onFinished() },
            feedback = { event ->
                hapticFeedback.performHapticFeedback(
                    when (event) {
                        SliderHapticEvent.Edge -> HapticFeedbackType.GestureThresholdActivate
                        SliderHapticEvent.Step -> HapticFeedbackType.TextHandleMove
                    },
                )
            },
            hapticEffect = hapticEffect,
            keyPointFractions = keyPointFractions,
            magnetThreshold = magnetThreshold,
            settleToKeyPoints = settleToKeyPoints,
        )
    }
    val touch = remember(scope, density) { SliderTouchMotion(scope, density) }
    val renderer = remember(density, shape, layoutDirection) { SliderRenderer(density, shape, layoutDirection) }
    val spotlight = remember(scope, dark) { InteractiveHighlight(animationScope = scope, isDark = { dark }) }
    SideEffect {
        motion.syncValue(value)
        if (!interactive) {
            motion.cancelGesture()
            touch.release(Offset.Zero)
        }
    }
    DisposableEffect(motion) { onDispose { motion.dispose() } }
    DisposableEffect(touch) { onDispose { touch.dispose() } }
    val span = rangeEnd - rangeStart
    val keyIncrement = span / if (coreSteps > 0 || useSection) coreSteps + 1 else 100
    Box(
        modifier
            .defaultMinSize(minWidth = 144.dp, minHeight = 48.dp)
            .progressSemantics(value.coerceIn(valueRange), valueRange, coreSteps)
            .semantics {
                if (!interactive) disabled()
                setProgress { next ->
                    if (!interactive || !next.isFinite()) {
                        false
                    } else {
                        motion.setFromAction(next)
                        true
                    }
                }
            }
            .sliderKeyEvents(motion, interactive, rtl, keyIncrement, valueRange)
            .focusable(interactive)
            .onSizeChanged { motion.resize(it.width - 2f * TrackInset.value * density) }
            .sliderGestures(motion, touch, interactive, rtl),
    ) {
        Canvas(
            Modifier.matchParentSize()
                .graphicsLayer { translationX = touch.translationX.value }
                .then(if (interactive) spotlight.gestureModifier else Modifier),
        ) {
            renderer.render(
                scope = this,
                height = size.height,
                motion = motion,
                colors = resolvedColors,
                enabled = interactive,
                rtl = rtl,
                tickCount = if (useSection) coreSteps + 1 else 0,
                tickFractions = if (useKeyPoints && showTickMarks) keyPointFractions else null,
            )
            renderer.clipTrack(this) { spotlight.drawHighlight(this@clipTrack) }
        }
    }
}

/** 方向键 / Home / End 调整数值，RTL 下左右互换。 */
private fun Modifier.sliderKeyEvents(
    motion: SliderMotion,
    interactive: Boolean,
    rtl: Boolean,
    keyIncrement: Float,
    range: ClosedFloatingPointRange<Float>,
): Modifier = onKeyEvent { event ->
    if (!interactive || event.type != KeyEventType.KeyDown) return@onKeyEvent false
    val next = when (event.key) {
        Key.DirectionLeft -> motion.value + keyIncrement * if (rtl) 1f else -1f
        Key.DirectionRight -> motion.value + keyIncrement * if (rtl) -1f else 1f
        Key.DirectionUp -> motion.value + keyIncrement
        Key.DirectionDown -> motion.value - keyIncrement
        Key.MoveHome -> range.start
        Key.MoveEnd -> range.endInclusive
        else -> return@onKeyEvent false
    }
    motion.setFromAction(next)
    true
}

/** 拖动 / 点按 / 多指切换。竖向滑动超过 slop 后交还父级滚动，一旦判定为拖动便不再被横向翻页抢占。 */
private fun Modifier.sliderGestures(
    motion: SliderMotion,
    touch: SliderTouchMotion,
    interactive: Boolean,
    rtl: Boolean,
): Modifier = pointerInput(motion, touch, interactive, rtl) {
    if (!interactive) return@pointerInput
    val touchSlop = viewConfiguration.touchSlop
    val inset = TrackInset.toPx()
    val highlightSlop = HighlightDragSlop.toPx()
    awaitEachGesture {
        val down = awaitFirstDown()
        if (motion.length <= 0f) return@awaitEachGesture
        val tracker = VelocityTracker()
        var active = down.id
        var pointerOffset = 0f
        var origin = down.position
        var dragging = false
        var completed = false
        fun fingerX(x: Float): Float {
            val local = x - pointerOffset - touch.translationX.value - inset
            return if (rtl) motion.length - local else local
        }
        fun velocityX(): Float =
            tracker.calculateVelocity().x.coerceIn(-8000f, 8000f) * if (rtl) -1f else 1f
        try {
            tracker.addChange(down)
            motion.press(fingerX(down.position.x))
            touch.begin(down.position, size)
            while (true) {
                val event = awaitPointerEvent()
                if (event.isSliderTouchCancelled()) break
                val tracked = event.changes.firstOrNull { it.id == active } ?: break
                // 多指：把坐标系迁移到新落下的手指，避免 thumb 跳变。
                val next = if (!tracked.pressed) event.changes.firstOrNull { it.pressed } else null
                if (next != null) {
                    pointerOffset += next.position.x - tracked.position.x
                    origin += next.position - tracked.position
                    touch.switchPointer(tracked.position, next.position)
                    active = next.id
                    tracker.resetTracking()
                    tracker.addChange(next)
                    continue
                }
                if (tracked.isConsumed) break
                if (!tracked.pressed) {
                    val local = fingerX(tracked.position.x)
                    val inside = tracked.position.x in 0f..size.width.toFloat() &&
                        tracked.position.y in 0f..size.height.toFloat()
                    if (dragging || inside) {
                        val velocity = velocityX()
                        if (dragging) motion.drag(local, velocity)
                        motion.release(local, velocity)
                        tracked.consume()
                    } else {
                        motion.cancelGesture()
                    }
                    touch.release(Offset(velocityX() * 0.0003f, 0f))
                    completed = true
                    break
                }
                tracker.addChange(tracked)
                val delta = tracked.position - origin
                // 竖向意图优先：一旦超过 slop 就放弃本次手势，避免接管列表滚动。
                if (!dragging && abs(delta.y) > touchSlop && abs(delta.y) > abs(delta.x)) break
                if (abs(delta.x) >= highlightSlop) touch.move(Offset(tracked.position.x, down.position.y))
                if (dragging || abs(delta.x) > touchSlop) {
                    dragging = true
                    motion.drag(fingerX(tracked.position.x), velocityX())
                    tracked.consume()
                }
            }
        } finally {
            if (!completed) {
                motion.cancelGesture()
                touch.release(Offset.Zero)
            }
        }
    }
}

/** 回放历史点，与 AndroidX 把批量 `MotionEvent` 全部喂入的行为一致。 */
private fun VelocityTracker.addChange(change: PointerInputChange) {
    change.historical.forEach { addPosition(it.uptimeMillis, it.position) }
    addPosition(change.uptimeMillis, change.position)
}
