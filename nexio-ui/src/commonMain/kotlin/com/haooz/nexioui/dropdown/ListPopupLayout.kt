package com.haooz.nexioui.dropdown

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import com.haooz.nexioui.interaction.InteractiveHighlight
import com.haooz.nexioui.internal.drawBlurredPathShadow
import com.haooz.nexioui.material.NexioMaterial
import com.haooz.nexioui.navigationback.LocalNexioPredictiveBackEnabled
import com.haooz.nexioui.navigationback.rememberNavigationBack
import com.haooz.nexioui.overlaydialog.LocalNexioDialogStates
import com.haooz.nexioui.overlaydialog.LocalNexioRootDialogStates
import com.haooz.nexioui.overlaydialog.NexioDialogEntry
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.GlassEffectScope
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import com.haooz.softglass.backdrop.effects.runtimeShaderEffect
import com.haooz.softglass.glass.BrightLargeTone
import com.haooz.softglass.glass.SOFT_GLASS_LIVE_SHADER
import com.haooz.softglass.glass.applySoftGlass
import com.haooz.softglass.platform.isRenderEffectSupported
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// 进场：尺寸 spring 0.77/220，锚点迁移 spring 0.77/420
private val FractionEnterAnimSpec =
    spring<Float>(dampingRatio = 0.77f, stiffness = 220f, visibilityThreshold = 0.0001f)
// 退场：尺寸 spring 0.85/650，锚点迁移 tween(340)
private val FractionExitAnimSpec =
    spring<Float>(dampingRatio = 0.85f, stiffness = 650f, visibilityThreshold = 0.0001f)
private val OriginEnterAnimSpec =
    spring<Float>(dampingRatio = 0.77f, stiffness = 420f, visibilityThreshold = 0.0001f)
private val OriginExitAnimSpec =
    tween<Float>(340, easing = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f))
/** 预测性返回取消后弹回展开态 */
private val BackCancelAnimSpec = tween<Float>(150)

/** 面板圆角。 */
private val PanelCornerRadius = 25.dp

/** 面板向外投射阴影预留的留白。 */
private val PopupShadowPadding = 24.dp

/** 玻璃盒为 spring 过冲额外撑出的余量。 */
private val PopupOvershootRoom = 14.dp

/** 弹窗内容相对面板的额外占位，定位时需扣回来。 */
private val PopupChrome = PopupShadowPadding + PopupOvershootRoom

/** 下拉弹窗相对锚点的补偿量。 */
private val DropdownAnchorOffsetX = 7.dp
private val DropdownAnchorOffsetY = 5.dp

private const val PopupGlassKey = "NexioSoftGlassLiveDropdownPopup"

private const val ShadowArgbLight = 0x1A000000
private const val ShadowArgbDark = 0x2E000000
private const val ShadowBlur = 12f
private const val ShadowExtend = 2f
private const val ShadowAlphaGain = 3.2f

private const val ContainerAlphaEnter = 7f
private const val ContainerAlphaExit = 3.6f
private const val ContentEnterFrom = 0.22f
private const val ContentEnterTo = 0.62f
private const val ContentExitFrom = 0.30f
private const val ContentExitTo = 0.78f
private const val CollapseIconKEnter = 3.2f
private const val CollapseIconKExit = 1.7f

/** layout 阶段算出的锚点角，普通对象，draw 同帧可读。 */
internal class DropdownPopupAnchors {
    var rightAligned: Boolean = false
    var showAbove: Boolean = false
}

