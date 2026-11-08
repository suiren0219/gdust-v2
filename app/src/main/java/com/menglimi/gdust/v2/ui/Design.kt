package com.menglimi.gdust.v2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
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

    // 主题真源：Glasmio（iOS 27 液态玻璃），Ds 只是页面取色的便捷映射
    GlasmioTheme(isDark = dark) {
        val glass = GlasmioTheme.colors
        val mapped = ds.copy(
            bg = glass.groupedBackground,
            card = glass.container,
            text = glass.content,
            sub = glass.secondaryContent,
            accent = glass.accent,
            danger = glass.destructive,
        )
        MaterialTheme(
            colorScheme = if (dark) androidx.compose.material3.darkColorScheme(
                background = mapped.bg, surface = mapped.card, primary = mapped.accent,
            ) else androidx.compose.material3.lightColorScheme(
                background = mapped.bg, surface = mapped.card, primary = mapped.accent,
            ),
            typography = Typography(
                headlineLarge = TextStyle(fontSize = 35.sp, fontWeight = FontWeight.Bold, color = mapped.text),
                titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = mapped.text),
                bodyLarge = TextStyle(fontSize = 16.sp, color = mapped.text),
                bodySmall = TextStyle(fontSize = 13.sp, color = mapped.sub),
                labelSmall = TextStyle(fontSize = 11.sp, color = mapped.sub),
            ),
        ) {
            // BackdropHost：sampledContent 提供被玻璃采样的背景，overlayContent 是全部界面
            GlasmioBackdropHost(
                modifier = Modifier.fillMaxSize(),
                sampledContent = {
                    Box(Modifier.fillMaxSize().background(glass.groupedBackground))
                },
                overlayContent = {
                    CompositionLocalProvider(LocalDs provides mapped) {
                        content()
                    }
                },
            )
        }
    }
}

/** 统一玻璃卡：直接用 Glasmio 的液态玻璃卡（真折射/高光，非自绘渐变）。 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    io.github.neoruaa.glasmio.ui.GlasmioCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}
