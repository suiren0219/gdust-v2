package com.menglimi.gdust.v2.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * 广科数据层（v2 单文件，全部本机直连，不经过任何自建服务器）。
 *
 * 四方协议均为 2026-11-07/08 实测：
 * - CAS:  loginCode（验证码 base64+uuid）→ loginByAccount（明文 JSON）→ ticket
 * - 门户: user/login?loginCode=ticket → TOKEN 头 → 校历/课表/通知
 * - 水电: 完美校园 SWAEServlet，账号+customercode+roomverify 三静态值，无登录态
 * - LLM:  用户自填 OpenAI 兼容端点
 */

// ─────────────────────────────── 模型 ───────────────────────────────

data class CalendarInfo(
    val week: Int, val allWeek: Int, val startDate: String, val year: String, val semester: String,
)

data class Lesson(
    val week: Int, val day: Int, val section: Int,
    val name: String, val room: String, val teacher: String, val date: String,
    /** 门户原始单双周标记，仅作元数据；不能据此推断其他周。 */
    val weekParity: Int = 0,
)

/** 连堂合并后的整卡（数据层就完成，不交给 UI 拼）。 */
data class CourseBlock(
    val week: Int, val day: Int, val startSection: Int, val endSection: Int,
    val name: String, val room: String, val teacher: String, val date: String,
    val weekParity: Int = 0,
)

data class Notice(val title: String, val url: String, val type: String, val issuer: String?, val time: Long)
data class PowerRoom(val name: String, val verify: String)

/** 水电详情：剩余电量 + 接口自带近 7 日/今日/上月。 */
data class PowerDetail(
    val odd: Double?,
    val todayUse: Double?,
    val weekUse: List<Pair<String, Double>>,
    val monthUse: Double?,
    val sumBuy: Double?,
)

data class Captcha(val imageBytes: ByteArray, val uuid: String)

class AuthFailed(val code: Int, msg: String) : Exception(msg)

// ─────────────────────────────── 客户端 ───────────────────────────────

class GdustApi {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    companion object {
        const val CAS = "https://cas.gdust.edu.cn"
        const val PORTAL = "https://portal.gdust.edu.cn"
        private const val POWER_URL = "https://xqh5.17wanxiao.com/smartWaterAndElectricityService/SWAEServlet"
        private val TS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun jsonObj(text: String) = JsonParser.parseString(text).asJsonObject
    private fun str(v: Any?): String = "\"" + v.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun request(url: String, token: String? = null, postJson: String? = null, form: FormBody? = null): String {
        val b = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
        token?.let { b.header("TOKEN", it) }
        postJson?.let { b.post(it.toRequestBody("application/json; charset=utf-8".toMediaType())) }
        form?.let { b.post(it) }
        http.newCall(b.build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (token != null) schoolAuthFailure(r.code, text)?.let { throw it }
            if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}: ${text.take(120)}")
            return text
        }
    }

    // ── CAS ──

    suspend fun captcha(): Captcha = io {
        val o = jsonObj(request("$CAS/cas-api/cas/loginCode")).getAsJsonObject("data")
        val url = o.str("codeUrl").orEmpty()
        Captcha(
            imageBytes = java.util.Base64.getDecoder().decode(url.substringAfter("base64,")),
            uuid = o.str("uuid").orEmpty(),
        )
    }

    /** 账号密码登录，返回 ticket（GKCAS...）。验证码错误 code=30001 单独抛 AuthFailed。 */
    suspend fun login(account: String, password: String, code: String, uuid: String): String = io {
        val body = """{"loginName":${str(account)},"loginPwd":${str(password)},"code":${str(code)},"uuid":${str(uuid)}}"""
        val resp = jsonObj(request("$CAS/cas-api/cas/loginByAccount", postJson = body))
        if ((resp.num("code") ?: 0) != 0) throw AuthFailed(resp.num("code") ?: 0, resp.str("msg") ?: "登录失败")
        resp.str("data").orEmpty()
    }

    // ── 门户 ──

    suspend fun exchangeToken(ticket: String): String = io {
        // ticket 是 CAS 票据（常含 +/=// 等 base64 字符）：必须用 HttpUrl 编码，
        // 直接拼进查询串会被解析成空格/分隔符 → 门户偶发拒绝，表现为「概率性换取失败」
        val url = okhttp3.HttpUrl.Builder()
            .scheme("https").host("portal.gdust.edu.cn")
            .addPathSegments("smart-admin-api/user/login")
            .addQueryParameter("loginCode", ticket.trim())
            .addQueryParameter("appId", "portalRemote")
            .build().toString()
        val root = jsonObj(request(url))
        val token = root.getAsJsonObject("data")?.getAsJsonObject("userBase")?.str("token").orEmpty()
        if (token.isBlank()) error("门户未返回 TOKEN（票据可能已被使用或过期）")
        token
    }

