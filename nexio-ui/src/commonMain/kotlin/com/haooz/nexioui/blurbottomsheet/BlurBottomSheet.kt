package com.haooz.nexioui.blurbottomsheet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
 * 顶栏材质动画值载体：由 [BlurBottomSheet] 下发，供标题栏动作按钮读取，
 * 让玻璃背景与阴影随内容滚动渐变。
 *
 * 持有 [Animatable] 而非快照值，使 provider 下发稳定实例，动画期间不会逐帧重组整个弹窗。
 */
@Stable
class SheetTopBarMaterial internal constructor(
    internal val backdropAlphaAnimatable: Animatable<Float, AnimationVector1D>,
    internal val shadowAlphaAnimatable: Animatable<Float, AnimationVector1D>,
) {
    val backdropAlpha: Float get() = backdropAlphaAnimatable.value
    val shadowAlpha: Float get() = shadowAlphaAnimatable.value
}

/** 顶栏材质入口，标题栏动作按钮从这里读取动画值。 */
val LocalSheetTopBarMaterial = compositionLocalOf {
    SheetTopBarMaterial(Animatable(1f), Animatable(1f))
}

/** 弹窗内容自身的采样层，玻璃组件应从这里读取。 */
val LocalSheetContentBackdrop = compositionLocalOf<GlassBackdrop?> { null }

/**
 * 非快照的采样层持有者，配合 `onSheetContentBackdropCreated` 使用。
 *
 * 供弹窗外部、读不到 [LocalSheetContentBackdrop] 的二级弹层采样弹窗内容。
 */
class BackdropHolder {
    var value: GlassBackdrop? = null
}

/**
 * 底部弹层：打开时高度贴合内容，拖动顶部把手可拉满全屏，下拉关闭。
 *
 * @param show 是否显示
 * @param title 标题文字
 * @param dimBackground 是否压暗背景
 * @param sheetBackgroundColor 弹窗背景颜色，null 则使用主题色
 * @param sheetMaxWidth 弹窗最大宽度，未指定时铺满窗口
 * @param onDismissRequest 关闭回调
 * @param startAction 标题栏左侧操作按钮
 * @param endAction 标题栏右侧操作按钮
 * @param onSheetContentBackdropCreated 内容采样层创建回调
 * @param content 内容区域
 */
