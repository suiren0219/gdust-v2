package com.menglimi.gdust.v2.ui.screens

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.CloudMcpPrefs
import com.menglimi.gdust.v2.data.CloudMcpRepository
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.service.LocalMcpRuntime
import com.menglimi.gdust.v2.service.LocalMcpService
import com.menglimi.gdust.v2.ui.LocalDs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun McpSettingsScreen(
    repo: Repository,
    onBack: () -> Unit,
    onImported: () -> Unit = {},
    offlinePreview: Boolean = false,
) {
    val preview = offlinePreview || com.menglimi.gdust.v2.BuildConfig.PREVIEW_TARGET ||
        !com.menglimi.gdust.v2.BuildConfig.MCP_ENABLED
    if (preview) {
        SettingSubPage("MCP", "离线预览 · 本机服务与云端操作已禁用。", onBack) {
            SectionHeader("离线预览")
            SettingGroup {
                Text("不会读取或保存 MCP 配置，不会启动本机服务，也不会发送远程请求。",
                    color = LocalDs.current.sub, fontSize = 13.sp)
            }
        }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runtime by LocalMcpRuntime.status.collectAsState()
    var localEnabled by remember { mutableStateOf(false) }
    var localBusy by remember { mutableStateOf(true) }
    var token by remember { mutableStateOf("") }
    var savedToken by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val localDirty = token != savedToken
    val controller = remember(repo, context) {
        CloudMcpController(scope, CloudMcpRepository(CloudMcpPrefs(context)),
            exportSchedule = { withContext(Dispatchers.IO) { repo.store.exportCurrentSchedule() } },
            importSchedule = { preview -> withContext(Dispatchers.IO) {
                repo.store.importIndependentSchedule(preview.document, preview.revision).name
            } })
    }
    val page by controller.state.collectAsState()
    DisposableEffect(controller) { controller.load(); onDispose { controller.close() } }
    LaunchedEffect(page.importedName) { if (page.importedName != null) onImported() }
    LaunchedEffect(Unit) {
        try {
            val config = withContext(Dispatchers.IO) { repo.store.localMcpEnabled to repo.store.localMcpToken }
            localEnabled = config.first; token = config.second; savedToken = config.second
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { localError = "本机配置无法读取，请稍后重试。" }
        finally { localBusy = false }
    }
    LaunchedEffect(runtime.error) { if (runtime.error != null) localEnabled = false }
    val back = { if (!page.confirming && !localBusy && !runtime.starting) onBack() }
    SettingSubPage("MCP", "手机本机只读服务 · 可选个人课表云", back) {
        SectionHeader("手机本机 MCP")
        SettingGroup {
            SettingOption("启用本机 MCP", "仅同一台手机可连接，不向局域网开放。",
                detail = when { runtime.starting -> "启动中"; runtime.running -> "运行中"; else -> "已停止" },
                switch = localEnabled, onSwitch = { enabled ->
                    if (!localBusy && !runtime.starting && (!enabled || !localDirty)) {
                        localBusy = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { repo.store.localMcpEnabled = enabled }
                                localEnabled = enabled; localError = null
                                if (enabled) LocalMcpService.start(context) else LocalMcpService.stop(context)
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { localError = "本机开关保存失败，请重试。" }
                            finally { localBusy = false }
                        }
                    }
                })
            SettingEditor {
                Text("五个只读工具：按周、日期或关键词查课，读取学期与待办。没有修改课表或联网同步工具。", color = LocalDs.current.sub, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(LocalMcpService.url, color = LocalDs.current.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { copySensitive(context, LocalMcpService.url) }) { Text("复制") }
                }
                Text("访问 Token（可选）", color = LocalDs.current.text, fontSize = 14.sp)
                PrivateMcpField(token, { if (it.length <= 4096) token = it }, "不填写则不校验 Token",
                    enabled = !localBusy && !localEnabled && !runtime.running && !runtime.starting)
                Text("先关闭服务再修改 Token；填写后所有请求必须提供 Authorization: Bearer Token。", color = LocalDs.current.sub, fontSize = 13.sp)
                NexioActionButton(if (localDirty) "保存 Token" else "Token 已保存", enabled = localDirty && !localBusy && !localEnabled && !runtime.running) {
                    localBusy = true
                    scope.launch {
                        try {
                            val normalized = token.trim()
                            require(normalized.isEmpty() || normalized.all { it.code in 33..126 })
                            withContext(Dispatchers.IO) { repo.store.localMcpToken = normalized }
                            token = normalized; savedToken = normalized; localError = null
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { localError = "Token 保存失败，请使用无空格的 ASCII 字符。" }
                        finally { localBusy = false }
                    }
                }
                (localError ?: runtime.error)?.let { Text(it, fontSize = 13.sp, color = LocalDs.current.danger) }
            }
        }
        SectionHeader("个人课表云（可选）")
        SettingGroup {
            SettingOption("允许连接个人云", "默认关闭。开关和配置保存都不会联网；仅手动操作才发请求。",
                switch = page.draft.enabled, onSwitch = { controller.edit(page.draft.copy(enabled = it)) })
            SettingEditor {
                Text("HTTPS 地址（/mcp）", color = LocalDs.current.text, fontSize = 14.sp)
                PrivateMcpField(page.draft.url, { if (it.length <= 2048) controller.edit(page.draft.copy(url = it)) },
                    "https://你的个人云/mcp", !page.busy)
                Text("Bearer Token", color = LocalDs.current.text, fontSize = 14.sp)
                PrivateMcpField(page.draft.token, { if (it.length <= 4096) controller.edit(page.draft.copy(token = it)) },
                    "仅加密保存于本机，不进入备份", !page.busy)
                McpActions {
                    Box(Modifier.weight(1f)) { NexioActionButton(if (page.dirty) "保存配置" else "已保存", !page.busy && page.dirty) { controller.save() } }
                    Box(Modifier.weight(1f)) { NexioActionButton("测试连接", page.canRequest) { controller.inspect() } }
                }
                Text(page.error ?: page.message, color = if (page.error == null) LocalDs.current.sub else LocalDs.current.danger,
                    fontSize = 13.sp, lineHeight = 19.sp)
                Text("上传当前课表", color = LocalDs.current.text, fontSize = 16.sp)
                Text("先生成预览，确认后才写云端。若云端已有课表，确认会替换那一份。", color = LocalDs.current.sub, fontSize = 13.sp)
                NexioActionButton("预览上传", page.canRequest) { controller.previewUpload() }
                page.upload?.let { preview ->
                    Text("${preview.courseCount} 门 · 基于云端版本 ${preview.baseRevision}\n${if (preview.replaces) "确认将替换个人云已有课表" else "确认将在个人云新建课表"}",
                        color = LocalDs.current.sub, fontSize = 13.sp)
                    McpActions {
                        Box(Modifier.weight(1f)) { NexioActionButton("取消预览", !page.busy) { controller.cancelPreview() } }
                        Box(Modifier.weight(1f)) { NexioActionButton("确认上传", page.canRequest) { controller.confirmUpload() } }
                    }
                }
                Text("下载为独立课表", color = LocalDs.current.text, fontSize = 16.sp)
                Text("确认后新建并切换一张本地课表；学校课表和已有独立课表均保留。重名自动加后缀。", color = LocalDs.current.sub, fontSize = 13.sp)
                NexioActionButton("预览下载", page.canRequest) { controller.previewDownload() }
                page.download?.let { preview ->
                    Text("${preview.scheduleName} · ${preview.courseCount} 门 · 云端版本 ${preview.revision}", color = LocalDs.current.sub, fontSize = 13.sp)
                    McpActions {
                        Box(Modifier.weight(1f)) { NexioActionButton("取消预览", !page.busy) { controller.cancelPreview() } }
                        Box(Modifier.weight(1f)) { NexioActionButton("确认导入", page.canRequest) { controller.confirmDownload() } }
                    }
                }
                Text("首版支持广科 4＋4＋2 节固定作息。不支持的自定义作息、假期调休、教学周重组和单日例外会明确拒绝，不会静默丢弃。", color = LocalDs.current.sub, fontSize = 13.sp, lineHeight = 19.sp)
                if (page.confirming) Text("正在确认，请留在此页；不会自动重试提交。", color = LocalDs.current.sub, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun McpActions(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun PrivateMcpField(value: String, onChange: (String) -> Unit, hint: String, enabled: Boolean = true) {
    var shown by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(shown) { if (shown) { delay(30_000); shown = false } }
    LaunchedEffect(windowFocused, enabled) { if (!windowFocused || !enabled) shown = false }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        NexioField(value, onChange, hint, modifier = Modifier.weight(1f).onFocusChanged {
            if (focused && !it.isFocused) shown = false
            focused = it.isFocused
        }, visual = if (shown) VisualTransformation.None else PasswordVisualTransformation(), enabled = enabled)
        TextButton(onClick = { shown = !shown }, enabled = enabled) { Text(if (shown) "隐藏" else "显示") }
    }
}

private fun copySensitive(context: Context, value: String) {
    val clip = ClipData.newPlainText("本机 MCP 地址", value)
    clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    Toast.makeText(context, "地址已复制，剪贴板内容标记为敏感", Toast.LENGTH_SHORT).show()
}
