package com.menglimi.gdust.v2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haooz.softglass.backdrop.backdrops.LayerBackdrop
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import com.haooz.softglass.shape.ContinuousRoundedRectangle

/** 设计令牌：页面取色唯一真源（玻璃引擎由 UiStyle 分派）。 */
data class Ds(
    val bg: Color, val card: Color, val cardStroke: Color, val text: Color,
    val sub: Color, val accent: Color, val accentSoft: Color, val breakBar: Color,
    val danger: Color, val isDark: Boolean,
)

val LocalDs = staticCompositionLocalOf<Ds> { error("no Ds") }
val LocalBackdrop = staticCompositionLocalOf<LayerBackdrop> { error("no backdrop") }

@Composable
fun dsFor(dark: Boolean): Ds = if (dark) Ds(
    bg = Color(0xFF0D0D10), card = Color(0xFF1C1C21), cardStroke = Color(0x22FFFFFF),
    text = Color(0xFFF2F2F7), sub = Color(0xFF9A9AA5), accent = Color(0xFF0A84FF),
    accentSoft = Color(0x220A84FF), breakBar = Color(0xFF232329), danger = Color(0xFFFF6B6B), isDark = true,
) else Ds(
    bg = Color(0xFFF7F7FA), card = Color(0xFFFFFFFF), cardStroke = Color(0x0A000000),
    text = Color(0xFF1C1C1E), sub = Color(0xFF8A8A8F), accent = Color(0xFF007AFF),
    accentSoft = Color(0x14007AFF), breakBar = Color(0xFFECECF1), danger = Color(0xFFE5484D), isDark = false,
)

/**
 * 莫奈取色：拿系统壁纸色（Material You）当种子，经 MaterialKolor 生成完整色调方案。
 * API 33+ 无需权限、无需 AppCompat；取不到就回落默认 Ds。两套玻璃风格共用。
 */
@Composable
fun monetDs(dark: Boolean, base: Ds, enabled: Boolean): Ds {
    if (!enabled || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return base
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val system = if (dark) androidx.compose.material3.dynamicDarkColorScheme(ctx)
    else androidx.compose.material3.dynamicLightColorScheme(ctx)
    val scheme = com.materialkolor.scheme.SchemeTonalSpot(
        sourceColorHct = com.materialkolor.hct.Hct.fromInt(system.primary.toArgb()),
        isDark = dark,
        contrastLevel = 0.0,
    )
    return base.copy(
        bg = Color(scheme.background),
        card = Color(scheme.surface),
        text = Color(scheme.onSurface),
        sub = Color(scheme.onSurfaceVariant),
        accent = Color(scheme.primary),
        accentSoft = Color(scheme.primary).copy(alpha = 0.14f),
        breakBar = Color(scheme.surfaceContainerHigh),
        danger = Color(scheme.error),
    )
}

/** Material 排版统一（两套风格共用，颜色随 Ds）。 */
@Composable
fun WithTypography(ds: Ds, content: @Composable () -> Unit) {
    androidx.compose.material3.MaterialTheme(
        colorScheme = if (ds.isDark) androidx.compose.material3.darkColorScheme(
            background = ds.bg, surface = ds.card, primary = ds.accent,
        ) else androidx.compose.material3.lightColorScheme(
            background = ds.bg, surface = ds.card, primary = ds.accent,
        ),
        typography = androidx.compose.material3.Typography(
            headlineLarge = androidx.compose.ui.text.TextStyle(fontSize = 35.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = ds.text),
            titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, color = ds.text),
            bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = ds.text),
            bodySmall = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = ds.sub),
            labelSmall = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = ds.sub),
        ),
        content = content,
    )
}

/** 玻璃卡按风格分派：SOFT 走 softglass drawBackdrop，LIQUID 走 GlasmioCard。 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    if (LocalUiStyle.current.value == UiStyle.SOFT) {
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
            Box(Modifier.padding(16.dp)) { content() }
        }
    } else {
        io.github.neoruaa.glasmio.ui.GlasmioCard(
            modifier = modifier.fillMaxWidth(),
            onClick = onClick,
        ) {
            Box(Modifier.padding(16.dp)) { content() }
        }
    }
}
