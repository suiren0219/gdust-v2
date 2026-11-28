// 派生自 compose-miuix-ui（Apache-2.0）。

package com.haooz.nexioui.scaffold

import androidx.compose.ui.Alignment

/** 悬浮工具栏在脚手架内的位置。 */
@kotlin.jvm.JvmInline
value class ToolbarPosition internal constructor(@Suppress("unused") private val value: Int) {
    companion object {
        val TopStart = ToolbarPosition(0)
        val CenterStart = ToolbarPosition(1)
        val BottomStart = ToolbarPosition(2)
        val TopEnd = ToolbarPosition(3)
        val CenterEnd = ToolbarPosition(4)
        val BottomEnd = ToolbarPosition(5)
        val TopCenter = ToolbarPosition(6)
        val BottomCenter = ToolbarPosition(7)
    }

    override fun toString(): String = when (this) {
        TopStart -> "ToolbarPosition.TopStart"
        CenterStart -> "ToolbarPosition.CenterStart"
        BottomStart -> "ToolbarPosition.BottomStart"
        TopEnd -> "ToolbarPosition.TopEnd"
        CenterEnd -> "ToolbarPosition.CenterEnd"
        BottomEnd -> "ToolbarPosition.BottomEnd"
        TopCenter -> "ToolbarPosition.TopCenter"
        else -> "ToolbarPosition.BottomCenter"
    }
}

internal fun ToolbarPosition.toAlignment(): Alignment = when (this) {
    ToolbarPosition.TopStart -> Alignment.TopStart
    ToolbarPosition.CenterStart -> Alignment.CenterStart
    ToolbarPosition.BottomStart -> Alignment.BottomStart
    ToolbarPosition.TopEnd -> Alignment.TopEnd
    ToolbarPosition.CenterEnd -> Alignment.CenterEnd
    ToolbarPosition.BottomEnd -> Alignment.BottomEnd
    ToolbarPosition.TopCenter -> Alignment.TopCenter
    ToolbarPosition.BottomCenter -> Alignment.BottomCenter
    else -> Alignment.BottomCenter
}
