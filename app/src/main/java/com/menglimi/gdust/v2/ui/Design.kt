package com.menglimi.gdust.v2.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** CZeroX tokens：页面只使用中性实底与高对比文字。 */
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
) {
    val brandBlue: Color get() = if (isDark) Color(0xFF4D8BFF) else Color(0xFF106DFD)
    val success: Color get() = Color(0xFF36D267)
    val successTint: Color get() = if (isDark) success.copy(alpha = 0.16f).compositeOver(card) else Color(0xFFDEFAE4)
    val chartGreen: Color get() = if (isDark) Color(0xFF4ADE80) else Color(0xFF23C55E)
}

val LocalDs = staticCompositionLocalOf<Ds> { error("no Ds") }

fun dsFor(dark: Boolean): Ds = if (dark) {
    Ds(
        bg = Color(0xFF0E0E10),
        card = Color(0xFF1C1C1E),
        cardStroke = Color.Transparent,
        text = Color(0xFFF2F2F7),
        sub = Color.White.copy(alpha = 0.62f),
        accent = Color(0xFF7AB0FF),
        accentSoft = Color(0xFF7AB0FF).copy(alpha = 0.20f),
        breakBar = Color(0xFF2A2A2C),
        danger = Color(0xFFFF6B6B),
        isDark = true,
    )
} else {
    Ds(
        bg = Color(0xFFF7F7F7),
        card = Color.White,
        cardStroke = Color.Transparent,
        text = Color.Black,
        sub = Color(0xFF666666),
        accent = Color(0xFF3482FF),
        accentSoft = Color(0xFF3482FF).copy(alpha = 0.12f),
        breakBar = Color(0xFFF0F0F0),
        danger = Color(0xFFEE4444),
        isDark = false,
    )
}

/** Material 排版统一：主文字与页面底色保持高对比。 */
@Composable
fun WithTypography(ds: Ds, content: @Composable () -> Unit) {
    androidx.compose.material3.MaterialTheme(
        colorScheme = if (ds.isDark) {
            androidx.compose.material3.darkColorScheme(
                background = ds.bg,
                surface = ds.card,
                primary = ds.accent,
                onBackground = ds.text,
                onSurface = ds.text,
                onSurfaceVariant = ds.sub,
                primaryContainer = ds.accentSoft,
                onPrimaryContainer = ds.accent,
                error = ds.danger,
                outline = ds.sub,
                onPrimary = Color.Black,
                surfaceTint = Color.Transparent,
                surfaceContainer = ds.card,
                surfaceContainerHigh = ds.card,
                surfaceContainerHighest = ds.breakBar,
                surfaceContainerLow = ds.card,
                surfaceContainerLowest = ds.bg,
                secondary = ds.accent,
                onSecondary = Color.Black,
                secondaryContainer = ds.accentSoft,
                onSecondaryContainer = ds.text,
                tertiary = ds.accent,
                onTertiary = Color.Black,
                tertiaryContainer = ds.accentSoft,
                onTertiaryContainer = ds.text,
                errorContainer = ds.danger.copy(alpha = 0.12f).compositeOver(ds.card),
                onErrorContainer = ds.text,
            )
        } else {
            androidx.compose.material3.lightColorScheme(
                background = ds.bg,
                surface = ds.card,
                primary = ds.accent,
                onBackground = ds.text,
                onSurface = ds.text,
                onSurfaceVariant = ds.sub,
                primaryContainer = ds.accentSoft,
                onPrimaryContainer = ds.accent,
                error = ds.danger,
                outline = ds.sub,
                onPrimary = Color.Black,
                surfaceTint = Color.Transparent,
                surfaceContainer = ds.card,
                surfaceContainerHigh = ds.card,
                surfaceContainerHighest = ds.breakBar,
                surfaceContainerLow = ds.card,
                surfaceContainerLowest = ds.bg,
                secondary = ds.accent,
                onSecondary = Color.Black,
                secondaryContainer = ds.accentSoft,
                onSecondaryContainer = ds.text,
                tertiary = ds.accent,
                onTertiary = Color.Black,
                tertiaryContainer = ds.accentSoft,
                onTertiaryContainer = ds.text,
                errorContainer = ds.danger.copy(alpha = 0.12f).compositeOver(ds.card),
                onErrorContainer = ds.text,
            )
        },
        typography = androidx.compose.material3.Typography(
            headlineLarge = androidx.compose.ui.text.TextStyle(
                fontSize = 35.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = ds.text,
            ),
            titleMedium = androidx.compose.ui.text.TextStyle(
                fontSize = 17.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = ds.text,
            ),
            bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = ds.text),
            bodySmall = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = ds.sub),
            labelSmall = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = ds.sub),
        ),
        content = content,
    )
}

/** 兼容旧页面命名；业务卡片统一走 CZeroX 实底 AppCard。 */
@Composable
fun GlassCard(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    AppCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Box(androidx.compose.ui.Modifier.padding(20.dp), content = content)
    }
}
