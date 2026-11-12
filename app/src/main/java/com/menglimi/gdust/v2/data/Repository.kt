package com.menglimi.gdust.v2.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

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
    // due = 截止时间毫秒（0 = 无）；学习通作业同步用，旧数据没有该字段，Gson 反序列化默认 0，向后兼容
    data class Todo(val id: Long, val text: String, val done: Boolean, val due: Long = 0)

    companion object {
        private val todoLock = Any()
    }

    fun loadTodos(): List<Todo> {
        val t = object : TypeToken<List<Todo>>() {}.type
        return runCatching { gson.fromJson<List<Todo>>(plain.getString("todos", "[]"), t) }
            .getOrNull().orEmpty()
    }

    fun updateTodos(transform: (List<Todo>) -> List<Todo>): List<Todo> = synchronized(todoLock) {
        transform(loadTodos()).also { plain.edit().putString("todos", gson.toJson(it)).commit() }
    }

    fun addTodo(text: String): List<Todo> = updateTodos { items ->
        val id = generateSequence(System.currentTimeMillis()) { it + 1 }
            .first { candidate -> items.none { it.id == candidate } }
        listOf(Todo(id, text, false)) + items
    }

    var chxNotificationsEnabled: Boolean
        get() = plain.getBoolean("chx_notifications_enabled", false)
        set(value) = plain.edit().putBoolean("chx_notifications_enabled", value).apply()

    // ── 学习通联动：只存 CAS 单点后拿到的超星 Cookie，不存密码 ──
    var chxCookie: String
        get() = "chx_cookie".s
        set(v) { "chx_cookie".s = v }

    var chxCookieAt: Long
        get() = plain.getLong("chx_cookie_at", 0L)
        set(v) = plain.edit().putLong("chx_cookie_at", v).apply()

    fun saveChxCookie(cookie: String) {
        chxCookie = cookie
        chxCookieAt = System.currentTimeMillis()
    }

    fun clearChxCookie() {
        chxCookie = ""
        chxCookieAt = 0L
    }

    val hasChxSession: Boolean get() = ChxSso.hasSessionCookie(chxCookie)

    fun addChxNotification(key: String, text: String): Boolean = synchronized(todoLock) {
        val type = object : TypeToken<List<String>>() {}.type
        val seen = runCatching {
            gson.fromJson<List<String>>(plain.getString("chx_seen_notifications", "[]"), type)
        }.getOrNull().orEmpty()
        if (key in seen) return@synchronized false
        val items = loadTodos()
        val id = generateSequence(System.currentTimeMillis()) { it + 1 }
            .first { candidate -> items.none { it.id == candidate } }
        plain.edit()
            .putString("todos", gson.toJson(listOf(Todo(id, text, false)) + items))
            .putString("chx_seen_notifications", gson.toJson((seen + key).takeLast(500)))
            .commit()
    }

    fun observeTodos(listener: () -> Unit): () -> Unit {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val refresh = Runnable { listener() }
        val observer = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "todos") handler.post(refresh)
        }
        plain.registerOnSharedPreferenceChangeListener(observer)
        return {
            plain.unregisterOnSharedPreferenceChangeListener(observer)
            handler.removeCallbacks(refresh)
        }
    }

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
        val cutoff = now - 30L * 24 * 3600 * 1000
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

    /** 壁纸：本机文件路径（空=不用壁纸）；模糊 dp；压暗 0~0.8。 */
    var wallpaperPath: String
        get() = plain.getString("wallpaper_path", "") ?: ""
        set(v) = plain.edit().putString("wallpaper_path", v).apply()

    var wallpaperBlur: Float
        get() = plain.getFloat("wallpaper_blur", 16f).coerceIn(0f, 40f)
        set(v) = plain.edit().putFloat("wallpaper_blur", v.coerceIn(0f, 40f)).apply()

    var wallpaperDim: Float
        get() = plain.getFloat("wallpaper_dim", 0.35f).coerceIn(0f, 0.85f)
        set(v) = plain.edit().putFloat("wallpaper_dim", v.coerceIn(0f, 0.85f)).apply()

    /** 课表格子字号倍率（0.85 小 / 1.0 标准 / 1.15 大 / 1.3 特大）。 */
    var courseTextScale: Float
        get() = plain.getFloat("course_text_scale", 1.0f).coerceIn(0.7f, 1.4f)
        set(v) = plain.edit().putFloat("course_text_scale", v.coerceIn(0.7f, 1.4f)).apply()

    /** 智能显示周末：开启时隐藏无课的周六日，关闭则始终显示 7 天。 */
    var smartHideWeekend: Boolean
        get() = plain.getBoolean("smart_hide_weekend", true)
        set(v) = plain.edit().putBoolean("smart_hide_weekend", v).apply()

    /** 课表格子高度 dp（48 紧凑 / 56 标准 / 64 宽松）。 */
    var courseRowHeight: Int
        get() = plain.getInt("course_row_h", 56).coerceIn(44, 72)
        set(v) = plain.edit().putInt("course_row_h", v.coerceIn(44, 72)).apply()

    /** 课表格子圆角 dp。 */
    var courseCorner: Int
        get() = plain.getInt("course_corner", 10).coerceIn(4, 20)
        set(v) = plain.edit().putInt("course_corner", v.coerceIn(4, 20)).apply()

    /** 格子内容是否居中（false=左对齐）。 */
    var cardAlignCenter: Boolean
        get() = plain.getBoolean("card_align_center", false)
        set(v) = plain.edit().putBoolean("card_align_center", v).apply()

    /** 格子文字单色（false=课程彩色）。 */
    var cardTextMono: Boolean
        get() = plain.getBoolean("card_text_mono", false)
        set(v) = plain.edit().putBoolean("card_text_mono", v).apply()

    /** 课表格子上是否显示教师。 */
    var showTeacherOnCard: Boolean
        get() = plain.getBoolean("card_teacher", true)
        set(v) = plain.edit().putBoolean("card_teacher", v).apply()

    /** 课表格子上是否显示地点。 */
    var showClassroomOnCard: Boolean
        get() = plain.getBoolean("card_classroom", true)
        set(v) = plain.edit().putBoolean("card_classroom", v).apply()

    // ── 课程提醒 ──
    /** 课前提醒总开关。 */
    var reminderEnabled: Boolean
        get() = plain.getBoolean("reminder_enabled", false)
        set(v) = plain.edit().putBoolean("reminder_enabled", v).apply()
    /** 提前分钟数（5/10/20/30）。 */
    var reminderLead: Int
        get() = plain.getInt("reminder_lead", 20).coerceIn(5, 60)
        set(v) = plain.edit().putInt("reminder_lead", v.coerceIn(5, 60)).apply()
    /** 次日课程提醒开关（每晚汇总明天课表）。 */
    var nextDayReminder: Boolean
        get() = plain.getBoolean("nextday_reminder", false)
        set(v) = plain.edit().putBoolean("nextday_reminder", v).apply()
    /** 次日提醒触发小时（默认 23 点）。 */
    var nextDayHour: Int
        get() = plain.getInt("nextday_hour", 23).coerceIn(0, 23)
        set(v) = plain.edit().putInt("nextday_hour", v.coerceIn(0, 23)).apply()
    /** 课中提醒（快下课时提示）。 */
    var inClassReminder: Boolean
        get() = plain.getBoolean("inclass_reminder", false)
        set(v) = plain.edit().putBoolean("inclass_reminder", v).apply()
    /** 课中提醒提前分钟（距下课）。 */
    var inClassLead: Int
        get() = plain.getInt("inclass_lead", 10).coerceIn(3, 30)
        set(v) = plain.edit().putInt("inclass_lead", v.coerceIn(3, 30)).apply()
    /** 超级岛（HyperOS 焦点通知）开关。 */
    var islandEnabled: Boolean
        get() = plain.getBoolean("island_enabled", true)
        set(v) = plain.edit().putBoolean("island_enabled", v).apply()

    /** 去重键：同一节课/同一个晚上只提醒一次。 */
    var lastReminderKey: String
        get() = plain.getString("last_reminder_key", "") ?: ""
        set(v) = plain.edit().putString("last_reminder_key", v).apply()

    /** 通知已读水位线：晚于该时间的公告算未读。 */
    var lastNoticeReadAt: Long
        get() = plain.getLong("notice_read_at", 0L)
        set(v) = plain.edit().putLong("notice_read_at", v).apply()

    /** 上次成功同步整学期的时间戳（用于 App 打开时判断要不要顺手续租刷新）。 */
    var lastSyncAt: Long
        get() = plain.getLong("last_sync_at", 0L)
        set(v) = plain.edit().putLong("last_sync_at", v).apply()

    /** 登录后是否自动定时同步课表（12 小时一次）。 */
    var autoSyncEnabled: Boolean
        get() = plain.getBoolean("auto_sync", true)
        set(v) = plain.edit().putBoolean("auto_sync", v).apply()

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

    /** 手动网页备用导入后复用 WebView 自己的持久 CookieManager 会话。 */
    fun saveWebImport(pageUrl: String) {
        val httpsSchool = pageUrl.startsWith("https://") && pageUrl.contains("gdust.edu.cn")
        val internal = pageUrl.startsWith("http://172.16.254.1")
        require(httpsSchool || internal) { "只保存学校域名的网页入口" }
        plain.edit().putString("web_import_page", pageUrl).apply()
    }

    fun loadWebImportPage(): String = plain.getString("web_import_page", "").orEmpty()

    fun clearWebImportPage() { plain.edit().remove("web_import_page").apply() }

    fun clearPortalSession() {
        secure.edit().remove("token").apply()
    }

    fun logout() {
        clearPortalSession()
        secure.edit().remove("ticket").remove("job").remove("name").remove("chx_cookie").apply()
        plain.edit().remove("blocks").remove("calendar").remove("schedule_source")
            .remove("web_import_page").remove("chx_cookie_at").apply()
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
    /** 学习通只读接口（当前只有探活；作业清单等抓到 HAR 再补）。 */
    private val chxApi = ChxApi()
    /** 登录后教务会话 Cookie 持久化在这里；后续同步、续租、定时任务共用。 */
    val teachingCookies = PersistentCookieJar(context)

    /** 校历：优先本机缓存；没有再找门户；都没有就给可操作提示。 */
    private suspend fun resolveCalendar(): CalendarInfo {
        store.loadCalendar()?.let { return it }
        if (store.portalToken.isNotBlank()) {
            runCatching { api.calendar(store.portalToken, store.jobNumber) }.getOrNull()?.let { return it }
        }
        error("本机还没有校历。请先用「网页登录导入」填一次学期设置（或重新登录门户），再同步。")
    }

    /** 账号密码快捷同步：优先复用现存正方 Cookie，明确认证失效时才用加密保存的 CAS ticket 续换。 */
    suspend fun syncTermWithLogin(): Pair<CalendarInfo, List<CourseBlock>> {
        val cal = resolveCalendar()
        val hasTeachingCookie = listOf("172.16.254.1", "gkjx.gdust.edu.cn", "webvpn.gdust.edu.cn")
            .any(teachingCookies::hasSessionFor)
        val blocks = if (hasTeachingCookie) {
            try {
                teachingSso.fetchWithCookies(cal, teachingCookies)
            } catch (e: AuthFailed) {
                val ticket = store.casTicket
                if (ticket.isBlank()) throw e
                teachingCookies.clear()
                teachingSso.loginAndFetch(ticket, cal, teachingCookies)
            }
        } else {
            val ticket = store.casTicket
            require(ticket.isNotBlank()) { "本机登录凭证不含 CAS 票据，请重新登录一次后再同步" }
            teachingSso.loginAndFetch(ticket, cal, teachingCookies)
        }
        store.saveCalendar(cal)
        store.saveBlocks(blocks, "zf_term")
        store.lastSyncAt = System.currentTimeMillis()
        return cal to blocks
    }

    /** 已有持久化 Cookie 时直接拉学期课表；OkHttp 没有则回退到网页登录留下的会话（后台定时任务走这条）。 */
    suspend fun syncTermWithCookies(): Pair<CalendarInfo, List<CourseBlock>> {
        val cal = resolveCalendar()
        val hasDirectCookie = listOf("172.16.254.1", "gkjx.gdust.edu.cn", "webvpn.gdust.edu.cn")
            .any(teachingCookies::hasSessionFor)
        val blocks = if (hasDirectCookie) {
            teachingSso.fetchWithCookies(cal, teachingCookies)
        } else {
            // 网页登录会话在 WebView 的 CookieManager 里；同进程可读，按当初登录页域名取
            val page = store.loadWebImportPage()
            val webCookie = page.takeIf { it.isNotBlank() }
                ?.let { android.webkit.CookieManager.getInstance().getCookie(it).orEmpty() }
            if (webCookie.isNullOrBlank()) {
                throw IllegalStateException("教务会话已失效（本机无可用会话），请重新登录一次。")
            }
            teachingSso.fetchWithCookieHeader(cal, page, webCookie)
        }
        store.saveCalendar(cal)
        store.saveBlocks(blocks, "zf_term")
        store.lastSyncAt = System.currentTimeMillis()
        return cal to blocks
    }

    /**
     * 用网页登录留下的会话读取**指定**学期课表。
     * 教务网页导入页：网页在独立 Activity（TeachingWebActivity）里登录，回到 Compose 后走这里读，
     * 不再依赖页面内 JS fetch。
     */
    suspend fun importTermWithWebSession(cal: CalendarInfo): List<CourseBlock> {
        val page = store.loadWebImportPage()
        val cookie = page.takeIf { it.isNotBlank() }
            ?.let { android.webkit.CookieManager.getInstance().getCookie(it).orEmpty() }
            .orEmpty()
        if (page.isBlank() || cookie.isBlank()) {
            throw IllegalStateException("还没有教务网页会话：请先在网页里登录并进入「教务系统」")
        }
        val blocks = teachingSso.fetchWithCookieHeader(cal, page, cookie)
        store.saveCalendar(cal)
        store.saveBlocks(blocks, "zf_term")
        store.lastSyncAt = System.currentTimeMillis()
        android.webkit.CookieManager.getInstance().flush()
        return blocks
    }

    /** 是否已有任一教务会话（直连 Cookie 或网页登录会话）。 */
    fun hasTeachingSession(): Boolean {
        if (listOf("172.16.254.1", "gkjx.gdust.edu.cn", "webvpn.gdust.edu.cn")
                .any(teachingCookies::hasSessionFor)) return true
        val page = store.loadWebImportPage()
        return page.isNotBlank() && !android.webkit.CookieManager.getInstance()
            .getCookie(page).isNullOrBlank()
    }

    /** 教务会话探活：true 有效 / false 明确失效 / null 网络原因未知（不判死、不清会话）。 */
    suspend fun teachingSessionAlive(): Boolean? = runCatching {
        teachingSso.probe(teachingCookies)
    }.getOrNull()

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

    suspend fun fetchCaptcha() = api.captcha()

    /**
     * 登录：CAS 拿 ticket → **先**换教务会话（ticket 一次性，教务更难打通，优先给它）→
     * 再用同一 ticket 换门户 TOKEN（失败不阻塞课表同步，只是校历/公告暂不可用）。
     * 验证码错误以 AuthFailed(30001) 抛出，调用方换码重试。
     */
    suspend fun login(account: String, password: String, code: String, uuid: String) {
        val ticket = api.login(account, password, code, uuid)
        store.casTicket = ticket
        applySessions(account, ticket)
    }

    /** 扫码拿 ticket 后的收尾。 */
    suspend fun finishSso(ticket: String) {
        store.casTicket = ticket
        // 钉钉票是移动端 SSO 体系：先换门户（必须成功），教务拿不到不算登录失败
        applySessions("", ticket, portalFirst = true)
    }

    /**
     * 票据一次性，不同来源票的适用范围不同：
     * - 账号密码（CAS 网页登录）：先给教务，再换门户；
     * - 钉钉扫码：先换门户（钉钉票本就为门户签发），再试教务，失败只提示不阻断登录。
     */
    private suspend fun applySessions(fallbackJob: String, ticket: String, portalFirst: Boolean = false) {
        var teachingOk = false
        var token = ""
        if (portalFirst) {
            token = runCatching { api.exchangeToken(ticket) }.getOrElse { "" }
            teachingOk = runCatching { teachingSso.openSession(ticket, teachingCookies) }.getOrNull() != null
        } else {
            teachingOk = runCatching { teachingSso.openSession(ticket, teachingCookies) }.getOrNull() != null
            token = runCatching { api.exchangeToken(ticket) }.getOrElse { "" }
        }
        if (token.isNotBlank()) {
            val (job, name) = runCatching { api.userInfo(token) }.getOrDefault(fallbackJob to "")
            store.portalToken = token
            store.jobNumber = job.ifBlank { fallbackJob }
            store.realName = name
        }
        if (!teachingOk && token.isBlank()) {
            error("登录票据未能换取任何会话（教务与门户都被拒绝），请稍后重试或改用网页登录导入。")
        }
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

    /**
     * 同步学习通作业到待办（阶段2）。
     *
     * 链路（端点全部来自真实抓包，见 docs/EXTENDING.md §3.4）：
     * 课程列表 → 每门课的课程页（隐藏域 workEnc）→ `mooc2/work/list` → 解析条目。
     *
     * 待办 id = 学习通 workId（与手动待办的时间戳 id 天然不冲突，互不覆盖）；
     * 重复同步只更新文本/截止/完成态，不重复添加；手动勾选的保持勾选；
     * 截止时间来自列表页的相对剩余时间（绝对截止在详情页，待补抓包后再精确化）。
     *
     * @return 结果文案（中文，直接展示）
     */
    suspend fun chxSyncWorkTodos(): String {
        val cookie = store.chxCookie
        require(cookie.isNotBlank()) { "还没有学习通会话：请先在学习通联动里登录一次" }
        val listHtml = chxApi.courseListHtml(cookie)
            ?: error("课程列表读取失败：网络不通，或学习通会话已失效（可在联动里检查会话）")
        val courses = ChxWorkParser.parseCourses(listHtml)
        if (courses.isEmpty()) error("课程列表为空：请先在学习通里确认已加入课程")
        var added = 0
        var updated = 0
        var pending = 0
        var scanned = 0
        var failed = 0
        var firstFail: String? = null
        val updates = mutableMapOf<Long, Store.Todo>()
        // 批量预过滤（stu-job-info 一次带全量 clazzId_cpi 对）：全班都完成/无作业的课直接跳过，
        // 把 work/list 的请求数从「课程数」压到「有未完成的课」。接口无有效数据时退回全量检查。
        val clazzPersonStr = courses.joinToString("%2C") { "${it.classId}_${it.cpi}" }
        val jobInfo = chxApi.jobInfoJson(cookie, clazzPersonStr)
            ?.let { ChxWorkParser.parseJobInfo(it) }
            .orEmpty()
        // 课程多（实测一个账号 30+ 门，含历史学期）：并发拉取，失败按课程隔离
        val semaphore = Semaphore(4)
        val results = coroutineScope {
            courses.map { course ->
                async {
                    if (jobInfo.isNotEmpty()) {
                        val (jobCount, jobFinish) = jobInfo[course.classId]
                            ?: return@async course to Result.success(emptyList<ChxWorkItem>())
                        if (jobCount == 0 || jobFinish >= jobCount) {
                            // 无作业，或全班（含本人）都完成
                            return@async course to Result.success(emptyList<ChxWorkItem>())
                        }
                    }
                    semaphore.withPermit {
                        course to runCatching {
                            val tabsHtml = chxApi.courseTabsHtml(cookie, course.courseId, course.classId, course.cpi)
                                ?: error("课程页读取失败")
                            val tabs = ChxWorkParser.parseCourseTabs(tabsHtml)
                                ?: error("课程页缺少作业签名（可能没有作业 tab）")
                            val workHtml = chxApi.workListHtml(
                                cookie, tabs.moocDomain, course.courseId, course.classId, course.cpi,
                                tabs.enc, tabs.workEnc, tabs.t,
                            ) ?: error("作业列表读取失败")
                            ChxWorkParser.parseWorkList(workHtml)
                        }
                    }
                }
            }.awaitAll()
        }
        for ((course, r) in results) {
            val items = r.getOrNull()
            if (items == null) {
                failed++
                firstFail = firstFail ?: "${course.name}：${r.exceptionOrNull()?.message}"
                continue
            }
            scanned++
            for (item in items) {
                if (item.workId <= 0) continue
                val isPending = item.status == "未交"
                if (isPending) pending++
                val due = if (isPending) {
                    System.currentTimeMillis() + ChxWorkParser.remainingMillis(item.remainingText)
                } else 0L
                updates[item.workId] = Store.Todo(item.workId, item.title, done = !isPending, due = due)
            }
        }
        if (updates.isEmpty()) {
            return "同步完成：扫了 $scanned 门课${failSuffix(failed, firstFail)}，学习通里没有作业"
        }
        store.updateTodos { current ->
            val byId = current.associateBy { it.id }.toMutableMap()
            for ((id, todo) in updates) {
                val existing = byId[id]
                if (existing == null) {
                    added++
                    // 未交的作业排前面：截止时间近的优先
                    byId[id] = todo
                } else {
                    updated++
                    // 完成态只置 true 不回退（手动勾选的保持勾选）
                    byId[id] = existing.copy(
                        text = todo.text,
                        done = existing.done || todo.done,
                        due = todo.due,
                    )
                }
            }
            byId.values.toList()
        }
        return "同步完成：扫了 $scanned 门课${failSuffix(failed, firstFail)}，" +
            "新增 $added 条、更新 $updated 条，未交 $pending 条"
    }

    private fun failSuffix(failed: Int, firstFail: String?): String =
        if (failed == 0) "" else "（$failed 门读取失败，首个：$firstFail）"

    /**
     * 学习通会话探活：true 有效 / false 失效 / null 网络原因未知（不清 Cookie）。
     *
     * 探活的失效判定一半靠页面文案启发式，单次 false 可能是误判；
     * 连续两次都判失效才清 Cookie，避免把有效会话删掉。
     */
    suspend fun chxSessionAlive(): Boolean? {
        if (store.chxCookie.isBlank()) return false
        val first = chxApi.probe(store.chxCookie)
        val alive = if (first == false) chxApi.probe(store.chxCookie) else first
        if (alive == false) store.clearChxCookie()
        return alive
    }

    /**
     * 彻底退出学习通联动：清本机存储 + WebView 里的超星/CAS 会话 Cookie。
     *
     * 只按域清（超星各域 + 学校 CAS），不整体 removeAllCookies——
     * 教务网页导入、学校门户的会话跟学习通无关，不能误伤。
     * CAS 的本地登录态在 localStorage 里，由 ChxWebActivity 打开登出页时顺带清空。
     */
    fun clearChxSession() {
        store.clearChxCookie()
        clearWebCookies(
            listOf(
                "https://chaoxing.com",
                "https://passport2.chaoxing.com",
                "https://i.chaoxing.com",
                "https://mooc2-ans.chaoxing.com",
                "http://gdkjxy.fysso.chaoxing.com",
                "http://gdkjxy.fanya.chaoxing.com",
                "https://cas.gdust.edu.cn",
            ),
        )
    }

    /** 对给定入口逐个把同名 Cookie 写成过期（CookieManager 没有按域删除的 API）。 */
    private fun clearWebCookies(hosts: List<String>) {
        val cm = android.webkit.CookieManager.getInstance()
        for (host in hosts) {
            val header = cm.getCookie(host) ?: continue
            for (pair in header.split(";")) {
                val name = pair.trim().substringBefore("=")
                if (name.isBlank()) continue
                // host 级与 .chaoxing.com 域级各过期一次，覆盖两种归属
                cm.setCookie(host, "$name=; Max-Age=0; Path=/")
                cm.setCookie(host, "$name=; Max-Age=0; Path=/; Domain=.chaoxing.com")
            }
        }
        cm.flush()
    }

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
