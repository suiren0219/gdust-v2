package com.menglimi.chaoxing

import com.google.gson.JsonParser
import org.jsoup.Jsoup

/**
 * 学习通网页（服务端渲染 HTML）的解析器。基础协议沿用已有脱敏样本：
 *
 * - 课程列表 `POST mooc2-ans/visit/courselistdata`：
 *   每门课一个 `<a href="…/visit/stucoursemiddle?courseid=&clazzid=&cpi=…">`，课程名在 `span.course-name`
 * - 课程页 `mycourse/stu`：隐藏域 `#enc`(页面自身，后续请求的 stuenc)、`#workEnc`(作业 tab)、
 *   `#examEnc`(考试 tab)、`#t`、`#userId`、`#moocDomainName`
 * - 作业列表 `mooc2/work/list?courseId&classId&cpi&ut&s…&stuenc&enc`：
 *   `<li onclick="goTask(this);" data="work/task?…workId&answerId&enc">`，
 *   标题 `p.overHidden2`、状态 `p.status`(未交/已完成)、时间 `div.time`(「剩余X小时X分钟」，相对时间)
 * 标签、链接载体和同语义 class 的兼容变体由合成 fixture 覆盖，不代表已验证 issue #6 的真实 HAR。
 */
data class ChxWorkCourse(
    val courseId: Long, val classId: Long, val cpi: Long, val name: String, val url: String,
    /** 课程列表页的可选学期信息；解析不出时为 null，不代表课程已结束或未开课。 */
    val openingStart: String? = null, val openingEnd: String? = null, val ended: Boolean = false,
    val joinedOn: String? = null,
) {
    /** 仅用页面给出的时间判断；开课区间覆盖今天返回 false，完全未知时返回 null。 */
    fun closedAsOf(today: String): Boolean? = when {
        ended -> true
        openingEnd != null && openingEnd < today -> true
        openingStart != null && openingStart > today -> true
        openingStart != null && openingEnd != null -> false
        else -> null
    }
}

/** 课程页隐藏域：各 tab 的签名与公共参数。 */
data class ChxWorkTabs(
    val enc: String,
    val workEnc: String,
    val examEnc: String,
    val t: String,
    val userId: String,
    val moocDomain: String,
    val workListEndpoint: String = "",
    val coursePageUrl: String = "",
)

/** 作业列表的一条作业。 */
data class ChxWorkItem(
    val title: String,
    val status: String,
    val detailUrl: String,
    val workId: Long,
    val answerId: Long,
    val enc: String,
    val remainingText: String,
)

object ChxWorkParser {

    /** 与课程展示共用身份和入口校验，未知版式或不完整卡片不能当成没有课程。 */
    fun parseCourses(html: String): List<ChxWorkCourse> = ChxCourseApi.parseCourses(html).map {
        ChxWorkCourse(it.courseId.toLong(), it.clazzId.toLong(), it.curPersonId.toLong(), it.name, it.url,
            it.openingStart, it.openingEnd, it.ended, it.joinedOn)
    }

    /** 兼容旧调用；生产请求使用 readCourseTabs 保留具体缺失/冲突原因。 */
    fun parseCourseTabs(html: String, expectedCourse: ChxWorkCourse? = null): ChxWorkTabs? = try {
        readCourseTabs(html, expectedCourse)
    } catch (_: ChxCourseReadException) {
        null
    }

