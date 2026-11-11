package com.menglimi.gdust.v2.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 本机存储（v2）：凭据/会话/课表缓存/待办/设置全部只在本机。
 * 敏感项走 EncryptedSharedPreferences；课表/待办为非敏感缓存走普通 prefs。
 */
class Store(context: Context) {

    private val app = context.applicationContext
    private val gson = Gson()

    private val secure = EncryptedSharedPreferences.create(
        app, "gdust_secure",
        MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val plain = app.getSharedPreferences("gdust_cache", Context.MODE_PRIVATE)

    private var String.s: String
        get() = secure.getString(this, null).orEmpty()
        set(v) = secure.edit().putString(this, v).apply()

    // ── 会话与凭据 ──
    var portalToken: String
        get() = "token".s
        set(v) { "token".s = v }
    /** CAS 登录票据：只用于换教务会话；票据过期由学校决定，不因超时删除。 */
    var casTicket: String
        get() = "ticket".s
        set(v) { "ticket".s = v }
    var jobNumber: String
        get() = "job".s
        set(v) { "job".s = v }
    var realName: String
        get() = "name".s
        set(v) { "name".s = v }

    // 水电绑定（三静态值）
    var powerAccount: String
        get() = "pacc".s
        set(v) { "pacc".s = v }
    var powerCode: Int
        get() = secure.getInt("pcode", 0)
        set(v) = secure.edit().putInt("pcode", v).apply()
    var powerRoomVerify: String
        get() = "prv".s
        set(v) { "prv".s = v }
    var powerRoomName: String
        get() = "prn".s
        set(v) { "prn".s = v }

    // LLM（用户自填，无内置默认）
    var llmBase: String
        get() = "lbase".s
        set(v) { "lbase".s = v }
    var llmKey: String
        get() = "lkey".s
        set(v) { "lkey".s = v }
    var llmModel: String
        get() = "lmodel".s
        set(v) { "lmodel".s = v }

    // ── 课表缓存 ──
    fun saveCalendar(cal: CalendarInfo) = plain.edit().putString("calendar", gson.toJson(cal)).apply()
    fun loadCalendar(): CalendarInfo? = plain.getString("calendar", null)?.let {
        runCatching { gson.fromJson(it, CalendarInfo::class.java) }.getOrNull()
    }

    val scheduleSource: String get() = plain.getString("schedule_source", "legacy") ?: "legacy"

    fun saveBlocks(blocks: List<CourseBlock>, source: String = "portal_week") = plain.edit()
        .putString("blocks", gson.toJson(blocks))
        .putString("schedule_source", source)
        .apply()

    fun loadBlocks(): List<CourseBlock> {
        val t = object : TypeToken<List<CourseBlock>>() {}.type
        val blocks = runCatching {
            gson.fromJson<List<CourseBlock>>(plain.getString("blocks", "[]"), t)
        }.getOrDefault(emptyList())
        if (scheduleSource != "legacy") return blocks
        // 旧版曾复制单周缓存：只有日期与真实周次一致的条目可以继续显示。
        val start = runCatching { java.time.LocalDate.parse(loadCalendar()?.startDate) }.getOrNull()
            ?: return emptyList()
        return blocks.filter { block ->
            val date = runCatching { java.time.LocalDate.parse(block.date) }.getOrNull()
            date != null && date.dayOfWeek.value == block.day &&
                java.time.temporal.ChronoUnit.DAYS.between(start, date) / 7 + 1 == block.week.toLong()
        }
    }

    // ── 待办 ──
    data class Todo(val id: Long, val text: String, val done: Boolean)

    fun loadTodos(): List<Todo> {
        val t = object : TypeToken<List<Todo>>() {}.type
        return runCatching { gson.fromJson<List<Todo>>(plain.getString("todos", "[]"), t) }
            .getOrDefault(emptyList())
    }

    fun saveTodos(items: List<Todo>) = plain.edit().putString("todos", gson.toJson(items)).apply()

    data class Sample(val time: Long, val odd: Double)

    /** 本地小时采样序列（App 自己攒，供折线图用；最多留 7 天）。 */
    fun loadSamples(): List<Sample> {
        val t = object : TypeToken<List<Sample>>() {}.type
        return runCatching { gson.fromJson<List<Sample>>(plain.getString("samples", "[]"), t) }
            .getOrDefault(emptyList())
    }

    /** 记录一个采样点（同一小时内只留最后一个）。 */
    fun addSample(odd: Double) {
        val now = System.currentTimeMillis()
        val hour = now / 3600000L
        val list = loadSamples().filterNot { it.time / 3600000L == hour }.toMutableList()
        list.add(Sample(now, odd))
        val cutoff = now - 7L * 24 * 3600 * 1000
        val kept = list.filter { it.time >= cutoff }
        plain.edit().putString("samples", gson.toJson(kept)).apply()
    }

    var uiStyle: String
        get() = plain.getString("ui_style", "SOFT") ?: "SOFT"
        set(v) = plain.edit().putString("ui_style", v).apply()

    /** 启动默认 tab：0 今日 / 1 课表 / 2 电费 / 3 待办。 */
    var startTab: Int
        get() = plain.getInt("start_tab", 1)
        set(v) = plain.edit().putInt("start_tab", v.coerceIn(0, 3)).apply()

    /** 莫奈取色：用系统壁纸色（Material You）生成整套 Ds 令牌；关=默认蓝。 */
    var monetEnabled: Boolean
        get() = plain.getBoolean("monet_enabled", false)
        set(v) = plain.edit().putBoolean("monet_enabled", v).apply()

    // ── 学期日历设置：只校正日期映射，不生成课程 ──
    /** 学期第一周周一，YYYY-MM-DD；空=用校历 startTime。 */
    var termStartDate: String
        get() = plain.getString("term_start", "") ?: ""
        set(v) = plain.edit().putString("term_start", v).apply()

    /** 学期总周数；0=用校历 allWeek。 */
    var termWeeks: Int
        get() = plain.getInt("term_weeks", 0)
        set(v) = plain.edit().putInt("term_weeks", v).apply()

    /** 手动指定当前周；0=用校历 week。 */
    var termCurrentWeek: Int
        get() = plain.getInt("term_week", 0)
        set(v) = plain.edit().putInt("term_week", v).apply()

    var sampleIntervalMinutes: Int
        get() = plain.getInt("sample_interval", 60)
        set(v) { plain.edit().putInt("sample_interval", v).apply(); publishPowerWorkerConfig() }

    var warnThreshold: Float
        get() = plain.getFloat("warn_th", 20f)
        set(v) { plain.edit().putFloat("warn_th", v).apply(); publishPowerWorkerConfig() }
    var warnDropRate: Float
        get() = plain.getFloat("warn_drop", 5f)
        set(v) { plain.edit().putFloat("warn_drop", v).apply(); publishPowerWorkerConfig() }

    /** 把绑定/阈值同步给 WorkManager 用的普通 prefs（Worker 不读加密存储）。 */
    fun publishPowerWorkerConfig() {
        app.getSharedPreferences("gdust_power", Context.MODE_PRIVATE).edit()
            .putString("account", powerAccount)
            .putInt("code", powerCode)
            .putString("verify", powerRoomVerify)
            .putString("name", powerRoomName)
            .putFloat("threshold", warnThreshold)
            .putFloat("drop_rate", warnDropRate)
            .putInt("interval", sampleIntervalMinutes)
            .apply()
    }

    val hasSession: Boolean get() = portalToken.isNotEmpty() && jobNumber.isNotEmpty()
    val hasPowerBinding: Boolean get() = powerAccount.isNotEmpty() && powerRoomVerify.isNotEmpty()
    val hasLlm: Boolean get() = llmBase.isNotEmpty() && llmKey.isNotEmpty() && llmModel.isNotEmpty()

    fun saveTeachingSession(session: TeachingSession) {
        require(isTeachingEndpoint(session.endpoint))
        secure.edit()
            .putString("teaching_endpoint", session.endpoint)
            .putString("teaching_cookie", session.cookie)
            .putString("teaching_page", session.pageUrl)
            .apply()
    }

    fun loadTeachingSession(): TeachingSession? {
        val endpoint = "teaching_endpoint".s
        val cookie = "teaching_cookie".s
        if (!isTeachingEndpoint(endpoint) || cookie.isBlank()) return null
        return TeachingSession(endpoint, cookie, "teaching_page".s)
    }

    fun clearTeachingSession() {
        secure.edit().remove("teaching_endpoint").remove("teaching_cookie")
            .remove("teaching_page").apply()
    }

    fun clearPortalSession() {
        secure.edit().remove("token").apply()
    }

    fun logout() {
        clearPortalSession()
        clearTeachingSession()
        secure.edit().remove("ticket").remove("job").remove("name").apply()
        plain.edit().remove("blocks").remove("calendar").remove("schedule_source").apply()
        android.webkit.CookieManager.getInstance().removeAllCookies {
            android.webkit.CookieManager.getInstance().flush()
        }
        android.webkit.WebStorage.getInstance().deleteAllData()
    }

    // ── 备份与恢复：只含课表/校历/待办/采样/偏好；不含登录凭证与密码 ──

    fun exportBackup(): String {
        val payload = mapOf(
            "app" to "gdust-v2",
            "schema" to 1,
            "exportedAt" to System.currentTimeMillis(),
            "calendar" to plain.getString("calendar", null),
            "blocks" to plain.getString("blocks", null),
            "schedule_source" to scheduleSource,
            "todos" to plain.getString("todos", null),
            "samples" to plain.getString("samples", null),
            "term_start" to termStartDate,
            "term_weeks" to termWeeks,
            "term_week" to termCurrentWeek,
            "start_tab" to startTab,
            "ui_style" to uiStyle,
            "monet_enabled" to monetEnabled,
            "sample_interval" to sampleIntervalMinutes,
            "warn_th" to warnThreshold,
            "warn_drop" to warnDropRate,
            "power" to if (hasPowerBinding) mapOf(
                "account" to powerAccount, "code" to powerCode,
                "verify" to powerRoomVerify, "name" to powerRoomName,
            ) else null,
        )
        return gson.toJson(payload)
    }

    /** 恢复备份并返回条目摘要；结构不认识的字段忽略，坏文件抛异常。 */
    fun importBackup(json: String): String {
        val root = runCatching { gson.fromJson(json, Map::class.java) as Map<*, *> }
            .getOrElse { error("备份文件格式无效") }
        require(root["app"] == "gdust-v2") { "不是本应用的备份文件" }
        val edit = plain.edit()
        (root["calendar"] as? String)?.let { edit.putString("calendar", it) }
        (root["blocks"] as? String)?.let { edit.putString("blocks", it) }
        (root["schedule_source"] as? String)?.let { edit.putString("schedule_source", it) }
        (root["todos"] as? String)?.let { edit.putString("todos", it) }
        (root["samples"] as? String)?.let { edit.putString("samples", it) }
        (root["term_start"] as? String)?.let { edit.putString("term_start", it) }
        (root["term_weeks"] as? Double)?.let { edit.putInt("term_weeks", it.toInt()) }
        (root["term_week"] as? Double)?.let { edit.putInt("term_week", it.toInt()) }
        (root["start_tab"] as? Double)?.let { edit.putInt("start_tab", it.toInt()) }
        (root["ui_style"] as? String)?.let { edit.putString("ui_style", it) }
        (root["monet_enabled"] as? Boolean)?.let { edit.putBoolean("monet_enabled", it) }
        (root["sample_interval"] as? Double)?.let { edit.putInt("sample_interval", it.toInt()) }
        (root["warn_th"] as? Double)?.let { edit.putFloat("warn_th", it.toFloat()) }
        (root["warn_drop"] as? Double)?.let { edit.putFloat("warn_drop", it.toFloat()) }
        (root["power"] as? Map<*, *>)?.let { p ->
            secure.edit()
                .putString("pacc", p["account"]?.toString().orEmpty())
                .putInt("pcode", (p["code"] as? Double)?.toInt() ?: 0)
                .putString("prv", p["verify"]?.toString().orEmpty())
                .putString("prn", p["name"]?.toString().orEmpty())
                .apply()
        }
        edit.apply()
        publishPowerWorkerConfig()
        val blocks = loadBlocks().size
        val todos = loadTodos().size
        return "课表 $blocks 张卡 · 待办 $todos 条 · 偏好与水电绑定已恢复"
    }
}

/** 会话门面：所有数据操作的唯一入口。 */
class Repository(context: Context) {