    suspend fun userInfo(token: String): Pair<String, String> = io {
        val d = jsonObj(request("$PORTAL/smart-admin-api/app/userInfo/addition", token)).getAsJsonObject("data")
        (d.str("jobNum").orEmpty()) to (d.str("realName").orEmpty())
    }

    suspend fun calendar(token: String, job: String): CalendarInfo = io {
        val d = jsonObj(request("$PORTAL/smart-admin-api/app/zf/get_school_calendar?jobNumber=$job", token)).getAsJsonObject("data")
        CalendarInfo(
            week = d.num("week") ?: 0, allWeek = d.num("allWeek") ?: 0,
            startDate = d.str("startTime").orEmpty(),
            year = d.str("year").orEmpty(), semester = d.str("semester").orEmpty(),
        )
    }

    suspend fun lessons(token: String, job: String): List<Lesson> = io {
        val d = jsonObj(request("$PORTAL/smart-admin-api/app/zf/get_student_course?jobNumber=$job&week=&year=&semester=", token)).getAsJsonObject("data")
        val arr = d.getAsJsonArray("courseList") ?: return@io emptyList()
        arr.mapNotNull { e ->
            val o = e.asJsonObject
            val name = o.str("courseName") ?: return@mapNotNull null
            Lesson(
                week = o.num("week") ?: 0,
                day = o.num("dayWeek") ?: 0,
                section = o.num("whichSection") ?: 0,
                name = name,
                room = o.str("classroomName").orEmpty(),
                teacher = o.str("teacher").orEmpty(),
                date = o.str("courseDate").orEmpty(),
                weekParity = when (o.str("singleOrDoubleWeek")?.trim()) {
                    "单" -> 1
                    "双" -> 2
                    else -> 0
                },
            )
        }
    }

    suspend fun notices(token: String, job: String): List<Notice> = io {
        parsePortalNotices(request("$PORTAL/smart-admin-api/app/ding/get_announcement_items?jobNumber=$job", token))
    }

    /** 教务处/官网新闻；门户通知继续使用 notices(token, job)。 */
    suspend fun news(token: String, source: NoticeSource): List<Notice> = io {
        PortalNews.parse(request(PortalNews.requestUrl(source), token), source)
    }

    // ── 水电 ──

    private fun powerPost(cmd: String, param: String, customerCode: Int): String {
        val form = FormBody.Builder()
            .add("param", param).add("customercode", customerCode.toString())
            .add("method", cmd).add("command", "JBSWaterElecService").build()
        return request(POWER_URL, form = form)
    }

    suspend fun powerRooms(account: String, customerCode: Int): List<PowerRoom> = io {
        val outer = jsonObj(powerPost("getbindroom", """{"cmd":"getbindroom","account":${str(account)}}""", customerCode))
        val arr = jsonObj(outer.str("body").orEmpty()).getAsJsonArray("roomlist") ?: return@io emptyList()
        arr.map { e ->
            val o = e.asJsonObject
            PowerRoom(
                name = o.str("roomfullname").orEmpty(),
                verify = o.str("roomverify").orEmpty(),
            )
        }
    }

    /** 完整水电详情：剩余电量 + 今日/近7日/上月/累购（字段来自 -seed 插件 main.py 实测）。 */
    suspend fun powerDetail(account: String, customerCode: Int, roomVerify: String): PowerDetail = io {
        val ts = LocalDateTime.now().format(TS)
        val param = """{"cmd":"h5_getstuindexpage","account":${str(account)},"roomverify":${str(roomVerify)},"timestamp":${str(ts)}}"""
        val outer = jsonObj(powerPost("h5_getstuindexpage", param, customerCode))
        val inner = jsonObj(outer.str("body").orEmpty())
        val mod = inner.getAsJsonArray("modlist")?.firstOrNull()?.asJsonObject
            ?: inner
        fun dbl(o: com.google.gson.JsonObject?, vararg keys: String): Double? {
            if (o == null) return null
            for (k in keys) o.str(k)?.toDoubleOrNull()?.let { return it }
            return null
        }
        val odd = dbl(mod, "odd", "surpluselec", "surplusElec", "surplus_elec", "remain", "balance")
        val today = dbl(mod, "todayuse", "todayUse")
        val week = (mod.getAsJsonArray("weekuselist") ?: com.google.gson.JsonArray())
            .mapNotNull { e ->
                val o = e.asJsonObject
                val label = o.str("weekday") ?: return@mapNotNull null
                val use = dbl(o, "dayuse", "use") ?: return@mapNotNull null
                label.replace("星期", "") to use
            }
        val month = mod.getAsJsonArray("monthuselist")?.lastOrNull()?.asJsonObject
            ?.let { dbl(it, "monthuse") }
        val sumBuy = dbl(mod, "sumbuy")
        PowerDetail(odd, today, week, month, sumBuy)
    }

    suspend fun powerOdd(account: String, customerCode: Int, roomVerify: String): Double? =
        powerDetail(account, customerCode, roomVerify).odd

    // ── 钉钉扫码（SSE）──

