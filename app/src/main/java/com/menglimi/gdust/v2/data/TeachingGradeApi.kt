package com.menglimi.gdust.v2.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.net.URI
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.Jsoup

internal class TeachingGradeApi(private val http: SessionHttpClient, private val base: String) {
    private val loader = TeachingGradeLoader(base, http::execute)

    suspend fun load(year: String, semester: String): GradeReport = withContext(Dispatchers.Default) {
        loader.load(year, semester)
    }
}

internal const val GRADE_PAGE_SIZE = 15
internal const val GRADE_MAX_PAGES = 100

/** 可注入响应的纯加载流程；Cookie、会话版本和跳转策略由调用方的客户端负责。 */
internal class TeachingGradeLoader(
    private val base: String,
    private val execute: suspend (Request) -> SessionResponse,
    private val now: () -> Long = System::currentTimeMillis,
) {
    // time 是此加载器的查询次数，不是时间戳；多次刷新也继续递增。
    private val requests = AtomicLong(0)

    suspend fun load(year: String, semester: String): GradeReport {
        require(year.matches(Regex("[0-9]{4}")) && semester in setOf("3", "12")) { "请选择有效学年和学期" }
        currentCoroutineContext().ensureActive()
        val pageUrl = endpoint("cjcx/cjcx_cxDgXscj.html")
            .addQueryParameter("gnmkdm", "N305005").addQueryParameter("layout", "default").build()
        val queryUrl = endpoint("cjcx/cjcx_cxXsgrcj.html")
            .addQueryParameter("doType", "query").addQueryParameter("gnmkdm", "N305005").build()
        val form = parseGradeForm(gradeResponseBody(execute(Request.Builder().url(pageUrl).get().build())))
        if (year !in form.years || semester !in form.semesters) failGrade(GradeFailure.TERM_UNAVAILABLE)

        val entries = mutableListOf<TeachingGrade>()
        val seenPages = hashSetOf<Map<TeachingGrade, Int>>()
        var total: Int? = null
        var totalPages: Int? = null
        for (pageNumber in 1..GRADE_MAX_PAGES) {
            currentCoroutineContext().ensureActive()
            val body = FormBody.Builder()
                .add("xnm", year).add("xqm", semester)
                .add("sfzgcj", "").add("kcbj", "").add("pkey", form.pkey)
                .add("_search", "false").add("nd", now().toString())
                .add("queryModel.showCount", GRADE_PAGE_SIZE.toString())
                .add("queryModel.currentPage", pageNumber.toString())
                // 抓取契约中的字面值 +，FormBody 将其编码成 %2B，不能改成业务列名。
                .add("queryModel.sortName", "+").add("queryModel.sortOrder", "asc")
                .add("time", requests.incrementAndGet().toString()).build()
            val response = execute(Request.Builder().url(queryUrl)
                .header("Referer", pageUrl.toString()).header("X-Requested-With", "XMLHttpRequest")
                .post(body).build())
            currentCoroutineContext().ensureActive()
            val page = parseGradePage(gradeResponseBody(response))
            if (page.currentPage != pageNumber) failGrade(GradeFailure.INCOMPLETE)
            if (total != null && (page.total != total || page.totalPages != totalPages)) {
                failGrade(GradeFailure.INCOMPLETE)
            }
            total = page.total
            totalPages = page.totalPages
            val expectedPages = (page.total.toLong() + GRADE_PAGE_SIZE - 1) / GRADE_PAGE_SIZE
            if (expectedPages > GRADE_MAX_PAGES || page.totalPages > GRADE_MAX_PAGES) failGrade(GradeFailure.LIMIT)
            if (page.total == 0) {
                // 正方空结果可能用 0 或 1 表示总页数，当前请求仍必须明确回显第 1 页。
                if (pageNumber != 1 || page.totalPages !in 0..1 || page.entries.isNotEmpty()) {
                    failGrade(GradeFailure.INCOMPLETE)
                }
                return GradeReport(emptyList(), 0)
            }
            if (page.totalPages.toLong() != expectedPages) failGrade(GradeFailure.INCOMPLETE)
            val expectedCount = minOf(GRADE_PAGE_SIZE, page.total - entries.size)
            if (page.entries.size != expectedCount) failGrade(GradeFailure.INCOMPLETE)
            if (page.entries.any { it.year != year || it.semester != semester }) failGrade(GradeFailure.TERM_MISMATCH)
            // 用整页的多重集合识别重复；不按课程号去重，避免误删重修/补考记录。
            if (!seenPages.add(page.entries.groupingBy { it }.eachCount())) failGrade(GradeFailure.INCOMPLETE)
            entries += page.entries
            if (pageNumber == page.totalPages) {
                if (entries.size != page.total) failGrade(GradeFailure.INCOMPLETE)
                currentCoroutineContext().ensureActive()
                return GradeReport(entries.toList(), page.total)
            }
        }
        failGrade(GradeFailure.LIMIT)
    }

    private fun endpoint(path: String): HttpUrl.Builder {
        val url = base.toHttpUrlOrNull() ?: failGrade(GradeFailure.RESPONSE)
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) failGrade(GradeFailure.RESPONSE)
        // 保留传入的全部路径前缀；以 / 开头 resolve 会丢掉 WebVPN 的转发路径。
        return url.newBuilder().encodedPath(url.encodedPath.trimEnd('/') + "/" + path)
            .query(null).fragment(null)
    }
}

