// 开关交互与手感移植自 COUI Kit 的 Switch（third_party/coui-kit, Apache-2.0）。

package com.haooz.nexioui.switch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.internal.drawBlurredPathShadow
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.shape.ContinuousCapsule
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Clock

/**
 * COUI 开关：44×24 轨道、18dp thumb，跟手拖拽 + 甩动吸附 + thumb 拉伸形变。
 *
 * 宿主持有 checked；onCheckedChange 为 null 时仅展示状态、由整行持有点击与语义。
 * 颜色取自 [SwitchDefaults.colors]，与主题强调色一致。
 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    colors: SwitchColors = SwitchDefaults.colors(),
    enabled: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == LayoutDirection.Rtl

    val widthPx = with(density) { SwitchDefaults.width.toPx() }
    val thumbPx = with(density) { SwitchDefaults.thumbSize.toPx() }
    val paddingPx = with(density) { SwitchDefaults.thumbPadding.toPx() }
    val travel = (widthPx - paddingPx * 2f - thumbPx).coerceAtLeast(1f)

    val state = remember(travel, rtl, scope) { CouiSwitchMotion(checked, travel, rtl, scope) }
    val currentChecked by rememberUpdatedState(checked)
    val callback by rememberUpdatedState(onCheckedChange)
    val source = remember { MutableInteractionSource() }
    var touchPressed by remember { mutableStateOf(false) }
    var pendingChecked by remember { mutableStateOf(checked) }

    SideEffect {
        pendingChecked = checked
        state.onCommit = { target ->
            if (target != currentChecked) callback?.invoke(target)
        }
        state.update(checked, enabled)
        if (!enabled) touchPressed = false
    }
    DisposableEffect(state) {
        onDispose {
            touchPressed = false
            state.dispose()
        }
    }
    LaunchedEffect(state.settled, checked) {
        withFrameNanos { }
        state.reconcile(currentChecked)
    }

    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val hoverProgress = rememberSwitchFeedback(enabled && hovered, scope)
    val pressProgress = rememberSwitchFeedback(enabled && (pressed || touchPressed), scope)
    val focusProgress = rememberSwitchFeedback(enabled && focused, scope)

    val interactionInput = if (onCheckedChange != null) {
        Modifier.pointerInput(enabled, state, touchSlop) {
            if (!enabled) return@pointerInput
            val velocityTracker = VelocityTracker()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                velocityTracker.resetTracking()
                velocityTracker.addPosition(Clock.System.now().toEpochMilliseconds(), down.position)
                val accepted = state.begin(down.position.x, down.position.y)
                var dragging = false
                var complete = false
                touchPressed = true
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val distance = (change.position - down.position).getDistance()
                        if (accepted && !dragging && distance > touchSlop) dragging = true
                        if (dragging) {
                            change.consume()
                            if (change.pressed) {
                                velocityTracker.addPosition(Clock.System.now().toEpochMilliseconds(), change.position)
                                state.drag(change.position.x, change.position.y)
                            }
                        }
                        if (!change.pressed) {
                            if (dragging) {
                                val velocity = velocityTracker.calculateVelocity()
                                state.attach(velocity.x, velocity.y)
                            } else {
                                // 点击不 snap 归位，避免连点瞬移；toggle 运行中由 update 反向重定向
                                if (accepted) state.cancelDrag()
                                val target = !pendingChecked
                                pendingChecked = target
                                hapticFeedback.performHapticFeedback(
                                    if (target) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                                )
                                callback?.invoke(target)
                            }
                            complete = true
                            break
                        }
                        if (!dragging) {
                            val finalEvent = awaitPointerEvent(PointerEventPass.Final)
                            if (finalEvent.changes.any { it.id == down.id && it.isConsumed }) break
                        }
                    }
                } finally {
                    touchPressed = false
                    if (!complete && accepted) state.cancel()
                }
            }
        }
    } else Modifier

    val semanticsInput = Modifier.semantics {
        role = Role.Switch
        toggleableState = ToggleableState(checked)
        if (!enabled) disabled()
        if (onCheckedChange != null && enabled) {
            onClick {
                hapticFeedback.performHapticFeedback(
                    if (!checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                )
                onCheckedChange(!checked)
                true
            }
        }
    }

    val shape = ContinuousCapsule
    val thumbShadow = remember { ThumbShadowPath() }
    Box(
        modifier
            .then(semanticsInput)
            .then(interactionInput)
            .hoverable(interactionSource = source, enabled = enabled)
            .focusable(interactionSource = source, enabled = enabled)
            .semantics { if (state.stretch > 1f) stateDescription = "切换中" }
            .wrapContentSize(Alignment.Center)
            .requiredSize(SwitchDefaults.width, SwitchDefaults.height)
            .drawWithCache {
                val outlinePath = (shape.createOutline(size, layoutDirection, this) as? Outline.Generic)?.path
                val stroke = Stroke(3.dp.toPx().toInt() * 2f)
                onDrawBehind {
                    if (enabled) outlinePath?.let { path ->
                        clipPath(path, ClipOp.Difference) {
                            drawPath(
                                path,
                                colors.focusOutline.copy(alpha = colors.focusOutline.alpha * focusProgress()),
                                style = stroke,
                            )
                        }
                    }
                }
            }
            .clip(shape)
            .drawBehind {
                val track = if (enabled) {
                    lerp(colors.uncheckedTrackColor, colors.checkedTrackColor, state.fraction)
                } else if (checked) colors.disabledCheckedTrackColor else colors.disabledUncheckedTrackColor
                drawRect(track)
                val hover = hoverProgress()
                if (hover > 0f) drawRect(lerp(Color.Transparent, colors.hoverOverlay, hover))
                val press = pressProgress()
                if (press > 0f) drawRect(lerp(Color.Transparent, colors.pressOverlay, press))
            },
    ) {
        Canvas(Modifier.requiredSize(SwitchDefaults.width, SwitchDefaults.height)) {
            val thumbColor = if (enabled) {
                if (checked) colors.checkedThumbColor else colors.uncheckedThumbColor
            } else if (checked) colors.disabledCheckedThumbColor else colors.disabledUncheckedThumbColor
            val stretched = thumbPx * state.stretch
            val x = paddingPx + state.translation
            val y = (size.height - thumbPx) / 2f
            val radius = CornerRadius(thumbPx / 2f)
            if (enabled) {
                translate(x, y + ThumbShadowOffsetPx) {
                    drawBlurredPathShadow(
                        thumbShadow.path(Size(stretched, thumbPx)),
                        ThumbShadowColor,
                        ThumbShadowBlurPx,
                    )
                }
            }
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(x, y),
                size = Size(stretched, thumbPx),
                cornerRadius = radius,
            )
        }
    }
}

object SwitchDefaults {
    val width: Dp = 48.dp
    val height: Dp = 26.dp
    val thumbSize: Dp = 20.dp
    val thumbPadding: Dp = 3.dp
    val shape = ContinuousCapsule

    internal const val transformVelocityPx = 1000f
    internal const val transformDistancePx = 4f
    internal const val toggleResponse = .4f
    internal const val toggleBounce = .3f
    internal const val dragResponse = .3f
    internal const val dragBounce = 0f
    internal const val attachResponse = .4f
    internal const val attachBounce = .2f

    /** COUI 配色：轨道在强调色与半透明底之间，thumb 恒白。 */
    @Composable
    fun colors(): SwitchColors {
        val dark = NexioTheme.colorScheme.background.luminance() < .5f
        val accent = NexioTheme.colorScheme.primary
        return SwitchColors(
            checkedThumbColor = Color.White,
            uncheckedThumbColor = Color.White,
            disabledCheckedThumbColor = if (dark) Color(0x29FFFFFF) else Color(0x8AFFFFFF),
            disabledUncheckedThumbColor = if (dark) Color(0x29FFFFFF) else Color(0x8AFFFFFF),
            checkedTrackColor = accent,
            uncheckedTrackColor = if (dark) Color(0x40FFFFFF) else Color(0x29000000),
            disabledCheckedTrackColor = Color(accent.copy(alpha = .3f).value),
            disabledUncheckedTrackColor = if (dark) Color(0x26FFFFFF) else Color(0x14000000),
            hoverOverlay = if (dark) Color(0x26FFFFFF) else Color(0x14000000),
            pressOverlay = if (dark) Color(0x33FFFFFF) else Color(0x1F000000),
            focusOutline = accent.copy(alpha = .4f),
        )
    }
}

