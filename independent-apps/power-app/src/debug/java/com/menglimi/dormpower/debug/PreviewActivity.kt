package com.menglimi.dormpower.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.menglimi.dormpower.power.PowerUiState
import com.menglimi.dormpower.ui.PowerActions
import com.menglimi.dormpower.ui.PowerRoot
import com.menglimi.ksu.ui.Appearance
import com.menglimi.ksu.ui.KsuTheme
import com.menglimi.ksu.ui.ThemeMode
import com.menglimi.power.PowerBinding
import com.menglimi.power.PowerDetail
import com.menglimi.power.PowerMonitorConfig
import com.menglimi.power.PowerReading
import com.menglimi.power.PowerRoom
import com.menglimi.power.PowerSample
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 仅 debug 且在 :preview 进程。无 Controller/Store/Api/WorkManager，所有操作均是合成内存演示。 */
class PreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialTab = when (intent.getStringExtra("screen")) { "history" -> 1; "settings" -> 2; "ledger" -> 3; else -> 0 }
        val scenario = intent.getStringExtra("state").orEmpty()
        val now = System.currentTimeMillis()
        if (intent.getStringExtra("screen") in listOf("widget-calendar", "widget-ledger")) {
            setContent { LedgerWidgetPreview(intent.getStringExtra("screen") == "widget-calendar") }
            return
        }
        setContent {
            var tab by remember { mutableIntStateOf(initialTab) }
            var appearance by remember { mutableStateOf(Appearance(
                mode = if (intent.getBooleanExtra("dark", false)) ThemeMode.DARK else ThemeMode.LIGHT,
                dynamic = false, amoled = intent.getBooleanExtra("amoled", false))) }
            var power by remember { mutableStateOf(syntheticState(now, scenario)) }
            var roomJob by remember { mutableStateOf<Job?>(null) }
            var refreshJob by remember { mutableStateOf<Job?>(null) }
            var notifications by remember { mutableStateOf(false) }
            var notificationMessage by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()
            val cancelRooms: () -> Unit = {
                roomJob?.cancel(); roomJob = null
                power = power.copy(roomBusy = false, rooms = emptyList(), roomQueryAccount = "", roomQueryCode = 0, roomError = null)
            }
            val actions = PowerActions(
                refresh = {
                    refreshJob?.cancel()
                    power = power.copy(busy = true, error = null, message = null)
                    refreshJob = scope.launch {
                        delay(800)
                        power = if (scenario == "error") power.copy(busy = false, error = "合成预览：网络不可用，已保留上次成功数据。")
                        else {
                            val refreshedAt = System.currentTimeMillis()
                            power.copy(busy = false, reading = power.reading?.copy(updatedAt = refreshedAt),
                                lastSampleAt = refreshedAt, lastAttemptAt = refreshedAt,
                                message = "合成预览：刷新完成，未发起网络请求")
                        }
                    }
                },
                cancelRefresh = { refreshJob?.cancel(); power = power.copy(busy = false, message = "合成预览：刷新已取消") },
                reload = { power = power.copy(error = null, localLoading = false) },
                queryRooms = { account, code ->
                    cancelRooms()
                    power = power.copy(roomBusy = true)
                    roomJob = scope.launch {
                        delay(650)
                        val rooms = if (scenario == "empty" || account == "empty") emptyList() else listOf(
                            PowerRoom("合成房间 · 6 栋 408", "synthetic-a"), PowerRoom("合成房间 · 8 栋 302", "synthetic-b"))
                        power = power.copy(roomBusy = false, rooms = rooms, roomQueryAccount = account, roomQueryCode = code,
                            roomError = if (rooms.isEmpty()) "合成预览：未查到可绑定的房间，请检查账号与学校代码。" else null)
                    }
                },
                cancelRooms = cancelRooms,
                bind = { account, code, room ->
                    val valid = power.roomQueryAccount == account && power.roomQueryCode == code && room in power.rooms
                    if (valid) {
                        cancelRooms()
                        power = PowerUiState(config = power.config.copy(binding = PowerBinding(account, code, room.verify)),
                            roomName = room.name, bindingRevision = power.bindingRevision + 1,
                            message = "合成预览：绑定仅保存在内存中，暂无真实读数")
                    }
                },
                clearError = { power = power.copy(error = null) },
                clearSettingsError = { power = power.copy(settingsError = null) },
                preset = { power = power.copy(config = power.config.copy(preset = it)) },
                interval = { power = power.copy(config = power.config.copy(customIntervalMinutes = it)) },
                thresholds = { low, drop -> power = power.copy(config = power.config.copy(lowThreshold = low, dropRateThreshold = drop)) },
                price = { value, _ -> power = power.copy(ledger = power.ledger.copy(unitPrice = value), ledgerSavedVersion = power.ledgerSavedVersion + 1) },
                saveEntry = { entry, _ -> power = power.copy(ledger = power.ledger.copy(entries = power.ledger.entries.filterNot { it.id == entry.id } + entry),
                    ledgerSavedVersion = power.ledgerSavedVersion + 1) },
                removeEntry = { id, _ -> power = power.copy(ledger = power.ledger.copy(entries = power.ledger.entries.filterNot { it.id == id })) },
                unbind = {
                    cancelRooms(); refreshJob?.cancel()
                    power = PowerUiState(config = power.config.copy(binding = null), bindingRevision = power.bindingRevision + 1,
                        message = "合成预览：已清除当前内存绑定")
                },
            )
            KsuTheme(appearance) {
                PowerRoot(tab, { tab = it }, appearance, { appearance = it }, power, actions, notifications,
                    onNotifications = {
                        notifications = !notifications
                        notificationMessage = "合成预览：仅演示通知开关，不申请权限、不发送通知"
                    },
                    onTestNotification = { notificationMessage = "合成预览：仅演示测试通知，不访问系统通知" },
                    notificationMessage = notificationMessage, preview = true)
            }
        }
    }
}

