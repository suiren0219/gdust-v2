package com.menglimi.gdust.v2.ui.nexio.utils

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Nexio 组件的接线点：上游这些工具读的是它自己的偏好（主题档位/材质档/壁纸）。
 * 本项目只保留语义等价的最小实现，避免把上游整套设置系统搬进来。
 */

val AppThemeSnapshot = com.kyant.backdrop.AppThemeSnapshot

val PageBackdropSnapshot = com.kyant.backdrop.PageBackdropSnapshot

/** 当前生效的深浅色；顺带把结果写进 [AppThemeSnapshot] 供 draw 阶段读取。 */
@Composable
fun isAppDarkTheme(): Boolean {
    val dark = isSystemInDarkTheme()
    SideEffect { AppThemeSnapshot.isDark.value = dark }
    return dark
}

/** 材质档位：本项目只有「标准」一档，取值对齐上游默认（均衡）。 */
object AppMaterialSettings {
    fun solidSurfaceAlpha(value: Float): Float = value
    fun chromeLensEnabled(): Boolean = true
    fun glassEffectsEnabled(): Boolean = true
    fun glassBlurDp(): Float = 8f
    fun resolveEdgeLight(base: com.kyant.backdrop.edgelight.EdgeLight, isLight: Boolean, color: Color?): com.kyant.backdrop.edgelight.EdgeLight = base
}

/** 描边光：上游注入「是否浅色 + 材质档」，这里只保留深浅色信息。 */
@Composable
fun rememberDefaultEdgeLight(baseColor: Color? = null): com.kyant.backdrop.edgelight.EdgeLight =
    com.kyant.backdrop.edgelight.rememberDefaultEdgeLight(
        baseColor = baseColor,
        isLightTheme = !isAppDarkTheme(),
        resolveMaterial = { base, _, _ -> base },
    )

@Composable
fun rememberCardEdgeLight(baseColor: Color? = null): com.kyant.backdrop.edgelight.EdgeLight =
    com.kyant.backdrop.edgelight.rememberCardEdgeLight(
        baseColor = baseColor,
        isLightTheme = !isAppDarkTheme(),
        resolveMaterial = { base, _, _ -> base },
    )

/** 底栏内边距档位：上游按屏幕尺寸取，这里固定标准值。 */
val BottomBarHorizontalPadding = 4.dp
