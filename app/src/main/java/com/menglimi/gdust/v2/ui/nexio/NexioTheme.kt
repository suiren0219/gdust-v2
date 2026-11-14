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
        val ds = Ds(
            bg = if (monetEnabled) colors.surface else if (dark) Color.Black else Color(0xFFF7F7F7),
            card = colors.surfaceContainer,
            cardStroke = colors.dividerLine.copy(alpha = 0.45f),
            text = colors.onSurface,
            sub = colors.onSurfaceVariantSummary,
            accent = colors.primary,
            accentSoft = colors.primary.copy(alpha = if (dark) 0.2f else 0.10f),
            breakBar = colors.surfaceContainerHigh.copy(alpha = 0.55f),
            danger = colors.error,
            isDark = dark,
        )
        SideEffect { com.kyant.backdrop.AppThemeSnapshot.isDark.value = dark }
        CompositionLocalProvider(LocalDs provides ds) {
            WithTypography(ds, content)
        }
    }
}
