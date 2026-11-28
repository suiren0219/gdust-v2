package com.haooz.nexioui.checkbox

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.haooz.nexioui.internal.rememberSinkScale
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.shape.ContinuousCapsule

/**
 * 复选框，支持开、关与半选三态。
 *
 * 状态由调用方维护；对勾按线段长度裁剪绘制，半选时两段向中线收拢成一条短横。
 *
 * @param state 当前三态。
 * @param onClick 点击回调；为 null 时不可交互。
 * @param modifier 外层修饰符。
 * @param colors 底色与对勾颜色。
 * @param enabled 是否可用。
 */
@Composable
fun Checkbox(
    state: ToggleableState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    colors: CheckboxColors = CheckboxDefaults.colors(),
    enabled: Boolean = true,
) {
    val currentOnClickState = rememberUpdatedState(onClick)
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val sinkScale by rememberSinkScale(interactionSource, enabled)

    val transition = updateTransition(state, label = "CheckboxTransition")

    val backgroundColorState = transition.animateColor(
        transitionSpec = { tween(durationMillis = 300, easing = FastOutSlowInEasing) },
        label = "BackgroundColor",
    ) {
        if (it != ToggleableState.Off) colors.checkedBackgroundColor(enabled) else colors.uncheckedBackgroundColor(enabled)
    }

    val foregroundColorState = transition.animateColor(
        transitionSpec = { tween(durationMillis = 300, easing = FastOutSlowInEasing) },
        label = "ForegroundColor",
    ) {
        if (it != ToggleableState.Off) colors.checkedForegroundColor(enabled) else colors.uncheckedForegroundColor(enabled)
    }

    val checkAlphaState = transition.animateFloat(
        transitionSpec = {
            if (targetState != ToggleableState.Off) {
                tween(durationMillis = 10, easing = FastOutSlowInEasing)
            } else {
                tween(durationMillis = 150, easing = FastOutSlowInEasing)
            }
        },
        label = "CheckAlpha",
    ) { if (it != ToggleableState.Off) 1f else 0f }

    val checkStartTrimState = transition.animateFloat(
        transitionSpec = {
            if (targetState != ToggleableState.Off) {
                tween(durationMillis = 200, easing = FastOutSlowInEasing)
            } else {
                keyframes {
                    durationMillis = 300
                    0.1f at 300
                }
            }
        },
        label = "CheckStartTrim",
    ) { if (it != ToggleableState.Off) 0.186f else 0.1f }

    val checkEndTrimState = transition.animateFloat(
        transitionSpec = {
            if (targetState != ToggleableState.Off) {
                keyframes {
                    durationMillis = 300
                    0.85f at 200 using FastOutSlowInEasing
                    0.803f at 300 using FastOutSlowInEasing
                }
            } else {
                keyframes {
                    durationMillis = 300
                    0.1f at 300
                }
            }
        },
        label = "CheckEndTrim",
    ) { if (it != ToggleableState.Off) 0.803f else 0.1f }

    val crossCenterGravitationState = transition.animateFloat(
        transitionSpec = {
            if (targetState == ToggleableState.Indeterminate) {
                tween(durationMillis = 200, easing = FastOutSlowInEasing)
            } else {
                tween(durationMillis = 150, easing = FastOutSlowInEasing)
            }
        },
        label = "CrossCenterGravitation",
    ) { if (it == ToggleableState.Indeterminate) 1f else 0f }

    val capsuleShape = remember { ContinuousCapsule() }
    val checkPath = remember { Path() }

    val finalModifier = if (onClick != null) {
        Modifier.triStateToggleable(
            state = state,
            onClick = {
                currentOnClickState.value?.invoke()
                hapticFeedback.performHapticFeedback(
                    when (state) {
                        ToggleableState.Off -> HapticFeedbackType.ToggleOn
                        ToggleableState.On -> HapticFeedbackType.ToggleOff
                        ToggleableState.Indeterminate -> HapticFeedbackType.SegmentTick
                    },
                )
            },
            enabled = enabled,
            role = Role.Checkbox,
            interactionSource = interactionSource,
            indication = null,
        )
    } else {
        Modifier.semantics {
            role = Role.Checkbox
            toggleableState = state
            if (!enabled) disabled()
        }
    }

    Box(
        modifier = modifier
            .wrapContentSize(Alignment.Center)
            .requiredSize(26.dp)
            .graphicsLayer {
                scaleX = sinkScale
                scaleY = sinkScale
            }
            .clip(capsuleShape)
            .drawWithCache {
                val viewportSize = 23f
                val strokeWidth = size.width * 0.09f
                val centerX = size.width / 2
                val centerY = size.height / 2
                val viewportCenterX = viewportSize / 2
                val viewportCenterY = viewportSize / 2

                val startPoint = Offset(
                    centerX + ((5f - viewportCenterX) / viewportSize * size.width),
                    centerY + ((9.4f - viewportCenterY) / viewportSize * size.height),
                )
                val middlePoint = Offset(
                    centerX + ((10.3f - viewportCenterX) / viewportSize * size.width),
                    centerY + ((14.9f - viewportCenterY) / viewportSize * size.height),
                )
                val endPoint = Offset(
                    centerX + ((17.9f - viewportCenterX) / viewportSize * size.width),
                    centerY + ((5.1f - viewportCenterY) / viewportSize * size.height),
                )

                val cache = CheckmarkCache(
                    startPoint,
                    middlePoint,
                    endPoint,
                    centerX,
                    centerY,
                    strokeWidth,
                )

                val stroke = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    miter = 10.0f,
                )

                onDrawBehind {
                    drawCircle(backgroundColorState.value)
                    drawTrimmedCheckmark(
                        color = foregroundColorState.value,
                        alpha = checkAlphaState.value,
                        trimStart = checkStartTrimState.value,
                        trimEnd = checkEndTrimState.value,
                        crossCenterGravitation = crossCenterGravitationState.value,
                        path = checkPath,
                        cache = cache,
                        stroke = stroke,
                    )
                }
            }
            .then(finalModifier),
    ) {}
}

