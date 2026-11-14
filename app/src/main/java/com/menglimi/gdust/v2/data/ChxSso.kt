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
 * 因此联动方式是复用 CAS 网页会话（TGC 存在 WebView 的 CookieManager 里）：
 * 用户在同一 WebView 里登录后可尝试复用认证，是否有效以各站点实际验证为准。
 * CAS ticket 一次性，不能拿同一张票换两个系统，所以这里不做 ticket 复用。
 */
object ChxSso {
    private const val CAS_LOGIN = "https://cas.gdust.edu.cn/lyuapServer/login?service="

    /** 泛雅 / 广科智课（机构 fid=420）。该加速域没开 HTTPS，实测 443 无响应，必须走 http。 */
    private const val FYSSO_SERVICE = "http://gdkjxy.fysso.chaoxing.com/sso/gdkjxy"

    /** 新版超星课程中心。 */
    private const val MH_SERVICE =
        "https://gdkj.mh.chaoxing.com/sso/login/3rd?wfwfid=420&refer=https%3A%2F%2Fgdkj.mh.chaoxing.com"

    /** 教务（正方）网页入口，同样走 CAS，验证「一次登录双通」用。 */
    private const val TEACHING_SERVICE = "http://gkjx.gdust.edu.cn/sso/lyiotlogin"

    /** CAS 登出入口，用于显式退出共享的学校网页登录会话。 */
    const val LOGOUT_URL = "https://cas.gdust.edu.cn/cas/logout"

    private fun sso(service: String) = CAS_LOGIN + URLEncoder.encode(service, "UTF-8")

    val fyssoUrl: String get() = sso(FYSSO_SERVICE)
    val mhUrl: String get() = sso(MH_SERVICE)
    val teachingWebUrl: String get() = sso(TEACHING_SERVICE)

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
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        val path = uri.path.orEmpty().trimEnd('/').lowercase(Locale.ROOT)
        if (host == "cas.gdust.edu.cn") return path in setOf("/cas/login", "/lyuapserver/login")
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
