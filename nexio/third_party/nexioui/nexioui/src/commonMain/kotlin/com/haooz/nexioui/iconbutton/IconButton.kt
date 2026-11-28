package com.haooz.nexioui.iconbutton

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.haooz.nexioui.internal.GlassDragStretchRatio
import com.haooz.nexioui.internal.GlassDragTranslationDamping
import com.haooz.nexioui.internal.computeDragTransform
import com.haooz.nexioui.internal.drawBlurredPathShadow
import com.haooz.nexioui.interaction.InteractiveHighlight
import com.haooz.nexioui.material.NexioGlassStyle
import com.haooz.nexioui.material.NexioMaterial
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.GlassEffectScope
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import com.haooz.softglass.backdrop.effects.runtimeShaderEffect
import com.haooz.softglass.glass.BrightTone
import com.haooz.softglass.glass.GlassFill
import com.haooz.softglass.glass.GlassTone
import com.haooz.softglass.glass.SOFT_GLASS_LIVE_SHADER
import com.haooz.softglass.glass.ThemeFill
import com.haooz.softglass.glass.applySoftGlass
import com.haooz.softglass.shape.ContinuousCapsule

private const val LIVE_ICON_BUTTON_GLASS_KEY = "NexioSoftGlassLiveIconButton"

/**
 * 圆形液态玻璃图标按钮。
 *
 * 拖动时沿拖动方向拉伸、抬手回弹；按压高光跟随手指，图标随整体形变。
 *
 * @param onClick 点击回调。
 * @param backdrop 底层玻璃采样。
 * @param icon 图标。
 * @param contentDescription 无障碍描述。
 * @param modifier 外层修饰符。
 * @param iconSize 图标尺寸。
 * @param iconOffset 图标额外偏移。
 * @param buttonHeight 按钮边长。
 * @param backdropAlpha 玻璃层透明度，随页面滚动淡出时使用。
 * @param shadowAlpha 投影强度。
 * @param iconTint 图标颜色；未指定时按深浅色取 85% 黑 / 白。
 * @param containerColor 自定义容器色；未指定时按材质档计算。
 * @param performHapticFeedback 点击时是否触发触感。
 * @param enabled 是否可点击。
 * @param fill 玻璃填充策略。
 * @param tone 玻璃方向光基调。
 */
