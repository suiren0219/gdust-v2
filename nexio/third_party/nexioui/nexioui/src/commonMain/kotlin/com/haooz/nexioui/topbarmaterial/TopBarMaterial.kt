package com.haooz.nexioui.topbarmaterial

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.anim.folmeSpring
import com.haooz.nexioui.overscroll.LocalOverScrollState
import com.haooz.nexioui.overscroll.OverScrollState
import com.haooz.nexioui.topappbar.SharedScrollBehavior

/** 顶栏材质出现的唯一阈值：内容滚过 10dp。 */
val TopBarMaterialThreshold: Dp = 10.dp

/**
 * 本页顶栏的材质状态（由页面壳下发）。
 *
 * 头部组件（搜索框、分段胶囊）挂在顶栏下方，必须读同一个实例才能与返回键同帧出现；
 * 各自建一份 `Animatable` 必然差一帧。
 */
val LocalTopBarMaterial: ProvidableCompositionLocal<TopBarMaterialState?> =
    compositionLocalOf { null }

/** 材质出现的弹簧（唯一来源）。 */
val TopBarMaterialShowSpec: AnimationSpec<Float> = folmeSpring(damping = 1.0f, response = 0.6f)

/** 材质消失的弹簧（唯一来源）。 */
val TopBarMaterialHideSpec: AnimationSpec<Float> = folmeSpring(damping = 1.0f, response = 0.4f)

/**
 * 一页顶栏的材质状态。
 *
 * 一个页面只能有一个实例：渐变遮罩、按钮玻璃 / 阴影、分段胶囊、搜索框全部读它，
 * 材质才会「一起出现、一起消失」。
 *
 * [alpha] 是绘制期读取的稳定 lambda：逐帧变化只失效绘制，不进组合。
 */
@Stable
class TopBarMaterialState(initiallyVisible: Boolean) {

    internal val animatable = Animatable(if (initiallyVisible) 1f else 0f)

    /** 绘制期读取（`graphicsLayer` / `draw` 阶段）。不要放进组合期分支。 */
    val alpha: () -> Float = { animatable.value }

    /** 当前判定（无动画），供需要在布局期分支的调用方使用。 */
    var visible: Boolean = initiallyVisible
        internal set

    internal suspend fun settleTo(visible: Boolean) {
        this.visible = visible
        animatable.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = if (visible) TopBarMaterialShowSpec else TopBarMaterialHideSpec,
        )
    }
}

/**
 * 判据由调用方给：页面自己有「材质何时出现」的进度时用它。
 *
 * 初值取当前判定 —— 首帧进页面时材质该有就有，不会在滚动位置已经很深的情况下从 0 弹一次。
 */
@Composable
fun rememberTopBarMaterialState(visible: Boolean): TopBarMaterialState {
    val state = remember { TopBarMaterialState(visible) }
    LaunchedEffect(state, visible) {
        state.settleTo(visible)
    }
    return state
}

/**
 * 材质判据本体（唯一一份）：给累计滚动量与越界量，回答「现在该不该有材质」。
 *
 * 两支：
 * 1. 停在顶部向下回弹（`overScrollOffset < 0`）：直接算出现。
 * 2. 内容滚过 [TopBarMaterialThreshold]：`contentOffset` 是累计滚动量，回到顶部才归零。
 *
 * `contentOffset` 会被越界位移污染：回弹时越界节点把 `available` 返给父级，
 * `SharedScrollBehavior.onPostScroll` 照样累加。正值在真实滚动里不可能出现，直接夹到 0 自愈。
 */
fun isTopBarMaterialVisible(
    contentOffset: Float,
    thresholdPx: Float,
    overScrollOffset: Float,
): Boolean {
    if (contentOffset >= 0f && overScrollOffset < 0f) return true
    return contentOffset.coerceAtMost(0f) < -thresholdPx
}

/**
 * 判据由调用方以 lambda 给：页面已有自己的「是否滚起」信号，但还想吃掉「顶部下拉回弹」时用它
 * （lambda 在 `derivedStateOf` 里求值，逐帧变化不会触发重组）。
 */
@Composable
fun rememberTopBarMaterialState(visibleProvider: () -> Boolean): TopBarMaterialState {
    val visible by remember(visibleProvider) { derivedStateOf { visibleProvider() } }
    return rememberTopBarMaterialState(visible)
}

/**
 * 判据由调用方以累计滚动量给：只有 `scrollY`、没有 `SharedScrollBehavior` 的容器用它
 * （传 `contentOffset` 语义，向下滚为负）。越界量仍读本作用域的 `LocalOverScrollState`。
 */
@Composable
fun rememberTopBarMaterialState(
    contentOffsetProvider: () -> Float,
    overScrollState: OverScrollState = LocalOverScrollState.current,
): TopBarMaterialState {
    val thresholdPx = with(LocalDensity.current) { TopBarMaterialThreshold.toPx() }
    val visible by remember(contentOffsetProvider, thresholdPx, overScrollState) {
        derivedStateOf {
            isTopBarMaterialVisible(
                contentOffset = contentOffsetProvider(),
                thresholdPx = thresholdPx,
                overScrollOffset = overScrollState.offset,
            )
        }
    }
    return rememberTopBarMaterialState(visible)
}

/**
 * 标准判据：顶栏材质随内容滚动出现。
 *
 * 要求顶栏与内容在同一个 `LocalOverScrollState` 作用域内，否则「顶部下拉回弹」那一支恒为 false。
 */
@Composable
fun rememberTopBarMaterialState(scrollBehavior: SharedScrollBehavior?): TopBarMaterialState {
    val thresholdPx = with(LocalDensity.current) { TopBarMaterialThreshold.toPx() }
    // 只作身份键：offset 在 derivedStateOf 内部读，本身就会被订阅
    val overScrollState = LocalOverScrollState.current
    val visible by remember(scrollBehavior, thresholdPx, overScrollState) {
        derivedStateOf {
            isTopBarMaterialVisible(
                contentOffset = scrollBehavior?.state?.contentOffset ?: 0f,
                thresholdPx = thresholdPx,
                overScrollOffset = overScrollState.offset,
            )
        }
    }
    return rememberTopBarMaterialState(visible)
}
