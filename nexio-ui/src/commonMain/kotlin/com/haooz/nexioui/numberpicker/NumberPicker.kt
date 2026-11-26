package com.haooz.nexioui.numberpicker

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.lerp
import com.haooz.nexioui.text.NexioText
import com.haooz.nexioui.theme.NexioTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 滚轮数字选择器：竖向滑动，选中项居中，越远离中心字越淡、越小。
 *
 * @param value 当前值；超出 [range] 时按边界处理。
 * @param onValueChange 选中值变化回调。
 * @param modifier 修饰符。
 * @param enabled 是否可交互。
 * @param range 可选值范围。
 * @param label 数值到显示文本的转换。
 * @param visibleItemCount 可见项数量，必须为不小于 3 的奇数。
 * @param wrapAround 是否首尾循环。
 * @param colors 配色。
 * @param textStyle 文字样式。
 * @param itemHeight 单项高度。
 */
@Composable
fun NumberPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    range: IntRange = 0..10,
    label: (Int) -> String = { it.toString() },
    visibleItemCount: Int = 5,
    wrapAround: Boolean = false,
    colors: NumberPickerColors = NumberPickerDefaults.colors(),
    textStyle: TextStyle = NexioTheme.textStyles.title1,
    itemHeight: Dp = NumberPickerDefaults.ItemHeight,
) {
    require(visibleItemCount % 2 == 1 && visibleItemCount >= 3) {
        "visibleItemCount must be odd and at least 3, but was $visibleItemCount"
    }
    require(range.first <= range.last) {
        "range must not be empty"
    }

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val latestValue by rememberUpdatedState(value.coerceIn(range))
    val itemCount = range.last - range.first + 1
    val coercedValue = value.coerceIn(range)
    val currentIndex = coercedValue - range.first
    val halfVisibleCount = visibleItemCount / 2
    val hapticFeedback = LocalHapticFeedback.current

    // 中心项的虚拟位置，可带小数。base 承载惯性与吸附动画，dragDelta 承载拖拽实时位移。
    // 用绝对位置而非「相对已选中项的偏移」，使提交选中值与视觉位置解耦，动画中途提交也不会跳。
    val base = remember { Animatable(currentIndex.toFloat()) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isSettling by remember { mutableStateOf(false) }
    var itemHeightPx by remember { mutableIntStateOf(0) }

    val position by remember { derivedStateOf { base.value + dragDelta } }

    // 跨过 item 边界时的触感，由拖拽回调与动画逐帧回调直接触发。
    // 不用 snapshotFlow：它只发信号不携带数值，两次变化合并到同一帧时会漏掉一次。
    var lastHapticIndex by remember { mutableIntStateOf(currentIndex) }

    fun tickHaptic(rawIndex: Int) {
        if (rawIndex != lastHapticIndex) {
            lastHapticIndex = rawIndex
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // 把虚拟位置换算成 range 内的真实值并回调
    fun commitPosition(pos: Float) {
        val rawIndex = pos.roundToInt()
        val newIndex = if (wrapAround) {
            ((rawIndex % itemCount) + itemCount) % itemCount
        } else {
            rawIndex.coerceIn(0, itemCount - 1)
        }
        val newValue = range.first + newIndex
        if (newValue != latestValue) {
            currentOnValueChange(newValue)
        }
    }

    // 把惯性落点吸附到最近的整格
    fun snapToItem(projected: Float): Float {
        val rounded = projected.roundToInt().toFloat()
        return if (wrapAround) rounded else rounded.coerceIn(0f, (itemCount - 1).toFloat())
    }

    // 外部改动 value 或 range 变化时同步位置
    LaunchedEffect(coercedValue, itemCount, wrapAround) {
        if (wrapAround) {
            base.updateBounds(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)
        } else {
            base.updateBounds(0f, (itemCount - 1).toFloat())
        }
        if (!isDragging && !isSettling) {
            dragDelta = 0f
            base.snapTo(currentIndex.toFloat())
            lastHapticIndex = currentIndex
        }
    }

    val draggableState = rememberDraggableState { delta ->
        if (itemHeightPx > 0) {
            val raw = base.value + dragDelta - delta / itemHeightPx
            dragDelta = if (wrapAround) {
                raw - base.value
            } else {
                raw.coerceIn(0f, (itemCount - 1).toFloat()) - base.value
            }
            val currentPosition = base.value + dragDelta
            // 拖拽过程中实时提交，手指未抬起时确认也能拿到正确值
            commitPosition(currentPosition)
            tickHaptic(currentPosition.roundToInt())
        }
    }

    val displayValue = label(coercedValue)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(itemHeight * visibleItemCount)
            .clipToBounds()
            .semantics {
                contentDescription = "$displayValue, ${range.first} - ${range.last}"
            }
            .onSizeChanged { size ->
                itemHeightPx = size.height / visibleItemCount
            }
            .then(
                if (enabled) {
                    Modifier.draggable(
                        orientation = Orientation.Vertical,
                        state = draggableState,
                        onDragStarted = {
                            isDragging = true
                            // 打断进行中的动画，从当前视觉位置继续拖
                            base.stop()
                            dragDelta = 0f
                        },
                        onDragStopped = { velocity ->
                            isDragging = false
                            if (itemHeightPx > 0) {
                                // 把拖拽位移并入动画基准
                                val startPosition = base.value + dragDelta
                                dragDelta = 0f
                                base.snapTo(startPosition)

                                val velocityInItems = -velocity / itemHeightPx
                                val decay = exponentialDecay<Float>(
                                    frictionMultiplier = NumberPickerDefaults.FlingFrictionMultiplier,
                                    absVelocityThreshold = NumberPickerDefaults.FlingVelocityThreshold,
                                )
                                // 先算出惯性落点并立即提交，用户提前确认也能拿到最终停留项
                                val target = snapToItem(
                                    decay.calculateTargetValue(startPosition, velocityInItems),
                                )
                                isSettling = true
                                commitPosition(target)
                                try {
                                    val decayResult = base.animateDecay(velocityInItems, decay) {
                                        tickHaptic(base.value.roundToInt())
                                    }
                                    // 用惯性结束时的残余速度衔接吸附动画，避免滑行与吸附之间顿挫。
                                    // 不能读 base.velocity：动画结束后它会被重置为 0。
                                    base.animateTo(
                                        targetValue = target,
                                        animationSpec = NumberPickerDefaults.SnapSpring,
                                        initialVelocity = decayResult.endState.velocity,
                                    ) {
                                        tickHaptic(base.value.roundToInt())
                                    }
                                    // 循环模式下位置持续累加，归一化避免长时间滚动后精度下降
                                    if (wrapAround && !isDragging) {
                                        val normalized =
                                            ((base.value.roundToInt() % itemCount) + itemCount) % itemCount
                                        base.snapTo(normalized.toFloat())
                                        lastHapticIndex = normalized
                                    }
                                } finally {
                                    if (!isDragging) isSettling = false
                                }
                            }
                        },
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (itemHeightPx > 0) {
            val currentPosition = position
            val roundedOffset = currentPosition.roundToInt()
            val centerItemOffset = currentPosition - roundedOffset
            val selectedColor = colors.selectedTextColor(enabled)
            val unselectedColor = colors.unselectedTextColor(enabled)
            val resolvedTextStyle = if (textStyle.fontWeight == null) {
                textStyle.copy(fontWeight = FontWeight.SemiBold)
            } else {
                textStyle
            }

            for (i in -halfVisibleCount - 1..halfVisibleCount + 1) {
                val rawItemIndex = roundedOffset + i
                val itemIndex = if (wrapAround) {
                    ((rawItemIndex % itemCount) + itemCount) % itemCount
                } else {
                    if (rawItemIndex !in 0..<itemCount) continue
                    rawItemIndex
                }

                val distanceFromCenter = i.toFloat() - centerItemOffset
                val normalizedDistance = (abs(distanceFromCenter) / (halfVisibleCount + 0.5f)).coerceIn(0f, 1f)

                val alpha = (1f - normalizedDistance) * (1f - normalizedDistance * 0.5f)
                val scale = 1f - 0.2f * normalizedDistance
                val yOffset = distanceFromCenter * itemHeightPx

                val textColor = Color(
                    red = lerp(selectedColor.red, unselectedColor.red, normalizedDistance),
                    green = lerp(selectedColor.green, unselectedColor.green, normalizedDistance),
                    blue = lerp(selectedColor.blue, unselectedColor.blue, normalizedDistance),
                    alpha = lerp(selectedColor.alpha, unselectedColor.alpha, normalizedDistance),
                )

                NexioText(
                    text = label(range.first + itemIndex),
                    modifier = Modifier
                        .graphicsLayer {
                            this.alpha = alpha
                            scaleX = scale
                            scaleY = scale
                            translationY = yOffset
                        },
                    style = resolvedTextStyle,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
