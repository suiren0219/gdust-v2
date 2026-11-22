package com.menglimi.study.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.menglimi.chaoxing.ChxSso
import com.menglimi.ksu.ui.KCard
import com.menglimi.ksu.ui.displayTime
import com.menglimi.study.learning.LearningReadControl
import kotlinx.coroutines.delay

@Composable
internal fun ReadControlCard(control: LearningReadControl, loading: Boolean, onResume: () -> Unit, onOpen: (String) -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(control.pause?.retryAt) {
        while ((control.pause?.retryAt ?: 0) > now) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val remaining = ((control.pause?.retryAt ?: 0) - now).coerceAtLeast(0)
    KCard(Modifier.fillMaxWidth()) {
        Text(if (control.pause == null) "可以继续读取" else "读取已暂停", style = MaterialTheme.typography.titleMedium)
        Text(control.message().orEmpty(), style = MaterialTheme.typography.bodyMedium)
        if (remaining > 0) Text("${displayTime(control.pause!!.retryAt)} 后可重查 · 剩余约 ${remaining / 1000 + if (remaining % 1000 > 0) 1 else 0} 秒",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (control.pause?.verificationRequired == true) {
                Button(onClick = { onOpen(control.verificationUrl ?: ChxSso.HOME) }, enabled = !loading && remaining == 0L) {
                    Text("打开网页完成验证")
                }
            } else {
                Button(onClick = onResume, enabled = !loading && remaining == 0L) {
                    Text(if (control.pause != null) "重查受阻项" else "继续读取剩余 ${control.pendingCourseKeys.size} 门课程")
                }
            }
        }
    }
}
