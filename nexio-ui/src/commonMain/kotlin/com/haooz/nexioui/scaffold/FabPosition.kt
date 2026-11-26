// 派生自 compose-miuix-ui（Apache-2.0）。

package com.haooz.nexioui.scaffold

/** 悬浮按钮在脚手架底部的位置。 */
@kotlin.jvm.JvmInline
value class FabPosition internal constructor(@Suppress("unused") private val value: Int) {
    companion object {
        /** 底部起始侧，位于底栏之上。 */
        val Start = FabPosition(0)

        /** 底部居中，位于底栏之上。 */
        val Center = FabPosition(1)

        /** 底部结束侧，位于底栏之上。 */
        val End = FabPosition(2)

        /** 底部结束侧，覆盖在底栏之上。 */
        val EndOverlay = FabPosition(3)
    }

    override fun toString(): String = when (this) {
        Start -> "FabPosition.Start"
        Center -> "FabPosition.Center"
        End -> "FabPosition.End"
        else -> "FabPosition.EndOverlay"
    }
}
