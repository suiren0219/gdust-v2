package com.menglimi.gdust.v2.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 本机存储（v2）：凭据/会话/课表缓存/待办/设置全部只在本机。
 * 敏感项走 EncryptedSharedPreferences；课表/待办为非敏感缓存走普通 prefs。
 */
class Store(context: Context) {

    private val app = context.applicationContext
    private val gson = Gson()

    // EncryptedSharedPreferences 初始化要读 keystore（冷启动 100–500ms），且每次 new Store 都会重建：
    // 改懒加载——Worker 等只碰 plain 的路径不再付这笔开销；App 启动时后台预热一次。
    private val secure: android.content.SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            app, "gdust_secure",
            MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** 主线程外预热加密存储（App 启动时调用，避免首次敏感读写卡主线程）。 */
    fun warmSecure() {
        runCatching { secure }
    }

    private val plain = app.getSharedPreferences("gdust_cache", Context.MODE_PRIVATE)
    internal val schedules = ScheduleLibrary(
        read = { plain.getString(ScheduleLibrary.KEY, null) },
        write = { check(plain.edit().putString(ScheduleLibrary.KEY, it).commit()) { "课表保存失败，原数据已保留" } },
    )
    internal val selectedIndependentSchedule: IndependentSchedule? get() = runCatching { schedules.snapshot().selected }.getOrNull()
    val isIndependentSchedule: Boolean get() = selectedIndependentSchedule != null
    val currentScheduleName: String get() = selectedIndependentSchedule?.name ?: "学校课表"
    internal fun importIndependentSchedule(document: String, revision: Long): IndependentSchedule = schedules.import(document, revision)
    internal fun selectSchedule(id: String) = schedules.select(id)
    internal fun exportCurrentSchedule(): String = selectedIndependentSchedule?.schedule?.document
        ?: ScheduleShareCodec.export(loadCalendar(), loadBlocks(), currentScheduleName)
    internal var automaticSyncNeedsLogin: Boolean
        get() = plain.getBoolean("auto_sync_needs_login", false)
        set(value) { plain.edit().putBoolean("auto_sync_needs_login", value).apply() }
    internal var lastAutoSyncAttemptAt: Long
        get() = plain.getLong("last_auto_sync_attempt_at", 0L)
        set(value) { plain.edit().putLong("last_auto_sync_attempt_at", value).apply() }

    private var String.s: String
        get() = secure.getString(this, null).orEmpty()
        set(v) = secure.edit().putString(this, v).apply()

    // ── 会话与凭据 ──
    var portalToken: String
        get() = "token".s
        set(v) { "token".s = v }
    /** 仅保留旧版本存储键以兼容迁移；一次性 CAS ticket 不再保存或用于续租。 */
    var casTicket: String
        get() = "ticket".s
        set(v) { "ticket".s = v }
    var jobNumber: String
        get() = "job".s
        set(v) { "job".s = v }
    var realName: String
        get() = "name".s
        set(v) { "name".s = v }

    /** 仅保存来自已认证教务页面的生日月日；不保存出生年份，也不进入备份。 */
    val birthdayMonthDay: String
        get() = if (jobNumber.isNotBlank() && "birthday_account".s == jobNumber) "birthday_month_day".s else ""

    internal fun saveBirthday(monthDay: java.time.MonthDay, account: String) {
        if (account.isBlank() || account != jobNumber) return
        secure.edit().putString("birthday_account", account)
            .putString("birthday_month_day", monthDay.toString()).apply()
        plain.edit().putLong("birthday_revision", System.nanoTime()).apply()
    }

    fun birthdayDue(today: java.time.LocalDate = java.time.LocalDate.now()): Boolean =
        Birthday.shouldShow(birthdayMonthDay, "birthday_shown".s, today)

    fun markBirthdayShown(today: java.time.LocalDate = java.time.LocalDate.now()) {
        "birthday_shown".s = today.toString()
    }

