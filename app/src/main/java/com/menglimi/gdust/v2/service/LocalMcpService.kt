package com.menglimi.gdust.v2.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.menglimi.gdust.v2.App
import com.menglimi.gdust.v2.BuildConfig
import com.menglimi.gdust.v2.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 用户显式开启的本机只读服务，停止时不自启、不重复调用 Android 生命周期。 */
class LocalMcpService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var server: LocalMcpServer? = null
    private var opening: Job? = null
    private var foregroundReady = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!BuildConfig.MCP_ENABLED) {
            LocalMcpRuntime.state.value = McpRuntimeState(error = "此构建未启用本机 MCP 服务")
            stopSelf()
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "本机 MCP 服务", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("本机 MCP 服务")
            .setContentText("仅监听 127.0.0.1 · 只读课表").setContentIntent(open).setOngoing(true).setSilent(true).build()
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(ID, notification)
            foregroundReady = true
        } catch (_: Exception) {
            runCatching { (application as App).repo.store.localMcpEnabled = false }
            LocalMcpRuntime.state.value = McpRuntimeState(error = "无法启动前台服务")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!BuildConfig.MCP_ENABLED) {
            LocalMcpRuntime.state.value = McpRuntimeState(error = "此构建未启用本机 MCP 服务")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!foregroundReady || server != null || opening?.isActive == true) return START_NOT_STICKY
        LocalMcpRuntime.state.value = McpRuntimeState(starting = true)
        opening = scope.launch {
            val store = (application as App).repo.store
            var opened: LocalMcpServer? = null
            try {
                if (!withContext(Dispatchers.IO) { store.localMcpEnabled }) { stopSelf(startId); return@launch }
                val tools = LocalMcpTools {
                    McpReadSnapshot(store.loadCalendar(), store.loadBlocks(), store.scheduleSource,
                        store.loadTodos(), store.termStartDate.ifBlank { store.loadCalendar()?.startDate.orEmpty() },
                        store.termCurrentWeek.takeIf { it > 0 } ?: store.loadCalendar()?.week ?: 1)
                }
                withContext(Dispatchers.IO) { opened = LocalMcpServer(BuildConfig.MCP_PORT, { store.localMcpToken }, tools) }
                ensureActive()
                if (!withContext(Dispatchers.IO) { store.localMcpEnabled }) { stopSelf(startId); return@launch }
                server = opened
                opened = null
                LocalMcpRuntime.state.value = McpRuntimeState(running = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                withContext(Dispatchers.IO) { runCatching { store.localMcpEnabled = false } }
                LocalMcpRuntime.state.value = McpRuntimeState(error = "本机 MCP 启动失败，端口可能被占用")
                stopSelf(startId)
            } finally {
                // 构造完成但尚未交给 Service 就被取消时，也必须释放监听器。
                opened?.close()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        foregroundReady = false
        scope.cancel()
        server?.close()
        server = null
        LocalMcpRuntime.state.value = LocalMcpRuntime.state.value.copy(running = false, starting = false)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "gdust_local_mcp"
        private const val ID = 0x4D43
        val url: String get() = "http://127.0.0.1:${BuildConfig.MCP_PORT}/mcp"
        fun start(context: Context) {
            if (!BuildConfig.MCP_ENABLED) {
                LocalMcpRuntime.state.value = McpRuntimeState(error = "此构建未启用本机 MCP 服务")
                return
            }
            try { context.startForegroundService(Intent(context, LocalMcpService::class.java)) }
            catch (_: Exception) {
                runCatching { (context.applicationContext as App).repo.store.localMcpEnabled = false }
                LocalMcpRuntime.state.value = McpRuntimeState(error = "系统拒绝启动本机服务，请从 App 内重试")
            }
        }
        fun stop(context: Context) { context.stopService(Intent(context, LocalMcpService::class.java)) }
    }
}

internal data class McpRuntimeState(val starting: Boolean = false, val running: Boolean = false, val error: String? = null)
internal object LocalMcpRuntime {
    val state = MutableStateFlow(McpRuntimeState())
    val status = state.asStateFlow()
}
