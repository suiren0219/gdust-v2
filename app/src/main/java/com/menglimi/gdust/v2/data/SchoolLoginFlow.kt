package com.menglimi.gdust.v2.data

import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLEncoder

internal enum class SchoolService(val label: String) {
    PORTAL("门户"), TEACHING("教务"), CHX("学习通"),
}

internal enum class SchoolConnection {
    PENDING, CONNECTING, AUTH_REQUIRED, VERIFIED, UNAVAILABLE, SKIPPED,
}

/** 只接收各服务业务接口的验证结果；URL、Cookie 存在与网页加载完成都不是成功事件。 */
internal class SchoolLoginFlow(saved: List<String>? = null, startAt: SchoolService = SchoolService.PORTAL) {
    private val connections = SchoolService.entries.associateWith {
        if (it.ordinal < startAt.ordinal) SchoolConnection.SKIPPED else SchoolConnection.PENDING
    }.toMutableMap()
    var current: SchoolService? = startAt
        private set
    var closed: Boolean = false
        private set
    private var revision = 0L
    private var pageUrl: String? = null
    private var pendingUrl: String? = null
    private var pageFinished = false
    private var pageFailed = false
    private var activeProbe: Probe? = null

    // 不含 URL、TOKEN、Cookie 或 ticket；旋转只恢复已经结束的阶段。
    init {
        if (saved?.size == SchoolService.entries.size) {
            for (service in SchoolService.entries) {
                val state = SchoolConnection.entries.firstOrNull { it.name == saved[service.ordinal] }
                if (state == null || state !in setOf(SchoolConnection.VERIFIED, SchoolConnection.SKIPPED, SchoolConnection.UNAVAILABLE)) break
                if (state == SchoolConnection.UNAVAILABLE &&
                    saved.getOrNull(service.ordinal + 1) in listOf(null, SchoolConnection.PENDING.name)) break
                connections[service] = state
                current = SchoolService.entries.getOrNull(service.ordinal + 1)
            }
        }
    }

    class Probe internal constructor(val service: SchoolService, internal val revision: Long)

    fun snapshot(): List<String> = SchoolService.entries.map { connection(it).name }
    fun connection(service: SchoolService): SchoolConnection = connections.getValue(service)
    fun isVerified(service: SchoolService): Boolean = connection(service) == SchoolConnection.VERIFIED
    val schoolReady: Boolean get() = isVerified(SchoolService.PORTAL) || isVerified(SchoolService.TEACHING)
    val complete: Boolean get() = current == null

    /** 在 loadUrl/stopLoading 前同步调用；旧 Job 即使还未结束，也不能占住新页探活。 */
    fun navigating(target: String? = null): Boolean {
        if (closed || complete) return false
        invalidatePage()
        pendingUrl = target
        connections[current!!] = SchoolConnection.CONNECTING
        return true
    }

    fun pageStarted(url: String): Boolean {
        if (!navigating()) return false
        pageUrl = url
        return true
    }

    fun isLoadingPage(url: String, actualUrl: String?): Boolean =
        !pageFinished && !pageFailed && matchesPage(url, actualUrl)

    fun matchesPage(url: String, actualUrl: String?): Boolean =
        !closed && !complete && pageUrl?.substringBefore('#') == url.substringBefore('#') &&
            actualUrl?.substringBefore('#') == url.substringBefore('#')

    /** 失败时 WebView.url 可能仍停在跳转前的 CAS；按已请求的主框架目标接收错误。 */
    fun matchesFailure(url: String): Boolean = !closed && !complete && !pageFailed &&
        listOfNotNull(pendingUrl, pageUrl).any { it.substringBefore('#') == url.substringBefore('#') }

    fun pageFinished(url: String, actualUrl: String?): Boolean {
        if (!matchesPage(url, actualUrl) || pageFailed) return false
        pageFinished = true
        return true
    }

    fun authenticationRequired() {
        if (!closed && pageFinished && !pageFailed && activeProbe == null) {
            current?.let { connections[it] = SchoolConnection.AUTH_REQUIRED }
        }
    }

    fun beginProbe(): Probe? {
        val service = current ?: return null
        if (closed || !pageFinished || pageFailed || activeProbe != null ||
            connection(service) == SchoolConnection.AUTH_REQUIRED) return null
        return Probe(service, revision).also { activeProbe = it }
    }

    fun accepts(probe: Probe): Boolean = !closed && !pageFailed && pageFinished &&
        current == probe.service && revision == probe.revision && activeProbe === probe

    /** 旧协程的 finally 不能清掉下一页或下一阶段的探活。 */
    fun endProbe(probe: Probe): Boolean {
        if (activeProbe !== probe) return false
        activeProbe = null
        return true
    }

    fun unavailable(probe: Probe? = null): Boolean {
        if (closed || complete || (probe != null && !accepts(probe))) return false
        invalidatePage()
        pageFailed = true
        connections[current!!] = SchoolConnection.UNAVAILABLE
        return true
    }

    /** 只有当前页面的业务验证能推进；同一服务重试前的成功也会被拒绝。 */
    fun verified(probe: Probe): Boolean {
        if (!accepts(probe)) return false
        connections[probe.service] = SchoolConnection.VERIFIED
        advance()
        return true
    }

    fun skip() {
        if (closed || complete) return
        connections[current!!] = SchoolConnection.SKIPPED
        advance()
    }