    fun observeBirthday(listener: () -> Unit): () -> Unit {
        val observer = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "birthday_revision") listener()
        }
        plain.registerOnSharedPreferenceChangeListener(observer)
        return { plain.unregisterOnSharedPreferenceChangeListener(observer) }
    }

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

    // Local MCP token is optional; the service always binds to loopback.
    var localMcpToken: String
        get() = "local_mcp_token".s
        set(value) { "local_mcp_token".s = value.trim() }

    var localMcpEnabled: Boolean
        get() = secure.getBoolean("local_mcp_enabled", false)
        set(value) { secure.edit().putBoolean("local_mcp_enabled", value).apply() }

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
    fun loadCalendar(): CalendarInfo? = selectedIndependentSchedule?.schedule?.calendar?.let { cal ->
        val week = com.menglimi.gdust.v2.ui.schedule.ScheduleTimes.weekAt(java.time.LocalDate.parse(cal.startDate), java.time.LocalDate.now())
        cal.copy(week = week.coerceIn(1, cal.allWeek))
    } ?: loadSchoolCalendar()
    internal fun loadSchoolCalendar(): CalendarInfo? = plain.getString("calendar", null)?.let {
        runCatching { gson.fromJson(it, CalendarInfo::class.java) }.getOrNull()
    }

    val scheduleSource: String get() = if (isIndependentSchedule) "cloud_import" else schoolScheduleSource
    internal val schoolScheduleSource: String get() = plain.getString("schedule_source", "legacy") ?: "legacy"

    fun saveBlocks(blocks: List<CourseBlock>, source: String = "portal_week") = plain.edit()
        .putString("blocks", gson.toJson(blocks))
        .putString("schedule_source", source)
        .apply()

    /** 校历、课程和成功时间属于同一快照，避免观察者读到半份新学期。 */
    internal fun saveSchedule(cal: CalendarInfo, blocks: List<CourseBlock>, source: String, syncedAt: Long? = null) {
        val edit = plain.edit().putString("calendar", gson.toJson(cal))
            .putString("blocks", gson.toJson(blocks)).putString("schedule_source", source)
        if (syncedAt != null) edit.putLong("last_sync_at", syncedAt)
        check(edit.commit()) { "课表已读取，但未能保存到本机。" }
    }

    fun loadBlocks(): List<CourseBlock> = selectedIndependentSchedule?.schedule?.blocks ?: loadSchoolBlocks()
    internal fun loadSchoolBlocks(): List<CourseBlock> {
        val t = object : TypeToken<List<CourseBlock>>() {}.type
        val blocks = runCatching {
            gson.fromJson<List<CourseBlock>>(plain.getString("blocks", "[]"), t)
        }.getOrDefault(emptyList())
        if (schoolScheduleSource != "legacy") return blocks
        // 旧版曾复制单周缓存：只有日期与真实周次一致的条目可以继续显示。
        val start = runCatching { java.time.LocalDate.parse(loadSchoolCalendar()?.startDate) }.getOrNull()
            ?: return emptyList()
        return blocks.filter { block ->
            val date = runCatching { java.time.LocalDate.parse(block.date) }.getOrNull()
            date != null && date.dayOfWeek.value == block.day &&
                java.time.temporal.ChronoUnit.DAYS.between(start, date) / 7 + 1 == block.week.toLong()
        }
    }

    // ── 学习通 tab 签名缓存：courseId → (enc/workEnc/t) ──
    // 免去每次同步都拉 138KB 的课程页；签名疑似失效时由调用方刷新并回写。
    fun loadChxTabCache(): Map<Long, ChxWorkTabs> {
        val t = object : TypeToken<Map<Long, ChxWorkTabs>>() {}.type
        return runCatching { gson.fromJson<Map<Long, ChxWorkTabs>>(plain.getString("chx_tabs", "[]"), t) }
            .getOrNull().orEmpty()
    }

    fun saveChxTabCache(cache: Map<Long, ChxWorkTabs>) = synchronized(todoLock) {
        plain.edit().putString("chx_tabs", gson.toJson(cache)).commit()
    }

    // ── 待办提醒 ──
    /** 待办到期提醒总开关（独立于课程提醒）。 */
    var todoReminderEnabled: Boolean
        get() = plain.getBoolean("todo_reminder_enabled", false)
        set(v) = plain.edit().putBoolean("todo_reminder_enabled", v).apply()
    /** 提前提醒分钟数：0 / 10 / 30 / 60 / 1440。 */
    var todoReminderLead: Int
        get() = plain.getInt("todo_reminder_lead", 60)
        set(v) = plain.edit().putInt("todo_reminder_lead", v).apply()
    /** 只提醒作业/考试（关=所有带截止时间的待办都提醒）。 */
    var todoReminderOnlyDatedTypes: Boolean
        get() = plain.getBoolean("todo_reminder_types", true)
        set(v) = plain.edit().putBoolean("todo_reminder_types", v).apply()
    /** 已提醒过的待办 key，避免重复通知。 */
    var lastTodoReminderKey: String
        get() = plain.getString("todo_reminder_key", "") ?: ""
        set(v) = plain.edit().putString("todo_reminder_key", v).apply()

    // ── 待办（结构化模型见 TodoModel.kt，带旧 JSON 兼容默认值）──

    companion object {
        private val todoLock = Any()
    }

    fun loadTodos(): List<Todo> = parseTodos(plain.getString("todos", "[]").orEmpty(), gson)

    fun updateTodos(transform: (List<Todo>) -> List<Todo>): List<Todo> = synchronized(todoLock) {
        val current = loadTodos()
        transform(current).also { updated ->
            if (updated != current) plain.edit().putString("todos", gson.toJson(updated)).commit()
        }
    }

    /** 按稳定 ID 设置完成态；与同步共用锁内最新快照，重复 callback 不反转状态。 */
    fun setTodoDone(id: Long, done: Boolean): List<Todo> = updateTodos { current ->
        current.map { if (it.id == id && it.done != done) it.copy(done = done) else it }
    }

    fun addTodo(
        text: String,
        kind: TodoKind = TodoKind.GENERAL,
        course: String = "",
        dueAt: Long = 0L,
    ): List<Todo> = updateTodos { items ->
        val id = generateSequence(System.currentTimeMillis()) { it + 1 }
            .first { candidate -> items.none { it.id == candidate } }
        listOf(Todo(id, text, false, kind, course, dueAt, "manual")) + items
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
        val (kind, course) = classifyNotificationTodo(text)
        val todo = Todo(id = id, text = text, done = false, kind = kind, course = course, source = "chaoxing")
        plain.edit()
            .putString("todos", gson.toJson(listOf(todo) + items))
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

    /** 本机采样序列，供折线图与用量估计使用，保留最近 30 天。 */
    fun loadSamples(): List<Sample> {
        val t = object : TypeToken<List<Sample>>() {}.type
        return runCatching { gson.fromJson<List<Sample>>(plain.getString("samples", "[]"), t) }
            .getOrDefault(emptyList())
    }

    /** 同一采样桶保留最后一个值，兼容短间隔预设且不增加网络查询。 */
    fun addSample(odd: Double) {
        val now = System.currentTimeMillis()
        val kept = PowerQueryPolicy.appendSample(loadSamples().map { PowerSample(it.time, it.odd) }, PowerSample(now, odd))
            .map { Sample(it.time, it.odd) }
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
    internal val schoolTermStartDate: String get() = plain.getString("term_start", "") ?: ""
    var termStartDate: String
        get() = selectedIndependentSchedule?.schedule?.calendar?.startDate ?: schoolTermStartDate
        set(v) = plain.edit().putString("term_start", v).apply()

    /** 学期总周数；0=用校历 allWeek。 */
    internal val schoolTermWeeks: Int get() = plain.getInt("term_weeks", 0)
    var termWeeks: Int
        get() = selectedIndependentSchedule?.schedule?.calendar?.allWeek ?: schoolTermWeeks
        set(v) = plain.edit().putInt("term_weeks", v).apply()

    /** 手动指定当前周；0=用校历 week。 */
    internal val schoolTermCurrentWeek: Int get() = plain.getInt("term_week", 0)
    var termCurrentWeek: Int
        get() = if (isIndependentSchedule) loadCalendar()?.week ?: 1 else schoolTermCurrentWeek
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

    // ── 使用须知与教务夜间关闭提醒 ──
    /** 免责声明已确认（首次进入 App 弹一次）。 */
    var disclaimerAccepted: Boolean
        get() = plain.getBoolean("disclaimer_accepted", false)
        set(v) = plain.edit().putBoolean("disclaimer_accepted", v).apply()
    /** 教务夜间关闭提醒开关（默认开：失败且处于时段内时提示）。 */
    var maintenanceReminderEnabled: Boolean
        get() = plain.getBoolean("maintenance_reminder", true)
        set(v) = plain.edit().putBoolean("maintenance_reminder", v).apply()
    /** 教务夜间关闭开始分钟数（默认 23:00）。 */
    var maintenanceStartMin: Int
        get() = plain.getInt("maintenance_start", MaintenanceWindow.DEFAULT_START_MIN).coerceIn(0, 1439)
        set(v) = plain.edit().putInt("maintenance_start", v.coerceIn(0, 1439)).apply()
    /** 教务夜间关闭结束分钟数（默认 06:30，跨午夜允许 start > end）。 */
    var maintenanceEndMin: Int
        get() = plain.getInt("maintenance_end", MaintenanceWindow.DEFAULT_END_MIN).coerceIn(0, 1439)
        set(v) = plain.edit().putInt("maintenance_end", v.coerceIn(0, 1439)).apply()

    // ── 课表卡片玻璃材质（Nexio「效果」页同款六项）──
    /** 卡片着色程度（tint alpha，0.18~0.6，默认 0.24；下限保证课程色可区分）。 */
    var glassTint: Float
        get() = plain.getFloat("glass_tint", 0.24f).coerceIn(0.18f, 0.6f)
        set(v) = plain.edit().putFloat("glass_tint", v).apply()
    /** 卡片不透明度（玻璃底色 alpha，0~1，默认 0.34）。 */
    var glassOpacity: Float
        get() = plain.getFloat("glass_opacity", 0.34f).coerceIn(0f, 1f)
        set(v) = plain.edit().putFloat("glass_opacity", v).apply()
    /** 卡片模糊（px，0~24，默认 1）。 */
    var glassBlurPx: Int
        get() = plain.getInt("glass_blur", 1).coerceIn(0, 24)
        set(v) = plain.edit().putInt("glass_blur", v).apply()
    /** 卡片折射强度（0=关闭，0~1）。 */
    var glassRefraction: Float
        get() = plain.getFloat("glass_refraction", 0f).coerceIn(0f, 1f)
        set(v) = plain.edit().putFloat("glass_refraction", v).apply()
    /** 边缘厚度：0 关闭 / 1 默认 / 2 细 / 3 厚。 */
    var glassEdge: Int
        get() = plain.getInt("glass_edge", 1).coerceIn(0, 3)
        set(v) = plain.edit().putInt("glass_edge", v).apply()
    /** 高光描边（折射着色器内的边缘高亮）。 */
    var glassHighlightEdge: Boolean
        get() = plain.getBoolean("glass_highlight", false)
        set(v) = plain.edit().putBoolean("glass_highlight", v).apply()

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
    // ── 超级岛显示（对齐 Nexio 课程提醒设置）──
    /** 超级岛左侧：0 课程名 / 1 上课地点 / 2 倒计时。 */
    var islandLeftMode: Int
        get() = plain.getInt("island_left_mode", 1).coerceIn(0, 2)
        set(v) = plain.edit().putInt("island_left_mode", v).apply()
    /** 超级岛右侧：0 课程名 / 1 上课地点 / 2 倒计时。 */
    var islandRightMode: Int
        get() = plain.getInt("island_right_mode", 2).coerceIn(0, 2)
        set(v) = plain.edit().putInt("island_right_mode", v).apply()
    /** 课中岛右侧：0 课程名 / 1 上课地点 / 2 倒计时。 */
    var islandInClassRightMode: Int
        get() = plain.getInt("island_in_class_right_mode", 2).coerceIn(0, 2)
        set(v) = plain.edit().putInt("island_in_class_right_mode", v).apply()
    /** 息屏显示：0 课程名称 / 1 上课地点。 */
    var islandAodMode: Int
        get() = plain.getInt("island_aod_mode", 0).coerceIn(0, 1)
        set(v) = plain.edit().putInt("island_aod_mode", v).apply()

    /** 缩略态地点：去掉「教学楼/松/实验楼」等前缀，只保留能塞进岛里的短名。 */
    fun shortClassroom(room: String): String {
        val t = room.trim()
        return t.replace(Regex("^(教学楼|实验楼|松山湖|博|松)"), "")
            .replace(Regex("[（(].*?[)）]"), "")
            .take(8).ifEmpty { t.take(8) }
    }

    /** 缩略态课程名：去掉罗马数字后缀与「基础与应用」等长尾。 */
    fun shortCourse(name: String): String = name.trim()
        .replace(Regex("[ⅠⅡⅢⅣⅤI I I I I I]+$", RegexOption.IGNORE_CASE), "")
        .replace("基础与应用", "")
        .take(8)

    /** 超级岛（HyperOS 焦点通知）开关。 */
    var islandEnabled: Boolean
        get() = plain.getBoolean("island_enabled", true)
        set(v) = plain.edit().putBoolean("island_enabled", v).apply()

    /** 去重键：同一节课/同一个晚上只提醒一次。 */
    var lastReminderKey: String
        get() = plain.getString("last_reminder_key", "") ?: ""
        set(v) = plain.edit().putString("last_reminder_key", v).apply()

    /** 逐条已读按学校账号隔离；旧全局水位无法辨别实际阅读情况，不继承。 */
    fun readNoticeIds(account: String = jobNumber): Set<String> {
        val key = noticeReadStorageKey(account) ?: return emptySet()
        return plain.getStringSet(key, emptySet()).orEmpty().toSet()
    }

    fun markNoticesRead(account: String, notices: List<Notice>) {
        if (account != jobNumber) return
        val key = noticeReadStorageKey(account) ?: return
        plain.edit().putStringSet(key, readNoticeIds(account) + notices.map { it.readId })
            .remove("notice_read_at").apply()
    }

    fun observeNoticeReads(listener: () -> Unit): () -> Unit {
        val observer = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key?.startsWith("notice_read_ids_") == true) listener()
        }
        plain.registerOnSharedPreferenceChangeListener(observer)
        return { plain.unregisterOnSharedPreferenceChangeListener(observer) }
    }

    /** 联动会话保存/清除时回调（chx_cookie_at 在 plain prefs，写 Secure 不触发重组，以它为准）。 */
    fun observeChxSession(listener: () -> Unit): () -> Unit {
        val observer = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "chx_cookie_at") listener()
        }
        plain.registerOnSharedPreferenceChangeListener(observer)
        return { plain.unregisterOnSharedPreferenceChangeListener(observer) }
    }

    /** 上次成功同步整学期的时间戳（用于 App 打开时判断要不要顺手续租刷新）。 */
    var lastSyncAt: Long
        get() = plain.getLong("last_sync_at", 0L)
        set(v) = plain.edit().putLong("last_sync_at", v).apply()

    fun observeSchedule(listener: () -> Unit): () -> Unit {
        val observer = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in setOf("blocks", "calendar", "last_sync_at", ScheduleLibrary.KEY)) listener()
        }
        plain.registerOnSharedPreferenceChangeListener(observer)
        return { plain.unregisterOnSharedPreferenceChangeListener(observer) }
    }

    /** 登录后是否自动定时维护会话并同步课表（4 小时一次，与电量任务合并唤醒）。 */
    var autoSyncEnabled: Boolean
        get() = plain.getBoolean("auto_sync", true)
        set(v) {
            val edit = plain.edit().putBoolean("auto_sync", v)
            if (!v) edit.remove("last_auto_sync_attempt_at").remove("auto_sync_needs_login")
            edit.apply()
            if (v) com.menglimi.gdust.v2.work.ScheduleSyncWorker.schedule(app)
            else com.menglimi.gdust.v2.work.ScheduleSyncWorker.cancel(app)
        }

    var powerQueryPreset: PowerQueryPreset
        get() = plain.getString("power_query_preset", null)?.let(PowerQueryPreset::fromKey)
            ?: if (plain.contains("sample_interval")) PowerQueryPreset.CUSTOM else PowerQueryPreset.BALANCED
        set(value) { plain.edit().putString("power_query_preset", value.key).apply(); publishPowerWorkerConfig() }

    var sampleIntervalMinutes: Int
        get() = plain.getInt("sample_interval", 60).coerceIn(15, 120)
        set(v) {
            plain.edit().putInt("sample_interval", v.coerceIn(15, 120))
                .putString("power_query_preset", PowerQueryPreset.CUSTOM.key).apply()
            publishPowerWorkerConfig()
        }

    var warnThreshold: Float
        get() = plain.getFloat("warn_th", 20f)
        set(v) { plain.edit().putFloat("warn_th", v).apply(); publishPowerWorkerConfig() }
    var warnDropRate: Float
        get() = plain.getFloat("warn_drop", 5f)
        set(v) { plain.edit().putFloat("warn_drop", v).apply(); publishPowerWorkerConfig() }

    /** Worker 只读取绑定配置镜像；换宿舍时清掉旧宿舍采样，配置相同不重排。 */
    fun publishPowerWorkerConfig() {
        val power = app.getSharedPreferences("gdust_power", Context.MODE_PRIVATE)
        val bindingChanged = power.getString("account", "").orEmpty() != powerAccount ||
            power.getInt("code", 0) != powerCode || power.getString("verify", "").orEmpty() != powerRoomVerify
        val edit = power.edit()
        if (bindingChanged) {
            plain.edit().remove("samples").apply()
            listOf("last_sample", "prev_odd", "low_warn", "drop_warn", "cached_detail", "cached_binding", "last_attempt")
                .forEach(edit::remove)
        }
        edit.putString("account", powerAccount)
            .putInt("code", powerCode)
            .putString("verify", powerRoomVerify)
            .putString("name", powerRoomName)
            .putFloat("threshold", warnThreshold)
            .putFloat("drop_rate", warnDropRate)
            .putInt("interval", sampleIntervalMinutes)
            .putString("query_preset", powerQueryPreset.key)
            .apply()
        com.menglimi.gdust.v2.work.PowerWorker.schedule(app)
    }

    val hasSession: Boolean get() = portalToken.isNotEmpty() && jobNumber.isNotEmpty()
    val hasPowerBinding: Boolean get() = powerAccount.isNotBlank() && powerCode > 0 && powerRoomVerify.isNotBlank()
    val hasLlm: Boolean get() = llmBase.isNotEmpty() && llmKey.isNotEmpty() && llmModel.isNotEmpty()

    internal fun loadGradeSnapshot(year: String, semester: String): DataSnapshot<GradeReport>? {
        val owner = gradeSnapshotOwner() ?: return null
        if (webLogoutPending) return null
        return DataSnapshots.grades(secure.getString(snapshotKey("grades", owner, year, semester), null), year, semester)
    }

    internal fun saveGradeSnapshot(year: String, semester: String, snapshot: DataSnapshot<GradeReport>): Boolean {
        if (webLogoutPending) return false
        val owner = gradeSnapshotOwner() ?: java.util.UUID.randomUUID().toString().also {
            secure.edit().putString("teaching_snapshot_owner", it).commit()
        }
        return secure.edit().putString(snapshotKey("grades", owner, year, semester), DataSnapshots.encode(snapshot)).commit()
    }

    private fun gradeSnapshotOwner(): String? = jobNumber.takeIf { it.isNotBlank() }?.let { account ->
        secure.getString(snapshotKey("teaching_owner", account), null)?.takeIf { it.isNotBlank() } ?: account
    } ?: secure.getString("teaching_snapshot_owner", null)?.takeIf { it.isNotBlank() }

    /** 网页教务先于门户确认身份时，把匿名快照归到已确认账号；不复制或丢弃原始资料。 */
    internal fun promoteTeachingSnapshotOwner(account: String) {
        if (jobNumber.isNotBlank() || account.isBlank()) return
        val anonymous = secure.getString("teaching_snapshot_owner", null)?.takeIf { it.isNotBlank() } ?: return
        secure.edit().putString(snapshotKey("teaching_owner", account), anonymous).commit()
    }

    internal val chxCourseSnapshotOwner: String?
        get() = secure.getString("chx_snapshot_owner", null)?.takeIf { it.isNotBlank() }

    /** 只在业务读取成功后切换快照归属，Cookie 候选本身不算认证证据。 */
    internal fun acceptChxSnapshotOwner(uid: String) {
        require(uid.isNotBlank())
        val previous = chxCourseSnapshotOwner ?: DataSnapshots.cookieOwner(chxCookie)
        if (previous != null && previous != uid) {
            plain.edit().remove("chx_tabs").remove("chx_seen_notifications").commit()
            updateTodos { items -> items.filterNot { it.source == "chaoxing" } }
        }
        check(secure.edit().putString("chx_snapshot_owner", uid).commit()) { "未能保存学习通资料归属。" }
    }

    internal fun loadChxCourseSnapshot(uid: String): DataSnapshot<ChxCourseOverview>? {
        if (uid.isBlank() || webLogoutPending) return null
        return DataSnapshots.courses(secure.getString(snapshotKey("chx_courses", uid), null))
    }

    internal fun saveChxCourseSnapshot(uid: String, overview: ChxCourseOverview): Boolean {
        if (uid.isBlank() || webLogoutPending) return false
        val snapshot = DataSnapshot(overview, overview.loadedAt.coerceAtLeast(1L))
        return secure.edit().putString(snapshotKey("chx_courses", uid), DataSnapshots.encode(snapshot))
            .putString("chx_snapshot_owner", uid).commit()
    }

    internal fun loadNoticeSnapshot(source: NoticeSource): DataSnapshot<List<Notice>>? {
        val owner = if (source == NoticeSource.ACADEMIC) "public" else jobNumber.takeIf { it.isNotBlank() } ?: return null
        if (webLogoutPending && source != NoticeSource.ACADEMIC) return null
        return DataSnapshots.notices(secure.getString(snapshotKey("notices", owner, source.name), null))
    }

    internal fun saveNoticeSnapshot(source: NoticeSource, snapshot: DataSnapshot<List<Notice>>): Boolean {
        val owner = if (source == NoticeSource.ACADEMIC) "public" else jobNumber.takeIf { it.isNotBlank() } ?: return false
        if (webLogoutPending && source != NoticeSource.ACADEMIC) return false
        return secure.edit().putString(snapshotKey("notices", owner, source.name), DataSnapshots.encode(snapshot)).commit()
    }

    private fun localDataOwner(create: Boolean = false): String? {
        if (jobNumber.isNotBlank()) return jobNumber
        secure.getString("local_data_owner", null)?.takeIf { it.isNotBlank() }?.let { return it }
        if (!create) return null
        val owner = java.util.UUID.randomUUID().toString()
        return owner.takeIf { secure.edit().putString("local_data_owner", it).commit() }
    }

    fun loadAiChatJson(): String? {
        val owner = localDataOwner() ?: return null
        return secure.getString(snapshotKey("ai_chat", owner), null)
    }

    fun saveAiChatJson(json: String): Boolean {
        if (runCatching { com.google.gson.JsonParser.parseString(json).isJsonArray }.getOrDefault(false).not()) return false
        val owner = localDataOwner(create = true) ?: return false
        return secure.edit().putString(snapshotKey("ai_chat", owner), json).commit()
    }

    fun loadNoticeSummary(notices: List<Notice>): String? {
        val owner = localDataOwner() ?: return null
        return secure.getString(snapshotKey("notice_summary", owner, gson.toJson(notices)), null)
    }

    fun saveNoticeSummary(notices: List<Notice>, summary: String): Boolean {
        if (summary.isBlank()) return false
        val owner = localDataOwner(create = true) ?: return false
        return secure.edit().putString(snapshotKey("notice_summary", owner, gson.toJson(notices)), summary).commit()
    }

    internal fun loadPowerRooms(account: String, code: Int): DataSnapshot<List<PowerRoom>>? =
        DataSnapshots.powerRooms(secure.getString(snapshotKey("power_rooms", account, code.toString()), null))

    internal fun savePowerRooms(account: String, code: Int, rooms: List<PowerRoom>): Boolean = secure.edit()
        .putString(snapshotKey("power_rooms", account, code.toString()),
            DataSnapshots.encode(DataSnapshot(rooms, System.currentTimeMillis()))).commit()

    private fun snapshotKey(kind: String, vararg values: String): String {
        val source = values.joinToString("") { "${it.length}:$it" }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "snapshot_${kind}_$digest"
    }

    /** 手动网页备用导入后复用 WebView 自己的持久 CookieManager 会话；白名单见 ChxSso.isTeachingWebEntry。 */
    fun saveWebImport(pageUrl: String) {
        require(ChxSso.isTeachingWebEntry(pageUrl)) { "只保存学校域名的网页入口" }
        if (jobNumber.isBlank() && gradeSnapshotOwner() == null) secure.edit()
            .putString("teaching_snapshot_owner", java.util.UUID.randomUUID().toString()).apply()
        plain.edit().putString("web_import_page", pageUrl).apply()
    }

    fun loadWebImportPage(): String = plain.getString("web_import_page", "").orEmpty()

    fun clearWebImportPage() { plain.edit().remove("web_import_page").apply() }

    fun clearPortalSession() {
        secure.edit().remove("token").apply()
    }

    var webLogoutPending: Boolean
        get() = plain.getBoolean("web_logout_pending", false)
        set(value) = plain.edit().putBoolean("web_logout_pending", value).apply()

    internal fun clearAccountSession(preserveSnapshots: Boolean = false) {
        val secureEdit = secure.edit().remove("token").remove("ticket").remove("job").remove("name")
            .remove("chx_cookie").remove("birthday_account").remove("birthday_month_day").remove("birthday_shown")
            .remove("teaching_snapshot_owner").remove("chx_snapshot_owner").remove("local_data_owner")
        secure.all.keys.filter { (!preserveSnapshots && it.startsWith("snapshot_")) ||
            it.startsWith("snapshot_ai_chat_") || it.startsWith("snapshot_notice_summary_") }.forEach(secureEdit::remove)
        secureEdit.apply()
        plain.edit().remove("blocks").remove("calendar").remove("schedule_source")
            .remove("web_import_page").remove("chx_cookie_at").remove("last_sync_at")
            .remove("chx_tabs").remove("chx_seen_notifications")
            .remove("notice_read_at").putBoolean("web_logout_pending", true).apply()
        updateTodos { items -> items.filterNot { it.source == "chaoxing" } }
        plain.edit().remove("last_auto_sync_attempt_at").remove("auto_sync_needs_login").apply()
    }

    // ── 备份与恢复：只含课表/校历/待办/采样/偏好；不含登录凭证与密码 ──

    fun exportBackup(): String {
        val payload = mapOf(
            "app" to "gdust-v2",
            "schema" to 1,
            "exportedAt" to System.currentTimeMillis(),
            "calendar" to plain.getString("calendar", null),
            "blocks" to plain.getString("blocks", null),
            "schedule_source" to schoolScheduleSource,
            "todos" to plain.getString("todos", null),
            "samples" to plain.getString("samples", null),
            "term_start" to schoolTermStartDate,
            "term_weeks" to schoolTermWeeks,
            "term_week" to schoolTermCurrentWeek,
            "start_tab" to startTab,
            "ui_style" to uiStyle,
            "monet_enabled" to monetEnabled,
            "sample_interval" to sampleIntervalMinutes,
            "power_query_preset" to powerQueryPreset.key,
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
        val restoredSamples = root["samples"] as? String
        (root["term_start"] as? String)?.let { edit.putString("term_start", it) }
        (root["term_weeks"] as? Double)?.let { edit.putInt("term_weeks", it.toInt()) }
        (root["term_week"] as? Double)?.let { edit.putInt("term_week", it.toInt()) }
        (root["start_tab"] as? Double)?.let { edit.putInt("start_tab", it.toInt()) }
        (root["ui_style"] as? String)?.let { edit.putString("ui_style", it) }
        (root["monet_enabled"] as? Boolean)?.let { edit.putBoolean("monet_enabled", it) }
        (root["sample_interval"] as? Double)?.let {
            edit.putInt("sample_interval", it.toInt().coerceIn(15, 120))
                .putString("power_query_preset", PowerQueryPreset.CUSTOM.key)
        }
        (root["power_query_preset"] as? String)?.let {
            edit.putString("power_query_preset", PowerQueryPreset.fromKey(it).key)
        }
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
        restoredSamples?.let { plain.edit().putString("samples", it).apply() }
        val blocks = loadBlocks().size
        val todos = loadTodos().size
        return "课表 $blocks 张卡 · 待办 $todos 条 · 偏好与水电绑定已恢复"
    }
}

