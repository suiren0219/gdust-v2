package com.haooz.nexioui.numberpicker

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.theme.NexioTheme

/** [NumberPicker] 的默认参数与配色。 */
object NumberPickerDefaults {

    val ItemHeight = 45.dp

    /** 惯性摩擦系数：越大减速越猛、滑得越近。 */
    const val FlingFrictionMultiplier: Float = 1.0f

    /** 惯性结束速度阈值，单位「项/秒」：越大越早交给吸附动画收尾。 */
    const val FlingVelocityThreshold: Float = 3.4f

    /** 吸附动画阻尼比：1f 临界阻尼无回弹；0.6~0.7 轻微过冲后荡回。 */
    const val SnapDampingRatio: Float = 0.78f

    /** 吸附动画刚度：越大吸附越干脆，越小收尾越柔和。 */
    const val SnapStiffness: Float = 200f

    /** 吸附到最近一格的动画规格。 */
    val SnapSpring: SpringSpec<Float> = spring(
        dampingRatio = SnapDampingRatio,
        stiffness = SnapStiffness,
    )

    /**
     * 构造默认配色。
     *
     * @param selectedTextColor 选中项文字颜色。
     * @param unselectedTextColor 未选中项文字颜色。
     * @param disabledSelectedTextColor 禁用时选中项文字颜色。
     * @param disabledUnselectedTextColor 禁用时未选中项文字颜色。
     */
    @Composable
    fun colors(
        selectedTextColor: Color = NexioTheme.colorScheme.onSurface,
        unselectedTextColor: Color = NexioTheme.colorScheme.onSurfaceSecondary,
        // 滚轮文字画在弹窗/卡片表面上，禁用色取 surface 系的 disabledOnSurface
        disabledSelectedTextColor: Color = NexioTheme.colorScheme.disabledOnSurface,
        disabledUnselectedTextColor: Color = NexioTheme.colorScheme.disabledOnSurface,
    ): NumberPickerColors = remember(
        selectedTextColor,
        unselectedTextColor,
        disabledSelectedTextColor,
        disabledUnselectedTextColor,
    ) {
        NumberPickerColors(
            selectedTextColor = selectedTextColor,
            unselectedTextColor = unselectedTextColor,
            disabledSelectedTextColor = disabledSelectedTextColor,
            disabledUnselectedTextColor = disabledUnselectedTextColor,
        )
    }
}

/**
 * [NumberPicker] 的配色。
 *
 * @see NumberPickerDefaults.colors
 */
@Immutable
data class NumberPickerColors(
    private val selectedTextColor: Color,
    private val unselectedTextColor: Color,
    private val disabledSelectedTextColor: Color,
    private val disabledUnselectedTextColor: Color,
) {

    @Stable
    internal fun selectedTextColor(enabled: Boolean): Color =
        if (enabled) selectedTextColor else disabledSelectedTextColor

    @Stable
    internal fun unselectedTextColor(enabled: Boolean): Color =
        if (enabled) unselectedTextColor else disabledUnselectedTextColor
}
