package com.haooz.nexioui.interaction

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import com.haooz.softglass.platform.GlassRuntimeShader
import com.haooz.softglass.platform.asComposeShader
import com.haooz.softglass.platform.createGlassRuntimeShader
import com.haooz.softglass.platform.isRuntimeShaderSupported
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 按压跟手高光。
 *
 * 只观察指针，不消费事件；以按下位置为点光源画一团柔和光效（与 Slider 同一套光照：
 * Lambert 漫反射 + 环境光，固定设备像素半径，按下展开、熄灭再扩散）。
 * 所有按压高光（含 Slider）共用这一份绘制实现。
 *
 * @param animationScope 驱动高光与回弹动画的作用域。
 * @param position 将手指位置映射到绘制坐标系。
 * @param radiusScale 光斑半径倍率（绘制期读取）。
 * @param radiusBaseDp 光斑最终半径；null 时取 Slider 的固定 500px。
 * @param pressProgressOverride 由外部状态驱动按压进度；非 null 时忽略自身手势。
 * @param isDark 当前是否深色（绘制期读取，可跟随主题变化），决定光强。
 */
class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
    val radiusScale: () -> Float = { 1f },
    val radiusBaseDp: Dp? = null,
    val pressProgressOverride: (() -> Float)? = null,
    val isDark: () -> Boolean = { false },
) {

    private companion object {
        /** Slider 按压响应的固有振荡周期（秒）。 */
        const val PRESS_RESPONSE = 0.15f

        /** Slider 熄灭响应的固有振荡周期（秒）。 */
        const val RELEASE_RESPONSE = 0.4f

        /** 光斑最终半径（设备像素），与 Slider 的 OpaqueMedium1 预设一致。 */
        const val DEFAULT_END_RADIUS_PX = 500f

        /** 初始半径占最终半径的比例（Slider 为 100 / 500）。 */
        const val START_RADIUS_RATIO = 0.2f

        /** 光强：在原厂 `OpaqueMedium1`（0.6 / 0.8）基础上整体提亮。 */
        const val INTENSITY_LIGHT = 0.8f
        const val INTENSITY_DARK = 1f

        /** COUI `OpaqueMedium1` 预设的光照参数。 */
        const val AMBIENT = 0.4f
        const val DIFFUSE = 0.4f
        /** 光源高度 / 光斑半径，取自原厂 200 / 500，小控件按同比例缩放，避免光斑被高度吃掉。 */
        const val LIGHT_HEIGHT_RATIO = 0.4f
        const val NOISE = 0.08f
        const val DITHER = 0f

        const val SPRING_DAMPING_RATIO = 0.5f
        const val SPRING_STIFFNESS = 300f
    }

    private val visibilityThreshold = 0.001f
    private val offsetVisibilityThreshold = Offset(visibilityThreshold, visibilityThreshold)

    private fun responseStiffness(response: Float): Float {
        val omega = (2.0 * kotlin.math.PI / response).toFloat()
        return omega * omega
    }

    /** 按下与松开走 Slider 的两套时序：快进慢出。 */
    private val pressProgressAnimationSpec =
        spring(1f, responseStiffness(PRESS_RESPONSE), visibilityThreshold)
    private val releaseProgressAnimationSpec =
        spring(1f, responseStiffness(RELEASE_RESPONSE), visibilityThreshold)
    private val positionAnimationSpec =
        spring(SPRING_DAMPING_RATIO, SPRING_STIFFNESS, offsetVisibilityThreshold)

    /** 当前是否处于熄灭阶段：熄灭时半径会额外扩散到 2×。 */
    private var releasing = false

    private val pressProgressAnimation = Animatable(0f, visibilityThreshold)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, offsetVisibilityThreshold)

    /** 形变位移；松手回到 0，高光则原地淡出。 */
    private val dragOffsetAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, offsetVisibilityThreshold)

    private var startPosition = Offset.Zero
    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = dragOffsetAnimation.value

    private suspend fun playPress() {
        releasing = false
        pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec)
    }

    private suspend fun playRelease() {
        releasing = true
        pressProgressAnimation.animateTo(0f, releaseProgressAnimationSpec)
    }

    /**
     * 在绘制阶段画当前光效。Slider 这类需要把光效扣在自身内容之上的组件可直接调用，
     * 其余组件用 [modifier] 即可。
     */
    fun drawHighlight(scope: DrawScope) = with(scope) {
        val progress = pressProgressOverride?.invoke() ?: pressProgressAnimation.value
        if (progress <= 0f) {
            releasing = false
            return
        }

        // 显式给了基准的控件沿用原口径：基准 × 1.5；没给的走 Slider 的固定 500px
        val end = ((radiusBaseDp?.toPx()?.times(1.5f)) ?: DEFAULT_END_RADIUS_PX) * radiusScale()
        val start = end * START_RADIUS_RATIO
        val radius = start + (end - start) * progress +
            if (releasing) 2f * end * (1f - progress) else 0f
        // 手势层与绘制层的原点可能不同（玻璃阴影 / padding），夹回自身范围内
        val raw = position(size, positionAnimation.value)
        val light = Offset(raw.x.coerceIn(0f, size.width), raw.y.coerceIn(0f, size.height))
        val shader = shader ?: return
        val intensity = (if (isDark()) INTENSITY_DARK else INTENSITY_LIGHT) * progress
        shader.setFloatUniform("u_lightPosAndHeight", light.x, light.y, radius, intensity)
        shader.setFloatUniform("u_lightColorAndAmbient", 1f, 1f, 1f, AMBIENT)
        shader.setFloatUniform("u_lightingParams", DIFFUSE, radius * LIGHT_HEIGHT_RATIO, NOISE, DITHER)
        // 与 COUISpotLightEffectDrawable 一致：整层铺 shader，混合交给 Overlay
        drawRect(ShaderBrush(shader.asComposeShader()), blendMode = BlendMode.Overlay)
    }

    private val shader: GlassRuntimeShader? =
        if (isRuntimeShaderSupported()) {
            // 与原厂一致：编译失败时退化为无光效，而不是让 shader 异常冒到组合里
            runCatching { createGlassRuntimeShader(SpotlightShader) }.getOrNull()
        } else {
            null
        }

    val modifier: Modifier = Modifier.drawWithContent {
        drawHighlight(this)
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        inspectDragGestures(
            onDragStart = { down ->
                startPosition = down.position
                animationScope.launch {
                    launch { playPress() }
                    launch { positionAnimation.snapTo(startPosition) }
                    launch { dragOffsetAnimation.snapTo(Offset.Zero) }
                }
            },
            onDragEnd = {
                animationScope.launch {
                    launch { playRelease() }
                    launch { dragOffsetAnimation.animateTo(Offset.Zero, positionAnimationSpec) }
                }
            },
            onDragCancel = {
                animationScope.launch {
                    launch { playRelease() }
                    launch { dragOffsetAnimation.animateTo(Offset.Zero, positionAnimationSpec) }
                }
            },
            observeConsumed = true,
        ) { change, _ ->
            animationScope.launch {
                positionAnimation.snapTo(change.position)
                dragOffsetAnimation.snapTo(change.position - startPosition)
            }
        }
    }

    /** 只处理按压、不处理拖动；不消费事件，交给 clickable。 */
    val pressOnlyModifier: Modifier = Modifier.pointerInput(animationScope) {
        awaitEachGesture {
            awaitFirstDown()
            animationScope.launch {
                playPress()
            }
            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
            } while (event.changes.any { it.pressed })
            animationScope.launch {
                playRelease()
            }
        }
    }
}
