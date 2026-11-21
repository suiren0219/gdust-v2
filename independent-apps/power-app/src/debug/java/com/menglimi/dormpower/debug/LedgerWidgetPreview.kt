package com.menglimi.dormpower.debug

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import com.menglimi.dormpower.power.*
import com.menglimi.dormpower.widget.LedgerWidgetBody
import java.time.ZoneId

/** 仅渲染生产布局，所有数据在内存中；拦截点击避免触发真实应用 PendingIntent。 */
@Composable
internal fun LedgerWidgetPreview(calendar: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val size = if (calendar) DpSize(320.dp, 320.dp) else DpSize(320.dp, 160.dp)
    var views by remember { mutableStateOf<RemoteViews?>(null) }
    LaunchedEffect(calendar) {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val ledger = PowerLedgerState(entries = listOf(
            PowerLedgerEntry(time = now, kwh = 49.0, detected = true),
            PowerLedgerEntry(time = now - 86_400_000, kwh = 100.0, paidYuan = "60.00")),
            days = listOf(PowerDayCost(PowerLedger.date(now, zone).toString(), 3.0, "1.8", 3, now - 3600_000, now)))
        val widget = object : GlanceAppWidget() {
            override suspend fun provideGlance(context: Context, id: GlanceId) {
                provideContent { LedgerWidgetBody(ledger, zone, calendar, now) }
            }
        }
        views = widget.compose(context = context, size = size)
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        views?.let { remote -> AndroidView(modifier = Modifier.size(size.width, size.height), factory = { host ->
            object : FrameLayout(host) {
                override fun onInterceptTouchEvent(event: MotionEvent): Boolean = true
                override fun onTouchEvent(event: MotionEvent): Boolean = true
            }.apply { addView(remote.apply(host, this)) }
        }) }
    }
}
