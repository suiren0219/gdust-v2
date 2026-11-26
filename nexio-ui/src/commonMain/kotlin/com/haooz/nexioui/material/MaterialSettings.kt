package com.haooz.nexioui.material

import androidx.compose.runtime.compositionLocalOf

/** 无材质档：玻璃材质整体关闭，渐进模糊降级为渐变遮罩。 */
val LocalMaterialNoneEnabled = compositionLocalOf { false }

/**
 * 渐进模糊是否用「假」实现（等值 blur + alpha 淡出，不跑 AGSL 多重采样）。
 *
 * 应用关闭「真·渐进模糊」开关时为真；无材质档走渐变遮罩，不受它影响。
 */
val LocalUseFakeProgressiveBlur = compositionLocalOf { false }
