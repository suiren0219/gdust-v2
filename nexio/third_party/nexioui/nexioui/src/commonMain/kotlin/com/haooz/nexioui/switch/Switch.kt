package com.haooz.nexioui.switch

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.shape.ContinuousCapsule
import kotlin.math.absoluteValue

/**
 * 开关。
 *
 * 支持点按与横向拖动切换：滑块跟手位移，拖过中点才改变选中状态，越过两端时给出触感；
 * 按下、拖动或悬停时滑块放大。
 *
 * @param checked 是否开启。
 * @param onCheckedChange 状态变化回调；为 null 时仅展示状态。
 * @param modifier 外层修饰符。
 * @param colors 轨道与滑块颜色。
 * @param enabled 是否可用。
 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    colors: SwitchColors = SwitchDefaults.colors(),
    enabled: Boolean = true,
) {
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    val hapticFeedback = LocalHapticFeedback.current
    var hasVibrated by remember { mutableStateOf(false) }
    var hasVibratedOnce by remember { mutableStateOf(false) }
    var rawDragOffset by remember { mutableFloatStateOf(0f) }
    var currentDragInteraction by remember { mutableStateOf<DragInteraction.Start?>(null) }

    val capsuleShape = ContinuousCapsule()
    val thumbOffsetSpringSpec = remember { spring<Dp>(dampingRatio = 0.7f, stiffness = 987f) }
    val thumbScaleSpringSpec = remember { spring<Float>(dampingRatio = 0.6f, stiffness = 987f) }

    var dragOffset by remember { mutableFloatStateOf(0f) }
    val thumbOffsetState = animateDpAsState(
        targetValue = (if (checked) 25.dp else 4.dp) + dragOffset.dp,
        animationSpec = thumbOffsetSpringSpec,
    )

    val thumbScaleState = animateFloatAsState(
        targetValue = if (!enabled) {
            1f
        } else if (isPressed || isDragged || isHovered) {
            1.127f
        } else {
            1f
        },
        animationSpec = thumbScaleSpringSpec,
    )

    val thumbColorState = animateColorAsState(
        if (checked) colors.checkedThumbColor(enabled) else colors.uncheckedThumbColor(enabled),
    )

    val backgroundColorState = animateColorAsState(
        if (checked) colors.checkedTrackColor(enabled) else colors.uncheckedTrackColor(enabled),
        animationSpec = spring(dampingRatio = 0.99f, stiffness = 438.6f),
    )

    val toggleableModifier = if (onCheckedChange != null) {
        remember(checked, enabled, interactionSource) {
            Modifier.toggleable(
                value = checked,
                onValueChange = { v ->
                    currentOnCheckedChange?.invoke(v)
                    hapticFeedback.performHapticFeedback(
                        if (v) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                    )
                },
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
            )
        }
    } else {
        Modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
            if (!enabled) disabled()
        }
    }

    Box(
        modifier = modifier
            .wrapContentSize(Alignment.Center)
            .size(49.dp, 28.dp)
            .clip(capsuleShape)
            .drawBehind {
                drawRect(backgroundColorState.value)
            }
            .hoverable(
                interactionSource = interactionSource,
                enabled = enabled,
            )
            .then(toggleableModifier),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .align(Alignment.CenterStart)
                .offset {
                    IntOffset(thumbOffsetState.value.roundToPx(), 0)
                }
                .graphicsLayer {
                    scaleX = thumbScaleState.value
                    scaleY = thumbScaleState.value
                }
                .drawBehind {
                    drawCircle(color = thumbColorState.value)
                }
                .then(
                    if (enabled) {
                        Modifier.draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { dragAmount ->
                                rawDragOffset += dragAmount / 2f
                                dragOffset = if (checked) {
                                    rawDragOffset.coerceIn(-21f, 0f)
                                } else {
                                    rawDragOffset.coerceIn(0f, 21f)
                                }

                                // 回到中段时重置触感标记，两端吸附时各给一次触感
                                if (dragOffset in -11f..-10f || dragOffset in 10f..11f) {
                                    hasVibratedOnce = false
                                } else if (dragOffset in -20f..-1f || dragOffset in 1f..20f) {
                                    hasVibrated = false
                                } else if (!hasVibrated) {
                                    if ((checked && dragOffset == -21f) || (!checked && dragOffset == 0f)) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.ToggleOff)
                                        hasVibrated = true
                                        hasVibratedOnce = true
                                    } else if ((checked && dragOffset == 0f) || (!checked && dragOffset == 21f)) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.ToggleOn)
                                        hasVibrated = true
                                        hasVibratedOnce = true
                                    }
                                }
                            },
                            onDragStarted = { _ ->
                                currentDragInteraction = DragInteraction.Start().also { interactionSource.tryEmit(it) }
                                hasVibrated = true
                                hasVibratedOnce = false
                                rawDragOffset = 0f
                            },
                            onDragStopped = {
                                if (dragOffset.absoluteValue > 21f / 2f) currentOnCheckedChange?.invoke(!checked)
                                if (!hasVibratedOnce && dragOffset.absoluteValue >= 1f) {
                                    if ((checked && dragOffset <= -11f) || (!checked && dragOffset <= 10f)) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.ToggleOff)
                                    } else if ((checked && dragOffset >= -10f) || (!checked && dragOffset >= 11f)) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.ToggleOn)
                                    }
                                }
                                currentDragInteraction?.let { interactionSource.tryEmit(DragInteraction.Stop(it)) }
                                dragOffset = 0f
                                rawDragOffset = 0f
                            },
                        )
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

object SwitchDefaults {

    /** 默认配色，全部取自当前主题角色色。 */
    @Composable
    fun colors(
        checkedThumbColor: Color = NexioTheme.colorScheme.onPrimary,
        uncheckedThumbColor: Color = NexioTheme.colorScheme.onSecondary,
        disabledCheckedThumbColor: Color = NexioTheme.colorScheme.disabledOnPrimary,
        disabledUncheckedThumbColor: Color = NexioTheme.colorScheme.disabledOnSurface,
        checkedTrackColor: Color = NexioTheme.colorScheme.primary,
        uncheckedTrackColor: Color = if (NexioTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF424242) else Color(0xFFECECEC),
        disabledCheckedTrackColor: Color = NexioTheme.colorScheme.disabledPrimarySlider,
        disabledUncheckedTrackColor: Color = NexioTheme.colorScheme.disabledSecondary,
    ): SwitchColors = remember(
        checkedThumbColor,
        uncheckedThumbColor,
        disabledCheckedThumbColor,
        disabledUncheckedThumbColor,
        checkedTrackColor,
        uncheckedTrackColor,
        disabledCheckedTrackColor,
        disabledUncheckedTrackColor,
    ) {
        SwitchColors(
            checkedThumbColor = checkedThumbColor,
            uncheckedThumbColor = uncheckedThumbColor,
            disabledCheckedThumbColor = disabledCheckedThumbColor,
            disabledUncheckedThumbColor = disabledUncheckedThumbColor,
            checkedTrackColor = checkedTrackColor,
            uncheckedTrackColor = uncheckedTrackColor,
            disabledCheckedTrackColor = disabledCheckedTrackColor,
            disabledUncheckedTrackColor = disabledUncheckedTrackColor,
        )
    }
}

@Immutable
data class SwitchColors(
    private val checkedThumbColor: Color,
    private val uncheckedThumbColor: Color,
    private val disabledCheckedThumbColor: Color,
    private val disabledUncheckedThumbColor: Color,
    private val checkedTrackColor: Color,
    private val uncheckedTrackColor: Color,
    private val disabledCheckedTrackColor: Color,
    private val disabledUncheckedTrackColor: Color,
) {
    @Stable
    internal fun checkedThumbColor(enabled: Boolean): Color = if (enabled) checkedThumbColor else disabledCheckedThumbColor

    @Stable
    internal fun uncheckedThumbColor(enabled: Boolean): Color = if (enabled) uncheckedThumbColor else disabledUncheckedThumbColor

    @Stable
    internal fun checkedTrackColor(enabled: Boolean): Color = if (enabled) checkedTrackColor else disabledCheckedTrackColor

    @Stable
    internal fun uncheckedTrackColor(enabled: Boolean): Color = if (enabled) uncheckedTrackColor else disabledUncheckedTrackColor
}
