package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.PowerRoom
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.GlassCard
import com.menglimi.gdust.v2.ui.LocalDs
import kotlinx.coroutines.launch

@Composable
fun PowerScreen(repo: Repository) {
    val ds = LocalDs.current
    val scope = rememberCoroutineScope()
    val store = repo.store

    var account by remember { mutableStateOf(store.powerAccount) }
    var code by remember { mutableStateOf(store.powerCode.takeIf { it > 0 }?.toString() ?: "") }
    var rooms by remember { mutableStateOf<List<PowerRoom>>(emptyList()) }
    var odd by remember { mutableStateOf<Double?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }

    fun refreshOdd() {
        if (!store.hasPowerBinding || busy) return
        busy = true
        scope.launch {
            runCatching { repo.powerOdd() }
                .onSuccess { odd = it; msg = "" }
                .onFailure { msg = it.message ?: "查询失败" }
            busy = false
        }
    }

    LaunchedEffect(Unit) { refreshOdd() }

    Column(
        Modifier
            .fillMaxSize()
            .background(ds.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Text("宿舍电费", fontSize = 35.sp, fontWeight = FontWeight.Bold, color = ds.text)
        Text("完美校园智能水电 · 本机直连查询", fontSize = 14.sp, color = ds.sub, modifier = Modifier.padding(top = 6.dp, bottom = 20.dp))

        if (store.hasPowerBinding) {
            GlassCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(store.powerRoomName, fontSize = 14.sp, color = ds.sub)
                    Text(
                        odd?.let { "%.1f".format(it) } ?: if (busy) "…" else "--",
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = ds.accent,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text("剩余电量（度）", fontSize = 12.sp, color = ds.sub)
                }
            }
            Spacer(Modifier.height(12.dp))
            GlassCard(Modifier.clickable { refreshOdd() }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(if (busy) "查询中…" else "立即刷新", fontSize = 16.sp, color = ds.text)
                        Text("每 15 分钟后台巡检，低电量/掉电异常会推送提醒", fontSize = 12.sp, color = ds.sub)
                    }
                    Text(">", fontSize = 18.sp, color = ds.sub)
                }
            }
        } else {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("绑定宿舍（一次即可）", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ds.text)
                    InputField(account, { account = it }, "水电账号（完美校园 outid，一般是身份证号）")
                    InputField(code, { code = it }, "学校代码（广科 1000145）", KeyboardType.Number)
                    Button("查询我的房间", enabled = !busy && account.isNotBlank() && code.toIntOrNull() != null) {
                        busy = true
                        msg = "查询房间中…"
                        scope.launch {
                            runCatching { repo.powerRooms(account.trim(), code.toInt()) }
                                .onSuccess {
                                    rooms = it
                                    msg = if (it.isEmpty()) "该账号名下没有房间，检查账号与学校代码" else ""
                                }
                                .onFailure { msg = it.message ?: "查询失败" }
                            busy = false
                        }
                    }
                }
            }
            rooms.forEach { room ->
                GlassCard(
                    Modifier
                        .padding(top = 10.dp)
                        .clickable {
                            repo.savePowerBinding(account.trim(), code.toInt(), room)
                            rooms = emptyList()
                            refreshOdd()
                        },
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(room.name, fontSize = 14.sp, color = ds.text, modifier = Modifier.weight(1f))
                        Text("点击绑定", fontSize = 12.sp, color = ds.accent)
                    }
                }
            }
        }

        if (msg.isNotEmpty()) {
            Text(msg, fontSize = 12.sp, color = ds.sub, modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp))
        }
        Spacer(Modifier.height(110.dp))
    }
}