/** 成功加载且识别作业详情页；deadlineMillis=0 表示详情没有可用截止时间。 */
internal data class ChxWorkDetailParseResult(val deadlineMillis: Long)

/** 非作业详情页返回 null；已识别详情即使没有截止时间也是成功解析。 */
internal fun parseChxWorkDetail(html: String, nowMillis: Long): ChxWorkDetailParseResult? {
    val detail = org.jsoup.Jsoup.parse(html).selectFirst(".infoHead") ?: return null
    return ChxWorkDetailParseResult(ChxWorkParser.parseDeadlineMillis(detail.outerHtml(), nowMillis))
}

/** 会话门面：所有数据操作的唯一入口。 */
class Repository(context: Context) {

    private val app = context.applicationContext
    val store = Store(context)
    private val sessionGuard = SessionGuard()
    private val mutableSessionVersion = MutableStateFlow(0L)
    val sessionVersion = mutableSessionVersion.asStateFlow()
    private val mutableAccountLogin = MutableStateFlow(if (store.hasSession) AccountLoginState.UNVERIFIED else AccountLoginState.SIGNED_OUT)
    val accountLogin = mutableAccountLogin.asStateFlow()
    private val accountCheckMutex = Mutex()
    private var accountCheckedAt = 0L
    private val mutableChxSessionVersion = MutableStateFlow(0L)
    val chxSessionVersion = mutableChxSessionVersion.asStateFlow()
    @Volatile private var clearingChxSession = false
    private val authenticationMutex = Mutex()
    private val scheduleSyncMutex = Mutex()
    private val webCleanupMutex = Mutex()
    private val chxSessionMutex = Mutex()
    private val chxRequests = ChxRequestControl()
    val chxRequestPause = chxRequests.pause
    private val api = GdustApi()
    private val teachingSso = TeachingSsoClient()
    /** 登录后教务会话 Cookie 持久化在这里；后续同步、续租、定时任务共用。 */
    val teachingCookies = PersistentCookieJar(context)

    // ── 三服务连接状态：进程内 StateFlow，模型与转换见 SchoolServicesState.kt ──
    private val mutableServiceConnections = MutableStateFlow(SchoolServicesState())

    /** 转换器锁内统一发布：StateFlow 只在 tracker 更新点写入，不会回退到旧快照。 */
    private val servicesTracker = SchoolServicesTracker {
        mutableServiceConnections.value = it
    }

    /** UI 订阅：门户/教务/学习通独立认证状态与读取问题；首次访问即完成版本绑定与 store 恢复（SAVED）。 */
    val serviceConnections: StateFlow<SchoolServicesState>
        get() {
            ensureServicesBound()
            return mutableServiceConnections
        }

    internal fun portalSession(): PortalSession? {
        val version = sessionGuard.current()
        return sessionGuard.withCurrent(version) {
            if (!store.hasSession || store.webLogoutPending) null
            else PortalSession(store.jobNumber, store.portalToken, version)
        }
    }

    fun isCurrentSession(version: Long): Boolean = sessionGuard.current() == version

    /** WebView 重新认证前使旧课程请求过期，不清除正在复用的网页 Cookie。 */
    fun invalidateChxRequests() = synchronized(sessionGuard) {
        mutableChxSessionVersion.value++
        // 只作废学习通进行中的检查，保留已经验证的连接状态及同 UID 恢复目标。
        ensureServicesBound()
        servicesTracker.bindChx(mutableChxSessionVersion.value)
    }

    /** 只有实时网页有效会话的 UID 才能绑定业务；请求版本失效不等同于身份变化。 */
    private suspend fun currentChxIdentity(version: Long, chxVersion: Long): ChxRequestControl.Identity =
        withContext(Dispatchers.Main.immediate) {
            webCleanupMutex.withLock {
                sessionGuard.withCurrent(version) {
                    if (store.webLogoutPending || clearingChxSession || chxSessionVersion.value != chxVersion) {
                        throw CancellationException("学习通登录状态已变更")
                    }
                    val uid = DataSnapshots.cookieOwner(ChxWebSession.candidateCookie())
                        ?: throw AuthFailed(401, "请先登录学习通")
                    chxRequests.bindIdentity(uid)
                }
            }
        }

    /** HTTP 早锁存只核对身份，不检查 Job 或普通请求版本，以保留同身份取消后收到的暂停。 */
    private fun checkChxIdentity(expected: ChxRequestControl.Identity) {
        chxRequests.checkIdentity(expected)
        val uid = DataSnapshots.cookieOwner(ChxWebSession.candidateCookie())
        if (uid != expected.uid) {
            if (uid != null) chxRequests.bindIdentity(uid)
            throw CancellationException("学习通身份已变更")
        }
        chxRequests.checkIdentity(expected)
    }

    internal fun saveWebSession(pageUrl: String, version: Long, generation: Long? = null): Boolean =
        sessionGuard.withCurrent(version) {
            if (store.webLogoutPending || !TeachingEndpoints.isLandingPage(pageUrl)) return@withCurrent false
            store.saveWebImport(pageUrl.substringBefore('?').substringBefore('#'))
            store.automaticSyncNeedsLogin = false
            applyServicePatch(SchoolService.TEACHING, version, null, ServiceAuthStatus.SAVED, null,
                System.currentTimeMillis(), generation)
            true
        }