/** 下拉弹窗定位：优先向下展开，空间不足翻到上方，最后居中。 */
private class DropdownPopupPositionProvider(
    private val offsetX: Int,
    private val offsetY: Int,
    private val chromePx: Int,
    private val safeTop: Int,
    private val safeBottom: Int,
    private val alignEnd: Boolean,
    private val anchors: DropdownPopupAnchors,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val windowBounds = IntRect(
            left = 0,
            top = safeTop,
            right = windowSize.width,
            bottom = (windowSize.height - safeBottom).coerceAtLeast(safeTop),
        )
        val panelWidth = (popupContentSize.width - 2 * chromePx).coerceAtLeast(1)
        val panelHeight = (popupContentSize.height - 2 * chromePx).coerceAtLeast(1)

        val spaceBelow = windowBounds.bottom - anchorBounds.bottom
        val spaceAbove = anchorBounds.top - windowBounds.top
        val panelTop: Int
        val showAbove: Boolean
        when {
            spaceBelow > panelHeight -> {
                panelTop = anchorBounds.top - offsetY
                showAbove = false
            }
            spaceAbove > panelHeight -> {
                panelTop = anchorBounds.bottom - panelHeight + offsetY
                showAbove = true
            }
            else -> {
                panelTop = anchorBounds.top + anchorBounds.height / 2 - panelHeight / 2
                showAbove = false
            }
        }

        val desiredLeft = if (alignEnd) {
            anchorBounds.right - panelWidth + offsetX
        } else {
            anchorBounds.left + offsetX
        }
        val maxLeft = (windowBounds.right - panelWidth).coerceAtLeast(windowBounds.left)
        val panelLeft = desiredLeft.coerceIn(windowBounds.left, maxLeft)
        val maxTop = (windowBounds.bottom - panelHeight).coerceAtLeast(windowBounds.top)
        val top = panelTop.coerceIn(windowBounds.top, maxTop)

        anchors.rightAligned = alignEnd
        anchors.showAbove = showAbove
        return IntOffset(panelLeft - chromePx, top - chromePx)
    }
}

/**
 * 弹层宿主：负责进出场动画、锚点定位与面板绘制。
 *
 * 与对话框一样挂进 Scaffold 的弹层宿主，在同一个窗口里绘制 —— 不用独立 `Popup` 窗口。
 * 独立窗口拿不到宿主窗口的 NavigationEventDispatcher（返回键 / 预测性返回全部失效），
 * 而且窗口销毁晚于组合，退场时会闪一帧空白。
 */
