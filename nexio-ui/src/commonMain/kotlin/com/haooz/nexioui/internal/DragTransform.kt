package com.haooz.nexioui.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/** 玻璃控件沿拖动方向的拉伸倍率。 */
internal const val GlassDragStretchRatio = 0.012f

/** 玻璃控件拖动位移阻尼。 */
internal const val GlassDragTranslationDamping = 0.032f

private const val VerticalDragRefScale = 0.6f
private const val AspectPenaltyFloor = 0.97f

/** 按压 + 拉伸 + 阻尼位移的计算结果。 */
internal data class DragTransform(
    val scaleX: Float,
    val scaleY: Float,
    val translationX: Float,
    val translationY: Float,
)

/**
 * 按压缩放 + 沿拖动方向拉伸 + tanh 阻尼位移。
 *
 * 天生长条的控件 `shapeAspectRatio` 必须传 `1f`，否则纵向拉伸会被比例惩罚吃掉。
 */
internal fun computeDragTransform(
    width: Float,
    height: Float,
    fraction: Float,
    pressProgress: Float,
    offset: Offset,
    density: Density,
    shapeAspectRatio: Float? = null,
    stretchRatio: Float = GlassDragStretchRatio,
    translationDamping: Float = GlassDragTranslationDamping,
    pressScaleDp: Float = 2f,
): DragTransform {
    val selfH = height.coerceAtLeast(1f)
    val minDim = minOf(width, selfH).coerceAtLeast(1f)
    val pressScale = 1f + with(density) { pressScaleDp.dp.toPx() } / selfH * pressProgress.coerceAtLeast(0f)
    val refY = minDim * (1f - (1f - VerticalDragRefScale) * fraction.coerceIn(0f, 1f))
    val penaltyX = (shapeAspectRatio ?: width / selfH).coerceAtMost(AspectPenaltyFloor)
    val penaltyY = (shapeAspectRatio?.let { 1f / it } ?: (selfH / width)).coerceAtMost(AspectPenaltyFloor)
    val angle = atan2(offset.y, offset.x)
    return DragTransform(
        scaleX = pressScale + stretchRatio * abs(cos(angle) * offset.x / minDim) * penaltyX,
        scaleY = pressScale + stretchRatio * abs(sin(angle) * offset.y / refY) * penaltyY,
        translationX = minDim * tanh(translationDamping * offset.x / minDim),
        translationY = minDim * tanh(translationDamping * offset.y / minDim),
    )
}
