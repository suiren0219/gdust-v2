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
 * Adapted from NexioSchedule 28a99a3 AppMaterialSettings.kt.
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

    /** SOFT=磨砂（更实、无折射），LIQUID=通透（折射 + 高光）。 */
    @Composable
    fun solidSurfaceAlpha(value: Float): Float =
        if (materialLevel() == GlassMaterialLevel.FROSTED) value.coerceAtLeast(0.82f) else value * 0.65f

    @Composable
    fun cardGlassParams(
        tint: Float, opacity: Float, blurPx: Int, refraction: Float,
    ): GlassCardParams = if (materialLevel() == GlassMaterialLevel.FROSTED) {
        GlassCardParams(tint, opacity.coerceAtLeast(0.55f), (blurPx + 6).coerceAtMost(20), 0f)
    } else {
        // 方形卡片上的 shader lens 会画圆形光晕（P3 的残留圆弧），默认不开折射；
        // 液态档差异用更透的底色 + 描边光体现，折射由「课表效果」页滑杆显式启用
        GlassCardParams(tint * 0.75f, opacity * 0.85f, blurPx, refraction.coerceAtMost(0.35f))
    }

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

/** 卡片材质档参数，供课表卡片按 SOFT/LIQUID 分派。 */
data class GlassCardParams(
    val tint: Float,
    val opacity: Float,
    val blurPx: Int,
    val refraction: Float,
)
