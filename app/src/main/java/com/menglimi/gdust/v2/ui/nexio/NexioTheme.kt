package com.menglimi.gdust.v2.ui.nexio

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.menglimi.gdust.v2.ui.Ds
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.WithTypography
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** Adapted from Nexio Theme.kt / ThemeUtils.kt; retains gdust's preference keys. */
@Composable
fun NexioTheme(
    monetEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val controller = remember { ThemeController() }
    controller.colorSchemeMode = when {
        monetEnabled && dark -> ColorSchemeMode.MonetDark
        monetEnabled -> ColorSchemeMode.MonetLight
        dark -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.Light
    }
    MiuixTheme(controller = controller) {
        val colors = MiuixTheme.colorScheme
        val base = com.menglimi.gdust.v2.ui.dsFor(dark)
        val ds = if (monetEnabled) base.copy(
            bg = colors.surface,
            card = colors.surfaceContainer,
            text = colors.onSurface,
            sub = colors.onSurfaceVariantSummary,
            accent = colors.primary,
            accentSoft = colors.primary.copy(alpha = if (dark) 0.2f else 0.10f),
            breakBar = colors.surfaceContainerHigh.copy(alpha = 0.55f),
            danger = colors.error,
        ) else base
        val pageColors = colors.copy(
            primary = ds.accent,
            onPrimary = if (!monetEnabled && dark) ds.bg else colors.onPrimary,
            primaryContainer = ds.accentSoft,
            onPrimaryContainer = ds.accent,
            background = ds.bg,
            onBackground = ds.text,
            surface = ds.bg,
            onSurface = ds.text,
            surfaceContainer = ds.card,
            onSurfaceContainer = ds.text,
            onSurfaceVariantSummary = ds.sub,
            onSurfaceVariantActions = ds.sub,
            error = ds.danger,
        )
        SideEffect { com.kyant.backdrop.AppThemeSnapshot.isDark.value = dark }
        MiuixTheme(colors = pageColors) {
            CompositionLocalProvider(LocalDs provides ds) {
                WithTypography(ds, content)
            }
        }
    }
}
