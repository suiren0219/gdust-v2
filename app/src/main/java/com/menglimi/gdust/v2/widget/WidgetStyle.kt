package com.menglimi.gdust.v2.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
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
 * 小部件画风 tokens：对齐 App 内 Nexio 卡片观感（大圆角、近实色卡底、细描边、单一强调色）。
 * 壁纸透过太多会显灰且文字对比不足，卡底改为 88–95% 不透明度的"实感玻璃"，
 * 只靠顶部高光与细描边暗示玻璃材质。注意：短名 [Color] 是 Compose 的，
 * Android graphics 色彩 API 走全限定名。
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
        if (dark) Color(0xFFB7BAC1) else Color(0xFF63666E)

    /** 强调色柔光底（节次胶囊 / 类型徽章 / 切换钮激活态）。 */
    fun accentSoft(dark: Boolean): Color =
        Color(accent(dark)).copy(alpha = if (dark) 0.26f else 0.15f)

    /** 卡底 bitmap：竖向实感渐变 + 顶部高光 + 1px 细描边。 */
    fun glassCard(context: Context, widthPx: Int, heightPx: Int, radiusPx: Float): Bitmap {
        val dark = isDark(context)
        val d = { dp: Float -> dp * context.resources.displayMetrics.density }
        val bmp = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rect = RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat())

        // 深色卡贴近 App 的 #1A1C22，浅色为白瓷底；不再依赖壁纸透出
        val baseTop = if (dark) android.graphics.Color.argb(242, 26, 28, 34) else android.graphics.Color.argb(247, 252, 253, 255)
        val baseBottom = if (dark) android.graphics.Color.argb(230, 16, 18, 24) else android.graphics.Color.argb(236, 243, 245, 248)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, heightPx.toFloat(), baseTop, baseBottom, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(rect, radiusPx, radiusPx, fill)

        // 顶部高光：贴着上缘的一条渐隐亮线
        val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, widthPx.toFloat(), 0f,
                intArrayOf(
                    android.graphics.Color.argb(0, 255, 255, 255),
                    android.graphics.Color.argb(if (dark) 64 else 90, 255, 255, 255),
                    android.graphics.Color.argb(0, 255, 255, 255),
                ),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP,
            )
            strokeWidth = d(1.4f)
        }
        val hlY = d(1.6f)
        canvas.drawLine(d(10f), hlY, widthPx - d(10f), hlY, highlight)

        // 细描边：内圈微亮做厚度，外圈压暗收边
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = d(1.2f)
        }
        stroke.color = if (dark) android.graphics.Color.argb(46, 255, 255, 255) else android.graphics.Color.argb(70, 255, 255, 255)
        val inset = stroke.strokeWidth / 2f
        val inner = RectF(rect).apply { inset(inset, inset) }
        canvas.drawRoundRect(inner, radiusPx - inset, radiusPx - inset, stroke)
        stroke.color = android.graphics.Color.argb(24, 0, 0, 0)
        val outer = RectF(rect).apply { inset(0.5f, 0.5f) }
        canvas.drawRoundRect(outer, radiusPx - 0.5f, radiusPx - 0.5f, stroke)
        return bmp
    }

    /**
     * 电量走势图：折线 + 渐变面积 + 末点 + 刻度（纵轴上下界、横轴起止时间、
     * 两条淡网格、预警红虚线）。左侧与底部留白给刻度文字。
     */
    fun trend(
        context: Context,
        samples: List<Store.Sample>,
        widthPx: Int,
        heightPx: Int,
        days: Int,
        warnTh: Float,
    ): Bitmap? {
        if (widthPx <= 0 || heightPx <= 0) return null
        val dark = isDark(context)
        val accent = accent(dark)
        val d = { dp: Float -> dp * context.resources.displayMetrics.density }
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val subText = if (dark) android.graphics.Color.argb(190, 183, 186, 193) else android.graphics.Color.argb(200, 99, 102, 110)

        val labelW = d(26f)
        val labelH = d(14f)
        val topPad = d(11f)
        val rightPad = d(4f)
        val plotLeft = labelW
        val plotRight = (widthPx - rightPad).coerceAtLeast(plotLeft + 1)
        val plotTop = topPad
        val plotBottom = (heightPx - labelH).coerceAtLeast(plotTop + 1)

        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) android.graphics.Color.argb(40, 255, 255, 255) else android.graphics.Color.argb(34, 0, 0, 0)
            strokeWidth = d(1f)
        }
        // 水平网格（1/3 与 2/3 处），横轴基线
        listOf(1f / 3, 2f / 3).forEach { f ->
            val y = plotTop + (plotBottom - plotTop) * f.toFloat()
            canvas.drawLine(plotLeft, y, plotRight, y, grid)
        }
        val baseline = Paint(grid).apply {
            color = if (dark) android.graphics.Color.argb(70, 255, 255, 255) else android.graphics.Color.argb(52, 0, 0, 0)
        }
        canvas.drawLine(plotLeft, plotBottom, plotRight, plotBottom, baseline)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subText
            textSize = d(9f)
        }
        if (samples.size < 2) {
            // 数据不足：只画基线 + 起止时间，等待采样积累
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("积累中", plotLeft + d(2f), plotTop + d(10f), textPaint)
            return bmp
        }

        val lo = samples.minOf { it.odd }
        val hi = samples.maxOf { it.odd }
        val span = (hi - lo).takeIf { it > 0.05 } ?: 1.0
        fun x(t: Long): Float {
            val first = samples.first().time
            val total = (samples.last().time - first).coerceAtLeast(1L).toFloat()
            return plotLeft + ((t - first).coerceAtLeast(0L) / total) * (plotRight - plotLeft)
        }
        fun y(v: Double) = plotTop + (1 - ((v - lo) / span)).toFloat() * (plotBottom - plotTop)

        // 面积渐变 + 折线
        val path = Path()
        samples.forEachIndexed { i, s ->
            if (i == 0) path.moveTo(x(s.time), y(s.odd)) else path.lineTo(x(s.time), y(s.odd))
        }
        val area = Path(path).apply {
            lineTo(x(samples.last().time), plotBottom)
            lineTo(plotLeft, plotBottom)
            close()
        }
        val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, plotTop, 0f, plotBottom,
                android.graphics.Color.argb(64, android.graphics.Color.red(accent), android.graphics.Color.green(accent), android.graphics.Color.blue(accent)),
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

        // 预警线（范围内才画）：红虚线 + 左侧红字
        if (warnTh.toDouble() > lo && warnTh.toDouble() < hi) {
            val wy = y(warnTh.toDouble())
            val warn = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = DANGER
                strokeWidth = d(1.2f)
                pathEffect = DashPathEffect(floatArrayOf(d(3f), d(3f)), 0f)
            }
            canvas.drawLine(plotLeft, wy, plotRight, wy, warn)
            textPaint.color = DANGER
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("%.0f".format(warnTh), d(1f), wy + d(3f), textPaint)
            textPaint.color = subText
        }

        // 纵轴刻度：顶部 hi、底部 lo（取整显示）
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("%.0f".format(kotlin.math.ceil(hi)), d(1f), plotTop + d(9f), textPaint)
        canvas.drawText("%.0f".format(kotlin.math.floor(lo)), d(1f), plotBottom - d(2f), textPaint)

        // 横轴：起止时间（覆盖 >48h 用 M/d，否则 HH:mm，由 Presentation 决定）
        val stats = WidgetPresentation.trendStats(samples, 0L, System.currentTimeMillis())
        if (stats != null) {
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(stats.startLabel, plotLeft, heightPx - d(2f), textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(stats.endLabel, plotRight, heightPx - d(2f), textPaint)
        }

        // 末点：白边圆点 + 右上数值
        val last = samples.last()
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        canvas.drawCircle(x(last.time), y(last.odd), d(3f), dot)
        dot.color = android.graphics.Color.WHITE
        dot.style = Paint.Style.STROKE
        dot.strokeWidth = d(1.4f)
        canvas.drawCircle(x(last.time), y(last.odd), d(3f), dot)
        val valuePaint = Paint(textPaint).apply {
            color = accent
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
        }
        val valueY = (y(last.odd) - d(5f)).coerceAtLeast(plotTop + d(9f))
        canvas.drawText("%.1f".format(last.odd), plotRight, valueY, valuePaint)
        return bmp
    }

    /** 勾选圆圈：未完成为描边圆环，完成为实底对勾（用于待办条子）。 */
    fun checkbox(context: Context, checked: Boolean, sizePx: Int): Bitmap? {
        if (sizePx <= 0) return null
        val dark = isDark(context)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val d = { dp: Float -> dp * context.resources.displayMetrics.density }
        val c = sizePx / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (checked) {
            paint.color = accent(dark)
            canvas.drawCircle(c, c, c - d(1f), paint)
            paint.color = android.graphics.Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = d(2f)
            paint.strokeCap = Paint.Cap.ROUND
            val s = sizePx * 0.30f
            val path = Path().apply {
                moveTo(c - s * 0.6f, c + s * 0.05f)
                lineTo(c - s * 0.1f, c + s * 0.5f)
                lineTo(c + s * 0.7f, c - s * 0.5f)
            }
            canvas.drawPath(path, paint)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = d(1.6f)
            paint.color = if (dark) android.graphics.Color.argb(150, 183, 186, 193) else android.graphics.Color.argb(150, 99, 102, 110)
            canvas.drawCircle(c, c, c - d(1.5f), paint)
        }
        return bmp
    }

    fun dp2px(context: Context, dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()
}
