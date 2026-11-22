package com.menglimi.gdust.v2.data

import java.net.URI
import java.net.URLEncoder
import java.util.Locale

/**
 * 学习通单点入口。
 *
 * 来源（不是猜的）：
 * - 门户前端 `portal.gdust.edu.cn/index.js` 的应用磁贴：
 *   `cas.gdust.edu.cn/lyuapServer/login?service=http://gdkjxy.fysso.chaoxing.com/sso/gdkjxy`（超星学习通 / 广科智课）
 *   `gdkj.mh.chaoxing.com/sso/login/3rd?wfwfid=420&refer=…`（新版超星课程中心，isSinglePoint）
 * - 实测：未登录时 `http://gdkjxy.fysso.chaoxing.com/sso/gdkjxy` 302 回 `cas.gdust.edu.cn/lyuapServer/login`，
 *   登录页标题「广科统一身份认证」——学习通与教务共用同一套 CAS。
 *
 * 联动只复用应用内 CAS 网页会话（由 CookieManager 按域名/路径发送）：
 * 原生 loginByAccount / 钉钉 ticket、门户 TOKEN 和教务 JSESSIONID 都不是 CAS 网页会话，
 * 不能据此生成 TGC、复制 Cookie 到超星或重放 ticket。没有网页会话时由学校页面完成认证。
 * 即使有 CAS Cookie，也必须以超星自己的响应验证联动结果。
 */
object ChxSso {
    private const val CAS_LOGIN = "https://cas.gdust.edu.cn/lyuapServer/login?service="

    /** 泛雅 / 广科智课（机构 fid=420）。该加速域没开 HTTPS，实测 443 无响应，必须走 http。 */
    private const val FYSSO_SERVICE = "http://gdkjxy.fysso.chaoxing.com/sso/gdkjxy"

    /** 2026-11-15 只读核对门户 index.js：service 解码一次后的 refer 是原始 URL，不能再预编码。 */
    private const val MH_SERVICE =
        "https://gdkj.mh.chaoxing.com/sso/login/3rd?wfwfid=420&refer=https://gdkj.mh.chaoxing.com"

    /** 教务（正方）网页入口，同样走 CAS，验证「一次登录双通」用。 */
    private const val TEACHING_SERVICE = "http://gkjx.gdust.edu.cn/sso/lyiotlogin"
    // 门户“教学综合服务平台”公布的校园网入口；公网失败时由 CAS 重新签票。
    private const val CAMPUS_TEACHING_SERVICE = "http://172.16.254.1/sso/lyiotlogin"

    /** CAS 登出入口，用于显式退出共享的学校网页登录会话。 */
    const val LOGOUT_URL = "https://cas.gdust.edu.cn/cas/logout"

    private fun sso(service: String) = CAS_LOGIN + URLEncoder.encode(service, "UTF-8")

    val fyssoUrl: String get() = sso(FYSSO_SERVICE)
    val mhUrl: String get() = sso(MH_SERVICE)
    val teachingWebUrl: String get() = sso(TEACHING_SERVICE)
    val campusTeachingWebUrl: String get() = sso(CAMPUS_TEACHING_SERVICE)
    // 沿用教务学期导入的校外 WebVPN 入口，由网页完成认证并进入教务。
    const val TEACHING_VPN_LOGIN = "https://webvpn.gdust.edu.cn/https/77726476706e69737468656265737421f3f652d220347d436a468ca88d1b203b/cas/?service=https%3A%2F%2Fwebvpn.gdust.edu.cn%2Flogin%3Fcas_login%3Dtrue"

    /** 只检测 CAS 网页会话候选，不能把匿名 JSESSIONID 或原生登录 ticket 当成已认证。 */
    val casCookieProbes = listOf("https://cas.gdust.edu.cn/lyuapServer/login", "https://cas.gdust.edu.cn/cas/login")

    fun linkingHint(hasCasWebSession: Boolean): String = if (hasCasWebSession) {
        "检测到学校网页会话，可尝试学校单点登录；是否联动成功仍需验证学习通会话。"
    } else {
        "本机尚未检测到学校网页认证会话。教务或门户登录不代表已登录学习通；" +
            "请打开学校单点登录完成认证，或使用学习通账号网页登录。"
    }

    /** 学习通首页（HAR 实测存在的入口，用于探活与回到主页）。 */
    const val HOME = "https://mooc2-ans.chaoxing.com/mooc2-ans/visit/interaction"

    /**
     * 超星 passport 直登页（fid=420 来自学校 SSO 链路实测）。
     * 学校 CAS 不可达时的备用入口：用学习通账号（手机号/密码）直接登录，
     * 拿到的是同一套 `.chaoxing.com` 会话，作业同步照常可用。
     */
    const val DIRECT_LOGIN = "https://passport2.chaoxing.com/login?fid=420&refer=https%3A%2F%2Fi.chaoxing.com"

