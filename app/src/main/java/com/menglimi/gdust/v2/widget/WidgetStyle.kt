package com.menglimi.gdust.v2.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.menglimi.gdust.v2.data.Store

/**
 * 小部件画风 tokens：对齐 App 内 Nexio / SoftGlass 卡片观感
 * （大圆角、半透明柔光底、细描边、单一强调色）。Glance 无法实时模糊壁纸，
 * 用「半透明渐变底 + 顶部扫光 + 细描边」的 bitmap 背景近似玻璃卡材质。
 * 注意：短名 [Color] 是 Compose 的，Android graphics 色彩 API 走全限定名。
 */
object WidgetStyle {

    /** 主卡圆角，与 App 内课表卡一致。 */
    val radius: Dp = 22.dp

    // ── 色板：跟随系统深浅色；强调色用 HyperOS 蓝，危险色对齐 App 的 ds.danger ──
    const val ACCENT_LIGHT = 0xFF3482FE.toInt()
    const val ACCENT_DARK = 0xFF7AB0FF.toInt()
    const val DANGER = 0xFFE5484D.toInt()

    fun isDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun accent(dark: Boolean): Int = if (dark) ACCENT_DARK else ACCENT_LIGHT

    fun text(dark: Boolean): Color = if (dark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    fun sub(dark: Boolean): Color =
        if (dark) Color.White.copy(alpha = 0.62f) else Color.Black.copy(alpha = 0.55f)

    /** 强调色柔光底（节次胶囊 / 类型徽章 / 切换钮激活态）。 */
    fun accentSoft(dark: Boolean): Color =
        Color(accent(dark)).copy(alpha = if (dark) 0.24f else 0.14f)

    /** 玻璃卡 bitmap：竖向柔光渐变 + 顶部扫光 + 1px 细描边，底色半透明可透壁纸。 */
    fun glassCard(context: Context, widthPx: Int, heightPx: Int, radiusPx: Float): Bitmap {
        val dark = isDark(context)
        val d = { dp: Float -> dp * context.resources.displayMetrics.density }
        val bmp = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rect = RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat())

        val baseTop = if (dark) android.graphics.Color.argb(30, 255, 255, 255) else android.graphics.Color.argb(108, 255, 255, 255)
        val baseBottom = if (dark) android.graphics.Color.argb(10, 255, 255, 255) else android.graphics.Color.argb(64, 255, 255, 255)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, heightPx.toFloat(), baseTop, baseBottom, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(rect, radiusPx, radiusPx, fill)

        // 顶部高光线：贴着上缘的一条渐隐亮线，模拟 softglass 的 highlightEdge
        val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, widthPx.toFloat(), 0f,
                intArrayOf(
                    android.graphics.Color.argb(0, 255, 255, 255),
                    android.graphics.Color.argb(110, 255, 255, 255),
                    android.graphics.Color.argb(0, 255, 255, 255),
                ),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP,
            )
            strokeWidth = d(1.4f)
        }
        val hlY = d(1.6f)
        canvas.drawLine(d(10f), hlY, widthPx - d(10f), hlY, highlight)

        // 细描边：内圈提亮做玻璃厚度，外圈压暗收边
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = d(1.2f)
        }
        stroke.color = if (dark) android.graphics.Color.argb(60, 255, 255, 255) else android.graphics.Color.argb(80, 255, 255, 255)
        val inset = stroke.strokeWidth / 2f
        val inner = RectF(rect).apply { inset(inset, inset) }
        canvas.drawRoundRect(inner, radiusPx - inset, radiusPx - inset, stroke)
        stroke.color = android.graphics.Color.argb(30, 0, 0, 0)
        val outer = RectF(rect).apply { inset(0.5f, 0.5f) }
        canvas.drawRoundRect(outer, radiusPx - 0.5f, radiusPx - 0.5f, stroke)
        return bmp
    }

    /** 电量走势线：折线 + 渐变面积 + 末点高亮 + 底部基线（数据线保持不变）。 */
    fun trend(context: Context, samples: List<Store.Sample>, widthPx: Int, heightPx: Int, days: Int): Bitmap? {
        if (widthPx <= 0 || heightPx <= 0) return null
        val dark = isDark(context)
        val accent = accent(dark)
        val d = { dp: Float -> dp * context.resources.displayMetrics.density }
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // 底部基线：数据线底衬
        val baseline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) android.graphics.Color.argb(56, 255, 255, 255) else android.graphics.Color.argb(38, 0, 0, 0)
            strokeWidth = d(1f)
        }
        val baseY = heightPx - d(1f)
        canvas.drawLine(0f, baseY, widthPx.toFloat(), baseY, baseline)

        if (samples.size < 2) return bmp

        val now = System.currentTimeMillis()
        val start = now - days.toLong() * 24 * 3600 * 1000
        val lo = samples.minOf { it.odd }
        val hi = samples.maxOf { it.odd }
        val span = (hi - lo).takeIf { it > 0.05 } ?: 1.0
        val pad = d(3f)
        val usableH = heightPx - pad * 2
        val total = (now - start).toFloat().coerceAtLeast(1f)
        fun x(t: Long) = ((t - start).coerceAtLeast(0L) / total) * widthPx
        fun y(v: Double) = pad + (1 - ((v - lo) / span)).toFloat() * usableH

        val path = Path()
        samples.forEachIndexed { i, s ->
            if (i == 0) path.moveTo(x(s.time), y(s.odd)) else path.lineTo(x(s.time), y(s.odd))
        }

        // 面积渐变填充：强调色向底部淡出
        val area = Path(path).apply {
            lineTo(widthPx.toFloat(), baseY)
            lineTo(x(samples.first().time), baseY)
            close()
        }
        val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, heightPx.toFloat(),
                android.graphics.Color.argb(70, android.graphics.Color.red(accent), android.graphics.Color.green(accent), android.graphics.Color.blue(accent)),
                android.graphics.Color.argb(0, android.graphics.Color.red(accent), android.graphics.Color.green(accent), android.graphics.Color.blue(accent)),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(area, areaPaint)

        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            strokeWidth = d(2.2f)
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(path, line)

        // 末点：白边圆点，突出最新电量
        val last = samples.last()
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        canvas.drawCircle(x(last.time), y(last.odd), d(3f), dot)
        dot.color = android.graphics.Color.WHITE
        dot.style = Paint.Style.STROKE
        dot.strokeWidth = d(1.4f)
        canvas.drawCircle(x(last.time), y(last.odd), d(3f), dot)
        return bmp
    }

    fun dp2px(context: Context, dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()
}