private data class CheckmarkCache(
    val startPoint: Offset,
    val middlePoint: Offset,
    val endPoint: Offset,
    val centerX: Float,
    val centerY: Float,
    val strokeWidth: Float,
)

/** 按 trim 比例截取对勾路径；半选时把两端点向中心收拢，得到一条短横。 */
private fun DrawScope.drawTrimmedCheckmark(
    color: Color,
    alpha: Float = 1f,
    trimStart: Float,
    trimEnd: Float,
    crossCenterGravitation: Float,
    path: Path,
    cache: CheckmarkCache,
    stroke: Stroke,
) {
    path.rewind()

    val gravitatedStart = Offset(
        cache.startPoint.x,
        lerp(cache.startPoint.y, cache.centerY, crossCenterGravitation),
    )
    val gravitatedMiddle = Offset(
        lerp(cache.middlePoint.x, cache.centerX, crossCenterGravitation),
        lerp(cache.middlePoint.y, cache.centerY, crossCenterGravitation),
    )
    val gravitatedEnd = Offset(
        cache.endPoint.x,
        lerp(cache.endPoint.y, cache.centerY, crossCenterGravitation),
    )

    val firstSegmentLength = (gravitatedMiddle - gravitatedStart).getDistance()
    val secondSegmentLength = (gravitatedEnd - gravitatedMiddle).getDistance()
    val totalLength = firstSegmentLength + secondSegmentLength

    val startDistance = totalLength * trimStart
    val endDistance = totalLength * trimEnd

    if (startDistance < firstSegmentLength && endDistance > 0) {
        val segStartRatio = (startDistance / firstSegmentLength).coerceIn(0f, 1f)
        val segEndRatio = (endDistance / firstSegmentLength).coerceIn(0f, 1f)

        val startX = gravitatedStart.x + (gravitatedMiddle.x - gravitatedStart.x) * segStartRatio
        val startY = gravitatedStart.y + (gravitatedMiddle.y - gravitatedStart.y) * segStartRatio
        val endX = gravitatedStart.x + (gravitatedMiddle.x - gravitatedStart.x) * segEndRatio
        val endY = gravitatedStart.y + (gravitatedMiddle.y - gravitatedStart.y) * segEndRatio

        path.moveTo(startX, startY)
        path.lineTo(endX, endY)
    }

    if (endDistance > firstSegmentLength) {
        val segStartRatio = ((startDistance - firstSegmentLength) / secondSegmentLength).coerceIn(0f, 1f)
        val segEndRatio = ((endDistance - firstSegmentLength) / secondSegmentLength).coerceIn(0f, 1f)

        val startX = gravitatedMiddle.x + (gravitatedEnd.x - gravitatedMiddle.x) * segStartRatio
        val startY = gravitatedMiddle.y + (gravitatedEnd.y - gravitatedMiddle.y) * segStartRatio
        val endX = gravitatedMiddle.x + (gravitatedEnd.x - gravitatedMiddle.x) * segEndRatio
        val endY = gravitatedMiddle.y + (gravitatedEnd.y - gravitatedMiddle.y) * segEndRatio

        if (startDistance < firstSegmentLength) {
            path.lineTo(endX, endY)
        } else {
            path.moveTo(startX, startY)
            path.lineTo(endX, endY)
        }
    }

    drawPath(
        path = path,
        color = color,
        alpha = alpha,
        style = stroke,
    )
}

