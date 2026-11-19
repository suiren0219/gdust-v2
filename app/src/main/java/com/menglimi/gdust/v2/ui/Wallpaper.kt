package com.menglimi.gdust.v2.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 所有业务页共用不透明的 CZeroX 页面底色。 */
@Composable
fun pageBackground(): Color = LocalDs.current.bg
