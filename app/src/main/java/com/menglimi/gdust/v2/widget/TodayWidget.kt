package com.menglimi.gdust.v2.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.menglimi.gdust.v2.data.CalendarInfo
import com.menglimi.gdust.v2.data.CourseBlock
import java.time.LocalDate
import java.time.LocalTime

/**
 * 今日小组件：2x2 起步，显示今日课程（最多 4 条）+ 剩余电量。
 * 数据全部来自本机缓存（gdust_cache/gdust_power），不打网络；App 打开或 Worker 巡检后刷新。
 */
class TodayWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 把本机数据快照写进 widget 状态（每次 provideGlance 重新读取，进程内缓存足够新鲜）
        val cache = context.getSharedPreferences("gdust_cache", Context.MODE_PRIVATE)
        val power = context.getSharedPreferences("gdust_power", Context.MODE_PRIVATE)
        val gson = Gson()
        val blocks = runCatching {
            gson.fromJson<List<CourseBlock>>(
                cache.getString("blocks", "[]"),
                object : TypeToken<List<CourseBlock>>() {}.type,
            )
        }.getOrDefault(emptyList())
        val cal = runCatching {
            gson.fromJson(cache.getString("calendar", null), CalendarInfo::class.java)
        }.getOrNull()
        val odd = power.getFloat("prev_odd", -1f).takeIf { it >= 0 }
        val termStart = cache.getString("term_start", null)?.takeIf { it.isNotBlank() }
            ?: cal?.startDate
        val week = termStart?.let {
            runCatching {
                (java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(it), LocalDate.now()) / 7 + 1).toInt()
            }.getOrNull() ?: cal?.week
        } ?: cal?.week ?: 1
        val day = LocalDate.now().dayOfWeek.value
        val today = blocks.filter { it.week == week && it.day == day }.sortedBy { it.startSection }
        val now = LocalTime.now()

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier.fillMaxSize().padding(10.dp).cornerRadius(16.dp),
                ) {
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "今日 · 第 $week 周",
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
                        )
                        Spacer(GlanceModifier.defaultWeight())
                        Text(
                            odd?.let { "%.0f°".format(it) } ?: "--",
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary),
                        )
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    if (today.isEmpty()) {
                        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "今天没有课程，好好休息～",
                                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
                            )
                        }
                    } else {
                        today.take(4).forEach { b ->
                            val start = com.menglimi.gdust.v2.ui.schedule.ScheduleTimes.start(b.startSection)
                            val end = com.menglimi.gdust.v2.ui.schedule.ScheduleTimes.end(b.endSection)
                            val ongoing = start.isNotEmpty() && end.isNotEmpty() &&
                                !now.isBefore(LocalTime.parse(start)) && !now.isAfter(LocalTime.parse(end))
                            Row(
                                modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)
                                    .background(GlanceTheme.colors.surface).cornerRadius(8.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    start.ifEmpty { "${b.startSection}节" },
                                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary),
                                    maxLines = 1,
                                )
                                Spacer(GlanceModifier.width(8.dp))
                                Text(
                                    b.name + if (ongoing) " · 上课中" else "",
                                    style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface),
                                    maxLines = 1,
                                )
                                Spacer(GlanceModifier.defaultWeight())
                                Text(
                                    b.room, style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant),
                                    maxLines = 1,
                                )
                            }
                        }
                        if (today.size > 4) {
                            Text(
                                "还有 ${today.size - 4} 门课…",
                                style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant),
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** 数据变化后调用：App 打开、同步完成、电量采样都触发一次刷新。 */
        fun refresh(context: Context) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                TodayWidget().updateAll(context)
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
