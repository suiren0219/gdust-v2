package com.haooz.nexioui.color

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.hsv
import kotlin.math.max
import kotlin.math.min

/**
 * HSV 表示，取值贴近人用习惯：
 * - [h] 色相，角度 `[0, 360]`
 * - [s] 饱和度，百分比 `[0, 100]`
 * - [v] 明度，百分比 `[0, 100]`
 */
data class Hsv(val h: Float, val s: Float, val v: Float) {
    fun toColor(alpha: Float = 1f): Color {
        val hue = (((h % 360f) + 360f) % 360f)
        val sN = (s / 100f).coerceIn(0f, 1f)
        val vN = (v / 100f).coerceIn(0f, 1f)
        return hsv(hue, sN, vN, alpha)
    }
}

/** sRGB（0..255）→ HSV：h 角度 0..360，s / v 归一化 0..1。 */
internal fun rgbToHsv(r: Int, g: Int, b: Int, out: FloatArray) {
    val rf = r / 255f
    val gf = g / 255f
    val bf = b / 255f

    val max = max(rf, max(gf, bf))
    val min = min(rf, min(gf, bf))
    val delta = max - min

    val hue = when {
        delta == 0f -> 0f
        max == rf -> (60f * ((gf - bf) / delta) + 360f) % 360f
        max == gf -> (60f * ((bf - rf) / delta) + 120f) % 360f
        else -> (60f * ((rf - gf) / delta) + 240f) % 360f
    }

    out[0] = hue.coerceIn(0f, 360f)
    out[1] = (if (max > 0f) delta / max else 0f).coerceIn(0f, 1f)
    out[2] = max.coerceIn(0f, 1f)
}

/** Compose 颜色（sRGB）→ [Hsv]。 */
fun Color.toHsv(): Hsv {
    val arr = FloatArray(3)
    rgbToHsv((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(), arr)
    return Hsv(
        h = arr[0],
        s = (arr[1] * 100f).coerceIn(0f, 100f),
        v = (arr[2] * 100f).coerceIn(0f, 100f),
    )
}
