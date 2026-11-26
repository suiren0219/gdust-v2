package com.haooz.nexioui.window

/**
 * 屏幕物理圆角（px），与界面布局方向无关。
 *
 * 各平台自行测量后填入；读不到时各角为 0（表示不裁切）。
 */
data class ScreenCornerRadiiPx(
    val topLeft: Float = 0f,
    val topRight: Float = 0f,
    val bottomRight: Float = 0f,
    val bottomLeft: Float = 0f,
) {
    val maxRadius: Float get() = maxOf(topLeft, topRight, bottomRight, bottomLeft)

    fun scaled(scale: Float): ScreenCornerRadiiPx = ScreenCornerRadiiPx(
        topLeft * scale, topRight * scale, bottomRight * scale, bottomLeft * scale,
    )

    /**
     * 给每个角设下限（px）。屏幕圆角读不到（直角屏 / 模拟器返回 0）或缩放后过小时，
     * 用它兜底出一个最小圆角，避免裁切结果变成硬直角。
     */
    fun coerceAtLeast(minRadius: Float): ScreenCornerRadiiPx = ScreenCornerRadiiPx(
        maxOf(topLeft, minRadius),
        maxOf(topRight, minRadius),
        maxOf(bottomRight, minRadius),
        maxOf(bottomLeft, minRadius),
    )

    /** 各物理角从同一个起始圆角插值到自身。 */
    fun interpolateFrom(startRadius: Float, fraction: Float): ScreenCornerRadiiPx = ScreenCornerRadiiPx(
        startRadius + (topLeft - startRadius) * fraction,
        startRadius + (topRight - startRadius) * fraction,
        startRadius + (bottomRight - startRadius) * fraction,
        startRadius + (bottomLeft - startRadius) * fraction,
    )

    companion object {
        val Zero = ScreenCornerRadiiPx()

        fun uniform(radius: Float): ScreenCornerRadiiPx = ScreenCornerRadiiPx(
            radius, radius, radius, radius,
        )
    }
}
