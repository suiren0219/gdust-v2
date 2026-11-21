package com.menglimi.dormpower

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.menglimi.dormpower.power.PowerController
import com.menglimi.dormpower.power.PowerNotifications
import com.menglimi.dormpower.power.PowerScheduling
import com.menglimi.dormpower.power.PowerStore
import com.menglimi.dormpower.ui.PowerActions
import com.menglimi.dormpower.ui.PowerRoot
import com.menglimi.ksu.ui.AppearanceStore
import com.menglimi.ksu.ui.KsuTheme
import com.menglimi.power.PowerApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class PowerViewModel(application: Application) : AndroidViewModel(application) {
    val appearance = AppearanceStore(application)
    val notifications = PowerNotifications(application)
    private val api by lazy { PowerApi() }
    val controller = PowerController(PowerStore(application),
        source = { binding -> api.detail(binding) }, roomsSource = { account, code -> api.rooms(account, code) },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        onSchedule = { PowerScheduling.ensure(application) }, onWarning = notifications::send,
        onUnbound = notifications::clear)
    private val mutableTab = MutableStateFlow(0)
    val tab = mutableTab.asStateFlow()
    fun selectTab(value: Int) { mutableTab.value = value.coerceIn(0, 3) }
    val actions = PowerActions(
        refresh = { controller.refresh() }, cancelRefresh = { controller.cancelRefresh() },
        reload = { controller.reload() }, queryRooms = { account, code -> controller.queryRooms(account, code) },
        cancelRooms = { controller.cancelRooms() }, bind = { account, code, room -> controller.bind(account, code, room) },
        clearError = controller::clearError, clearSettingsError = controller::clearSettingsError,
        preset = { controller.setPreset(it) }, interval = { controller.setInterval(it) },
        thresholds = { low, drop -> controller.setThresholds(low, drop) }, unbind = { controller.unbind() },
        price = { value, revision -> controller.setPrice(value, revision) },
        saveEntry = { entry, revision -> controller.saveEntry(entry, revision) },
        removeEntry = { id, revision -> controller.removeEntry(id, revision) },
    )
    override fun onCleared() { controller.close() }
}

class MainActivity : ComponentActivity() {
    private val model: PowerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent.hasExtra("tab")) model.selectTab(intent.getIntExtra("tab", 0))
        setContent {
            val state by model.controller.state.collectAsStateWithLifecycle()
            val appearance by model.appearance.state.collectAsStateWithLifecycle()
            val tab by model.tab.collectAsStateWithLifecycle()
            val schedulingError by PowerScheduling.error.collectAsStateWithLifecycle()
            var notificationsEnabled by rememberSaveable { mutableStateOf(model.notifications.enabled()) }
            var permissionRequested by rememberSaveable { mutableStateOf(false) }
            var notificationMessage by rememberSaveable { mutableStateOf(model.notifications.access().message) }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                notificationsEnabled = model.notifications.enabled()
                notificationMessage = if (granted) "通知权限已允许，可在系统设置管理通知渠道。"
                    else "通知权限未允许，电量查询仍可使用；预警不会被记为已发送。可再次点击到系统设置开启。"
                if (granted) model.controller.recheckWarnings()
            }
            val owner = LocalLifecycleOwner.current
            DisposableEffect(owner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        model.controller.reload()
                        val wasEnabled = notificationsEnabled
                        notificationsEnabled = model.notifications.enabled()
                        notificationMessage = model.notifications.access().message
                        if (!wasEnabled && notificationsEnabled) model.controller.recheckWarnings()
                    }
                }
                owner.lifecycle.addObserver(observer)
                onDispose { owner.lifecycle.removeObserver(observer) }
            }
            KsuTheme(appearance) {
                PowerRoot(tab, model::selectTab, appearance, model.appearance::update, state, model.actions,
                    notificationsEnabled = notificationsEnabled,
                    onNotifications = {
                        val allowed = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED
                        if (!allowed && !permissionRequested) {
                            permissionRequested = true
                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            runCatching { startActivity(model.notifications.settingsIntent()) }
                                .onFailure { notificationMessage = "无法打开系统通知设置，请在系统应用设置中管理通知。" }
                        }
                    },
                    onTestNotification = {
                        notificationMessage = if (model.notifications.sendTest()) "测试通知已发送，请查看系统通知栏。"
                            else if (model.notifications.enabled()) "系统未接受测试通知，请稍后重试。"
                            else model.notifications.access().message
                    },
                    notificationMessage = notificationMessage, schedulingError = schedulingError,
                    onRetryScheduling = { PowerScheduling.ensure(this) })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra("tab")) model.selectTab(intent.getIntExtra("tab", 0))
    }
}
