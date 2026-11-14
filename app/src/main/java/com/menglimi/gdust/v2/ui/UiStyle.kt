package com.menglimi.gdust.v2.ui

import androidx.compose.runtime.Composable

/** 应用只使用 CZeroX，旧备份中的外观偏好不再改变渲染。 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    CZeroTheme(content)
}
