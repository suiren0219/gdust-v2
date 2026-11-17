package com.menglimi.ksu.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.net.Uri
import android.util.AtomicFile
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal fun desktopNameError(value: String): String? {
    val name = value.trim()
    return when {
        name.isEmpty() -> "桌面名称不能为空"
        name.codePointCount(0, name.length) > 30 -> "桌面名称最多 30 个字符"
        name.any { Character.isISOControl(it) } -> "桌面名称不能包含换行或控制字符"
        else -> null
    }
}

internal object DesktopAppearance {
    private const val ID = "suiren-custom-home"
    private const val FILE = "custom-desktop-icon.png"
    private fun prefs(context: Context) = context.getSharedPreferences("desktop_appearance", Context.MODE_PRIVATE)
    fun defaultName(context: Context): String = context.applicationInfo.loadLabel(context.packageManager).toString()
    fun name(context: Context): String = prefs(context).getString("name", null)?.takeIf { it.isNotBlank() }
        ?: defaultName(context)
    fun customIcon(context: Context): Bitmap? = File(context.filesDir, FILE).takeIf(File::isFile)
        ?.let { BitmapFactory.decodeFile(it.absolutePath) }

    fun defaultIcon(context: Context): Bitmap {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val foreground = requireNotNull(BitmapFactory.decodeResource(context.resources, R.drawable.suiren_launcher_foreground))
        Canvas(bitmap).apply {
            drawColor(android.graphics.Color.rgb(250, 231, 216))
            drawBitmap(foreground, null, Rect(0, 0, 256, 256), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        foreground.recycle()
        return bitmap
    }

    fun importIcon(context: Context, uri: Uri): Bitmap {
        val original = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            require(longest > 0) { "图片无法读取" }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            if (longest > 1024) decoder.setTargetSize(
                (info.size.width * 1024L / longest).toInt().coerceAtLeast(1),
                (info.size.height * 1024L / longest).toInt().coerceAtLeast(1),
            )
        }
        val edge = minOf(original.width, original.height)
        val left = (original.width - edge) / 2
        val top = (original.height - edge) / 2
        val result = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        try {
            Canvas(result).drawBitmap(original, Rect(left, top, left + edge, top + edge), Rect(0, 0, 256, 256),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } finally { original.recycle() }
        val file = AtomicFile(File(context.filesDir, FILE))
        val stream = file.startWrite()
        try {
            check(result.compress(Bitmap.CompressFormat.PNG, 100, stream))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            result.recycle()
            throw error
        }
        return result
    }

    fun reset(context: Context) {
        val file = File(context.filesDir, FILE)
        if (file.exists()) check(file.delete()) { "无法删除自定义图标" }
        prefs(context).edit().remove("name").apply()
    }

    fun apply(context: Context, value: String): String {
        desktopNameError(value)?.let { error(it) }
        val manager = context.getSystemService(ShortcutManager::class.java)
        val title = value.trim()
        val intent = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName)) {
            "无法找到应用启动入口"
        }.setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val icon = customIcon(context) ?: defaultIcon(context)
        val shortcut = ShortcutInfo.Builder(context, ID).setShortLabel(title).setLongLabel(title)
            .setIcon(Icon.createWithAdaptiveBitmap(icon)).setIntent(intent).build()
        val exists = manager.pinnedShortcuts.any { it.id == ID }
        val message = if (exists) {
            check(manager.updateShortcuts(listOf(shortcut))) { "桌面入口更新失败，请稍后重试。" }
            "已更新自定义桌面入口；名称与图标的刷新时间取决于桌面。"
        } else {
            if (!manager.isRequestPinShortcutSupported) return "当前桌面不支持添加自定义入口，可更换支持快捷方式的桌面。"
            check(manager.requestPinShortcut(shortcut, null)) { "桌面未接受添加请求，请稍后重试。" }
            "已请求添加，请在系统桌面弹窗中确认。"
        }
        prefs(context).edit().putString("name", title).apply()
        return message
    }
}

@Composable
fun DesktopAppearanceCard(preview: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf(DesktopAppearance.name(context)) }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val custom = remember(revision) { DesktopAppearance.customIcon(context) }
    val bitmap = custom ?: remember { DesktopAppearance.defaultIcon(context) }
    val invalid = desktopNameError(title)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { DesktopAppearance.importIcon(context, uri) }
                    revision++
                    message = "图标已选好，点击“应用到桌面”后生效。"
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    message = "无法读取这张图片，请重新选择。"
                } finally { busy = false }
            }
        }
    }
    KCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(bitmap.asImageBitmap(), "桌面图标预览", Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)))
            Text(if (custom == null) "主项目默认图标" else "自定义图标", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(title, { title = it; message = null }, label = { Text("桌面名称") },
            singleLine = true, enabled = !busy && !preview, isError = invalid != null,
            supportingText = { Text(invalid ?: "1–30 个字符，仅修改自定义桌面入口") },
            modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = !busy && !preview, onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text(if (busy) "正在读取…" else "选择图片") }
            TextButton(enabled = !busy && !preview, onClick = {
                runCatching { DesktopAppearance.reset(context) }
                    .onSuccess {
                        title = DesktopAppearance.defaultName(context)
                        revision++
                        message = "已恢复默认，点击“应用到桌面”更新已有入口。"
                    }.onFailure { message = "暂时无法恢复默认，请稍后重试。" }
            }) { Text("恢复默认") }
        }
        Button(enabled = invalid == null && !busy && !preview, onClick = {
            message = runCatching { DesktopAppearance.apply(context, title) }
                .getOrElse { "暂时无法添加或更新桌面入口，请稍后重试。" }
        }, modifier = Modifier.fillMaxWidth()) { Text("应用到桌面") }
        Spacer(Modifier.height(10.dp))
        Text("创建或更新一个自定义桌面快捷方式。应用列表中的名称和安装包名保持不变；包名始终含 gdust 和 suiren。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        if (preview) Text("合成预览不修改桌面。", style = MaterialTheme.typography.bodySmall)
    }
}
