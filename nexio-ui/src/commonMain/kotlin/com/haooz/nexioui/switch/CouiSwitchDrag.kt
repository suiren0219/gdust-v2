// 移植自 COUI Kit 的 CouiSwitchDrag（third_party/coui-kit, Apache-2.0）。

package com.haooz.nexioui.switch

import kotlinx.coroutines.CoroutineScope
import kotlin.math.abs

/** COUI 开关拖拽：双锚点吸附，不持有 checked。 */
internal class CouiSwitchDrag(
    private val travel: Float,
    scope: CoroutineScope,
    private val onPositionChanged: (Float) -> Unit,
    private val onAttached: () -> Unit,
) {
    private val horizontal = CouiSpring(0f, scope)
    private val vertical = CouiSpring(0f, scope)
    private var phase = Phase.Idle
    private var start = 0f
    private var downX = 0f
    private var downY = 0f
    private var deltaSatisfied = false

    val isActive: Boolean get() = phase != Phase.Idle

    fun begin(position: Float, x: Float, y: Float) {
        horizontal.snapTo(position)
        vertical.snapTo(0f)
        start = position
        downX = x
        downY = y
        deltaSatisfied = false
        phase = Phase.Drag
    }

    fun dragTo(x: Float, y: Float) {
        if (phase != Phase.Drag) return
        deltaSatisfied = deltaSatisfied || abs(x - downX) >= SwitchDefaults.transformDistancePx ||
            abs(y - downY) >= SwitchDefaults.transformDistancePx
        // 原厂 overDistance 为零，只限制目标，不截断弹簧当前位置。
        val target = (start + x - downX).coerceIn(0f, travel)
        val distance = abs(target - horizontal.value)
        // 保留 dragTo 的两级启动阈值；停稳后重新启动不继承旧速度。
        if (distance >= .01f && (horizontal.isRunning || distance >= 1f)) {
            horizontal.animateTo(
                target,
                SwitchDefaults.dragResponse,
                SwitchDefaults.dragBounce,
                velocity = if (horizontal.isRunning) null else 0f,
                onFrame = onPositionChanged,
            )
        }
    }

    fun attach(inputVelocity: Float, inputVelocityY: Float) {
        if (phase != Phase.Drag) return
        val position = horizontal.value
        val v = inputVelocity.toInt().toFloat()
        val vy = inputVelocityY.toInt().toFloat()
        // 等距时保留第一个锚点；速度够大且确实拖动过才按甩动方向选锚点。
        var target = if (abs(position) <= abs(travel - position)) 0f else travel
        if (abs(v) >= SwitchDefaults.transformVelocityPx && deltaSatisfied) target = if (v > 0f) travel else 0f
        else if (abs(vy) >= SwitchDefaults.transformVelocityPx && deltaSatisfied) target = if (vy > 0f) travel else 0f
        cancel()
        phase = Phase.Attach
        if (abs(target - position) < 1f) {
            finishAttachment()
            return
        }
        horizontal.animateTo(
            target,
            SwitchDefaults.attachResponse,
            SwitchDefaults.attachBounce,
            velocity = inputVelocity.coerceIn(-SwitchDefaults.transformVelocityPx, SwitchDefaults.transformVelocityPx),
            onFrame = onPositionChanged,
            onEnd = ::finishAttachment,
        )
        vertical.snapTo(0f)
        vertical.animateTo(
            0f,
            SwitchDefaults.attachResponse,
            SwitchDefaults.attachBounce,
            velocity = inputVelocityY.coerceIn(-SwitchDefaults.transformVelocityPx, SwitchDefaults.transformVelocityPx),
            onEnd = ::finishAttachment,
        )
    }

    fun cancel() {
        phase = Phase.Idle
        horizontal.cancelComplete()
        vertical.cancelComplete()
    }

    private fun finishAttachment() {
        if (phase != Phase.Attach || horizontal.isRunning || vertical.isRunning) return
        phase = Phase.Idle
        onAttached()
    }

    private enum class Phase { Idle, Drag, Attach }
}
