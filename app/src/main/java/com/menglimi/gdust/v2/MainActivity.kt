package com.menglimi.gdust.v2

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.menglimi.gdust.v2.ui.AppRoot
import com.menglimi.gdust.v2.ui.AppTheme
import com.menglimi.gdust.v2.ui.UiStyle
import com.menglimi.gdust.v2.ui.rememberUiStyle

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        // 锁字体缩放：避免系统大字号撑爆固定高度课表网格
        val cfg = newBase.resources.configuration.apply { fontScale = 1.0f }
        super.attachBaseContext(newBase.createConfigurationContext(cfg))
    }

    private val notifPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val app = application as App
        // WorkManager 首启迁移数据库与 App.onCreate 的入队存在竞态（入队可能被迁移覆盖清空）：
        // 在 Activity 建立后再幂等补排一次（心跳 KEEP / 链 REPLACE，重复调用无副作用）
        com.menglimi.gdust.v2.work.PowerWorker.schedule(app)
        // 精确闹钟链与 6h 兜底同样做一次幂等补排（防 WorkManager 迁移竞态 / 系统清闹钟）
        if (app.repo.store.reminderEnabled) com.menglimi.gdust.v2.work.LessonReminderWorker.schedule(app)
        setContent {
            val style = rememberUiStyle(app.repo.store)
            // 风格切换持久化：值变了就写本机
            androidx.compose.runtime.LaunchedEffect(style.value) {
                app.repo.store.uiStyle = style.value.name
            }
            // 页面状态提到主题外层：切风格会重建两套主题树，状态不能跟着丢
            // （rememberSaveable 救不了同 composition 内的树切换——slot 销毁时不会走存档）
            val tab = androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(app.repo.store.startTab) }
            val showSettings = androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
            val loggedIn = androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
            val checked = androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
            val monetEnabled = androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(app.repo.store.monetEnabled) }
            AppTheme(styleState = style, monetEnabled = monetEnabled.value) {
                AppRoot(app.repo, tab = tab, showSettings = showSettings, loggedIn = loggedIn, checked = checked, monetEnabled = monetEnabled)
            }
        }
    }
}
