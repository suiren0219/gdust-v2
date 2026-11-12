package com.menglimi.gdust.v2.ui.nexio.utils

import androidx.compose.runtime.Composable
import com.haooz.softglass.GlassMaterialLevel
import com.haooz.softglass.glass.BrightTone
import com.haooz.softglass.glass.GlassFill
import com.haooz.softglass.glass.GlassTone
import com.haooz.softglass.glass.SoftGlassStyle
import com.haooz.softglass.glass.ThemeFill
import com.haooz.softglass.tuning
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.LocalUiStyle
import com.menglimi.gdust.v2.ui.UiStyle

/** Read the host theme; SoftGlassTheme owns GlassEnvironment for drawing. */
@Composable
fun isAppDarkTheme(): Boolean = LocalDs.current.isDark

/**
 * Adapted from NexioSchedule 28a99a3 AppMaterialSettings.kt (AGPL-3.0).
 * gdust SOFT is the upstream frosted tier; LIQUID retains refraction and edge highlights.
 * Read CompositionLocal state directly so style changes invalidate the remembered shader effects.
 */
object AppMaterialSettings {
    @Composable
    private fun materialLevel(): GlassMaterialLevel = when (LocalUiStyle.current.value) {
        UiStyle.SOFT -> GlassMaterialLevel.FROSTED
        UiStyle.LIQUID -> GlassMaterialLevel.SOFT
    }

    @Composable
    fun chromeLensEnabled(): Boolean = materialLevel() == GlassMaterialLevel.SOFT

    @Composable
    fun glassEffectsEnabled(): Boolean = materialLevel() != GlassMaterialLevel.NONE

    @Composable
    fun glassBlurDp(): Float = materialLevel().tuning().blurDp

    fun solidSurfaceAlpha(value: Float): Float = value

    @Composable
    fun chromeGlassStyle(
        base: SoftGlassStyle,
        isDark: Boolean,
        fill: GlassFill = ThemeFill,
        tone: GlassTone = BrightTone,
    ): SoftGlassStyle {
        val resolved = base.copy(fill = fill, tone = tone).filled(isDark)
        return if (chromeLensEnabled()) resolved
        else resolved.copy(refractionScale = 0f, edgeHighlight = false)
    }
}
