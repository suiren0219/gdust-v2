package com.menglimi.dormpower.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.menglimi.dormpower.power.PowerUiState
import com.menglimi.ksu.ui.Appearance
import com.menglimi.ksu.ui.Glyph
import com.menglimi.ksu.ui.KsuScaffold
import com.menglimi.ksu.ui.NavItem
import com.menglimi.power.PowerQueryPreset
import com.menglimi.power.PowerRoom
import kotlinx.coroutines.delay

internal data class PowerActions(
    val refresh: () -> Unit,
    val cancelRefresh: () -> Unit,
    val reload: () -> Unit,
    val queryRooms: (String, Int) -> Unit,
    val cancelRooms: () -> Unit,
    val bind: (String, Int, PowerRoom) -> Unit,
    val clearError: () -> Unit,
    val clearSettingsError: () -> Unit,
    val preset: (PowerQueryPreset) -> Unit,
    val interval: (Int) -> Unit,
    val thresholds: (Double, Double) -> Unit,
    val unbind: () -> Unit,
)

@Composable
internal fun PowerRoot(
    tab: Int,
    onTab: (Int) -> Unit,
    appearance: Appearance,
    onAppearance: (Appearance) -> Unit,
    state: PowerUiState,
    actions: PowerActions,
    notificationsEnabled: Boolean,
    onNotifications: () -> Unit,
    onTestNotification: () -> Unit = {},
    notificationMessage: String? = null,
    schedulingError: String? = null,
    onRetryScheduling: () -> Unit = {},
    preview: Boolean = false,
) {
    var clockTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // 新快照到达时同步读时钟，避免读数晚于上一次 30 秒 tick 被误判为时间回退。
    val now = remember(clockTick, state) { System.currentTimeMillis() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { clockTick = System.currentTimeMillis(); delay(30_000) }
        }
    }
    val tabs = remember { listOf(NavItem("电量", Glyph.BOLT), NavItem("历史", Glyph.CHART), NavItem("设置", Glyph.SETTINGS)) }
    KsuScaffold(tab, onTab, tabs) { page ->
        when (page) {
            0 -> PowerScreen(state, actions, now, preview)
            1 -> PowerHistoryScreen(state, now, preview)
            else -> PowerSettingsScreen(appearance, onAppearance, state, actions, notificationsEnabled,
                onNotifications, onTestNotification, notificationMessage, schedulingError, onRetryScheduling, preview)
        }
    }
}
