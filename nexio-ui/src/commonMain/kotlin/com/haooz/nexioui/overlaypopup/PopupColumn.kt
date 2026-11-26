package com.haooz.nexioui.overlaypopup

import androidx.compose.runtime.Composable
import com.haooz.nexioui.dropdown.DropdownEntriesColumn

/**
 * 弹层内容列：宽度对齐到最宽子项，钳制在 200dp ~ 288dp，内容超高时纵向滚动。
 *
 * @param onFitsOnScreen 内容是否一屏装得下
 * @param content 弹层内容
 */
@Composable
fun PopupColumn(
    onFitsOnScreen: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    DropdownEntriesColumn(onFitsOnScreen = onFitsOnScreen, content = content)
}