    // Cookie 必须来自实际探活 URL，不能把 i.chaoxing.com 的 host-only Cookie 发给另一个主机。
    val cookieProbes = listOf(HOME)
    private val identityCookieNames = setOf("_uid", "UID")
    internal val casTicketNames = setOf("TGC", "CASTGC", "CAS_TGC")
    private val cookieName = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")

    /** 解析 Cookie 请求头；保留同名条目，名称区分大小写，值允许包含 '='。 */
    internal fun cookiePairs(header: String?): List<Pair<String, String>> =
        header.orEmpty().split(';').mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator < 1) return@mapNotNull null
            val name = part.substring(0, separator).trim()
            val value = part.substring(separator + 1).trim().removeSurrounding("\"").trim()
            if (!cookieName.matches(name) || value.any { it.code < 32 || it.code == 127 }) null
            else name to value
        }

    /** 仅代表保存了可供验证的身份 Cookie；匿名 JSESSIONID 不是登录证据。 */
    fun hasSessionCookie(header: String?): Boolean = cookiePairs(header).any { (name, value) ->
        name in identityCookieNames && value.isNotBlank()
    }

    internal fun hasCasTicket(header: String?): Boolean = cookiePairs(header).any { (name, value) ->
        name in casTicketNames && value.isNotBlank()
    }

    fun isChaoxingHost(host: String?): Boolean {
        val h = host.orEmpty().lowercase(Locale.ROOT)
        // 必须按域名边界匹配，避免 notchaoxing.com 这类伪装域被当成超星。
        return h == "chaoxing.com" || h.endsWith(".chaoxing.com") ||
            h == "chaoxing.cn" || h.endsWith(".chaoxing.cn")
    }

    /** 只认登录主机/路径，不拿 query 里的 login 或 passport 字样判失效。 */
    internal fun isLoginUrl(url: String): Boolean {
        val uri = parseUrl(url) ?: return false
        if (uri.scheme != "https" || uri.userInfo != null || uri.port !in setOf(-1, 443)) return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        val path = uri.path.orEmpty().trimEnd('/').lowercase(Locale.ROOT)
        // 2026-11-15 未登录入口实际重定向到 /cas/（标题「广科统一身份认证」）。
        if (host == "cas.gdust.edu.cn") return path in setOf("/cas", "/cas/login", "/lyuapserver/login")
        if (!isChaoxingHost(host)) return false
        val passport = host.substringBefore('.') in setOf("passport", "passport2")
        return path in setOf("/login", "/login.html", "/login/index", "/fanyalogin") ||
            (passport && (path.isEmpty() || path == "/mlogin"))
    }

    /**
     * 教务网页入口白名单：学习通联动里登录过教务网页后，落地页可直接作为
     * 「网页登录导入」的持久入口。教务公网入口实测只有 HTTP（443 无响应），
     * 内网与加速域同理；fetchWithCookieHeader 会按同域发 Cookie。
     * 按 host 精确判断，query 里伪装的 gdust.edu.cn 不能绕过。
     */
    fun isTeachingWebEntry(url: String): Boolean {
        val uri = parseUrl(url) ?: return false
        if (uri.userInfo != null) return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return (uri.scheme == "http" && (host == "172.16.254.1" || host == "gkjx.gdust.edu.cn")) ||
            (uri.scheme == "https" && (host == "gdust.edu.cn" || host.endsWith(".gdust.edu.cn")))
    }

    /** URL 仅限定可信页面范围，不能单独证明学校已经退出。 */
    internal fun isSchoolLogoutUrl(url: String): Boolean {
        val uri = parseUrl(url) ?: return false
        return uri.scheme == "https" && uri.host.equals("cas.gdust.edu.cn", ignoreCase = true) &&
            (uri.port == -1 || uri.port == 443) && uri.userInfo == null &&
            uri.path.trimEnd('/') in setOf("/cas/logout", "/lyuapServer/logout")
    }

    /** 只接受可信学校退出页的明确完成文案；HTTP/TLS/网络失败或手动返回都不得确认。 */
    internal fun confirmsSchoolLogout(url: String, pageText: String?, loadSucceeded: Boolean, sentCasTicket: Boolean): Boolean {
        if (!sentCasTicket || !loadSucceeded || !isSchoolLogoutUrl(url)) return false
        val lines = pageText.orEmpty().lines().map { it.trim().trimEnd('。', '.', '!', '！') }
        return lines.any { line ->
            line in setOf("注销成功", "退出成功", "成功退出", "您已成功退出", "您已成功注销") ||
                Regex("(?:您已|您已经|已经|已)?成功(?:退出|注销)(?:系统|登录|统一身份认证系统|中央认证系统|CAS系统)").matches(line) ||
                line.equals("You have successfully logged out", ignoreCase = true) ||
                line.equals("You have successfully logged out of the Central Authentication Service", ignoreCase = true) ||
                line.equals("Log Out Successful", ignoreCase = true) ||
                line.equals("Logout successful", ignoreCase = true)
        }
    }

    private fun parseUrl(url: String): URI? = try {
        URI(url).takeIf { it.scheme in setOf("http", "https") && it.host != null }
    } catch (_: java.net.URISyntaxException) {
        null
    }
}
