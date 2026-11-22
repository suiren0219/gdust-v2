package com.menglimi.gdust.v2.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import com.menglimi.gdust.v2.data.Store

/**
 * 小部件画风 tokens：CZeroX 实底卡语言（纯色卡面、无描边、无阴影）。
 * 卡片通过页面底与卡面的明度差分层；[glassCard] 保留旧函数名以兼容调用，
 * 实现使用纯色 bitmap，不再包含玻璃渐变、顶部扫光或描边。
 */
object WidgetStyle {

    /** 主卡圆角，与 CZeroX 卡片统一为 24dp。 */
    val radius: Dp = 24.dp

    /** 以 sp / dp 存储，布局预算与实际渲染共用，放大字体时减内容而不缩小正文。 */
    object Type {
        const val title = 16f
        const val headline = 18f
        const val body = 14f
        const val label = 13f
        const val caption = 12f
        const val number = 36f
        const val compactNumber = 32f
    }

    object Space {
        const val card = 16f
        const val compactCard = 12f
        const val narrowCard = 6f
        const val gap = 8f
        const val smallGap = 4f
        const val row = 34f
    }

    // ── CZeroX 色板：强调色沿用 widget 既有浅/深色蓝，危险色按规范统一 ──
    const val BACKGROUND_LIGHT = 0xFFF7F7F7.toInt()
    const val CARD_LIGHT = 0xFFFFFFFF.toInt()
    const val TEXT_LIGHT = 0xFF000000.toInt()
    const val SUB_LIGHT = 0xFF666666.toInt()
    const val BACKGROUND_DARK = 0xFF0E0E10.toInt()
    const val CARD_DARK = 0xFF1C1C1E.toInt()
    const val TEXT_DARK = 0xFFF2F2F7.toInt()
    const val ACCENT_LIGHT = 0xFF3482FF.toInt()
    const val ACCENT_DARK = 0xFF7AB0FF.toInt()
    const val DANGER = 0xFFEE4444.toInt()
    const val DANGER_DARK = 0xFFFF6B6B.toInt()

    fun isDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun accent(dark: Boolean): Int = if (dark) ACCENT_DARK else ACCENT_LIGHT
    fun danger(dark: Boolean): Int = if (dark) DANGER_DARK else DANGER
    fun card(dark: Boolean): Int = if (dark) CARD_DARK else CARD_LIGHT

    // 基准蓝/红继续画图表；小字用在卡面和柔和徽章底上均达到 4.5:1 的派生色。
    private val accentTextLight = readableForeground(ACCENT_LIGHT, CARD_LIGHT, blend(ACCENT_LIGHT, CARD_LIGHT, 0.14f))
    private val accentTextDark = readableForeground(ACCENT_DARK, CARD_DARK, blend(ACCENT_DARK, CARD_DARK, 0.24f))
    private val dangerTextLight = readableForeground(DANGER, CARD_LIGHT, blend(DANGER, CARD_LIGHT, 0.12f))
    private val dangerTextDark = readableForeground(DANGER_DARK, CARD_DARK, blend(DANGER_DARK, CARD_DARK, 0.12f))

    fun accentText(dark: Boolean): Int = if (dark) accentTextDark else accentTextLight
    fun dangerText(dark: Boolean): Int = if (dark) dangerTextDark else dangerTextLight

