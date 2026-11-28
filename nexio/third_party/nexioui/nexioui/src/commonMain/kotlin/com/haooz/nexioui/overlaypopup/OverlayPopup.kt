package com.haooz.nexioui.overlaypopup

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.dropdown.DropdownPopupLayout
import com.haooz.softglass.backdrop.GlassBackdrop

/** 弹层相对锚点的水平对齐方式。 */
enum class PopupAlignment {
    /** 贴锚点起始缘（LTR 下为左缘）。 */
    Start,

    /** 贴锚点结束缘（LTR 下为右缘）。 */
    End,
}

/** 弹层默认参数。 */
object PopupDefaults {
    /** 面板右缘相对锚点右缘的水平补偿（正值右移）。 */
    val AnchorOffsetX: Dp = 7.dp

    /** 面板上缘相对锚点上缘的竖直补偿（正值上移，展开方向自动取反）。 */
    val AnchorOffsetY: Dp = 5.dp
}

/**
 * 通用弹层：玻璃材质面板 + 从触发区尺寸展开的进出场动画。
 *
 * 面板优先向下展开，下方空间不足时翻到锚点上方，两端都放不下则居中；
 * 水平方向按 [alignment] 贴齐锚点并做窗口边界 clamp。
 *
 * @param show 是否显示
 * @param onDismissRequest 点击外部 / 返回键的关闭回调
 * @param onDismissFinished 退场动画完成回调
 * @param alignment 相对锚点的水平对齐
 * @param anchorOffsetX 水平补偿（正值右移）
 * @param anchorOffsetY 竖直补偿（正值上移，向上展开时自动取反）
 * @param maxHeight 面板内容最大高度
 * @param liquidGlassBackdrop 玻璃采样层；为 null 时退化为纯色面板
 * @param onFractionProgress 展开进度回调（0f~1f）
 * @param collapseSize 收起态尺寸，用于从触发区长成面板
 * @param collapseExtra 收起态尺寸补偿
 * @param collapseContent 收起态绘制的内容
 * @param content 面板内容
 */
@Composable
fun OverlayPopup(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit = {},
    alignment: PopupAlignment = PopupAlignment.End,
    anchorOffsetX: Dp = PopupDefaults.AnchorOffsetX,
    anchorOffsetY: Dp = PopupDefaults.AnchorOffsetY,
    maxHeight: Dp? = null,
    liquidGlassBackdrop: GlassBackdrop? = null,
    onFractionProgress: ((Float) -> Unit)? = null,
    collapseSize: IntSize? = null,
    collapseExtra: DpSize = DpSize.Zero,
    collapseContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    DropdownPopupLayout(
        show = show,
        maxHeight = maxHeight,
        liquidGlassBackdrop = liquidGlassBackdrop,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        onFractionProgress = onFractionProgress,
        collapseSize = collapseSize,
        collapseExtra = collapseExtra,
        collapseContent = collapseContent,
        dragSelectState = null,
        alignEnd = alignment == PopupAlignment.End,
        anchorOffsetX = anchorOffsetX,
        anchorOffsetY = anchorOffsetY,
        content = content,
    )
}
