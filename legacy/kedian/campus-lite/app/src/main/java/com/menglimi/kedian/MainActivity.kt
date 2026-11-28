package com.menglimi.kedian

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.menglimi.kedian.learning.LearningWebActivity
import com.menglimi.kedian.ui.CampusRoot
import com.menglimi.kedian.ui.KedianTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val selectedTab = mutableIntStateOf(0)
    private val notificationsEnabled = mutableStateOf(false)
    private val app: CampusApp get() = application as CampusApp
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsEnabled.value = NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        selectedTab.intValue = savedInstanceState?.getInt("selected_tab") ?: intent.getIntExtra("tab", 0).coerceIn(0, 2)
        intent.removeExtra("tab")
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                notificationsEnabled.value = NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()
                app.learning.reload()
                app.power.reload()
            }
        }
        setContent {
            val appearance by app.appearance.state.collectAsStateWithLifecycle()
            val learning by app.learning.state.collectAsStateWithLifecycle()
            val power by app.power.state.collectAsStateWithLifecycle()
            KedianTheme(appearance) {
                CampusRoot(
                    tab = selectedTab.intValue,
                    onTab = { selectedTab.intValue = it },
                    appearance = appearance,
                    learning = learning,
                    power = power,
                    notificationsEnabled = notificationsEnabled.value,
                    onAppearance = app.appearance::update,
                    onLogin = { action {
                        app.learning.prepareLogin()
                        startActivity(Intent(this@MainActivity, LearningWebActivity::class.java))
                    } },
                    onLearningRefresh = { action { app.learning.refresh() } },
                    onOpenWork = { url -> action {
                        startActivity(Intent(this@MainActivity, LearningWebActivity::class.java).putExtra("url", url))
                    } },
                    onLearningClearError = app.learning::clearError,
                    onPowerRefresh = { action { app.power.refresh() } },
                    onQueryRooms = { account, code -> action { app.power.queryRooms(account, code) } },
                    onBind = { account, code, room -> action { app.power.bind(account, code, room) } },
                    onPowerClearError = app.power::clearError,
                    onNotifications = ::manageNotifications,
                    onPreset = app.power::setPreset,
                    onInterval = app.power::setInterval,
                    onThresholds = { low, drop -> app.power.setThreshold(low); app.power.setDropThreshold(drop) },
                    onDisconnect = { action { app.learning.disconnect() } },
                    onUnbind = app.power::unbind,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra("tab")) selectedTab.intValue = intent.getIntExtra("tab", 0).coerceIn(0, 2)
        intent.removeExtra("tab")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("selected_tab", selectedTab.intValue)
        super.onSaveInstanceState(outState)
    }

    private fun manageNotifications() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun action(block: suspend () -> Unit) {
        lifecycleScope.launch {
            try { block() } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { Toast.makeText(this@MainActivity, "操作暂未完成，请稍后重试", Toast.LENGTH_SHORT).show() }
        }
    }
}
