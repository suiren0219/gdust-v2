package com.menglimi.gdust.v2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.haooz.softglass.GlassMaterialLevel
import com.haooz.softglass.GlassPreset
import com.haooz.softglass.SoftGlassTheme
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import com.haooz.softglass.backdrop.backdrops.layerBackdrop

/** 风格一：HyperOS 柔光（softglass）。 */
@Composable
fun SoftGlassAppTheme(dark: Boolean, content: @Composable () -> Unit) {
    val ds = dsFor(dark)
    SoftGlassTheme(
        preset = GlassPreset.Default,
        level = GlassMaterialLevel.SOFT,
        isDark = dark,
        hasWallpaper = false,
    ) {
        val backdrop = rememberLayerBackdrop()
        WithTypography(ds) {
            CompositionLocalProvider(LocalDs provides ds, LocalBackdrop provides backdrop) {
                // 采样层与内容层平级（softglass 约束：玻璃节点不能在采样层内，否则自采样崩）
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(ds.bg))
                    content()
                }
            }
        }
    }
}

/** 风格二：iOS 27 液态玻璃（Glasmio）。 */
@Composable
fun LiquidGlassAppTheme(dark: Boolean, content: @Composable () -> Unit) {
    val ds = dsFor(dark)
    io.github.neoruaa.glasmio.core.GlasmioTheme(isDark = dark) {
        val glass = io.github.neoruaa.glasmio.core.GlasmioTheme.colors
        val mapped = ds.copy(
            bg = glass.groupedBackground,
            card = glass.container,
            text = glass.content,
            sub = glass.secondaryContent,
            accent = glass.accent,
            danger = glass.destructive,
        )
        WithTypography(mapped) {
            CompositionLocalProvider(LocalDs provides mapped) {
                // Glasmio BackdropHost：采样层只画底色，内容在 overlayContent（不进采样层）
                io.github.neoruaa.glasmio.glass.GlasmioBackdropHost(
                    modifier = Modifier.fillMaxSize(),
                    sampledContent = {
                        Box(Modifier.fillMaxSize().background(mapped.bg))
                    },
                    overlayContent = {
                        content()
                    },
                )
            }
        }
    }
}
