package com.haooz.nexioui.slider

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 滑块配色。
 *
 * @param activeTrack 已完成部分的轨道颜色。
 * @param inactiveTrack 未完成部分的轨道颜色。
 * @param thumb 滑块圆球颜色。
 * @param activeTick 已越过锚点的刻度颜色。
 * @param inactiveTick 未越过锚点的刻度颜色。
 * @param thumbShadow 圆球投影颜色。
 */
@Immutable
data class SliderColors(
    val activeTrack: Color,
    val inactiveTrack: Color,
    val thumb: Color,
    val activeTick: Color,
    val inactiveTick: Color,
    val thumbShadow: Color,
)
