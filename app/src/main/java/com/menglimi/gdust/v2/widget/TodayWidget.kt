package com.menglimi.gdust.v2.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
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
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 三个格式共用：数据来自 WidgetDataSource（本机缓存），刷新入口统一 updateAll。 */
abstract class BaseGdustWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    protected fun oddText(data: WidgetData): String =
        data.odd?.let { "%.0f°".format(it) } ?: "--"

    fun refreshAll(context: Context) {
        CoroutineScope(Dispatchers.Default).launch {
            TodayWidget().updateAll(context)
            NextClassWidget().updateAll(context)
            PowerWidget().updateAll(context)
        }
    }
}

/** 标准款 3x2：今天（剩余）/明天课程列表，最多 4 条。 */
class TodayWidget : BaseGdustWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataSource.load(context)
        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier.fillMaxSize().padding(10.dp).cornerRadius(16.dp)
                        .background(GlanceTheme.colors.widgetBackground),
                ) {
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            data.title(),
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
                            maxLines = 1,
                        )
                        Spacer(GlanceModifier.defaultWeight())
                        Text(
                            oddText(data),
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary),
                            maxLines = 1,
                        )
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    if (data.lessons.isEmpty()) {
                        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (data.scope == WidgetData.Scope.TOMORROW) "明天没有课程" else "暂无课程数据",
                                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
                            )
                        }
                    } else {
                        data.lessons.take(4).forEach { b ->
                            val start = com.menglimi.gdust.v2.ui.schedule.ScheduleTimes.start(b.startSection)
                            val ongoing = start.isNotEmpty() && data.scope != WidgetData.Scope.TOMORROW &&
                                !java.time.LocalTime.now().isBefore(java.time.LocalTime.parse(start))
                            Row(
                                modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)
                                    .background(GlanceTheme.colors.surface).cornerRadius(8.dp)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
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
                                    b.room,
                                    style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant),
                                    maxLines = 1,
                                )
                            }
                        }
                        if (data.lessons.size > 4) {
                            Text(
                                "还有 ${data.lessons.size - 4} 门课…",
                                style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 紧凑款 2x1：下一节课。 */
class NextClassWidget : BaseGdustWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataSource.load(context)
        val next = data.lessons.firstOrNull()
        val start = next?.let { com.menglimi.gdust.v2.ui.schedule.ScheduleTimes.start(it.startSection) }.orEmpty()
        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier.fillMaxSize().padding(10.dp).cornerRadius(16.dp)
                        .background(GlanceTheme.colors.widgetBackground),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (data.scope == WidgetData.Scope.TOMORROW) "下一节 · 明天" else "下一节",
                        style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        next?.name ?: "没有更多课啦",
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        if (next != null) "${start.ifEmpty { "${next.startSection}节" }} ${next.room}".trim() else oddText(data),
                        style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 电量款 2x2：剩余电量大字 + 房间名。 */
class PowerWidget : BaseGdustWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataSource.load(context)
        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier.fillMaxSize().padding(10.dp).cornerRadius(16.dp)
                        .background(GlanceTheme.colors.widgetBackground),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        data.roomName.ifEmpty { "宿舍电量" },
                        style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        data.odd?.let { "%.1f".format(it) } ?: "--",
                        style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary),
                        maxLines = 1,
                    )
                    Text(
                        "剩余电量（度）",
                        style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    )
                }
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

class NextClassWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextClassWidget()
}

class PowerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PowerWidget()
}
