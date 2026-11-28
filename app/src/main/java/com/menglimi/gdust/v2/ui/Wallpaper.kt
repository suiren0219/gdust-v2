package com.menglimi.gdust.v2.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.menglimi.gdust.v2.data.Store
import java.io.File
import java.io.FileOutputStream

/** 全屏共享壁纸状态；图片复制到应用私有目录，避免系统 photo picker URI 权限过期。 */
@Composable
fun rememberWallpaperState(store: Store): WallpaperState {
    val context = LocalContext.current
    var revision by remember(store) { mutableStateOf(0) }
    var bitmap by remember(store) { mutableStateOf<ImageBitmap?>(null) }
    var loadError by remember(store) { mutableStateOf<String?>(null) }
    var blur by remember(store) { mutableFloatStateOf(store.wallpaperBlur) }
    var dim by remember(store) { mutableFloatStateOf(store.wallpaperDim) }

    LaunchedEffect(store, revision) {
        val path = store.wallpaperPath
        val loaded = if (path.isBlank()) null else runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, options)
                options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight,
                    context.resources.displayMetrics.widthPixels * 2, context.resources.displayMetrics.heightPixels * 2)
                options.inJustDecodeBounds = false
                BitmapFactory.decodeFile(path, options)?.asImageBitmap()
            }
        }.getOrNull()
        bitmap = loaded
        loadError = if (path.isNotBlank() && loaded == null) "壁纸文件无法读取，重新选择一张图片即可。" else null
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val target = File(context.filesDir, WALLPAPER_FILE)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                } ?: error("无法读取所选图片")
                store.wallpaperPath = target.absolutePath
                revision++
            }.onFailure { loadError = it.message ?: "无法保存壁纸" }
        }
    }

    return remember(store, bitmap, loadError, revision, blur, dim) {
        WallpaperState(
            bitmap = bitmap,
            blur = blur,
            dim = dim,
            error = loadError,
            choose = { picker.launch("image/*") },
            clear = {
                store.wallpaperPath.takeIf { it.isNotBlank() }?.let { runCatching { File(it).delete() } }
                store.wallpaperPath = ""
                revision++
            },
            setBlur = { value -> blur = value.coerceIn(0f, 40f); store.wallpaperBlur = blur },
            setDim = { value -> dim = value.coerceIn(0f, 0.85f); store.wallpaperDim = dim },
        )
    }
}

class WallpaperState internal constructor(
    val bitmap: ImageBitmap?,
    val blur: Float,
    val dim: Float,
    val error: String?,
    val choose: () -> Unit,
    val clear: () -> Unit,
    val setBlur: (Float) -> Unit,
    val setDim: (Float) -> Unit,
)

val LocalWallpaperState = androidx.compose.runtime.staticCompositionLocalOf<WallpaperState?> { null }

/** Background image layer. With no custom image the normal theme surface stays fully functional. */
@Composable
fun AppWallpaperBackground(
    state: WallpaperState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val ds = LocalDs.current
    CompositionLocalProvider(LocalWallpaperState provides state) {
        Box(modifier.fillMaxSize().background(ds.bg)) {
            state.bitmap?.let { image ->
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(state.blur.dp.coerceIn(0.dp, 40.dp)),
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = state.dim.coerceIn(0f, 0.85f))))
            }
            content()
        }
    }
}

/** All business pages share the app theme surface; the wallpaper is drawn by the root background layer. */
@Composable
fun pageBackground(): Color = Color.Transparent

private const val WALLPAPER_FILE = "app_wallpaper_image"

private fun calculateSampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    while (width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight) {
        sample *= 2
    }
    return sample
}
