package com.menglimi.ksu.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object KsuTokens {
    val PagePadding = 16.dp
    val CardGap = 12.dp
    val HeroRadius = 28.dp
    val CardRadius = 24.dp
    val BrandGreen = Color(0xFF36D167)
}

private val Light = lightColorScheme(
    primary = Color(0xFF236B40), onPrimary = Color.White,
    primaryContainer = Color(0xFFDFFAE4), onPrimaryContainer = Color(0xFF164A29),
    secondary = Color(0xFF526456), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEADD), onSecondaryContainer = Color(0xFF263C2B),
    tertiary = Color(0xFF4D657F), tertiaryContainer = Color(0xFFD9E7FA), onTertiaryContainer = Color(0xFF203C56),
    background = Color(0xFFF4F6F3), onBackground = Color(0xFF1A211B),
    surface = Color(0xFFF4F6F3), onSurface = Color(0xFF1A211B),
    surfaceBright = Color.White, surfaceContainer = Color(0xFFEBEFEB),
    surfaceContainerLow = Color(0xFFFDFEFC), surfaceContainerHigh = Color(0xFFE6EBE5),
    surfaceContainerHighest = Color(0xFFDEE4DC), onSurfaceVariant = Color(0xFF586259),
    outline = Color(0xFF788279), outlineVariant = Color(0xFFC9D2C8),
    error = Color(0xFFBA3435), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF68000A),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF9BD6AC), onPrimary = Color(0xFF06391C),
    primaryContainer = Color(0xFF1A3825), onPrimaryContainer = Color(0xFFBCECC8),
    secondary = Color(0xFFB8CCBA), secondaryContainer = Color(0xFF354A3A), onSecondaryContainer = Color(0xFFDCEADD),
    tertiary = Color(0xFFB2CAE6), tertiaryContainer = Color(0xFF314B65), onTertiaryContainer = Color(0xFFD9E7FA),
    background = Color(0xFF101510), onBackground = Color(0xFFE2E8DE),
    surface = Color(0xFF101510), onSurface = Color(0xFFE2E8DE),
    surfaceBright = Color(0xFF232B24), surfaceContainerLow = Color(0xFF192019),
    surfaceContainer = Color(0xFF1D251E), surfaceContainerHigh = Color(0xFF283229),
    surfaceContainerHighest = Color(0xFF333D34), onSurfaceVariant = Color(0xFFBAC5B7),
    outlineVariant = Color(0xFF404B40),
)

@Composable
fun KsuTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val dark = when (appearance.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    var scheme = if (appearance.dynamic) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) Dark else Light
    if (dark && appearance.amoled) scheme = scheme.copy(
        background = Color.Black, surface = Color.Black, surfaceDim = Color.Black,
        surfaceBright = Color.Black, surfaceContainer = Color.Black, surfaceContainerLow = Color.Black,
        surfaceContainerLowest = Color.Black, surfaceContainerHigh = Color.Black, surfaceContainerHighest = Color.Black,
    )
    MaterialTheme(
        colorScheme = animatedScheme(scheme),
        shapes = Shapes(RoundedCornerShape(8.dp), RoundedCornerShape(12.dp), RoundedCornerShape(18.dp),
            RoundedCornerShape(24.dp), RoundedCornerShape(32.dp)),
        typography = Typography(bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp)),
        content = {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                content()
            }
        },
    )
}

@Composable
private fun animatedScheme(s: ColorScheme): ColorScheme {
    @Composable fun smooth(color: Color): Color {
        val value by animateColorAsState(color, spring(stiffness = 400f), label = "theme")
        return value
    }
    return s.copy(
        primary = smooth(s.primary), onPrimary = smooth(s.onPrimary), primaryContainer = smooth(s.primaryContainer),
        onPrimaryContainer = smooth(s.onPrimaryContainer), inversePrimary = smooth(s.inversePrimary),
        secondary = smooth(s.secondary), onSecondary = smooth(s.onSecondary), secondaryContainer = smooth(s.secondaryContainer),
        onSecondaryContainer = smooth(s.onSecondaryContainer), tertiary = smooth(s.tertiary), onTertiary = smooth(s.onTertiary),
        tertiaryContainer = smooth(s.tertiaryContainer), onTertiaryContainer = smooth(s.onTertiaryContainer),
        background = smooth(s.background), onBackground = smooth(s.onBackground), surface = smooth(s.surface),
        onSurface = smooth(s.onSurface), surfaceVariant = smooth(s.surfaceVariant), onSurfaceVariant = smooth(s.onSurfaceVariant),
        surfaceTint = smooth(s.surfaceTint), inverseSurface = smooth(s.inverseSurface), inverseOnSurface = smooth(s.inverseOnSurface),
        error = smooth(s.error), onError = smooth(s.onError), errorContainer = smooth(s.errorContainer), onErrorContainer = smooth(s.onErrorContainer),
        outline = smooth(s.outline), outlineVariant = smooth(s.outlineVariant), scrim = smooth(s.scrim),
        surfaceBright = smooth(s.surfaceBright), surfaceDim = smooth(s.surfaceDim),
        surfaceContainer = smooth(s.surfaceContainer), surfaceContainerHigh = smooth(s.surfaceContainerHigh),
        surfaceContainerHighest = smooth(s.surfaceContainerHighest), surfaceContainerLow = smooth(s.surfaceContainerLow),
        surfaceContainerLowest = smooth(s.surfaceContainerLowest),
    )
}
