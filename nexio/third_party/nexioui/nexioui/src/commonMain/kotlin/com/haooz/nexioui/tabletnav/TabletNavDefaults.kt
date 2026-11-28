package com.haooz.nexioui.tabletnav

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** [TabletNavigation] 的尺寸常量。 */
object TabletNavDefaults {

    /** 展开态面板宽度占宿主宽度的比例。 */
    const val ExpandedWidthFraction = 0.22f

    /** 展开态面板最小宽度（不含左缘间距）。 */
    val ExpandedMinWidth = 200.dp

    /** 折叠态图标轨宽度。 */
    val RailWidth = 84.dp

    /** 面板与宿主左缘的间距，展开 / 折叠保持不变。 */
    val SideInset = 12.dp

    /** 面板与宿主底缘的间距。 */
    val BottomInset = 12.dp

    /** 面板内边距。 */
    val PanelPadding = 8.dp

    /** 侧栏条目行高。 */
    val ItemHeight = 52.dp

    /** 侧栏条目行距。 */
    val ItemGap = 4.dp

    /** 图标尺寸。 */
    val IconSize = 26.dp

    /** 图标中心相对面板左缘的位置。 */
    val IconCenterX = 42.dp

    /** 图标与文字的间距。 */
    val LabelGap = 12.dp

    /** 条目字号。 */
    val LabelFontSize = 16.sp

    /** 分组标题字号。 */
    val SectionTitleFontSize = 13.4.sp

    /** 顶栏胶囊高度。 */
    val TopBarHeight = 48.dp

    /** 胶囊左右内边距。 */
    val TopBarPad = 4.dp

    /** 胶囊最左侧圆钮宽度。 */
    val TopBarButtonWidth = 48.dp

    /** 胶囊内每格页签宽度。 */
    val TopBarTabWidth = 78.dp

    /** 顶栏图标尺寸。 */
    val TopBarIconSize = 26.dp

    /** 顶栏文字字号。 */
    val TopBarLabelFontSize = 16.sp

    /** 取不到屏幕圆角时的面板圆角基准。 */
    val FallbackCornerRadius = 28.dp

    /** 面板圆角相对屏幕圆角的收缩量。 */
    val CornerInset = 10.dp

    /** 顶栏 → 回侧栏：等面板长到底再弹的延迟。 */
    const val LandingDelayMillis = 220L
}
