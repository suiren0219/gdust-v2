package com.haooz.nexioui.capsuleslider

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs

/** 胶囊手势快甩速度采样：分段控件 / 底栏 / 顶栏共用。 */
class CapsuleFlingTracker(
    private val downUptimeMillis: Long,
    downX: Float,
) {
    private val velocityTracker = VelocityTracker()
    private var lastMoveUptime = downUptimeMillis
    private var lastTotalDx = 0f

    init {
        velocityTracker.addPosition(downUptimeMillis, Offset(downX, 0f))
    }

    /** 每帧 move 调用一次；[downX] 为按下横坐标。 */
    fun onMove(uptimeMillis: Long, position: Offset, downX: Float) {
        lastMoveUptime = uptimeMillis
        lastTotalDx = position.x - downX
        velocityTracker.addPosition(uptimeMillis, position)
    }

    /** 净位移（px）。 */
    val totalDx: Float get() = lastTotalDx

    /** 松手瞬时速度（px/s）。 */
    fun velocityX(): Float {
        val spanMs = (lastMoveUptime - downUptimeMillis).coerceAtLeast(16L)
        val avg = lastTotalDx * 1000f / spanMs
        val tracked = runCatching { velocityTracker.calculateVelocity().x }.getOrDefault(0f)
        return when {
            avg != 0f && tracked != 0f && (avg > 0f) != (tracked > 0f) -> avg
            abs(avg) > abs(tracked) -> avg
            else -> tracked
        }
    }

    /** 拖动且速度达标时返回屏幕方向（-1 左 / +1 右），否则 0。 */
    fun flingDirection(thresholdPxPerSec: Float, touchSlop: Float): Int {
        if (abs(lastTotalDx) <= touchSlop) return 0
        val vx = velocityX()
        if (abs(vx) < thresholdPxPerSec) return 0
        return if (vx > 0f) 1 else -1
    }
}