@Composable
fun IconButton(
    onClick: () -> Unit,
    backdrop: GlassBackdrop,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconSize: Dp = IconButtonDefaults.IconSize,
    iconOffset: DpOffset = DpOffset.Zero,
    buttonHeight: Dp = IconButtonDefaults.ButtonHeight,
    /** 按压高光相对默认半径的倍率（1f 为默认大小）。 */
    highlightRadiusScale: Float = 1f,
    backdropAlpha: Float = 1f,
    shadowAlpha: Float = 1f,
    iconTint: Color = Color.Unspecified,
    containerColor: Color = Color.Unspecified,
    performHapticFeedback: Boolean = true,
    enabled: Boolean = true,
    fill: GlassFill = ThemeFill,
    tone: GlassTone = BrightTone,
) {
    val animationScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val colorScheme = NexioTheme.colorScheme
    val isLightTheme = !NexioTheme.isDark
    val interactiveHighlight = remember(animationScope, highlightRadiusScale) {
        InteractiveHighlight(
            animationScope = animationScope,
            isDark = { !isLightTheme },
            radiusScale = { highlightRadiusScale },
        )
    }
    val resolvedContainerColor = when {
        containerColor != Color.Unspecified -> containerColor
        isLightTheme -> Color(0xFFFAFAFA).copy(alpha = NexioMaterial.glassSurfaceAlpha(0.76f, true))
        else -> colorScheme.surfaceContainer.copy(alpha = NexioMaterial.glassSurfaceAlpha(0.84f, false))
    }
    val shadowColor = if (isLightTheme) Color(0x12000000) else Color(0x20000000)
    val interactionSource = remember { MutableInteractionSource() }

    // drawBackdrop 按引用比较 shape / effects / onDrawSurface，lambda 必须 remember。
    val glassBlur = NexioMaterial.glassBlurDp()
    val glassEffectsOn = NexioMaterial.glassEffectsEnabled()
    val buttonShapeBlock: () -> Shape = remember { { ContinuousCapsule() } }
    val buttonGlassStyle = NexioMaterial.chromeGlassStyle(
        base = NexioGlassStyle,
        isDark = !isLightTheme,
        fill = fill,
        tone = tone,
    )
    val buttonEffects: GlassEffectScope.() -> Unit = remember(glassBlur, glassEffectsOn, buttonGlassStyle) {
        {
            if (glassEffectsOn) {
                blur(glassBlur.dp.toPx())
                runtimeShaderEffect(
                    key = LIVE_ICON_BUTTON_GLASS_KEY,
                    shaderString = SOFT_GLASS_LIVE_SHADER,
                    uniformShaderName = "content",
                ) {
                    applySoftGlass(
                        style = buttonGlassStyle,
                        width = size.width,
                        height = size.height,
                    )
                }
            }
        }
    }
    val buttonOnDrawSurface: DrawScope.() -> Unit = remember(resolvedContainerColor) {
        { drawRect(resolvedContainerColor) }
    }

    Box(
        modifier = modifier
            .wrapContentSize()
            .size(buttonHeight)
            .graphicsLayer {
                clip = false
                val drag = computeDragTransform(
                    width = size.width,
                    height = size.height,
                    fraction = 0f,
                    pressProgress = interactiveHighlight.pressProgress,
                    offset = interactiveHighlight.offset,
                    density = this,
                    stretchRatio = GlassDragStretchRatio,
                    translationDamping = GlassDragTranslationDamping,
                    pressScaleDp = 2f,
                )
                scaleX = drag.scaleX
                scaleY = drag.scaleY
                translationX = drag.translationX
                translationY = drag.translationY
            }
            .drawBehind {
                if (shadowAlpha > 0.01f) {
                    val blurRadius = 10f * density * shadowAlpha
                    val shadowSpread = 2f * density * shadowAlpha
                    val outerRadius = size.minDimension / 2f + shadowSpread
                    val innerRadius = size.minDimension / 2f
                    // 环形阴影：外圈顺时针 + 内圈逆时针，用环绕方向做差集。
                    val path = Path().apply {
                        addOval(
                            Rect(
                                center.x - outerRadius,
                                center.y - outerRadius,
                                center.x + outerRadius,
                                center.y + outerRadius,
                            ),
                            Path.Direction.Clockwise,
                        )
                        addOval(
                            Rect(
                                center.x - innerRadius,
                                center.y - innerRadius,
                                center.x + innerRadius,
                                center.y + innerRadius,
                            ),
                            Path.Direction.CounterClockwise,
                        )
                    }
                    drawBlurredPathShadow(
                        path = path,
                        color = shadowColor.copy(alpha = (shadowColor.alpha * 3.2f).coerceAtMost(1f)),
                        blurRadius = blurRadius.coerceAtLeast(0.1f),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(buttonHeight)
                .drawBackdrop(
                    backdrop = backdrop,
                    shadow = null,
                    shape = buttonShapeBlock,
                    effects = buttonEffects,
                    layerBlock = { alpha = backdropAlpha },
                    // 无材质档不跑 shader，必须保留容器色，否则按钮会变成透空圆孔。
                    onDrawSurface = if (glassEffectsOn) null else buttonOnDrawSurface,
                )
                .zIndex(0f),
        )
        // 高光与材质层平级：材质层整体 alpha 会随页面滚动，高光不应跟着消失。
        Box(
            modifier = Modifier
                .size(buttonHeight)
                .clip(ContinuousCapsule())
                .then(interactiveHighlight.modifier)
                .then(if (enabled) interactiveHighlight.gestureModifier else Modifier)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = {
                        if (performHapticFeedback) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        }
                        onClick()
                    },
                )
                .zIndex(1f),
        )
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(iconSize)
                .offset(iconOffset.x, iconOffset.y)
                .zIndex(2f),
            tint = if (iconTint != Color.Unspecified) {
                iconTint
            } else if (isLightTheme) {
                Color.Black.copy(alpha = 0.85f)
            } else {
                Color.White.copy(alpha = 0.85f)
            },
        )
    }
}
