package com.menglimi.gdust.v2.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.PowerDetail
import com.menglimi.gdust.v2.data.PowerRoom
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.data.Store
import com.menglimi.gdust.v2.data.isNetworkDown
import com.menglimi.gdust.v2.ui.GlassCard
import com.menglimi.gdust.v2.ui.LocalDs
import com.menglimi.gdust.v2.ui.pageBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.preference.ArrowPreference

/**
 * Layout adapted from NexioSchedule, revision 28a99a3:
 * ui-shared/.../ui/screens/SettingsScreen.kt and ui/activities/PreferenceSettingsScreen.kt.
 * Uses the same large-title, grouped-card and Miuix preference rhythm with real power data.
 */
@Composable
fun PowerScreen(repo: Repository) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
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
    var sampleIntervalLocal by remember { mutableStateOf(store.sampleIntervalMinutes) }
    var hasBinding by remember { mutableStateOf(store.hasPowerBinding) }
    var editingBinding by remember { mutableStateOf(false) }
    var roomName by remember { mutableStateOf(store.powerRoomName) }
    var roomQueryAccount by remember { mutableStateOf("") }
    var roomQueryCode by remember { mutableStateOf(0) }
    var updatedAt by remember { mutableStateOf<Long?>(null) }

    fun refresh() {
        if (!hasBinding || busy) return
        busy = true
        msg = ""
        netDown = false
        scope.launch {
            try {
                val result = repo.powerDetail()
                detail = result
                result.odd?.let { store.addSample(it) }
                updatedAt = System.currentTimeMillis()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                netDown = error.isNetworkDown()
                msg = error.message ?: "查询失败"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }
    val samples = remember(detail, busy, roomName) { store.loadSamples().sortedBy { it.time } }
    // 走势图展示跨度（天）：1/3/7/30，实际点数=天数×每天采样数
    var chartDays by remember { mutableStateOf(1) }
    val pullState = remember { androidx.compose.material3.pulltorefresh.PullToRefreshState() }

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = busy && hasBinding && !editingBinding,
        onRefresh = { if (!editingBinding) refresh() },
        state = pullState,
        modifier = Modifier.fillMaxSize(),
    ) {
    Column(
        Modifier
            .fillMaxSize()
            .background(pageBackground())
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("宿舍电费", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = ds.text, modifier = Modifier.weight(1f))
            if (hasBinding && !editingBinding) {
                IconButton(onClick = { refresh() }, enabled = !busy, modifier = Modifier.size(48.dp)) {
                    Icon(MiuixIcons.Refresh, contentDescription = "刷新电量", tint = ds.accent, modifier = Modifier.size(24.dp))
                }
            }
        }
        Text(
            if (hasBinding) roomName.ifBlank { "已绑定宿舍" } else "绑定宿舍，查看电量与用电趋势",
            fontSize = 14.sp, color = ds.sub, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
        )

        if (msg.isNotEmpty()) {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("查询未完成", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = ds.danger)
                    Text(msg, fontSize = 14.sp, color = ds.sub)
                    if (hasBinding && !editingBinding) Button("重新查询", enabled = !busy) { refresh() }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (hasBinding && !editingBinding) {
            val weekUse = detail?.weekUse.orEmpty()
            // 预计可用天数：近 7 日平均日耗为 0 时（如刚充值）不显示估算，避免误导
            val avgDaily = weekUse.map { it.second }.takeIf { it.isNotEmpty() }?.average()?.takeIf { it > 0.01 }
            val daysLeft = avgDaily?.let { detail?.odd?.div(it) }
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("剩余电量", fontSize = 14.sp, color = ds.sub)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            detail?.odd?.let { "%.1f".format(it) } ?: if (busy) "查询中" else "--",
                            fontSize = if (detail?.odd == null && busy) 32.sp else 56.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (detail?.odd?.let { it < store.warnThreshold } == true) ds.danger else ds.text,
                        )
                        Text("度", fontSize = 17.sp, color = ds.sub, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp))
                    }
                    Text(
                        when {
                            busy -> "正在更新电量"
                            msg.isNotEmpty() && detail != null -> "更新失败，保留上次查询结果"
                            updatedAt != null -> "更新于 " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
                                .format(java.util.Date(updatedAt!!))
                            else -> "等待查询"
                        },
                        fontSize = 13.sp, color = ds.sub,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            GlassCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PowerMetric("今日用电", detail?.todayUse?.let { "%.1f 度".format(it) } ?: "--", Modifier.weight(1f))
                    PowerMetric("预计可用", daysLeft?.let { "约 %.0f 天".format(it) } ?: "--", Modifier.weight(1f), warn = daysLeft?.let { it < 3 } == true)
                    PowerMetric("预警电量", "%.0f 度".format(store.warnThreshold), Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(20.dp))

            // 最近几小时折线（App 自采样序列）
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("电量走势", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = ds.text)
                    SegmentedChoice(
                        options = listOf(1 to "1 天", 3 to "3 天", 7 to "7 天", 30 to "30 天"),
                        selected = chartDays,
                        onSelect = { chartDays = it },
                    )
                    // 先按时间窗口过滤，再检查空态，不能对空集合调用 min/max。
                    val cutoff = System.currentTimeMillis() - chartDays * 24L * 3600 * 1000
                    val ranged = samples.filter { it.time >= cutoff }.takeLast(240)
                    val coveredHours = if (ranged.size >= 2) {
                        ((ranged.last().time - ranged.first().time) / 3600000.0).coerceAtLeast(0.0)
                    } else 0.0
                    UsageChart(ranged, store.warnThreshold)
                    Text(
                        "近 $chartDays 天 · ${ranged.size} 个采样点 · 覆盖 %.1f 小时".format(coveredHours),
                        fontSize = 13.sp, color = ds.sub,
                    )
                    Text(
                        "采样间隔 $sampleIntervalLocal 分钟，保留最近 30 天；预警线 ${store.warnThreshold.toInt()} 度。",
                        fontSize = 12.sp, lineHeight = 17.sp, color = ds.sub,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // 接口自带近 7 日（竖柱状）：明确「哪一天 · 多少度 · 哪天是今天」
            val week = detail?.weekUse.orEmpty()
            if (week.isNotEmpty()) {
                val maxUse = week.maxOf { it.second }.coerceAtLeast(0.1)
                val today = java.time.LocalDate.now()
                val dowNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
                // weekday 字段有两种历史格式：中文「星期四」/「四」，或日期「11/10」「11-10」「2026-11-10」
                fun parseDay(day: String): Triple<String, String, Boolean> {
                    val d = day.trim()
                    val cnMap = mapOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7)
                    if (cnMap.containsKey(d) || d.startsWith("星期") || d.startsWith("周")) {
                        val core = d.removePrefix("星期").removePrefix("周")
                        val dow = cnMap[core] ?: return Triple(d, "", false)
                        val name = dowNames[dow - 1]
                        return Triple(name, "", dow == today.dayOfWeek.value)
                    }
                    val date = runCatching {
                        when {
                            d.matches(Regex("\\d{4}-\\d{1,2}-\\d{1,2}")) -> java.time.LocalDate.parse(d)
                            d.matches(Regex("\\d{1,2}/\\d{1,2}")) -> java.time.LocalDate.parse(
                                "${today.year}/${d}", java.time.format.DateTimeFormatter.ofPattern("yyyy/M/d"))
                            d.matches(Regex("\\d{1,2}-\\d{1,2}")) -> java.time.LocalDate.parse(
                                "${today.year}-${d}", java.time.format.DateTimeFormatter.ofPattern("yyyy-M-d"))
                            else -> null
                        }
                    }.getOrNull() ?: return Triple(d, "", false)
                    return Triple(dowNames[date.dayOfWeek.value - 1], "${date.monthValue}/${date.dayOfMonth}", date == today)
                }
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("近 7 日用电", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = ds.text, modifier = Modifier.weight(1f))
                            Text("峰值 %.1f 度".format(maxUse), fontSize = 12.sp, color = ds.sub)
                        }
                        Text(
                            "每日用电量（度）",
                            fontSize = 13.sp, color = ds.sub,
                        )
                        // 7 根等宽柱随可用宽度分配空间，兼容窄屏与24dp页面边距。
                        Row(
                            Modifier.fillMaxWidth().height(112.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            week.forEach { (day, use) ->
                                val (dayName, dayDate, isToday) = parseDay(day)
                                val ratio = (use / maxUse).toFloat().coerceIn(0f, 1f)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .then(
                                            // 今天整列加淡色带，一眼定位
                                            if (isToday) Modifier.background(
                                                ds.accent.copy(alpha = 0.12f),
                                                androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                            ) else Modifier
                                        )
                                        .padding(vertical = 3.dp),
                                ) {
                                    Text(
                                        "%.1f".format(use),
                                        fontSize = 9.sp,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isToday || use >= maxUse) ds.accent else ds.sub,
                                        maxLines = 1,
                                    )
                                    Box(
                                        Modifier
                                            .padding(top = 3.dp)
                                            .width(if (isToday) 30.dp else 26.dp)
                                            .height((66f * ratio).coerceAtLeast(8f).dp)
                                            .then(
                                                if (isToday) Modifier.border(
                                                    1.5.dp,
                                                    Color.White.copy(alpha = 0.75f),
                                                    androidx.compose.foundation.shape.RoundedCornerShape(7.dp),
                                                ) else Modifier
                                            )
                                            .background(
                                                when {
                                                    isToday -> ds.accent
                                                    use >= maxUse -> ds.accent.copy(alpha = 0.75f)
                                                    else -> ds.accent.copy(alpha = 0.35f)
                                                },
                                                androidx.compose.foundation.shape.RoundedCornerShape(7.dp),
                                            ),
                                    )
                                    Text(
                                        if (isToday) "今天" else dayName,
                                        fontSize = 9.sp,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isToday) ds.accent else ds.sub,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                    if (dayDate.isNotBlank()) {
                                        Text(
                                            dayDate, fontSize = 8.sp, color = ds.sub,
                                            maxLines = 1, softWrap = false,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            SectionHeader("查询与采样")
            SettingGroup {
                ArrowPreference(
                    title = if (busy) "正在刷新" else "立即刷新",
                    summary = "从完美校园更新电量", onClick = { refresh() }, enabled = !busy,
                )
                ArrowPreference(
                    title = "重新绑定宿舍", summary = roomName, enabled = !busy,
                    onClick = { editingBinding = true; rooms = emptyList(); msg = "" },
                )
            }
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("采样间隔", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = ds.text)
                    Text("每 $sampleIntervalLocal 分钟记录电量，后台最低 15 分钟巡检。", fontSize = 14.sp, color = ds.sub)
                    SegmentedChoice(
                        options = listOf(15, 30, 60, 120).map { it to "${it}分" },
                        selected = sampleIntervalLocal,
                        onSelect = {
                            sampleIntervalLocal = it
                            store.sampleIntervalMinutes = it
                            // 链式调度按新间隔立即重排，不用等下次启动
                            runCatching {
                                com.menglimi.gdust.v2.work.PowerWorker.schedule(
                                    ctx,
                                )
                            }
                            intervalSaved = true
                        },
                    )
                    if (intervalSaved) Text("已保存", fontSize = 13.sp, color = ds.accent)
                }
            }
        } else {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(if (hasBinding) "重新绑定宿舍" else "绑定宿舍", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = ds.text)
                    Text("使用完美校园水电账号查询，选定房间后保存到本机。", fontSize = 14.sp, color = ds.sub)
                    InputField(account, { account = it; rooms = emptyList() }, "水电账号（完美校园 outid）")
                    InputField(code, { code = it; rooms = emptyList() }, "学校代码（广科 1000145）", KeyboardType.Number)
                    Button(
                        if (busy) "正在查询房间" else "查询我的房间",
                        enabled = !busy && account.isNotBlank() && (code.toIntOrNull() ?: 0) > 0,
                    ) {
                        // 保留本次查询参数，避免输入变更后把房间绑定到另一个账号。
                        val requestedAccount = account.trim()
                        val requestedCode = code.toIntOrNull() ?: return@Button
                        busy = true
                        rooms = emptyList()
                        msg = ""
                        netDown = false
                        scope.launch {
                            try {
                                val result = repo.powerRooms(requestedAccount, requestedCode)
                                if (account.trim() == requestedAccount && code.toIntOrNull() == requestedCode) {
                                    roomQueryAccount = requestedAccount
                                    roomQueryCode = requestedCode
                                    rooms = result
                                    if (result.isEmpty()) msg = "该账号名下没有房间，请检查账号与学校代码。"
                                }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                netDown = error.isNetworkDown()
                                msg = error.message ?: "查询失败"
                            } finally {
                                busy = false
                            }
                        }
                    }
                    if (hasBinding) {
                        Button("保留当前绑定", enabled = !busy) {
                            editingBinding = false
                            account = store.powerAccount
                            code = store.powerCode.toString()
                            rooms = emptyList()
                            msg = ""
                        }
                    }
                }
            }
            if (rooms.isNotEmpty()) {
                SectionHeader("选择房间")
                SettingGroup {
                    rooms.forEach { room ->
                        ArrowPreference(
                            title = room.name, summary = "绑定此房间", enabled = !busy,
                            onClick = {
                                repo.savePowerBinding(roomQueryAccount, roomQueryCode, room)
                                account = roomQueryAccount
                                code = roomQueryCode.toString()
                                roomName = room.name
                                hasBinding = true
                                editingBinding = false
                                detail = null
                                updatedAt = null
                                rooms = emptyList()
                                refresh()
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.navigationBarsPadding().height(110.dp))
    }
    } // PullToRefreshBox

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
    if (samples.size < 2) {
        Text(
            "这个时间范围内的采样还不足，稍后刷新或选择更长时间查看。",
            fontSize = 14.sp, lineHeight = 20.sp, color = ds.sub,
            modifier = Modifier.padding(vertical = 20.dp),
        )
        return
    }
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
    // 点按选中：点最近的数据点查看具体时间与数值（-1 = 未选中，显示最新值标签）
    var selectedIndex by remember(samples) { mutableStateOf(-1) }
    val fmtFull = remember { java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA) }
    val shownIndex = if (selectedIndex in values.indices) selectedIndex else values.size - 1

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
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 6.dp)
                    .semantics { contentDescription = "电量折线图，共 ${samples.size} 个采样点，最新 %.1f 度".format(values.last()) }
                    .pointerInput(samples) {
                        detectTapGestures { tap ->
                            // 再点同一采样点恢复最新值；数据更新时重启手势，避免保留旧列表索引。
                            val plotW = (size.width - 8f).coerceAtLeast(1f)
                            val rel = ((tap.x - 8f) / plotW).coerceIn(0f, 1f)
                            val index = (rel * (values.size - 1)).toInt().coerceIn(values.indices)
                            selectedIndex = if (selectedIndex == index) -1 else index
                        }
                    },
            ) {
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
                // 选中点/最新点高亮：选中的白心大圆，未选中时中间点保持干净
                if (selectedIndex in values.indices) {
                    drawCircle(color = ds.accent, radius = 6f, center = pts[selectedIndex])
                    drawCircle(color = ds.card, radius = 3f, center = pts[selectedIndex])
                } else {
                    drawCircle(color = ds.accent, radius = 4.5f, center = pts.last())
                    drawCircle(color = ds.card, radius = 2.5f, center = pts.last())
                }
                pts.takeIf { it.size <= 6 }?.forEach { drawCircle(color = ds.accent, radius = 3f, center = it) }
            }
        }
        // X 轴时间刻度
        Row(Modifier.fillMaxWidth().padding(start = 40.dp, top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            xLabels.forEach { (_, label) ->
                Text(label, fontSize = 10.sp, color = ds.sub)
            }
        }
        // 最新值 / 选中点详情
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${fmtFull.format(java.util.Date(samples[shownIndex].time))} · %.1f 度".format(values[shownIndex]),
                fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ds.accent,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "平均变化 %.1f 度/小时".format(kotlin.math.abs(values.last() - values.first()) /
                        ((samples.last().time - samples.first().time) / 3600000f).coerceAtLeast(0.25f)),
                    fontSize = 12.sp, color = ds.sub, modifier = Modifier.weight(1f),
                )
                if (selectedIndex in values.indices) {
                    Box(
                        Modifier.heightIn(min = 44.dp)
                            .clickable(role = Role.Button) { selectedIndex = -1 }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("回到最新", fontSize = 12.sp, color = ds.accent) }
                }
            }
        }
    }
}

/** 页面专用信息列，与余额卡分离，窄屏也能保留完整标签。 */
@Composable
private fun PowerMetric(label: String, value: String, modifier: Modifier = Modifier, warn: Boolean = false) {
    val ds = LocalDs.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 12.sp, color = ds.sub)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (warn) ds.danger else ds.text)
    }
}
