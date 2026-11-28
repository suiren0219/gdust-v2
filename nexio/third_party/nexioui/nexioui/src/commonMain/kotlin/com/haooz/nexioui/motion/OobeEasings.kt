/*
 * 缓动与时序常量。
 *
 * 类名沿用「Oobe」前缀只是历史遗留 —— 它们从 HyperOS 开机引导的动效中提取，
 * 现在被各类页面转场直接使用。
 *
 * Animation timings are derived from HyperCeiler provision at commit
 * 7266aaa0d698ad10795381c5bf23651c2e1719d0.
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.haooz.nexioui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 二次方缓出。 */
val OobeQuadraticOutEasing = Easing { fraction ->
    1f - (1f - fraction) * (1f - fraction)
}

/** 三次方缓出。 */
val OobeCubicOutEasing = Easing { fraction ->
    1f - (1f - fraction) * (1f - fraction) * (1f - fraction)
}

/** 四次方（4.5 次）缓出，起步快、末段缓。 */
val OobeQuartOutEasing = Easing { fraction ->
    val inverse = 1f - fraction
    1f - inverse.pow(4.5f)
}

/** [OobeQuartOutEasing] 的温和起步变体：Hermite 把起点速度压为 0，中后段保持原强减速 */
val OobeQuartOutSoftStartEasing = Easing { fraction ->
    val t = fraction.coerceIn(0f, 1f)
    val k = 0.48f
    val shaped = if (t <= k) {
        // shaped(0)=0, shaped'(0)=0, shaped(k)=k, shaped'(k)=1
        -t * t * t / (k * k) + 2f * t * t / k
    } else {
        t
    }
    val inverse = 1f - shaped
    1f - inverse.pow(4.5f)
}

/** 二级页「出场」缓动：起步更缓、后段正常滑出。 */
val SecondaryPageExitEasing = CubicBezierEasing(0.36f, 0.18f, 0.3f, 0.85f)

/** 整页入场/退场、缩放、盖层、弹窗让位共用的缓动。 */
val CustomizePageEasing = CubicBezierEasing(0.3f, 0.5f, 0.2f, 1.0f)

/**
 * 整屏截图当封面盖上去后，随主内容一起缩进洞里的时序。
 * 页面元素入场动画必须整体收尾在 [CUSTOMIZE_COVER_WINDOW_MILLIS] 之内，
 * 否则出现「封面都没了、按钮还在飞」的脱节感。
 */

/** 封面快照开始入洞缩放前的静置（毫秒）。 */
const val CUSTOMIZE_COVER_HOLD_MILLIS = 32

/** 封面快照入洞缩放时长（毫秒）。 */
const val CUSTOMIZE_COVER_SCALE_MILLIS = 400

/** 封面快照淡出时长（毫秒）。 */
const val CUSTOMIZE_COVER_FADE_MILLIS = 120

/** 封面快照从开始组合到完全撤除的总时长（毫秒）。 */
const val CUSTOMIZE_COVER_WINDOW_MILLIS =
    CUSTOMIZE_COVER_HOLD_MILLIS + CUSTOMIZE_COVER_SCALE_MILLIS + CUSTOMIZE_COVER_FADE_MILLIS

/** 五次方缓出，比 [OobeQuartOutEasing] 更陡。 */
val OobeFifthpowerOutEasing = Easing { fraction ->
    val inverse = 1f - fraction
    1f - inverse * inverse * inverse * inverse * inverse
}

/** 引导完成进入主界面的入场时长（毫秒）。 */
const val OOBE_HOME_ENTER_MILLIS = 700

/** 引导完成时主界面淡入的时长与延迟（毫秒）。 */
const val OOBE_HOME_FADE_MILLIS = 230
const val OOBE_HOME_FADE_DELAY_MILLIS = 60

/** 正弦缓出。 */
val OobeSinOutEasing = Easing { fraction -> sin(fraction * PI.toFloat() / 2f) }

/**
 * 进入主界面的弹簧缓动：阻尼 0.65、角频率 10.5 的欠阻尼阶跃响应，
 * 过冲一次再收敛；曲线在 1f 处强制收敛。
 */
val OobeHomeSpringEasing = Easing { fraction ->
    if (fraction <= 0f) return@Easing 0f
    if (fraction >= 1f) return@Easing 1f
    val dampingRatio = 0.65f
    val angularFrequency = 10.5f
    val dampedFrequency = angularFrequency * sqrt(1f - dampingRatio * dampingRatio)
    val envelope = exp((-dampingRatio * angularFrequency * fraction).toDouble()).toFloat()
    1f - envelope * (
        cos(dampedFrequency * fraction) +
            dampingRatio / sqrt(1f - dampingRatio * dampingRatio) * sin(dampedFrequency * fraction)
        )
}
