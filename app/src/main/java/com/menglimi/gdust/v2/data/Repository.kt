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

    fun saveBlocks(blocks: List<CourseBlock>) = plain.edit().putString("blocks", gson.toJson(blocks)).apply()
    fun loadBlocks(): List<CourseBlock> {
        val t = object : TypeToken<List<CourseBlock>>() {}.type
        return runCatching {
            gson.fromJson<List<CourseBlock>>(plain.getString("blocks", "[]"), t)
        }.getOrDefault(emptyList())
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

    /** 莫奈取色：用系统壁纸色（Material You）生成整套 Ds 令牌；关=默认蓝。 */
    var monetEnabled: Boolean
        get() = plain.getBoolean("monet_enabled", false)
        set(v) = plain.edit().putBoolean("monet_enabled", v).apply()

    // ── 学期设置（拾光式：用户可校正，门户只给单周课表，按此铺全学期）──
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

    fun logout() {
        secure.edit().remove("token").remove("job").remove("name").apply()
        plain.edit().remove("blocks").remove("calendar").apply()
    }
}

/** 会话门面：所有数据操作的唯一入口。 */
class Repository(context: Context) {

    val store = Store(context)
    private val api = GdustApi()

    suspend fun isSessionAlive(): Boolean {
        if (!store.hasSession) return false
        return runCatching { api.calendar(store.portalToken, store.jobNumber) }.isSuccess
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
        store.jobNumber = job.ifBlank { account }
        store.realName = name
    }

    /** 扫码拿 ticket 后的收尾。 */
    suspend fun finishSso(ticket: String) {
        val token = api.exchangeToken(ticket)
        val (job, name) = runCatching { api.userInfo(token) }.getOrDefault("" to "")
        store.portalToken = token
        store.jobNumber = job
        store.realName = name
    }

    fun subscribeSse() = api.subscribeSse()
    fun mobieAuthUrl(clientId: String) = api.mobieAuthUrl(clientId)

    /** 同步课表+校历：门户只返回单周，按学期设置把这一周铺成全学期。 */
    suspend fun syncSchedule(): Pair<CalendarInfo, List<CourseBlock>> {
        val raw = api.calendar(store.portalToken, store.jobNumber)
        // 用户可在设置里校正开学日期/总周数/当前周（拾光式）
        val cal = raw.copy(
            startDate = store.termStartDate.ifBlank { raw.startDate },
            allWeek = if (store.termWeeks > 0) store.termWeeks else raw.allWeek,
            week = if (store.termCurrentWeek > 0) store.termCurrentWeek else raw.week,
        )
        val weekLessons = api.lessons(store.portalToken, store.jobNumber)
        val weekBlocks = mergeLessons(weekLessons)
        // 门户没给周次范围，只能按单双周标记把本周课表复制到全学期
        val total = cal.allWeek.coerceIn(1, 30)
        val blocks = buildList {
            for (w in 1..total) {
                weekBlocks.forEach { b ->
                    val parityOk = when (b.weekParity) {
                        1 -> w % 2 == 1
                        2 -> w % 2 == 0
                        else -> true
                    }
                    if (parityOk) add(b.copy(week = w))
                }
            }
        }
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
