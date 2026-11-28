package com.haooz.softglass.backdrop.backdrops

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.Density
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.internal.InverseLayerScope

private val DefaultOnDraw: ContentDrawScope.() -> Unit = { drawContent() }

/** @param recordScale 采样层录制分辨率比例；纯采样层可降到 1 以下省 blit 面积，上屏层不要降。 */
@Composable
fun rememberLayerBackdrop(
    graphicsLayer: GraphicsLayer = rememberGraphicsLayer(),
    recordScale: Float = 1f,
    onDraw: ContentDrawScope.() -> Unit = DefaultOnDraw,
): LayerBackdrop = remember(graphicsLayer, recordScale, onDraw) {
    LayerBackdrop(graphicsLayer, recordScale, onDraw)
}

@Stable
class LayerBackdrop internal constructor(
    val graphicsLayer: GraphicsLayer,
    val recordScale: Float = 1f,
    internal val onDraw: ContentDrawScope.() -> Unit,
) : GlassBackdrop {

    internal val contentOnlyCapture: Boolean = onDraw === DefaultOnDraw

    override val isCoordinatesDependent: Boolean = true

    /**
     * 录制版本号。
     *
     * 做成快照状态：采样节点在 draw 期读它，任何一层重录（滚动、翻页、切主 tab 后按
     * recordKey 重录）都会让玻璃重新采样——采样节点自己位置不变的场景（底栏 / 顶栏 /
     * 设置按钮）否则会一直停在旧像素上。
     */
    override var contentVersion: Int
        get() = contentVersionState.intValue
        internal set(value) {
            contentVersionState.intValue = value
        }

    private val contentVersionState = mutableIntStateOf(0)

    internal var layerCoordinates: LayoutCoordinates? by mutableStateOf(null)

    private var inverseLayerScope: InverseLayerScope? = null

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val coordinates = coordinates ?: return
        val layerCoordinates = layerCoordinates ?: return
        withTransform({
            if (layerBlock != null) {
                with(obtainInverseLayerScope()) { inverseTransform(density, layerBlock) }
            }
            val offset = try {
                layerCoordinates.localPositionOf(coordinates)
            } catch (_: Exception) {
                // 跨窗口（Popup / Dialog）时 localPositionOf 不可用；窗口内坐标各自从窗口
                // 左上角算起，直接相减会带上弹窗窗口的屏幕偏移，必须用屏幕坐标对齐。
                coordinates.positionOnScreen() - layerCoordinates.positionOnScreen()
            }
            translate(-offset.x, -offset.y)
            if (recordScale != 1f) {
                scale(1f / recordScale, 1f / recordScale, Offset.Zero)
            }
        }) {
            drawLayer(graphicsLayer)
        }
    }

    private fun obtainInverseLayerScope(): InverseLayerScope =
        inverseLayerScope?.apply { reset() } ?: InverseLayerScope().also { inverseLayerScope = it }
}
