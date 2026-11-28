package com.haooz.softglass.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 方向光默认向量，模长约 0.849 时峰值亮度稳定。 */
val GlassLightDirection: Triple<Float, Float, Float> = Triple(-0.42f, 0.74f, -1.15f)

/** 柔光玻璃的完整渲染参数；几何量均为像素，不随 density 变化。 */
@Immutable
data class SoftGlassStyle(
    val cornerRadius: Dp = 28.dp,
    val edgeWidth: Float = 60f,
    val edgePow: Float = 4f,
    val slopeMode: Int = 0,
    val thickness: Float = 96f,
    val reflectOffset: Float = 600f,
    val refractionScale: Float = 1f,
    /**
     * 液态玻璃折射：只替换边缘折射的采样方式（圆弧剖面 + SDF 梯度位移），
     * 倒角法线、方向光、反射、调色与模糊仍走柔光玻璃那一套。
     */
    val liquidRefraction: Boolean = false,
    /** 液态折射的光学参数；不传就用 [LiquidLens] 的默认值，调用处可整体替换。 */
    val liquidLens: LiquidLens = LiquidLens(),
    val refractIOR: Float = 3f,
    val reflectionStrength: Float = 0.8f,
    val reflectionLighten: Float = 1f,
    val colorPow: Float = 1.2f,
    val glassColor: Color = Color.White,
    val glassColorMix: Float = 0.1f,
    val fill: GlassFill = NoneFill,
    val tone: GlassTone = TokenTone,
    val alpha: Float = 1f,
    val hit: Boolean = false,
    val innerBottom: Float = 0.05f,
    val unshade: Float = 0f,
    val usePowGate: Boolean = true,
    val lightDirection: Triple<Float, Float, Float> = GlassLightDirection,
    val lightAngleRange: Float = 1f,
    val lightIntensity: Float = 1.6f,
    val lightOppositeIntensity: Float = 0.8f,
    val bgColorSaturation: Float = 0f,
    val bgColorBrightness: Float = 0f,
    val luminanceValues: List<Float> = listOf(0f, 2f, 0.5f, 0.8f),
    val luminanceAmount: Float = 0.15f,
    val luminanceCurveMode: Int = 1,
    val saturation: Float = 2f,
    val brightness: Float = 0.08f,
    val innerColorWhite: Float = 0.15f,
    val innerColorMix: Float = 0.4f,
    val burn: Float = 0f,
    val darker: Float = 0f,
    val darkerColor: Color = Color(0.07874f, 0.02848f, 0.09278f),
    val darkerRange: Pair<Float, Float> = 0.6f to 1f,
    val pressLightCenterFall: Float = 0f,
    val pressLightCenterStrength: Float = 0f,
    val pressLightCenterPeak: Float = 0f,
    val pressRingStrength: List<Float> = listOf(0f, 0f),
    val edgeHighlight: Boolean = true,
    val edgeLightFalloff: Float = 1f,
    val smallBlurRadius: Float = 32f,
    val bigBlurRadius: Float = 500f,
    val debugView: Int = 0,
) {
    /** 照明参数按固定档位取值，不随采样源亮度变化。 */
    fun adapted(meanLuma: Float): SoftGlassStyle = this

    /** 先套基调再套填充，产出交给 shader 的最终样式。 */
    fun filled(isDark: Boolean): SoftGlassStyle = fill.apply(tone.apply(this), isDark)
}

/**
 * 液态玻璃的 lens 参数
 *
 * 几何量用 dp 给出，灌 uniform 时按当前 density 换成屏幕 px；折射带会被封顶在
 * 倒角带内，否则内区边界会出现位移跳变。默认值即全局口径：宽 10dp、位移 42dp；
 * 课程卡片这类小尺寸面板在调用处另给一套（5 / 25）。
 */
@Immutable
data class LiquidLens(
    /** 折射带宽度。 */
    val refractionHeight: Dp = 10.dp,
    /** 边缘最大位移（实际再乘卡片的折射倍率 [SoftGlassStyle.refractionScale]）。 */
    val refractionAmount: Dp = 42.dp,
    /** 叠加指向中心的径向梯度（参考实现的 depthEffect）。 */
    val depthEffect: Boolean = true,
    /** 色散强度，0 = 关；>0 时边缘做 7 次错位采样（参考实现的 chromaticAberration）。 */
    val chromaticAberration: Float = 0f,
)