    /** CAS 扫码事件流：HELLO(clientId) → 授权 → LOGIN_SUCCESS_TICKET。 */
    fun subscribeSse(): kotlinx.coroutines.flow.Flow<SseEvent> =
        kotlinx.coroutines.flow.callbackFlow {
            val req = Request.Builder()
                .url("$CAS/cas-api/sse/subscribe")
                .header("Accept", "text/event-stream")
                .get().build()
            val listener = object : okhttp3.sse.EventSourceListener() {
                override fun onEvent(
                    eventSource: okhttp3.sse.EventSource,
                    id: String?, type: String?, data: String,
                ) {
                    when (type) {
                        "HELLO" -> trySend(SseEvent.Hello(data))
                        "LOGIN_SUCCESS_TICKET" -> { trySend(SseEvent.Ticket(data)); close() }
                        "NEED_TO_BIND" -> trySend(SseEvent.NeedBind(data))
                    }
                }
                override fun onFailure(eventSource: okhttp3.sse.EventSource, t: Throwable?, response: okhttp3.Response?) {
                    trySend(SseEvent.Error(t?.message ?: "SSE 连接失败")); close()
                }
                override fun onClosed(eventSource: okhttp3.sse.EventSource) { close() }
            }
            val es = okhttp3.sse.EventSources.createFactory(http).newEventSource(req, listener)
            awaitClose { es.cancel() }
        }

    fun mobieAuthUrl(clientId: String) = "$CAS/cas/mobieAuth?clientId=$clientId"

    // ── LLM（用户自填配置）──

    suspend fun summarize(base: String, key: String, model: String, notices: List<Notice>): String = io {
        if (notices.isEmpty()) return@io "最近没有公告可总结。"
        val items = notices.take(15).joinToString("\n") { "- [${it.type}] ${it.title}" }
        val system = "你是广东科技学院的学生助手。把学校通知公告标题整理成简明中文要点：" +
            "按类别分组，每条一句话，突出停课/考试/缴费/截止等与学生相关事项，250 字以内。"
        // 用 Gson 构造 JSON：手拼字符串没转义公告标题里的换行/引号时，
        // 请求体会变成非法 JSON（服务端报「请求体必须是 JSON 对象」）
        val body = com.google.gson.Gson().toJson(
            mapOf(
                "model" to model,
                "messages" to listOf(
                    mapOf("role" to "system", "content" to system),
                    mapOf("role" to "user", "content" to "最近的学校通知公告：\n$items"),
                ),
                "max_tokens" to 2048,
                "temperature" to 0.3,
            )
        )
        val root = base.trim().trimEnd('/')
        val endpoint = when {
            root.endsWith("/chat/completions") -> root
            root.endsWith("/v1") -> "$root/chat/completions"
            else -> "$root/v1/chat/completions"
        }
        val req = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $key")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw RuntimeException("LLM HTTP ${r.code}: ${text.take(120)}")
            jsonObj(text).getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("message")?.str("content")
                ?: throw RuntimeException("LLM 响应缺少 content")
        }
    }
}

/** 门户按节返回 → 数据层合并连堂：同天同课同教室同教师且节次连续且同周 → 一张整卡。 */
fun mergeLessons(lessons: List<Lesson>): List<CourseBlock> {
    val out = mutableListOf<CourseBlock>()
    lessons.groupBy { it.day }.values.forEach { dayLessons ->
        var cur: CourseBlock? = null
        dayLessons.sortedBy { it.section }.forEach { l ->
            val p = cur
            val merge = p != null && p.name == l.name && p.room == l.room &&
                p.teacher == l.teacher && p.week == l.week && p.endSection + 1 == l.section
            if (merge) {
                cur = p!!.copy(endSection = l.section)
            } else {
                p?.let { out.add(it) }
                cur = CourseBlock(l.week, l.day, l.section, l.section, l.name, l.room, l.teacher, l.date, l.weekParity)
            }
        }
        cur?.let { out.add(it) }
    }
    return out
}


sealed class SseEvent {
    data class Hello(val clientId: String) : SseEvent()
    data class Ticket(val ticket: String) : SseEvent()
    data class NeedBind(val unionId: String) : SseEvent()
    data class Error(val message: String) : SseEvent()
}

/** 沿 cause 链找 IOException：连不上/超时/DNS 失败都算「学校官网不可达」，供 UI 弹窗分流。 */
fun Throwable.isNetworkDown(): Boolean {
    var t: Throwable? = this
    while (t != null) {
        if (t is java.io.IOException) return true
        t = t.cause
    }
    return false
}

// ── JSON 安全取值：门户大量字段是字面 null（Gson JsonNull），?.asString 挡不住会抛 "JsonNull" ──
internal fun JsonObject.str(key: String): String? = get(key)?.takeIf { !it.isJsonNull }?.asString
internal fun JsonObject.num(key: String): Int? = get(key)?.takeIf { !it.isJsonNull }?.asInt
internal fun JsonObject.numL(key: String): Long? = get(key)?.takeIf { !it.isJsonNull }?.asLong