    /** 课程身份可位于隐藏域 id/name 或实际落地 URL；存在的证据必须全部匹配，不能用入口参数冒充正文身份。 */
    fun readCourseTabs(
        html: String,
        expectedCourse: ChxWorkCourse? = null,
        pageUrl: String? = null,
    ): ChxWorkTabs {
        val document = Jsoup.parse(html)
        document.select("script, style, template").remove()
        val inputs = document.select("input").filter {
            !it.hasAttr("type") || it.attr("type").equals("hidden", ignoreCase = true)
        }
        fun fields(ids: Array<out String>) = inputs.filter { input ->
            ids.any { it.equals(input.id(), ignoreCase = true) || it.equals(input.attr("name"), ignoreCase = true) }
        }
        fun field(vararg ids: String, required: Boolean = true, repeated: Boolean = false): String {
            val matches = fields(ids)
            if (matches.isEmpty()) {
                if (required) throw ChxCourseReadException("课程页缺少字段 ${ids.first()}，请在网页打开该课程后重试。", missingField = ids.first())
                return ""
            }
            val values = matches.map { it.attr("value").trim() }.distinct()
            if ((!repeated && matches.size != 1) || values.size != 1) {
                throw ChxCourseReadException("课程页字段 ${ids.first()} 存在冲突，未读取其他课程的作业。")
            }
            return values.single().also {
                if (required && it.isBlank()) throw ChxCourseReadException("课程页字段 ${ids.first()} 为空，暂时无法读取作业。")
            }
        }
        if (expectedCourse != null) {
            val landing = pageUrl?.let(ChxApi::trustedUrl)
            fun identity(expected: Long, vararg names: String) {
                val values = mutableListOf<String>()
                if (fields(names).isNotEmpty()) values += field(*names, repeated = true)
                landing?.queryParameterNames?.filter { key -> names.any { it.equals(key, ignoreCase = true) } }
                    ?.forEach { key ->
                        val parameters = landing.queryParameterValues(key)
                        if (parameters.size != 1) throw ChxCourseReadException("课程页标识 ${names.first()} 参数重复，已停止读取。")
                        values += parameters.single().orEmpty()
                    }
                if (values.isEmpty()) throw ChxCourseReadException("课程页缺少字段 ${names.first()}，请在网页打开该课程后重试。", missingField = names.first())
                if (values.any { value ->
                        value.isBlank() || value.any { it !in '0'..'9' } || value.toLongOrNull() != expected || expected <= 0
                    }) {
                    throw ChxCourseReadException("课程页标识 ${names.first()} 与所选课程不符，已停止读取。")
                }
            }
            identity(expectedCourse.courseId, "courseid")
            identity(expectedCourse.classId, "clazzid", "classid")
            identity(expectedCourse.cpi, "cpi")
        }
        val domain = field("moocDomainName")
        val base = pageUrl?.let(ChxApi::trustedUrl) ?: ChxApi.trustedUrl(domain)
        val menuLinks = document.select("a[data-url], a[href], [data-url]").flatMap { element ->
            listOf("data-url", "href").filter(element::hasAttr).map { element.attr(it).trim() }
        }.filter { link ->
            runCatching { java.net.URI(link).path.orEmpty().trimEnd('/').endsWith("/work/list") ||
                java.net.URI(link).path.orEmpty().trimEnd('/').endsWith("/work/list.html") }.getOrDefault(false)
        }.distinct()
        val endpoints = menuLinks.map { link ->
            base?.let { ChxApi.redirectTarget(it, link) }?.takeIf(ChxApi::isWorkList)?.toString()
                ?: throw ChxCourseReadException("课程页作业入口无法识别，未请求其他地址。")
        }.distinct()
        if (endpoints.size > 1) throw ChxCourseReadException("课程页作业入口存在冲突，已停止读取。")
        return ChxWorkTabs(
            field("enc"), field("workEnc"), field("examEnc", required = false),
            field("t"), field("userId", required = false), domain,
            workListEndpoint = endpoints.singleOrNull().orEmpty(),
        )
    }

