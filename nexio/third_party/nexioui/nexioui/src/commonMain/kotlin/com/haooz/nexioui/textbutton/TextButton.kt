package com.haooz.nexioui.textbutton

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.haooz.nexioui.internal.GlassDragStretchRatio
import com.haooz.nexioui.internal.GlassDragTranslationDamping
import com.haooz.nexioui.internal.computeDragTransform
import com.haooz.nexioui.internal.drawBlurredPathShadow
import com.haooz.nexioui.interaction.InteractiveHighlight
import com.haooz.nexioui.material.NexioMaterial
import com.haooz.nexioui.text.NexioText
import com.haooz.nexioui.theme.NexioTheme
import com.haooz.softglass.backdrop.GlassBackdrop
import com.haooz.softglass.backdrop.GlassEffectScope
import com.haooz.softglass.backdrop.drawBackdrop
import com.haooz.softglass.backdrop.effects.blur
import com.haooz.softglass.backdrop.effects.runtimeShaderEffect
import com.haooz.softglass.glass.BrightTone
import com.haooz.softglass.glass.CustomizeFill
import com.haooz.softglass.glass.SOFT_GLASS_LIVE_SHADER
import com.haooz.softglass.glass.ThemeFill
import com.haooz.softglass.glass.applySoftGlass
import com.haooz.softglass.shape.ContinuousCapsule

private const val LIVE_TEXT_BUTTON_GLASS_KEY = "NexioSoftGlassLiveTextButton"

/**
 * 悬浮液态玻璃文字按钮。
 *
 * 依赖调用方传入的 [backdrop]（通常是整页的玻璃采样层）；按钮自身负责投影、按压高光、
 * 拖动形变与玻璃材质，不裁切形变。
 *
 * @param text 按钮文案。
 * @param onClick 点击回调。
 * @param backdrop 底层玻璃采样。
 * @param modifier 外层修饰符。
 * @param enabled 是否可点击。
 * @param minHeight 最小高度。
 * @param primary 主按钮使用主题色，次按钮使用中性底色。
 */
@Composable
fun TextButton(
    text: String,
    onClick: () -> Unit,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = TextButtonDefaults.MinHeight,
    primary: Boolean = true,
) {
    val animationScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val colorScheme = NexioTheme.colorScheme
    val isLightTheme = !NexioTheme.isDark
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope,
            isDark = { !isLightTheme },
        )
    }
    val interactionSource = remember { MutableInteractionSource() }

    val resolvedContainerColor = if (primary) {
        if (isLightTheme) {
            colorScheme.primary.copy(alpha = NexioMaterial.solidSurfaceAlpha(0.72f))
        } else {
            colorScheme.primary.copy(alpha = NexioMaterial.solidSurfaceAlpha(0.62f))
        }
    } else if (isLightTheme) {
        Color.White.copy(alpha = NexioMaterial.solidSurfaceAlpha(0.76f))
    } else {
        colorScheme.surfaceContainer.copy(alpha = NexioMaterial.solidSurfaceAlpha(0.84f))
    }
    val textColor = if (primary) {
        Color.White
    } else if (isLightTheme) {
        Color.Black.copy(alpha = 0.85f)
    } else {
        Color.White.copy(alpha = 0.85f)
    }
    val shadowColor = if (primary) colorScheme.primary else colorScheme.onSurface
    val shadowAlpha = 0.4f

    val buttonShape: Shape = remember { ContinuousCapsule() }
    val buttonShapeBlock: () -> Shape = remember { { buttonShape } }
    val glassBlur = NexioMaterial.glassBlurDp()
    val glassEffectsOn = NexioMaterial.glassEffectsEnabled()
    val buttonGlassStyle = NexioMaterial.chromeGlassStyle(
        isDark = !isLightTheme,
        fill = if (primary) CustomizeFill(colorScheme.primary) else ThemeFill,
        tone = BrightTone,
    )
    // drawBackdrop 按引用比较 shape / effects / onDrawSurface，lambda 每帧新建会重录采样层。
    val buttonEffects: GlassEffectScope.() -> Unit = remember(glassBlur, glassEffectsOn, buttonGlassStyle) {
        {
            if (glassEffectsOn) {
                blur(glassBlur.dp.toPx())
                runtimeShaderEffect(
                    key = LIVE_TEXT_BUTTON_GLASS_KEY,
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
    val buttonOnDrawSurface: DrawScope.() -> Unit = remember(resolvedContainerColor, interactiveHighlight) {
        {
            drawRect(resolvedContainerColor)
            drawRect(Color.Black.copy(alpha = 0.03f * interactiveHighlight.pressProgress))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                clip = false
                val drag = computeDragTransform(
                    width = size.width,
                    height = size.height,
                    fraction = 1f,
                    pressProgress = interactiveHighlight.pressProgress,
                    offset = interactiveHighlight.offset,
                    density = this,
                    shapeAspectRatio = 1f,
                    stretchRatio = GlassDragStretchRatio,
                    translationDamping = GlassDragTranslationDamping,
                )
                translationX = drag.translationX
                translationY = drag.translationY
            }
            .drawBehind {
                val offsetY = 4.dp.toPx()
                val blurRadius = 10f * density
                val radius = size.height / 2f
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(0f, offsetY, size.width, size.height + offsetY),
                            cornerRadius = CornerRadius(radius, radius),
                        ),
                    )
                }
                drawBlurredPathShadow(
                    path = path,
                    color = shadowColor.copy(alpha = shadowAlpha),
                    blurRadius = blurRadius.coerceAtLeast(0.1f),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    enabled = enabled,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onClick()
                    },
                )
                .drawBackdrop(
                    backdrop = backdrop,
                    shadow = null,
                    shape = buttonShapeBlock,
                    effects = buttonEffects,
                    layerBlock = {
                        val drag = computeDragTransform(
                            width = size.width,
                            height = size.height,
                            fraction = 1f,
                            pressProgress = interactiveHighlight.pressProgress,
                            offset = interactiveHighlight.offset,
                            density = this,
                            shapeAspectRatio = 1f,
                            stretchRatio = GlassDragStretchRatio,
                            translationDamping = GlassDragTranslationDamping,
                            // 文字按钮按压放大比通用档小一半（4dp → 2dp）
                            pressScaleDp = 2f,
                        )
                        scaleX = drag.scaleX
                        scaleY = drag.scaleY
                    },
                    // 柔光自己画玻璃色与方向光；无材质档不跑 shader，必须留容器色。
                    onDrawSurface = if (glassEffectsOn) null else buttonOnDrawSurface,
                )
                .then(interactiveHighlight.modifier)
                .then(if (enabled) interactiveHighlight.gestureModifier else Modifier)
                .defaultMinSize(minHeight = minHeight)
                .padding(horizontal = TextButtonDefaults.HorizontalPadding, vertical = TextButtonDefaults.VerticalPadding),
            contentAlignment = Alignment.Center,
        ) {
            NexioText(
                text = text,
                style = NexioTheme.textStyles.button,
                fontWeight = FontWeight.Medium,
                color = textColor.copy(alpha = if (enabled) 1f else 0.4f),
                // 父级对齐在测量期不可见，必须显式居中，避免文案变长时先贴左再滑回。
                textAlign = TextAlign.Center,
            )
        }
    }
}
