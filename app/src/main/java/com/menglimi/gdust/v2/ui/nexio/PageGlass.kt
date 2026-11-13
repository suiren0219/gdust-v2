package com.menglimi.gdust.v2.ui.nexio

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * 页面玻璃采样层：MainScaffold 把「壁纸 + 页面内容」录进这一层，
 * 课程卡/顶栏按钮用它做 drawBackdrop，实现 Nexio 式透出壁纸的玻璃材质。
 * 未提供（null）时页面回退到无材质路径。
 */
val LocalPageLayerBackdrop = staticCompositionLocalOf<com.haooz.softglass.backdrop.GlassBackdrop?> { null }

/**
 * Nexio 玻璃卡表面色（28a99a3 CourseCard / 效果页默认档）：
 * 卡片着色 12% 叠在底色 34% 上，底色深色为黑、浅色为白。
 */
fun glassSurfaceColor(
    tint: Color,
    isDark: Boolean,
    tintAlpha: Float = 0.12f,
    surfaceAlpha: Float = 0.34f,
): Color = tint.copy(alpha = tintAlpha).compositeOver(
    (if (isDark) Color.Black else Color.White).copy(alpha = surfaceAlpha),
)
