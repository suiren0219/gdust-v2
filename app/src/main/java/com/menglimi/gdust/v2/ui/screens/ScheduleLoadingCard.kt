package com.menglimi.gdust.v2.ui.screens

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.ui.AppCard
import com.menglimi.gdust.v2.ui.AppUiTokens
import com.menglimi.gdust.v2.ui.LocalDs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh

/** 仅展示真实等待时长；取消与超时由调用方控制。移出组合后计时和动画自动停止。 */
@Composable
fun ScheduleLoadingCard(
    title: String,
    detail: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onContinue: (() -> Unit)? = null,
) {
    val ds = LocalDs.current
    val startedAt by rememberSaveable { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var seconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(startedAt) {
        while (isActive) {
            seconds = ((SystemClock.elapsedRealtime() - startedAt) / 1_000L).coerceAtLeast(0L)
            delay(1_000L)
        }
    }
    // Compose 无限动画遵循系统动画时长设置；没有可度量进度时不显示百分比。
    val rotation by rememberInfiniteTransition(label = "scheduleLoading").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scheduleLoadingRotation",
    )
    AppCard(modifier.fillMaxWidth(), insideMargin = PaddingValues(AppUiTokens.CardPadding)) {
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                liveRegion = LiveRegionMode.Polite
            },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MiuixIcons.Refresh, contentDescription = null, tint = ds.accent,
                modifier = Modifier.size(28.dp).graphicsLayer { rotationZ = rotation })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = ds.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, color = ds.sub, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
        Text("已等待 $seconds 秒", color = ds.sub, fontSize = 13.sp,
            modifier = Modifier.padding(top = 16.dp))
        if (onContinue != null) {
            TextButton("后台继续，先用其他功能", onClick = onContinue,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp))
        }
        TextButton("取消读取", onClick = onCancel,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp))
    }
}
