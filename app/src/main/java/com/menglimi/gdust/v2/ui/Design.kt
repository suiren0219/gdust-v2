package com.menglimi.gdust.v2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.softglass.GlassMaterialLevel
import com.haooz.softglass.GlassPreset
import com.haooz.softglass.SoftGlassTheme
import com.haooz.softglass.backdrop.backdrops.LayerBackdrop
import com.haooz.softglass.backdrop.backdrops.layerBackdrop
import com.haooz.softglass.backdrop.backdrops.rememberLayerBackdrop
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import com.haooz.softglass.shape.ContinuousRoundedRectangle

/** 设计令牌：页面取色唯一真源（玻璃渲染交给 softglass）。 */
data class Ds(
    val bg: Color,
    val card: Color,
    val cardStroke: Color,
    val text: Color,
    val sub: Color,
    val accent: Color,
    val accentSoft: Color,
    val breakBar: Color,
    val danger: Color,
    val isDark: Boolean,
)

val LocalDs = staticCompositionLocalOf<Ds> { error("no Ds") }
val LocalBackdrop = staticCompositionLocalOf<LayerBackdrop> { error("no backdrop") }

@Composable
fun GdustTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ds = if (dark) Ds(
        bg = Color(0xFF0D0D10), card = Color(0xFF1C1C21), cardStroke = Color(0x22FFFFFF),
        text = Color(0xFFF2F2F7), sub = Color(0xFF9A9AA5), accent = Color(0xFF0A84FF),
        accentSoft = Color(0x220A84FF), breakBar = Color(0xFF232329), danger = Color(0xFFFF6B6B), isDark = true,
    ) else Ds(
        bg = Color(0xFFF7F7FA), card = Color(0xFFFFFFFF), cardStroke = Color(0x0A000000),
        text = Color(0xFF1C1C1E), sub = Color(0xFF8A8A8F), accent = Color(0xFF007AFF),
        accentSoft = Color(0x14007AFF), breakBar = Color(0xFFECECF1), danger = Color(0xFFE5484D), isDark = false,
    )

    SoftGlassTheme(
        preset = GlassPreset.Default,
        level = GlassMaterialLevel.SOFT,  // HyperOS 完整柔光（折射+方向光）
        isDark = dark,
        hasWallpaper = false,
    ) {
        val backdrop = rememberLayerBackdrop()
        MaterialTheme(
            colorScheme = if (dark) darkColorScheme(
                background = ds.bg, surface = ds.card, primary = ds.accent,
            ) else lightColorScheme(
                background = ds.bg, surface = ds.card, primary = ds.accent,
            ),
            typography = Typography(
                headlineLarge = TextStyle(fontSize = 35.sp, fontWeight = FontWeight.Bold, color = ds.text),
                titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = ds.text),
                bodyLarge = TextStyle(fontSize = 16.sp, color = ds.text),
                bodySmall = TextStyle(fontSize = 13.sp, color = ds.sub),
                labelSmall = TextStyle(fontSize = 11.sp, color = ds.sub),
            ),
        ) {
            CompositionLocalProvider(LocalDs provides ds, LocalBackdrop provides backdrop) {
                // softglass 约束 1：玻璃节点必须画在采样层「之外」，自采样会直接崩 RenderThread。
                // 采样层与内容层平级：先铺被采样的底色，再叠内容（玻璃卡在内容层里）。
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(ds.bg))
                    content()
                }
            }
        }
    }
}

/** 统一玻璃卡：HyperOS 柔光（折射+方向光+7级模糊）+ 连续圆角 20dp。 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalBackdrop.current
    val shape = ContinuousRoundedRectangle(20.dp)
    Box(
        modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = { blur(16f) },
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}
