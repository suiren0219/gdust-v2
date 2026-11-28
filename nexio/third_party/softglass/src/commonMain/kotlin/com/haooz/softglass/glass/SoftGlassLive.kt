package com.haooz.softglass.glass

import com.haooz.softglass.GlassEnvironment
import com.haooz.softglass.platform.GlassRuntimeShader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

/** σ（屏幕 px）→ Compose BlurEffect 半径；两条采样路的模糊量单位不同。 */
fun glassBlurRadiusForSigma(sigmaPx: Float): Float =
    if (sigmaPx <= 0f) 0f else (sigmaPx - 0.5f) / 0.57735f

internal fun smooth5Vertical(x: Float): Float {
    var t = 0.5f + 0.5f * sqrt(x.coerceIn(0f, 1f))
    t = t.coerceIn(0f, 1f)
    t = t * t * t * (t * (t * 6f - 15f) + 10f)
    return (t - 0.5f) * 2f
}

/**
 * 折射带能用的最大深度（占 `edgeWidth` 的比例），即 shader 里 `inner` 的切换点。
 *
 * `inner` 的判定是 `pow(1 - circle, edgePow) <= 1e-6`，edgePow = 4 时约在 x = 0.564，
 * 比代码里写死的 0.85 早得多。液态折射带一旦越过这里，位移还没收到 0 就被切到
 * 内区分支，会在边界留下「折射突然消失」的硬边，所以带高必须卡在这个距离以内。
 */
internal fun liquidBandLimitRatio(edgePow: Float): Float {
    val exponent = -6.0 / edgePow.coerceAtLeast(0.05f).toDouble()
    val aInner = 10.0.pow(exponent).toFloat()
    val target = 1f - aInner
    var lo = 0f
    var hi = 1f
    repeat(24) {
        val mid = (lo + hi) * 0.5f
        if (smooth5Vertical(mid) < target) lo = mid else hi = mid
    }
    return ((lo + hi) * 0.5f).coerceAtMost(0.85f)
}

/** 把样式灌入实时录制版 shader；尺寸与圆角由调用方按当前布局传入。 */
fun GlassRuntimeShader.applySoftGlass(
    style: SoftGlassStyle,
    width: Float,
    height: Float,
    radiusPx: Float? = null,
    shapeRectPx: FloatArray? = null,
    density: Float = GlassEnvironment.density,
) {
    if (width <= 0f || height <= 0f) return
    val rect = shapeRectPx?.takeIf { it.size >= 4 }
    val shapeLeft = rect?.get(0) ?: 0f
    val shapeTop = rect?.get(1) ?: 0f
    val shapeRight = rect?.get(2) ?: width
    val shapeBottom = rect?.get(3) ?: height
    val half = minOf(shapeRight - shapeLeft, shapeBottom - shapeTop).coerceAtLeast(1f) * 0.5f
    val edgePx = style.edgeWidth
    val refractionOn = style.refractionScale > 0f

    setFloatUniform("uSize", width, height)
    setFloatUniform("uShapeRect", shapeLeft, shapeTop, shapeRight, shapeBottom)
    val r = (radiusPx ?: half).coerceIn(0f, half)
    setFloatUniform("uCornerRadii", r, r, r, r)

    setFloatUniform("uEdgePx", edgePx)
    setFloatUniform("uEdgePow", style.edgePow)
    setFloatUniform("uInvEdgePow", 1f / style.edgePow.coerceAtLeast(0.05f))
    setFloatUniform("uThicknessPx", style.thickness * style.refractionScale)
    setFloatUniform("uReflectOffsetPx", if (refractionOn) style.reflectOffset else 0f)
    // 液态玻璃：只换折射的位移场；lens 是 dp 口径，按 density 换成屏幕 px，带高封顶在 inner 边界内
    val lens = style.liquidLens
    val liquidOn = style.liquidRefraction && refractionOn
    val liquidInnerX = liquidBandLimitRatio(style.edgePow)
    setFloatUniform("uLiquid", if (liquidOn) 1f else 0f)
    setFloatUniform(
        "uLiquidHeightPx",
        (lens.refractionHeight.value * density)
            .coerceAtMost(liquidInnerX * style.edgeWidth)
            .coerceAtLeast(1f),
    )
    setFloatUniform(
        "uLiquidAmountPx",
        lens.refractionAmount.value * density * style.refractionScale.coerceAtLeast(0f),
    )
    setFloatUniform("uLiquidDepth", if (lens.depthEffect) 1f else 0f)
    setFloatUniform("uLiquidDispersion", lens.chromaticAberration.coerceAtLeast(0f))
    setFloatUniform("uLiquidInnerX", liquidInnerX)
    setFloatUniform("uIOR", style.refractIOR)
    setFloatUniform("uReflStrength", style.reflectionStrength)
    setFloatUniform("uReflLighten", style.reflectionLighten)
    setFloatUniform("uColorPow", style.colorPow)
    setFloatUniform("uAlpha", style.alpha)
    setFloatUniform("uInnerBottom", style.innerBottom)
    setFloatUniform("uEdgeHighlight", if (style.edgeHighlight) 1f else 0f)
    setFloatUniform("uLightFalloff", style.edgeLightFalloff.coerceIn(0f, 1f))
    setFloatUniform("uUnshade", style.unshade)
    setFloatUniform("uUsePowGate", if (style.usePowGate) 1f else 0f)

    val ld = style.lightDirection
    setFloatUniform("uLightDir", ld.first, ld.second, ld.third)
    val cosA = cos(style.lightAngleRange * PI.toFloat() * 0.5f)
    setFloatUniform("uAngleK", 0.5f / sqrt((1f - cosA).coerceAtLeast(1e-6f)))
    setFloatUniform("uLightIntensity", style.lightIntensity)
    setFloatUniform("uLightOpposite", style.lightOppositeIntensity)
    setFloatUniform("uNmlZAAThreshold", smooth5Vertical(3f / edgePx.coerceAtLeast(1f)))

    setFloatUniform("uDarker", style.darker)
    val dc = style.darkerColor
    setFloatUniform("uDarkerColor", dc.red, dc.green, dc.blue)
    setFloatUniform("uDarkerRange", style.darkerRange.first, style.darkerRange.second)
    val lc = style.luminanceValues
    setFloatUniform(
        "uLuminance",
        lc.getOrElse(0) { 0f },
        lc.getOrElse(1) { 0f },
        lc.getOrElse(2) { 0f },
        lc.getOrElse(3) { 1f },
    )
    setFloatUniform("uLuminanceAmount", style.luminanceAmount)
    setFloatUniform("uSaturation", style.saturation)
    setFloatUniform("uBrightness", style.brightness)
    setFloatUniform("uBgSat", style.bgColorSaturation)
    setFloatUniform("uBgBri", style.bgColorBrightness)
    setFloatUniform("uInnerWhite", style.innerColorWhite)
    setFloatUniform("uInnerMix", style.innerColorMix)
    setFloatUniform("uBurn", style.burn)
    val gc = style.glassColor
    setFloatUniform("uGlassColor", gc.red, gc.green, gc.blue, style.glassColorMix)
}
