package com.menglimi.gdust.v2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import com.haooz.softglass.backdrop.effects.runtimeShaderEffect
import com.haooz.softglass.glass.SOFT_GLASS_LIVE_SHADER
import com.haooz.softglass.glass.applySoftGlass
import com.menglimi.gdust.v2.ui.nexio.LocalPageLayerBackdrop
import com.menglimi.gdust.v2.ui.nexio.glass.ScheduleSoftGlassStyle
import com.menglimi.gdust.v2.ui.nexio.utils.AppMaterialSettings
import top.yukonga.miuix.kmp.theme.LocalContentColor

object AppUiTokens {
    val PagePadding = 16.dp
    val TitleSize = 34.sp
    val TitleWeight = FontWeight.Medium
    val CardCorner = 24.dp
    val ItemCorner = 16.dp
}

@Composable
fun secondaryTextColor(): Color {
    val ds = LocalDs.current
    return ds.sub.compositeOver(ds.card)
}

/** 卡外文字不直接叠在任意明暗壁纸上；半透底衬让壁纸仍可透出。 */
@Composable
fun Modifier.pageTextBacking(cornerRadius: Dp = AppUiTokens.ItemCorner): Modifier =
    if (LocalWallpaperActive.current) {
        background(LocalDs.current.card.copy(alpha = 0.72f), RoundedCornerShape(cornerRadius))
    } else this

/** 只采样宿主背景层，表面与内容共用一个轮廓，避免自采样及两层圆角。 */
@Composable
fun Modifier.appSurface(
    cornerRadius: Dp = AppUiTokens.CardCorner,
): Modifier {
    val ds = LocalDs.current
    val material = AppMaterialSettings.current()
    val backdrop = LocalPageLayerBackdrop.current
    val wallpaper = LocalWallpaperActive.current && backdrop != null
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    if (!wallpaper) return background(ds.card, shape).clip(shape)
    val surface = ds.card.copy(alpha = material.surfaceAlpha)
    val glassStyle = AppMaterialSettings.chromeGlassStyle(ScheduleSoftGlassStyle, ds.isDark)
    val outline = if (material.liquid) Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = material.edgeAlpha),
            ds.accent.copy(alpha = if (ds.isDark) 0.24f else 0.16f),
            Color.White.copy(alpha = material.edgeAlpha * 0.5f),
        ),
    ) else Brush.linearGradient(listOf(ds.cardStroke, ds.cardStroke))
    val base = if (material.liquid) shadow(4.dp, shape, clip = false) else this
    return base.then(
        if (wallpaper) Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            shadow = null,
            effects = {
                blur(material.blurDp.dp.toPx(), TileMode.Mirror)
                if (material.liquid) {
                    // 着色器坐标与卡片尺寸一致，不能把模糊外扩算进高光轮廓。
                    padding = 0f
                    runtimeShaderEffect(
                        key = "GdustSharedCardGlass",
                        shaderString = SOFT_GLASS_LIVE_SHADER,
                        uniformShaderName = "content",
                    ) {
                        applySoftGlass(glassStyle, size.width, size.height, radiusPx = cornerRadius.toPx())
                    }
                }
            },
            onDrawSurface = { drawRect(surface) },
        ) else Modifier.background(surface, shape),
    ).border(if (material.liquid) 1.dp else 0.5.dp, outline, shape).clip(shape)
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = AppUiTokens.CardCorner,
    insideMargin: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val foreground = LocalDs.current.text
    CompositionLocalProvider(
        LocalContentColor provides foreground,
        androidx.compose.material3.LocalContentColor provides foreground,
    ) {
        Column(
            modifier.appSurface(cornerRadius)
                .semantics { isTraversalGroup = true }
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(insideMargin),
            content = content,
        )
    }
}
