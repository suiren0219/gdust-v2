package com.haooz.nexioui.bottombar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.zIndex
import com.haooz.nexioui.interaction.InteractiveHighlight
import com.haooz.nexioui.internal.GlassDragStretchRatio
import com.haooz.nexioui.internal.GlassDragTranslationDamping
import com.haooz.nexioui.internal.computeDragTransform
import com.haooz.nexioui.material.NexioGlassStyle
import com.haooz.nexioui.material.NexioMaterial
import com.haooz.nexioui.capsuleslider.CapsuleFlingTracker
import com.haooz.nexioui.capsuleslider.CapsuleSelector
import com.haooz.nexioui.capsuleslider.CapsuleSlider
import com.haooz.nexioui.capsuleslider.capsuleSliderPill
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop

private const val LIVE_BOTTOM_BAR_PILL_GLASS_KEY = "NexioSoftGlassLiveBottomBarPill"
private const val LIVE_BOTTOM_BAR_ACTION_GLASS_KEY = "NexioSoftGlassLiveBottomBarAction"

/** 底栏中的一个页签。 */
@Immutable
data class BottomBarItem(
    val icon: ImageVector,
    val label: String,
    val contentDescription: String? = label,
)

/** 底栏右侧的圆形动作按钮。 */
@Immutable
data class BottomBarAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
)

/**
 * 柔光玻璃底栏：左侧页签胶囊 + 右侧圆形动作按钮。
 *
 * 两块玻璃相互独立，只有间距、没有融合。左侧胶囊始终位于更高层（[zIndex] 1f），
 * 靠近或按压放大时不会被右侧圆钮覆盖。
 *
 * 手势统一由胶囊上的观察层处理：点按页签直接切换，拖动胶囊跟手，松手吸附最近页签。
 *
 * @param items 页签。
 * @param selectedIndex 当前页签。
 * @param onSelect 页签切换回调。
 * @param backdrop 底层玻璃采样。
 * @param modifier 外层修饰符。
 * @param height 底栏高度，也是右侧圆钮直径。
 * @param action 右侧圆钮；null 时只显示页签胶囊。
 * @param actionGap 胶囊与圆钮的间距。
 * @param tabWidthFraction 单个页签占可用宽度的比例。
 * @param iconSize 图标尺寸。
 * @param labelFontSize 标签字号。
 */
