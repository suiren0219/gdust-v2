// 派生自 compose-miuix-ui（Apache-2.0），已按 NexioUI 的 API 与包结构重写。

@file:OptIn(ExperimentalAtomicApi::class)

package com.haooz.nexioui.progressiveblurtopbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.material.LocalMaterialNoneEnabled
import com.haooz.nexioui.material.LocalUseFakeProgressiveBlur
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.GlassEffectScope
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import com.haooz.softglass.backdrop.effects.runtimeShaderEffect
import com.haooz.softglass.platform.isRenderEffectSupported
import com.haooz.softglass.platform.isRuntimeShaderSupported
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * shader 名唯一序号。
 *
 * 必须原子：并发组合若拿到同一个序号，两个 shader 会共用名字，
 * 而 shader 缓存按名字索引 —— 会导致取到错误的 shader。
 */
private val progressiveBlurShaderSeq = AtomicInt(0)
private val NoSampleTrack: () -> Float = { 0f }
private val FullMaskAlpha: () -> Float = { 1f }

/** 顶部最大模糊半径（dp）：糊带的半径从这里随 Y 收到 0。 */
private val BlurMaxRadius = 12.dp

/**
 * 顶部没有状态栏时糊带额外收窄的量：80.dp 那档是按「有状态栏」的观感定的，
 * 顶部空出来后显得偏高，统一再收 8dp。调用方自己算高度时也用这一个值。
 */
val NoStatusBarBlurReduction: Dp = 8.dp

/**
 * 读取任意状态并仅失效 draw，不进组合。
 * 侧栏伸缩时顶栏糊层需要跟手重采样，但顶栏自身尺寸不变。
 */
internal fun Modifier.invalidateDrawOnState(read: () -> Unit): Modifier =
    this then InvalidateDrawOnStateElement(read)

internal fun Modifier.invalidateDrawOnSampleTrack(track: () -> Float): Modifier =
    invalidateDrawOnState { track() }

private class InvalidateDrawOnStateElement(
    private val read: () -> Unit,
) : ModifierNodeElement<InvalidateDrawOnStateNode>() {

    override fun create(): InvalidateDrawOnStateNode = InvalidateDrawOnStateNode(read)

    override fun update(node: InvalidateDrawOnStateNode) {
        node.read = read
        node.reobserve()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InvalidateDrawOnStateElement) return false
        return read === other.read
    }

    override fun hashCode(): Int = read.hashCode()
}

private class InvalidateDrawOnStateNode(
    var read: () -> Unit,
) : DrawModifierNode, ObserverModifierNode, Modifier.Node() {

    override fun onObservedReadsChanged() {
        invalidateDraw()
        reobserve()
    }

    fun reobserve() {
        observeReads { read() }
    }

    override fun onAttach() {
        reobserve()
    }

    override fun ContentDrawScope.draw() {
        drawContent()
    }
}

/**
 * 顶部栏渐进模糊：模糊半径随 Y 从顶部最大（[BlurMaxRadius]）连续收到 0。
 *
 * 四条渲染路径，按能力从高到低：
 * 1. 渐变遮罩（无材质档）：无模糊，表面色 alpha 纵向渐变淡出。
 * 2. AGSL 两趟（`isRuntimeShaderSupported()`）：抖动采样 + 环形去噪。
 * 3. 假渐进（开关关闭，或 AGSL 不可用但 RenderEffect 可用）：一次 blur + 一个极简淡出 pass
 *    串在同一条 RenderEffect 链里。API 31–32 / 鸿蒙没有 AGSL 图像滤镜，退回 `Offscreen + DstIn`。
 * 4. 渐变遮罩兜底（无 RenderEffect）：模糊不可用时不降级为纯色。
 */
