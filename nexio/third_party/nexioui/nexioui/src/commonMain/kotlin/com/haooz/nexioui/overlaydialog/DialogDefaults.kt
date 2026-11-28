package com.haooz.nexioui.overlaydialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.theme.NexioTheme

/** [OverlayDialog] 的默认尺寸与配色。 */
object DialogDefaults {

    /** 内容最大宽度，避免平板上被拉满。 */
    val MaxWidth = 420.dp

    /** 弹窗外部留白。 */
    val outsideMargin = DpSize(12.dp, 12.dp)

    /** 弹窗内部留白。 */
    val insideMargin = DpSize(24.dp, 24.dp)

    /** 屏幕圆角，用于贴底时的下圆角收缩。 */
    val screenCornerRadius = 48.dp

    /** 标题颜色。 */
    @Composable
    fun titleColor(): Color = NexioTheme.colorScheme.onBackground

    /** 摘要颜色。 */
    @Composable
    fun summaryColor(): Color = NexioTheme.colorScheme.onSurfaceSecondary

    /** 弹窗底色。 */
    @Composable
    fun backgroundColor(): Color = NexioTheme.colorScheme.background

    /** 蒙层颜色。 */
    @Composable
    fun windowDimmingColor(): Color =
        if (NexioTheme.isDark) {
            Color.Black.copy(alpha = 0.6f)
        } else {
            Color.Black.copy(alpha = 0.3f)
        }

    /** 宽屏（约 expanded 断点）时居中显示并改用缩放动画。 */
    @Composable
    internal fun isLargeScreen(): Boolean {
        val windowInfo = LocalWindowInfo.current
        val density = LocalDensity.current
        val windowWidth = with(density) { windowInfo.containerSize.width.toDp() }
        val windowHeight = with(density) { windowInfo.containerSize.height.toDp() }
        return windowHeight >= 480.dp && windowWidth >= 840.dp
    }
}
