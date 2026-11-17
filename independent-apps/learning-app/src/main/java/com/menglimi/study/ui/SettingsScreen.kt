package com.menglimi.study.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.ksu.ui.Appearance
import com.menglimi.ksu.ui.AppearanceCard
import com.menglimi.ksu.ui.AboutProjectCard
import com.menglimi.ksu.ui.DesktopAppearanceCard
import com.menglimi.ksu.ui.Glyph
import com.menglimi.ksu.ui.KCard
import com.menglimi.ksu.ui.PageHeader
import com.menglimi.ksu.ui.SectionTitle
import com.menglimi.ksu.ui.SettingRow
import com.menglimi.study.BuildConfig
import com.menglimi.study.learning.CoursesUiState
import com.menglimi.study.learning.LearningUiState

@Composable
fun SettingsScreen(
    appearance: Appearance, work: LearningUiState, courses: CoursesUiState,
    onAppearance: (Appearance) -> Unit, onLogin: () -> Unit,
    onDisconnect: () -> Unit, onClearCache: () -> Unit,
    modifier: Modifier = Modifier, preview: Boolean = false,
) {
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("设置", if (preview) "合成预览 · 不访问网络或正式数据" else "按你的习惯，刚刚好") }
        item { SectionTitle("外观") }
        item { AppearanceCard(appearance, onAppearance) }
        item { SectionTitle("桌面名称与图标") }
        item { DesktopAppearanceCard(preview) }
        item { SectionTitle("账号与数据") }
        item { KCard(Modifier.fillMaxWidth()) {
            SettingRow("学习通账号", if (work.connected || courses.connected) "已确认会话" else "登录状态待确认", Glyph.BOOK) {
                TextButton(onClick = { confirm = "login" }) { Text("登录 / 换号") }
            }
            SettingRow("本机加密缓存", if (work.hasSnapshot || courses.hasSnapshot) "已保存课程或作业快照" else "暂无已读取记录", Glyph.CLOCK) {
                TextButton(onClick = { confirm = "cache" }) { Text("清缓存") }
            }
            SettingRow("退出学习通", "清除登录 Cookie、网页存储和学习缓存", Glyph.CLOSE) {
                TextButton(onClick = { confirm = "logout" }) { Text("退出") }
            }
        } }
        item { SectionTitle("关于学习助手") }
        item { AboutProjectCard(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, "learning-assistant", preview) }
        item { KCard(Modifier.fillMaxWidth()) {
            Text("作业与课程，轻装同行", style = MaterialTheme.typography.titleMedium)
            Text("非学习通、超星或学校官方应用。由本机直接读取服务，账号密码不保存到应用；仅查看信息，不提供自动做题或自动提交。",
                fontSize = 13.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, bottom = 14.dp))
            Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", fontFamily = FontFamily.Monospace,
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("GNU AGPL-3.0 · 开源软件", fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        } }
        item { Text("课程页面可能调整，未知或失败结果会明确提示。截止时间、提交状态及任务点以学习通原网页为准。",
            fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    confirm?.let { type ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text(when (type) {
            "logout" -> "退出学习通？"
            "cache" -> "清除本机学习缓存？"
            else -> "打开学习通登录？"
        }) }, text = { Text(when (type) {
            "logout" -> "将停止正在进行的读取，清除本机登录 Cookie、网页存储、课程和作业快照。"
            "cache" -> "将停止正在进行的读取并清除课程、作业快照。网页登录 Cookie 保留，下次刷新会重新确认会话。"
            else -> "将清除当前课程和作业快照，进入通用学习通官网登录页。切换账号时请在官方网页操作，或先退出再登录。"
        }) }, confirmButton = { TextButton(onClick = {
            confirm = null
            when (type) { "logout" -> onDisconnect(); "cache" -> onClearCache(); else -> onLogin() }
        }) { Text("确认") } }, dismissButton = { TextButton(onClick = { confirm = null }) { Text("取消") } })
    }
}
