package com.haooz.nexioui.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** Android：直接读 `screenWidthDp`，与系统口径（分屏 / 折叠屏 / 多窗口）一致。 */
@Composable
actual fun isTabletWidth(): Boolean =
    LocalConfiguration.current.screenWidthDp >= 600
