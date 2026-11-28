package com.haooz.softglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity

@Immutable
data class GlassSurfaceAlphaDrop(val light: Float = 0f, val dark: Float = 0f)

/** 绘制期可读的主题环境，避免悬浮层 uniform 捕获组合期的旧值。 */
object GlassEnvironment {
    var isDark: Boolean by mutableStateOf(false)
        internal set
    var hasWallpaper: Boolean by mutableStateOf(true)
        internal set
    /** 当前 density，供 dp 口径的 lens 参数在灌 uniform 时换算成 px。 */
    var density: Float by mutableStateOf(1f)
        internal set

    fun update(
        isDark: Boolean = this.isDark,
        hasWallpaper: Boolean = this.hasWallpaper,
        density: Float = this.density,
    ) {
        this.isDark = isDark
        this.hasWallpaper = hasWallpaper
        this.density = density
    }
}

val LocalGlassSpec = compositionLocalOf { GlassPreset.Default }
val LocalGlassMaterialLevel = compositionLocalOf { GlassMaterialLevel.SOFT }
val LocalGlassTuning = compositionLocalOf { GlassMaterialLevel.SOFT.tuning() }
val LocalGlassIsDark = compositionLocalOf { false }
val LocalMaterialNoneEnabled = compositionLocalOf { false }
val LocalGlassBlurDp = compositionLocalOf { 6f }
val LocalGlassPanelBlurDp = compositionLocalOf { 12f }
val LocalGlassDialogBlurDp = compositionLocalOf { 12f }
val LocalGlassSurfaceAlphaDrop = compositionLocalOf {
    GlassSurfaceAlphaDrop(0.2f, 0.32f)
}
val LocalGlassEffectsEnabled = compositionLocalOf { true }
val LocalGlassRedrawSignal = staticCompositionLocalOf<() -> Unit> { {} }

/** 一次注入档位、色调与全部降级口径。 */
@Composable
fun SoftGlassTheme(
    preset: GlassSpec = GlassPreset.Default,
    level: GlassMaterialLevel = GlassMaterialLevel.SOFT,
    isDark: Boolean,
    hasWallpaper: Boolean = true,
    content: @Composable () -> Unit,
) {
    val tuning = level.tuning()
    val density = LocalDensity.current.density
    SideEffect {
        GlassEnvironment.isDark = isDark
        GlassEnvironment.hasWallpaper = hasWallpaper
        GlassEnvironment.density = density
    }
    CompositionLocalProvider(
        LocalGlassSpec provides preset,
        LocalGlassMaterialLevel provides level,
        LocalGlassTuning provides tuning,
        LocalGlassIsDark provides isDark,
        LocalMaterialNoneEnabled provides (level == GlassMaterialLevel.NONE),
        LocalGlassBlurDp provides tuning.blurDp,
        LocalGlassPanelBlurDp provides tuning.panelBlurDp,
        LocalGlassDialogBlurDp provides tuning.dialogBlurDp,
        LocalGlassSurfaceAlphaDrop provides GlassSurfaceAlphaDrop(
            tuning.surfaceAlphaDropLight,
            tuning.surfaceAlphaDropDark,
        ),
        LocalGlassEffectsEnabled provides tuning.glassEffectsEnabled,
        content = content,
    )
}
