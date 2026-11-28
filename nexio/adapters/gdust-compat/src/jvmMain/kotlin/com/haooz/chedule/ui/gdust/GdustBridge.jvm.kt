package com.haooz.chedule.ui.gdust

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

/** 桌面端不提供广科直连（接口数据依赖移动端使用场景），同步页显示不支持。 */
@Composable
actual fun rememberGdustBridge(): GdustBridge? = null
