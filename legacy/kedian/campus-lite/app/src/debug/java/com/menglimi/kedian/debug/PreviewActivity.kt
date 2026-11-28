package com.menglimi.kedian.debug

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.kedian.learning.Homework
import com.menglimi.kedian.learning.LearningUiState
import com.menglimi.kedian.power.PowerUiState
import com.menglimi.kedian.ui.Appearance
import com.menglimi.kedian.ui.CampusRoot
import com.menglimi.kedian.ui.KedianTheme
import com.menglimi.kedian.ui.ThemeMode
import com.menglimi.power.PowerBinding
import com.menglimi.power.PowerDetail
import com.menglimi.power.PowerMonitorConfig
import com.menglimi.power.PowerReading
import com.menglimi.power.PowerRoom
import com.menglimi.power.PowerSample

/** 仅调试包含此入口；注入内存快照，所有操作均不访问网络或正式存储。 */
class PreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val now = System.currentTimeMillis()
        val offline = { Toast.makeText(this, "合成预览，不执行真实请求", Toast.LENGTH_SHORT).show(); Unit }
        setContent {
            var tab by remember { mutableIntStateOf(intent.getIntExtra("tab", 0).coerceIn(0, 2)) }
            var appearance by remember {
                mutableStateOf(Appearance(if (intent.getBooleanExtra("dark", false)) ThemeMode.DARK else ThemeMode.LIGHT,
                    dynamic = false, amoled = intent.getBooleanExtra("amoled", false)))
            }
            var learning by remember { mutableStateOf(LearningUiState(
                connected = true, updatedAt = now - 300_000,
                works = listOf(
                    Homework("synthetic-1", "高等数学", "第四章 · 多元函数微分学", "未交", now + 5 * 3600_000L, ""),
                    Homework("synthetic-2", "大学英语", "Unit 5 · Reading & Writing", "未交", now + 36 * 3600_000L, ""),
                    Homework("synthetic-3", "计算机基础", "实验三：数据与算法", "未交", now + 72 * 3600_000L, ""),
                    Homework("synthetic-4", "思想道德与法治", "课程阅读与思考", "已完成", 0, ""),
                ),
            )) }
            var power by remember { mutableStateOf(PowerUiState(
                config = PowerMonitorConfig(binding = PowerBinding("synthetic-account", 1, "synthetic-room")),
                roomName = "示例宿舍 · 6 栋 408",
                reading = PowerReading(PowerDetail(42.6, 2.3,
                    listOf("周一" to 2.7, "周二" to 2.4, "周三" to 3.1, "周四" to 2.8, "周五" to 2.5, "周六" to 3.0, "周日" to 2.3),
                    78.4, 200.0), now - 600_000),
                samples = (0..28).map { index -> PowerSample(now - (28 - index) * 6 * 3600_000L, 64.2 - index * (21.6 / 28)) },
            )) }
            KedianTheme(appearance) {
                Box(Modifier.fillMaxSize()) {
                    CampusRoot(tab, { tab = it }, appearance, learning, power, true, { appearance = it },
                        onLogin = offline, onLearningRefresh = offline, onOpenWork = { offline() },
                        onLearningClearError = { learning = learning.copy(error = null) },
                        onPowerRefresh = offline,
                        onQueryRooms = { account, code -> power = power.copy(rooms = listOf(PowerRoom("合成房间 A", "synthetic-a")),
                            roomQueryAccount = account, roomQueryCode = code) },
                        onBind = { account, code, room -> power = power.copy(config = power.config.copy(binding = PowerBinding(account, code, room.verify)),
                            roomName = room.name, rooms = emptyList(), roomQueryAccount = "", roomQueryCode = 0) },
                        onPowerClearError = { power = power.copy(error = null) }, onNotifications = offline,
                        onPreset = { power = power.copy(config = power.config.copy(preset = it)) },
                        onInterval = { power = power.copy(config = power.config.copy(customIntervalMinutes = it)) },
                        onThresholds = { low, drop -> power = power.copy(config = power.config.copy(lowThreshold = low, dropRateThreshold = drop)) },
                        onDisconnect = { learning = LearningUiState() },
                        onUnbind = { power = PowerUiState(PowerMonitorConfig()) },
                    )
                    Text("合成预览", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 18.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 7.dp, vertical = 3.dp))
                }
            }
        }
    }
}