object CheckboxDefaults {

    /** 默认配色，全部取自当前主题角色色。 */
    @Composable
    fun colors(
        checkedForegroundColor: Color = NexioTheme.colorScheme.onPrimary,
        uncheckedForegroundColor: Color = NexioTheme.colorScheme.onPrimary,
        disabledCheckedForegroundColor: Color = NexioTheme.colorScheme.disabledOnPrimary,
        disabledUncheckedForegroundColor: Color = NexioTheme.colorScheme.disabledOnPrimary,
        checkedBackgroundColor: Color = NexioTheme.colorScheme.primary,
        uncheckedBackgroundColor: Color = NexioTheme.colorScheme.secondary,
        disabledCheckedBackgroundColor: Color = NexioTheme.colorScheme.disabledPrimarySlider,
        disabledUncheckedBackgroundColor: Color = NexioTheme.colorScheme.disabledSecondary,
    ): CheckboxColors = remember(
        checkedForegroundColor,
        uncheckedForegroundColor,
        disabledCheckedForegroundColor,
        disabledUncheckedForegroundColor,
        checkedBackgroundColor,
        uncheckedBackgroundColor,
        disabledCheckedBackgroundColor,
        disabledUncheckedBackgroundColor,
    ) {
        CheckboxColors(
            checkedForegroundColor = checkedForegroundColor,
            uncheckedForegroundColor = uncheckedForegroundColor,
            disabledCheckedForegroundColor = disabledCheckedForegroundColor,
            disabledUncheckedForegroundColor = disabledUncheckedForegroundColor,
            checkedBackgroundColor = checkedBackgroundColor,
            uncheckedBackgroundColor = uncheckedBackgroundColor,
            disabledCheckedBackgroundColor = disabledCheckedBackgroundColor,
            disabledUncheckedBackgroundColor = disabledUncheckedBackgroundColor,
        )
    }
}

@Immutable
data class CheckboxColors(
    private val checkedForegroundColor: Color,
    private val uncheckedForegroundColor: Color,
    private val disabledCheckedForegroundColor: Color,
    private val disabledUncheckedForegroundColor: Color,
    private val checkedBackgroundColor: Color,
    private val uncheckedBackgroundColor: Color,
    private val disabledCheckedBackgroundColor: Color,
    private val disabledUncheckedBackgroundColor: Color,
) {
    internal fun checkedForegroundColor(enabled: Boolean): Color = if (enabled) checkedForegroundColor else disabledCheckedForegroundColor

    internal fun uncheckedForegroundColor(enabled: Boolean): Color = if (enabled) uncheckedForegroundColor else disabledUncheckedForegroundColor

    internal fun checkedBackgroundColor(enabled: Boolean): Color = if (enabled) checkedBackgroundColor else disabledCheckedBackgroundColor

    internal fun uncheckedBackgroundColor(enabled: Boolean): Color = if (enabled) uncheckedBackgroundColor else disabledUncheckedBackgroundColor
}
