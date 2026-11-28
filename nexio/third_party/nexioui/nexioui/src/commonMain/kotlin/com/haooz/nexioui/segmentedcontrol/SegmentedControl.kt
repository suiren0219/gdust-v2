package com.haooz.nexioui.segmentedcontrol

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.haooz.nexioui.capsuleslider.CapsuleFlingTracker
import com.haooz.nexioui.capsuleslider.CapsuleSelector
import com.haooz.nexioui.capsuleslider.CapsuleSlider
import com.haooz.nexioui.capsuleslider.capsuleSliderPill
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.nexioui.interaction.InteractiveHighlight
import com.haooz.nexioui.internal.drawBlurredPathShadow
import com.haooz.nexioui.material.NexioMaterial
import com.haooz.nexioui.text.NexioText
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.GlassEffectScope
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import com.haooz.softglass.backdrop.effects.runtimeShaderEffect
import com.haooz.softglass.glass.BarFill
import com.haooz.softglass.glass.BrightLargeTone
import com.haooz.softglass.glass.SOFT_GLASS_LIVE_SHADER
import com.haooz.softglass.glass.applySoftGlass
import com.haooz.softglass.shape.ContinuousCapsule
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val LIVE_SEGMENT_PILL_GLASS_KEY = "NexioSoftGlassLiveSegmentPill"