    /** 无可核实的空列表协议；维护页、未知结构或缺字段都不能当成“没有作业”。 */
    fun parseWorkList(
        html: String,
        expectedCourse: ChxWorkCourse? = null,
        pageUrl: String = "https://mooc1.chaoxing.com/mooc-ans/mooc2/work/list",
    ): List<ChxWorkItem> {
        // 原因只使用客户端定义的结构/字段名，绝不带正文、URL query 或字段值。
        fun unknown(reason: String): Nothing = throw ChxCourseReadException(
            "作业列表结构无法识别（$reason），未确认是否有作业，请在学习通网页查看。")
        val base = ChxApi.trustedUrl(pageUrl) ?: unknown("列表地址不可信")
        val doc = Jsoup.parse(html)
        if (doc.select("form input[type=password]").isNotEmpty()) unknown("包含登录表单")
        doc.select("script, style, template").remove()
        val candidates = doc.select(".bottomList, #workList, #taskList, .work-list, .task-list, [aria-label=作业列表]")
        // 同一容器可同时命中多个标记或套在 task-list 内，只取最内层作业容器。
        val containers = candidates.filter { outer -> candidates.none { inner ->
            inner !== outer && inner.parents().contains(outer)
        } }
        if (containers.isEmpty()) unknown("缺少作业容器")
        if (containers.size != 1) unknown("作业容器不唯一")
        val container = containers.single()
        val rowCandidates = container.select("li, .work-item, .task-item, [data], [data-url], a[href]")
        val rows = rowCandidates.filter { row ->
            row !== container && row.parents().none { it !== container && rowCandidates.contains(it) }
        }
        if (rows.isEmpty()) unknown("未找到作业条目，空列表尚未确认")
        val out = linkedMapOf<Long, ChxWorkItem>()
        for (row in rows) {
            fun field(selector: String, name: String, required: Boolean = true, titleAttribute: Boolean = false): String {
                val values = row.select(selector).map {
                    (if (titleAttribute) it.attr("title").ifBlank { it.text() } else it.text()).trim()
                }.filter { it.isNotEmpty() }.distinct()
                if (values.size > 1) unknown("$name 字段冲突")
                return values.singleOrNull() ?: if (required) unknown("缺少 $name 字段") else ""
            }
            // 条目内“查看统计”不是作业入口，只忽略已确认的精确统计路径；从不请求统计站点。
            val hasPrimaryLink = row.select("[data], [data-url]").any { element ->
                listOf("data", "data-url").any { key -> element.hasAttr(key) && element.attr(key).isNotBlank() }
            }
            fun isStatisticsLink(value: String): Boolean = runCatching {
                val uri = java.net.URI(value)
                uri.scheme == "https" && uri.host == "stat2-ans.chaoxing.com" && uri.port in setOf(-1, 443) &&
                    uri.rawUserInfo == null && uri.rawFragment == null && uri.rawPath == "/study-knowledge/ans" &&
                    value.none { it.code <= 32 || it.code == 127 || it == '\\' } &&
                    uri.rawQuery.orEmpty().split('&').none {
                        it.substringBefore('=').lowercase() in setOf("workid", "answerid", "enc")
                    }
            }.getOrDefault(false)
            // 不执行 onclick；其余详情入口必须可信，且同一作业的所有入口一致。
            val links = row.select("[data], [data-url], a[href]").flatMap { element ->
                listOf("data", "data-url", "href").filter { element.hasAttr(it) }.mapNotNull { key ->
                    val value = element.attr(key).trim()
                    value.takeUnless { key == "href" && hasPrimaryLink && isStatisticsLink(value) }
                }
            }.distinct()
            if (links.isEmpty() || links.any { it.isBlank() }) unknown("缺少详情地址 data/data-url/href")
            val details = links.map { link ->
                ChxApi.workDetailTarget(base, link)
                    ?: unknown("详情地址不可信或路径不支持；${ChxReadDiagnostics.route(base, link)}")
            }.distinct()
            if (details.size != 1) unknown("详情地址冲突")
            val detail = details.single()
            fun parameter(name: String): String {
                val values = detail.queryParameterValues(name)
                if (values.size != 1) unknown("$name 参数缺失或重复")
                return values.single()?.takeIf { it.isNotBlank() } ?: unknown("$name 参数为空")
            }
            fun id(name: String, allowZero: Boolean = false): Long = parameter(name).let { value ->
                value.takeIf { it.all { char -> char in '0'..'9' } }?.toLongOrNull()
                    ?.takeIf { if (allowZero) it >= 0 else it > 0 }
                    ?: unknown("$name 参数无效")
            }
            val courseId = id("courseId")
            val classId = id("classId")
            val cpi = id("cpi")
            if (expectedCourse != null) {
                if (courseId != expectedCourse.courseId) unknown("courseId 与所选课程不符")
                if (classId != expectedCourse.classId) unknown("classId 与所选课程不符")
                if (cpi != expectedCourse.cpi) unknown("cpi 与所选课程不符")
            }
            val title = field(".overHidden2, .work-title, .task-title", "标题", titleAttribute = true)
            // answerId=0 表示尚未提交作答，是合法值，不能当成参数无效。
            val status = when (field(".status, .work-status, .task-status", "status")) {
                "未交", "未提交" -> "未交"
                // 已提交/已批改/待批阅都表示学生侧已交；待批阅仅说明老师尚未批改。
                "已完成", "已提交", "待批阅", "已批阅" -> "已完成"
                else -> unknown("status 字段不支持")
            }
            val item = ChxWorkItem(title, status, detail.toString(), id("workId"), id("answerId", allowZero = true), parameter("enc"),
                field(".time, .work-time, .task-time", "time", required = false))
            val previous = out.putIfAbsent(item.workId, item)
            if (previous != null && previous != item) unknown("workId 重复且条目冲突")
        }
        return out.values.toList()
    }

