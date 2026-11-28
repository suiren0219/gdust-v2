package com.haooz.nexioui.window

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.haooz.softglass.shape.ContinuousRoundedRectangle

/**
 * 把四角物理圆角转成连续圆角形状，并让硬件圆角始终贴合物理边缘（RTL 下也不镜像）。
 */
fun ScreenCornerRadiiPx.toShape(density: Density): Shape = PhysicalScreenCornerShape(
    with(density) {
        ContinuousRoundedRectangle(
            topStart = topLeft.toDp(),
            topEnd = topRight.toDp(),
            bottomEnd = bottomRight.toDp(),
            bottomStart = bottomLeft.toDp(),
        )
    }
)

@Immutable
private data class PhysicalScreenCornerShape(
    val delegate: ContinuousRoundedRectangle,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        delegate.createOutline(size, LayoutDirection.Ltr, density)
}

/** 把同样的四角连续圆角轮廓追加到 Path。 */
fun Path.addScreenCornerRect(width: Float, height: Float, corners: ScreenCornerRadiiPx) {
    val density = Density(1f)
    addOutline(corners.toShape(density).createOutline(Size(width, height), LayoutDirection.Ltr, density))
}
