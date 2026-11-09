package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.PowerDetail
import com.menglimi.gdust.v2.data.PowerRoom
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.data.Store
import com.menglimi.gdust.v2.data.isNetworkDown
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
    var detail by remember { mutableStateOf<PowerDetail?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var netDown by remember { mutableStateOf(false) }
    var interval by remember { mutableStateOf(store.sampleIntervalMinutes.toString()) }
    var intervalSaved by remember { mutableStateOf(false) }

    fun refresh() {
        if (!store.hasPowerBinding || busy) return
        busy = true
        scope.launch {
            runCatching { repo.powerDetail() }
                .onSuccess { d ->
                    detail = d
                    d.odd?.let { store.addSample(it) }
                    msg = ""
                }
                .onFailure {
                    netDown = it.isNetworkDown()
                    msg = it.message ?: "查询失败"
                }
            busy = false
        }
    }

    LaunchedEffect(Unit) { refresh() }
    val samples = remember(detail, busy) { store.loadSamples() }

    Column(
        Modifier
            .fillMaxSize()
            .background(ds.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        PageTitle(title = "宿舍电费", subtitle = "完美校园智能水电 · 本机直连查询")

        if (store.hasPowerBinding) {
            GlassCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(store.powerRoomName, fontSize = 14.sp, color = ds.sub)
                    Text(
                        detail?.odd?.let { "%.1f".format(it) } ?: if (busy) "…" else "--",
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = ds.accent,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text("剩余电量（度）", fontSize = 12.sp, color = ds.sub)
                    detail?.todayUse?.let {
                        Text("今日用 %.1f 度".format(it), fontSize = 12.sp, color = ds.sub, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // 最近几小时折线（App 自采样序列）
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("最近电量走势", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ds.text)
                    if (samples.size < 2) {
                        Text("采样点还不够，等下一个间隔自动记点（或点下方刷新）", fontSize = 12.sp, color = ds.sub)
                    } else {
                        UsageChart(samples.takeLast(24))
                        Text("共 ${samples.size} 个采样点 · 每 ${store.sampleIntervalMinutes} 分钟一次", fontSize = 11.sp, color = ds.sub)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // 接口自带近 7 日
            val week = detail?.weekUse.orEmpty()
            if (week.isNotEmpty()) {
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("近 7 日用电（接口自带）", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ds.text)
                        week.forEach { (day, use) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(day, fontSize = 13.sp, color = ds.text)
                                Text("%.1f 度".format(use), fontSize = 13.sp, color = ds.sub)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // 采样间隔设置
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("采样间隔", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ds.text)
                    Text("15 / 30 / 60 / 120 分钟，后台按此间隔自动采样并画走势；下限 15 分钟", fontSize = 12.sp, color = ds.sub)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15, 30, 60, 120).forEach { m ->
                            val picked = store.sampleIntervalMinutes == m
                            Box(
                                Modifier
                                    .weight(1f)
                                    .background(
                                        if (picked) ds.accent else ds.card,
                                        androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                    )
                                    .clickable {
                                        store.sampleIntervalMinutes = m
                                        interval = m.toString()
                                        intervalSaved = true
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${m}m", fontSize = 13.sp, color = if (picked) androidx.compose.ui.graphics.Color.White else ds.text)
                            }
                        }
                    }
                    if (intervalSaved) Text("已保存 ✓", fontSize = 12.sp, color = ds.accent)
                }
            }
            Spacer(Modifier.height(12.dp))
            GlassCard(Modifier.clickable { refresh() }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (busy) "查询中…" else "立即刷新", fontSize = 16.sp, color = ds.text)
                    Text("›", fontSize = 18.sp, color = ds.sub)
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
                                .onFailure {
                                    netDown = it.isNetworkDown()
                                    msg = it.message ?: "查询失败"
                                }
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
                            refresh()
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

    NetworkDownDialog(visible = netDown, onDismiss = { netDown = false })
}

/** Canvas 手绘小时折线（不引图表库）。 */
@Composable
private fun UsageChart(samples: List<Store.Sample>) {
    val ds = LocalDs.current
    val values = samples.map { it.odd.toFloat() }
    val min = values.minOrNull() ?: 0f
    val max = values.maxOrNull() ?: 1f
    val span = (max - min).coerceAtLeast(1f)
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val w = size.width
        val h = size.height
        val stepX = if (values.size > 1) w / (values.size - 1) else w
        val pts = values.mapIndexed { i, v ->
            Offset(i * stepX, h - (v - min) / span * (h * 0.8f) - h * 0.1f)
        }
        val path = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, color = ds.accent, style = Stroke(width = 3f))
        pts.forEach { drawCircle(color = ds.accent, radius = 3.5f, center = it) }
        drawLine(
            color = ds.sub.copy(alpha = 0.3f),
            start = Offset(0f, h - 1f), end = Offset(w, h - 1f), strokeWidth = 1f,
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("%.1f".format(values.last()), fontSize = 11.sp, color = ds.sub)
        Text("%.1f° ~ %.1f°".format(min, max), fontSize = 11.sp, color = ds.sub)
    }
}