    /** stu-job-info 返回任务点计数，不是作业数量；不能据此跳过任何课程的作业列表。 */
    fun parseJobInfo(json: String): Map<Long, Pair<Int, Int>> {
        val out = mutableMapOf<Long, Pair<Int, Int>>()
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return out
        val arr = root.getAsJsonArray("jobArray") ?: return out
        for (e in arr) {
            val o = e.asJsonObject
            val clazzId = o.get("clazzId")?.asLong ?: continue
            out[clazzId] = (o.get("jobCount")?.asInt ?: 0) to (o.get("jobFinishCount")?.asInt ?: 0)
        }
        return out
    }

    /**
     * 作业详情页 → 绝对截止时间（epoch ms，0=解析不到）。
     * 结构（实测）：`<div class="infoHead"><p>Time:<em>10-28 15:54</em>to<em>11-22 23:59</em></p>`
     * 只有月-日 时:分，没有年份：按「离 now 最近」推断（跨年 ±1 年修正）。
     */
    fun parseDeadlineMillis(html: String, nowMillis: Long): Long {
        val m = Regex("""Time:\s*<em>([^<]+)</em>\s*to\s*<em>([^<]+)</em>""", RegexOption.IGNORE_CASE)
            .find(html)
            ?: Regex("""时间[：:]\s*<em>([^<]+)</em>\s*(?:至|到)\s*<em>([^<]+)</em>""").find(html)
            ?: return 0L
        return parseMonthDayTime(m.groupValues[2].trim(), nowMillis)
    }

    /** 「11-22 23:59」/「2026-11-22 23:59」→ epoch ms；年份缺失时按 now 最近推断。 */
    fun parseMonthDayTime(text: String, nowMillis: Long): Long {
        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone)
        val full = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})[ T］](\d{1,2}):(\d{2})""").find(text)
        if (full != null) {
            val (y, mo, d, h, mi) = full.destructured
            return runCatching {
                java.time.LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt())
                    .atZone(zone).toInstant().toEpochMilli()
            }.getOrDefault(0L)
        }
        val md = Regex("""(\d{1,2})-(\d{1,2})\s*(\d{1,2}):(\d{2})""").find(text) ?: return 0L
        val (mo, d, h, mi) = md.destructured
        return runCatching {
            fun at(y: Int) = java.time.LocalDateTime.of(y, mo.toInt(), d.toInt(), h.toInt(), mi.toInt())
                .atZone(zone).toInstant().toEpochMilli()
            var ts = at(now.year)
            if (ts - nowMillis > 180L * 24 * 3600 * 1000) ts = at(now.year - 1)
            if (nowMillis - ts > 180L * 24 * 3600 * 1000) ts = at(now.year + 1)
            ts
        }.getOrDefault(0L)
    }

    /** 「剩余1125小时55分钟」/「剩余2天3小时」→ 毫秒；解析不了返回 0（无截止信息）。 */
    fun remainingMillis(text: String): Long {
        val m = Regex("剩余(?:(\\d+)天)?(?:(\\d+)小时)?(?:(\\d+)分钟)?").find(text) ?: return 0
        val d = m.groupValues[1].toLongOrNull() ?: 0
        val h = m.groupValues[2].toLongOrNull() ?: 0
        val mi = m.groupValues[3].toLongOrNull() ?: 0
        if (d == 0L && h == 0L && mi == 0L) return 0
        return ((d * 24 + h) * 60 + mi) * 60_000L
    }

}