@Composable
fun BlurBottomSheet(
    show: Boolean,
    title: String,
    dimBackground: Boolean = false,
    sheetBackgroundColor: Color? = null,
    sheetMaxWidth: Dp = Dp.Unspecified,
    onDismissRequest: () -> Unit,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    onSheetContentBackdropCreated: ((GlassBackdrop?) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val visibleState = remember { mutableStateOf(show) }
    // 显示时立即可见，隐藏时等退出动画播完再移除。
    LaunchedEffect(show) {
        if (show) {
            visibleState.value = true
        }
    }

    DialogLayout(
        visible = visibleState.value,
        renderInRootScaffold = true,
    ) {
        BlurBottomSheetContent(
            show = show,
            visibleState = visibleState,
            title = title,
            dimBackground = dimBackground,
            sheetBackgroundColor = sheetBackgroundColor,
            onDismissRequest = onDismissRequest,
            startAction = startAction,
            endAction = endAction,
            onSheetContentBackdropCreated = onSheetContentBackdropCreated,
            sheetMaxWidth = sheetMaxWidth,
            content = content,
        )
    }
}

@Composable
private fun BlurBottomSheetContent(
    show: Boolean,
    visibleState: MutableState<Boolean>,
    title: String,
    dimBackground: Boolean = false,
    sheetBackgroundColor: Color? = null,
    sheetMaxWidth: Dp = Dp.Unspecified,
    onDismissRequest: () -> Unit,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    onSheetContentBackdropCreated: ((GlassBackdrop?) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val animationProgress = remember { Animatable(0f) }
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val sheetHeightPx = remember { mutableIntStateOf(0) }
    val imeInsets = WindowInsets.ime
    val backState = rememberBlurBottomSheetBackState()
    val backProgress = remember { Animatable(0f) }
    val detentState = remember { BlurBottomSheetDetentState() }
    val isDetentExpanded by remember(detentState) {
        derivedStateOf { detentState.expansionFraction > 0.001f }
    }

    val isDark = NexioTheme.isDark
    // sheet 底色：莫奈取色跟随 surfaceContainer；非莫奈保持硬编码（暗 #1E1E1E / 亮 #F2F2F2）。
    val sheetBgColor = sheetBackgroundColor ?: if (NexioTheme.isDynamicColor) {
        NexioTheme.colorScheme.surfaceContainer
    } else if (isDark) {
        Color(0xFF1E1E1E)
    } else {
        Color(0xFFF2F2F2)
    }
    val dismissThresholdPx = with(density) { 150.dp.toPx() }
    val velocityThresholdPx = with(density) { 800.dp.toPx() }

    // 圆角固定，不跟随屏幕物理圆角。
    val sheetShape = remember { ContinuousRoundedRectangle(36.dp) }
    val windowWidth = with(density) { windowInfo.containerSize.width.toDp() }
    val horizontalGap = if (sheetMaxWidth != Dp.Unspecified) {
        ((windowWidth - minOf(sheetMaxWidth, windowWidth)) / 2).coerceAtLeast(0.dp)
    } else {
        0.dp
    }

    // 显示 / 隐藏动画同时驱动位移与遮罩透明度。
    LaunchedEffect(show) {
        if (show) {
            detentState.reset()
            backProgress.snapTo(0f)
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 480,
                    easing = CubicBezierEasing(0.34f, 1.12f, 0.3f, 1f),
                ),
            )
        } else {
            animationProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(320, easing = CubicBezierEasing(0.34f, 1f, 0.3f, 1f)),
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

    // 回调身份不稳定，不能作为手势协程的 key。
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(dimModifier)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { currentOnDismissRequest() })
            },
    ) {
        val windowHeightPx = with(density) { windowInfo.containerSize.height.toFloat() }
        val sheetModifier = Modifier
            .graphicsLayer {
                val progress = animationProgress.value
                val currentHeight = sheetHeightPx.intValue.toFloat()
                val baseOffset = if (currentHeight > 0) currentHeight else windowHeightPx
                translationY = baseOffset * (1f - progress) +
                    backProgress.value * currentHeight +
                    detentState.dismissOffsetPx
            }
            // 水平系统栏避让加在外壳上，让整张卡片一起内缩。
            .then(
                if (sheetMaxWidth == Dp.Unspecified) {
                    Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                } else {
                    Modifier
                },
            )

        // 卡片整体下移：全屏态时上沿落在状态栏下方，半屏态靠这段位移把底边溢出屏幕。
        val sheetOffsetDpValue = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 5.dp

        Box(
            modifier = sheetModifier
                .offset(y = sheetOffsetDpValue)
                .align(Alignment.BottomCenter)
                .then(
                    if (sheetMaxWidth != Dp.Unspecified) {
                        Modifier.width(sheetMaxWidth).fillMaxWidth()
                    } else {
                        Modifier.fillMaxWidth()
                    },
                )
                .heightIn(max = with(density) { windowInfo.containerSize.height.toDp() })
                .blurBottomSheetDetentHeight(detentState)
                .onGloballyPositioned { coordinates ->
                    if (imeInsets.getBottom(density) == 0) {
                        val newHeight = coordinates.size.height
                        if (sheetHeightPx.intValue != newHeight) {
                            sheetHeightPx.intValue = newHeight
                        }
                        detentState.updateCollapsedHeight(newHeight)
                    }
                }
                .imePadding()
                .clip(sheetShape)
                .background(sheetBgColor)
                .sheetRimHighlight(isDark, 36.dp)
                .consumeWindowInsets(PaddingValues(horizontal = horizontalGap))
                .pointerInput(Unit) {
                    // 消费空白处点击，防止穿透到背景层触发关闭。
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

                // 录制时先铺弹窗底色，使渐进模糊底边与外壳颜色对齐。
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

                // 进入动画早期挂载采样层，避开动画结束后才编译带来的卡顿。
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
                                velocityThresholdPx = velocityThresholdPx,
                                dismissThresholdPx = dismissThresholdPx,
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
                            .sheetTopOpacityMask(84.dp) { shadowAlpha.value }
                            .then(
                                if (sheetBackdropMounted) {
                                    Modifier.layerBackdrop(sheetContentBackdrop)
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        content()
                    }

                    BlurBottomSheetHeader(
                        title = title,
                        sheetBgColor = sheetBgColor,
                        titleBarHeight = 76.dp,
                        startAction = startAction,
                        endAction = endAction,
                    )
                }
            },
        )
    }
}
