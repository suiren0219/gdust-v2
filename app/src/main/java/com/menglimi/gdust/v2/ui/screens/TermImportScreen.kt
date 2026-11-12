package com.menglimi.gdust.v2.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menglimi.gdust.v2.data.CalendarInfo
import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.ui.LocalDs
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 显示版本号，方便确认装的是哪一次构建。 */
private fun appVersionLabel(ctx: android.content.Context): String = runCatching {
    val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
    val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    "v${info.versionName}($code)"
}.getOrDefault("")

/** 把底层英文报错翻译成可操作中文（即使某个分支漏改 JS，也不会甩英文给用户）。 */
private fun localizeImportError(raw: String): String = when {
    raw.contains("Unexpected token", true) || raw.contains("is not valid JSON", true) ||
        raw.contains("Unexpected end", true) ->
        "教务返回的是网页而不是课表（当前入口没放行接口）。请在页面里进入「教务系统」后再读取；校外建议连校园网用直连同步。"
    raw.contains("Failed to fetch", true) || raw.contains("NetworkError", true) ->
        "网络请求失败（可能是断网或跨域拦截），换网络或入口后重试。"
    raw.contains("HTTP 5", true) || raw.contains("HTTP 4", true) ->
        "学校服务器返回错误（$raw），稍后重试。"
    else -> raw
}

/**
 * 教务学期导入（兼容路线）：在 [TeachingWebActivity] 里登录教务，回来用同一份 Cookie 读取正方学期响应。
 *
 * WebView 不放 Compose `AndroidView` 里——那里视口单位恒为 0，学校页面会白屏
 * （见 TeachingWebActivity / ChxWebActivity 注释与 debug 源集的 WebViewProbeActivity）。
 */
@Composable
fun TermImportScreen(
    repo: Repository,
    onBack: () -> Unit,
    onImported: (CalendarInfo, List<CourseBlock>) -> Unit,
) {
    val ds = LocalDs.current
    val hostContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val cached = remember { repo.store.loadCalendar() }
    var year by remember { mutableStateOf(cached?.year ?: LocalDate.now().let { if (it.monthValue < 8) it.year - 1 else it.year }.toString()) }
    var semester by remember { mutableStateOf(cached?.semester ?: "3") }
    var start by remember { mutableStateOf(repo.store.termStartDate.ifBlank { cached?.startDate.orEmpty() }) }
    var weeks by remember { mutableStateOf((repo.store.termWeeks.takeIf { it > 0 } ?: cached?.allWeek ?: 20).toString()) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var importTarget by remember { mutableStateOf<CalendarInfo?>(null) }

    // 网页登录在独立 Activity 完成；回来后再用 CookieManager 里的会话读取课表
    val browser = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val target = importTarget ?: return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK) {
            message = "已取消；未读取课表。旧课表保持不变。"
            return@rememberLauncherForActivityResult
        }
        busy = true
        message = "正在读取教务返回的实际周次…"
        scope.launch {
            runCatching { repo.importTermWithWebSession(target) }
                .onSuccess { blocks ->
                    busy = false
                    onImported(target, blocks)
                }
                .onFailure {
                    busy = false
                    message = localizeImportError(it.message ?: "课表解析失败，旧课表已保留")
                }
        }
    }

    Column(Modifier.fillMaxSize().background(ds.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(16.dp))
            PageTitle(
                "教务学期导入",
                "按拾光 GDUST 适配的正方接口读取 · ${appVersionLabel(hostContext)}",
                onBack,
            )
            Text(
                "这是兼容备用路线：在网页里登录教务后读取原始周次。\n" +
                    "扫码登录不用走这里——回登录页点「生成登录二维码」，直接、免验证码。",
                color = ds.sub, fontSize = 13.sp, lineHeight = 18.sp,
            )
            Spacer(Modifier.height(16.dp))
            InputField(year, { year = it }, "起始学年，如 2026", KeyboardType.Number)
            Spacer(Modifier.height(10.dp))
            SegmentedChoice(listOf("3" to "第一学期", "12" to "第二学期"), semester, { semester = it })
            Spacer(Modifier.height(10.dp))
            InputField(start, { start = it }, "第一周周一，如 2026-08-31")
            Spacer(Modifier.height(10.dp))
            InputField(weeks, { weeks = it }, "学期总周数", KeyboardType.Number)
            Spacer(Modifier.height(16.dp))
            Button(if (busy) "正在读取…" else "登录教务并导入", enabled = !busy) {
                runCatching {
                    require(year.matches(Regex("[0-9]{4}"))) { "请输入四位起始学年（如 2026）" }
                    require(start.isNotBlank()) { "请填写第一周周一日期（如 2026-08-31）" }
                    val date = runCatching { LocalDate.parse(start.trim()) }.getOrElse {
                        error("开学日期格式不对，应为 2026-08-31 这种")
                    }
                    require(date.dayOfWeek.value == 1) { "开学日期须为周一" }
                    val total = weeks.toIntOrNull() ?: error("请输入总周数")
                    require(total in 1..60) { "总周数须为 1–60" }
                    val current = (ChronoUnit.DAYS.between(date, LocalDate.now()) / 7 + 1).toInt().coerceIn(1, total)
                    importTarget = CalendarInfo(current, total, date.toString(), year, semester)
                    message = "在网页里登录学校门户 → 进入「教务系统」→ 点右上角「读取课表」返回。"
                    browser.launch(Intent(hostContext, TeachingWebActivity::class.java))
                }.onFailure { message = it.message ?: "学期设置无效" }
            }
            if (message.isNotEmpty()) Text(message, color = ds.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
}
