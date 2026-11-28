package com.menglimi.gdust.v2.ui

import androidx.compose.runtime.Composable
import com.haooz.nexioui.material.NexioMaterial
import com.haooz.nexioui.material.NexioMaterialLevel

/** 应用级 NexioUI 材质档位，与 Store.uiStyle 持久化键一一对应。 */
enum class AppMaterialStyle(
    val key: String,
    val label: String,
    val summary: String,
    val level: NexioMaterialLevel,
) {
    SOFT("SOFT", "柔光玻璃", "完整柔光折射、方向边光与顶栏渐进模糊", NexioMaterialLevel.Soft),
    LIQUID("LIQUID", "液态玻璃", "清透液态折射与动态高光", NexioMaterialLevel.Liquid),
    FROSTED("FROSTED", "清透磨砂", "轻磨砂毛玻璃，无折射与边光", NexioMaterialLevel.Frosted),
    NONE("NONE", "纯色无材质", "关闭玻璃采样与模糊，极致省电", NexioMaterialLevel.None),
    ;

    companion object {
        fun fromKey(key: String?): AppMaterialStyle =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: SOFT
    }
}

fun applyNexioMaterial(uiStyleKey: String?) {
    NexioMaterial.level = AppMaterialStyle.fromKey(uiStyleKey).level
}

/** 统一应用主题入口，全部业务页面与弹层共享 NexioTheme + SoftGlass。 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    NexioAppTheme(content)
}
