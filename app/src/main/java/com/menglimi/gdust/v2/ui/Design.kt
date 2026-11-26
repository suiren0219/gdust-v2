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
import com.haooz.nexioui.theme.NexioDarkColorScheme
import com.haooz.nexioui.theme.NexioLightColorScheme
import androidx.compose.ui.unit.sp

/** 应用语义色映射到 NexioUI，业务页与组件库共享深浅色。 */
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
    val brandBlue: Color get() = accent
    val success: Color get() = Color(0xFF36D267)
    val successTint: Color get() = if (isDark) success.copy(alpha = 0.16f).compositeOver(card) else Color(0xFFDEFAE4)
    val chartGreen: Color get() = if (isDark) Color(0xFF4ADE80) else Color(0xFF23C55E)
}

val LocalDs = staticCompositionLocalOf<Ds> { error("no Ds") }

fun nexioColors(dark: Boolean): com.haooz.nexioui.theme.NexioColorScheme {
    val base = if (dark) NexioDarkColorScheme else NexioLightColorScheme
    return base.copy(
        background = base.surface,
        primary = Color(0xFF0A84FF),
        onPrimary = Color.White,
        onSurfaceVariantSummary = if (dark) base.onSurfaceVariantSummary.copy(alpha = 0.7f) else base.onSurfaceVariantSummary,
    )
}

fun dsFor(dark: Boolean): Ds {
    val colors = nexioColors(dark)
    return Ds(
        bg = colors.background,
        card = colors.surfaceContainer,
        cardStroke = Color.Transparent,
        text = colors.onSurfaceContainer,
        sub = colors.onSurfaceVariantSummary,
        accent = colors.primary,
        accentSoft = colors.primary.copy(alpha = if (dark) 0.20f else 0.10f).compositeOver(colors.surfaceContainer),
        breakBar = colors.surfaceContainerHigh,
        danger = colors.error,
        isDark = dark,
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
                onPrimary = Color.White,
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
                onPrimary = Color.White,
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

/** 兼容旧页面命名，外观由统一 NexioUI 卡片承接。 */
@Composable
fun GlassCard(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    AppCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Box(androidx.compose.ui.Modifier.padding(AppUiTokens.CardPadding), content = content)
    }
}
