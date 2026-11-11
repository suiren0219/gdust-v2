package com.menglimi.gdust.v2.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

/** 是否有壁纸激活：页面据此决定背景是否透明（让壁纸透出来）。 */
val LocalWallpaperActive = staticCompositionLocalOf { false }

private fun File.pathOrBlank(): String = if (exists() && length() > 0) absolutePath else ""

/** 壁纸图片文件（存在应用私有目录，换机/重装即失效）。 */
fun wallpaperFile(context: Context): File = File(context.filesDir, "wallpaper.img")

/** 把用户选的图片复制进私有目录，返回可用路径；失败返回空。 */
fun importWallpaper(context: Context, uri: Uri): String = runCatching {
    val target = wallpaperFile(context)
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    } ?: return ""
    target.pathOrBlank()
}.getOrDefault("")

/** 解码壁纸（限制尺寸，避免大图 OOM）。 */
@Composable
fun rememberWallpaperBitmap(path: String): ImageBitmap? {
    if (path.isBlank()) return null
    return remember(path) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 1440 || bounds.outHeight / sample > 2880) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
        }.getOrNull()
    }
}

/** 壁纸背景层：铺满 + 模糊 + 压暗。 */
@Composable
fun WallpaperBackdrop(
    path: String,
    blurDp: Float,
    dim: Float,
    modifier: Modifier = Modifier,
) {
    val image = rememberWallpaperBitmap(path)
    if (image == null) {
        Box(modifier.fillMaxSize().background(LocalDs.current.bg))
        return
    }
    Box(modifier.fillMaxSize()) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (blurDp > 0.5f) Modifier.blur(blurDp.dp) else Modifier),
        )
        if (dim > 0.01f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        }
    }
}

/** 页面底色：有壁纸时透明，让壁纸透出来；否则用主题底色。 */
@Composable
fun pageBackground(): Color {
    val ds = LocalDs.current
    return if (LocalWallpaperActive.current) Color.Transparent else ds.bg
}
