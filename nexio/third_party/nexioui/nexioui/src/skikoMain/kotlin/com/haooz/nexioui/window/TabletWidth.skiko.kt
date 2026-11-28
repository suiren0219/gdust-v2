package com.haooz.nexioui.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * Skia（iOS / Desktop）：窗口像素宽度按密度换算成 dp，与 Android 的 `screenWidthDp >= 600` 等价。
 *
 * 读不到有效密度时保守返回 false，退回手机布局。
 */
@Composable
actual fun isTabletWidth(): Boolean {
    val widthPx = LocalWindowInfo.current.containerSize.width.toFloat()
    val density = LocalDensity.current.density
    val widthDp = if (density > 0f) widthPx / density else widthPx
    return widthDp >= 600f
}
