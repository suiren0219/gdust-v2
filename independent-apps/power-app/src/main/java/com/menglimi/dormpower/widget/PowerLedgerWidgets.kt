package com.menglimi.dormpower.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.FontWeight
import com.menglimi.dormpower.MainActivity
import com.menglimi.dormpower.power.PowerLedger
import com.menglimi.dormpower.power.PowerLedgerState
import com.menglimi.dormpower.power.PowerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.YearMonth
import java.time.ZoneId

class PowerCalendarReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = PowerCalendarWidget() }
class PowerLedgerReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = PowerLedgerWidget() }

class PowerCalendarWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val now = System.currentTimeMillis()
        val snapshot = withContext(Dispatchers.IO) { PowerStore(context).snapshot() }
        val ledger = snapshot.ledger ?: PowerLedgerState()
        provideContent { LedgerWidgetBody(ledger, snapshot.config.zone, true, now) }
    }
}
class PowerLedgerWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val now = System.currentTimeMillis()
        val snapshot = withContext(Dispatchers.IO) { PowerStore(context).snapshot() }
        val ledger = snapshot.ledger ?: PowerLedgerState()
        provideContent { LedgerWidgetBody(ledger, snapshot.config.zone, false, now) }
    }
}

@Composable
internal fun LedgerWidgetBody(ledger: PowerLedgerState, zone: ZoneId, calendar: Boolean, now: Long = System.currentTimeMillis()) {
    val context = LocalContext.current
    val month = YearMonth.from(PowerLedger.date(now, zone))
    val entries = ledger.entries.filterNot { it.ignored }.filter { YearMonth.from(PowerLedger.date(it.time, zone)) == month }
    val recorded = entries.filterNot { it.detected && it.paidYuan == null }
    val pending = entries.filter { it.detected && it.paidYuan == null }
    val background = ColorProvider(Color(0xFFFFF8F2), Color(0xFF1A1613))
    val foreground = ColorProvider(Color(0xFF221A14), Color(0xFFF3EDE7))
    val accent = ColorProvider(Color(0xFFB4551F), Color(0xFFFFB68A))
    val sub = ColorProvider(Color(0xFF7A6A5F), Color(0xFFB8AAA0))
    Column(GlanceModifier.fillMaxSize().background(background).cornerRadius(24.dp).padding(14.dp)
        .clickable(actionStartActivity(Intent(context, MainActivity::class.java).putExtra("tab", 3)))) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("$month · 电费${if (calendar) "日历" else "账本"}", GlanceModifier.defaultWeight(), style = TextStyle(color = foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Box(GlanceModifier.size(44.dp).clickable(actionRunCallback<PowerWidgetRefreshAction>()), contentAlignment = Alignment.Center) {
                Text("刷新", style = TextStyle(color = accent, fontSize = 11.sp))
            }
        }
        Text("已记 ¥${PowerLedger.money(recorded.fold(BigDecimal.ZERO) { sum, e -> sum + PowerLedger.amount(e) })} · ${pending.size} 笔待核实",
            style = TextStyle(color = sub, fontSize = 11.sp), maxLines = 1)
        Spacer(GlanceModifier.height(if (calendar) 8.dp else 2.dp))
        if (calendar) {
            Column {
            Row(GlanceModifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, GlanceModifier.defaultWeight(), style = TextStyle(color = sub, fontSize = 11.sp)) }
            }
            val offset = month.atDay(1).dayOfWeek.value - 1
            repeat((offset + month.lengthOfMonth() + 6) / 7) { week ->
                Row(GlanceModifier.fillMaxWidth().height(25.dp)) {
                    repeat(7) { index ->
                        val day = week * 7 + index - offset + 1
                        val date = day.takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay)
                        val dailyEntries = entries.filter { date != null && PowerLedger.date(it.time, zone) == date }
                        val recharge = dailyEntries.isNotEmpty()
                        val mark = if (!recharge) "" else if (dailyEntries.any { it.detected && it.paidYuan == null }) "○" else "●"
                        Text(if (date == null) "" else "$day$mark", GlanceModifier.defaultWeight(),
                            style = TextStyle(color = if (recharge) accent else foreground, fontSize = 12.sp), maxLines = 1)
                    }
                }
            }
            }
            Text("● 已记  ○ 待核实 · 点按记账", style = TextStyle(color = sub, fontSize = 10.sp))
        } else {
            val spent = ledger.days.filter { it.date.startsWith(month.toString()) }.fold(BigDecimal.ZERO) { sum, day -> sum + day.yuan.toBigDecimal() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("≈ ¥${PowerLedger.money(spent)}", style = TextStyle(color = foreground, fontSize = 26.sp, fontWeight = FontWeight.Bold))
                Text(" 采样用电", style = TextStyle(color = sub, fontSize = 11.sp))
            }
            Text("非完整账单 · 点按记账", style = TextStyle(color = sub, fontSize = 10.sp), maxLines = 1)
        }
    }
}
