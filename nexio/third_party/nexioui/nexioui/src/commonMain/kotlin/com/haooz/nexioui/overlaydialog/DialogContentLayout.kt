package com.haooz.nexioui.overlaydialog

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.NavigationEventState
import androidx.navigationevent.compose.rememberNavigationEventState
import com.haooz.nexioui.material.NexioGlassStyle
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
import com.haooz.softglass.platform.isRenderEffectSupported
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlin.math.pow
import kotlinx.coroutines.launch

/** 对话框面板的柔光 shader 缓存键。 */
private const val DIALOG_GLASS_KEY = "NexioSoftGlassLiveDialog"

/** 减速曲线：factor 越大，起步越快、收尾越缓。 */
private class DecelerateEasing(private val factor: Float = 1f) : Easing {
    override fun transform(fraction: Float): Float =
        if (factor == 1f) {
            1f - (1f - fraction) * (1f - fraction)
        } else {
            1f - (1f - fraction).pow(2f * factor)
        }
}

/** 返回手势状态；宿主未提供返回分发器时为 null，此时只保留点击外部关闭。 */
private class DialogBackState(
    private val state: NavigationEventState<out NavigationEventInfo>?,
) {
    /** 手势进度 0f→1f；非手势期间为 null。 */
    fun backProgress(): Float? {
        val transition = state?.transitionState ?: return null
        return if (transition is NavigationEventTransitionState.InProgress &&
            transition.direction == NavigationEventTransitionState.TRANSITIONING_BACK
        ) {
            transition.latestEvent.progress
        } else {
            null
        }
    }

    @Composable
    fun handler(
        enabled: Boolean,
        onBackCompleted: () -> Unit,
        onBackCancelled: () -> Unit,
    ) {
        val current = state ?: return
        NavigationBackHandler(
            state = current,
            isBackEnabled = enabled,
            onBackCancelled = onBackCancelled,
            onBackCompleted = onBackCompleted,
        )
    }
}

@Composable
private fun rememberDialogBackState(): DialogBackState {
    val owner = LocalNavigationEventDispatcherOwner.current
    return if (owner != null) {
        val navState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
        remember(navState) { DialogBackState(navState) }
    } else {
        remember { DialogBackState(null) }
    }
}

/**
 * 对话框的面板布局与进出场状态机。
 *
 * 手机贴底、宽屏居中；负责蒙层、圆角、玻璃背景、预测性返回跟手和内容排版。
 */