@Composable
internal fun DropdownPopupLayout(
    show: Boolean,
    maxHeight: Dp?,
    liquidGlassBackdrop: GlassBackdrop?,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    onFractionProgress: ((Float) -> Unit)?,
    collapseSize: IntSize?,
    collapseExtra: DpSize,
    collapseContent: (@Composable () -> Unit)?,
    dragSelectState: DropdownPanelDragSelectState?,
    renderInRootScaffold: Boolean = true,
    alignEnd: Boolean = true,
    anchorOffsetX: Dp = DropdownAnchorOffsetX,
    anchorOffsetY: Dp = DropdownAnchorOffsetY,
    content: @Composable () -> Unit,
) {
    val fractionProgress = remember { Animatable(0f) }
    val originProgress = remember { Animatable(0f) }
    val backProgress = remember { Animatable(0f) }
    val backScope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    val currentOnDismissFinished by rememberUpdatedState(onDismissFinished)
    val internalVisible = remember { mutableStateOf(false) }

    // 锚点：本组合位于触发区所在行内，父节点就是触发区。取窗口坐标供弹层定位。
    val anchorBounds = remember { mutableStateOf(IntRect.Zero) }
    Spacer(
        modifier = Modifier.onGloballyPositioned { coordinates ->
            val parent = coordinates.parentLayoutCoordinates ?: return@onGloballyPositioned
            val position = parent.positionInWindow()
            anchorBounds.value = IntRect(
                left = position.x.roundToInt(),
                top = position.y.roundToInt(),
                right = position.x.roundToInt() + parent.size.width,
                bottom = position.y.roundToInt() + parent.size.height,
            )
        },
    )

    val navBackState = rememberNavigationBack()
    val predictiveBackEnabled = LocalNexioPredictiveBackEnabled.current
    navBackState.handler(
        enabled = show,
        onBackCompleted = { currentOnDismiss.invoke() },
        onBackCancelled = {
            backScope.launch {
                if (backProgress.value > 0f) {
                    fractionProgress.animateTo(1f, BackCancelAnimSpec)
                    originProgress.animateTo(1f, BackCancelAnimSpec)
                    backProgress.snapTo(0f)
                }
            }
        },
    )
    // 手势进度逐帧驱动；单独协程避免手势期间反复取消/重启 LaunchedEffect
    LaunchedEffect(Unit) {
        snapshotFlow { navBackState.backProgress() }.collect { progress ->
            if (progress != null && predictiveBackEnabled) {
                backProgress.snapTo(progress)
                fractionProgress.snapTo(1f - progress)
                originProgress.snapTo(1f - progress)
            }
        }
    }

    LaunchedEffect(show) {
        if (show) {
            internalVisible.value = true
            launch { fractionProgress.animateTo(1f, FractionEnterAnimSpec) }
            launch { originProgress.animateTo(1f, OriginEnterAnimSpec) }
        } else {
            if (!internalVisible.value) return@LaunchedEffect
            launch { fractionProgress.animateTo(0f, FractionExitAnimSpec) }
            // 等锚点动画收尾后再整体退场
            originProgress.animateTo(0f, OriginExitAnimSpec)
            fractionProgress.snapTo(0f)
            originProgress.snapTo(0f)
            internalVisible.value = false
            currentOnDismissFinished.invoke()
        }
    }

    val currentOnFractionProgress by rememberUpdatedState(onFractionProgress)
    LaunchedEffect(Unit) {
        currentOnFractionProgress?.let { callback ->
            snapshotFlow { fractionProgress.value }.collect { callback(it) }
        }
    }

    if (!show && !internalVisible.value) return

    // 弹层列表：与对话框同一份，renderInRootScaffold 时挂到最外层 Scaffold。
    val states = if (renderInRootScaffold) {
        LocalNexioRootDialogStates.current ?: LocalNexioDialogStates.current
    } else {
        LocalNexioDialogStates.current
    } ?: return

    val entry = remember { NexioDialogEntry() }
    SideEffect {
        entry.content = {
            DropdownPopupOverlay(
                show = show,
                anchorBounds = { anchorBounds.value },
                maxHeight = maxHeight,
                liquidGlassBackdrop = liquidGlassBackdrop,
                fractionProgress = { fractionProgress.value },
                originProgress = { originProgress.value },
                onDismissRequest = { currentOnDismiss.invoke() },
                collapseSize = collapseSize,
                collapseExtra = collapseExtra,
                collapseContent = collapseContent,
                dragSelectState = dragSelectState,
                alignEnd = alignEnd,
                anchorOffsetX = anchorOffsetX,
                anchorOffsetY = anchorOffsetY,
                content = content,
            )
        }
    }
    DisposableEffect(states, entry) {
        states.add(entry)
        onDispose { states.remove(entry) }
    }
}

