package com.haooz.nexioui.cardexpand

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.card.CardDefaults
import com.haooz.softglass.platform.isRenderEffectSupported

/** [CardExpand] 的统一参数。 */
object CardExpandDefaults {

    /** 高度补间曲线，与 [CardDefaults.SizeAnimation] 保持同一条 spring。 */
    val HeightSpring: FiniteAnimationSpec<IntSize> = CardDefaults.SizeAnimation

    /** 内容淡入淡出时长（ms），比高度弹簧短，避免「先亮后长」。 */
    const val FadeMillis = 180

    /** 展开 / 收起时的最大模糊半径（dp）。 */
    const val BlurDp = 12f

    /** 展开时模糊散尽时长（ms），比高度长，让高度先到位再收尾变清晰。 */
    const val BlurInMillis = 420

    /** 收起时模糊铺满时长（ms），比高度短，先糊住再缩回。 */
    const val BlurOutMillis = 160
}

/**
 * 卡片内条件出现 / 消失的一段内容。
 *
 * 展开时高度从 0 长到实际尺寸，同时淡入并由模糊变清晰；收起时先淡出、糊满，再压到 0 高度。
 * 高度走 spring，模糊与透明度走固定时长，进慢出快。
 *
 * @param visible 是否展示。
 * @param modifier 外层修饰符。
 * @param expandFrom 展开 / 收缩锚点，默认从顶部；需要内容贴底边变化时传 [Alignment.Bottom]。
 * @param fade 是否淡入淡出。
 * @param content 内容。
 */
@Composable
fun CardExpand(
    visible: Boolean,
    modifier: Modifier = Modifier,
    expandFrom: Alignment.Vertical = Alignment.Top,
    fade: Boolean = true,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(
            animationSpec = CardExpandDefaults.HeightSpring,
            expandFrom = expandFrom,
        ),
        exit = shrinkVertically(
            animationSpec = CardExpandDefaults.HeightSpring,
            shrinkTowards = expandFrom,
        ),
    ) {
        val blurProgress = transition.animateFloat(
            transitionSpec = {
                if (targetState == EnterExitState.Visible) {
                    tween(CardExpandDefaults.BlurInMillis, easing = FastOutSlowInEasing)
                } else {
                    tween(CardExpandDefaults.BlurOutMillis, easing = FastOutSlowInEasing)
                }
            },
            label = "CardExpandBlur",
        ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
        val alphaProgress = transition.animateFloat(
            transitionSpec = { tween(CardExpandDefaults.FadeMillis) },
            label = "CardExpandAlpha",
        ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
        // 只在动画期间挂离屏层：稳定态保留会把超高内容罩进 layer，下半部分无法点击。
        val animating by remember(transition) {
            derivedStateOf { transition.currentState != transition.targetState }
        }
        // AnimatedVisibility 的子内容会被全部摆到 (0,0)，多行必须自己包一层 Column。
        Column(
            modifier = if (animating) {
                Modifier.graphicsLayer {
                    if (fade) alpha = alphaProgress.value
                    val px = CardExpandDefaults.BlurDp.dp.toPx() * (1f - blurProgress.value)
                    renderEffect = if (px > 0.01f && isRenderEffectSupported()) {
                        BlurEffect(px, px, TileMode.Decal)
                    } else {
                        null
                    }
                }
            } else {
                Modifier
            },
        ) {
            content()
        }
    }
}

/**
 * 容器自身测量结果变化时按 [CardDefaults.SizeAnimation] 补间。
 *
 * 只用于自绘容器；普通卡片里条件出现的内容请用 [CardExpand]。
 *
 * @param alignment 尺寸变化时的对齐方向。
 */
fun Modifier.cardContentSize(
    alignment: Alignment = Alignment.TopStart,
): Modifier = animateContentSize(
    animationSpec = CardDefaults.SizeAnimation,
    alignment = alignment,
)
