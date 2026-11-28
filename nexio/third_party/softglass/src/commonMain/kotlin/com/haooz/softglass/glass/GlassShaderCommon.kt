package com.haooz.softglass.glass

import com.haooz.softglass.shape.SdfG2Corner

internal val GLASS_SDF = SdfG2Corner.shaderSource

/**
 * 液态玻璃折射（移植自 AndroidLiquidGlass 的 `RoundedRectRefractionShaderString`）。
 *
 * 只负责「位移场」：带内沿圆角矩形 SDF 梯度向形状内部采样，剖面是 `1 - sqrt(1 - t²)`，
 * 越靠外边缘位移越大、带尾导数为 0；梯度半径放宽到 1.5r 再叠径向项，藏掉圆角切点处的
 * 曲率跳变。倒角法线、方向光、反射、调色仍走柔光玻璃那一套。
 *
 * `uLiquidInnerX` 是柔光 `inner` 的硬切位置（edgePow = 4 时约 0.564×edgeWidth）：
 * 折射带高必须卡在它以内，边光 / 反射也要在到达它之前由 `liquidEdgeGate` 收到 0，
 * 否则会在内区边界留下一条「折射突然消失」的硬边。
 */
internal val GLASS_LIQUID = """
uniform float uLiquid;
uniform float uLiquidHeightPx;
uniform float uLiquidAmountPx;
uniform float uLiquidDepth;
uniform float uLiquidDispersion;
uniform float uLiquidInnerX;

float liquidCircleMap(float t) {
    t = clamp(t, 0.0, 1.0);
    return 1.0 - sqrt(max(1.0 - t * t, 0.0));
}

// 柔光的 inner 是硬切：从 x ≈ uLiquidInnerX 起方向光与反射整段消失。
// 液态档把这两项在到达边界前收干净，否则折射带内侧会出现一条「折射突然消失」的直边。
float liquidEdgeGate(float x) {
    if (uLiquid <= 0.5) { return 1.0; }
    return 1.0 - smoothstep(uLiquidInnerX * 0.45, uLiquidInnerX, x);
}

// 位移为负 = 往形状内部采样：边缘的内容被「压」进来，形成凸透镜的挤压感。
float2 liquidRefractOffset(float2 p, float2 halfSize, float cornerR, float sd) {
    float band = clamp(-sd / max(uLiquidHeightPx, 1e-3), 0.0, 1.0);
    float amount = -liquidCircleMap(1.0 - band) * uLiquidAmountPx;
    float gradR = min(cornerR * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = roundRectGrad(p, halfSize, gradR);
    float2 radial = p / max(length(p), 1e-3);
    float2 dir = grad + uLiquidDepth * radial;
    return amount * dir / max(length(dir), 1e-4);
}
"""

/** 大模糊底色固定取金字塔最粗一级，与当前模糊档位无关。 */
internal val GLASS_WALLPAPER_COARSE = """
const float3 GLASS_LUMA = float3(0.2126, 0.7152, 0.0722);

float3 sampleCoarse(float2 texPx) {
    return uWallpaperCoarse.eval(
        clamp(texPx * uLevelScaleCoarse, float2(0.5), uTexSizeCoarse - 0.5)
    ).rgb;
}
"""

/** 相邻两级按权重混合，实现级别间连续过渡。 */
internal val GLASS_WALLPAPER_LEVELS = """
float3 sampleLevel(float2 texPx) {
    float3 a = uWallpaperA.eval(clamp(texPx * uLevelScaleA, float2(0.5), uTexSizeA - 0.5)).rgb;
    if (uBlendT <= 0.0) { return a; }
    float3 b = uWallpaperB.eval(clamp(texPx * uLevelScaleB, float2(0.5), uTexSizeB - 0.5)).rgb;
    return mix(a, b, uBlendT);
}
"""

/** 先模糊后调色：gcr 与 glassColor 的输入都取自重模糊后的背景色。 */
internal val GLASS_COLOR_PROCESS = """
uniform float3 uDarkerColor;

float glassLuma(float3 c) { return dot(c, GLASS_LUMA); }

float3 glassDarker(float3 c) {
    float k = mix(0.0, uDarker, smoothstep(uDarkerRange.x, uDarkerRange.y, glassLuma(c)));
    return mix(c, uDarkerColor, k);
}

float3 glassAdjust(float3 c, float sat, float bri) {
    float l = dot(c, float3(0.2125, 0.7153, 0.0721));
    return mix(float3(l), c, sat) + bri;
}

float4 processGlassColor(float3 refractCol, float3 bigCol) {
    float3 c = glassDarker(refractCol);
    float l = clamp(glassLuma(c), 0.0, 1.0);
    float lc = ((uLuminance.x * l + uLuminance.y) * l + uLuminance.z) * l + uLuminance.w;
    float curve = clamp(lc, 0.0, 1.0) / max(l, 0.01) * smoothstep(0.0, 0.1, l);
    c = c * mix(1.0, curve, uLuminanceAmount);
    c = glassAdjust(c, uSaturation, uBrightness);

    float3 b = glassDarker(bigCol);
    b = glassAdjust(b, uBgSat, uBgBri);
    b = mix(b, float3(1.0), uInnerWhite);

    float lumin = clamp(glassLuma(b), 0.0, 1.0);
    float tBurn = (uBurn <= 0.0) ? 1.0 : pow(lumin, uBurn);
    tBurn -= 0.5;
    float ratio = 0.8 * mix(lumin, 1.0, 1.587 * tBurn * tBurn * tBurn + 0.5);

    float3 gcr = mix(float3(1.0), b, ratio);
    float m = (gcr.r + gcr.g + gcr.b) / 3.0;
    float gmask = smoothstep(0.0, 0.4, length(b - float3(m)));
    gcr = mix(float3(m), gcr, gmask);
    gcr = mix(gcr, uGlassColor.rgb, uGlassColor.a);

    float im = uInnerMix * ratio;
    c = mix(c * gcr, gcr, im);
    return float4(clamp(c, float3(0.0), float3(1.0)), 1.0);
}
"""