@Composable
fun ProgressiveBlurTopBar(
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    height: Dp = Dp.Unspecified,
    /** 叠在模糊层上的表面色浓度（0 = 纯模糊）。只作用于糊层，不盖 [content]。 */
    tintIntensity: Float = 0.2f,
    /** 表面色，默认取主题 surface。 */
    tintColor: Color = NexioTheme.colorScheme.surface,
    blurAlpha: Float = 1f,
    /** 是否用「假渐进模糊」（一次 blur + 极简淡出，不跑 AGSL 多重采样）。可显式传参覆盖。 */
    useFakeProgressiveBlur: Boolean = LocalUseFakeProgressiveBlur.current,
    /** 无材质档渐变遮罩：整条糊带换成无模糊的半透明渐变（不采样 backdrop）。 */
    useGradientMask: Boolean = LocalMaterialNoneEnabled.current,
    /** 渐变遮罩的额外不透明度（0–1），在绘制期读，只失效绘制。 */
    maskAlpha: () -> Float = FullMaskAlpha,
    /** 底端透明淡出起点（0–1，相对糊层高度）。越小过渡越长。 */
    edgeFadeStart: Float = 0.88f,
    /** 侧栏伸缩等导致采样源位移后自增，强制重建糊层采样。 */
    resampleKey: Int = 0,
    /** 伸缩过程中的连续跟踪值；draw 阶段调用并只失效 draw，不进组合。 */
    sampleTrack: () -> Float = NoSampleTrack,
    /** 已经被外围 scaffold 消费掉的原生工具栏 inset。 */
    topInsetAdjustment: Dp = 0.dp,
    /**
     * true = 屏幕顶部没有状态栏（折叠屏 iPhone 折叠态，状态栏随竖排 tab bar 移到右侧）。
     * 算出来的高度是 0 分不出「顶部没有」和「被 [topInsetAdjustment] 吃光」，只有宿主能区分。
     */
    noTopStatusBar: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val totalHeight = if (height != Dp.Unspecified) {
        height
    } else {
        val statusBarHeight = (WindowInsets.statusBars.asPaddingValues().calculateTopPadding() -
            topInsetAdjustment).coerceAtLeast(0.dp)
        when {
            // 顶部压根没有状态栏（折叠屏折叠态）：别按「被导航栏吃完」那套撑到 120.dp
            noTopStatusBar -> 72.dp - NoStatusBarBlurReduction
            statusBarHeight > 0.dp -> 72.dp + statusBarHeight
            else -> 120.dp
        }
    }

    val blurShapeBlock: () -> Shape = remember { { RectangleShape } }
    // ShaderRegistry 按 key 共享运行时 shader；多顶栏同时挂载时必须各用独立 key，
    val shaderKey = remember(resampleKey) {
        "ProgressiveBlurRadial_${progressiveBlurShaderSeq.addAndFetch(1)}"
    }
    val denoiseKey = remember(shaderKey) { "${shaderKey}_denoise" }
    val blurEffects: GlassEffectScope.() -> Unit =
        remember(shaderKey, denoiseKey, tintColor, tintIntensity, edgeFadeStart) {
            {
                val maxRadiusPx = BlurMaxRadius.toPx()
                // padding = 0：shader 的采样全部镜像折回 content 区
                padding = 0f
                val contentW = size.width * downsampleScale
                val contentH = size.height * downsampleScale

                runtimeShaderEffect(shaderKey, PROGRESSIVE_BLUR_SHADER, "content") {
                    setFloatUniform("contentOrigin", 0f, 0f)
                    setFloatUniform("contentSize", contentW, contentH)
                    setFloatUniform("maxRadius", maxRadiusPx * downsampleScale)
                    setFloatUniform("edgeFadeStart", edgeFadeStart)
                    setColorUniform("tint", tintColor)
                    setFloatUniform("tintIntensity", tintIntensity)
                }
                runtimeShaderEffect(denoiseKey, PROGRESSIVE_DENOISE_SHADER, "content") {
                    setFloatUniform("contentOrigin", 0f, 0f)
                    setFloatUniform("contentSize", contentW, contentH)
                    setFloatUniform("maxRadius", maxRadiusPx * downsampleScale)
                }
            }
        }

    // 假渐进淡出 pass 的 shader key 必须独立：缓存按名字索引，与真渐进共名会取到 32 tap 那份。
    val fadeShaderKey = remember(resampleKey) {
        "ProgressiveBlurFade_${progressiveBlurShaderSeq.addAndFetch(1)}"
    }
    /**
     * 假渐进：blur → 淡出 一条 RenderEffect 链。
     *
     * 淡出不再走 `drawWithContent + DstIn`（那要再开一层全尺寸 Offscreen 合成层）；
     * 折进链里后，录制 backdrop 的那一个离屏 layer 就够。
     */
    val fakeBlurEffects: GlassEffectScope.() -> Unit =
        remember(fadeShaderKey, edgeFadeStart) {
            {
                // Mirror：`TileMode.Clamp` 会把顶边的采样钳到第一行，顶栏最上沿会被拉平成一条「没糊」的带子。
                blur(BlurMaxRadius.toPx(), TileMode.Mirror)
                // Mirror 会让 blur() 把 padding 撑到 radius，而外扩的那圈缓冲录的是屏幕外（空），
                // 镜像过去仍是空 —— 压回 0，让镜像只发生在节点内。
                padding = 0f
                runtimeShaderEffect(fadeShaderKey, PROGRESSIVE_FADE_SHADER, "content") {
                    setFloatUniform(
                        "contentSize",
                        size.width * downsampleScale,
                        size.height * downsampleScale,
                    )
                    setFloatUniform("fadeStart", edgeFadeStart.coerceIn(0.4f, 0.95f))
                    setFloatUniform("hold", 0.55f)
                    setFloatUniform("midAlpha", 0.45f)
                }
            }
        }

    Box(modifier = modifier) {
        // 只重建糊层，不 remount content
        key(resampleKey, useFakeProgressiveBlur, useGradientMask) {
            when {
                useGradientMask -> {
                    ProgressiveBlurGradientMask(totalHeight, tintColor, blurAlpha, maskAlpha)
                }
                // 真渐进：AGSL 守 API 33+（31–32 上 runtimeShaderEffect 会静默 no-op）
                isRuntimeShaderSupported() && !useFakeProgressiveBlur -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(totalHeight)
                            .graphicsLayer { alpha = blurAlpha }
                            .invalidateDrawOnSampleTrack(sampleTrack)
                            .drawBackdrop(
                                backdrop = backdrop,
                                shadow = null,
                                shape = blurShapeBlock,
                                effects = blurEffects,
                            ),
                    )
                }
                // 假渐进：开关关闭，或 AGSL 不可用但 RenderEffect 可用（API 31–32）
                isRenderEffectSupported() -> {
                    if (isRuntimeShaderSupported()) {
                        // 单趟：模糊与淡出共用同一个离屏 layer，没有第二层 Offscreen 合成。
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(totalHeight)
                                .graphicsLayer { alpha = blurAlpha }
                                .invalidateDrawOnSampleTrack(sampleTrack)
                                .drawBackdrop(
                                    backdrop = backdrop,
                                    shadow = null,
                                    shape = blurShapeBlock,
                                    effects = fakeBlurEffects,
                                ),
                        )
                    } else {
                        // API 31–32 / 鸿蒙：AGSL 图像滤镜不可用，淡出只能回 Canvas 合成。
                        LegacyFakeProgressiveBlur(
                            backdrop = backdrop,
                            blurShapeBlock = blurShapeBlock,
                            totalHeight = totalHeight,
                            blurAlpha = blurAlpha,
                            edgeFadeStart = edgeFadeStart,
                            sampleTrack = sampleTrack,
                        )
                    }
                }
                // 兜底仍走渐变遮罩：模糊能力缺失时不降级为纯色
                else -> {
                    ProgressiveBlurGradientMask(totalHeight, tintColor, blurAlpha, maskAlpha)
                }
            }
        }
        content()
    }
}

