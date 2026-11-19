package com.menglimi.gdust.v2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@Composable
fun CZeroTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ds = dsFor(dark)
    val colors = remember(ds) {
        (if (dark) darkColorScheme() else lightColorScheme()).copy(
            primary = ds.accent,
            onPrimary = Color.Black,
            primaryVariant = ds.accentSoft,
            onPrimaryVariant = ds.text,
            primaryContainer = ds.accentSoft,
            onPrimaryContainer = ds.text,
            error = ds.danger,
            onError = Color.Black,
            errorContainer = ds.danger.copy(alpha = 0.12f).compositeOver(ds.card),
            onErrorContainer = ds.text,
            secondary = ds.breakBar,
            onSecondary = ds.text,
            secondaryVariant = ds.breakBar,
            onSecondaryVariant = ds.text,
            secondaryContainer = ds.breakBar,
            onSecondaryContainer = ds.sub,
            secondaryContainerVariant = ds.breakBar,
            onSecondaryContainerVariant = ds.sub,
            tertiaryContainer = ds.accentSoft,
            onTertiaryContainer = ds.text,
            tertiaryContainerVariant = ds.accentSoft,
            background = ds.bg,
            onBackground = ds.text,
            onBackgroundVariant = ds.sub,
            surface = ds.bg,
            onSurface = ds.text,
            surfaceVariant = ds.card,
            onSurfaceSecondary = ds.sub,
            onSurfaceVariantSummary = ds.sub,
            onSurfaceVariantActions = ds.sub,
            surfaceContainer = ds.card,
            onSurfaceContainer = ds.text,
            onSurfaceContainerVariant = ds.sub,
            surfaceContainerHigh = ds.breakBar,
            onSurfaceContainerHigh = ds.text,
            surfaceContainerHighest = ds.breakBar,
            onSurfaceContainerHighest = ds.text,
            disabledPrimaryButton = ds.breakBar,
            disabledOnPrimaryButton = ds.sub,
            disabledOnSurface = ds.sub,
            outline = ds.sub,
            dividerLine = Color.Transparent,
            sliderKeyPointForeground = ds.accent,
        )
    }
    SideEffect { com.kyant.backdrop.AppThemeSnapshot.isDark.value = dark }
    MiuixTheme(colors = colors) {
        CompositionLocalProvider(
            LocalDs provides ds,
            top.yukonga.miuix.kmp.material.LocalMaterialNoneEnabled provides true,
            top.yukonga.miuix.kmp.material.LocalChromeLensEnabled provides false,
            top.yukonga.miuix.kmp.material.LocalGlassBlurDp provides 0f,
            top.yukonga.miuix.kmp.material.LocalGlassPanelBlurDp provides 0f,
            top.yukonga.miuix.kmp.material.LocalGlassDialogBlurDp provides 0f,
        ) {
            WithTypography(ds) {
                Box(Modifier.fillMaxSize().background(ds.bg)) { content() }
            }
        }
    }
}