@Composable
fun BottomBar(
    items: List<BottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    height: Dp = BottomBarDefaults.Height,
    action: BottomBarAction? = null,
    actionGap: Dp = BottomBarDefaults.ActionGap,
    tabWidthFraction: Float = BottomBarDefaults.TabWidthFraction,
    iconSize: Dp = BottomBarDefaults.IconSize,
    labelFontSize: TextUnit = BottomBarDefaults.LabelFontSize,
) {
    if (items.isEmpty()) return

    val isLightTheme = !NexioTheme.isDark
    val containerColor = bottomBarSurfaceColor(isLightTheme)
    val iconTint = NexioTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.8f)
    val us = LocalDensity.current
    val animationScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    val glassBlur = NexioMaterial.glassBlurDp()
    val glassEffectsOn = NexioMaterial.glassEffectsEnabled()
    val glassStyle = NexioMaterial.chromeGlassStyle(
        base = NexioGlassStyle,
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
                    key = LIVE_BOTTOM_BAR_PILL_GLASS_KEY,
                    shaderString = SOFT_GLASS_LIVE_SHADER,
                    uniformShaderName = "content",
                ) {
                    applySoftGlass(style = glassStyle, width = size.width, height = size.height)
                }
            }
        }
    }
    val actionEffects: GlassEffectScope.() -> Unit = remember(glassBlur, glassEffectsOn, glassStyle) {
        {
            if (glassEffectsOn) {
                blur(glassBlur.dp.toPx(), TileMode.Mirror)
                padding = 0f
                runtimeShaderEffect(
                    key = LIVE_BOTTOM_BAR_ACTION_GLASS_KEY,
                    shaderString = SOFT_GLASS_LIVE_SHADER,
                    uniformShaderName = "content",
                ) {
                    applySoftGlass(style = glassStyle, width = size.width, height = size.height)
                }
            }
        }
    }
    val surface: DrawScope.() -> Unit = remember(containerColor) { { drawRect(containerColor) } }

    val pillHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope,
            isDark = { !isLightTheme },
        )
    }
    val actionHighlight = if (action != null) {
        remember(animationScope, height) {
            InteractiveHighlight(
                animationScope = animationScope,
                radiusBaseDp = height,
                isDark = { !isLightTheme },
            )
        }
    } else {
        null
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.CenterStart,
    ) {
        val viewConfiguration = LocalViewConfiguration.current
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val itemCount = items.size
        val pillWidthDp = maxWidth * tabWidthFraction * itemCount
        val trailingSize = if (action != null) height else 0.dp
        val gapDp = if (action != null) actionGap else 0.dp
        val groupWidth = pillWidthDp + gapDp + trailingSize
        val startX = ((maxWidth - groupWidth) / 2).coerceAtLeast(0.dp)

        val pillPx = with(us) { pillWidthDp.toPx() }
        val padPx = with(us) { 4.dp.toPx() }
        val tabWidthPx = (pillPx - padPx * 2f) / itemCount
        val selectorInsetPx = with(us) { CapsuleSlider.Inset.toPx() }
        val selectorWidthPx = tabWidthPx - selectorInsetPx * 2f
        val selectorHeightPx = with(us) { (height - CapsuleSlider.VerticalInset).roundToPx() }
        val trailMaxPx = with(us) { CapsuleSlider.TrailMax.toPx() }
        val flingVelocityThresholdPxPerSec =
            with(us) { CapsuleSlider.FlingVelocityDpPerSecond.toPx() }
        val ltrSign = if (isLtr) 1f else -1f
        val maxIndex = (itemCount - 1).toFloat()

        var currentIndex by remember { mutableIntStateOf(selectedIndex.coerceIn(0, itemCount - 1)) }
        val latestSelected by rememberUpdatedState(selectedIndex)

        val slotLeft: (Float) -> Float = { index ->
            if (isLtr) {
                padPx + index * tabWidthPx + selectorInsetPx
            } else {
                pillPx - padPx - (index + 1f) * tabWidthPx + selectorInsetPx
            }
        }
        val capsule = remember(animationScope, itemCount, tabWidthPx, pillPx, padPx, selectorInsetPx) {
            CapsuleSelector(
                scope = animationScope,
                widthPx = selectorWidthPx,
                startLeftPx = slotLeft(currentIndex.toFloat()),
                leftLimit = { padPx..(pillPx - padPx - selectorWidthPx) },
            )
        }
        val settleToSlot: (Int) -> Unit = { index ->
            val target = slotLeft(index.toFloat())
            capsule.settleTo(target, forward = target > capsule.leftPx)
        }
        val indexAtCapsule: () -> Int = {
            val raw = if (isLtr) {
                (capsule.centerPx - padPx) / tabWidthPx - 0.5f
            } else {
                (pillPx - padPx - capsule.centerPx) / tabWidthPx - 0.5f
            }
            raw.fastRoundToInt().fastCoerceIn(0, itemCount - 1)
        }

        LaunchedEffect(capsule) {
            snapshotFlow { latestSelected }.collectLatest { index ->
                val target = index.coerceIn(0, itemCount - 1)
                if (target != currentIndex) {
                    currentIndex = target
                    settleToSlot(target)
                }
            }
        }

        // 左侧页签胶囊：层级高于右侧圆钮。
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = startX)
                .width(pillWidthDp)
                .height(height)
                .zIndex(1f)
                .graphicsLayer {
                    clip = false
                    val drag = computeDragTransform(
                        width = size.width,
                        height = size.height,
                        fraction = 1f,
                        pressProgress = pillHighlight.pressProgress,
                        offset = pillHighlight.offset,
                        density = this,
                        shapeAspectRatio = 1f,
                        stretchRatio = GlassDragStretchRatio,
                        translationDamping = GlassDragTranslationDamping,
                        pressScaleDp = 2f,
                    )
                    scaleX = drag.scaleX
                    scaleY = drag.scaleY
                    translationX = drag.translationX
                    translationY = drag.translationY
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = capsuleShape,
                    effects = pillEffects,
                    onDrawSurface = if (glassEffectsOn) null else surface,
                ),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(pillHighlight.modifier)
                    .clip(ContinuousCapsule()),
            )
            Box(
                modifier = Modifier.capsuleSliderPill(
                    selector = capsule,
                    heightPx = selectorHeightPx,
                    pressProgress = { pillHighlight.pressProgress },
                ),
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(ContinuousCapsule())
                            .semantics { role = Role.Tab },
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.contentDescription,
                            modifier = Modifier.size(iconSize),
                            tint = iconTint,
                        )
                        NexioText(
                            text = item.label,
                            fontSize = labelFontSize,
                            fontWeight = FontWeight.Medium,
                            color = iconTint,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // 右侧圆形按钮：另一块独立玻璃，层级低于左侧胶囊。
        if (action != null && actionHighlight != null) {
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = startX + pillWidthDp + gapDp)
                    .size(height)
                    .zIndex(0f)
                    .graphicsLayer {
                        clip = false
                        val drag = computeDragTransform(
                            width = size.width,
                            height = size.height,
                            fraction = 0f,
                            pressProgress = actionHighlight.pressProgress,
                            offset = actionHighlight.offset,
                            density = this,
                            stretchRatio = GlassDragStretchRatio,
                            translationDamping = GlassDragTranslationDamping,
                            pressScaleDp = 2f,
                        )
                        scaleX = drag.scaleX
                        scaleY = drag.scaleY
                        translationX = drag.translationX
                        translationY = drag.translationY
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = capsuleShape,
                        effects = actionEffects,
                        onDrawSurface = if (glassEffectsOn) null else surface,
                    )
                    .then(actionHighlight.gestureModifier)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            action.onClick()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .then(actionHighlight.modifier)
                        .clip(ContinuousCapsule()),
                )
                Icon(
                    imageVector = action.icon,
                    contentDescription = action.contentDescription,
                    modifier = Modifier.size(iconSize + 4.dp),
                    tint = if (isLightTheme) {
                        Color.Black.copy(alpha = 0.85f)
                    } else {
                        Color.White.copy(alpha = 0.85f)
                    },
                )
            }
        }

        // 手势层只覆盖胶囊宽度，不抢右侧圆钮的点击。
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = startX)
                .width(pillWidthDp)
                .fillMaxHeight()
                .then(pillHighlight.gestureModifier)
                .pointerInput(
                    capsule,
                    itemCount,
                    tabWidthPx,
                    isLtr,
                    padPx,
                    pillPx,
                    trailMaxPx,
                    flingVelocityThresholdPxPerSec,
                ) {
                    val touchSlop = viewConfiguration.touchSlop
                    val flingVelocityThreshold = flingVelocityThresholdPxPerSec
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downX = down.position.x
                        val startIndex = currentIndex.coerceIn(0, itemCount - 1)
                        val fling = CapsuleFlingTracker(down.uptimeMillis, downX)

                        val capsuleHalf = tabWidthPx * CapsuleSlider.GrabHalfWidthRatio
                        var dragging = abs(downX - capsule.centerPx) <= capsuleHalf
                        var pressedTab = -1
                        var dragAnchorLeft = capsule.leftPx
                        var dragAnchorX = downX
                        var trailVelocity = 0f

                        if (!dragging) {
                            pressedTab = if (isLtr) {
                                floor((downX - padPx) / tabWidthPx).toInt()
                            } else {
                                floor((pillPx - padPx - downX) / tabWidthPx).toInt()
                            }.fastCoerceIn(0, itemCount - 1)
                            currentIndex = pressedTab
                            settleToSlot(pressedTab)
                        }

                        var lastX = downX
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                val positionalTarget = if (dragging) {
                                    indexAtCapsule()
                                } else {
                                    pressedTab.fastCoerceIn(0, itemCount - 1)
                                }
                                val flingDir = if (dragging) {
                                    fling.flingDirection(flingVelocityThreshold, touchSlop) *
                                        ltrSign.toInt()
                                } else {
                                    0
                                }
                                val target = CapsuleSlider.flingTarget(
                                    startIndex = startIndex,
                                    positionalTarget = positionalTarget,
                                    indexDirection = flingDir,
                                    lastIndex = itemCount - 1,
                                )
                                currentIndex = target
                                settleToSlot(target)
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(target)
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
                                currentIndex = indexAtCapsule()
                            }
                            lastX = change.position.x
                            change.consume()
                        }
                    }
                },
        )
    }
}

@Composable
private fun bottomBarSurfaceColor(isLight: Boolean): Color =
    if (isLight) {
        Color(0xFFFAFAFA).copy(alpha = NexioMaterial.solidSurfaceAlpha(0.5f))
    } else {
        Color(0xFF242424).copy(alpha = NexioMaterial.solidSurfaceAlpha(0.6f))
    }
