package com.haooz.nexioui.colorpalette

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.color.Hsv
import com.haooz.softglass.shape.ContinuousCapsule
import kotlin.math.roundToInt

private val SliderHeight = 26.dp
private val IndicatorSize = 20.dp
private val CheckerSize = 6.dp

/**
 * HSV 透明度滑条：棋盘格 + 从全透明到不透明的渐变，右端（RTL 为左端）为不透明。
 */
@Composable
fun HsvAlphaSlider(
    currentHue: Float,
    currentSaturation: Float,
    currentValue: Float,
    currentAlpha: Float,
    onAlphaChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onAlphaChangedState = rememberUpdatedState(onAlphaChanged)
    val capsuleShape = ContinuousCapsule()
    val density = LocalDensity.current
    val sliderHeightPx = with(density) { SliderHeight.toPx() }
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    var dragOffset by remember { mutableFloatStateOf(0f) }
    var sliderWidthPx by remember { mutableFloatStateOf(0f) }

    val baseColor = remember(currentHue, currentSaturation, currentValue) {
        Hsv(currentHue, currentSaturation * 100f, currentValue * 100f).toColor()
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(capsuleShape)
            .fillMaxWidth()
            .height(SliderHeight)
            .onSizeChanged { sliderWidthPx = it.width.toFloat() }
            .drawWithCache {
                val widthPx = size.width
                val halfHeightPx = SliderHeight.toPx() / 2f
                val gradientBrush = Brush.horizontalGradient(
                    colors = if (isRtl) {
                        listOf(baseColor.copy(alpha = 1f), baseColor.copy(alpha = 0f))
                    } else {
                        listOf(baseColor.copy(alpha = 0f), baseColor.copy(alpha = 1f))
                    },
                    startX = halfHeightPx,
                    endX = widthPx - halfHeightPx,
                    tileMode = TileMode.Clamp,
                )
                val borderStroke = Stroke(width = 0.5.dp.toPx())
                val borderColor = Color.Gray.copy(alpha = 0.1f)
                val checkerPx = CheckerSize.toPx()

                onDrawBehind {
                    // 棋盘格垫底：半透明区域才看得出「有多透明」
                    var y = 0f
                    var rowIndex = 0
                    while (y < size.height) {
                        var x = 0f
                        var colIndex = 0
                        while (x < size.width) {
                            val gray = if ((rowIndex + colIndex) % 2 == 0) {
                                Color(0xFFB0B0B0)
                            } else {
                                Color(0xFFE8E8E8)
                            }
                            drawRect(
                                color = gray,
                                topLeft = Offset(x, y),
                                size = Size(
                                    minOf(checkerPx, size.width - x),
                                    minOf(checkerPx, size.height - y),
                                ),
                            )
                            x += checkerPx
                            colIndex++
                        }
                        y += checkerPx
                        rowIndex++
                    }
                    drawRect(brush = gradientBrush)
                    drawRect(color = borderColor, style = borderStroke)
                }
            }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    val d = if (isRtl) -delta else delta
                    dragOffset += d
                    val newValue = handleSliderInteraction(
                        dragOffset,
                        sliderWidthPx,
                        sliderHeightPx,
                    ).coerceIn(0f, 1f)
                    onAlphaChangedState.value(newValue)
                },
                onDragStarted = { offset ->
                    val x = if (isRtl) sliderWidthPx - offset.x else offset.x
                    dragOffset = x
                    onAlphaChangedState.value(handleSliderInteraction(x, sliderWidthPx, sliderHeightPx))
                },
            ),
    ) {
        AlphaSliderIndicator(
            modifier = Modifier.align(Alignment.CenterStart),
            alpha = currentAlpha,
            sliderWidth = maxWidth,
            sliderSizePx = sliderHeightPx,
            indicatorSize = IndicatorSize,
        )
    }
}

@Composable
private fun AlphaSliderIndicator(
    alpha: Float,
    sliderWidth: Dp,
    sliderSizePx: Float,
    indicatorSize: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .offset {
                val effectiveWidthPx = sliderWidth.toPx() - sliderSizePx
                val indicatorPositionPx = (alpha * effectiveWidthPx) + (sliderSizePx / 2)
                val indicatorOffsetXDp = indicatorPositionPx.toDp() - (indicatorSize / 2)
                IntOffset(indicatorOffsetXDp.roundToPx(), 0)
            }
            .size(indicatorSize)
            .drawWithCache {
                val strokeWidth = 6.dp.toPx()
                val halfStroke = strokeWidth / 2f
                val glowSpread = 2.dp.toPx()
                val glowColor = Color.Black.copy(alpha = 0.25f)

                val ringCenterRadius = (size.minDimension / 2f) - halfStroke
                val gradientRadius = ringCenterRadius + halfStroke + glowSpread

                val glowBrush = Brush.radialGradient(
                    colorStops = listOf(
                        ((ringCenterRadius - halfStroke - glowSpread).coerceAtLeast(0f) / gradientRadius) to Color.Transparent,
                        ((ringCenterRadius - halfStroke) / gradientRadius) to glowColor,
                        ((ringCenterRadius + halfStroke) / gradientRadius) to glowColor,
                        ((ringCenterRadius + halfStroke + glowSpread) / gradientRadius) to Color.Transparent,
                    ).toTypedArray(),
                    radius = gradientRadius,
                )

                onDrawBehind {
                    drawCircle(brush = glowBrush, radius = gradientRadius)
                    drawCircle(color = Color.White, radius = ringCenterRadius, style = Stroke(width = strokeWidth))
                }
            },
    )
}

/** 拖动横坐标 → 归一化进度；两端按半个滑块宽度夹紧，保证圆环不出界。 */
private fun handleSliderInteraction(
    positionX: Float,
    totalWidth: Float,
    sliderSizePx: Float,
): Float {
    val sliderHalfSizePx = sliderSizePx / 2
    val effectiveWidth = totalWidth - sliderSizePx
    if (effectiveWidth <= 0f) return 0f
    val constrainedX = positionX.coerceIn(sliderHalfSizePx, totalWidth - sliderHalfSizePx)
    val newPosition = (constrainedX - sliderHalfSizePx) / effectiveWidth
    return newPosition.coerceIn(0f, 1f)
}
