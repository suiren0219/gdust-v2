package com.menglimi.gdust.v2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.haooz.nexioui.material.LocalMaterialNoneEnabled
import com.haooz.nexioui.material.NexioMaterial
import com.haooz.nexioui.overlaydialog.LocalOverlayDialogBackdrop
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.backdrops.LayerBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberCanvasBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop

/** 当前页面/宿主共享的 SoftGlass 采样层；悬浮底栏、玻璃按钮、分段控件和弹窗统一从这里采样。 */
val LocalPageBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** 创建以给定底色打底的 SoftGlass 采样层，避免边缘或透明区域采样出黑边。 */
@Composable
fun rememberAppBackdrop(baseColor: Color = NexioTheme.colorScheme.surface): LayerBackdrop {
    return rememberLayerBackdrop(
        onDraw = remember(baseColor) {
            {
                drawRect(baseColor)
                drawContent()
            }
        },
    )
}

/** 获取当前可用的 SoftGlass 采样层；若在独立 Activity/预览中尚未提供宿主层，则返回纯色 Canvas 兜底。 */
@Composable
fun currentPageBackdrop(): GlassBackdrop {
    val hosted = LocalPageBackdrop.current ?: LocalOverlayDialogBackdrop.current
    if (hosted != null) return hosted
    val fallback = NexioTheme.colorScheme.surface
    return rememberCanvasBackdrop(
        onDraw = remember(fallback) {
            { drawRect(fallback) }
        },
    )
}

/** 纯 NexioUI 主题宿主：不再依赖 MiuixTheme 或旧 kyant backdrop，直接驱动 SoftGlass 材质档。 */
@Composable
fun NexioAppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ds = remember(dark) { dsFor(dark) }
    val colors = remember(dark) { nexioColors(dark) }
    val materialNone = NexioMaterial.isNone()
    NexioTheme(isDark = dark, colorScheme = colors) {
        CompositionLocalProvider(
            LocalDs provides ds,
            LocalMaterialNoneEnabled provides materialNone,
        ) {
            WithTypography(ds) {
                Box(Modifier.fillMaxSize().background(Color.Transparent)) {
                    content()
                }
            }
        }
    }
}