/** 弹层窗口内容：铺满宿主，空白处点击关闭，面板按锚点位置摆放。 */
@Composable
private fun DropdownPopupOverlay(
    show: Boolean,
    anchorBounds: () -> IntRect,
    maxHeight: Dp?,
    liquidGlassBackdrop: GlassBackdrop?,
    fractionProgress: () -> Float,
    originProgress: () -> Float,
    onDismissRequest: () -> Unit,
    collapseSize: IntSize?,
    collapseExtra: DpSize,
    collapseContent: (@Composable () -> Unit)?,
    dragSelectState: DropdownPanelDragSelectState?,
    alignEnd: Boolean,
    anchorOffsetX: Dp,
    anchorOffsetY: Dp,
    content: @Composable () -> Unit,
) {
    val anchors = remember { DropdownPopupAnchors() }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val windowInfo = LocalWindowInfo.current
    val safeDrawing = WindowInsets.safeDrawing
    val safeTop = safeDrawing.getTop(density)
    val safeBottom = safeDrawing.getBottom(density)
    val chromePx = with(density) { PopupChrome.roundToPx() }
    val offsetXPx = with(density) { anchorOffsetX.roundToPx() }
    val offsetYPx = with(density) { anchorOffsetY.roundToPx() }
    // End 对齐 = LTR 下贴锚点右缘、RTL 下贴左缘
    val resolvedAlignEnd = alignEnd == (layoutDirection == LayoutDirection.Ltr)
    val positionProvider = remember(chromePx, offsetXPx, offsetYPx, safeTop, safeBottom, resolvedAlignEnd, anchors) {
        DropdownPopupPositionProvider(
            offsetX = offsetXPx,
            offsetY = offsetYPx,
            chromePx = chromePx,
            safeTop = safeTop,
            safeBottom = safeBottom,
            alignEnd = resolvedAlignEnd,
            anchors = anchors,
        )
    }
    var hostPositionInWindow by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { hostPositionInWindow = it.positionInWindow() }
            .pointerInput(Unit) { detectTapGestures { onDismissRequest() } }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    constraints.copy(minWidth = 0, minHeight = 0),
                )
                val anchor = anchorBounds()
                if (anchor == IntRect.Zero) {
                    layout(constraints.maxWidth, constraints.maxHeight) { }
                } else {
                    val offset = positionProvider.calculatePosition(
                        anchorBounds = anchor,
                        windowSize = windowInfo.containerSize,
                        layoutDirection = layoutDirection,
                        popupContentSize = IntSize(placeable.width, placeable.height),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(
                            offset.x - hostPositionInWindow.x.roundToInt(),
                            offset.y - hostPositionInWindow.y.roundToInt(),
                        )
                    }
                }
            },
    ) {
        DropdownPopupPanel(
            show = show,
            maxHeight = maxHeight,
            fractionProgress = fractionProgress,
            originProgress = originProgress,
            anchors = anchors,
            liquidGlassBackdrop = liquidGlassBackdrop,
            collapseSize = collapseSize,
            collapseExtra = collapseExtra,
            collapseContent = collapseContent,
            dragSelectState = dragSelectState,
            content = content,
        )
    }
}

