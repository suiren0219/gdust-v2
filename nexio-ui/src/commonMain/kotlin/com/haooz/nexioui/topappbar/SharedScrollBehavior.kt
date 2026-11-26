package com.haooz.nexioui.topappbar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.haooz.nexioui.anim.folmeSpring
import kotlin.math.abs

/**
 * 折叠顶栏的滚动行为：把列表的嵌套滚动翻译成顶栏折叠量。
 *
 * 与 [CollapsibleTopAppBarState] 配对使用，同一页面只建一份。
 */
@Stable
class SharedScrollBehavior(
    val state: CollapsibleTopAppBarState,
    val snapAnimationSpec: AnimationSpec<Float> = folmeSpring(damping = 1.0f, response = 0.3f),
    val flingAnimationSpec: DecayAnimationSpec<Float> = exponentialDecay(frictionMultiplier = 1f),
) {
    var currentHeightPx by mutableFloatStateOf(0f)

    var postCollapseScrollOffset by mutableFloatStateOf(0f)
        internal set

    private var connection: NestedScrollConnection? = null

    /** 带动画收起标题栏。 */
    suspend fun collapse() {
        val target = state.heightOffsetLimit
        if (target == 0f) return
        val animatable = Animatable(state.heightOffset)
        animatable.animateTo(target, snapAnimationSpec) {
            state.heightOffset = value
        }
    }

    /** 立即收起标题栏（无动画）。程序化滚动前调用，避免折叠动画与列表滚动抢帧导致二次位移。 */
    fun collapseImmediately() {
        val target = state.heightOffsetLimit
        // 初值 -MAX（大标题尚未测出）或无大标题时的 -1，都不应写入
        if (target >= -1f || target <= -Float.MAX_VALUE / 2f) return
        state.heightOffset = target
    }

    /** 带动画展开标题栏。 */
    suspend fun expand() {
        val animatable = Animatable(state.heightOffset)
        animatable.animateTo(0f, snapAnimationSpec) {
            state.heightOffset = value
        }
    }

    val nestedScrollConnection: NestedScrollConnection
        get() {
            if (connection == null) {
                connection = object : NestedScrollConnection {
                    override fun onPreScroll(
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        if (available.y > 0) {
                            // 向上滚动时不重置 postCollapseScrollOffset，只在回弹结束时由外部重置
                            return Offset.Zero
                        }

                        val currentHeightOffset = state.heightOffset
                        val heightOffsetLimit = state.heightOffsetLimit

                        // 没有大标题的两种形态都必须不消费滚动：
                        // -1f 为显式标记「无大标题」；-Float.MAX_VALUE 为「从未被测量」（例如原生顶栏接管后
                        // Compose 不再组合本组件），此时 maxConsumable 是天文数字，会把上滑位移整段吃掉。
                        val noLargeTitle =
                            heightOffsetLimit >= -1f || heightOffsetLimit <= -Float.MAX_VALUE / 2f

                        if (currentHeightOffset <= heightOffsetLimit || noLargeTitle) {
                            postCollapseScrollOffset += -available.y
                            return Offset.Zero
                        }

                        val maxConsumable = currentHeightOffset - heightOffsetLimit
                        val consumed = maxConsumable.coerceAtMost(-available.y)

                        state.heightOffset = currentHeightOffset - consumed

                        return if (consumed > 0f) Offset(0f, -consumed) else Offset.Zero
                    }

                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        state.contentOffset += consumed.y

                        if (available.y > 0f) {
                            val oldHeightOffset = state.heightOffset
                            state.heightOffset = (oldHeightOffset + available.y).coerceAtMost(0f)
                            val consumedByBar = state.heightOffset - oldHeightOffset
                            return Offset(0f, consumedByBar)
                        }

                        return Offset.Zero
                    }

                    override suspend fun onPostFling(
                        consumed: Velocity,
                        available: Velocity,
                    ): Velocity {
                        // 不在这里清零 contentOffset：触底回弹同样会走这支，一清零顶栏材质就会整块消失。
                        // 回到顶部时 onPostScroll 会把它自然累加回 0。
                        return settleAppBar(
                            state,
                            available.y,
                            flingAnimationSpec,
                            snapAnimationSpec,
                        )
                    }
                }
            }
            return connection!!
        }
}

/**
 * 惯性结束后把顶栏吸附到完全展开或完全折叠。
 */
private suspend fun settleAppBar(
    state: CollapsibleTopAppBarState,
    velocity: Float,
    flingAnimationSpec: DecayAnimationSpec<Float>?,
    snapAnimationSpec: AnimationSpec<Float>?,
): Velocity {
    if (state.collapsedFraction < 0.01f || state.collapsedFraction == 1f) {
        return Velocity.Zero
    }
    var remainingVelocity = velocity

    if (flingAnimationSpec != null && abs(velocity) > 1f) {
        var lastValue = 0f
        AnimationState(initialValue = 0f, initialVelocity = velocity).animateDecay(
            flingAnimationSpec,
        ) {
            val delta = value - lastValue
            val initialHeightOffset = state.heightOffset
            state.heightOffset = initialHeightOffset + delta
            val consumed = abs(initialHeightOffset - state.heightOffset)
            lastValue = value
            remainingVelocity = this.velocity
            if (abs(delta - consumed) > 0.5f) this.cancelAnimation()
        }
    }

    if (snapAnimationSpec != null) {
        if (state.heightOffset < 0 && state.heightOffset > state.heightOffsetLimit) {
            AnimationState(initialValue = state.heightOffset).animateTo(
                if (state.collapsedFraction < 0.5f) {
                    0f
                } else {
                    state.heightOffsetLimit
                },
                animationSpec = snapAnimationSpec,
            ) {
                state.heightOffset = value
            }
        }
    }
    return Velocity(0f, velocity - remainingVelocity)
}

@Composable
fun rememberSharedScrollBehavior(
    state: CollapsibleTopAppBarState = rememberCollapsibleTopAppBarState(),
): SharedScrollBehavior = remember(state) {
    SharedScrollBehavior(state)
}
