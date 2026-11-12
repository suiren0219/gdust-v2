package com.menglimi.gdust.v2.ui.nexio

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 新版界面主题：直接采用 Nexio 的 Miuix 主题（深浅色跟随系统，可选莫奈取色）。
 * 与旧的双玻璃风格（softglass / Glasmio）并存：调用方自行决定包在哪一层。
 */
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
    MiuixTheme(controller = controller, content = content)
}
