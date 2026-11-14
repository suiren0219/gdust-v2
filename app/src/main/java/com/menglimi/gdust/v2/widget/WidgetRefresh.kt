package com.menglimi.gdust.v2.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 活跃 Glance session 更新时也需重新读取缓存，UI 在 provideContent 内观察此版本。 */
internal val WIDGET_REFRESH_VERSION = longPreferencesKey("gdust_widget_refresh")

object WidgetRefresher {
    private enum class Kind { SCHEDULE, POWER, TODO }
    private data class Provider(val receiver: Class<out GlanceAppWidgetReceiver>, val kind: Kind)

    private val providers = listOf(
        Provider(ScheduleWideReceiver::class.java, Kind.SCHEDULE),
        Provider(ScheduleMediumReceiver::class.java, Kind.SCHEDULE),
        Provider(ScheduleTallReceiver::class.java, Kind.SCHEDULE),
        Provider(PowerWideReceiver::class.java, Kind.POWER),
        Provider(PowerMediumReceiver::class.java, Kind.POWER),
        Provider(PowerTallReceiver::class.java, Kind.POWER),
        Provider(TodoWideReceiver::class.java, Kind.TODO),
        Provider(TodoMediumReceiver::class.java, Kind.TODO),
        Provider(TodoTallReceiver::class.java, Kind.TODO),
    )
    private val updateMutex = Mutex()
    private val requests = Channel<Context>(Channel.CONFLATED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // 应用数据写入可合并刷新；callback/Worker 则直接调用 suspend 入口并等待。
        scope.launch {
            for (context in requests) {
                try {
                    updateAll(context)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    Log.w("WidgetRefresher", "本机小部件刷新失败，等待下一次刷新")
                }
            }
        }
    }

    fun refreshAll(context: Context) {
        requests.trySend(context.applicationContext)
    }

    fun hasWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return providers.any { manager.getAppWidgetIds(ComponentName(context, it.receiver)).isNotEmpty() }
    }

    suspend fun updateAll(context: Context) = update(context, Kind.entries.toSet())
    suspend fun updatePower(context: Context) = update(context, setOf(Kind.POWER))
    suspend fun updateTodos(context: Context) = update(context, setOf(Kind.TODO))

    private suspend fun update(context: Context, kinds: Set<Kind>) = updateMutex.withLock {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val glanceManager = GlanceAppWidgetManager(app)
        val widgets = mutableMapOf<Kind, GlanceAppWidget>()
        var failure: Exception? = null
        // Glance 1.1.1 的 getGlanceIds 支持多个 receiver，但依赖内部登记；直接枚举固定
        // provider 可覆盖冷启动、升级后尚未登记的实例，也不会混入其他 App 的小部件。
        for (provider in providers.filter { it.kind in kinds }) {
            for (appWidgetId in manager.getAppWidgetIds(ComponentName(app, provider.receiver))) {
                try {
                    val glanceId = glanceManager.getGlanceIdBy(appWidgetId)
                    updateAppWidgetState(app, glanceId) { preferences ->
                        preferences[WIDGET_REFRESH_VERSION] = (preferences[WIDGET_REFRESH_VERSION] ?: 0L) + 1L
                    }
                    val widget = widgets.getOrPut(provider.kind) {
                        when (provider.kind) {
                            Kind.SCHEDULE -> ScheduleWidget()
                            Kind.POWER -> PowerWidget()
                            Kind.TODO -> TodoWidget()
                        }
                    }
                    widget.update(app, glanceId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Launcher 移除实例可与刷新并发；真实失败继续刷新其余款式后再反馈给调用方。
                    if (manager.getAppWidgetInfo(appWidgetId) != null && failure == null) failure = e
                }
            }
        }
        failure?.let { throw it }
        Unit
    }
}

/** 九个静态 provider 共用生命周期：每次检查全体实例，不能因某一款移除而停掉其他款。 */
abstract class GdustWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.ensureScheduled(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetRefreshScheduler.ensureScheduled(context)
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshScheduler.ensureScheduled(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetRefreshScheduler.ensureScheduled(context)
    }
}