    /** 继续学校网页认证时保留同一 CookieManager；只在尚未完成退出清理时先清理。 */
    internal suspend fun prepareSchoolWebLogin(): Long = authenticationMutex.withLock {
        currentCoroutineContext().ensureActive()
        val version = sessionGuard.current()
        if (store.webLogoutPending) check(clearWebLoginData()) { "旧网页会话尚未清除，请重试退出后再登录。" }
        currentCoroutineContext().ensureActive()
        sessionGuard.withCurrent(version) {
            // 历史版本曾持久化一次性票据；新流程绝不拿它续换或跨服务使用。
            store.casTicket = ""
            invalidateChxRequests()
        }
        version
    }

    /** 门户网页自己消费 ticket 后，只桥接最终 TOKEN，并用门户身份接口独立确认。 */
    internal suspend fun acceptPortalWebSession(token: String, version: Long): Boolean =
        withServiceProbe(SchoolService.PORTAL, version, null) { generation ->
        currentCoroutineContext().ensureActive()
        sessionGuard.withCurrent(version) { check(!store.webLogoutPending) }
        val identity = runCatching { api.userInfo(token) }
        identity.exceptionOrNull()?.let { failure ->
            if (failure is CancellationException) throw failure
            recordServiceFailure(SchoolService.PORTAL, version, null, failure, generation)
        }
        val (account, name) = identity.getOrThrow()
        currentCoroutineContext().ensureActive()
        if (account.isBlank() || account.equals("guest", ignoreCase = true)) {
            // TOKEN 被换出 guest 身份：明确拒绝，不是网络问题。
            applyServicePatch(SchoolService.PORTAL, version, null, ServiceAuthStatus.AUTH_REQUIRED, null,
                System.currentTimeMillis(), generation)
            return@withServiceProbe false
        }
        sessionGuard.withCurrent(version) {
            check(!store.webLogoutPending)
            if (!servicesTracker.accepts(SchoolService.PORTAL, version, null, generation)) {
                throw CancellationException("门户验证状态已变更")
            }
            check(store.jobNumber.isBlank() || store.jobNumber == account) {
                "学校网页账号与当前账号不同，请先退出当前账号再登录。"
            }
            store.promoteTeachingSnapshotOwner(account)
            store.portalToken = token
            store.jobNumber = account
            store.realName = name
            store.automaticSyncNeedsLogin = false
            mutableAccountLogin.value = AccountLoginState.VALID
            accountCheckedAt = android.os.SystemClock.elapsedRealtime()
            applyServicePatch(SchoolService.PORTAL, version, null, ServiceAuthStatus.VERIFIED, null,
                System.currentTimeMillis(), generation)
        }
        true
    }

    /** 用 CookieManager 针对精确课表 URL 取 Cookie，教务自己返回业务 JSON 后才保存入口。 */
    internal suspend fun verifyTeachingWebSession(pageUrl: String, version: Long, generation: Long? = null): Boolean? =
        withContext(Dispatchers.Main.immediate) {
            if (generation == null) return@withContext withServiceProbe(SchoolService.TEACHING, version, null) {
                verifyTeachingWebSession(pageUrl, version, it)
            }
            val probeJob = currentCoroutineContext()[Job]
            fun checkSession() {
                probeJob?.ensureActive()
                sessionGuard.withCurrent(version) {
                    if (store.webLogoutPending || !servicesTracker.accepts(SchoolService.TEACHING, version, null, generation)) {
                        throw CancellationException("网页登录状态已变更")
                    }
                }
            }
            checkSession()
            val request = teachingProbeRequest(pageUrl, store.loadSchoolCalendar()) ?: run {
                // 连验证请求都构造不出来：无会话材料，不是明确拒绝。
                applyServicePatch(SchoolService.TEACHING, version, null, ServiceAuthStatus.NOT_CONNECTED, null,
                    System.currentTimeMillis(), generation)
                return@withContext false
            }
            val hasCookie = webCleanupMutex.withLock {
                checkSession()
                !android.webkit.CookieManager.getInstance().getCookie(request.url.toString()).isNullOrBlank()
            }
            if (!hasCookie) {
                applyServicePatch(SchoolService.TEACHING, version, null, ServiceAuthStatus.NOT_CONNECTED, null,
                    System.currentTimeMillis(), generation)
                return@withContext false
            }
            val client = SessionHttpClient(allowedUrl = { it == request.url }, checkSession = ::checkSession,
                webCookieMutex = webCleanupMutex)
            var verificationFailed = false
            val verified = try {
                teachingSessionEvidence(client.execute(request))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                // 保留失败证据：网络不可达与业务/格式失败分开，不吞成“不确定”。
                recordServiceFailure(SchoolService.TEACHING, version, null, e, generation)
                verificationFailed = true
                null
            }
            checkSession()
            when (verified) {
                true -> {
                    saveWebSession(pageUrl, version, generation)
                    applyServicePatch(SchoolService.TEACHING, version, null, ServiceAuthStatus.VERIFIED, null,
                        System.currentTimeMillis(), generation)
                }
                false -> applyServicePatch(SchoolService.TEACHING, version, null, ServiceAuthStatus.AUTH_REQUIRED,
                    null, System.currentTimeMillis(), generation)
                null -> if (!verificationFailed) {
                    applyServicePatch(SchoolService.TEACHING, version, null, null, ServiceProblem.UNCONFIRMED,
                        System.currentTimeMillis(), generation)
                }
            }
            verified
        }

