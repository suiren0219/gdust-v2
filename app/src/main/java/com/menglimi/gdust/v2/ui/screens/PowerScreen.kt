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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
            val weekUse = detail?.weekUse.orEmpty()
            // 预计可用天数：近 7 日平均日耗为 0 时（如刚充值）不显示估算，避免误导
            val avgDaily = weekUse.map { it.second }.takeIf { it.isNotEmpty() }?.average()?.takeIf { it > 0.01 }
            val daysLeft = avgDaily?.let { detail?.odd?.div(it) }
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
                    Row(
                        Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        detail?.todayUse?.let { Text("今日 %.1f 度".format(it), fontSize = 12.sp, color = ds.sub) }
                        daysLeft?.let {
                            Text(
                                "约可用 %.0f 天".format(it),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (it < 3) ds.danger else ds.sub,
                            )
                        }
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
                        UsageChart(samples.takeLast(24), store.warnThreshold)
                        Text("共 ${samples.size} 个采样点 · 每 ${store.sampleIntervalMinutes} 分钟一次 · 虚线为预警线（${store.warnThreshold.toInt()} 度）", fontSize = 11.sp, color = ds.sub)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // 接口自带近 7 日
            val week = detail?.weekUse.orEmpty()
            if (week.isNotEmpty()) {
                val maxUse = week.maxOf { it.second }.coerceAtLeast(0.1)
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("近 7 日用电（接口自带）", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ds.text)
                        week.forEach { (day, use) ->
                            Column {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(day, fontSize = 13.sp, color = ds.text)
                                    Text("%.1f 度".format(use), fontSize = 13.sp, color = ds.sub)
                                }
                                // 用量比例条：一眼看出哪天用电最多
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 3.dp)
                                        .height(4.dp)
                                        .background(ds.sub.copy(alpha = 0.1f), androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth((use / maxUse).toFloat().coerceIn(0.02f, 1f))
                                            .height(4.dp)
                                            .background(ds.accent.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                                    )
                                }
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

/**
 * 电量走势图（不引图表库）：
 * Y 轴刻度数值 + X 轴时间刻度 + 网格线 + 面积渐变 + 预警阈值虚线 + 最新值标签。
 * 数据点不足时退化为提示文案，不画空轴。
 */
@Composable
private fun UsageChart(samples: List<Store.Sample>, warnThreshold: Float) {
    val ds = LocalDs.current
    val values = samples.map { it.odd.toFloat() }
    val minV = values.min()
    val maxV = values.max()
    // 纵轴留 8% 余量并取整刻度，避免数据贴边
    val lo = (minV - (maxV - minV).coerceAtLeast(1f) * 0.1f).let { kotlin.math.floor(it * 2f) / 2f }
    val hi = (maxV + (maxV - minV).coerceAtLeast(1f) * 0.1f).let { kotlin.math.ceil(it * 2f) / 2f }
    val span = (hi - lo).coerceAtLeast(0.5f)

    // 横轴时间刻度：取首/1/3、2/3/尾共 3~4 个采样点标 HH:mm
    val xLabels = remember(samples) {
        val n = samples.size
        val idx = when {
            n <= 4 -> samples.indices.toList()
            else -> listOf(0, n / 3, n * 2 / 3, n - 1).distinct()
        }
        val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
        idx.map { i -> i to fmt.format(java.util.Date(samples[i].time)) }
    }

    Column {
        Row(Modifier.fillMaxWidth().height(150.dp)) {
            // Y 轴刻度：lo / 中点 / hi 三个数值标签
            Column(
                Modifier.width(34.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text("%.0f".format(hi), fontSize = 10.sp, color = ds.sub)
                Text("%.0f".format((lo + hi) / 2f), fontSize = 10.sp, color = ds.sub)
                Text("%.0f".format(lo), fontSize = 10.sp, color = ds.sub)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight().padding(start = 6.dp)) {
                val w = size.width
                val h = size.height
                val left = 8f
                val plotW = w - left
                fun y(v: Float) = h - (v - lo) / span * (h - 20f) - 10f
                fun x(i: Int) = left + if (values.size > 1) i * plotW / (values.size - 1) else plotW / 2f

                // 水平网格线（3 条，最淡）
                listOf(hi, (lo + hi) / 2f, lo).forEach { v ->
                    drawLine(
                        color = ds.sub.copy(alpha = 0.12f),
                        start = Offset(left, y(v)), end = Offset(w, y(v)),
                        strokeWidth = 1f,
                    )
                }
                // 低电量阈值虚线（越界才画，避免噪音）
                if (warnThreshold in lo..hi) {
                    val ty = y(warnThreshold)
                    val seg = 10f
                    var px = left
                    while (px < w) {
                        drawLine(
                            color = ds.danger.copy(alpha = 0.5f),
                            start = Offset(px, ty),
                            end = Offset(minOf(px + seg, w), ty),
                            strokeWidth = 2f,
                        )
                        px += seg * 2
                    }
                }
                // 面积渐变（从折线到底部），然后折线
                val pts = values.mapIndexed { i, v -> Offset(x(i), y(v)) }
                val area = Path().apply {
                    moveTo(pts.first().x, h - 10f)
                    pts.forEach { lineTo(it.x, it.y) }
                    lineTo(pts.last().x, h - 10f)
                    close()
                }
                drawPath(
                    area,
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to ds.accent.copy(alpha = 0.18f),
                        1f to ds.accent.copy(alpha = 0f),
                    ),
                )
                val line = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    pts.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(line, color = ds.accent, style = Stroke(width = 3.5f))
                // 只在端点画圆点，中间点保持干净
                drawCircle(color = ds.accent, radius = 4.5f, center = pts.last())
                drawCircle(color = ds.card, radius = 2.5f, center = pts.last())
                pts.takeIf { it.size <= 6 }?.forEach { drawCircle(color = ds.accent, radius = 3f, center = it) }
            }
        }
        // X 轴时间刻度
        Row(Modifier.fillMaxWidth().padding(start = 40.dp, top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            xLabels.forEach { (_, label) ->
                Text(label, fontSize = 10.sp, color = ds.sub)
            }
        }
        // 最新值 + 区间摘要
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .background(ds.accent.copy(alpha = 0.14f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text("最新 %.1f 度".format(values.last()), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ds.accent)
            }
            Text(
                "%.0f 度/小时".format(if (values.size > 1) kotlin.math.abs(values.last() - values.first()) / ((samples.last().time - samples.first().time) / 3600000f).coerceAtLeast(0.25f) else 0f),
                fontSize = 11.sp, color = ds.sub,
            )
        }
    }
}
