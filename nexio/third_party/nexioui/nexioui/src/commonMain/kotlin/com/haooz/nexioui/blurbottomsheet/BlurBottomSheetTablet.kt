package com.haooz.nexioui.blurbottomsheet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.material.NexioMaterial
import com.haooz.nexioui.overlaydialog.DialogLayout
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.layerBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberCanvasBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 平板版底部弹层：居中悬浮矩形，从底部滑入。
 *
 * @param show 是否显示
 * @param title 标题文字
 * @param dimBackground 是否压暗背景
 * @param sheetMaxWidth 弹窗最大宽度
 * @param sheetMaxHeight 弹窗最大高度，未指定时取窗口高度的 80%
 * @param sheetBackgroundColor 弹窗背景颜色，null 则使用主题色
 * @param isBottomAligned 是否贴底对齐，false 时垂直居中
 * @param onDismissRequest 关闭回调
 * @param startAction 标题栏左侧操作按钮
 * @param endAction 标题栏右侧操作按钮
 * @param onSheetContentBackdropCreated 内容采样层创建回调
 * @param content 内容区域
 */
@Composable
fun BlurBottomSheetTablet(
    show: Boolean,
    title: String,
    dimBackground: Boolean = false,
    sheetMaxWidth: Dp = 560.dp,
    sheetMaxHeight: Dp = Dp.Unspecified,
    sheetBackgroundColor: Color? = null,
    isBottomAligned: Boolean = false,
    onDismissRequest: () -> Unit,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    onSheetContentBackdropCreated: ((GlassBackdrop?) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val visibleState = remember { mutableStateOf(show) }

    LaunchedEffect(show) {
        if (show) {
            visibleState.value = true
        }
    }

    DialogLayout(
        visible = visibleState.value,
        renderInRootScaffold = true,
    ) {
        BlurBottomSheetTabletContent(
            show = show,
            visibleState = visibleState,
            title = title,
            dimBackground = dimBackground,
            sheetMaxWidth = sheetMaxWidth,
            sheetMaxHeight = sheetMaxHeight,
            sheetBackgroundColor = sheetBackgroundColor,
            isBottomAligned = isBottomAligned,
            onDismissRequest = onDismissRequest,
            startAction = startAction,
            endAction = endAction,
            onSheetContentBackdropCreated = onSheetContentBackdropCreated,
            content = content,
        )
    }
}

@Composable
private fun BlurBottomSheetTabletContent(
    show: Boolean,
    visibleState: MutableState<Boolean>,
    title: String,
    dimBackground: Boolean = false,
    sheetMaxWidth: Dp = 560.dp,
    sheetMaxHeight: Dp = Dp.Unspecified,
    sheetBackgroundColor: Color? = null,
    isBottomAligned: Boolean = false,
    onDismissRequest: () -> Unit,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    onSheetContentBackdropCreated: ((GlassBackdrop?) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val animationProgress = remember { Animatable(0f) }
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    // 滑入动画用的窗口高度在组合期读取一次，避免动画期间逐帧换算。
    val windowHeightPx = with(density) { windowInfo.containerSize.height.toFloat() }
    val backState = rememberBlurBottomSheetBackState()
    val backProgress = remember { Animatable(0f) }
    val detentState = remember { BlurBottomSheetDetentState() }
    val isDetentExpanded by remember(detentState) {
        derivedStateOf { detentState.expansionFraction > 0.001f }
    }

    val isDark = NexioTheme.isDark
    // 与手机端一致：莫奈取色跟随 surfaceContainer，非莫奈保持硬编码中性色。
    val sheetBgColor = sheetBackgroundColor ?: if (NexioTheme.isDynamicColor) {
        NexioTheme.colorScheme.surfaceContainer
    } else if (isDark) {
        Color(0xFF1E1E1E)
    } else {
        Color(0xFFF2F2F2)
    }

    LaunchedEffect(show) {
        if (show) {
            detentState.reset()
            backProgress.snapTo(0f)
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 500,
                    easing = CubicBezierEasing(0.34f, 1.12f, 0.3f, 1f),
                ),
            )
        } else {
            animationProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(380, easing = CubicBezierEasing(0.34f, 1f, 0.3f, 1f)),
            )
            visibleState.value = false
        }
    }

    val dimModifier = if (dimBackground) {
        Modifier.drawBehind {
            drawRect(Color.Black.copy(alpha = 0.2f * animationProgress.value * (1f - backProgress.value)))
        }
    } else {
        Modifier
    }

    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val coroutineScope = rememberCoroutineScope()

    backState.handler(
        enabled = show,
        onBackCancelled = {
            coroutineScope.launch {
                backProgress.animateTo(0f, animationSpec = tween(150))
            }
        },
        onBackCompleted = {
            currentOnDismissRequest()
        },
    )

    LaunchedEffect(Unit) {
        snapshotFlow { backState.backProgress() }
            .collect { progress ->
                if (progress != null) {
                    backProgress.snapTo(progress)
                }
            }
    }

    // 形状固定引用，避免采样层重编译；平板弹窗居中悬浮，圆角不跟随屏幕。
    val sheetShape = remember { ContinuousRoundedRectangle(38.dp) }
    val windowWidth = with(density) { windowInfo.containerSize.width.toDp() }
    val horizontalGap = ((windowWidth - minOf(sheetMaxWidth, windowWidth)) / 2).coerceAtLeast(0.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(dimModifier)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { currentOnDismissRequest() })
            },
        contentAlignment = if (isBottomAligned) Alignment.BottomCenter else Alignment.Center,
    ) {
        val sheetModifier = Modifier
            .graphicsLayer {
                val progress = animationProgress.value
                // 从屏幕底部滑入；返回手势把弹窗向下推出屏幕。
                translationY = windowHeightPx * (1f - progress) +
                    backProgress.value * windowHeightPx +
                    detentState.dismissOffsetPx
            }

        Box(
            modifier = sheetModifier
                .width(sheetMaxWidth)
                .fillMaxWidth()
                .heightIn(
                    max = if (sheetMaxHeight != Dp.Unspecified) {
                        sheetMaxHeight
                    } else {
                        with(density) { windowInfo.containerSize.height.toDp() } * 0.8f
                    },
                )
                .blurBottomSheetDetentHeight(detentState)
                .then(if (isBottomAligned) Modifier.padding(bottom = 20.dp) else Modifier)
                .clip(sheetShape)
                .background(sheetBgColor)
                .sheetRimHighlight(isDark, 38.dp)
                // 居中窄条已与窗口边缘留有水平间距。
                .consumeWindowInsets(PaddingValues(horizontal = horizontalGap))
                .onGloballyPositioned { coordinates ->
                    detentState.updateCollapsedHeight(coordinates.size.height)
                }
                .pointerInput(Unit) {
                    // 消费弹窗空白处的点击，防止穿透到背景层触发关闭。
                    detectTapGestures(onTap = {})
                }
                .semantics {
                    onClick(label = "关闭") {
                        onDismissRequest()
                        true
                    }
                },
            content = {
                val scrollState = rememberSheetScrollState()
                val showButtonShadow by remember(scrollState) {
                    derivedStateOf { scrollState.showTopBarMaterial }
                }
                val shadowAlpha = remember { Animatable(0f) }
                val backdropAlpha = remember { Animatable(0f) }
                val topBarMaterial = remember { SheetTopBarMaterial(backdropAlpha, shadowAlpha) }
                LaunchedEffect(showButtonShadow) {
                    val target = if (showButtonShadow) 1f else 0f
                    val spec: AnimationSpec<Float> = if (showButtonShadow) {
                        spring(dampingRatio = 1f, stiffness = 110f)
                    } else {
                        spring(dampingRatio = 1f, stiffness = 250f)
                    }
                    launch { shadowAlpha.animateTo(target, spec) }
                    launch { backdropAlpha.animateTo(target, spec) }
                }

                // 录制时先铺弹窗底色，空隙处采到的是实底。
                val sheetContentBackdrop = rememberLayerBackdrop(
                    onDraw = remember(sheetBgColor) {
                        {
                            drawRect(sheetBgColor)
                            drawContent()
                        }
                    },
                )
                LaunchedEffect(sheetContentBackdrop) {
                    onSheetContentBackdropCreated?.invoke(sheetContentBackdrop)
                }

                val materialNone = NexioMaterial.isNone()
                var sheetBackdropMounted by remember { mutableStateOf(false) }
                LaunchedEffect(show, materialNone) {
                    if (show && !materialNone) {
                        delay(80.milliseconds)
                        sheetBackdropMounted = true
                    }
                }

                val placeholderOnDraw: DrawScope.() -> Unit = remember(sheetBgColor) {
                    {
                        drawRect(
                            color = sheetBgColor,
                            topLeft = Offset(-size.width, -size.height),
                            size = Size(size.width * 3f, size.height * 3f),
                        )
                    }
                }
                val placeholderBackdrop = rememberCanvasBackdrop(placeholderOnDraw)

                CompositionLocalProvider(
                    LocalSheetTopBarMaterial provides topBarMaterial,
                    LocalSheetContentBackdrop provides
                        if (sheetBackdropMounted) sheetContentBackdrop else placeholderBackdrop,
                    LocalBlurBottomSheetContentExpanded provides isDetentExpanded,
                ) {
                    BlurBottomSheetDragHandle(
                        onDrag = detentState::dragBy,
                        onDragStarted = { detentState.beginDrag() },
                        onDragStopped = { velocity ->
                            detentState.settle(
                                velocityY = velocity,
                                velocityThresholdPx = with(density) { 800.dp.toPx() },
                                dismissThresholdPx = with(density) { 150.dp.toPx() },
                                onDismiss = currentOnDismissRequest,
                            )
                        },
                    )

                    // 内容区域：采样层捕获「底色 + 内容」。
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isDetentExpanded) {
                                    Modifier.fillMaxHeight()
                                } else {
                                    Modifier.wrapContentHeight()
                                },
                            )
                            .nestedScroll(scrollState.nestedScrollConnection)
                            .sheetTopOpacityMask(82.dp) { shadowAlpha.value }
                            .then(
                                if (sheetBackdropMounted) {
                                    Modifier.layerBackdrop(sheetContentBackdrop)
                                } else {
                                    Modifier
                                },
                            )
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                    ) {
                        content()
                    }

                    BlurBottomSheetHeader(
                        title = title,
                        sheetBgColor = sheetBgColor,
                        titleBarHeight = 74.dp,
                        titleBarModifier = Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                        ),
                        startAction = startAction,
                        endAction = endAction,
                    )
                }
            },
        )
    }
}
