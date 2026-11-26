// 移植自 COUI Kit 的 CouiSwitchMotion（third_party/coui-kit, Apache-2.0）。

package com.haooz.nexioui.switch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlin.math.abs

/** COUI 开关状态机：受控值同步 + 切换外观；拖拽 / 吸附交给 [CouiSwitchDrag]。 */
internal class CouiSwitchMotion(
    initial: Boolean,
    val travel: Float,
    private val rtl: Boolean,
    scope: CoroutineScope,
) {
    private var observed = initial
    private var completedAttachment: Boolean? = null
    private val toggle = CouiSpring(endpoint(initial, travel, rtl), scope, minimumChange = .1f)
    private val drag = CouiSwitchDrag(
        travel,
        scope,
        onPositionChanged = { updatePosition(it, toggling = false) },
        onAttached = ::finishAttachment,
    )

    var translation by mutableFloatStateOf(endpoint(initial, travel, rtl).toInt().toFloat())
        private set
    var stretch by mutableFloatStateOf(1f)
        private set
    var fraction by mutableFloatStateOf(if (initial) 1f else 0f)
        private set
    var settled by mutableIntStateOf(0)
        private set
    var onCommit: (Boolean) -> Unit = {}

    fun update(checked: Boolean, enabled: Boolean) {
        if (checked != observed) {
            observed = checked
            if (completedAttachment == checked) {
                // 吸附提交由原厂分支处理，不再启动普通切换弹簧。
                completedAttachment = null
                snap(checked)
            } else {
                animateToggle(checked)
            }
        } else if (!enabled && drag.isActive) {
            cancel()
        }
    }

    fun begin(x: Float, y: Float): Boolean {
        if (toggle.isRunning || drag.isActive) return false
        completedAttachment = null
        drag.begin(translation, x, y)
        stretch = 1f
        return true
    }

    fun drag(x: Float, y: Float) = drag.dragTo(x, y)

    fun attach(inputVelocity: Float, inputVelocityY: Float) = drag.attach(inputVelocity, inputVelocityY)

    fun reconcile(checked: Boolean) {
        if (completedAttachment == checked) {
            completedAttachment = null
            return
        }
        completedAttachment = null
        if (!toggle.isRunning && !drag.isActive && abs(translation - endpoint(checked, travel, rtl)) >= .1f) {
            animateToggle(checked)
        }
    }

    fun cancel() {
        completedAttachment = null
        snap(observed)
    }

    /** 点击未拖动：只清理拖拽状态，不 snap 归位，交给 update 的 toggle 弹簧接续。 */
    fun cancelDrag() {
        completedAttachment = null
        drag.cancel()
        stretch = 1f
    }

    fun dispose() {
        toggle.cancelComplete()
        drag.cancel()
    }

    private fun finishAttachment() {
        stretch = 1f
        val checked = (translation >= travel.toInt() / 2) != rtl
        completedAttachment = checked
        onCommit(checked)
        settled++
    }

    private fun animateToggle(checked: Boolean) {
        drag.cancel()
        if (!toggle.isRunning) toggle.snapTo(translation)
        toggle.animateTo(
            endpoint(checked, travel, rtl),
            SwitchDefaults.toggleResponse,
            SwitchDefaults.toggleBounce,
            onFrame = { updatePosition(it, toggling = true) },
            onEnd = { stretch = 1f; settled++ },
        )
    }

    private fun updatePosition(value: Float, toggling: Boolean) {
        translation = value.toInt().toFloat()
        val p = if (toggling) logical(value, travel, rtl) else logical(translation, travel, rtl)
        fraction = p.coerceIn(0f, 1f)
        // toggle 过程中 thumb 横向拉伸到 1.3 倍再收回，拖手时保持不变形。
        stretch = if (toggling) {
            if (p < .34725848f) 1f + p / .34725848f * .29999995f
            else 1.3f - (p - .34725848f) / .65274155f * .29999995f
        } else 1f
    }

    private fun snap(checked: Boolean) {
        dispose()
        toggle.snapTo(endpoint(checked, travel, rtl))
        translation = endpoint(checked, travel, rtl).toInt().toFloat()
        fraction = if (checked) 1f else 0f
        stretch = 1f
    }

    private companion object {
        fun endpoint(checked: Boolean, travel: Float, rtl: Boolean) = if (checked != rtl) travel else 0f
        fun logical(value: Float, travel: Float, rtl: Boolean) = if (rtl) 1f - value / travel else value / travel
    }
}
