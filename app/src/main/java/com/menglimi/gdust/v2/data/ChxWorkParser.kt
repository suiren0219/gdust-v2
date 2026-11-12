package com.menglimi.gdust.v2.data

import org.jsoup.Jsoup

/**
 * 学习通网页（服务端渲染 HTML）的解析器。所有规则都来自真实抓包（HAR），不猜接口：
 *
 * - 课程列表 `POST mooc2-ans/visit/courselistdata`：
 *   每门课一个 `<a href="…/visit/stucoursemiddle?courseid=&clazzid=&cpi=…">`，课程名在 `span.course-name`
 * - 课程页 `mycourse/stu`：隐藏域 `#enc`(页面自身，后续请求的 stuenc)、`#workEnc`(作业 tab)、
 *   `#examEnc`(考试 tab)、`#t`、`#userId`、`#moocDomainName`
 * - 作业列表 `mooc2/work/list?courseId&classId&cpi&ut&s…&stuenc&enc`：
 *   `<li onclick="goTask(this);" data="work/task?…workId&answerId&enc">`，
 *   标题 `p.overHidden2`、状态 `p.status`(未交/已完成)、时间 `div.time`(「剩余X小时X分钟」，相对时间)
 */
data class ChxCourse(val courseId: Long, val classId: Long, val cpi: Long, val name: String)

/** 课程页隐藏域：各 tab 的签名与公共参数。 */
data class ChxCourseTabs(
    val enc: String,
    val workEnc: String,
    val examEnc: String,
    val t: String,
    val userId: String,
    val moocDomain: String,
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

    /** 课程列表 → 每门课的 courseid/clazzid/cpi/课程名。 */
    fun parseCourses(html: String): List<ChxCourse> {
        val doc = Jsoup.parse(html)
        val out = mutableListOf<ChxCourse>()
        for (a in doc.select("a[href*=stucoursemiddle]")) {
            val params = queryParams(a.attr("href"))
            val courseId = params["courseid"]?.toLongOrNull() ?: continue
            val classId = params["clazzid"]?.toLongOrNull() ?: continue
            val cpi = params["cpi"]?.toLongOrNull() ?: 0
            val name = a.select(".course-name").text().trim()
            if (name.isEmpty()) continue
            out += ChxCourse(courseId, classId, cpi, name)
        }
        return out
    }

    /** 课程页 → 各 tab 的签名；缺 enc/workEnc 视为结构变化，返回 null 由调用方跳过。 */
    fun parseCourseTabs(html: String): ChxCourseTabs? {
        val doc = Jsoup.parse(html)
        fun v(id: String) = doc.select("input#$id").attr("value").trim()
        val enc = v("enc")
        val workEnc = v("workEnc")
        if (enc.isEmpty() || workEnc.isEmpty()) return null
        return ChxCourseTabs(
            enc = enc,
            workEnc = workEnc,
            examEnc = v("examEnc"),
            t = v("t"),
            userId = v("userId"),
            moocDomain = v("moocDomainName").ifEmpty { "https://mooc1.chaoxing.com" },
        )
    }

    /** 作业列表 → 每条作业（含详情地址里的 workId/answerId/enc 与相对剩余时间）。 */
    fun parseWorkList(html: String): List<ChxWorkItem> {
        val doc = Jsoup.parse(html)
        val out = mutableListOf<ChxWorkItem>()
        for (li in doc.select("li[onclick*=goTask]")) {
            val data = li.attr("data").trim()
            val title = li.select("p.overHidden2").text().trim()
            if (data.isEmpty() || title.isEmpty()) continue
            val params = queryParams(data)
            val detail = if (data.startsWith("//")) "https:$data" else data
            out += ChxWorkItem(
                title = title,
                status = li.select("p.status").text().trim(),
                detailUrl = detail,
                workId = params["workId"]?.toLongOrNull() ?: 0,
                answerId = params["answerId"]?.toLongOrNull() ?: 0,
                enc = params["enc"].orEmpty(),
                remainingText = li.select("div.time").text().trim(),
            )
        }
        return out
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

    private fun queryParams(url: String): Map<String, String> {
        val q = url.substringAfter('?', "")
        if (q.isEmpty()) return emptyMap()
        return q.split('&')
            .mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null
                else java.net.URLDecoder.decode(it.substring(0, i), "UTF-8") to
                    java.net.URLDecoder.decode(it.substring(i + 1), "UTF-8")
            }
            .toMap()
    }
}
