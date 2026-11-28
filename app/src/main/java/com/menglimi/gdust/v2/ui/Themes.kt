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

/**
 * 显式下发给同级悬浮覆盖层的 SoftGlass 采样层。
 * 注意：绝不要把祖先容器的 [LayerBackdrop] 通过本 Local 下发给其子树内联组件，否则会形成 RenderNode 绘制环路导致 RenderThread 栈溢出闪退。
 */
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

/** 创建非递归的纯色 SoftGlass 采样层，供页面内联玻璃组件（如分段选择器、内联按钮）安全采样。 */
@Composable
fun rememberSurfaceBackdrop(color: Color = NexioTheme.colorScheme.surfaceContainerHigh): GlassBackdrop {
    return rememberCanvasBackdrop(
        onDraw = remember(color) {
            { drawRect(color) }
        },
    )
}

/** 获取当前可用的安全 SoftGlass 采样层；绝不回退到根级 [LocalOverlayDialogBackdrop]，避免子树采样祖先层导致 RenderThread 递归闪退。 */
@Composable
fun currentPageBackdrop(fallbackColor: Color = NexioTheme.colorScheme.surface): GlassBackdrop {
    val hosted = LocalPageBackdrop.current
    if (hosted != null) return hosted
    return rememberSurfaceBackdrop(fallbackColor)
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