@Composable
internal fun DialogContentLayout(
    show: Boolean,
    titleColor: Color,
    summaryColor: Color,
    backgroundColor: Color,
    outsideMargin: DpSize,
    insideMargin: DpSize,
    modifier: Modifier = Modifier,
    title: String? = null,
    summary: String? = null,
    liquidGlassBackdrop: GlassBackdrop? = null,
    enableWindowDim: Boolean = true,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    enablePredictiveBackGesture: Boolean = true,
    defaultWindowInsetsPadding: Boolean = true,
    renderInRootScaffold: Boolean = true,
    content: @Composable () -> Unit,
) {
    val animationProgress = remember { Animatable(0f, visibilityThreshold = 0.0001f) }
    val dimProgress = remember { Animatable(0f) }
    val currentOnDismissFinished by rememberUpdatedState(onDismissFinished)
    val internalVisible = remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val keyboardController = LocalSoftwareKeyboardController.current
    val isLargeScreen = DialogDefaults.isLargeScreen()

    LaunchedEffect(show) {
        // 动画规格按进场瞬间的窗口宽度取定，窗口在动画中途跨断点也不会换曲线。
        val largeAtStart = isLargeScreen
        if (show) {
            internalVisible.value = true
            if (enableWindowDim) {
                launch { dimProgress.animateTo(1f, tween(300, easing = DecelerateEasing(1.5f))) }
            }
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = if (largeAtStart) {
                    spring(dampingRatio = 0.9f, stiffness = 380f, visibilityThreshold = 0.0001f)
                } else {
                    spring(dampingRatio = 0.88f, stiffness = 450f, visibilityThreshold = 0.0001f)
                },
            )
        } else {
            if (!internalVisible.value) return@LaunchedEffect
            if (imeInsets.getBottom(density) > 0) {
                keyboardController?.hide()
            }
            if (enableWindowDim) {
                launch { dimProgress.animateTo(0f, tween(250, easing = DecelerateEasing(1.5f))) }
            }
            animationProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 260, easing = DecelerateEasing(1.5f)),
            )
            dimProgress.snapTo(0f)
            internalVisible.value = false
            currentOnDismissFinished?.invoke()
        }
    }

    if (!show && !internalVisible.value) return

    val coroutineScope = rememberCoroutineScope()
    val dimAlpha = remember { mutableFloatStateOf(1f) }
    val dialogHeightPx = remember { mutableIntStateOf(0) }
    val backProgress = remember { Animatable(0f) }
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val windowInfo = LocalWindowInfo.current
    val backState = rememberDialogBackState()

    val requestDismiss: () -> Unit = remember {
        { currentOnDismissRequest?.invoke() }
    }

    val resetGesture: suspend () -> Unit = remember {
        {
            backProgress.animateTo(0f, animationSpec = tween(durationMillis = 150))
            animate(dimAlpha.floatValue, 1f, animationSpec = tween(durationMillis = 150)) { value, _ ->
                dimAlpha.floatValue = value
            }
        }
    }

    DialogLayout(
        visible = internalVisible.value,
        renderInRootScaffold = renderInRootScaffold,
    ) {
        backState.handler(
            enabled = show,
            onBackCancelled = {
                coroutineScope.launch { resetGesture() }
            },
            onBackCompleted = { requestDismiss() },
        )

        LaunchedEffect(Unit) {
            // 不响应跟手时连进度都不订阅，避免弹窗被拖动；返回键仍由上面的 handler 消费。
            if (!enablePredictiveBackGesture) return@LaunchedEffect
            snapshotFlow { backState.backProgress() }
                .collect { progress ->
                    if (progress != null) {
                        backProgress.snapTo(progress)
                        dimAlpha.floatValue = 1f - progress
                    }
                }
        }

        if (enableWindowDim) {
            val baseColor = DialogDefaults.windowDimmingColor()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            baseColor.copy(
                                alpha = baseColor.alpha * dimAlpha.floatValue * dimProgress.value * 0.6f,
                            ),
                        )
                    },
            )
        }

        val contentModifier = modifier.graphicsLayer {
            val progress = animationProgress.value
            if (isLargeScreen) {
                val scale = 0.8f + 0.2f * progress
                scaleX = scale
                scaleY = scale
                alpha = progress
            } else {
                translationY = (1f - progress) * windowInfo.containerSize.height.toFloat()
                alpha = 1f
            }
        }

        DialogContent(
            title = title,
            titleColor = titleColor,
            summary = summary,
            summaryColor = summaryColor,
            backgroundColor = backgroundColor,
            outsideMargin = outsideMargin,
            insideMargin = insideMargin,
            defaultWindowInsetsPadding = defaultWindowInsetsPadding,
            backProgress = backProgress,
            dialogHeightPx = dialogHeightPx,
            onDismissRequest = requestDismiss,
            modifier = contentModifier,
            liquidGlassBackdrop = liquidGlassBackdrop,
            content = content,
        )
    }
}

