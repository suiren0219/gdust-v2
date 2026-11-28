package com.menglimi.gdust.v2.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

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
        try {
            val loaded = if (path.isBlank()) null else withContext(Dispatchers.IO) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, options)
                options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight,
                    context.resources.displayMetrics.widthPixels * 2, context.resources.displayMetrics.heightPixels * 2)
                options.inJustDecodeBounds = false
                BitmapFactory.decodeFile(path, options)?.asImageBitmap()
            }
            currentCoroutineContext().ensureActive()
            if (store.wallpaperPath == path) {
                bitmap = loaded
                loadError = if (path.isNotBlank() && loaded == null) "壁纸文件无法读取，重新选择一张图片即可。" else null
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (store.wallpaperPath == path) {
                bitmap = null
                loadError = "壁纸文件无法读取，重新选择一张图片即可。"
            }
        }
    }

    val scope = rememberCoroutineScope()
    val writeMutex = remember(store) { Mutex() }
    val requestGeneration = remember(store) { AtomicLong(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val generation = requestGeneration.incrementAndGet()
            scope.launch {
                val target = File(context.filesDir, WALLPAPER_FILE)
                val result = runCatching {
                    writeMutex.withLock {
                        withContext(Dispatchers.IO) {
                            val candidate = File.createTempFile("wallpaper-", ".image", context.cacheDir)
                            try {
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    candidate.outputStream().use(input::copyTo)
                                } ?: error("无法读取所选图片")
                                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeFile(candidate.absolutePath, bounds)
                                check(bounds.outWidth > 0 && bounds.outHeight > 0) { "所选文件不是可用图片" }
                                val options = BitmapFactory.Options().apply {
                                    inSampleSize = calculateSampleSize(
                                        bounds.outWidth,
                                        bounds.outHeight,
                                        context.resources.displayMetrics.widthPixels * 2,
                                        context.resources.displayMetrics.heightPixels * 2,
                                    )
                                }
                                val decoded = BitmapFactory.decodeFile(candidate.absolutePath, options)
                                    ?: error("所选图片无法解码")
                                decoded.recycle()
                                currentCoroutineContext().ensureActive()
                                check(requestGeneration.get() == generation) { "选择已取消" }
                                val file = AtomicFile(target)
                                val output = file.startWrite()
                                try {
                                    candidate.inputStream().use { input -> input.copyTo(output) }
                                    currentCoroutineContext().ensureActive()
                                    check(requestGeneration.get() == generation) { "选择已取消" }
                                    file.finishWrite(output)
                                } catch (error: Exception) {
                                    file.failWrite(output)
                                    throw error
                                }
                            } finally {
                                candidate.delete()
                            }
                        }
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                }
                if (requestGeneration.get() == generation) {
                    result.onSuccess {
                        store.wallpaperPath = target.absolutePath
                        loadError = null
                        revision++
                    }.onFailure {
                        loadError = it.message ?: "无法保存壁纸"
                    }
                }
            }
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
                requestGeneration.incrementAndGet()
                scope.launch {
                    writeMutex.withLock {
                        withContext(Dispatchers.IO) {
                            val file = AtomicFile(File(context.filesDir, WALLPAPER_FILE))
                            file.delete()
                            file.baseFile.delete()
                            File(context.filesDir, "$WALLPAPER_FILE.bak").delete()
                        }
                        store.wallpaperPath = ""
                        bitmap = null
                        loadError = null
                        revision++
                    }
                }
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

/** 仅绘制主题底色与自定义壁纸图层，便于在单页 SoftGlass 采样层内复用，避免顶栏渐进模糊丢失壁纸背景。 */
@Composable
fun AppWallpaperLayer(
    state: WallpaperState? = LocalWallpaperState.current,
    modifier: Modifier = Modifier,
) {
    val ds = LocalDs.current
    Box(modifier.fillMaxSize().background(ds.bg)) {
        state?.bitmap?.let { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(state.blur.dp.coerceIn(0.dp, 40.dp)),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = state.dim.coerceIn(0f, 0.85f))))
        }
    }
}

/** Background image layer. With no custom image the normal theme surface stays fully functional. */
@Composable
fun AppWallpaperBackground(
    state: WallpaperState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(LocalWallpaperState provides state) {
        Box(modifier.fillMaxSize()) {
            AppWallpaperLayer(state)
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
