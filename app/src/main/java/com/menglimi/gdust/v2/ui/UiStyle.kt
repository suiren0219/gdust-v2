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

/** 保留已存的 SOFT/LIQUID 值；两种材质共用 Nexio 页面和 Miuix 主题。 */
enum class UiStyle(val label: String, val desc: String) {
    SOFT("柔光玻璃", "轻柔磨砂 · 清晰易读"),
    LIQUID("液态玻璃", "通透折射 · 跟手高光"),
}

val LocalUiStyle = compositionLocalOf<MutableState<UiStyle>> { error("no UiStyle") }

/** 风格切换只改变材质参数，不销毁页面状态与导航树。 */
@Composable
fun AppTheme(styleState: MutableState<UiStyle>, monetEnabled: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalUiStyle provides styleState) {
        com.menglimi.gdust.v2.ui.nexio.NexioTheme(monetEnabled, content)
    }
}

/** 从本机存储恢复风格选择。 */
@Composable
fun rememberUiStyle(store: Store): MutableState<UiStyle> {
    val initial = runCatching { UiStyle.valueOf(store.uiStyle) }.getOrDefault(UiStyle.SOFT)
    return rememberSaveable { mutableStateOf(initial) }
}