    fun continueAfterFailure(): Boolean {
        if (closed || complete || connection(current!!) != SchoolConnection.UNAVAILABLE) return false
        advance()
        return true
    }

    fun close() {
        closed = true
        invalidatePage()
    }

    private fun advance() {
        invalidatePage()
        current = current?.ordinal?.plus(1)?.let { SchoolService.entries.getOrNull(it) }
    }

    private fun invalidatePage() {
        revision++
        pageUrl = null
        pendingUrl = null
        pageFinished = false
        pageFailed = false
        activeProbe = null
    }

    fun summary(): String = SchoolService.entries.joinToString(" · ") { service ->
        service.label + when (connection(service)) {
            SchoolConnection.PENDING -> "待连接"
            SchoolConnection.CONNECTING -> "连接中"
            SchoolConnection.AUTH_REQUIRED -> "待认证"
            SchoolConnection.VERIFIED -> "已连接"
            SchoolConnection.UNAVAILABLE -> "未验证"
            SchoolConnection.SKIPPED -> "未连接"
        }
    }
}

/** 入口与存储键来自公开 portal.gdust.edu.cn/index.js；不截获或重放登录 ticket。 */
internal object SchoolLoginWeb {
    const val PORTAL_ORIGIN = "https://portal.gdust.edu.cn"
    const val PORTAL_CALLBACK = "$PORTAL_ORIGIN/smart-admin-api/user/ssoLogin/?appId=portalRemote"
    val portalLoginUrl: String = "https://cas.gdust.edu.cn/cas/?service=" +
        URLEncoder.encode(PORTAL_CALLBACK, "UTF-8")

    fun entry(service: SchoolService): String = when (service) {
        SchoolService.PORTAL -> portalLoginUrl
        SchoolService.TEACHING -> ChxSso.teachingWebUrl
        SchoolService.CHX -> ChxSso.fyssoUrl
    }

    fun isPortalPage(value: String): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        return url.scheme == "https" && url.host == "portal.gdust.edu.cn" && url.port == 443 &&
            url.username.isEmpty() && url.password.isEmpty()
    }

    /** WebView 的 hash 路由可能不发 onPageStarted，不能因此取消仍属于同文档的探活。 */
    fun isFragmentNavigation(current: String?, target: String): Boolean = current != null && current != target &&
        current.substringBefore('#') == target.substringBefore('#')

    fun isServicePage(service: SchoolService, value: String): Boolean = when (service) {
        SchoolService.PORTAL -> isPortalPage(value)
        SchoolService.TEACHING -> TeachingEndpoints.isLandingPage(value)
        SchoolService.CHX -> value.toHttpUrlOrNull()?.let { url ->
            url.username.isEmpty() && url.password.isEmpty() &&
                url.port == (if (url.scheme == "https") 443 else 80) && ChxSso.isChaoxingHost(url.host)
        } == true
    }

    // 只判断视口内是否已有可见内容，不读取输入值、Cookie 或认证存储。
    val pageContentScript = """
        (function() {
            var body = document.body;
            if (!body || innerWidth <= 0 || innerHeight <= 0 ||
                body.getBoundingClientRect().height <= 0) return false;
            return Array.from(body.querySelectorAll('input:not([type=hidden]),button,a,p,label,h1,h2,canvas,iframe')).some(function(el) {
                var rect = el.getBoundingClientRect();
                var style = getComputedStyle(el);
                return rect.width > 0 && rect.height > 0 && rect.bottom > 0 && rect.right > 0 &&
                    rect.top < innerHeight && rect.left < innerWidth &&
                    style.visibility !== 'hidden' && style.display !== 'none' && style.opacity !== '0';
            });
        })()
    """.trimIndent()

    // 双重校验来源：调用前检查 WebView URL；脚本执行时再次检查实际 document 的 origin。
    // 只读门户最终 TOKEN，不读密码、CasToken、URL loginCode 或任意其他站点的存储。
    val portalSessionScript = """
        (function() {
            if (location.origin !== '$PORTAL_ORIGIN') return null;
            try {
                var user = JSON.parse(sessionStorage.getItem('USER_STATUS') || '{}');
                var token = sessionStorage.getItem('TOKEN') || '';
                if (!token || user.id === 'guest') return null;
                return {origin: location.origin, token: token};
            } catch (_) { return null; }
        })()
    """.trimIndent()

    fun portalToken(pageUrl: String, result: String?): String? {
        if (!isPortalPage(pageUrl) || result.isNullOrBlank() || result.length > 20_000) return null
        return try {
            val value = JsonParser.parseString(result)
            val objectValue = if (value.isJsonObject) value.asJsonObject else return null
            if (objectValue.get("origin")?.asString != PORTAL_ORIGIN) return null
            objectValue.get("token")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString?.takeIf { token ->
                    token.isNotBlank() && token.length <= 16_384 && token.none { it.code < 33 || it.code == 127 }
                }
        } catch (_: Exception) { null }
    }

    /** 只对确切的官方来源识别登录页，第二个 service 再要求认证时必须等待用户完成。 */
    fun isAuthenticationPage(url: String): Boolean {
        if (ChxSso.isLoginUrl(url)) return true
        val parsed = url.toHttpUrlOrNull() ?: return false
        val base = TeachingEndpoints.baseForPage(url) ?: return false
        val prefix = requireNotNull(base.toHttpUrlOrNull()).encodedPath.trimEnd('/')
        return parsed.encodedPath.removePrefix(prefix).startsWith("/xtgl/login_")
    }
}
