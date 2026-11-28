package com.haooz.nexioui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * NexioUI 的文字样式。
 *
 * 与 ColorScheme 一样只保留控件实际用到的层级；应用可以整体替换。
 */
@Immutable
data class NexioTextStyles(
    val main: TextStyle = TextStyle(fontSize = 17.sp),
    val paragraph: TextStyle = TextStyle(fontSize = 17.sp, lineHeight = 1.2f.em),
    val body1: TextStyle = TextStyle(fontSize = 16.sp),
    val body2: TextStyle = TextStyle(fontSize = 14.sp),
    val button: TextStyle = TextStyle(fontSize = 17.sp),
    val footnote1: TextStyle = TextStyle(fontSize = 13.sp),
    val footnote2: TextStyle = TextStyle(fontSize = 11.sp),
    val headline1: TextStyle = TextStyle(fontSize = 17.sp),
    val headline2: TextStyle = TextStyle(fontSize = 16.sp),
    val subtitle: TextStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
    val title1: TextStyle = TextStyle(fontSize = 32.sp),
    val title2: TextStyle = TextStyle(fontSize = 24.sp),
    val title3: TextStyle = TextStyle(fontSize = 20.sp),
    val title4: TextStyle = TextStyle(fontSize = 18.sp),
)
