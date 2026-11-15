package com.menglimi.gdust.v2.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.util.AtomicFile
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.net.Uri
import com.menglimi.gdust.v2.MainActivity
import com.menglimi.gdust.v2.R
import java.io.File

/** 自定义桌面入口与安装包标识独立；图片仅保存为应用私有缩略图。 */
object DesktopShortcut {
    private const val ID = "gdust-custom-home"
    private const val ICON_FILE = "desktop-icon.png"
    private fun prefs(context: Context) = context.getSharedPreferences("desktop_appearance", Context.MODE_PRIVATE)

    fun name(context: Context): String = prefs(context).getString("name", null)
        ?.takeIf { it.isNotBlank() } ?: context.getString(R.string.app_name)

    fun saveName(context: Context, value: String) {
        prefs(context).edit().putString("name", value.trim()).apply()
    }

    fun customIcon(context: Context): Bitmap? = File(context.filesDir, ICON_FILE).takeIf { it.isFile }
        ?.let { BitmapFactory.decodeFile(it.absolutePath) }

    fun useDefaultIcon(context: Context) {
        val icon = File(context.filesDir, ICON_FILE)
        if (icon.isFile) check(icon.delete()) { "暂时无法恢复默认图标" }
    }

    fun importIcon(context: Context, uri: Uri): Bitmap {
        val original = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            require(longest > 0) { "无法读取这张图片，请换一张" }
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
        Canvas(result).drawBitmap(original, Rect(left, top, left + edge, top + edge), Rect(0, 0, 256, 256),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        original.recycle()
        val file = AtomicFile(File(context.filesDir, ICON_FILE))
        val stream = file.startWrite()
        try {
            check(result.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "无法保存图标" }
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
        return result
    }

    fun pin(context: Context, title: String): Boolean {
        val manager = context.getSystemService(ShortcutManager::class.java)
        if (!manager.isRequestPinShortcutSupported) return false
        val label = title.trim()
        require(label.isNotBlank() && label.codePointCount(0, label.length) <= 30) { "桌面名称请填写 1–30 个字符" }
        val icon = customIcon(context)?.let(Icon::createWithAdaptiveBitmap)
            ?: Icon.createWithResource(context, R.drawable.desktop_icon_default)
        val shortcut = ShortcutInfo.Builder(context, ID)
            .setShortLabel(label).setLongLabel(label).setIcon(icon)
            .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            .build()
        saveName(context, label)
        manager.updateShortcuts(listOf(shortcut))
        return manager.requestPinShortcut(shortcut, null)
    }
}