/**
 * 胶囊分段控件。
 *
 * 选中块是一个可拖动、可快甩的胶囊；点按其它格子会直接切过去，拖动则松手吸附到最近格。
 *
 * @param options 选项文案。
 * @param selectedIndex 当前选中项。
 * @param onSelect 选中项变化回调。
 * @param backdrop 底层玻璃采样。
 * @param modifier 外层修饰符。
 * @param containerHeight 控件高度。
 * @param alpha 玻璃容器透明度；0 时回落到实底样式，便于随页面滚动淡入。
 * @param selectOnPress 按下即回调，默认松手回调。
 * @param freezeKey 每递增一次，滑块停在当前位置、速度归零。
 * @param alignKey 每递增一次，滑块吸附到 [selectedIndex]。
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    containerHeight: Dp = SegmentedControlDefaults.ContainerHeight,
    surfaceAlpha: Float = 1f,
    selectOnPress: Boolean = false,
    freezeKey: Int = 0,
    alignKey: Int = 0,
) {
    if (options.isEmpty()) return
    val isLightTheme = !NexioTheme.isDark
    val containerColor = segmentedContainerColor(isLightTheme)
    val idleColor = if (isLightTheme) Color.White else Color(0xFF242424)
    val labelColor = if (isLightTheme) Color.Black.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.8f)
    val animationScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current

    val shadowMaxBlurPx = with(density) { 10.dp.toPx() }
    val glassBlur = NexioMaterial.glassBlurDp()
    val glassEffectsOn = NexioMaterial.glassEffectsEnabled()
    val glassStyle = NexioMaterial.chromeGlassStyle(
        isDark = !isLightTheme,
        fill = BarFill,
        tone = BrightLargeTone,
    )
    val capsuleShape: () -> Shape = remember { { ContinuousCapsule() } }
    val pillEffects: GlassEffectScope.() -> Unit = remember(glassBlur, glassEffectsOn, glassStyle) {
        {
            if (glassEffectsOn) {
                blur(glassBlur.dp.toPx(), TileMode.Mirror)
                padding = 0f
                runtimeShaderEffect(
                    key = LIVE_SEGMENT_PILL_GLASS_KEY,
                    shaderString = SOFT_GLASS_LIVE_SHADER,
                    uniformShaderName = "content",
                ) {
                    applySoftGlass(style = glassStyle, width = size.width, height = size.height)
                }
            }
        }
    }
    val pillSurface: DrawScope.() -> Unit = remember(containerColor) { { drawRect(containerColor) } }

    val highlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope,
            isDark = { !isLightTheme },
        )
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        val viewConfiguration = LocalViewConfiguration.current
        val totalPx = constraints.maxWidth.toFloat()
        val padPx = with(density) { 4.dp.toPx() }
        val tabWidthPx = (totalPx - padPx * 2f) / options.size
        val insetPx = with(density) { CapsuleSlider.Inset.toPx() }
        val selectorWidthPx = tabWidthPx - insetPx * 2f
        val selectorHeightPx = with(density) {
            (containerHeight - CapsuleSlider.VerticalInset).roundToPx()
        }
        val trailMaxPx = with(density) { CapsuleSlider.TrailMax.toPx() }
        val flingVelocityThresholdPxPerSec =
            with(density) { CapsuleSlider.FlingVelocityDpPerSecond.toPx() }

        fun slotLeft(index: Float): Float = padPx + index * tabWidthPx + insetPx

        var currentIndex by remember { mutableIntStateOf(selectedIndex.coerceIn(0, options.lastIndex)) }
        val latestSelected by rememberUpdatedState(selectedIndex)
        val latestOnSelect by rememberUpdatedState(onSelect)

        val capsule = remember(animationScope, options.size, tabWidthPx, totalPx) {
            CapsuleSelector(
                scope = animationScope,
                widthPx = selectorWidthPx,
                startLeftPx = slotLeft(currentIndex.toFloat()),
                leftLimit = { padPx..(totalPx - padPx - selectorWidthPx) },
            )
        }
        val settleToSlot: (Int) -> Unit = { index ->
            val target = slotLeft(index.toFloat())
            capsule.settleTo(target, forward = target > capsule.leftPx)
        }
        val indexAtCapsule: () -> Int = {
            ((capsule.centerPx - padPx) / tabWidthPx - 0.5f).roundToInt()
                .coerceIn(0, options.lastIndex)
        }

        LaunchedEffect(capsule) {
            snapshotFlow { latestSelected }.collectLatest { index ->
                if (index != currentIndex) {
                    currentIndex = index
                    settleToSlot(index)
                }
            }
        }
        LaunchedEffect(capsule, freezeKey) {
            if (freezeKey > 0) capsule.freeze()
        }
        LaunchedEffect(capsule, alignKey) {
            if (alignKey > 0) {
                val index = latestSelected.coerceIn(0, options.lastIndex)
                currentIndex = index
                settleToSlot(index)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(containerHeight)
                .drawBehind {
                    val glassProgress = surfaceAlpha.coerceIn(0f, 1f)
                    val outline = capsuleShape().createOutline(size, layoutDirection, this@drawBehind)
                    if (surfaceAlpha > 0.01f) {
                        val shadowPath = Path().apply {
                            when (outline) {
                                is Outline.Rounded -> addRoundRect(outline.roundRect)
                                is Outline.Generic -> addPath(outline.path)
                                is Outline.Rectangle -> addRect(outline.rect)
                            }
                        }
                        drawBlurredPathShadow(
                            path = shadowPath,
                            color = if (isLightTheme) Color(0x12000000) else Color(0x20000000),
                            blurRadius = (shadowMaxBlurPx * surfaceAlpha).coerceAtLeast(0.1f),
                        )
                    }
                    // 材质淡入时底色淡出
                    drawOutline(outline = outline, color = idleColor.copy(alpha = 1f - glassProgress))
                },
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBackdrop(
                        backdrop = backdrop,
                        shadow = null,
                        shape = capsuleShape,
                        effects = pillEffects,
                        layerBlock = { alpha = surfaceAlpha },
                        onDrawSurface = if (glassEffectsOn) null else pillSurface,
                    ),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(ContinuousCapsule())
                    .then(highlight.modifier),
            )
            Box(
                modifier = Modifier.capsuleSliderPill(
                    selector = capsule,
                    heightPx = selectorHeightPx,
                    pressProgress = { highlight.pressProgress },
                ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(containerHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                options.forEach { label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        NexioText(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = labelColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .then(highlight.gestureModifier)
                .pointerInput(
                    capsule,
                    options.size,
                    tabWidthPx,
                    totalPx,
                    hapticFeedback,
                    flingVelocityThresholdPxPerSec,
                ) {
                    val touchSlop = viewConfiguration.touchSlop
                    val flingVelocityThreshold = flingVelocityThresholdPxPerSec
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downX = down.position.x
                        val startIndex = currentIndex.coerceIn(0, options.lastIndex)
                        val fling = CapsuleFlingTracker(down.uptimeMillis, downX)

                        var dragging = abs(downX - capsule.centerPx) <=
                            tabWidthPx * CapsuleSlider.GrabHalfWidthRatio
                        var pressedTab = -1
                        var dragAnchorLeft = capsule.leftPx
                        var dragAnchorX = downX
                        var trailVelocity = 0f

                        if (!dragging) {
                            pressedTab = floor((downX - padPx) / tabWidthPx)
                                .toInt()
                                .coerceIn(0, options.lastIndex)
                            settleToSlot(pressedTab)
                            if (selectOnPress && pressedTab != currentIndex) latestOnSelect(pressedTab)
                        }

                        var lastX = downX
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                val positionalTarget = if (dragging) {
                                    indexAtCapsule()
                                } else {
                                    pressedTab.coerceIn(0, options.lastIndex)
                                }
                                val flingDir = if (dragging) {
                                    fling.flingDirection(flingVelocityThreshold, touchSlop)
                                } else 0
                                val target = CapsuleSlider.flingTarget(
                                    startIndex = startIndex,
                                    positionalTarget = positionalTarget,
                                    indexDirection = flingDir,
                                    lastIndex = options.lastIndex,
                                )
                                currentIndex = target
                                settleToSlot(target)
                                if (target != startIndex) {
                                    hapticFeedback.performHapticFeedback(
                                        HapticFeedbackType.TextHandleMove,
                                    )
                                }
                                latestOnSelect(target)
                                break
                            }

                            val dx = change.position.x - lastX
                            val totalDx = change.position.x - downX
                            fling.onMove(change.uptimeMillis, change.position, downX)
                            if (!dragging && abs(totalDx) > touchSlop) {
                                dragging = true
                                dragAnchorLeft = capsule.leftPx
                                dragAnchorX = change.position.x
                                trailVelocity = 0f
                                lastX = change.position.x
                            }
                            if (dragging) {
                                trailVelocity = trailVelocity * CapsuleSlider.TrailSmoothing +
                                    dx * (1f - CapsuleSlider.TrailSmoothing)
                                val trail = (abs(trailVelocity) * CapsuleSlider.TrailGain)
                                    .coerceAtMost(trailMaxPx)
                                val dir = when {
                                    trailVelocity > 0.5f -> 1
                                    trailVelocity < -0.5f -> -1
                                    else -> 0
                                }
                                capsule.onDrag(
                                    leftPx = dragAnchorLeft + (change.position.x - dragAnchorX),
                                    trailPx = trail,
                                    dir = dir,
                                )
                            }
                            lastX = change.position.x
                            change.consume()
                        }
                    }
                },
        )
    }
}

/** 容器实底：随材质档把半透明底色实色化。 */
@Composable
private fun segmentedContainerColor(isLight: Boolean): Color =
    if (isLight) {
        Color(0xFFFAFAFA).copy(alpha = NexioMaterial.solidSurfaceAlpha(0.5f))
    } else {
        Color(0xFF242424).copy(alpha = NexioMaterial.solidSurfaceAlpha(0.6f))
    }