    /** 仅跟随当前已认证教务页面的只读个人信息菜单；获取不到生日不影响服务连接。 */
    suspend fun refreshTeachingBirthday(): Boolean = withContext(Dispatchers.IO) {
        val version = sessionGuard.current()
        val probeJob = currentCoroutineContext()[Job]
        val (account, pageUrl) = sessionGuard.withCurrent(version) { store.jobNumber to store.loadWebImportPage() }
        if (account.isBlank() || !TeachingEndpoints.isLandingPage(pageUrl)) return@withContext false
        val base = TeachingEndpoints.baseForPage(pageUrl) ?: return@withContext false
        val page = pageUrl.toHttpUrl().newBuilder().query(null).fragment(null).build()
        fun checkSession() {
            probeJob?.ensureActive()
            sessionGuard.withCurrent(version) {
                if (store.webLogoutPending || store.jobNumber != account) throw CancellationException("网页登录状态已变更")
            }
        }
        try {
            val http = SessionHttpClient(allowedUrl = { TeachingBirthdayApi.isAllowed(base, page, it) },
                checkSession = ::checkSession, webCookieMutex = webCleanupMutex)
            val birthday = TeachingBirthdayApi(http::execute).load(page.toString(), account) ?: return@withContext false
            checkSession()
            sessionGuard.withCurrent(version) { store.saveBirthday(birthday, account) }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    internal fun saveChxCandidate(cookie: String, version: Long, chxVersion: Long): Boolean = sessionGuard.withCurrent(version) {
        if (store.webLogoutPending || clearingChxSession || chxSessionVersion.value != chxVersion ||
            !ChxSso.hasSessionCookie(cookie)) return@withCurrent false
        if (store.chxCookie != cookie) store.saveChxCookie(cookie)
        true
    }

    private fun teachingSessionJar(version: Long) = object : okhttp3.CookieJar {
        override fun loadForRequest(url: okhttp3.HttpUrl) = sessionGuard.withCurrent(version) {
            teachingCookies.loadForRequest(url)
        }
        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
            sessionGuard.withCurrent(version) { teachingCookies.saveFromResponse(url, cookies) }
        }
    }

    private fun resetAccountSession(preserveSnapshots: Boolean = false): Long = synchronized(sessionGuard) {
        val version = sessionGuard.invalidate()
        sessionGuard.withCurrent(version) {
            teachingCookies.clear()
            store.clearAccountSession(preserveSnapshots)
            mutableAccountLogin.value = AccountLoginState.SIGNED_OUT
            accountCheckedAt = 0L
            if (!preserveSnapshots) lastSsoTicket = null
            invalidateChxRequests()
            chxRequests.invalidateIdentity()
            mutableSessionVersion.value = version
        }
        // 账号会话重置：三服务全部回到无候选；旧账号版本此后不能再写状态。
        servicesTracker.bindAccount(version) { SchoolServicesState() }
        // chx 版本与账号版本同步登记，否则重置后的 CHX 补丁会被版本校验拒绝。
        servicesTracker.bindChx(mutableChxSessionVersion.value)
        version
    }

    /** 本机退出：立刻隔离旧请求，随后等待 WebView 完成清理。个人偏好与手动待办保留。 */
    suspend fun logout(): Boolean = withContext(NonCancellable + Dispatchers.Main.immediate) {
        resetAccountSession()
        com.menglimi.gdust.v2.work.ScheduleSyncWorker.cancel(app)
        com.menglimi.gdust.v2.work.LessonReminderWorker.cancel(app)
        val cleared = clearWebLoginData()
        try {
            com.menglimi.gdust.v2.widget.WidgetRefresher.refreshAll(app)
        } catch (_: Exception) { /* 小组件刷新失败不阻止本机退出。 */ }
        cleared
    }

    private suspend fun clearWebLoginData(): Boolean = webCleanupMutex.withLock {
        withContext(Dispatchers.Main.immediate) {
            try {
                val cm = android.webkit.CookieManager.getInstance()
                val completed = withTimeoutOrNull(5_000L) {
                    suspendCancellableCoroutine { continuation ->
                        cm.removeAllCookies {
                            if (continuation.isActive) continuation.resume(true)
                        }
                    }
                } == true
                cm.flush()
                android.webkit.WebStorage.getInstance().deleteAllData()
                val cleared = completed && !cm.hasCookies()
                store.webLogoutPending = !cleared
                cleared
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        }
    }

    private suspend fun beginLogin(): Long {
        val version = sessionGuard.current()
        // 认证失败、取消或断网均不动现有资料；只有显式退出遗留的清理需要先完成。
        if (store.webLogoutPending) check(clearWebLoginData()) { "旧网页会话尚未清除，请重试退出后再登录。" }
        currentCoroutineContext().ensureActive()
        sessionGuard.withCurrent(version) { }
        return version
    }

    /** 校历：优先本机缓存；没有再找门户；都没有就给可操作提示。 */
    private fun calendarForSync(raw: CalendarInfo, semester: String? = null): CalendarInfo = DataSnapshots.calendarForSync(
        raw, semester, store.schoolTermStartDate, store.schoolTermWeeks, store.schoolTermCurrentWeek,
    )

    private suspend fun resolveCalendar(version: Long, semester: String? = null): CalendarInfo {
        sessionGuard.withCurrent(version) { store.loadSchoolCalendar()?.let { calendarForSync(it, semester) } }?.let { return it }
        val portal = portalSession()
        if (portal != null) {
            try {
                val raw = api.calendar(portal.token, portal.account)
                return sessionGuard.withCurrent(version) { calendarForSync(raw, semester) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { }
        }
        sessionGuard.withCurrent(version) { }
        error("本机还没有校历。请先用「网页登录导入」填一次学期设置（或重新登录门户），再同步。")
    }

    /** 兼容旧调用入口；一次性 ticket 已消费，续租只能复用真实会话或重新走学校 SSO。 */
    suspend fun syncTermWithLogin(): Pair<CalendarInfo, List<CourseBlock>> {
        sessionGuard.withCurrent(sessionGuard.current()) { store.casTicket = "" }
        if (!hasTeachingSession()) {
            applyServicePatch(SchoolService.TEACHING, sessionGuard.current(), null, ServiceAuthStatus.NOT_CONNECTED,
                null, System.currentTimeMillis())
            throw AuthFailed(401, "请完成学校统一登录，自动连接教务后再同步。")
        }
        return syncTermWithCookies()
    }

    /** 四小时维护期限与电量调度共用；失败退避不改变上次成功时间，重开 App 不推迟期限。 */
    fun nextAutomaticSyncAt(now: Long = System.currentTimeMillis()): Long? {
        if (!store.autoSyncEnabled || store.webLogoutPending || store.automaticSyncNeedsLogin || store.jobNumber.isBlank()) return null
        if (!store.hasSession && !TeachingEndpoints.isLandingPage(store.loadWebImportPage())) return null
        return ScheduleSyncPolicy.nextAt(now, store.lastSyncAt, store.lastAutoSyncAttemptAt)
    }

    /** 每四小时用学校 SSO 维护会话并拉整学期；与前台/电量请求串行去重。 */
    suspend fun syncTermSilentlyIfDue(): Pair<CalendarInfo, List<CourseBlock>>? = scheduleSyncMutex.withLock {
        val version = sessionGuard.current()
        val now = System.currentTimeMillis()
        val due = nextAutomaticSyncAt(now) ?: return@withLock null
        if (due > now) return@withLock null
        checkAutomaticSync(version)
        sessionGuard.withCurrent(version) { store.lastAutoSyncAttemptAt = now }
        val renewal = runCatching { renewSchoolSession(version, background = true) }
        renewal.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        checkAutomaticSync(version)
        val (cal, blocks) = try {
            readTermWithCookies(version)
        } catch (error: AuthFailed) {
            // 会话恢复遇到网络/维护异常时，不把它伪装成需要用户重新绑定。
            val failure = renewal.exceptionOrNull()
            if (failure != null && failure !is AuthFailed) throw failure
            sessionGuard.withCurrent(version) {
                checkAutomaticSync(version)
                store.automaticSyncNeedsLogin = true
            }
            throw error
        }
        sessionGuard.withCurrent(version) {
            checkAutomaticSync(version)
            store.automaticSyncNeedsLogin = false
            store.saveSchedule(cal, blocks, "zf_term", System.currentTimeMillis())
        }
        cal to blocks
    }

    private fun checkAutomaticSync(version: Long) {
        sessionGuard.withCurrent(version) {
            if (!store.autoSyncEnabled || store.webLogoutPending) throw CancellationException("自动同步已停止")
        }
    }

    private suspend fun renewSchoolSession(version: Long, background: Boolean = false) = authenticationMutex.withLock {
        val requestJob = currentCoroutineContext()[Job]
        fun checkSession() {
            requestJob?.ensureActive()
            sessionGuard.withCurrent(version) {
                if (store.webLogoutPending) throw CancellationException("学校登录状态已变更")
            }
            if (background) checkAutomaticSync(version)
        }
        checkSession()
        val page = sessionGuard.withCurrent(version) { store.loadWebImportPage() }
        SchoolSessionRenewal(app, ::checkSession,
            acceptPortal = { token ->
                checkSession()
                acceptPortalWebSession(token, version).also { checkSession() }
            },
            verifyTeaching = { url ->
                checkSession()
                verifyTeachingWebSession(url, version).also { checkSession() }
            },
        ).renew(page)
    }

    /** 手动同步先复用 Cookie；只有明确认证失效才静默恢复一次，不重放旧 ticket。 */
    suspend fun syncTermWithCookies(semester: String? = null): Pair<CalendarInfo, List<CourseBlock>> = scheduleSyncMutex.withLock {
        val version = sessionGuard.current()
        val (cal, blocks) = try {
            readTermWithCookies(version, semester)
        } catch (_: AuthFailed) {
            renewSchoolSession(version)
            readTermWithCookies(version, semester)
        }
        currentCoroutineContext().ensureActive()
        sessionGuard.withCurrent(version) {
            store.automaticSyncNeedsLogin = false
            store.saveSchedule(cal, blocks, "zf_term", System.currentTimeMillis())
        }
        cal to blocks
    }

    private suspend fun readTermWithCookies(version: Long, semester: String? = null): Pair<CalendarInfo, List<CourseBlock>> {
        val cal = resolveCalendar(version, semester)
        return withServiceProbe(SchoolService.TEACHING, version, null) { generation ->
        val hasDirectCookie = sessionGuard.withCurrent(version) {
            listOf("172.16.254.1", "gkjx.gdust.edu.cn", "webvpn.gdust.edu.cn")
                .any(teachingCookies::hasSessionFor)
        }
        val page = sessionGuard.withCurrent(version) { store.loadWebImportPage() }
        val blocks = try {
            if (TeachingEndpoints.isLandingPage(page)) {
                fetchWebTerm(cal, page, version)
            } else if (hasDirectCookie) {
                teachingSso.fetchWithCookies(cal, teachingSessionJar(version))
            } else {
                throw AuthFailed(401, "教务会话已失效，请完成学校统一登录。")
            }
        } catch (e: Exception) {
            // 校历门户请求和本机存储不在标记范围内；认证失效与取消原样抛出。
            recordServiceFailure(SchoolService.TEACHING, version, null, e, generation)
            throw e.asTeachingSyncFailure()
        }
        currentCoroutineContext().ensureActive()
        sessionGuard.withCurrent(version) { }
        applyServicePatch(SchoolService.TEACHING, version, null, ServiceAuthStatus.VERIFIED, null,
            System.currentTimeMillis(), generation)
        cal to blocks
        }
    }

    /**
     * 用网页登录留下的会话读取**指定**学期课表。
     * 教务网页导入页：网页在独立 Activity（TeachingWebActivity）里登录，回到 Compose 后走这里读，
     * 不再依赖页面内 JS fetch。
     */
    suspend fun importTermWithWebSession(cal: CalendarInfo): List<CourseBlock> = scheduleSyncMutex.withLock {
        val version = sessionGuard.current()
        withServiceProbe(SchoolService.TEACHING, version, null) { generation ->
        val target = sessionGuard.withCurrent(version) { calendarForSync(cal) }
        val page = sessionGuard.withCurrent(version) { store.loadWebImportPage() }
        val blocks = try {
            fetchWebTerm(target, page, version)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            recordServiceFailure(SchoolService.TEACHING, version, null, e, generation)
            throw e
        }
        currentCoroutineContext().ensureActive()
        sessionGuard.withCurrent(version) {
            store.saveSchedule(target, blocks, "zf_term", System.currentTimeMillis())
        }
        applyServicePatch(SchoolService.TEACHING, version, null, ServiceAuthStatus.VERIFIED, null,
            System.currentTimeMillis(), generation)
        blocks
        }
    }

    /** 请求头由 CookieManager 按接口的 Domain/Path/Secure 匹配，并接回学校轮换的 Set-Cookie。 */
    private suspend fun fetchWebTerm(cal: CalendarInfo, page: String, version: Long): List<CourseBlock> {
        val probeJob = currentCoroutineContext()[Job]
        fun checkSession() {
            probeJob?.ensureActive()
            sessionGuard.withCurrent(version) {
                if (store.webLogoutPending) throw CancellationException("网页登录状态已变更")
            }
        }
        checkSession()
        val request = teachingProbeRequest(page, cal)
            ?: throw AuthFailed(401, "还没有教务网页会话，请先完成学校统一登录。")
        val client = SessionHttpClient(allowedUrl = { it == request.url }, checkSession = ::checkSession,
            webCookieMutex = webCleanupMutex)
        val response = client.execute(request)
        when (teachingSessionEvidence(response)) {
            false -> throw AuthFailed(401, "教务登录已失效，请完成学校统一登录。")
            null -> error("教务未返回可验证的课表数据，请稍后重试。")
            true -> Unit
        }
        checkSession()
        return ZfScheduleParser.parse(response.body, cal)
    }

    /** 是否已有任一教务会话（直连 Cookie 或网页登录会话）。 */
    fun hasTeachingSession(): Boolean {
        if (listOf("172.16.254.1", "gkjx.gdust.edu.cn", "webvpn.gdust.edu.cn")
                .any(teachingCookies::hasSessionFor)) return true
        val page = store.loadWebImportPage()
        return page.isNotBlank() && !android.webkit.CookieManager.getInstance()
            .getCookie(page).isNullOrBlank()
    }

    /** 教务状态只认业务接口；网页会话与读取课表使用同一个 CookieManager。 */
    suspend fun teachingSessionAlive(): Boolean = runCatching {
        val version = sessionGuard.current()
        val page = sessionGuard.withCurrent(version) { store.loadWebImportPage() }
        if (TeachingEndpoints.isLandingPage(page)) verifyTeachingWebSession(page, version) == true
        else false // 旧 Cookie 只能说明本机保存过会话，不能证明本次教务认证有效。
    }.onFailure { if (it is CancellationException) throw it }.getOrDefault(false)
    // ── 三服务连接状态维护：绑定、探活与结果记录 ──

    /**
     * 绑定当前账号版本并同步学习通版本；版本变化才读 store 恢复（SAVED，不恢复 VERIFIED）。
     * 幂等：已绑定且版本未变时零开销，不降级既有状态；所有写路径入口都会先走到这里。
     */
    private fun ensureServicesBound(): Boolean = synchronized(sessionGuard) {
        val changed = servicesTracker.bindAccount(sessionGuard.current()) { restoredServiceState() }
        servicesTracker.bindChx(mutableChxSessionVersion.value)
        changed
    }

    /** 进程恢复只认凭据材料：一律 SAVED，等下一次真实验证再升级。 */
    private fun restoredServiceState(): SchoolServicesState {
        if (store.webLogoutPending) return SchoolServicesState()
        val saved = SchoolServiceStatus(auth = ServiceAuthStatus.SAVED)
        // 恢复只读保存过的入口，不在 Worker 冷启动时初始化 WebView；真实 Cookie 由探活检查。
        val teachingSaved = TeachingEndpoints.isLandingPage(store.loadWebImportPage()) ||
            listOf("172.16.254.1", "gkjx.gdust.edu.cn", "webvpn.gdust.edu.cn").any(teachingCookies::hasSessionFor)
        return SchoolServicesState(
            portal = if (store.hasSession) saved else SchoolServiceStatus(),
            teaching = if (teachingSaved) saved else SchoolServiceStatus(),
            chx = if (store.hasChxSession) saved else SchoolServiceStatus(),
        )
    }

    private fun applyServicePatch(
        service: SchoolService,
        version: Long,
        chxVersion: Long?,
        auth: ServiceAuthStatus?,
        problem: ServiceProblem?,
        checkedAt: Long = 0L,
        generation: Long? = null,
    ): Boolean = synchronized(sessionGuard) {
        if (!isCurrentSession(version)) return@synchronized false
        ensureServicesBound()
        servicesTracker.patch(service, version, chxVersion, auth, problem, checkedAt, generation) != null
    }

    /** 每次实际请求均携带服务代数；重新登录、退出或更新的请求都会令旧结果失效。 */
    private fun beginServiceProbe(service: SchoolService, version: Long, chxVersion: Long?): Long =
        sessionGuard.withCurrent(version) {
            ensureServicesBound()
            servicesTracker.beginProbe(service, version, chxVersion)
                ?: throw CancellationException("服务连接状态已变更")
        }

    private suspend fun <T> withServiceProbe(
        service: SchoolService, version: Long, chxVersion: Long?,
        block: suspend (Long) -> T,
    ): T {
        val generation = beginServiceProbe(service, version, chxVersion)
        try {
            return block(generation)
        } finally {
            servicesTracker.cancelProbe(service, version, chxVersion, generation)
        }
    }

    /** 业务失败统一记录：AuthFailed=明确拒绝；网络不可达=NETWORK；其余业务/格式=READ；取消不落状态。 */
    private fun recordServiceFailure(
        service: SchoolService,
        version: Long,
        chxVersion: Long?,
        failure: Throwable,
        generation: Long? = null,
    ) {
        if (failure is CancellationException) return
        if (failure is AuthFailed) {
            applyServicePatch(service, version, chxVersion, ServiceAuthStatus.AUTH_REQUIRED, null,
                System.currentTimeMillis(), generation)
        } else {
            applyServicePatch(service, version, chxVersion, null,
                if (failure.isNetworkDown()) ServiceProblem.NETWORK else ServiceProblem.READ,
                System.currentTimeMillis(), generation)
        }
    }

    /**
     * 只检查指定服务的连接；结果落 [serviceConnections]，普通异常不冒给 UI（取消照常抛）。
     * 探活令牌传递给内部记录点：内部带令牌提交不递增代数，外层 finish 仍有效；
     * 期间任何无令牌补丁（新登录/业务结果）会作废旧令牌，晚到的探活结果被拒。
     */
    suspend fun refreshServiceConnection(service: SchoolService) {
        ensureServicesBound()
        val version = sessionVersion.value
        val chxVersion = mutableChxSessionVersion.value
        val guardedChx = if (service == SchoolService.CHX) chxVersion else null
        val probe = servicesTracker.beginProbe(service, version, guardedChx)
        if (probe == null) return
        try {
            val outcome = when (service) {
                SchoolService.PORTAL -> probePortal(probe)
                SchoolService.TEACHING -> probeTeaching(version, probe)
                SchoolService.CHX -> probeChx(probe)
            }
            if (outcome.skipFinish) {
                // false/null 的细分（无候选 vs 明确拒绝 vs 分类失败）已由被探方法内部记录，这里只收尾。
                servicesTracker.cancelProbe(service, version, guardedChx, probe)
            } else {
                servicesTracker.finishProbe(service, version, guardedChx, probe, outcome.auth, outcome.problem,
                    System.currentTimeMillis())
            }
        } catch (cancelled: CancellationException) {
            servicesTracker.cancelProbe(service, version, guardedChx, probe)
            throw cancelled
        } catch (failure: Exception) {
            recordServiceFailure(service, version, guardedChx, failure, probe)
            servicesTracker.cancelProbe(service, version, guardedChx, probe)
        }
    }

    private class ProbeOutcome(val auth: ServiceAuthStatus?, val problem: ServiceProblem?, val skipFinish: Boolean = false)

    private suspend fun probePortal(generation: Long): ProbeOutcome {
        refreshAccountLogin(force = true, generation = generation)
        return ProbeOutcome(null, null, skipFinish = true)
    }

    private suspend fun probeTeaching(version: Long, generation: Long): ProbeOutcome {
        if (!hasTeachingSession()) return ProbeOutcome(ServiceAuthStatus.NOT_CONNECTED, null)
        val page = sessionGuard.withCurrent(version) { store.loadWebImportPage() }
        if (!TeachingEndpoints.isLandingPage(page)) {
            // 只有直连 Cookie 候选：没有可复用的业务探活入口。从未验证过升级为 SAVED，其余保持原认证。
            val auth = if (mutableServiceConnections.value[SchoolService.TEACHING].auth ==
                ServiceAuthStatus.NOT_CONNECTED
            ) ServiceAuthStatus.SAVED else null
            return ProbeOutcome(auth, ServiceProblem.UNCONFIRMED)
        }
        return when (verifyTeachingWebSession(page, version, generation)) {
            true -> ProbeOutcome(ServiceAuthStatus.VERIFIED, null)
            else -> ProbeOutcome(null, null, skipFinish = true)
        }
    }

    private suspend fun probeChx(generation: Long): ProbeOutcome {
        chxSessionAlive(generation)
        // 分类已由探活写入；只收尾，不能把 NETWORK/READ 再覆盖成 UNCONFIRMED。
        return ProbeOutcome(null, null, skipFinish = true)
    }

    /**
     * 网页加载/验证失败（非明确拒绝）：只补问题标记——已分类的证据（NETWORK/READ）与
     * 已判定的明确拒绝都不覆盖；供登录 Activity 调用，不抛。
     */
    fun recordServiceUnavailable(service: SchoolService, version: Long) {
        runCatching {
            ensureServicesBound()
            val current = mutableServiceConnections.value[service]
            if (current.auth == ServiceAuthStatus.AUTH_REQUIRED) return
            servicesTracker.patch(service, version,
                if (service == SchoolService.CHX) mutableChxSessionVersion.value else null,
                null, current.problem ?: ServiceProblem.UNCONFIRMED, System.currentTimeMillis())
        }
    }

    /** 明确拒绝（认证页出现/业务 401）：标 AUTH_REQUIRED；供登录 Activity 调用，不抛。 */
    fun recordServiceAuthRequired(service: SchoolService, version: Long) {
        runCatching {
            ensureServicesBound()
            servicesTracker.patch(service, version,
                if (service == SchoolService.CHX) mutableChxSessionVersion.value else null,
                ServiceAuthStatus.AUTH_REQUIRED, null, System.currentTimeMillis())
        }
    }
    /** 验证身份接口，校历停服不应把可用登录误报为失效；短期复用结果避免重复唤醒。 */
    suspend fun refreshAccountLogin(force: Boolean = false, generation: Long? = null): AccountLoginState =
        accountCheckMutex.withLock {
            if (generation != null) refreshAccountLoginLocked(force, generation)
            else withServiceProbe(SchoolService.PORTAL, sessionGuard.current(), null) {
                refreshAccountLoginLocked(force, it)
            }
        }

    private suspend fun refreshAccountLoginLocked(force: Boolean, generation: Long): AccountLoginState {
        val version = sessionGuard.current()
        if (!servicesTracker.accepts(SchoolService.PORTAL, version, null, generation)) {
            throw CancellationException("门户验证状态已变更")
        }
        val session = portalSession()
        if (session == null) {
            ensureServicesBound()
            // 明确拒绝（EXPIRED/AUTH_REQUIRED）保持原状，不被无候选覆盖；其余回到无候选。
            if (mutableAccountLogin.value != AccountLoginState.EXPIRED &&
                mutableServiceConnections.value[SchoolService.PORTAL].auth != ServiceAuthStatus.AUTH_REQUIRED
            ) {
                applyServicePatch(SchoolService.PORTAL, sessionVersion.value, null, ServiceAuthStatus.NOT_CONNECTED,
                    null, System.currentTimeMillis(), generation)
            }
            if (mutableAccountLogin.value != AccountLoginState.EXPIRED) mutableAccountLogin.value = AccountLoginState.SIGNED_OUT
            return mutableAccountLogin.value
        }
        val now = android.os.SystemClock.elapsedRealtime()
        val retryAfter = if (mutableAccountLogin.value == AccountLoginState.VALID) 5 * 60_000L else 30_000L
        if (!force && accountCheckedAt > 0 && now - accountCheckedAt in 0 until retryAfter) {
            return mutableAccountLogin.value
        }
        ensureServicesBound()
        mutableAccountLogin.value = AccountLoginState.CHECKING
        try {
            val (account, name) = api.userInfo(session.token)
            sessionGuard.withCurrent(session.version) {
                if (portalSession() != session ||
                    !servicesTracker.accepts(SchoolService.PORTAL, session.version, null, generation)) {
                    throw CancellationException("登录状态已变更")
                }
                val state = accountLoginState(session.account, account, null)
                if (state == AccountLoginState.VALID && name.isNotBlank()) store.realName = name
                mutableAccountLogin.value = state
                if (state == AccountLoginState.VALID) {
                    applyServicePatch(SchoolService.PORTAL, session.version, null, ServiceAuthStatus.VERIFIED, null,
                        System.currentTimeMillis(), generation)
                } else {
                    // 账号不匹配等不确定情况：保持过去认证，只标记问题。
                    applyServicePatch(SchoolService.PORTAL, session.version, null, null, ServiceProblem.UNCONFIRMED,
                        System.currentTimeMillis(), generation)
                }
            }
        } catch (e: CancellationException) {
            if (isCurrentSession(session.version) && mutableAccountLogin.value == AccountLoginState.CHECKING &&
                servicesTracker.accepts(SchoolService.PORTAL, session.version, null, generation)) {
                mutableAccountLogin.value = AccountLoginState.UNVERIFIED
            }
            throw e
        } catch (e: Exception) {
            sessionGuard.withCurrent(session.version) {
                if (portalSession() != session ||
                    !servicesTracker.accepts(SchoolService.PORTAL, session.version, null, generation)) {
                    throw CancellationException("登录状态已变更")
                }
                if (e is AuthFailed) store.clearPortalSession()
                mutableAccountLogin.value = accountLoginState(session.account, null, e)
            }
            recordServiceFailure(SchoolService.PORTAL, session.version, null, e, generation)
        }
        accountCheckedAt = android.os.SystemClock.elapsedRealtime()
        return mutableAccountLogin.value
    }

    suspend fun isSessionAlive(): Boolean {
        val state = refreshAccountLogin()
        // 网络或格式错误保留离线会话，但状态卡不会因此显示绿色。
        return state != AccountLoginState.EXPIRED && store.hasSession
    }

    suspend fun fetchCaptcha() = api.captcha()

    /** 原生兼容登录仅把新票交给门户一次；网页 CAS 会话由学校统一登录流程建立。 */
    suspend fun login(account: String, password: String, code: String, uuid: String) = authenticationMutex.withLock {
        val version = beginLogin()
        val ticket = api.login(account, password, code, uuid)
        applySessions(account, ticket, version)
    }

    /** 重复 SSE 投递只保留摘要去重，不持久化一次性票据，也不把它再交给其他服务。 */
    private var lastSsoTicket: String? = null

    suspend fun finishSso(rawTicket: String) = authenticationMutex.withLock {
        val ticket = rawTicket.trim()
        require(ticket.isNotBlank()) { "登录票据为空，请重新扫码" }
        val fingerprint = java.security.MessageDigest.getInstance("SHA-256")
            .digest(ticket.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        if (fingerprint == lastSsoTicket) return@withLock
        val version = beginLogin()
        sessionGuard.withCurrent(version) { lastSsoTicket = fingerprint }
        applySessions("", ticket, version)
    }

    /** 一张票只兑换门户一次；网络结果不明时也不重试，教务/学习通由网页 SSO 各取自己的票。 */
    private suspend fun applySessions(fallbackJob: String, ticket: String, version: Long) {
        sessionGuard.withCurrent(version) { }
        val token = api.exchangeToken(ticket)
        val (job, name) = api.userInfo(token)
        if (token.isBlank() || job.isBlank() || job.equals("guest", ignoreCase = true)) {
            throw AuthFailed(401, "未能确认学校账号，请重新登录。")
        }
        if (fallbackJob.isNotBlank() && fallbackJob != job) {
            throw AuthFailed(401, "登录身份与输入账号不一致，请重新登录。")
        }
        currentCoroutineContext().ensureActive()
        val changedAccount = sessionGuard.withCurrent(version) {
            store.jobNumber.isNotBlank() && store.jobNumber != job
        }
        var acceptedVersion = version
        if (changedAccount) {
            // 新身份已由业务接口确认；清网页会话失败也不提前清本地资料。
            val cleared = clearWebLoginData()
            if (!cleared) {
                sessionGuard.withCurrent(version) { store.webLogoutPending = false }
                error("旧网页会话尚未清除，请稍后重新登录。")
            }
            currentCoroutineContext().ensureActive()
            sessionGuard.withCurrent(version) { acceptedVersion = resetAccountSession(preserveSnapshots = true) }
        }
        sessionGuard.withCurrent(acceptedVersion) {
            store.promoteTeachingSnapshotOwner(job)
            store.portalToken = token
            store.jobNumber = job
            if (name.isNotBlank() || changedAccount) store.realName = name
            store.casTicket = ""
            store.webLogoutPending = false
            store.automaticSyncNeedsLogin = false
            mutableAccountLogin.value = AccountLoginState.VALID
            accountCheckedAt = android.os.SystemClock.elapsedRealtime()
            if (!changedAccount) {
                val nextVersion = sessionGuard.invalidate()
                invalidateChxRequests()
                mutableSessionVersion.value = nextVersion
                acceptedVersion = nextVersion
            }
            applyServicePatch(SchoolService.PORTAL, acceptedVersion, null,
                ServiceAuthStatus.VERIFIED, null, System.currentTimeMillis())
        }
    }

    fun subscribeSse() = api.subscribeSse()
    fun mobieAuthUrl(clientId: String) = api.mobieAuthUrl(clientId)

    /** 同步门户实际返回的课程；单周响应不能推断其他周。 */
    suspend fun syncSchedule(): Pair<CalendarInfo, List<CourseBlock>> {
        val session = portalSession() ?: run {
            applyServicePatch(SchoolService.PORTAL, sessionVersion.value, null, ServiceAuthStatus.NOT_CONNECTED, null,
                System.currentTimeMillis())
            error("请先登录学校门户")
        }
        return withServiceProbe(SchoolService.PORTAL, session.version, null) { generation ->
        val raw = try {
            api.calendar(session.token, session.account)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            recordServiceFailure(SchoolService.PORTAL, session.version, null, e, generation)
            throw e
        }
        val cal = sessionGuard.withCurrent(session.version) { calendarForSync(raw) }
        // 已有真实学期课表时，门户快照不参与覆盖；更新应重新读取正方学期响应。
        val imported = sessionGuard.withCurrent(session.version) {
            if (store.schoolScheduleSource == "zf_term") requireNotNull(store.loadSchoolCalendar()) to store.loadSchoolBlocks()
            else null
        }
        if (imported != null) {
            applyServicePatch(SchoolService.PORTAL, session.version, null, ServiceAuthStatus.VERIFIED, null,
                System.currentTimeMillis(), generation)
            return@withServiceProbe imported
        }
        val blocks = try {
            mergeLessons(api.lessons(session.token, session.account))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            recordServiceFailure(SchoolService.PORTAL, session.version, null, e, generation)
            throw e
        }
        currentCoroutineContext().ensureActive()
        sessionGuard.withCurrent(session.version) {
            store.saveSchedule(cal, blocks, "portal_week")
        }
        applyServicePatch(SchoolService.PORTAL, session.version, null, ServiceAuthStatus.VERIFIED, null,
            System.currentTimeMillis(), generation)
        cal to blocks
        }
    }

    suspend fun notices(): List<Notice> = notices(portalSession() ?: error("请先登录学校门户"))

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
    /** 作业同步失败计数：按异常类型统计，不解析文案。 */
    private class ChxSyncFailureCounts(val authFailures: Int, val readFailures: Int)

    suspend fun chxSyncWorkTodos(): String {
        val version = sessionGuard.current()
        val chxVersion = chxSessionVersion.value
        return withServiceProbe(SchoolService.CHX, version, chxVersion) { generation ->
        try {
            val (summary, counts) = chxSyncWorkTodosUnchecked(version, chxVersion)
            // 正常返回也可能是部分失败：本轮拿到 401 证据 → 明确拒绝；纯读取失败 → 认证有效但标 READ。
            if (counts.authFailures > 0) {
                applyServicePatch(SchoolService.CHX, version, chxVersion, ServiceAuthStatus.AUTH_REQUIRED, null,
                    System.currentTimeMillis(), generation)
            } else {
                applyServicePatch(SchoolService.CHX, version, chxVersion, ServiceAuthStatus.VERIFIED,
                    if (counts.readFailures > 0) ServiceProblem.READ else null, System.currentTimeMillis(), generation)
            }
            summary
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            recordServiceFailure(SchoolService.CHX, version, chxVersion, e, generation)
            throw e
        }
        }
    }

    private suspend fun chxSyncWorkTodosUnchecked(version: Long, chxVersion: Long): Pair<String, ChxSyncFailureCounts> {
        var authFailures = 0
        var readFailures = 0
        fun checkSession() = sessionGuard.withCurrent(version) {
            if (store.webLogoutPending || clearingChxSession || chxSessionVersion.value != chxVersion) {
                throw CancellationException("学习通登录状态已变更")
            }
        }
        checkSession()
        val identity = currentChxIdentity(version, chxVersion)
        fun checkIdentity() = checkChxIdentity(identity)
        chxRequests.check()
        fun workApi(owner: Any, checkCurrent: () -> Unit) = ChxApi(SessionHttpClient(
            ChxApi::isAllowedUrl, checkCurrent, webCookieMutex = webCleanupMutex,
            chxRequests = chxRequests, requestOwner = owner,
            chxBeforeRecovery = { kotlinx.coroutines.delay(1_000L) }))
        val courses = chxRequests.read(version, ::checkSession,
            identity = identity, checkIdentity = ::checkIdentity) { owner, checkCurrent ->
            workApi(owner, checkCurrent).courses()
        }
        checkSession()
        if (courses.isEmpty()) return "已读取课程列表，当前没有课程；本地待办已保留" to ChxSyncFailureCounts(0, 0)
        var added = 0
        var updated = 0
        var pending = 0
        var scanned = 0
        var failed = 0
        val failureCounts = linkedMapOf<String, Int>()
        val updates = mutableMapOf<Long, Todo>()
        // 任务点完成不代表没有作业；每门课程独立读取，单课失败不取消全部结果。
        val semaphore = Semaphore(3)
        val results = coroutineScope {
            courses.map { course ->
                async {
                    semaphore.withPermit {
                        val result = try {
                            checkSession()
                            val tabs = chxRequests.read(version, ::checkSession,
                                identity = identity, checkIdentity = ::checkIdentity) { owner, checkCurrent ->
                                workApi(owner, checkCurrent).courseTabs(course)
                            }
                            val items = chxRequests.read(version, ::checkSession,
                                identity = identity, checkIdentity = ::checkIdentity) { owner, checkCurrent ->
                                workApi(owner, checkCurrent).workList(course, tabs)
                            }
                            val now = System.currentTimeMillis()
                            val detailed = items.map { item ->
                                val detail = if (item.status != "未交" || item.detailUrl.isBlank()) null else try {
                                    chxRequests.read<ChxWorkDetailParseResult?>(
                                        version, ::checkSession, validateRecovery = { it != null },
                                        identity = identity, checkIdentity = ::checkIdentity,
                                    ) { owner, checkCurrent ->
                                        parseChxWorkDetail(
                                            workApi(owner, checkCurrent).workDetailHtml(item.detailUrl), now)
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: AuthFailed) {
                                    throw e
                                } catch (e: ChxCourseReadException) {
                                    if (e.pausesRequests || e is com.menglimi.chaoxing.ChxCookieWriteException) throw e
                                    null
                                } catch (_: Exception) {
                                    null
                                }
                                item to (detail?.deadlineMillis ?: 0L)
                            }
                            Result.success(detailed)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                        course to result
                    }
                }
            }.awaitAll()
        }
        checkSession()
        // 任一课程/详情触发暂停时整轮不落盘，保留原有截止时间与完成态。
        results.firstNotNullOfOrNull { (_, result) ->
            result.exceptionOrNull()?.takeIf { failure ->
                failure is ChxCourseReadException &&
                    (failure.pausesRequests || failure is com.menglimi.chaoxing.ChxCookieWriteException)
            }
        }?.let { throw it }
        chxRequests.check()
        for ((course, result) in results) {
            val items = result.getOrNull()
            if (items == null) {
                failed++
                val failure = result.exceptionOrNull()
                // 认证证据与读取失败分开计数，供连接状态结构化记录（不解析文案）。
                if (failure is AuthFailed) authFailures++ else readFailures++
                val reason = if (failure is AuthFailed || failure is ChxCourseReadException) failure.message ?: "请求未完成" else "请求未完成"
                failureCounts[reason] = (failureCounts[reason] ?: 0) + 1
                continue
            }
            scanned++
            for ((item, absoluteDue) in items) {
                if (item.workId <= 0) continue
                val isPending = item.status == "未交"
                if (isPending) pending++
                // 详情页给了绝对截止时间就用它；否则回退列表页的相对剩余时间；
                // 都解析不出就记 0=无截止信息（不能写成 now，否则刚同步完就显示「今天已过期」）
                val remaining = ChxWorkParser.remainingMillis(item.remainingText)
                val due = when {
                    !isPending -> 0L
                    absoluteDue > 0L -> absoluteDue
                    remaining > 0L -> System.currentTimeMillis() + remaining
                    else -> 0L
                }
                updates[item.workId] = Todo(
                    id = item.workId,
                    text = item.title,
                    done = !isPending,
                    kind = TodoKind.HOMEWORK,
                    course = course.name,
                    dueAt = due,
                    source = "chaoxing",
                )
            }
        }
        val failures = ChxReadDiagnostics.failures(failureCounts)
        if (scanned == 0) {
            val message = "$failed 门课程未能读取，本地待办已保留。\n$failures"
            if (authFailures > 0) throw AuthFailed(401, message)
            throw ChxCourseReadException(message)
        }
        if (updates.isEmpty()) {
            return sessionGuard.withCurrent(version) {
                checkSession()
                chxRequests.withIdentity(identity, ::checkIdentity) {
                    val summary = if (failed == 0) "已检查 $scanned 门课程，未发现作业；本地待办已保留"
                        else "部分完成：已检查 $scanned 门课程${failSuffix(failed, failures)}；本地待办已保留"
                    summary to ChxSyncFailureCounts(authFailures, readFailures)
                }
            }
        }
        var merged = 0
        sessionGuard.withCurrent(version) {
            checkSession()
            chxRequests.withIdentity(identity, ::checkIdentity) {
                store.acceptChxSnapshotOwner(requireNotNull(identity.uid))
                store.updateTodos { current ->
            val byId = current.associateBy { it.id }.toMutableMap()
            // 通知监听写入的条目与 web 清单是两套 id（时间戳 vs workId），但文本/课程相同；
            // 同步时按归一化文本合并，避免同一条作业出现两次。
            fun norm(s: String) = s.filter { it.isLetterOrDigit() }.lowercase()
            fun sameTask(a: Todo, b: Todo): Boolean {
                if (a.id == b.id) return false
                if (a.source != "chaoxing" || b.source != "chaoxing") return false
                if (a.course.isNotBlank() && b.course.isNotBlank() && a.course != b.course) return false
                val x = norm(a.text)
                val y = norm(b.text)
                return x.isNotEmpty() && y.isNotEmpty() && (x.contains(y) || y.contains(x))
            }
            // 先合并不在本轮清单里的通知条目（id 不在 updates 中）
            val others = current.filter { it.id !in updates.keys }
            val consumed = mutableSetOf<Long>()
            for ((id, todo) in updates) {
                val existing = byId[id]
                if (existing == null) {
                    val dup = others.firstOrNull { it.id !in consumed && sameTask(it, todo) }
                    if (dup != null) {
                        consumed += dup.id
                        byId.remove(dup.id)
                        byId[id] = todo.copy(done = todo.done || dup.done, dueAt = todo.dueAt.takeIf { it > 0 } ?: dup.dueAt)
                        merged++
                    } else {
                        added++
                        byId[id] = todo
                    }
                } else {
                    updated++
                    // 完成态只置 true 不回退（手动勾选的保持勾选）；
                    // 手动改过类型的保留手动分类，否则跟随同步结果
                    byId[id] = existing.copy(
                        text = todo.text,
                        done = existing.done || todo.done,
                        kind = if (existing.source == "manual") existing.kind else todo.kind,
                        course = todo.course,
                        dueAt = todo.dueAt,
                    )
                }
            }
            // 兜底：清单里已存在的作业，若还有重复的通知条目，也一并合并
            val webTodos = byId.values.filter { it.id in updates.keys }
            for (n in others) {
                if (n.id in consumed) continue
                val target = webTodos.firstOrNull { sameTask(it, n) }
                if (target != null) {
                    val t = byId[target.id] ?: continue
                    byId[target.id] = t.copy(
                        done = t.done || n.done,
                        dueAt = t.dueAt.takeIf { it > 0 } ?: n.dueAt,
                    )
                    byId.remove(n.id)
                    merged++
                }
            }
            byId.values.toList()
        }
        }
        }
        return (if (failed > 0) "部分完成" else "同步完成") + "：检查了 $scanned 门课${failSuffix(failed, failures)}，" +
            "新增 $added 条、更新 $updated 条" +
            (if (merged > 0) "、合并 $merged 条通知重复" else "") +
            "，未交 $pending 条" to ChxSyncFailureCounts(authFailures, readFailures)
    }

    private fun failSuffix(failed: Int, details: String): String =
        if (failed == 0) "" else "（$failed 门读取失败：\n$details）"

    /** 网页明确“完成检查”才允许重查原受阻业务；返回、探活和刷新均不会解除暂停。 */
    internal suspend fun checkChxRecoveryAfterWebVerification(version: Long, chxVersion: Long) =
        withContext(Dispatchers.Main.immediate) {
            val recoveryJob = currentCoroutineContext()[Job]
            fun checkSession() {
                recoveryJob?.ensureActive()
                sessionGuard.withCurrent(version) {
                    if (store.webLogoutPending || clearingChxSession || chxSessionVersion.value != chxVersion) {
                        throw CancellationException("学习通登录状态已变更")
                    }
                }
            }
            chxSessionMutex.withLock {
                checkSession()
                val identity = currentChxIdentity(version, chxVersion)
                chxRequests.recover(version, ::checkSession, afterWebVerification = true,
                    identity = identity, checkIdentity = { checkChxIdentity(identity) })
                checkSession()
            }
        }

    /** 学习通会话探活与课程读取共用实时网页 Cookie；存储快照不是独立的登录凭证。 */
    suspend fun chxSessionAlive(generation: Long? = null): Boolean? = withContext(Dispatchers.Main.immediate) {
        if (generation == null) return@withContext withServiceProbe(
            SchoolService.CHX, sessionGuard.current(), chxSessionVersion.value,
        ) { chxSessionAlive(it) }
        chxSessionMutex.withLock {
            val probeJob = currentCoroutineContext()[Job]
            val version = sessionGuard.current()
            val chxVersion = chxSessionVersion.value
            fun checkSession() {
                probeJob?.ensureActive()
                sessionGuard.withCurrent(version) {
                    if (store.webLogoutPending || clearingChxSession || chxSessionVersion.value != chxVersion ||
                        !servicesTracker.accepts(SchoolService.CHX, version, chxVersion, generation)) {
                        throw CancellationException("学习通登录状态已变更")
                    }
                }
            }
            checkSession()
            if (ChxWebSession.candidateCookie() == null) {
                store.clearChxCookie()
                val auth = if (servicesTracker.snapshot().chx.auth == ServiceAuthStatus.AUTH_REQUIRED)
                    ServiceAuthStatus.AUTH_REQUIRED else ServiceAuthStatus.NOT_CONNECTED
                applyServicePatch(SchoolService.CHX, version, chxVersion, auth, null,
                    System.currentTimeMillis(), generation)
                return@withLock false
            }
            val identity = currentChxIdentity(version, chxVersion)
            chxRequests.check()
            val home = ChxSso.HOME.toHttpUrl()
            var failureRecorded = false
            val result = ChxWebSession.verifyCandidate(
                readCookie = ChxWebSession::candidateCookie,
                probe = {
                    try {
                        chxRequests.read(version, ::checkSession, validateRecovery = { it == true },
                            identity = identity, checkIdentity = { checkChxIdentity(identity) }) { owner, checkCurrent ->
                            val http = SessionHttpClient(allowedUrl = { it == home }, checkSession = checkCurrent,
                                webCookieMutex = webCleanupMutex, chxRequests = chxRequests, requestOwner = owner,
                                chxBeforeRecovery = { kotlinx.coroutines.delay(1_000L) })
                            val response = http.execute(okhttp3.Request.Builder().url(home)
                                .header("User-Agent", ChxCourseApi.USER_AGENT).get().build())
                            ChxApi.sessionEvidence(response.status, ChxSso.HOME, response.body, response.location)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: ChxCourseReadException) {
                        recordServiceFailure(SchoolService.CHX, version, chxVersion, failure, generation)
                        throw failure
                    } catch (e: Exception) {
                        // 保留网络/读取证据，随后不覆盖为“不确定”。
                        recordServiceFailure(SchoolService.CHX, version, chxVersion, e, generation)
                        failureRecorded = true
                        null
                    }
                },
                checkCurrent = ::checkSession,
                saveCandidate = { saveChxCandidate(it, version, chxVersion) },
                clearCandidate = { store.clearChxCookie() },
            )
            when (result) {
                true -> applyServicePatch(SchoolService.CHX, version, chxVersion, ServiceAuthStatus.VERIFIED, null,
                    System.currentTimeMillis(), generation)
                false -> applyServicePatch(SchoolService.CHX, version, chxVersion, ServiceAuthStatus.AUTH_REQUIRED,
                    null, System.currentTimeMillis(), generation)
                null -> if (!failureRecorded) {
                    applyServicePatch(SchoolService.CHX, version, chxVersion, null, ServiceProblem.UNCONFIRMED,
                        System.currentTimeMillis(), generation)
                }
            }
            result
        }
    }

    /** 只清理学习通和 CAS 的已知网页存储范围，返回本机清理结果。 */
    suspend fun clearChxSession(): Boolean = withContext(NonCancellable + Dispatchers.Main.immediate) {
        clearingChxSession = true
        invalidateChxRequests()
        chxRequests.invalidateIdentity()
        store.clearChxCookie()
        val cleared = try {
            webCleanupMutex.withLock { ChxWebSession.clear() }
        } finally {
            clearingChxSession = false
            invalidateChxRequests()
        }
        applyServicePatch(SchoolService.CHX, sessionVersion.value, mutableChxSessionVersion.value,
            ServiceAuthStatus.NOT_CONNECTED, null, System.currentTimeMillis())
        cleared
    }

    suspend fun summarize(): String {
        val session = portalSession() ?: error("请先登录学校门户")
        return summarize(notices(session), session)
    }

    suspend fun chat(messages: List<AiChatMessage>): String {
        val version = sessionGuard.current()
        val result = api.complete(store.llmBase, store.llmKey, store.llmModel, messages)
        currentCoroutineContext().ensureActive()
        sessionGuard.withCurrent(version) { }
        return result
    }

    internal suspend fun summarize(notices: List<Notice>, session: PortalSession?): String {
        val version = sessionGuard.current()
        fun checkSession() = sessionGuard.withCurrent(version) {
            if (session != portalSession()) throw CancellationException("登录状态已变更")
        }
        checkSession()
        val result = api.summarize(store.llmBase, store.llmKey, store.llmModel, notices)
        currentCoroutineContext().ensureActive()
        checkSession()
        sessionGuard.withCurrent(version) {
            check(store.saveNoticeSummary(notices, result)) { "摘要已生成，但未能保存到本机。" }
        }
        return result
    }

    internal suspend fun notices(session: PortalSession, source: NoticeSource = NoticeSource.PORTAL): List<Notice> {
        if (session != portalSession()) throw CancellationException("登录状态已变更")
        val result = if (source == NoticeSource.PORTAL) api.notices(session.token, session.account)
            else api.news(session.token, source)
        if (session != portalSession()) throw CancellationException("登录状态已变更")
        return result
    }

    internal fun loadGradeSnapshot(year: String, semester: String): DataSnapshot<GradeReport>? =
        store.loadGradeSnapshot(year, semester)

    internal suspend fun loadChxCourseSnapshot(): ChxCourseOverview? = withContext(Dispatchers.Main.immediate) {
        if (store.webLogoutPending || clearingChxSession) return@withContext null
        val version = sessionGuard.current()
        val uid = webCleanupMutex.withLock {
            sessionGuard.withCurrent(version) {
                val live = android.webkit.CookieManager.getInstance().getCookie(ChxSso.HOME)
                DataSnapshots.courseSnapshotOwner(live, store.chxCookie, store.chxCourseSnapshotOwner)
            }
        } ?: return@withContext null
        sessionGuard.withCurrent(version) { store.loadChxCourseSnapshot(uid)?.data }
    }

    internal fun loadNoticeSnapshot(source: NoticeSource): DataSnapshot<List<Notice>>? =
        store.loadNoticeSnapshot(source)

    internal fun saveNoticeSnapshot(source: NoticeSource, snapshot: DataSnapshot<List<Notice>>) {
        check(store.saveNoticeSnapshot(source, snapshot)) { "通知已读取，但未能保存到本机。" }
    }

    /** 成绩只读取已建立的教务会话；不重放历史票据，失败可继续尝试同站点的网页会话。 */
    suspend fun grades(year: String, semester: String): DataSnapshot<GradeReport> = withContext(Dispatchers.Main.immediate) {
        val version = sessionGuard.current()
        withServiceProbe(SchoolService.TEACHING, version, null) { generation ->
        fun checkSession() = sessionGuard.withCurrent(version) {
            if (store.webLogoutPending) throw CancellationException("网页会话正在清理")
        }
        checkSession()
        val savedBase = TeachingEndpoints.baseForPage(store.loadWebImportPage())
        val bases = (listOfNotNull(savedBase) + TeachingEndpoints.bases).distinct()
        val jar = teachingSessionJar(version)
        var failure: Exception? = null
        for (base in bases) {
            val probe = "$base/cjcx/cjcx_cxDgXscj.html".toHttpUrl()
            val hasWeb = webCleanupMutex.withLock {
                checkSession()
                !android.webkit.CookieManager.getInstance().getCookie(probe.toString()).isNullOrBlank()
            }
            val hasDirect = jar.loadForRequest(probe).isNotEmpty()
            // 用户最近从网页登录时优先复用它，避免旧原生 Cookie 遮蔽新网页会话。
            for (web in if (base == savedBase) listOf(true, false) else listOf(false, true)) {
                if ((web && !hasWeb) || (!web && !hasDirect)) continue
                try {
                    val http = SessionHttpClient(
                        allowedUrl = { TeachingEndpoints.allowsGradeRequest(base, it) },
                        checkSession = ::checkSession,
                        cookieJar = if (web) null else jar,
                        webCookieMutex = webCleanupMutex,
                    )
                    val report = TeachingGradeApi(http, base).load(year, semester)
                    currentCoroutineContext().ensureActive()
                    checkSession()
                    val snapshot = DataSnapshot(report, System.currentTimeMillis())
                    withContext(Dispatchers.IO) {
                        currentCoroutineContext().ensureActive()
                        sessionGuard.withCurrent(version) {
                            checkSession()
                            check(store.saveGradeSnapshot(year, semester, snapshot)) { "成绩已读取，但未能保存到本机。" }
                        }
                    }
                    applyServicePatch(SchoolService.TEACHING, version, null, ServiceAuthStatus.VERIFIED, null,
                        System.currentTimeMillis(), generation)
                    return@withServiceProbe snapshot
                } catch (e: CancellationException) {
                    throw e
                } catch (e: GradeLoadException) {
                    // 已读到业务响应，分页/格式异常应明确报告，不换站点悄悄掩盖。
                    applyServicePatch(SchoolService.TEACHING, version, null, null, ServiceProblem.READ,
                        System.currentTimeMillis(), generation)
                    throw e
                } catch (e: Exception) {
                    if (failure == null || e !is AuthFailed) failure = e
                }
            }
        }
        checkSession()
        val finalFailure = failure ?: AuthFailed(401, "请先完成教务网页登录")
        if (failure == null) {
            applyServicePatch(SchoolService.TEACHING, version, null, ServiceAuthStatus.NOT_CONNECTED, null,
                System.currentTimeMillis(), generation)
        } else {
            recordServiceFailure(SchoolService.TEACHING, version, null, finalFailure, generation)
        }
        throw finalFailure
        }
    }

    /** 学习通课程与任务点只用应用内网页会话；快照变化后旧请求不得回填。 */
    suspend fun chxCourses(): ChxCourseOverview = withContext(Dispatchers.Main.immediate) {
        val version = sessionGuard.current()
        val chxVersion = chxSessionVersion.value
        withServiceProbe(SchoolService.CHX, version, chxVersion) { generation ->
        fun checkSession() = sessionGuard.withCurrent(version) {
            if (store.webLogoutPending || clearingChxSession || chxSessionVersion.value != chxVersion) {
                throw CancellationException("学习通登录状态已变更")
            }
        }
        val uid = webCleanupMutex.withLock {
            checkSession()
            DataSnapshots.cookieOwner(android.webkit.CookieManager.getInstance().getCookie(ChxSso.HOME))
                ?: run {
                    val auth = if (servicesTracker.snapshot().chx.auth == ServiceAuthStatus.AUTH_REQUIRED)
                        ServiceAuthStatus.AUTH_REQUIRED else ServiceAuthStatus.NOT_CONNECTED
                    applyServicePatch(SchoolService.CHX, version, chxVersion, auth, null,
                        System.currentTimeMillis(), generation)
                    throw AuthFailed(401, "请先登录学习通")
                }
        }
        val identity = chxRequests.bindIdentity(uid)
        chxRequests.check()
        val current = try {
            chxRequests.read(version, ::checkSession,
                validateRecovery = { it.progressError == null && !it.progressAuthRequired },
                identity = identity, checkIdentity = { checkChxIdentity(identity) }) { owner, checkCurrent ->
                val http = SessionHttpClient(ChxCourseApi::isAllowedUrl, checkCurrent,
                    webCookieMutex = webCleanupMutex, chxRequests = chxRequests, requestOwner = owner,
                    chxBeforeRecovery = { kotlinx.coroutines.delay(1_000L) })
                ChxCourseApi(http).load()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            recordServiceFailure(SchoolService.CHX, version, chxVersion, e, generation)
            throw e
        }
        currentCoroutineContext().ensureActive()
        webCleanupMutex.withLock {
            checkSession()
            val live = DataSnapshots.cookieOwner(android.webkit.CookieManager.getInstance().getCookie(ChxSso.HOME))
            if (live != null && live != uid) throw CancellationException("学习通登录状态已变更")
        }
        val overview = withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            sessionGuard.withCurrent(version) {
                checkSession()
                chxRequests.withIdentity(identity, { checkChxIdentity(identity) }) {
                    val merged = DataSnapshots.mergeCourseProgress(store.loadChxCourseSnapshot(uid)?.data, current)
                    store.acceptChxSnapshotOwner(uid)
                    check(store.saveChxCourseSnapshot(uid, merged)) { "课程已读取，但未能保存到本机。" }
                    merged
                }
            }
        }
        checkSession()
        if (overview.progressAuthRequired) {
            applyServicePatch(SchoolService.CHX, version, chxVersion, ServiceAuthStatus.AUTH_REQUIRED, null,
                System.currentTimeMillis(), generation)
        } else {
            // 课程列表读取成功即认证有效；进度/任务点的业务失败保留认证并标 READ。
            applyServicePatch(SchoolService.CHX, version, chxVersion, ServiceAuthStatus.VERIFIED,
                if (overview.progressError.isNullOrBlank()) null else ServiceProblem.READ,
                System.currentTimeMillis(), generation)
        }
        overview
        }
    }

    // 水电（无需学校登录态）：宿舍列表成功落盘；详情复用前台/Worker 共用的带绑定校验缓存。
    internal fun loadPowerRoomsSnapshot(account: String, code: Int): DataSnapshot<List<PowerRoom>>? =
        store.loadPowerRooms(account, code)

    suspend fun powerRooms(account: String, code: Int): List<PowerRoom> {
        val version = sessionGuard.current()
        val rooms = api.powerRooms(account, code)
        currentCoroutineContext().ensureActive()
        sessionGuard.withCurrent(version) {
            check(store.savePowerRooms(account, code, rooms)) { "宿舍列表已读取，但未能保存到本机。" }
        }
        return rooms
    }

    suspend fun powerOdd(): Double? = powerDetail().odd

    suspend fun powerDetail(): PowerDetail =
        com.menglimi.gdust.v2.work.PowerWorker.query(app, force = true)?.detail ?: error("请先绑定宿舍")

    fun savePowerBinding(account: String, code: Int, room: PowerRoom) {
        store.powerAccount = account
        store.powerCode = code
        store.powerRoomVerify = room.verify
        store.powerRoomName = room.name
        store.publishPowerWorkerConfig()
    }
}