internal data class GradePageForm(val pkey: String, val years: Set<String>, val semesters: Set<String>)

internal fun parseGradeForm(html: String): GradePageForm {
    if (isGradeLoginHtml(html)) throw gradeAuthFailed()
    val document = Jsoup.parse(html)
    val keys = document.select("input#pkey[value]")
    if (keys.size != 1) failGrade(GradeFailure.STRUCTURE)
    fun options(id: String): Set<String> {
        val selects = document.select("select#$id")
        if (selects.size != 1 || selects.single().hasAttr("disabled")) failGrade(GradeFailure.STRUCTURE)
        return selects.single().select("option[value]")
            .filter { !it.hasAttr("disabled") && it.parent()?.hasAttr("disabled") != true }
            .map { it.attr("value") }.filter { it.isNotEmpty() }.toSet()
            .ifEmpty { failGrade(GradeFailure.STRUCTURE) }
    }
    // 空值也有效，但元素缺失不是空 pkey；每次 load 都重新读取页面。
    return GradePageForm(keys.single().attr("value"), options("xnm"), options("xqm"))
}

internal data class TeachingGradePage(
    val entries: List<TeachingGrade>, val currentPage: Int, val totalPages: Int, val total: Int,
)

internal fun parseGradePage(body: String): TeachingGradePage {
    if (isGradeLoginHtml(body)) throw gradeAuthFailed()
    val root = gradeJson(body)
    gradeJsonAuthCode(root)?.let { throw gradeAuthFailed(it) }
    if (!root.isJsonObject) failGrade(GradeFailure.STRUCTURE)
    val obj = root.asJsonObject
    if (obj.get("success")?.let { it.isJsonPrimitive && it.asString == "false" } == true) {
        failGrade(GradeFailure.RESPONSE)
    }
    for (key in listOf("code", "status", "statusCode")) {
        obj.get(key)?.let {
            if (!it.isJsonNull && (!it.isJsonPrimitive || it.asString !in setOf("0", "200"))) {
                failGrade(GradeFailure.RESPONSE)
            }
        }
    }
    val items = obj.get("items")?.takeIf { it.isJsonArray }?.asJsonArray ?: failGrade(GradeFailure.STRUCTURE)
    if (items.size() > GRADE_PAGE_SIZE) failGrade(GradeFailure.INCOMPLETE)
    val entries = items.map { element ->
        if (!element.isJsonObject) failGrade(GradeFailure.STRUCTURE)
        val item = element.asJsonObject
        TeachingGrade(
            year = item.gradeString("xnm"), semester = item.gradeString("xqm"),
            yearLabel = item.gradeString("xnmmc"), semesterLabel = item.gradeString("xqmmc"),
            courseCode = item.gradeString("kch"), courseName = item.gradeString("kcmc"),
            courseNature = item.gradeString("kcxzmc"), score = item.gradeString("cj"),
            credits = item.gradeString("xf"), gradePoint = item.gradeString("jd"),
            creditGradePoint = item.gradeString("xfjd"), examType = item.gradeString("ksxz"),
            department = item.gradeString("kkbmmc"), courseCategory = item.optionalGradeString("kclbmc"),
            courseAttribute = item.optionalGradeString("kcgsmc"), assessmentMethod = item.optionalGradeString("khfsmc"),
            teachingClass = item.optionalGradeString("jxbmc"), teacher = item.optionalGradeString("jsxm"),
            degreeCourse = item.optionalGradeString("sfxwkc"), courseMark = item.optionalGradeString("kcbj"),
        )
    }
    // items 中嵌套的 queryModel 是记录元数据，不能拿来补顶层缺失的分页字段。
    return TeachingGradePage(entries, obj.gradeInt("currentPage"), obj.gradeInt("totalPage"), obj.gradeInt("totalResult"))
}

