package com.menglimi.gdust.v2.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.MonthDay

/** 本校实际教务菜单的个人信息查询路由；不会提交资料修改表单。 */
internal class TeachingBirthdayApi(private val execute: suspend (Request) -> SessionResponse) {
    suspend fun load(pageUrl: String, account: String, today: LocalDate = LocalDate.now()): MonthDay? {
        val base = TeachingEndpoints.baseForPage(pageUrl) ?: return null
        if (!TeachingEndpoints.isLandingPage(pageUrl) || account.isBlank()) return null
        val page = pageUrl.toHttpUrl().newBuilder().query(null).fragment(null).build()
        val response = execute(request(page))
        currentCoroutineContext().ensureActive()
        if (response.status != 200) return null
        val profile = findProfile(response.body, base) ?: return null
        val profileResponse = execute(request(profile).newBuilder().header("Referer", page.toString()).build())
        currentCoroutineContext().ensureActive()
        if (profileResponse.status != 200) return null
        return Birthday.fromTeachingHtml(profileResponse.body, account, today)
    }

    private fun request(url: HttpUrl) = Request.Builder().url(url)
        .header("User-Agent", ChxCourseApi.USER_AGENT).get().build()

    companion object {
        private const val PROFILE_PATH = "/xsxxxggl/xsgrxxwh_cxXsgrxx.html"
        // 来自本校首页 clickMenu(gnmkdm, dyym, gnmkmc, sfgnym)，只读字面值，不执行脚本。
        private val menu = Regex("""^\s*clickMenu\(\s*(['"])([A-Za-z0-9]+)\1\s*,\s*(['"])(/xsxxxggl/xsgrxxwh_cxXsgrxx\.html)\3\s*,\s*(['"])([^'"\r\n]{1,40})\5\s*,\s*(['"]?)([01]?)\7\s*\)\s*;?\s*$""")

        internal fun findProfile(html: String, base: String): HttpUrl? {
            if (base !in TeachingEndpoints.bases) return null
            val document = Jsoup.parse(html)
            if (document.select("input[type=password]").isNotEmpty()) return null
            val targets = document.select("[onclick]").mapNotNull { element ->
                val match = menu.matchEntire(element.attr("onclick")) ?: return@mapNotNull null
                val title = match.groupValues[6]
                if (!title.contains("个人信息") || listOf("修改", "维护", "申请").any(title::contains)) return@mapNotNull null
                val root = base.toHttpUrl()
                root.newBuilder().encodedPath(root.encodedPath.trimEnd('/') + PROFILE_PATH)
                    .query(null).fragment(null).addQueryParameter("gnmkdm", match.groupValues[2]).build()
            }.distinct()
            return targets.singleOrNull()
        }

        internal fun isAllowed(base: String, page: HttpUrl, url: HttpUrl): Boolean {
            val root = base.toHttpUrl()
            return url.scheme == root.scheme && url.host == root.host && url.port == root.port &&
                url.username.isEmpty() && url.password.isEmpty() && url.fragment == null &&
                (url == page || (url.encodedPath == root.encodedPath.trimEnd('/') + PROFILE_PATH &&
                    url.queryParameterNames == setOf("gnmkdm") &&
                    url.queryParameterValues("gnmkdm").singleOrNull()?.matches(Regex("[A-Za-z0-9]+")) == true))
        }
    }
}
