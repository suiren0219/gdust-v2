package com.menglimi.gdust.v2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.menglimi.gdust.v2.widget.LocalWidgetTodoId
import com.menglimi.gdust.v2.widget.WidgetOpenRequest
import com.menglimi.gdust.v2.widget.WidgetRefreshScheduler
import com.menglimi.gdust.v2.ui.AppRoot
import com.menglimi.gdust.v2.ui.AppTheme
import com.menglimi.gdust.v2.ui.UiStyle
import com.menglimi.gdust.v2.ui.rememberUiStyle

class MainActivity : ComponentActivity() {
    private val widgetRequest = mutableStateOf<WidgetOpenRequest?>(null)
    private val widgetRequestVersion = mutableIntStateOf(0)

    private fun receiveWidgetIntent(intent: Intent?) {
        val request = WidgetOpenRequest.fromIntent(intent) ?: return
        widgetRequest.value = request
        widgetRequestVersion.intValue++
        // 已接收后清除路由信息，旋转/进程恢复不能再次覆盖用户后来选择的 tab。
        intent?.apply {
            action = Intent.ACTION_MAIN
            data = null
            removeExtra(WidgetOpenRequest.EXTRA_TAB)
            removeExtra(WidgetOpenRequest.EXTRA_TODO_ID)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveWidgetIntent(intent)
    }

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
        receiveWidgetIntent(intent)
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
        WidgetRefreshScheduler.ensureScheduled(app)
        // 精确闹钟链与 6h 兜底同样做一次幂等补排（防 WorkManager 迁移竞态 / 系统清闹钟）
        if (app.repo.store.reminderEnabled) com.menglimi.gdust.v2.work.LessonReminderWorker.schedule(app)
        if (app.repo.store.todoReminderEnabled) com.menglimi.gdust.v2.work.LessonReminderAlarms.rescheduleTodo(app, app.repo.store)
        setContent {
            val style = rememberUiStyle(app.repo.store)
            // 风格切换持久化：值变了就写本机
            androidx.compose.runtime.LaunchedEffect(style.value) {
                app.repo.store.uiStyle = style.value.name
            }
            // 页面状态提到主题外层：切风格会重建两套主题树，状态不能跟着丢
            // （rememberSaveable 救不了同 composition 内的树切换——slot 销毁时不会走存档）
            val tab = rememberSaveable { mutableIntStateOf(widgetRequest.value?.tab ?: app.repo.store.startTab) }
            val showSettings = rememberSaveable { mutableStateOf(false) }
            // 小部件打开本机内容可走 App 已有的预览模式，不要求教务会话仍然有效。
            val loggedIn = rememberSaveable { mutableStateOf(widgetRequest.value != null) }
            val checked = rememberSaveable { mutableStateOf(false) }
            val monetEnabled = rememberSaveable { mutableStateOf(app.repo.store.monetEnabled) }
            // 定位与 navigationVersion 一起下发，避免旧页面先消费请求后又被导航重建。
            val focusedTodo = rememberSaveable { mutableStateOf<Long?>(null) }
            val navigationVersion = rememberSaveable { mutableIntStateOf(0) }
            LaunchedEffect(widgetRequestVersion.intValue) {
                widgetRequest.value?.let { request ->
                    tab.intValue = request.tab
                    showSettings.value = false
                    loggedIn.value = true
                    focusedTodo.value = request.todoId
                    navigationVersion.intValue++
                    widgetRequest.value = null
                }
            }
            CompositionLocalProvider(LocalWidgetTodoId provides focusedTodo) {
                AppTheme(styleState = style, monetEnabled = monetEnabled.value) {
                    // 热启动从设置的子页面进入时，也清除 AppRoot 内部的临时页面状态。
                    key(navigationVersion.intValue) {
                        AppRoot(app.repo, tab = tab, showSettings = showSettings, loggedIn = loggedIn, checked = checked, monetEnabled = monetEnabled)
                    }
                }
            }
        }
    }
}
