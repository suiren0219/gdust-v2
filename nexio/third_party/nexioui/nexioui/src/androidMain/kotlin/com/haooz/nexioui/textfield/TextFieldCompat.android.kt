/**
 * TextView 与 Typeface 的高版本 API 兼容层（minSdk 26）。
 *
 * - 带 weight 的 `Typeface.create` 需要 API 28，低版本退化为传统粗细档位。
 * - `textCursorDrawable` / `textSelectHandle*` 需要 API 29，低版本跳过着色，
 *   保留系统默认光标与手柄。
 */
package com.haooz.nexioui.textfield

import android.graphics.Typeface
import android.os.Build
import android.widget.TextView

private val supportsWeightedTypeface: Boolean
    get() = Build.VERSION.SDK_INT >= 28

private val supportsTextDrawables: Boolean
    get() = Build.VERSION.SDK_INT >= 29

/** 按 fontWeight(1..1000) 设置 [TextView.typeface]。 */
internal fun TextView.applyWeightedTypeface(base: Typeface?, weight: Int) {
    typeface = if (supportsWeightedTypeface) {
        Typeface.create(base, weight, false)
    } else {
        Typeface.create(base, legacyStyleForWeight(weight))
    }
}

/** fontWeight(1..1000) → 传统 Typeface 粗细档位。 */
private fun legacyStyleForWeight(weight: Int): Int =
    if (weight >= 600) Typeface.BOLD else Typeface.NORMAL

/** 给系统光标与选择手柄统一着色（API 29+；低版本静默跳过）。 */
internal fun TextView.applySelectionHandlesTint(color: Int) {
    if (!supportsTextDrawables) return
    runCatching { textCursorDrawable?.setTint(color) }
    runCatching { textSelectHandle?.setTint(color) }
    runCatching { textSelectHandleLeft?.setTint(color) }
    runCatching { textSelectHandleRight?.setTint(color) }
}