/** 面板：内容与玻璃在同一测量 pass 内完成，收起时从触发区尺寸真实插值。 */
@Composable
private fun DropdownPopupPanel(
    show: Boolean,
    maxHeight: Dp?,
    fractionProgress: () -> Float,
    originProgress: () -> Float,
    anchors: DropdownPopupAnchors,
    liquidGlassBackdrop: GlassBackdrop?,
    collapseSize: IntSize?,
    collapseExtra: DpSize,
    collapseContent: (@Composable () -> Unit)?,
    dragSelectState: DropdownPanelDragSelectState?,
    content: @Composable () -> Unit,
) {
    val localDensity = LocalDensity.current
    val isDark = NexioTheme.isDark
    val backgroundColor = NexioTheme.colorScheme.surfaceContainer
    val fallbackCollapsePx = with(localDensity) { 28.dp.toPx() }
    val extraWidth = with(localDensity) { collapseExtra.width.toPx() }
    val extraHeight = with(localDensity) { collapseExtra.height.toPx() }
    val collapseWidth = (collapseSize?.width?.takeIf { it > 0 }?.toFloat() ?: fallbackCollapsePx) + extraWidth
    val collapseHeight = (collapseSize?.height?.takeIf { it > 0 }?.toFloat() ?: fallbackCollapsePx) + extraHeight
    val overshootRoomPx = with(localDensity) { PopupOvershootRoom.toPx() }
    val cornerRadiusPx = with(localDensity) { PanelCornerRadius.toPx() }

    val glassEnabled = NexioMaterial.glassEffectsEnabled()
    val glassBlur = NexioMaterial.glassPanelBlurDp()
    val glassStyle = NexioMaterial.chromeGlassStyle(isDark = isDark, tone = BrightLargeTone)
    val surfaceAlpha = if (NexioMaterial.isNone()) {
        1f
    } else {
        NexioMaterial.glassSurfaceAlpha(if (isDark) 0.8f else 0.72f, isDark)
    }.coerceIn(0f, 1f)
    val useGlass = liquidGlassBackdrop != null && glassEnabled && isRenderEffectSupported()

    val glassEffects: GlassEffectScope.() -> Unit = remember(glassBlur, glassEnabled, glassStyle, cornerRadiusPx) {
        {
            if (glassEnabled) {
                blur(glassBlur.dp.toPx())
                runtimeShaderEffect(
                    key = PopupGlassKey,
                    shaderString = SOFT_GLASS_LIVE_SHADER,
                    uniformShaderName = "content",
                ) {
                    applySoftGlass(
                        style = glassStyle,
                        width = size.width,
                        height = size.height,
                        radiusPx = cornerRadiusPx,
                    )
                }
            }
        }
    }

    // 动画期间靠帧号触发重组刷新 shape 实例；静止时不重组
    val frame = remember { derivedStateOf { (fractionProgress() * 400f).roundToInt() } }.value

    val style = DropdownPanelStyle(
        cornerRadius = PanelCornerRadius,
        backgroundColor = backgroundColor,
        isDark = isDark,
        overshootRoomPx = overshootRoomPx,
        collapseWidth = collapseWidth,
        collapseHeight = collapseHeight,
        liquidGlassBackdrop = if (useGlass) liquidGlassBackdrop else null,
        glassEffects = glassEffects,
        surfaceAlpha = surfaceAlpha,
        frame = frame,
        fractionProgress = fractionProgress,
        originProgress = originProgress,
        isEntering = show,
        anchors = anchors,
    )

    val dragScope = rememberCoroutineScope()
    val dragHighlight = remember(dragScope, isDark) {
        InteractiveHighlight(
            animationScope = dragScope,
            isDark = { isDark },
        )
    }

    SubcomposeLayout(
        modifier = Modifier
            .padding(PopupShadowPadding)
            .then(
                if (dragSelectState != null) {
                    Modifier.dropdownPanelDragSelect(
                        state = dragSelectState,
                        fraction = { fractionProgress() },
                        hapticFeedback = LocalHapticFeedback.current,
                    )
                } else {
                    Modifier
                }
            )
            .then(dragHighlight.gestureModifier)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInRoot()
                dragSelectState?.panelTopInRoot = bounds.top
                dragSelectState?.panelLeftInRoot = bounds.left
            }
            .dropdownPanelDragTransform(
                fraction = { fractionProgress() },
                pressProgress = { dragHighlight.pressProgress },
                dragOffset = { dragHighlight.offset },
            ),
    ) { constraints ->
        val roomPx = overshootRoomPx.roundToInt()
        val maxContentWidth = if (constraints.maxWidth == Constraints.Infinity) {
            Constraints.Infinity
        } else {
            (constraints.maxWidth - 2 * roomPx).coerceAtLeast(0)
        }
        val heightCap = maxHeight?.roundToPx() ?: Constraints.Infinity
        val maxContentHeight = min(
            if (constraints.maxHeight == Constraints.Infinity) {
                Constraints.Infinity
            } else {
                (constraints.maxHeight - 2 * roomPx).coerceAtLeast(0)
            },
            heightCap,
        )

        val contentPlaceable = subcompose(PopupSlot.Content) {
            if (dragSelectState != null) {
                CompositionLocalProvider(LocalDropdownPanelDragSelect provides dragSelectState) {
                    DropdownPopupMenuLayer(style = style, content = content)
                }
            } else {
                DropdownPopupMenuLayer(style = style, content = content)
            }
        }.first().measure(
            constraints.copy(
                minWidth = 0,
                minHeight = 0,
                maxWidth = maxContentWidth,
                maxHeight = maxContentHeight,
            )
        )

        val naturalWidth = contentPlaceable.width.toFloat().coerceAtLeast(1f)
        val naturalHeight = contentPlaceable.height.toFloat().coerceAtLeast(1f)
        val canvasWidth = naturalWidth + 2 * overshootRoomPx
        val canvasHeight = naturalHeight + 2 * overshootRoomPx
        val rect = style.rectOf(naturalWidth, naturalHeight)

        val glassPlaceable = subcompose(PopupSlot.Glass) {
            DropdownGlassCanvas(
                style = style,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                rect = rect,
                collapseContent = collapseContent,
                highlight = dragHighlight,
            )
        }.first().measure(
            Constraints.fixed(canvasWidth.roundToInt(), canvasHeight.roundToInt()),
        )

        layout(canvasWidth.roundToInt(), canvasHeight.roundToInt()) {
            glassPlaceable.place(0, 0)
            contentPlaceable.place(roomPx, roomPx)
        }
    }
}

