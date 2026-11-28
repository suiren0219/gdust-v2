package com.haooz.nexioui.capsuleslider

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 选中胶囊的运动控制器：左右两条边界各跑一条弹簧。
 *
 * - 拖动时两条边跟随手指，并按运动方向拉出拖尾；
 * - 吸附时「前边快、后边慢」，宽度中途被撑开再收回，橡皮筋感来自两条线的速度差。
 *
 * 位置只在 layout 期读取（见 [capsuleBounds]），组合期不碰任何高频值。
 *
 * @param scope 驱动帧循环的作用域（通常为 `rememberCoroutineScope()`）。
 * @param widthPx 胶囊静止宽度（一格 tab 减去两侧内缩）；tab 数变化时整体重建，不做插值。
 * @param startLeftPx 初始左边界。
 * @param leftLimit 胶囊左边界允许的范围（含拖尾溢出后的夹限）。
 */
class CapsuleSelector(
    private val scope: CoroutineScope,
    private val widthPx: Float,
    startLeftPx: Float,
    private val leftLimit: () -> ClosedRange<Float>,
) {

    private val left = Channel(startLeftPx)
    private val right = Channel(startLeftPx + widthPx)

    /**
     * 拖动期的收尾弹簧：临界阻尼、0.15s 响应。比 1:1 直读软一档，
     * 手指停下时胶囊还会往前蹭一点点，这就是跟手的质感。
     */
    private val dragStiffness = omega(0.15f) * omega(0.15f)
    private val dragDamping = 2f * 1f * omega(0.15f)

    private var running = false

    val leftPx: Float get() = left.value
    val rightPx: Float get() = right.value

    /** 视觉中心。按在滑块上、松手吸附最近槽位都用它，比逻辑 index 准。 */
    val centerPx: Float get() = (left.value + right.value) * 0.5f

    /**
     * 跟手拖动。[leftPx] 是胶囊静止左边界（不含拖尾），[trailPx] 是尾巴拉伸量，
     * [dir] 为屏幕方向（>0 向右）。只往运动反方向那一侧长：向右拖长左边，向左拖长右边。
     */
    fun onDrag(leftPx: Float, trailPx: Float, dir: Int) {
        left.useSpring(dragStiffness, dragDamping)
        right.useSpring(dragStiffness, dragDamping)
        val range = leftLimit()
        left.target = (leftPx - if (dir > 0) trailPx else 0f).coerceIn(range)
        right.target = (leftPx + if (dir < 0) trailPx else 0f).coerceIn(range) + widthPx
        ensureLoop()
    }

    /**
     * 收尾 / 切换：两条边分别用快慢弹簧追目标。
     *
     * @param forward 目标在胶囊当前所在位置的右侧（几何方向，与 RTL 无关）。
     */
    fun settleTo(leftPx: Float, forward: Boolean) {
        val targetLeft = leftPx.coerceIn(leftLimit())
        val targetRight = targetLeft + widthPx
        if (targetLeft == left.target && targetRight == right.target) return

        // 快边先到、慢边后到 —— 宽度中途被撑开再收回，橡皮筋感就是这两条线速度差造出来的
        if (forward) {
            right.useLeading()
            left.useTrailing()
        } else {
            left.useLeading()
            right.useTrailing()
        }
        left.target = targetLeft
        right.target = targetRight
        ensureLoop()
    }

    /** 停在当前位置、速度归零，等下一次 [settleTo] 重新吸附。 */
    fun freeze() {
        left.freeze()
        right.freeze()
    }

    private fun ensureLoop() {
        if (running) return
        running = true
        scope.launch {
            var lastNanos = 0L
            while (isActive) {
                val nanos = withFrameNanos { it }
                val rawDt = if (lastNanos == 0L) 1f / 60f else (nanos - lastNanos) / 1_000_000_000f
                lastNanos = nanos
                // 掉帧时 dt 不能太大，否则显式积分会发散
                val dt = rawDt.coerceIn(1f / 240f, 1f / 30f)
                if (!step(dt)) break
            }
            running = false
            // 退出瞬间目标又被改过（松手与点按几乎同帧）时补一轮，否则会停在半路
            if (left.needsTick() || right.needsTick()) ensureLoop()
        }
    }

    private fun step(dt: Float): Boolean {
        val leftActive = left.advance(dt)
        val rightActive = right.advance(dt)
        return leftActive || rightActive
    }

    private class Channel(start: Float) {
        var value by mutableFloatStateOf(start)
        var target = start
        private var velocity = 0f
        private var stiffness = LeadingStiffness
        private var damping = LeadingDamping

        fun useLeading() {
            stiffness = LeadingStiffness
            damping = LeadingDamping
        }

        fun useTrailing() {
            stiffness = TrailingStiffness
            damping = TrailingDamping
        }

        fun useSpring(stiffness: Float, damping: Float) {
            this.stiffness = stiffness
            this.damping = damping
        }

        fun needsTick(): Boolean =
            abs(value - target) > SettlePx || abs(velocity) > SettleVelocity

        fun freeze() {
            target = value
            velocity = 0f
        }

        /** 半隐式欧拉，按 [MaxStep] 切子步；返回是否还在动。 */
        fun advance(dt: Float): Boolean {
            val steps = ceil(dt / MaxStep).toInt().coerceAtLeast(1)
            val h = dt / steps
            repeat(steps) {
                val accel = (target - value) * stiffness - velocity * damping
                velocity += accel * h
                value += velocity * h
            }
            if (!needsTick()) {
                value = target
                velocity = 0f
                return false
            }
            return true
        }
    }
}

/** 快弹簧：位移方向先到的那条边。 */
private const val LeadingStiffness = 260f
private val LeadingDamping = 2f * 0.72f * sqrt(LeadingStiffness)

/** 慢弹簧：落后那条边，拉伸感的来源。 */
private const val TrailingStiffness = 165f
private val TrailingDamping = 2f * 0.8f * sqrt(TrailingStiffness)

/** 积分子步上限，保证显式积分在高刚度下不发散。 */
private const val MaxStep = 1f / 240f

/** 收敛判定：位置（px）与速度（px/s）。 */
private const val SettlePx = 0.05f
private const val SettleVelocity = 2f

/** response（秒）→ 弹簧固有角频率。 */
private fun omega(responseSeconds: Float): Float = 6.2831855f / responseSeconds

/**
 * 把子项按「左边界 + 宽度」放进父级；宽度与位置都在 **layout 期**读 [CapsuleSelector] 的状态，
 * 组合期不读任何高频值，只触发该节点的 relayout。
 *
 * @param heightPx 胶囊高度（px）。
 */
fun Modifier.capsuleBounds(
    selector: CapsuleSelector,
    heightPx: Int,
): Modifier = layout { measurable, constraints ->
    val left = selector.leftPx.roundToInt()
    val width = (selector.rightPx - selector.leftPx).roundToInt().coerceAtLeast(1)
    val height = if (constraints.hasBoundedHeight) {
        heightPx.coerceAtMost(constraints.maxHeight)
    } else {
        heightPx
    }
    val placeable = measurable.measure(Constraints.fixed(width, height))
    val reportWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else width
    val reportHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else height
    layout(reportWidth, reportHeight) {
        placeable.place(left, ((reportHeight - height) / 2).coerceAtLeast(0))
    }
}
