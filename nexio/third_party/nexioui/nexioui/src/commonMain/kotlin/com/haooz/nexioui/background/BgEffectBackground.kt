/** 背景特效 - 绘制动态背景效果 */
package com.haooz.nexioui.background

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.nexioui.window.isTabletWidth
import com.haooz.softglass.platform.isRuntimeShaderSupported
import kotlin.math.floor
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * OS3 风格动态背景：四个彩色光点缓慢漂移、配色循环渐变，铺在 [content] 之下。
 *
 * 不支持 runtime shader 的平台直接退化为普通 [Box]。
 *
 * @param dynamicBackground 是否播放动画（关闭后只画一帧静态渐变）。
 * @param bgModifier 追加给背景层的 Modifier（例如玻璃采样层）。
 * @param isFullSize 背景占满全高（true）还是只占下半屏（false）。
 * @param effectBackground 是否绘制特效；false 时只铺 surface 底色。
 * @param alpha 特效整体不透明度，绘制期读取。
 * @param isDark 是否深色配色。
 * @param timeScale 动画时间倍率，越小流动越慢。
 */
@Composable
fun BgEffectBackground(
    dynamicBackground: Boolean,
    modifier: Modifier = Modifier,
    bgModifier: Modifier = Modifier,
    isFullSize: Boolean = false,
    effectBackground: Boolean = true,
    alpha: () -> Float = { 1f },
    isDark: Boolean = NexioTheme.isDark,
    timeScale: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    BgEffectBackgroundWithPreset(
        dynamicBackground = dynamicBackground,
        presetOverride = null,
        timeScale = timeScale,
        isDark = isDark,
        modifier = modifier,
        bgModifier = bgModifier,
        isFullSize = isFullSize,
        effectBackground = effectBackground,
        alpha = alpha,
        content = content,
    )
}

/**
 * 与 [BgEffectBackground] 相同，但允许调用方注入配色预设与动画时间倍率。
 *
 * @param presetOverride null 时按深浅色 / 平板取内置预设；非 null 时完全以它为准。
 * @param timeScale 动画时间倍率，越小流动越慢。
 */
@Composable
fun BgEffectBackgroundWithPreset(
    dynamicBackground: Boolean,
    presetOverride: BgEffectConfig.Config?,
    timeScale: Float,
    modifier: Modifier = Modifier,
    bgModifier: Modifier = Modifier,
    isFullSize: Boolean = false,
    effectBackground: Boolean = true,
    alpha: () -> Float = { 1f },
    isDark: Boolean = NexioTheme.isDark,
    content: @Composable BoxScope.() -> Unit,
) {
    val shaderSupported = remember { isRuntimeShaderSupported() }
    if (!shaderSupported) {
        Box(modifier = modifier, content = content)
        return
    }
    Box(
        modifier = modifier,
    ) {
        val surface = NexioTheme.colorScheme.surface
        val isTablet = isTabletWidth()
        val painter = remember { BgEffectPainter() }

        val preset = remember(isDark, isTablet, presetOverride) {
            presetOverride ?: BgEffectConfig.get(isDark, isTablet)
        }

        val colorStage = remember { Animatable(0f) }

        LaunchedEffect(dynamicBackground, preset) {
            if (!dynamicBackground) return@LaunchedEffect
            val animatesColors = preset.colors1 !== preset.colors2 || preset.colors2 !== preset.colors3
            if (!animatesColors) return@LaunchedEffect

            var targetStage = floor(colorStage.value) + 1f
            while (isActive) {
                delay((preset.colorInterpPeriod * 500).toLong().milliseconds)
                colorStage.animateTo(
                    targetValue = targetStage,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 35f),
                )
                targetStage += 1f
            }
        }

        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .then(bgModifier)
                .bgEffectDraw(
                    painter = painter,
                    preset = preset,
                    surface = surface,
                    effectBackground = effectBackground,
                    isFullSize = isFullSize,
                    playing = dynamicBackground,
                    colorStage = { colorStage.value },
                    alpha = alpha,
                    timeScale = timeScale,
                ),
        )
        content()
    }
}