private fun syntheticState(now: Long, scenario: String): PowerUiState {
    if (scenario == "unbound" || scenario == "empty") return PowerUiState()
    val updated = if (scenario == "stale") now - 2 * 86_400_000L else now - 600_000L
    val remaining = when (scenario) { "low" -> 8.6; "zero" -> 0.0; "debt" -> -1.2; else -> 42.6 }
    val samples = (0..28).map { index ->
        val balance = if (index < 8) 30.0 - index * 0.6 else remaining + (28 - index) * 0.65
        PowerSample(updated - (28 - index) * 6 * 3600_000L, balance)
    }
    return PowerUiState(
        config = PowerMonitorConfig(binding = PowerBinding("synthetic-account", 1000145, "synthetic-room")),
        roomName = "合成宿舍 · 6 栋 408", bindingRevision = 1,
        reading = if (scenario == "unknown") null else PowerReading(PowerDetail(remaining,
            if (scenario == "partial") null else 2.3,
            if (scenario == "partial") emptyList() else listOf("周一" to 2.7, "周二" to 2.4, "周三" to 3.1,
                "周四" to 2.8, "周五" to 2.5, "周六" to 3.0, "周日" to 2.3),
            if (scenario == "partial") null else 78.4, 200.0), updated),
        ledger = com.menglimi.dormpower.power.PowerLedgerState(entries = listOf(
            com.menglimi.dormpower.power.PowerLedgerEntry(time = now - 86_400_000, kwh = 100.0, paidYuan = "60.00", payer = "室友甲", note = "合成预览 · 公共电费"),
            com.menglimi.dormpower.power.PowerLedgerEntry(time = now, kwh = 49.0, detected = true)),
            days = listOf(com.menglimi.dormpower.power.PowerDayCost(java.time.LocalDate.now().toString(), 3.0, "1.8", 3, now - 3600_000, now))),
        samples = if (scenario == "unknown") emptyList() else samples,
        lastSampleAt = updated, lastAttemptAt = updated,
        busy = scenario == "loading",
        error = if (scenario == "error") "合成预览：刷新失败，保留上次读数。请检查网络后重试。" else null,
    )
}