private enum class PopupSlot { Content, Glass }

/** 面板绘制参数。 */
private class DropdownPanelStyle(
    val cornerRadius: Dp,
    val backgroundColor: Color,
    val isDark: Boolean,
    val overshootRoomPx: Float,
    val collapseWidth: Float,
    val collapseHeight: Float,
    val liquidGlassBackdrop: GlassBackdrop?,
    val glassEffects: GlassEffectScope.() -> Unit,
    val surfaceAlpha: Float,
    val frame: Int,
    val fractionProgress: () -> Float,
    val originProgress: () -> Float,
    val isEntering: Boolean,
    private val anchors: DropdownPopupAnchors,
) {
    private val anchorRight: () -> Boolean = { anchors.rightAligned }
    private val anchorBottom: () -> Boolean = { anchors.showAbove }

    fun containerAlpha(fraction: Float): Float =
        (fraction * if (isEntering) ContainerAlphaEnter else ContainerAlphaExit).coerceIn(0f, 1f)

    fun contentAlpha(fraction: Float): Float {
        val from = if (isEntering) ContentEnterFrom else ContentExitFrom
        val to = if (isEntering) ContentEnterTo else ContentExitTo
        return ((fraction - from) / (to - from)).coerceIn(0f, 1f)
    }

    /** 驼峰模糊：两端清晰、中间最糊。 */
    fun contentBlur(fraction: Float): Dp =
        6.dp * (1f - abs(2f * fraction - 1f)).coerceIn(0f, 1f)

    /**
     * 面板矩形 [left, top, width, height]（px），坐标系原点在玻璃画布左上角。
     */
    fun rectOf(naturalWidth: Float, naturalHeight: Float): (Float, Float) -> FloatArray = { canvasWidth, canvasHeight ->
        val fraction = fractionProgress()
        val rawWidth = collapseWidth + (naturalWidth - collapseWidth) * fraction
        val rawHeight = collapseHeight + (naturalHeight - collapseHeight) * fraction
        val progress = originProgress()
        val scale = if (naturalWidth > 0f) rawWidth / naturalWidth else 1f
        val migration = 0.5f * progress * (1f - scale)
        val migrationX = (if (anchorRight()) -1f else 1f) * naturalWidth * migration
        val migrationY = (if (anchorBottom()) -1f else 1f) * naturalHeight * migration
        val rawLeft = (if (anchorRight()) naturalWidth - rawWidth else 0f) + migrationX
        val rawTop = (if (anchorBottom()) naturalHeight - rawHeight else 0f) + migrationY

        val maxWidth = maxOf(canvasWidth, naturalWidth + 2 * overshootRoomPx)
        val maxHeight = maxOf(canvasHeight, naturalHeight + 2 * overshootRoomPx)
        val width = rawWidth.coerceIn(0f, maxWidth)
        val height = rawHeight.coerceIn(0f, maxHeight)
        val left = (rawLeft + overshootRoomPx).coerceIn(0f, (maxWidth - width).coerceAtLeast(0f))
        val top = (rawTop + overshootRoomPx).coerceIn(0f, (maxHeight - height).coerceAtLeast(0f))
        floatArrayOf(left, top, width, height)
    }
}

