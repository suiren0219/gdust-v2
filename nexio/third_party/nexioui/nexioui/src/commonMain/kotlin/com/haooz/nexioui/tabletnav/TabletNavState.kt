package com.haooz.nexioui.tabletnav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 平板导航的形态状态。
 *
 * | 字段 | 0 → 1 | 谁读 |
 * |---|---|---|
 * | [expandProgress] | 折叠图标轨 → 完整侧栏 | 面板宽度、内容避让 |
 * | [morphProgress] | 侧栏 → 顶部胶囊 | 面板四边界、圆角、条目形变 |
 * | [landBounce] | 静止 → 顶到底 | 面板底缘额外高度 |
 *
 * 三个进度都**不要在组合期读**，否则整树会随动画每帧重组；
 * layout / draw / graphicsLayer 里可以随便读。
 */
class TabletNavState(
    expanded: Boolean = true,
    topBarMode: Boolean = false,
) {
    /** 展开 = 完整侧栏；折叠 = 仅图标轨。 */
    var expanded by mutableStateOf(expanded)

    /** 顶栏形态：true = 收成顶部胶囊，侧边占位归 0；与 [expanded] 互不干扰。 */
    var topBarMode by mutableStateOf(topBarMode)

    /** 展开进度：0 = 折叠图标轨，1 = 完整侧栏。 */
    val expandProgress = mutableFloatStateOf(if (expanded) 1f else 0f)

    /** 侧栏 → 顶栏胶囊的连续形变进度。 */
    val morphProgress = mutableFloatStateOf(if (topBarMode) 1f else 0f)

    /** 底部落地回弹进度：0 = 静止，1 = 顶到最深。 */
    val landBounce = mutableFloatStateOf(0f)

    /** 二级页点选主 tab 后的待处理下标；-1 = 无。由宿主消费。 */
    var pendingMainTab by mutableStateOf(-1)
}

/**
 * 几何变化的唯一采样信号。
 *
 * 内容区会随展开 / 形变横移，任何按屏幕位置采样的玻璃都要把它并进自己的重绘信号。
 */
fun TabletNavState.samplingSignal() {
    expandProgress.floatValue
    morphProgress.floatValue
}

/**
 * [samplingSignal] 的「有没有变过」版本。
 *
 * 每个消费方各持一个实例，共用会把状态互相吃掉。
 */
class TabletNavSamplingTracker(private val state: TabletNavState) {
    private var lastExpand = Float.NaN
    private var lastMorph = Float.NaN

    fun moved(): Boolean {
        val expand = state.expandProgress.floatValue
        val morph = state.morphProgress.floatValue
        val moved = expand != lastExpand || morph != lastMorph
        lastExpand = expand
        lastMorph = morph
        return moved
    }
}