    internal fun blend(foreground: Int, background: Int, alpha: Float): Int {
        fun channel(shift: Int): Int = (((foreground ushr shift and 255) * alpha +
            (background ushr shift and 255) * (1f - alpha)) + 0.5f).toInt()
        return (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    internal fun contrastRatio(foreground: Int, background: Int): Double {
        fun luminance(color: Int): Double {
            fun linear(shift: Int): Double {
                val value = (color ushr shift and 255) / 255.0
                return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)
        }
        val a = luminance(foreground)
        val b = luminance(background)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun readableForeground(base: Int, vararg backgrounds: Int): Int {
        val target = if (contrastRatio(TEXT_LIGHT, backgrounds.first()) >= 4.5) TEXT_LIGHT else 0xFFFFFFFF.toInt()
        return (0..100).asSequence().map { blend(target, base, it / 100f) }
            .first { color -> backgrounds.all { contrastRatio(color, it) >= 4.5 } }
    }

    fun text(dark: Boolean): Color = if (dark) Color(TEXT_DARK) else Color(TEXT_LIGHT)
    fun sub(dark: Boolean): Color =
        if (dark) Color.White.copy(alpha = 0.62f) else Color(SUB_LIGHT)

    /** 强调色柔和底（节次胶囊 / 类型徽章 / 切换钮激活态）。 */
    fun accentSoft(dark: Boolean): Color =
        Color(accent(dark)).copy(alpha = if (dark) 0.24f else 0.14f)

    /** 卡底 bitmap：兼容旧调用名，但始终绘制纯色实底圆角卡。 */
    fun glassCard(context: Context, widthPx: Int, heightPx: Int, radiusPx: Float): Bitmap {
        val cardColor = if (isDark(context)) CARD_DARK else CARD_LIGHT
        val bmp = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardColor }
        canvas.drawRoundRect(
            0f,
            0f,
            widthPx.toFloat(),
            heightPx.toFloat(),
            radiusPx,
            radiusPx,
            paint,
        )
        return bmp
    }

    /**
     * 电量走势图：折线 + 纯色面积 + 末点 + 刻度（纵轴上下界、横轴起止时间、
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
        val danger = danger(dark)
        val d = { dp: Float -> dp * context.resources.displayMetrics.density }
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val subText = sub(dark).toArgb()

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subText
            textSize = Type.caption * context.resources.displayMetrics.scaledDensity
        }
        val metrics = textPaint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        val lo = samples.minOfOrNull { it.odd } ?: 0.0
        val hi = samples.maxOfOrNull { it.odd } ?: 0.0
        val loLabel = "%.0f".format(kotlin.math.floor(lo))
        val hiLabel = "%.0f".format(kotlin.math.ceil(hi))
        // 留白按真正的文字宽高计算，字体放大与四位数刻度不会撞到绘图区。
        val plotLeft = maxOf(textPaint.measureText(loLabel), textPaint.measureText(hiLabel)) + d(8f)
        val plotRight = widthPx - d(5f)
        val plotTop = textHeight / 2f + d(2f)
        val plotBottom = heightPx - textHeight - d(7f)
        if (plotRight - plotLeft < d(32f) || plotBottom - plotTop < d(16f)) return null

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

        if (samples.size < 2) {
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("积累中", plotLeft, plotTop - metrics.ascent, textPaint)
            return bmp
        }

        val span = (hi - lo).takeIf { it > 0.05 } ?: 1.0
        fun x(t: Long): Float {
            val first = samples.first().time
            val total = (samples.last().time - first).coerceAtLeast(1L).toFloat()
            return plotLeft + ((t - first).coerceAtLeast(0L) / total) * (plotRight - plotLeft)
        }
        fun y(v: Double) = plotTop + (1 - ((v - lo) / span)).toFloat() * (plotBottom - plotTop)

        // 均匀填充面积 + 折线
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
            color = accentSoft(dark).toArgb()
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
                color = danger
                strokeWidth = d(1.2f)
                pathEffect = DashPathEffect(floatArrayOf(d(3f), d(3f)), 0f)
            }
            canvas.drawLine(plotLeft, wy, plotRight, wy, warn)
            // 太接近上下界时只画警戒线，避免三组刻度叠在一起。
            if (wy - plotTop > textHeight && plotBottom - wy > textHeight) {
                textPaint.color = dangerText(dark)
                textPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText("%.0f".format(warnTh), plotLeft - d(6f), wy - (metrics.ascent + metrics.descent) / 2, textPaint)
                textPaint.color = subText
            }
        }

        // 纵轴刻度：顶部 hi、底部 lo（取整显示）
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(hiLabel, plotLeft - d(6f), plotTop - (metrics.ascent + metrics.descent) / 2, textPaint)
        canvas.drawText(loLabel, plotLeft - d(6f), plotBottom, textPaint)

        // 横轴：起止时间（覆盖 >48h 用 M/d，否则 HH:mm，由 Presentation 决定）
        val stats = WidgetPresentation.trendStats(samples, 0L, System.currentTimeMillis())
        if (stats != null) {
            val baselineY = heightPx - metrics.descent - d(1f)
            if (textPaint.measureText(stats.startLabel) + textPaint.measureText(stats.endLabel) + d(8f) <= plotRight - plotLeft) {
                textPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(stats.startLabel, plotLeft, baselineY, textPaint)
            }
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(stats.endLabel, plotRight, baselineY, textPaint)
        }

        // 末点：浅色卡用深色轮廓、深色卡用白色轮廓，保持与底色的对比
        val last = samples.last()
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        canvas.drawCircle(x(last.time), y(last.odd), d(3f), dot)
        dot.color = if (dark) android.graphics.Color.WHITE else TEXT_LIGHT
        dot.style = Paint.Style.STROKE
        dot.strokeWidth = d(1.4f)
        canvas.drawCircle(x(last.time), y(last.odd), d(3f), dot)
        if (plotBottom - plotTop >= textHeight * 2) {
            val valuePaint = Paint(textPaint).apply {
                color = accentText(dark)
                isFakeBoldText = true
                textAlign = Paint.Align.RIGHT
            }
            val valueY = (y(last.odd) - d(6f)).coerceAtLeast(plotTop - metrics.ascent)
            canvas.drawText("%.1f".format(last.odd), plotRight, valueY, valuePaint)
        }
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
            paint.color = sub(dark).toArgb()
            canvas.drawCircle(c, c, c - d(1.5f), paint)
        }
        return bmp
    }

    fun dp2px(context: Context, dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()
}