/** 面板矩形 Shape，每帧换实例以强制轮廓重算。 */
private class AnimatedPanelRectShape(
    private val rectKey: List<Int>,
    private val cornerRadius: Dp,
    private val rectProvider: (Float, Float) -> FloatArray,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val rect = rectProvider(size.width, size.height)
        val base = ContinuousRoundedRectangle(cornerRadius)
            .createOutline(Size(rect[2], rect[3]), layoutDirection, density)
        val offset = Offset(rect[0], rect[1])
        return when (base) {
            is Outline.Rounded -> Outline.Rounded(base.roundRect.translate(offset))
            is Outline.Generic -> Outline.Generic(
                Path().apply {
                    addPath(base.path)
                    translate(offset)
                }
            )
            else -> base
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AnimatedPanelRectShape) return false
        return rectKey == other.rectKey
    }

    override fun hashCode(): Int = rectKey.hashCode()
}

/** 内容层：按宽度等比缩放并移到面板矩形中心。 */
@Composable
private fun DropdownPopupMenuLayer(
    style: DropdownPanelStyle,
    content: @Composable () -> Unit,
) {
    val dragSelect = LocalDropdownPanelDragSelect.current
    val clipShape = AnimatedPanelRectShape(
        rectKey = listOf(style.frame),
        cornerRadius = style.cornerRadius,
        rectProvider = { width, height ->
            val rect = style.rectOf(width, height)(
                width + 2 * style.overshootRoomPx,
                height + 2 * style.overshootRoomPx,
            )
            floatArrayOf(
                rect[0] - style.overshootRoomPx,
                rect[1] - style.overshootRoomPx,
                rect[2],
                rect[3],
            )
        },
    )

    Box(
        modifier = Modifier
            .clip(clipShape)
            .graphicsLayer {
                val fraction = style.fractionProgress()
                // 本节点实测尺寸就是内容自然尺寸，据此自算画布
                val rect = style.rectOf(size.width, size.height)(
                    size.width + 2 * style.overshootRoomPx,
                    size.height + 2 * style.overshootRoomPx,
                )
                val contentScale = if (size.width > 0f) rect[2] / size.width else 1f
                scaleX = contentScale
                scaleY = contentScale
                alpha = style.contentAlpha(fraction)
                translationX = rect[0] + rect[2] / 2f - (style.overshootRoomPx + size.width / 2f)
                translationY = rect[1] + rect[3] / 2f - (style.overshootRoomPx + size.height / 2f)
            }
            .blur(style.contentBlur(style.fractionProgress()))
            .then(
                if (dragSelect?.maskMode == DropdownMaskMode.Sliding) {
                    Modifier.dropdownPanelSlidingMask(dragSelect)
                } else {
                    Modifier
                }
            ),
    ) {
        content()
    }
}

/** 玻璃画布：外投射阴影 + 材质 + 按压高光 + 收起态内容。 */
@Composable
private fun DropdownGlassCanvas(
    style: DropdownPanelStyle,
    canvasWidth: Float,
    canvasHeight: Float,
    rect: (Float, Float) -> FloatArray,
    collapseContent: (@Composable () -> Unit)?,
    highlight: InteractiveHighlight,
) {
    val panelShape: Shape = AnimatedPanelRectShape(
        rectKey = listOf(style.frame, canvasWidth.roundToInt(), canvasHeight.roundToInt()),
        cornerRadius = style.cornerRadius,
        rectProvider = rect,
    )

    Box(
        modifier = Modifier.dropdownPanelShadow(
            rect = rect,
            cornerRadius = style.cornerRadius,
            isDark = style.isDark,
            spread = { style.containerAlpha(style.fractionProgress()) },
        ),
    ) {
        DropdownMaterialLayer(
            style = style,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            rect = rect,
        )

        // 按压高光夹在材质层与内容层之间
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(panelShape)
                .then(highlight.modifier),
        )

        if (collapseContent != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .graphicsLayer {
                        val enterK = if (style.isEntering) CollapseIconKEnter else CollapseIconKExit
                        val progress = (style.fractionProgress() * enterK).coerceIn(0f, 1f)
                        alpha = 1f - progress
                        val panelRect = rect(canvasWidth, canvasHeight)
                        translationX = panelRect[0] + panelRect[2] / 2f - size.width / 2f
                        translationY = panelRect[1] + panelRect[3] / 2f - size.height / 2f
                    },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    collapseContent()
                }
            }
        }
    }
}

