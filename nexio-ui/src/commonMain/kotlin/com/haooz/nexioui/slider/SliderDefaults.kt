package com.haooz.nexioui.slider

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.theme.NexioTheme

/** 滑块触感策略。 */
enum class SliderHapticEffect {
    /** 无触感。 */
    None,

    /** 仅在 0% 与 100% 触发。 */
    Edge,

    /** 在等分档位 / 锚点处触发。 */
    Step,
}

/** [Slider] 的默认参数与配色。 */
object SliderDefaults {

    val MinHeight = 48.dp

    val DefaultHapticEffect = SliderHapticEffect.Edge

    /** 默认胶囊轨道。 */
    val TrackShape: Shape = SliderTrackShape

    /**
     * 构造默认配色。
     *
     * @param enabled 是否启用；禁用时取主题的 disabled 系列颜色。
     */
    @Composable
    fun colors(enabled: Boolean = true): SliderColors {
        val scheme = NexioTheme.colorScheme
        val dark = NexioTheme.isDark
        return SliderColors(
            activeTrack = if (enabled) scheme.primary else scheme.disabledPrimarySlider,
            inactiveTrack = if (enabled) scheme.sliderBackground else scheme.disabledSecondary,
            thumb = if (enabled) scheme.onPrimary else scheme.disabledOnPrimary,
            activeTick = scheme.sliderKeyPointForeground,
            inactiveTick = scheme.sliderKeyPoint,
            thumbShadow = Color(if (dark) 0x40000000 else 0x1a000000),
        )
    }
}