/**
 * 能力兜底（API 31–32 / 鸿蒙）：没有 AGSL 图像滤镜，淡出只能回到 Canvas 合成。
 *
 * `CompositingStrategy.Offscreen` 不能省 —— `DstIn` 作用于 canvas 的整个 dest，
 * 不独立成层会把顶栏下面的内容一起擦掉。
 */
@Composable
private fun LegacyFakeProgressiveBlur(
    backdrop: GlassBackdrop,
    blurShapeBlock: () -> Shape,
    totalHeight: Dp,
    blurAlpha: Float,
    edgeFadeStart: Float,
    sampleTrack: () -> Float,
) {
    val fadeBrush = remember(totalHeight, edgeFadeStart) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.White,
                (edgeFadeStart * 0.55f).coerceIn(0.2f, 0.9f) to Color.White,
                edgeFadeStart.coerceIn(0.4f, 0.95f) to Color.White.copy(alpha = 0.45f),
                1.0f to Color.Transparent,
            ),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight)
            .graphicsLayer {
                alpha = blurAlpha
                compositingStrategy = CompositingStrategy.Offscreen
                clip = true
            }
            .invalidateDrawOnSampleTrack(sampleTrack)
            .drawWithContent {
                drawContent()
                // 只淡出糊层自身，不盖到 content()
                drawRect(fadeBrush, blendMode = BlendMode.DstIn)
            }
            .drawBackdrop(
                backdrop = backdrop,
                shadow = null,
                shape = blurShapeBlock,
                effects = {
                    blur(BlurMaxRadius.toPx(), TileMode.Mirror)
                    padding = 0f
                },
            ),
    )
}

/**
 * 渐变遮罩：无模糊，表面色 alpha 纵向渐变淡出。
 * 无材质档的渐进模糊形态，也是能力缺失时的兜底。
 *
 * [maskAlpha] 在绘制期读：顶栏材质 alpha 逐帧变化时只失效这一层的绘制。
 */
@Composable
private fun ProgressiveBlurGradientMask(
    totalHeight: Dp,
    fillColor: Color,
    blurAlpha: Float,
    maskAlpha: () -> Float,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight)
            .graphicsLayer { alpha = blurAlpha * maskAlpha() }
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to fillColor.copy(alpha = 0.98f),
                        0.4f to fillColor.copy(alpha = 0.92f),
                        0.7f to fillColor.copy(alpha = 0.6f),
                        1.0f to fillColor.copy(alpha = 0.0f),
                    ),
                ),
            ),
    )
}
