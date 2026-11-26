package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.haooz.nexioui.cardexpand.CardExpand as NexioCardExpand

/** 旧页面保留 ColumnScope 入口，展开动效统一由 NexioUI 实现。 */
@Composable
fun CardExpand(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    NexioCardExpand(visible = visible, modifier = modifier) {
        Column(content = content)
    }
}
