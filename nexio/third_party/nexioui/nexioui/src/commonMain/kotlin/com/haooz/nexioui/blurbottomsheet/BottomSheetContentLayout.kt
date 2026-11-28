package com.haooz.nexioui.blurbottomsheet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.NavigationEventState
import androidx.navigationevent.compose.rememberNavigationEventState
import com.haooz.nexioui.text.NexioText
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.platform.asComposeShader
import com.haooz.softglass.platform.createGlassRuntimeShader
import com.haooz.softglass.platform.isRuntimeShaderSupported
import com.haooz.softglass.shape.ContinuousRoundedRectangle
import kotlinx.coroutines.launch

/** 弹窗边缘高光着色器。 */
private const val SHEET_RIM_SHADER = """
uniform float2 uSize;
uniform float uRadius;
uniform float uEdgePx;
uniform float uTopAlpha;
uniform float uBottomAlpha;
uniform float2 uLight;
uniform float uFlat;
uniform float uCornerScale;

float sdRoundRect(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float2 normalAt(float2 p, float2 b, float r) {
    float e = 0.75;
    float dx = sdRoundRect(p + float2(e, 0.0), b, r) - sdRoundRect(p - float2(e, 0.0), b, r);
    float dy = sdRoundRect(p + float2(0.0, e), b, r) - sdRoundRect(p - float2(0.0, e), b, r);
    float2 g = float2(dx, dy);
    float len = length(g);
    return len > 1e-5 ? g / len : float2(0.0, -1.0);
}

half4 main(float2 fragCoord) {
    float2 hs = uSize * 0.5;
    float2 p = fragCoord - hs;
    float sd = sdRoundRect(p, hs, uRadius);
    if (sd > 0.0) {
        return half4(0.0);
    }
    float d = -sd;
    float2 n = normalAt(p, hs, uRadius);
    float ndl = dot(n, normalize(uLight));
    float2 qc = abs(p) - (hs - uRadius);
    float cornerness = smoothstep(0.0, uRadius * 0.55, min(qc.x, qc.y));
    float edge = uEdgePx * mix(1.0, uCornerScale, cornerness);
    float t = clamp(d / max(edge, 0.001), 0.0, 1.0);
    float f = 1.0 - smoothstep(uFlat, 1.0, t);
    float fromTop = p.y + hs.y;
    float fromBottom = hs.y - p.y;
    float topAmt = 1.0 - smoothstep(uRadius * 0.35, uRadius * 1.55, fromTop);
    float bottomAmt = clamp(-ndl, 0.0, 1.0) * (1.0 - smoothstep(0.0, uRadius * 1.3, fromBottom));
    float amount = topAmt * uTopAlpha + bottomAmt * uBottomAlpha;
    float a = clamp(f * amount, 0.0, 1.0);
    return half4(half3(1.0) * half(a), half(a));
}
"""

/** 弹窗上下方向的高光描边，直接作为画笔绘制，不建离屏层。 */
@Composable
internal fun Modifier.sheetRimHighlight(isDark: Boolean, cornerRadius: Dp): Modifier {
    if (!isRuntimeShaderSupported()) return this
    val density = LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }
    val edgePx = with(density) { 2.2.dp.toPx() }
    val flat = if (isDark) 0f else 0.45f
    val cornerScale = 1f
    val topAlpha = if (isDark) 0.28f else 1f
    val bottomAlpha = if (isDark) 0.09f else 0.55f
    val shader = remember(radiusPx, edgePx, topAlpha, bottomAlpha, flat, cornerScale) {
        createGlassRuntimeShader(SHEET_RIM_SHADER).apply {
            setFloatUniform("uRadius", radiusPx)
            setFloatUniform("uEdgePx", edgePx)
            setFloatUniform("uTopAlpha", topAlpha)
            setFloatUniform("uBottomAlpha", bottomAlpha)
            setFloatUniform("uLight", 0f, -1f)
            setFloatUniform("uFlat", flat)
            setFloatUniform("uCornerScale", cornerScale)
        }
    }
    val brush = remember(shader) { ShaderBrush(shader.asComposeShader()) }
    return this.drawWithContent {
        drawContent()
        shader.setFloatUniform("uSize", size.width, size.height)
        drawRect(brush = brush)
    }
}

/** 内容顶部随滚动渐隐的遮罩。 */
internal fun Modifier.sheetTopOpacityMask(
    height: Dp,
    strength: () -> Float,
): Modifier = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithCache {
    val maskHeight = height.toPx()
    val stops = arrayOf(
        0f to Color.White.copy(alpha = 0.85f),
        0.45f to Color.White.copy(alpha = 0.55f),
        0.7f to Color.White.copy(alpha = 0.32f),
        0.85f to Color.White.copy(alpha = 0.14f),
        0.93f to Color.White.copy(alpha = 0.05f),
        1f to Color.Transparent,
    )
    val brush = Brush.verticalGradient(
        colorStops = stops,
        startY = 0f,
        endY = maskHeight,
    )
    onDrawWithContent {
        drawContent()
        val progress = strength().coerceIn(0f, 1f)
        if (progress > 0f) {
            drawRect(
                brush = brush,
                size = Size(size.width, maskHeight),
                alpha = progress,
                blendMode = BlendMode.DstOut,
            )
        }
    }
}

