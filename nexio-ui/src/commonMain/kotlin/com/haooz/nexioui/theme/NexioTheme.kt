package com.haooz.nexioui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.haooz.nexioui.indication.NexioIndication
import com.haooz.nexioui.overscroll.NexioOverscrollFactory

/** 当前作用域的语义色板。 */
val LocalNexioColorScheme = staticCompositionLocalOf { NexioLightColorScheme }

/** 当前作用域的文字样式。 */
val LocalNexioTextStyles = staticCompositionLocalOf { NexioTextStyles() }

/** 当前作用域是否深色。 */
val LocalNexioDark = staticCompositionLocalOf { false }

/** 当前作用域是否启用动态取色。 */
val LocalNexioDynamicColor = staticCompositionLocalOf { false }

/**
 * 主题入口。
 *
 * ```kotlin
 * NexioTheme(isDark = true) {
 *     TextButton(text = "确定", onClick = {}, backdrop = backdrop)
 * }
 * ```
 */
@Composable
fun NexioTheme(
    isDark: Boolean = isSystemInDarkTheme(),
    colorScheme: NexioColorScheme = if (isDark) NexioDarkColorScheme else NexioLightColorScheme,
    textStyles: NexioTextStyles = NexioTextStyles(),
    isDynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // 按压反馈与 miuix 一致：整块淡色覆盖，而不是 Material 水波纹
    val indication = remember(colorScheme.onBackground) {
        NexioIndication(color = colorScheme.onBackground)
    }
    CompositionLocalProvider(
        LocalNexioColorScheme provides colorScheme,
        LocalNexioTextStyles provides textStyles,
        LocalNexioDark provides isDark,
        LocalNexioDynamicColor provides isDynamicColor,
        LocalIndication provides indication,
        // 未显式挂 overScroll* 的滚动容器沿用同一套弹簧回弹
        LocalOverscrollFactory provides NexioOverscrollFactory,
        content = content,
    )
}

/** 主题读取入口，用法与 Compose 的 MaterialTheme 一致。 */
object NexioTheme {

    val colorScheme: NexioColorScheme
        @Composable @ReadOnlyComposable
        get() = LocalNexioColorScheme.current

    val textStyles: NexioTextStyles
        @Composable @ReadOnlyComposable
        get() = LocalNexioTextStyles.current

    val isDark: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalNexioDark.current

    val isDynamicColor: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalNexioDynamicColor.current
}