private fun JsonObject.gradeString(key: String): String {
    val value = get(key) ?: failGrade(GradeFailure.STRUCTURE)
    if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) failGrade(GradeFailure.STRUCTURE)
    return value.asString
}

private fun JsonObject.optionalGradeString(key: String): String? {
    val value = get(key) ?: return null
    if (value.isJsonNull) return null
    return gradeString(key)
}

private fun JsonObject.gradeInt(key: String): Int {
    val value = get(key) ?: failGrade(GradeFailure.STRUCTURE)
    if (!value.isJsonPrimitive || !value.asString.matches(Regex("[0-9]+"))) failGrade(GradeFailure.STRUCTURE)
    return value.asString.toIntOrNull() ?: failGrade(GradeFailure.STRUCTURE)
}

private fun gradeJson(body: String): JsonElement = try {
    JsonReader(StringReader(body.removePrefix("\uFEFF"))).use { reader ->
        reader.strictness = Strictness.STRICT
        val root = JsonParser.parseReader(reader)
        if (reader.peek() != JsonToken.END_DOCUMENT) failGrade(GradeFailure.STRUCTURE)
        root
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: GradeLoadException) {
    throw failure
} catch (_: Exception) {
    // 不保留解析异常的 cause，它可能包含响应片段。
    failGrade(GradeFailure.STRUCTURE)
}

private fun gradeJsonAuthCode(root: JsonElement): Int? {
    val values = if (root.isJsonObject) listOf("code", "status", "statusCode").mapNotNull { root.asJsonObject.get(it) }
        else listOf(root)
    return values.firstNotNullOfOrNull { value ->
        if (value.isJsonPrimitive) value.asString.toIntOrNull()?.takeIf { it == 401 || it == 901 } else null
    }
}

private fun gradeResponseBody(response: SessionResponse): String {
    if (response.status == 401 || response.status == 901) throw gradeAuthFailed(response.status)
    if (response.status in 300..399 && response.location?.let(::isGradeLoginUrl) == true) throw gradeAuthFailed()
    val body = response.body.trim().removePrefix("\uFEFF").trim()
    if (isGradeLoginHtml(body)) throw gradeAuthFailed()
    if (body.startsWith("{") || body in setOf("401", "901", "\"401\"", "\"901\"")) {
        gradeJsonAuthCode(gradeJson(body))?.let { throw gradeAuthFailed(it) }
    }
    if (response.status !in 200..299) failGrade(GradeFailure.RESPONSE)
    if (body.isEmpty()) failGrade(GradeFailure.STRUCTURE)
    return body
}

private fun isGradeLoginUrl(value: String): Boolean {
    val path = try { URI(value.trim()).path.orEmpty() } catch (_: Exception) { return false }
    return path.split('/').any {
        it.equals("cas", ignoreCase = true) ||
            it.matches(Regex("(?i)(?:login|sign[-_]?in)(?:[._-].*)?"))
    }
}

private fun isGradeLoginHtml(body: String): Boolean {
    val html = body.trim().removePrefix("\uFEFF").trim()
    if (!html.startsWith('<')) return false
    val document = Jsoup.parse(html)
    val title = document.title()
    if (title.contains("统一身份认证") || title.contains("登录") || title.contains("登陆") ||
        Regex("(?i)\\b(?:login|sign[ -]?in)\\b").containsMatchIn(title)) return true
    if (document.select("input[type=password]").isNotEmpty()) return true
    if (document.select("form[action]").any { isGradeLoginUrl(it.attr("action")) }) return true
    val refreshUrl = Regex("(?i)(?:^|;)\\s*url\\s*=\\s*(.+)$")
    if (document.select("meta[http-equiv]").any {
        it.attr("http-equiv").equals("refresh", ignoreCase = true) &&
            refreshUrl.find(it.attr("content"))?.groupValues?.get(1)?.let { target ->
                isGradeLoginUrl(target.trim().trim('\'', '"'))
            } == true
    }) return true
    // 只识别明确的脚本跳转，不把包含「登录」字样的普通错误页视作会话失效。
    val redirect = Regex("""(?:location(?:\.href)?\s*=|location\.(?:replace|assign)\s*\()\s*['"]([^'"]+)['"]""")
    return document.select("script").any { script ->
        redirect.findAll(script.data()).any { isGradeLoginUrl(it.groupValues[1]) }
    }
}

private fun gradeAuthFailed(code: Int = 401) = AuthFailed(code, "教务登录已失效，请先完成教务网页登录")
private fun failGrade(reason: GradeFailure): Nothing = throw GradeLoadException(reason)
