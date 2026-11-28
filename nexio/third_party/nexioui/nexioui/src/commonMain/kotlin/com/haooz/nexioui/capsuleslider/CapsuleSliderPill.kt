package com.haooz.nexioui.capsuleslider

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import com.haooz.softglass.shape.ContinuousCapsule

/**
 * 胶囊滑块本体：唯一绘制实现。
 *
 * 位置与宽度在 layout 期读（[capsuleBounds]），组合期不碰高频值；
 * 按压缩放走 `graphicsLayer`，绘制期读一次按压进度，不进组合、不触发 layout。
 * 只收缩、不压暗 —— 没有水波纹、没有遮罩层。
 *
 * @param heightPx 胶囊高度（px）。
 * @param pressProgress 按压进度 0..1。
 * @param visibility 额外透明度（形变中途才开始显形时用），默认全不透明。
 */
@Composable
fun Modifier.capsuleSliderPill(
    selector: CapsuleSelector,
    heightPx: Int,
    pressProgress: () -> Float,
    visibility: () -> Float = { 1f },
): Modifier = this
    .capsuleBounds(selector, heightPx)
    .graphicsLayer {
        val progress = pressProgress()
        if (progress > 0f && size.width > 0f && size.height > 0f) {
            val shrink = CapsuleSlider.PressShrink.toPx() * 2f
            val targetX = ((size.width - shrink) / size.width).coerceAtLeast(0.5f)
            val targetY = ((size.height - shrink) / size.height).coerceAtLeast(0.5f)
            scaleX = 1f - (1f - targetX) * progress
            scaleY = 1f - (1f - targetY) * progress
        }
        val v = visibility()
        if (v < 1f) alpha = v
    }
    .clip(ContinuousCapsule())
    .background(CapsuleSlider.color())