    val store = Store(context)
    private val api = GdustApi()
    private val teachingSso = TeachingSsoClient()

    /** 用登录时保存的 CAS ticket 直接换教务会话并拉整学期，不开 WebView。 */
    suspend fun syncTermWithLogin(): Pair<CalendarInfo, List<CourseBlock>> {
        val cal = store.loadCalendar() ?: api.calendar(store.portalToken, store.jobNumber)
        val ticket = store.casTicket
        require(ticket.isNotBlank()) { "登录票据已失效，请在登录页重新登录一次" }
        val blocks = teachingSso.loginAndFetch(ticket, cal)
        store.saveCalendar(cal)
        store.saveBlocks(blocks, "zf_term")
        return cal to blocks
    }

    suspend fun isSessionAlive(): Boolean {
        if (!store.hasSession) return false
        return try {
            api.calendar(store.portalToken, store.jobNumber)
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is AuthFailed) store.clearPortalSession()
            shouldKeepSession(true, e)
        }
    }

    suspend fun syncTeachingSchedule(): Pair<CalendarInfo, List<CourseBlock>> {
        val session = store.loadTeachingSession()
            ?: throw AuthFailed(401, "请先登录教务一次，之后会自动复用会话")
        val calendar = requireNotNull(store.loadCalendar()) { "请先选择要同步的学期" }
        val blocks = try {
            api.termSchedule(session, calendar)
        } catch (e: AuthFailed) {
            store.clearTeachingSession()
            throw e
        }
        store.saveBlocks(blocks, "zf_term")
        return calendar to blocks
    }

