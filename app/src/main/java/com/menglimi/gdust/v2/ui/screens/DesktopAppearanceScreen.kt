package com.menglimi.gdust.v2.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.R
import com.menglimi.gdust.v2.launcher.DesktopShortcut
import com.menglimi.gdust.v2.ui.LocalDs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DesktopAppearanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val ds = LocalDs.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(DesktopShortcut.name(context)) }
    var icon by remember { mutableStateOf(DesktopShortcut.customIcon(context)) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    icon = withContext(Dispatchers.IO) { DesktopShortcut.importIcon(context, uri) }
                    failed = false
                    message = "图标已选好，添加到桌面后生效"
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failed = true
                    message = "图片读取失败，请选择其他图片"
                } finally {
                    busy = false
                }
            }
        }
    }
    SettingSubPage("桌面名称与图标", "为桌面入口选择你喜欢的样子", onBack) {
        SettingGroup {
            SettingEditor {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val bitmap = icon
                    if (bitmap != null) Image(bitmap.asImageBitmap(), "桌面图标预览", Modifier.size(72.dp))
                    else Image(painterResource(R.drawable.desktop_icon_default), "桌面图标预览", Modifier.size(72.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(name.ifBlank { "桌面名称" }, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ds.text)
                        Text("预览", fontSize = 13.sp, color = ds.sub)
                    }
                }
                InputField(name, { if (it.codePointCount(0, it.length) <= 30) name = it }, "桌面显示名称")
                Button(if (busy) "正在读取图片…" else "选择图标图片", enabled = !busy) { picker.launch("image/*") }
                if (icon != null) Button("使用默认人物图标", enabled = !busy) {
                    runCatching { DesktopShortcut.useDefaultIcon(context) }.onSuccess { icon = null }
                        .onFailure { failed = true; message = "恢复默认图标失败，请重试" }
                }
                Button("添加到桌面", enabled = !busy && name.isNotBlank()) {
                    runCatching { DesktopShortcut.pin(context, name) }
                        .onSuccess { supported ->
                            failed = !supported
                            message = if (supported) "已请求添加，请在桌面确认；已有定制入口会同步更新"
                                else "当前桌面不支持添加快捷方式，可在桌面应用中修改名称和图标"
                        }.onFailure {
                            failed = true
                            message = "暂时无法添加，请稍后重试"
                        }
                }
                if (message.isNotEmpty()) Text(message, color = if (failed) ds.danger else ds.sub, fontSize = 13.sp)
            }
        }
        Text("自定义后会添加一个桌面快捷方式。应用列表中的原图标和安装标识保持固定；你可以从桌面移走旧入口。",
            fontSize = 13.sp, lineHeight = 20.sp, color = ds.sub)
        Spacer(Modifier.height(12.dp))
        SettingGroup {
            SettingOption(title = "应用标识", subtitle = context.packageName)
        }
    }
}
