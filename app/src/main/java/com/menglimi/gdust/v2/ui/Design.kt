package com.menglimi.gdust.v2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 设计令牌：整个 App 唯一颜色/尺寸真源，深浅色自动跟随系统。 */
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

@Composable
fun GdustTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ds = if (dark) Ds(
        bg = Color(0xFF0D0D10),
        card = Color(0xFF1C1C21),
        cardStroke = Color(0x22FFFFFF),
        text = Color(0xFFF2F2F7),
        sub = Color(0xFF9A9AA5),
        accent = Color(0xFF0A84FF),
        accentSoft = Color(0x220A84FF),
        breakBar = Color(0xFF232329),
        danger = Color(0xFFFF6B6B),
        isDark = true,
    ) else Ds(
        bg = Color(0xFFF7F7FA),
        card = Color(0xFFFFFFFF),
        cardStroke = Color(0x0A000000),
        text = Color(0xFF1C1C1E),
        sub = Color(0xFF8A8A8F),
        accent = Color(0xFF007AFF),
        accentSoft = Color(0x14007AFF),
        breakBar = Color(0xFFECECF1),
        danger = Color(0xFFE5484D),
        isDark = false,
    )

    val colors = if (dark) darkColorScheme(
        background = ds.bg, surface = ds.card, primary = ds.accent,
        onBackground = ds.text, onSurface = ds.text,
    ) else lightColorScheme(
        background = ds.bg, surface = ds.card, primary = ds.accent,
        onBackground = ds.text, onSurface = ds.text,
    )

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 35.sp, fontWeight = FontWeight.Bold, color = ds.text),
            titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = ds.text),
            bodyLarge = TextStyle(fontSize = 16.sp, color = ds.text),
            bodySmall = TextStyle(fontSize = 13.sp, color = ds.sub),
            labelSmall = TextStyle(fontSize = 11.sp, color = ds.sub),
        ),
    ) {
        CompositionLocalProvider(LocalDs provides ds) {
            content()
        }
    }
}

/** 统一玻璃卡：20dp 圆角、细描边、轻微渐变高光。 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val ds = LocalDs.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        ds.card.copy(alpha = if (ds.isDark) 0.92f else 0.96f),
                        ds.card,
                    )
                ),
                shape = RoundedCornerShape(20.dp),
            )
            .border(0.6.dp, ds.cardStroke, RoundedCornerShape(20.dp)),
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}
