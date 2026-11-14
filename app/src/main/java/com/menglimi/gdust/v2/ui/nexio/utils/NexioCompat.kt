package com.menglimi.gdust.v2.ui.nexio.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.haooz.softglass.GlassMaterialLevel
import com.haooz.softglass.glass.BrightTone
import com.haooz.softglass.glass.GlassFill
import com.haooz.softglass.glass.GlassTone
import com.haooz.softglass.glass.SoftGlassStyle
import com.haooz.softglass.glass.ThemeFill
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.LocalUiStyle
import com.menglimi.gdust.v2.ui.UiStyle

@Composable
fun isAppDarkTheme(): Boolean = LocalDs.current.isDark

/** 页面卡片、课程卡和悬浮控件共用的材质档位。 */
@Immutable
data class AppMaterial(
    val level: GlassMaterialLevel,
    val blurDp: Float,
    val surfaceAlpha: Float,
    val courseAlpha: Float,
    val refraction: Float,
    val edgeAlpha: Float,
) {
    val liquid: Boolean get() = level == GlassMaterialLevel.SOFT

    fun courseRefraction(override: Float?): Float =
        if (liquid) (override ?: refraction).coerceIn(0f, 1f) else 0f
}

/** SOFT 保持磨砂；LIQUID 使用清透采样与边缘折射，切换不重建导航树。 */
object AppMaterialSettings {
    private val frosted = AppMaterial(GlassMaterialLevel.FROSTED, 18f, 0.94f, 0.80f, 0f, 0.08f)
    private val liquid = AppMaterial(GlassMaterialLevel.SOFT, 4f, 0.80f, 0.56f, 0.65f, 0.42f)

    fun resolve(style: UiStyle): AppMaterial = when (style) {
        UiStyle.SOFT -> frosted
        UiStyle.LIQUID -> liquid
    }

    @Composable
    fun current(): AppMaterial = resolve(LocalUiStyle.current.value)

    @Composable
    fun materialLevel(): GlassMaterialLevel = current().level

    @Composable
    fun chromeLensEnabled(): Boolean = current().liquid

    @Composable
    fun glassEffectsEnabled(): Boolean = true

    @Composable
    fun glassBlurDp(): Float = current().blurDp

    @Composable
    fun solidSurfaceAlpha(value: Float): Float = if (current().liquid) value else maxOf(value, 0.90f)

    /** 兼容 UI 线课表卡片：双档参数仍以 current() 一份真源为准。 */
    @Composable
    fun cardGlassParams(
        tint: Float, opacity: Float, blurPx: Int, refraction: Float,
    ): GlassCardParams {
        val material = current()
        return GlassCardParams(
            tint = tint,
            opacity = opacity.coerceAtLeast(material.courseAlpha),
            blurPx = blurPx.takeIf { it > 0 } ?: material.blurDp.toInt(),
            refraction = material.courseRefraction(refraction.takeIf { it > 0f }),
        )
    }

    @Composable
    fun chromeGlassStyle(
        base: SoftGlassStyle,
        isDark: Boolean,
        fill: GlassFill = ThemeFill,
        tone: GlassTone = BrightTone,
    ): SoftGlassStyle {
        val material = current()
        return base.copy(fill = fill, tone = tone).filled(isDark).copy(
            refractionScale = material.refraction,
            edgeHighlight = material.liquid,
        )
    }
}

/** 卡片材质档参数，供课表卡片按 SOFT/LIQUID 分派。 */
data class GlassCardParams(
    val tint: Float,
    val opacity: Float,
    val blurPx: Int,
    val refraction: Float,
)
