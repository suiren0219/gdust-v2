package com.haooz.chedule.data.gdust

import android.content.Context

/**
 * 广科门户会话存储。
 *
 * 设计取舍：只持久化门户 TOKEN + 学号（短期会话凭据）；CAS 账号密码**不落盘**——
 * TOKEN 失效后重登必须人工过验证码，存密码没有自动化收益，明文落盘徒增泄露面。
 */
class GdustSessionStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("gdust_session", Context.MODE_PRIVATE)

    var portalToken: String
        get() = prefs.getString("portal_token", null).orEmpty()
        set(value) = prefs.edit().putString("portal_token", value).apply()

    var jobNumber: String
        get() = prefs.getString("job_number", null).orEmpty()
        set(value) = prefs.edit().putString("job_number", value).apply()

    var realName: String
        get() = prefs.getString("real_name", null).orEmpty()
        set(value) = prefs.edit().putString("real_name", value).apply()

    // 水电绑定（三静态值：账号/学校代码/房间凭证）
    var powerAccount: String
        get() = prefs.getString("power_account", null).orEmpty()
        set(value) = prefs.edit().putString("power_account", value).apply()

    var powerCustomerCode: Int
        get() = prefs.getInt("power_customer_code", 0)
        set(value) = prefs.edit().putInt("power_customer_code", value).apply()

    var powerRoomVerify: String
        get() = prefs.getString("power_room_verify", null).orEmpty()
        set(value) = prefs.edit().putString("power_room_verify", value).apply()

    var powerRoomName: String
        get() = prefs.getString("power_room_name", null).orEmpty()
        set(value) = prefs.edit().putString("power_room_name", value).apply()

    // LLM 总结（OpenAI 兼容中转站），全部用户自填，无内置默认
    var llmBase: String
        get() = prefs.getString("llm_base", null).orEmpty()
        set(value) = prefs.edit().putString("llm_base", value).apply()

    var llmKey: String
        get() = prefs.getString("llm_key", null).orEmpty()
        set(value) = prefs.edit().putString("llm_key", value).apply()

    var llmModel: String
        get() = prefs.getString("llm_model", null).orEmpty()
        set(value) = prefs.edit().putString("llm_model", value).apply()

    val hasSession: Boolean get() = portalToken.isNotEmpty() && jobNumber.isNotEmpty()
    val hasPowerBinding: Boolean get() = powerAccount.isNotEmpty() && powerRoomVerify.isNotEmpty()

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}

/**
 * 广科统一门面：登录链（CAS→门户 SSO）、课表/校历/通知拉取、水电、LLM 总结。
 * androidMain 专属（OkHttp + SharedPreferences）。
 */
class GdustService(context: Context) {

    val store = GdustSessionStore(context)
    private val cas = CasClient()
    private val portal = PortalClient()
    private val power = PowerClient()

    /** CAS 账号密码登录 → 门户 SSO → 会话落盘。 */
    suspend fun loginWithPassword(account: String, password: String, captcha: String, uuid: String): GdustSession {
        val ticket = cas.loginByAccount(account, password, captcha, uuid)
        val token = portal.exchangeToken(ticket)
        val info = runCatching { portal.userInfo(token) }.getOrNull()
        store.portalToken = token
        store.jobNumber = info?.jobNum?.takeIf { it.isNotEmpty() } ?: account
        store.realName = info?.realName.orEmpty()
        return GdustSession(token, store.jobNumber, store.realName)
    }

    /** 扫码登录拿到 ticket 后的收尾。 */
    suspend fun finishSso(ticket: String): GdustSession {
        val token = portal.exchangeToken(ticket)
        val info = runCatching { portal.userInfo(token) }.getOrNull()
        store.portalToken = token
        store.jobNumber = info?.jobNum?.takeIf { it.isNotEmpty() } ?: store.jobNumber
        store.realName = info?.realName.orEmpty()
        return GdustSession(token, store.jobNumber, store.realName)
    }

    suspend fun fetchCaptcha(): CasClient.Captcha = cas.fetchCaptcha()

    /** 钉钉扫码事件流。 */
    fun subscribeSse(): kotlinx.coroutines.flow.Flow<GdustSseEvent> = cas.subscribeSse()

    /** 扫码二维码内容（钉钉打开的授权页）。 */
    fun mobieAuthUrl(clientId: String): String = cas.mobieAuthUrl(clientId)

    val isCaptchaError: (Throwable) -> Boolean = { it is CasException && it.code == 30001 }

    suspend fun probeSession(): Boolean = store.hasSession && runCatching {
        portal.calendar(store.portalToken, store.jobNumber)
    }.isSuccess

    suspend fun calendar(): GdustCalendar = portal.calendar(store.portalToken, store.jobNumber)

    suspend fun lessons(): List<GdustLesson> = portal.courses(store.portalToken, store.jobNumber)

    suspend fun announcements(): List<GdustAnnouncement> = portal.announcements(store.portalToken, store.jobNumber)

    suspend fun powerRooms(account: String, customerCode: Int): List<GdustPowerRoom> =
        power.bindRooms(account, customerCode)

    suspend fun powerReading(): GdustPowerReading =
        power.roomPower(store.powerAccount, store.powerRoomVerify, store.powerCustomerCode)

    fun savePowerBinding(account: String, customerCode: Int, roomVerify: String, roomName: String) {
        store.powerAccount = account
        store.powerCustomerCode = customerCode
        store.powerRoomVerify = roomVerify
        store.powerRoomName = roomName
    }

    class LlmNotConfigured : Exception("请先在下方填写中转站 API（Base / Key / 模型名）")

    /**
     * 总结学校通知公告：拉最近公告 → 用户自填的中转站 LLM 提炼要点。
     * 配置全用户自填（无内置默认），公告标题文本只发往用户自己的中转站。
     */
    suspend fun summarizeAnnouncements(): String {
        val base = store.llmBase
        val key = store.llmKey
        val model = store.llmModel
        if (base.isEmpty() || key.isEmpty() || model.isEmpty()) throw LlmNotConfigured()
        val list = announcements()
        if (list.isEmpty()) return "最近没有公告可总结。"
        val llm = GdustLlm(base, key, model)
        val items = list.take(15).joinToString("\n") {
            "- [${it.announcementType}] ${it.title}（${it.issuer ?: "未知发布"}）"
        }
        return llm.chat(
            system = "你是广东科技学院的学生助手。请把学校通知公告的标题列表整理成简明的中文要点：" +
                "按类别分组（学校通知/保卫处反诈提醒等），每条一句话，" +
                "突出与学生直接相关的事项（停课、考试、缴费、活动截止等）。控制在 250 字以内，用短行列表。",
            user = "以下是最近的学校通知公告标题：\n$items",
        )
    }

    fun logout() = store.clearAll()
}
