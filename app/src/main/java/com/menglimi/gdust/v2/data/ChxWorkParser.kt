package com.menglimi.gdust.v2.data

import com.google.gson.JsonParser
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
data class ChxWorkCourse(val courseId: Long, val classId: Long, val cpi: Long, val name: String)

/** 课程页隐藏域：各 tab 的签名与公共参数。 */
data class ChxWorkTabs(
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

    /**
     * 课程列表 → 每门课的 courseid/clazzid/cpi/课程名。
     *
     * 页面结构（HAR 实测）：每张课程卡是一个 `div.course`，卡片内自带
     * `input.courseId` / `input.clazzId` / `input.curPersonId` 隐藏域与 `.course-name`；
     * 置顶区（stuTopCourseListDiv）与普通区（stuNormalCourseListDiv）可能重复同一门课，
     * 按 courseid+classId 去重。
     */
    fun parseCourses(html: String): List<ChxWorkCourse> {
        val doc = Jsoup.parse(html)
        val out = mutableListOf<ChxWorkCourse>()
        val seen = mutableSetOf<Pair<Long, Long>>()
        for (card in doc.select("div.course")) {
            val courseId = card.select("input.courseId").attr("value").toLongOrNull() ?: continue
            val classId = card.select("input.clazzId").attr("value").toLongOrNull() ?: continue
            val key = courseId to classId
            if (!seen.add(key)) continue
            val name = card.select(".course-name").text().trim()
            if (name.isEmpty()) continue
            // cpi 优先取卡片里 stucoursemiddle 链接上的值，退化用 curPersonId
            val link = card.select("a[href*=stucoursemiddle]").first()
            val cpi = link?.let { queryParams(it.attr("href"))["cpi"]?.toLongOrNull() }
                ?: card.select("input.curPersonId").attr("value").toLongOrNull()
                ?: 0
            out += ChxWorkCourse(courseId, classId, cpi, name)
        }
        return out
    }

    /**
     * 课程页 → 各 tab 的签名；缺 enc/workEnc 视为结构变化，返回 null 由调用方跳过。
     * 课程页是 138KB 的大页面、只需要 6 个隐藏域：正则直抽比 Jsoup 全量解析快一个量级。
     */
    fun parseCourseTabs(html: String): ChxWorkTabs? {
        fun v(id: String): String =
            Regex("<input[^>]*id=\"$id\"[^>]*value=\"([^\"]*)\"").find(html)
                ?.groupValues?.get(1)?.trim().orEmpty()
        val enc = v("enc")
        val workEnc = v("workEnc")
        if (enc.isEmpty() || workEnc.isEmpty()) return null
        return ChxWorkTabs(
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

    /**
     * stu-job-info（批量作业计数）→ clazzId → (jobCount, jobFinishCount)。
     * jobRate = finish/count，是班级聚合值：只有 finish >= count（全班含本人都完成）才可安全跳过。
     */
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