@Immutable
data class SwitchColors(
    val checkedThumbColor: Color,
    val uncheckedThumbColor: Color,
    val disabledCheckedThumbColor: Color,
    val disabledUncheckedThumbColor: Color,
    val checkedTrackColor: Color,
    val uncheckedTrackColor: Color,
    val disabledCheckedTrackColor: Color,
    val disabledUncheckedTrackColor: Color,
    val hoverOverlay: Color = Color.Transparent,
    val pressOverlay: Color = Color.Transparent,
    val focusOutline: Color = Color.Transparent,
)

private val ThumbShadowColor = Color(0x19000000)
private const val ThumbShadowBlurPx = 8f
private const val ThumbShadowOffsetPx = 4f

/** 静止时复用同一条 Path，只有 thumb 尺寸变化才重建。 */
private class ThumbShadowPath {
    private var previous = Size.Zero
    private var cached = Path()

    fun path(size: Size): Path {
        if (size != previous) {
            previous = size
            cached = Path().apply {
                addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(size.height / 2f)))
            }
        }
        return cached
    }
}

@Composable
private fun rememberSwitchFeedback(active: Boolean, scope: CoroutineScope): () -> Float {
    val effect = remember(scope) { SwitchFeedbackProgress(scope) }
    SideEffect { effect.setActive(active) }
    DisposableEffect(effect) { onDispose { effect.dispose() } }
    return { effect.value }
}

/** 对齐 COUI CouiStateProgress：response .3 临界阻尼，短按保底 70% 再退场。 */
private class SwitchFeedbackProgress(scope: CoroutineScope) {
    private val spring = CouiSpring(0f, scope)
    private var active = false
    private var pendingRelease = false
    val value: Float get() = spring.value / ProgressMax

    fun setActive(next: Boolean) {
        if (active == next) return
        active = next
        if (!next && spring.isRunning && spring.value <= MinimumPress) {
            pendingRelease = true
        } else {
            pendingRelease = false
            animate(if (next) ProgressMax else 0f)
        }
    }

    private fun animate(target: Float) {
        spring.animateTo(target, response = 0.3f, onEnd = {
            if (pendingRelease) {
                pendingRelease = false
                animate(0f)
            }
        })
    }

    fun dispose() {
        pendingRelease = false
        spring.cancelComplete()
    }

    private companion object {
        const val ProgressMax = 10_000f
        const val MinimumPress = 7_000f
    }
}
