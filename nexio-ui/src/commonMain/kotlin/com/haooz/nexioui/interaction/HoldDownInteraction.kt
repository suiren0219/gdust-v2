package com.haooz.nexioui.interaction

import androidx.compose.foundation.interaction.Interaction

/**
 * 「按住」交互。
 *
 * Compose 自带的 `PressInteraction` 在手指滑出边界后会取消，而长按态需要跨区保持，
 * 所以单开一组交互表示「按住」与「松开」。
 */
interface HoldDownInteraction : Interaction {
    /** 组件被按住。 */
    class HoldDown : HoldDownInteraction

    /** 按住结束；[holdDown] 是被释放的那次事件。 */
    class Release(
        val holdDown: HoldDown,
    ) : HoldDownInteraction
}
