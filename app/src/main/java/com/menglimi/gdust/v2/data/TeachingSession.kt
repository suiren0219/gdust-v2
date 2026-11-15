package com.menglimi.gdust.v2.data

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import java.time.LocalDate

internal const val VPN_TEACHING_ENDPOINT = "https://webvpn.gdust.edu.cn/http/77726476706e69737468656265737421a1a013d2766626022b5cc7fd/kbcx/xskbcx_cxXsgrkb.html?vpn-12-o1-172.16.254.1&gnmkdm=N2151"
internal const val TEACHING_ENDPOINT = "http://172.16.254.1/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151"
internal const val PUBLIC_TEACHING_ENDPOINT = "http://gkjx.gdust.edu.cn/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151"

internal fun isTeachingEndpoint(url: String): Boolean = url in setOf(
    VPN_TEACHING_ENDPOINT, TEACHING_ENDPOINT, PUBLIC_TEACHING_ENDPOINT,
    PUBLIC_TEACHING_ENDPOINT.replace("http://", "https://"),
)

data class TeachingSession(val endpoint: String, val cookie: String, val pageUrl: String)

/** 学期值仅用于只读探活，不把空课表或猜测日期写入用户课表。 */
internal fun teachingProbeRequest(
    pageUrl: String,
    calendar: CalendarInfo?,
    today: LocalDate = LocalDate.now(),
): okhttp3.Request? {
    if (!TeachingEndpoints.isLandingPage(pageUrl)) return null
    val base = TeachingEndpoints.baseForPage(pageUrl) ?: return null
    val year = calendar?.year ?: (if (today.monthValue < 8) today.year - 1 else today.year).toString()
    val semester = calendar?.semester ?: if (today.monthValue in 2..7) "12" else "3"
    return okhttp3.Request.Builder().url("$base/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151")
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        .header("X-Requested-With", "XMLHttpRequest")
        .post(okhttp3.FormBody.Builder().add("xnm", year).add("xqm", semester)
            .add("kzlx", "ck").add("xsdm", "").add("kclbdm", "").build()).build()
}

/** 200/匿名 Cookie/落地 URL 均不足以认证；只认教务自身 kbList JSON，维护返回保持未知。 */
internal fun teachingSessionEvidence(response: SessionResponse): Boolean? {
    if (response.status in setOf(401, 901)) return false
    if (response.status in 300..399) {
        return if (response.location?.let(::isTeachingLoginTarget) == true) false else null
    }
    if (response.status !in 200..299) return null
    val body = response.body.trim().removePrefix("\uFEFF").trim()
    if (isTeachingLoginHtml(body)) return false
    return try {
        val root = JsonParser.parseString(body)
        if (teachingAuthCode(root)) return false
        if (!root.isJsonObject) return null
        val obj = root.asJsonObject
        for (key in listOf("code", "status", "statusCode")) {
            val value = obj.get(key) ?: continue
            if (!value.isJsonPrimitive || value.asString !in setOf("0", "200")) return null
        }
        obj.get("success")?.let {
            if (!it.isJsonPrimitive || it.asString != "true") return null
        }
        val courses = obj.get("kbList")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        // 空课表可以探活；损坏列表、错误对象或未知包装不能冒充成功。
        if (courses.all { it.isJsonObject }) true else null
    } catch (_: Exception) { null }
}

private fun teachingAuthCode(value: JsonElement, depth: Int = 0): Boolean {
    if (depth > 4) return false
    if (value.isJsonPrimitive) return value.asString in setOf("401", "901")
    if (!value.isJsonObject) return false
    val obj = value.asJsonObject
    return listOf("code", "status", "statusCode").any { key ->
        obj.get(key)?.let { it.isJsonPrimitive && it.asString in setOf("401", "901") } == true
    } || obj.get("data")?.let { teachingAuthCode(it, depth + 1) } == true
}

/** 相对登录地址限定在已知教务来源；绝对跳转仍须通过精确来源校验。 */
private fun isTeachingLoginTarget(value: String): Boolean = TeachingEndpoints.bases.any { base ->
    val target = "$base/".toHttpUrl().resolve(value)?.toString() ?: return@any false
    SchoolLoginWeb.isAuthenticationPage(target)
}

private fun isTeachingLoginHtml(body: String): Boolean {
    if (!body.startsWith('<')) return false
    val document = Jsoup.parse(body)
    if (document.title().contains("统一身份认证") || document.select("input[type=password]").isNotEmpty()) return true
    if (document.select("form[action]").any { isTeachingLoginTarget(it.attr("action")) }) return true
    val refresh = Regex("(?i)(?:^|;)\\s*url\\s*=\\s*(.+)$")
    if (document.select("meta[http-equiv]").any {
        it.attr("http-equiv").equals("refresh", ignoreCase = true) &&
            refresh.find(it.attr("content"))?.groupValues?.get(1)?.let { target ->
                isTeachingLoginTarget(target.trim().trim('\'', '"'))
            } == true
    }) return true
    val redirect = Regex("""(?:location(?:\.href)?\s*=|location\.(?:replace|assign)\s*\()\s*['"]([^'"]+)['"]""")
    return document.select("script").any { script ->
        redirect.findAll(script.data()).any { isTeachingLoginTarget(it.groupValues[1]) }
    }
}
