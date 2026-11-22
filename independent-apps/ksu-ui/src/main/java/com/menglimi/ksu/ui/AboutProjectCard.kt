package com.menglimi.ksu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AboutProjectCard(versionName: String, versionCode: Int, artifact: String, preview: Boolean = false) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var available by remember { mutableStateOf<AppRelease?>(null) }
    fun open(url: String) {
        runCatching { uriHandler.openUri(url) }.onFailure { message = "无法打开浏览器，请稍后重试。" }
    }
    KCard(Modifier.fillMaxWidth()) {
        Text("作者 / 开发者", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(SuirenProject.AUTHOR, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Text("$versionName ($versionCode)", fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall)
        Text(context.packageName, fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text("GitHub 更新", style = MaterialTheme.typography.titleMedium)
        Text("手动获取本应用的发布信息；下载和安装由你确认。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = !checking && !preview, onClick = {
                checking = true
                available = null
                message = null
                scope.launch {
                    try {
                        val release = AppReleaseClient.latest(artifact)
                        when {
                            release == null -> message = "最近发布中还没有本应用的安装包，可在 GitHub 发布页查看。"
                            newerAppRelease(release, versionName) -> {
                                available = release
                                message = "发现 ${release.version}${if (release.preview) " 预览版" else ""}，可查看说明后下载。"
                            }
                            else -> message = "已检查，本应用没有更高版本的发布包。"
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        message = if (error is java.io.IOException) "无法连接 GitHub，请检查网络或打开发布页。"
                            else "暂时无法检查更新，请稍后重试或打开 GitHub 发布页。"
                    } finally {
                        checking = false
                    }
                }
            }) { Text(if (checking) "正在检查…" else "检查更新") }
            TextButton(enabled = !preview, onClick = { open(SuirenProject.RELEASES) }) { Text("GitHub 发布页") }
            TextButton(enabled = !preview, onClick = { open(SuirenProject.HOME) }) { Text("项目主页") }
        }
        if (checking) CircularProgressIndicator()
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        available?.let { release ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { open(release.page) }) { Text("查看更新说明") }
                TextButton(onClick = { open(release.download) }) { Text("下载 ${release.version}") }
            }
        }
        if (preview) Text("合成预览不访问更新服务。", style = MaterialTheme.typography.bodySmall)
    }
}
