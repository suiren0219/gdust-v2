package com.menglimi.dormpower.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as TextColorProvider
import com.menglimi.dormpower.MainActivity
import com.menglimi.dormpower.power.PowerStore
import com.menglimi.power.PowerMonitorConfig
import com.menglimi.power.PowerQueryPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 宿舍电量小部件：只读本机加密快照，不在渲染阶段发网络请求。
 * 刷新按钮启动一次性 Worker；整卡点按打开 App。
 * 两种形态共用一套类，按可用宽度决定是否显示今日/近七日指标。
 */
class DormPowerWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { loadWidgetData(context) }
        provideContent { WidgetBody(data) }
    }
}

internal data class WidgetData(
    val bound: Boolean,
    val odd: Double?,
    val todayUse: Double?,
    val weekUse: Double?,
    val lowThreshold: Double,
    val updatedAt: Long,
    val stale: Boolean,
    val running: Boolean,
    val message: String?,
    val now: Long,
    val unitPrice: String = "0.6",
)

internal fun loadWidgetData(context: Context): WidgetData {
    val snapshot = runCatching { PowerStore(context).snapshot() }.getOrNull()
    val config = snapshot?.config ?: PowerMonitorConfig()
    val bound = config.binding?.isValid == true
    val reading = snapshot?.monitor?.reading?.takeIf { bound }
    val now = System.currentTimeMillis()
    val plan = PowerQueryPolicy.plan(
        config.preset, config.customIntervalMinutes, snapshot?.monitor?.samples.orEmpty(), now,
        config.effectiveLowThreshold, config.effectiveDropRateThreshold,
    )
    val refresh = PowerWidgetState.read(context)
    val weekTotal = reading?.detail?.weekUse?.sumOf { it.second }?.takeIf { it > 0 }
    return WidgetData(
        bound = bound,
        odd = reading?.detail?.odd,
        todayUse = reading?.detail?.todayUse,
        weekUse = weekTotal,
        lowThreshold = config.effectiveLowThreshold,
        updatedAt = reading?.updatedAt ?: 0L,
        stale = bound && widgetStale(reading?.updatedAt ?: 0L, now, plan.intervalMinutes),
        running = refresh.running,
        message = refresh.message,
        now = now,
        unitPrice = snapshot?.ledger?.unitPrice ?: "0.6",
    )
}

private fun textStyle(size: TextUnit, bold: Boolean, color: TextColorProvider) = TextStyle(
    fontSize = size,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    color = color,
)

private fun colors() = WidgetColors(
    card = ColorProvider(Color(0xFFFFF8F2), Color(0xFF1A1613)),
    text = ColorProvider(Color(0xFF221A14), Color(0xFFF3EDE7)),
    sub = ColorProvider(Color(0xFF7A6A5F), Color(0xFFB8AAA0)),
    accent = ColorProvider(Color(0xFFB4551F), Color(0xFFFFB68A)),
    accentSoft = ColorProvider(Color(0xFFF3E3D7), Color(0xFF34271F)),
    danger = ColorProvider(Color(0xFFB3261E), Color(0xFFFFB4AB)),
)

private data class WidgetColors(
    val card: TextColorProvider,
    val text: TextColorProvider,
    val sub: TextColorProvider,
    val accent: TextColorProvider,
    val accentSoft: TextColorProvider,
    val danger: TextColorProvider,
)

@Composable
private fun WidgetBody(data: WidgetData) {
    val wide = LocalSize.current.width >= 200.dp
    val context = LocalContext.current
    val colors = colors()
    val statusColor = if (data.odd != null && data.odd >= 0 && data.odd <= data.lowThreshold) colors.danger else colors.sub
    Column(
        GlanceModifier.fillMaxSize().background(colors.card).cornerRadius(24.dp).padding(14.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        verticalAlignment = Alignment.Top,
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("宿舍电量", style = textStyle(13.sp, true, colors.sub), maxLines = 1)
            Spacer(GlanceModifier.defaultWeight())
            RefreshChip(data, colors)
        }
        Spacer(GlanceModifier.height(if (wide) 10.dp else 6.dp))
        if (!data.bound) {
            Text("未绑定宿舍", style = textStyle(if (wide) 26.sp else 20.sp, true, colors.text), maxLines = 1)
            Text("点按打开 App，查询并选择房间", style = textStyle(12.sp, false, colors.sub), maxLines = 1)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(widgetBalanceText(data.odd), style = textStyle(if (wide) 40.sp else 30.sp, true, colors.text), maxLines = 1)
                Text(" 度", style = textStyle(14.sp, false, colors.sub), maxLines = 1)
            }
            if (wide) Text("余额折合 ≈¥${com.menglimi.dormpower.power.PowerLedger.yuan(data.odd, data.unitPrice)}",
                style = textStyle(11.sp, false, colors.sub), maxLines = 1)
            if (wide) {
                Spacer(GlanceModifier.height(4.dp))
                Row {
                    Text("今日 ${widgetUsageText(data.todayUse)} 度", style = textStyle(12.sp, false, colors.sub), maxLines = 1)
                    Spacer(GlanceModifier.width(14.dp))
                    Text("近七日 ${widgetUsageText(data.weekUse)} 度", style = textStyle(12.sp, false, colors.sub), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.height(4.dp))
            Text(
                statusLine(data),
                style = textStyle(12.sp, false, if (data.running || data.message != null) colors.accent else statusColor),
                maxLines = 1,
            )
        }
    }
}

private fun statusLine(data: WidgetData): String = when {
    data.running -> "刷新中…"
    !data.message.isNullOrBlank() -> data.message
    else -> "${widgetStatusText(data.odd, data.lowThreshold, data.stale)} · ${widgetAgeText(data.updatedAt, data.now)}"
}

@Composable
private fun RefreshChip(data: WidgetData, colors: WidgetColors) {
    Box(
        GlanceModifier.size(44.dp).background(colors.accentSoft).cornerRadius(22.dp)
            .clickable(actionRunCallback<PowerWidgetRefreshAction>()),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (data.running) "…" else "刷新", style = textStyle(11.sp, true, colors.accent), maxLines = 1)
    }
}
