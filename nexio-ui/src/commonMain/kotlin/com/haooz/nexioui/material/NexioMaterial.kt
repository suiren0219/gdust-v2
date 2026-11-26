package com.haooz.nexioui.material

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.haooz.softglass.GlassMaterialLevel
import com.haooz.softglass.GlassPreset
import com.haooz.softglass.glass.BrightTone
import com.haooz.softglass.glass.GlassFill
import com.haooz.softglass.glass.GlassTone
import com.haooz.softglass.glass.SoftGlassStyle
import com.haooz.softglass.glass.ThemeFill
import com.haooz.softglass.liquidClear
import com.haooz.softglass.surfaceAlpha
import com.haooz.softglass.tuning

/** NexioUI 默认柔光玻璃样式。 */
val NexioGlassStyle: SoftGlassStyle = GlassPreset.Default.style

/** 玻璃材质档。 */
enum class NexioMaterialLevel {
    /** 柔光玻璃：完整折射 + 方向光。 */
    Soft,

    /** 液态玻璃：折射换液态位移场、调色走清透口径，模糊压低一档。 */
    Liquid,

    /** 清透磨砂：无折射、无边光。 */
    Frosted,

    /** 无材质：玻璃采样与折射全部关闭。 */
    None,
}

/**
 * 全局材质配置。
 *
 * 控件在组合 / 绘制期读取这里的值，应用只需要在设置页改 [level]。
 */
object NexioMaterial {

    var level: NexioMaterialLevel by mutableStateOf(NexioMaterialLevel.Soft)

    private val glassLevel: GlassMaterialLevel
        get() = when (level) {
            NexioMaterialLevel.Soft -> GlassMaterialLevel.SOFT
            NexioMaterialLevel.Liquid -> GlassMaterialLevel.LIQUID
            NexioMaterialLevel.Frosted -> GlassMaterialLevel.FROSTED
            NexioMaterialLevel.None -> GlassMaterialLevel.NONE
        }

    /** 小控件采样模糊半径（dp）。 */
    fun glassBlurDp(): Float = glassLevel.tuning().blurDp

    /** 弹出面板展开态采样模糊半径（dp）。 */
    fun glassPanelBlurDp(): Float = glassLevel.tuning().panelBlurDp

    /** 对话框 / 贴底弹窗采样模糊半径（dp）。 */
    fun glassDialogBlurDp(): Float = glassLevel.tuning().dialogBlurDp

    /** 玻璃材质总开关。 */
    fun glassEffectsEnabled(): Boolean = glassLevel.tuning().glassEffectsEnabled

    /** 是否柔光档。 */
    fun isSoft(): Boolean = level == NexioMaterialLevel.Soft

    /** 是否液态档。 */
    fun isLiquid(): Boolean = level == NexioMaterialLevel.Liquid

    /** 是否无材质档。 */
    fun isNone(): Boolean = level == NexioMaterialLevel.None

    /** 玻璃底色 alpha，其余档按材质口径降低。 */
    fun glassSurfaceAlpha(milledAlpha: Float, isLight: Boolean): Float =
        glassLevel.surfaceAlpha(milledAlpha, isLight)

    /** 无材质档把半透明底色实色化。 */
    fun solidSurfaceAlpha(baseAlpha: Float): Float = if (isNone()) 1f else baseAlpha

    /** 柔光档底色 alpha 降幅，供上层下发自己的材质令牌。 */
    fun glassSurfaceAlphaDrop(isLight: Boolean): Float {
        val tuning = glassLevel.tuning()
        return if (isLight) tuning.surfaceAlphaDropLight else tuning.surfaceAlphaDropDark
    }

    /**
     * 悬浮控件的玻璃样式：基准样式套上基调与填充，再按材质档决定折射与高光。
     *
     * @param base 基准样式，默认 [NexioGlassStyle]。
     * @param isDark 是否深色。
     * @param fill 玻璃填充策略。
     * @param tone 方向光基调。
     */
    fun chromeGlassStyle(
        base: SoftGlassStyle = NexioGlassStyle,
        isDark: Boolean,
        fill: GlassFill = ThemeFill,
        tone: GlassTone = BrightTone,
    ): SoftGlassStyle {
        val resolved = base.copy(fill = fill, tone = tone).filled(isDark)
        // liquidClear 必须排在 filled 之后：基调会把调色字段写回柔光档的值
        val glass = if (isLiquid()) {
            resolved.copy(liquidRefraction = true).liquidClear()
        } else {
            resolved
        }
        return if (isSoft() || isLiquid()) {
            glass
        } else {
            glass.copy(refractionScale = 0f, edgeHighlight = false)
        }
    }
}
