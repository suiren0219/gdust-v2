// KMP 等价的 COUI SpringAnimation：持续运行的物理弹簧，animateToFinalPosition 只改目标不重启。

package com.haooz.nexioui.switch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * COUI 弹簧：与 AndroidX SpringAnimation 同语义；运行中 [animateTo] 只更新目标与速度、不重启协程，高频拖动不打断跟手。
 *
 * @param initial 初始值。
 * @param scope 驱动弹簧的作用域。
 * @param minimumChange 默认收敛阈值。
 * @param onUpdate 每帧位置变化后的附加回调。
 */
internal class CouiSpring(
    initial: Float,
    private val scope: CoroutineScope,
    private val minimumChange: Float = 1f,
    private val onUpdate: () -> Unit = {},
) {
    private var position by mutableFloatStateOf(initial)
    val value: Float get() = position

    var velocity: Float = 0f
        private set

    private var running = false
    val isRunning: Boolean get() = running

    private var target = initial
    private var stiffness = 0f
    private var dampingRatio = 1f
    private var threshold = minimumChange
    private var onFrame: ((Float) -> Unit)? = null
    private var onEnd: (() -> Unit)? = null
    private var job: Job? = null
    private var generation = 0

    /**
     * 启动弹簧，或把运行中的弹簧重定向到 [target]。
     *
     * @param response 固有振荡周期（秒）；stiffness = (2π/response)²。
     * @param bounce 回弹幅度；dampingRatio = 1 - bounce。
     * @param velocity 起始速度；null 表示运行中保留当前速度。
     */
    fun animateTo(
        target: Float,
        response: Float,
        bounce: Float = 0f,
        minimumChange: Float = this.minimumChange,
        velocity: Float? = null,
        onFrame: ((Float) -> Unit)? = null,
        onEnd: (() -> Unit)? = null,
    ) {
        this.target = target
        this.stiffness = ((2.0 * PI / response) * (2.0 * PI / response)).toFloat()
        this.dampingRatio = 1f - bounce
        this.threshold = minimumChange
        this.onFrame = onFrame
        this.onEnd = onEnd
        // 与 SpringAnimation 一致：运行中只改目标，速度交给物理继续积分。
        if (velocity != null) this.velocity = velocity
        else if (!running) this.velocity = 0f
        if (!running) {
            running = true
            val token = ++generation
            job = scope.launch { runSpring(token) }
        }
    }

    private suspend fun runSpring(token: Int) {
        var lastNanos = withFrameNanos { it }
        while (running && token == generation) {
            val nanos = withFrameNanos { it }
            if (token != generation) return
            val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0f, MaxFrameSeconds)
            lastNanos = nanos
            integrate(dt)
            onFrame?.invoke(position)
            onUpdate()
            if (abs(position - target) < threshold && abs(velocity) < threshold) {
                position = target
                velocity = 0f
                running = false
                job = null
                onFrame?.invoke(target)
                onEnd?.invoke()
                break
            }
        }
    }

    /**
     * 四阶龙格-库塔，与 SpringAnimation 同阶。
     * 显式欧拉只在 dt < 2/(2ζω) 时稳定：拖拽弹簧（response .3 / bounce 0）上限约 48ms，
     * 卡一帧就会被放大成反向的巨幅摆动，也就是打断后的大回弹。
     */
    private fun integrate(dt: Float) {
        if (dt <= 0f) return
        val damping = 2f * dampingRatio * sqrt(stiffness)
        fun accel(x: Float, v: Float) = -stiffness * (x - target) - damping * v
        val x = position
        val v = velocity
        val k1v = accel(x, v)
        val k2x = v + 0.5f * dt * k1v
        val k2v = accel(x + 0.5f * dt * v, k2x)
        val k3x = v + 0.5f * dt * k2v
        val k3v = accel(x + 0.5f * dt * k2x, k3x)
        val k4x = v + dt * k3v
        val k4v = accel(x + dt * k3x, k4x)
        position = x + dt / 6f * (v + 2f * k2x + 2f * k3x + k4x)
        velocity = v + dt / 6f * (k1v + 2f * k2v + 2f * k3v + k4v)
    }

    /** 立即跳到 [next]，清除速度与结束回调。 */
    fun snapTo(next: Float) {
        cancelComplete()
        position = next
    }

    /** 取消动画但保留当前位置。 */
    fun cancel() {
        generation++
        onFrame = null
        onEnd = null
        running = false
        job?.cancel()
        job = null
    }

    /** 取消动画并清除速度。 */
    fun cancelComplete() {
        cancel()
        velocity = 0f
    }

    private companion object {
        /** 与 DynamicAnimation.MAX_DELTA_MS 一致，后台挂起后的超长帧不参与积分。 */
        const val MaxFrameSeconds = 0.064f
    }
}
