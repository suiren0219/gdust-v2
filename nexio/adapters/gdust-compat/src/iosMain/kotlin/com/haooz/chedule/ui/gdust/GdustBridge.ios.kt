package com.haooz.chedule.ui.gdust

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

/** iOS 端暂不提供广科直连桥（广科同步按 Android 优先交付）。 */
@Composable
actual fun rememberGdustBridge(): GdustBridge? = null
