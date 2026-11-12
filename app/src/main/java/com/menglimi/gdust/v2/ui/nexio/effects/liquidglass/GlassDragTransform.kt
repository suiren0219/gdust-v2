// Ported from HaoZai000/NexioSchedule 28a99a3b23458a0d39741b9841c9755f5374fba2 (AGPL-3.0).
// Extracted from miuix/.../interactive/DropdownPanelDragSelect.kt; vendor sources stay unchanged.
package com.menglimi.gdust.v2.ui.nexio.effects.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

private const val DragStretchRatio = 0.045f
private const val DefaultTranslationDamping = 0.08f
private const val DefaultPressScaleDp = 4f
private const val VerticalDragRefScale = 0.6f
private const val AspectPenaltyFloor = 0.97f

internal const val GlassDragStretchRatio = 0.012f
internal const val GlassDragTranslationDamping = 0.032f

internal data class DragTransform(
    val scaleX: Float,
    val scaleY: Float,
    val translationX: Float,
    val translationY: Float,
)

/** The exact upstream press scale, directional stretch and tanh drag damping. */
internal fun computeDragTransform(
    width: Float,
    height: Float,
    fraction: Float,
    pressProgress: Float,
    offset: Offset,
    density: Density,
    shapeAspectRatio: Float? = null,
    stretchRatio: Float = DragStretchRatio,
    translationDamping: Float = DefaultTranslationDamping,
    pressScaleDp: Float = DefaultPressScaleDp,
): DragTransform {
    val selfH = height.coerceAtLeast(1f)
    val minDim = minOf(width, selfH).coerceAtLeast(1f)
    val pressScale = 1f + with(density) { pressScaleDp.dp.toPx() } / selfH * pressProgress.coerceAtLeast(0f)
    val refY = minDim * (1f - (1f - VerticalDragRefScale) * fraction.coerceIn(0f, 1f))
    val base = shapeAspectRatio
    val penaltyX = (base ?: width / selfH).coerceAtMost(AspectPenaltyFloor)
    val penaltyY = (base?.let { 1f / it } ?: (selfH / width)).coerceAtMost(AspectPenaltyFloor)
    val angle = atan2(offset.y, offset.x)
    return DragTransform(
        scaleX = pressScale + stretchRatio * abs(cos(angle) * offset.x / minDim) * penaltyX,
        scaleY = pressScale + stretchRatio * abs(sin(angle) * offset.y / refY) * penaltyY,
        translationX = minDim * tanh(translationDamping * offset.x / minDim),
        translationY = minDim * tanh(translationDamping * offset.y / minDim),
    )
}
