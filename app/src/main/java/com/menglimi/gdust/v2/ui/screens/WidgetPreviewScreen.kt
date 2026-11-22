package com.menglimi.gdust.v2.ui.screens

import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.emptyPreferences
import androidx.glance.appwidget.compose
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.widget.WidgetPreset
import com.menglimi.gdust.v2.widget.WidgetPresets
import com.menglimi.gdust.v2.widget.WidgetStyle
import com.menglimi.gdust.v2.widget.pinWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 九款预览直接复用生产 Glance 布局、缓存数据与密度策略，避免两套 UI 漂移。 */
@Composable
fun WidgetPreviewScreen(onBack: () -> Unit) {
    val ds = LocalDs.current
    BackHandler { onBack() }
    Column(
        Modifier.fillMaxSize().background(ds.bg).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        PageTitle(title = "桌面小部件", onBack = onBack)
        M3Text("课表、电量与待办，随时抬眼可见。点按预览添加到桌面。",
            fontSize = 13.sp, color = ds.sub, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
        WidgetPresets.ALL.chunked(3).forEachIndexed { group, presets ->
            SectionLabel(listOf("课表数据", "宿舍电量", "待办条子")[group])
            presets.forEachIndexed { index, preset ->
                PresetRow(preset, when (index) {
                    1 -> DpSize(180.dp, 180.dp)
                    2 -> DpSize(96.dp, 192.dp)
                    else -> DpSize(340.dp, 170.dp)
                })
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(title: String) {
    val ds = LocalDs.current
    M3Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = ds.text,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
}

@Composable
private fun PresetRow(preset: WidgetPreset, preferredSize: DpSize) {
    val ds = LocalDs.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().clickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null,
        role = Role.Button, onClickLabel = "添加${preset.label}到桌面",
    ) {
        scope.launch {
            val ok = pinWidget(context, preset)
            Toast.makeText(context, if (ok) "请在桌面确认添加" else "当前桌面不支持一键添加，请长按桌面手动添加", Toast.LENGTH_SHORT).show()
        }
    }) {
        GlancePreview(preset, preferredSize)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            M3Text(preset.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ds.text, modifier = Modifier.weight(1f))
            M3Text("添加到桌面", fontSize = 13.sp, color = Color(WidgetStyle.accentText(WidgetStyle.isDark(context))))
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun GlancePreview(preset: WidgetPreset, preferredSize: DpSize) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val dark = WidgetStyle.isDark(context)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 按实际可用宽度重排，不缩放 bitmap，系统大字体也沿用桌面上的降密度规则。
        val size = DpSize(minOf(preferredSize.width, maxWidth), preferredSize.height)
        var remoteViews by remember(context, preset, size, configuration.uiMode, configuration.fontScale) { mutableStateOf<RemoteViews?>(null) }
        var failed by remember(context, preset, size, configuration.uiMode, configuration.fontScale) { mutableStateOf(false) }
        LaunchedEffect(context, preset, size, configuration.uiMode, configuration.fontScale) {
            try {
                val widget = preset.receiver.getDeclaredConstructor().newInstance().glanceAppWidget
                remoteViews = widget.compose(context = context, size = size, state = emptyPreferences())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            }
        }
        Box(Modifier.size(size.width, size.height).clip(RoundedCornerShape(WidgetStyle.radius))
            .background(Color(WidgetStyle.card(dark))), contentAlignment = Alignment.Center,
        ) {
            val views = remoteViews
            if (views != null) {
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { viewContext ->
                    object : FrameLayout(viewContext) {
                        // 拒绝传递到 RemoteViews 的 PendingIntent，让 Compose 外层处理添加与滚动。
                        override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
                    }.apply {
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    }
                }, update = { host ->
                    if (host.tag !== views) {
                        host.removeAllViews()
                        host.addView(views.apply(context, host), FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                        host.tag = views
                    }
                })
            } else {
                M3Text(if (failed) "预览暂不可用" else "载入预览", fontSize = WidgetStyle.Type.label.sp,
                    color = WidgetStyle.sub(dark), modifier = Modifier.padding(8.dp))
            }
        }
    }
}
