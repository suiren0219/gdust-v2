package com.haooz.softglass.glass

/** 静态壁纸路径的主体 shader：几何 SDF + 金字塔采样 + 调色 + 倒角剖面与光照。 */
val SOFT_GLASS_BODY_SHADER = GLASS_SDF + GLASS_LIQUID + """
uniform float2 uSize;
uniform float4 uCornerRadii;
uniform float2 uOrigin;

uniform shader uWallpaperA;
uniform shader uWallpaperB;
uniform shader uWallpaperCoarse;
uniform float4 uTexMap;
uniform float2 uTexSizeA;
uniform float2 uTexSizeB;
uniform float2 uTexSizeCoarse;
uniform float  uLevelScaleA;
uniform float  uLevelScaleB;
uniform float  uLevelScaleCoarse;
uniform float  uBlendT;

uniform float  uEdgePx;
uniform float  uEdgePow;
uniform float  uInvEdgePow;
uniform float  uThicknessPx;
uniform float  uReflectOffsetPx;
uniform float  uIOR;
uniform float  uReflStrength;
uniform float  uReflLighten;
uniform float  uColorPow;
uniform float4 uGlassColor;
uniform float  uAlpha;
uniform float  uHit;
uniform float  uInnerBottom;
uniform float  uEdgeHighlight;
uniform float  uLightFalloff;
uniform float3 uLightDir;
uniform float  uAngleK;
uniform float  uLightIntensity;
uniform float  uLightOpposite;
uniform float  uUnshade;
uniform float  uUsePowGate;
uniform float  uDebug;
uniform float  uNmlZAAThreshold;

uniform float  uDarker;
uniform float2 uDarkerRange;
uniform float4 uLuminance;
uniform float  uLuminanceAmount;
uniform float  uSaturation;
uniform float  uBrightness;
uniform float  uBgSat;
uniform float  uBgBri;
uniform float  uInnerWhite;
uniform float  uInnerMix;
uniform float  uBurn;

const float3 LUMA = float3(0.2126, 0.7152, 0.0722);

""" + GLASS_WALLPAPER_COARSE + GLASS_WALLPAPER_LEVELS + GLASS_COLOR_PROCESS + """

float smooth5Map(float t) {
    t = mix(0.5, 1.0, t);
    t = clamp(t, 0.0, 1.0);
    t = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    return (t - 0.5) * 2.0;
}

// (circle, d(circle)/dx) 共用一次 sqrt(x)
float2 circleAt(float x) {
    float s = sqrt(clamp(x, 0.0, 1.0));
    float sr = max(s, 1e-2);
    float u = 0.5 + 0.5 * sr;
    return float2(smooth5Map(s), 15.0 * u * u * (1.0 - u) * (1.0 - u) / sr);
}

float luma(float3 c) { return dot(c, LUMA); }

float3 miRefract(float3 I, float3 N, float eta) {
    float d = dot(N, I);
    float k = 1.0 - eta * eta * (1.0 - d * d);
    if (k < 0.0) { return float3(0.0); }
    return eta * I - (eta * d + sqrt(k)) * N;
}

float4 hsvv(float4 col, float lighten) {
    float v = luma(col.rgb);
    float w = smoothstep(0.0, 0.5, v);
    float k = mix(1.0 - v, v, w);
    float g = 1.0 + smoothstep(0.0, 1.0, lighten) * mix(0.75, 0.4, w);
    return float4((col.rgb + k) * g - k, col.a);
}

float dynamicAdd(float3 color) {
    return mix(0.5, mix(0.5, 1.0, smoothstep(0.2, 1.0, distance(float3(1.0), color))), luma(color));
}

float3 addLight(float3 color, float3 lightColor, float strength) {
    float w = 0.3 + 0.7 * clamp(1.9 - 2.15 * luma(color), 0.0, 1.0);
    return color + lightColor * strength * w;
}

float calculateLighting(float3 normal, float3 lightDir, float intensity) {
    float d = dot(normal, lightDir);
    float angleFactor = clamp(1.0 - sqrt(max(1.0 - d, 0.0)) * uAngleK, 0.0, 1.0);
    return max(d, 0.0) * intensity * angleFactor;
}

float4 processLighting(float4 color, float3 normal, float x, float falloffEnd) {
    float3 lightNormal = float3(normal.x, -normal.y, normal.z);
    float l1 = calculateLighting(lightNormal, uLightDir, uLightIntensity);
    float l2 = calculateLighting(lightNormal, uLightDir * float3(-1.0, -1.0, 1.0), uLightOpposite);
    float lightRatio = dynamicAdd(color.rgb);
    float mask = (falloffEnd >= 0.999)
        ? 1.0
        : 1.0 - smoothstep(0.0, max(falloffEnd, 1e-3), x);
    float lightenParam = (l1 + l2) * 2.0 * lightRatio * mask;
    return hsvv(color, lightenParam);
}

// 液态折射：偏移先换算到金字塔 texel 空间；色散权重与参考实现逐项一致。
float3 sampleLiquid(float2 texPx, float texK, float2 p, float2 halfSize, float cornerR, float sd) {
    float2 offset = liquidRefractOffset(p, halfSize, cornerR, sd) * texK;
    float2 base = texPx + offset;
    if (uLiquidDispersion <= 0.0) { return sampleLevel(base); }

    float2 spread = offset * (uLiquidDispersion * (p.x * p.y) / max(halfSize.x * halfSize.y, 1e-3));
    float3 c = float3(0.0);
    float3 s0 = sampleLevel(base + spread);
    c.r += s0.r / 3.5;
    float3 s1 = sampleLevel(base + spread * (2.0 / 3.0));
    c.r += s1.r / 3.5; c.g += s1.g / 7.0;
    float3 s2 = sampleLevel(base + spread * (1.0 / 3.0));
    c.r += s2.r / 3.5; c.g += s2.g / 3.5;
    float3 s3 = sampleLevel(base);
    c.g += s3.g / 3.5;
    float3 s4 = sampleLevel(base - spread * (1.0 / 3.0));
    c.g += s4.g / 3.5; c.b += s4.b / 3.0;
    float3 s5 = sampleLevel(base - spread * (2.0 / 3.0));
    c.b += s5.b / 3.0;
    float3 s6 = sampleLevel(base - spread);
    c.r += s6.r / 7.0; c.b += s6.b / 3.0;
    return c;
}

half4 main(float2 fragCoord) {
    float2 halfSize = uSize * 0.5;
    float2 p = fragCoord - halfSize;
    float cornerR = min(radiusAt(p, uCornerRadii), min(halfSize.x, halfSize.y));
    float sd = sdRoundRect(p, halfSize, cornerR);
    if (sd > 0.0) { return half4(0.0); }

    float x = clamp(-sd / max(uEdgePx, 1e-3), 0.0, 1.0);
    float2 cd = circleAt(x);
    float circle = cd.x;
    float a = max(1.0 - circle, 1e-6);
    float w = (x >= 0.85)
        ? 1.0
        : (uEdgePow == 4.0 ? 1.0 - (a * a) * (a * a) : 1.0 - pow(a, uEdgePow));
    float nmlZ = (x >= 0.85) ? 1.0 : circle;

    bool inner = (1.0 - w) <= 0.000001;
    float g = inner ? 0.0 : uEdgePow * ((1.0 - w) / a) * cd.y;
    g = min(g, 64.0);

    float finalAlpha = inner ? 1.0 : smoothstep(0.0, uNmlZAAThreshold, nmlZ);

    float2 nxy = inner ? float2(0.0) : roundRectGrad(p, halfSize, cornerR) * g;
    float3 normal = inner
        ? float3(0.0, 0.0, 1.0)
        : normalize(float3(nxy.x, nxy.y, 1.0));

    float2 texPx = (uOrigin + fragCoord) * uTexMap.xy + uTexMap.zw;
    float texK = uTexMap.x;

    if (uDebug > 0.5) {
        if (uDebug < 1.5) {
            return half4(x, x, x, 1.0);
        } else if (uDebug < 2.5) {
            return half4(inner ? 0.0 : 1.0, inner ? 1.0 : 0.0, 0.0, 1.0);
        } else {
            return half4(sampleLevel(texPx), 1.0);
        }
    }

    float3 refractionCol;
    if (inner) {
        refractionCol = sampleLevel(texPx);
    } else if (uLiquid > 0.5) {
        refractionCol = sampleLiquid(texPx, texK, p, halfSize, cornerR, sd);
    } else {
        float ior = uIOR + 0.2 * uHit;
        float3 refractDir = miRefract(float3(0.0, 0.0, -1.0), normal, 1.0 / ior);
        float refractThickness = mix((uThicknessPx - uEdgePx) * 2.0, uThicknessPx * 2.0, w);
        refractThickness *= step(0.5, uThicknessPx);
        refractionCol = sampleLevel(texPx + refractDir.xy * refractThickness * texK);
    }

    float3 materialRgb;
    float edgeGate = liquidEdgeGate(x);
    if (inner) {
        materialRgb = refractionCol;
    } else {
        float2 refractXY = 2.0 * normal.z * normal.xy;
        float3 reflectionCol = sampleLevel(
            texPx + refractXY * (uReflectOffsetPx * (1.0 - w) * texK)
        );
        materialRgb = mix(refractionCol, reflectionCol, (1.0 - nmlZ) * uReflStrength * edgeGate);
        materialRgb = addLight(materialRgb, reflectionCol, (1.0 - w) * uReflLighten * edgeGate);
    }

    float3 bigCol = sampleCoarse(texPx);

    float4 glassMaterial = processGlassColor(materialRgb, bigCol);

    float tEdge = smoothstep(0.0, 1.0, fragCoord.y / max(uSize.y, 1.0));
    glassMaterial.rgb += tEdge * tEdge * uInnerBottom;

    if (!inner && uEdgeHighlight > 0.5) {
        float4 lit = processLighting(glassMaterial, normal, x, uLightFalloff);
        glassMaterial = mix(glassMaterial, lit, edgeGate);
    }

    if (uUsePowGate > 0.5) {
        glassMaterial = pow(glassMaterial, float4(uColorPow));
    }
    glassMaterial.rgb = mix(clamp(glassMaterial.rgb, float3(0.0), float3(1.0)), uGlassColor.rgb, uUnshade);
    glassMaterial.a *= uAlpha * finalAlpha;

    return half4(glassMaterial.rgb * glassMaterial.a, glassMaterial.a);
}
"""
