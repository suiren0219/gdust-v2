// 派生自 compose-miuix-ui（Apache-2.0）。

package com.haooz.nexioui.anim

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import kotlin.math.PI

/**
 * 用「阻尼比 + 响应时长」描述弹簧，比直接给 stiffness 更直观。
 *
 * @param damping 阻尼比。1.0 为临界阻尼（不过冲），小于 1.0 会振荡，大于 1.0 过阻尼。
 * @param response 响应时长（秒），越小越快。
 */
fun <T> folmeSpring(
    damping: Float,
    response: Float,
): SpringSpec<T> {
    val stiffness = ((2.0 * PI / response) * (2.0 * PI / response)).toFloat()
    return spring(dampingRatio = damping, stiffness = stiffness)
}
