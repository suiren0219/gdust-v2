package com.haooz.nexioui.capsuleslider

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.theme.NexioTheme

/**
 * 胶囊滑块的唯一参数源。
 *
 * 分段控件、手机底栏、平板侧栏、反馈类型选择等共用这一份：数值、颜色、弹簧参数
 * 不允许在调用点另写。滑块的运动本身交给 [CapsuleSelector]。
 */
object CapsuleSlider {

    /** 滑块左右各内缩：宽度减 2×本值、位置补 1×本值，保持居中。 */
    val Inset = 0.5.dp

    /** 滑块上下各内缩。 */
    val VerticalInset = 8.dp

    /** 拖尾上限。 */
    val TrailMax = 20.dp

    /** 按压时四周各缩多少（收缩量 = 2 × 本值）。 */
    val PressShrink = 2.dp

    /** 按压缩放比。 */
    const val PressScale: Float = 78f / 56f

    /** 拖尾增益：`min(TrailGain × 单帧位移, 60px)`。 */
    const val TrailGain: Float = 4f

    /** 拖尾一阶低通系数：直接吃每帧 dx 会一直哆嗦。 */
    const val TrailSmoothing: Float = 0.55f

    /** 判定「按在滑块上」的半格宽比例：小于它算抓滑块，大于它算点格子。 */
    const val GrabHalfWidthRatio: Float = 0.55f

    /** 快甩阈值（dp/秒）。 */
    val FlingVelocityDpPerSecond = 420.dp

    /** 阻尼动画看成到位的阈值。 */
    const val DampedVisibilityThreshold: Float = 0.001f

    /** 滑块底色：浅色压 6% 黑、深色提 10% 白。 */
    fun color(isLightTheme: Boolean): Color =
        if (isLightTheme) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.1f)

    /** 按当前主题明暗取滑块底色（主题被强制深色时也正确）。 */
    @Composable
    fun color(): Color = color(!NexioTheme.isDark)

    /** 快甩补切：沿 [indexDirection] 至少前进一格，保留更远落点。 */
    fun flingTarget(
        startIndex: Int,
        positionalTarget: Int,
        indexDirection: Int,
        lastIndex: Int,
    ): Int {
        if (indexDirection == 0) return positionalTarget
        val flick = (startIndex + indexDirection).coerceIn(0, lastIndex)
        return if (indexDirection > 0) {
            maxOf(positionalTarget, flick)
        } else {
            minOf(positionalTarget, flick)
        }
    }
}