@Composable
internal fun DialogContent(
    title: String?,
    titleColor: Color,
    summary: String?,
    summaryColor: Color,
    backgroundColor: Color,
    outsideMargin: DpSize,
    insideMargin: DpSize,
    defaultWindowInsetsPadding: Boolean,
    backProgress: Animatable<Float, *>,
    dialogHeightPx: MutableIntState,
    onDismissRequest: (() -> Unit)?,
    modifier: Modifier = Modifier,
    liquidGlassBackdrop: GlassBackdrop? = null,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val windowHeight = with(density) { windowInfo.containerSize.height.toDp() }
    val isLargeScreen = DialogDefaults.isLargeScreen()
    val isDark = NexioTheme.isDark
    val contentAlignment = remember(isLargeScreen) {
        if (isLargeScreen) Alignment.Center else Alignment.BottomCenter
    }
    val bottomCornerRadius = remember(isLargeScreen, outsideMargin.width) {
        val offset = if (isLargeScreen) 0.dp else outsideMargin.width
        (DialogDefaults.screenCornerRadius - offset).coerceAtLeast(32.dp)
    }
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)

    val safeDrawingInsets = WindowInsets.safeDrawing
    // 底部 inset 不在这里吃，贴底弹窗的底部间距统一交给 bottomOutsideMargin。
    val dialogInsets = safeDrawingInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

    // 贴底弹窗的底部间距，与底部浮动导航栏同一套算法。
    val bottomOutsideMargin = if (isLargeScreen) {
        outsideMargin.height
    } else {
        maxOf(
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 2.dp,
            22.dp,
        )
    }
    // 预测返回时推出屏幕的额外位移 = 它到屏幕底的距离；预转 px，避免每帧 toPx()。
    val extraBottomPaddingPx = remember(density, bottomOutsideMargin, isLargeScreen) {
        if (isLargeScreen) 0f else with(density) { bottomOutsideMargin.toPx() }
    }

    // 玻璃背景：backdrop 存在、渲染能力可用且材质未关闭时才启用，否则退回实色底。
    val glassActive = liquidGlassBackdrop != null &&
        isRenderEffectSupported() &&
        NexioMaterial.glassEffectsEnabled()
    val dialogBlur = NexioMaterial.glassDialogBlurDp()
    val glassStyle = NexioMaterial.chromeGlassStyle(
        base = NexioGlassStyle,
        isDark = isDark,
        fill = BarFill,
        tone = BrightLargeTone,
    )
    val cornerRadiusPx = with(density) { bottomCornerRadius.toPx() }
    // drawBackdrop 按引用比较 effects，lambda 每帧新建会让节点重新录制采样层。
    val dialogEffects: GlassEffectScope.() -> Unit = remember(glassActive, dialogBlur, glassStyle, cornerRadiusPx) {
        {
            if (glassActive) {
                blur(dialogBlur.dp.toPx())
                runtimeShaderEffect(
                    key = DIALOG_GLASS_KEY,
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
    val dialogShapeBlock: () -> Shape = remember(bottomCornerRadius) {
        { ContinuousRoundedRectangle(bottomCornerRadius) }
    }

    val contentModifier = modifier
        .widthIn(max = DialogDefaults.MaxWidth)
        .heightIn(max = if (isLargeScreen) windowHeight * (2f / 3f) else Dp.Unspecified)
        .onGloballyPositioned { coordinates ->
            dialogHeightPx.intValue = coordinates.size.height
        }
        .graphicsLayer {
            // 返回跟手：宽屏缩放，手机整体下移。
            if (isLargeScreen) {
                val scale = 1f - (backProgress.value * 0.2f)
                scaleX = scale
                scaleY = scale
            } else {
                val maxOffset = if (dialogHeightPx.intValue > 0) {
                    dialogHeightPx.intValue.toFloat() + extraBottomPaddingPx
                } else {
                    500f
                }
                translationY = backProgress.value * maxOffset
            }
        }
        .pointerInput(Unit) {
            detectTapGestures { /* 消费面板内的点击 */ }
        }
        .clip(ContinuousRoundedRectangle(bottomCornerRadius))
        .then(
            if (glassActive) {
                Modifier.drawBackdrop(
                    backdrop = liquidGlassBackdrop,
                    shape = dialogShapeBlock,
                    effects = dialogEffects,
                )
            } else {
                Modifier
            },
        )
        .background(
            // 玻璃自己画底色与折射；无玻璃时用实色保证文字对比度。
            color = if (glassActive) Color.Transparent else backgroundColor,
            shape = ContinuousRoundedRectangle(bottomCornerRadius),
        )
        .padding(horizontal = insideMargin.width, vertical = insideMargin.height)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnDismiss?.invoke() },
                )
            }
            .semantics {
                onClick(label = "Dismiss") {
                    currentOnDismiss?.invoke()
                    true
                }
            }
            .then(
                if (defaultWindowInsetsPadding) {
                    Modifier.windowInsetsPadding(dialogInsets).imePadding()
                } else {
                    Modifier.padding(top = safeDrawingInsets.asPaddingValues().calculateTopPadding())
                },
            )
            .padding(horizontal = outsideMargin.width)
            .padding(bottom = bottomOutsideMargin),
    ) {
        Column(
            modifier = contentModifier.align(contentAlignment),
        ) {
            title?.let {
                NexioText(
                    text = it,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    color = titleColor,
                    fontSize = NexioTheme.textStyles.title4.fontSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
            summary?.let {
                NexioText(
                    text = it,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    color = summaryColor,
                    fontSize = NexioTheme.textStyles.body1.fontSize,
                    textAlign = TextAlign.Center,
                )
            }
            content()
        }
    }
}