/** 材质层：节点本身就是面板矩形，自带淡入淡出。 */
@Composable
private fun BoxScope.DropdownMaterialLayer(
    style: DropdownPanelStyle,
    canvasWidth: Float,
    canvasHeight: Float,
    rect: (Float, Float) -> FloatArray,
) {
    val panelShape: Shape = ContinuousRoundedRectangle(style.cornerRadius)
    val backdrop = style.liquidGlassBackdrop
    // 外层只负责把材质节点摆到面板矩形；绘制修饰符必须挂在「面板大小」的内层上，
    // 否则 clip / drawBackdrop 会以整块画布为坐标系，玻璃与采样整体偏移。
    Box(
        modifier = Modifier.layout { measurable, _ ->
            val panelRect = rect(canvasWidth, canvasHeight)
            val width = panelRect[2].roundToInt().coerceAtLeast(0)
            val height = panelRect[3].roundToInt().coerceAtLeast(0)
            val placeable = measurable.measure(Constraints.fixed(width, height))
            layout(canvasWidth.roundToInt(), canvasHeight.roundToInt()) {
                placeable.place(panelRect[0].roundToInt(), panelRect[1].roundToInt())
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = style.containerAlpha(style.fractionProgress()) }
                .clip(panelShape)
                // 玻璃层之下始终铺一层底色：弹层运行在独立窗口时采样可能不可用
                .drawBehind {
                    drawRect(style.backgroundColor.copy(alpha = style.surfaceAlpha))
                }
                .then(
                    if (backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shadow = null,
                            shape = { panelShape },
                            effects = style.glassEffects,
                            onDrawSurface = null,
                        )
                    } else {
                        Modifier
                    }
                ),
        )
    }
}

/** 面板外投射阴影：环形路径 + 模糊，强度随展开进度衰减。 */
private fun Modifier.dropdownPanelShadow(
    rect: (Float, Float) -> FloatArray,
    cornerRadius: Dp,
    isDark: Boolean,
    spread: () -> Float,
): Modifier = drawBehind {
    val strength = spread()
    if (strength <= 0.01f) return@drawBehind

    val panelRect = rect(size.width, size.height)
    val extend = ShadowExtend * density * strength
    val blurRadius = ShadowBlur * density * strength
    val radius = cornerRadius.toPx()
    val argb = if (isDark) ShadowArgbDark else ShadowArgbLight
    val shadowAlpha = (((argb ushr 24) * ShadowAlphaGain).coerceAtMost(255f)) / 255f

    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(
                    panelRect[0] - extend,
                    panelRect[1] - extend,
                    panelRect[0] + panelRect[2] + extend,
                    panelRect[1] + panelRect[3] + extend,
                ),
                cornerRadius = CornerRadius(radius + extend, radius + extend),
            ),
            Path.Direction.Clockwise,
        )
        addRoundRect(
            RoundRect(
                rect = Rect(
                    panelRect[0],
                    panelRect[1],
                    panelRect[0] + panelRect[2],
                    panelRect[1] + panelRect[3],
                ),
                cornerRadius = CornerRadius(radius, radius),
            ),
            Path.Direction.CounterClockwise,
        )
    }
    drawBlurredPathShadow(
        path = path,
        color = Color.Black.copy(alpha = shadowAlpha),
        blurRadius = blurRadius.coerceAtLeast(0.1f),
    )
}