/**
 * 弹窗内容的累计滚动状态。
 *
 * 向下滚动为负、回到顶部归零；顶部下拉时另记一份越界量，用于驱动顶栏材质。
 */
internal class SheetScrollState {

    var contentOffset by mutableFloatStateOf(0f)
        private set

    var overScrollOffset by mutableFloatStateOf(0f)
        private set

    val showTopBarMaterial: Boolean
        get() = contentOffset < 0f || overScrollOffset < 0f

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {

        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            if (available.y < 0f) overScrollOffset = available.y
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            contentOffset += consumed.y
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            overScrollOffset = 0f
            return Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            overScrollOffset = 0f
            return Velocity.Zero
        }
    }
}

@Composable
internal fun rememberSheetScrollState(): SheetScrollState = remember { SheetScrollState() }

/** 返回手势状态；宿主未提供返回分发器时只保留点击外部关闭。 */
internal class BlurBottomSheetBackState(
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
internal fun rememberBlurBottomSheetBackState(): BlurBottomSheetBackState {
    val owner = LocalNavigationEventDispatcherOwner.current
    return if (owner != null) {
        val navState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
        remember(navState) { BlurBottomSheetBackState(navState) }
    } else {
        remember { BlurBottomSheetBackState(null) }
    }
}

/** 顶部拖拽把手：按下放大，拖动时回传位移与松手速度。 */
@Composable
internal fun BlurBottomSheetDragHandle(
    onDrag: (Float) -> Unit = {},
    onDragStarted: suspend () -> Unit = {},
    onDragStopped: suspend (Float) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val pressScale = remember { Animatable(1f) }
    val pressWidth = remember { Animatable(45f) }
    var isPressing by remember { mutableStateOf(false) }
    val handleShape = remember { ContinuousRoundedRectangle(2.dp) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .zIndex(2f)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(requireUnconsumed = false)
                        isPressing = true
                        scope.launch {
                            launch { pressScale.animateTo(1.15f, tween(100)) }
                            launch { pressWidth.animateTo(55f, tween(100)) }
                        }
                        while (awaitPointerEvent().changes.any { it.pressed }) {
                            // 持续跟踪直到把手抬起。
                        }
                        isPressing = false
                        scope.launch {
                            launch { pressScale.animateTo(1f, tween(150)) }
                            launch { pressWidth.animateTo(45f, tween(150)) }
                        }
                    }
                }
            }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState(onDrag),
                onDragStarted = { onDragStarted() },
                onDragStopped = { onDragStopped(it) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(pressWidth.value.dp)
                .height(4.dp)
                .graphicsLayer { scaleY = pressScale.value }
                .clip(handleShape)
                .background(
                    NexioTheme.colorScheme.onSurfaceVariantSummary.copy(
                        alpha = 0.2f + if (isPressing) 0.15f else 0f,
                    ),
                ),
        )
    }
}

/**
 * 弹窗顶部渐隐底色与标题栏。
 *
 * @param titleBarHeight 标题栏固定高度，保证有无操作按钮时标题都垂直居中。
 * @param titleBarModifier 附加到标题栏（不含渐变层）的修饰符，用于平板版的水平避让。
 */
@Composable
internal fun BlurBottomSheetHeader(
    title: String,
    sheetBgColor: Color,
    titleBarHeight: Dp,
    titleBarModifier: Modifier = Modifier,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(titleBarHeight)
            .zIndex(1f)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to sheetBgColor,
                        0.2f to sheetBgColor.copy(alpha = 0.95f),
                        0.5f to sheetBgColor.copy(alpha = 0.8f),
                        0.8f to sheetBgColor.copy(alpha = 0.45f),
                        1.0f to sheetBgColor.copy(alpha = 0f),
                    ),
                ),
            ),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(titleBarHeight)
            .then(titleBarModifier)
            .zIndex(2f),
    ) {
        NexioText(
            text = title,
            modifier = Modifier.align(Alignment.Center),
            fontSize = NexioTheme.textStyles.title4.fontSize,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            color = NexioTheme.colorScheme.onSurface,
        )
        if (startAction != null) {
            Box(modifier = Modifier.align(Alignment.CenterStart)) {
                startAction()
            }
        }
        if (endAction != null) {
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                endAction()
            }
        }
    }
}
