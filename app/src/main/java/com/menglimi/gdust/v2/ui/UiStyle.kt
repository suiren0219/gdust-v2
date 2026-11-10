package com.menglimi.gdust.v2.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.isSystemInDarkTheme
import com.menglimi.gdust.v2.data.Store

/** 两套 UI 风格：SOFT=HyperOS 柔光（softglass），LIQUID=iOS 27 液态玻璃（Glasmio）。 */
enum class UiStyle(val label: String, val desc: String) {
    SOFT("柔光玻璃", "HyperOS 风格 · 折射/方向光/磨砂 · 轻量"),
    LIQUID("液态玻璃", "iOS 27 风格 · 原生控件与动效 · 体积大"),
}

val LocalUiStyle = compositionLocalOf<MutableState<UiStyle>> { error("no UiStyle") }

/** 全局主题入口：按风格把状态注入，再分派到对应实现。 */
@Composable
fun AppTheme(styleState: MutableState<UiStyle>, monetEnabled: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalUiStyle provides styleState) {
        when (styleState.value) {
            UiStyle.SOFT -> SoftGlassAppTheme(dark = dark, monetEnabled = monetEnabled, content = content)
            UiStyle.LIQUID -> LiquidGlassAppTheme(dark = dark, monetEnabled = monetEnabled, content = content)
        }
    }
}

/** 从本机存储恢复风格选择。 */
@Composable
fun rememberUiStyle(store: Store): MutableState<UiStyle> {
    val initial = runCatching { UiStyle.valueOf(store.uiStyle) }.getOrDefault(UiStyle.SOFT)
    return rememberSaveable { mutableStateOf(initial) }
}