    suspend fun fetchCaptcha() = api.captcha()

    /**
     * 登录：CAS → 门户 SSO → 会话落盘。
     * 验证码错误以 AuthFailed(30001) 抛出，调用方换码重试。
     */
    suspend fun login(account: String, password: String, code: String, uuid: String) {
        val ticket = api.login(account, password, code, uuid)
        val token = api.exchangeToken(ticket)
        val (job, name) = runCatching { api.userInfo(token) }.getOrDefault(account to "")
        store.portalToken = token
        store.casTicket = ticket
        store.jobNumber = job.ifBlank { account }
        store.realName = name
    }

    /** 扫码拿 ticket 后的收尾。 */
    suspend fun finishSso(ticket: String) {
        val token = api.exchangeToken(ticket)
        val (job, name) = runCatching { api.userInfo(token) }.getOrDefault("" to "")
        store.portalToken = token
        store.casTicket = ticket
        store.jobNumber = job
        store.realName = name
    }

    fun subscribeSse() = api.subscribeSse()
    fun mobieAuthUrl(clientId: String) = api.mobieAuthUrl(clientId)

    /** 同步门户实际返回的课程；单周响应不能推断其他周。 */
    suspend fun syncSchedule(): Pair<CalendarInfo, List<CourseBlock>> {
        val raw = api.calendar(store.portalToken, store.jobNumber)
        val cal = raw.copy(
            startDate = store.termStartDate.ifBlank { raw.startDate },
            allWeek = if (store.termWeeks > 0) store.termWeeks else raw.allWeek,
            week = if (store.termCurrentWeek > 0) store.termCurrentWeek else raw.week,
        )
        // 已有真实学期课表时，门户快照不参与覆盖；更新应重新读取正方学期响应。
        if (store.scheduleSource == "zf_term") {
            return requireNotNull(store.loadCalendar()) to store.loadBlocks()
        }
        val blocks = mergeLessons(api.lessons(store.portalToken, store.jobNumber))
        store.saveCalendar(cal)
        store.saveBlocks(blocks)
        return cal to blocks
    }

    suspend fun notices() = api.notices(store.portalToken, store.jobNumber)

    suspend fun summarize() = api.summarize(
        store.llmBase, store.llmKey, store.llmModel, notices(),
    )

    // 水电（无需登录态）
    suspend fun powerRooms(account: String, code: Int) = api.powerRooms(account, code)
    suspend fun powerOdd() = api.powerOdd(store.powerAccount, store.powerCode, store.powerRoomVerify)
    suspend fun powerDetail() = api.powerDetail(store.powerAccount, store.powerCode, store.powerRoomVerify)

    fun savePowerBinding(account: String, code: Int, room: PowerRoom) {
        store.powerAccount = account
        store.powerCode = code
        store.powerRoomVerify = room.verify
        store.powerRoomName = room.name
        store.publishPowerWorkerConfig()
    }
}
