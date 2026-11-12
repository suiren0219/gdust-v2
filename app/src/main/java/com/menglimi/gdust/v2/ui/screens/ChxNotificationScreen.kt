package com.menglimi.gdust.v2.ui.screens

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.service.ChxNotificationListener
import com.menglimi.gdust.v2.ui.LocalDs

@Composable
fun ChxNotificationScreen(repo: Repository, onBack: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val ds = LocalDs.current
    var enabled by remember { mutableStateOf(repo.store.chxNotificationsEnabled) }
    var granted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    DisposableEffect(context, owner) {
        fun refresh() {
            val service = ComponentName(context, ChxNotificationListener::class.java)
            granted = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                .orEmpty().split(':').any { ComponentName.unflattenFromString(it) == service }
        }
        refresh()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    SettingSubPage("学习通通知待办", "本机通知 · 手动授权", onBack) {
        SectionHeader("同步")
        SettingGroup {
            SettingOption(
                title = "将通知加入待办",
                subtitle = "只接收学习通明确提示的新作业、待提交任务或考试；不读取账号密码",
                switch = enabled,
                onSwitch = { enabled = it; repo.store.chxNotificationsEnabled = it },
            )
            SettingDivider()
            SettingOption(
                title = "通知使用权",
                subtitle = "Android 授权会允许本应用接收设备通知；本功能只处理学习通通知",
                detail = if (granted) "已授权" else "未授权",
                onClick = {
                    error = !runCatching {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }.isSuccess
                },
            )
        }
        if (error) Text("无法打开系统设置，请在设置中手动开启通知使用权。", fontSize = 12.sp, color = ds.danger)
        Spacer(Modifier.height(8.dp))
        Text(
            "仅新收到的通知会加入待办。学习通未推送或通知未包含具体事项时不会生成待办；截止时间以原通知为准。关闭开关后不再采集，已有待办可手动删除。",
            fontSize = 12.sp, lineHeight = 18.sp, color = ds.sub,
        )
    }
}
