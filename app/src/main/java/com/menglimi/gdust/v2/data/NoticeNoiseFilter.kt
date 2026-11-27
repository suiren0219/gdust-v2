package com.menglimi.gdust.v2.data

import java.net.URI
import java.text.Normalizer
import java.util.Locale

/** 只做展示投影，原始公告与已读标识保持不变；办事、安全与教学事项优先保留。 */
internal object NoticeNoiseFilter {
    private val protectedTitle = Regex(
        "考试|考务|补考|缓考|测验|四六级|成绩|教务|教学安排|选课|调课|停课|学籍|注册|" +
            "缴费|交费|收费|退费|学费|住宿费|奖学金|助学|资助|贷款|报到|报名|办理|申请|申报|" +
            "认定|资格|就业|招聘|实习|毕业|证书|证明|停水|停电|供水|供电|断水|断电|" +
            "安全|紧急|防汛|台风|放假|返校|离校|宿舍|校园卡|医保|体检|接种|" +
            "通知|通告|公示|申诉|截止|复核|评选|征集|招募|志愿|招领|认领",
    )
    private val noiseTitle = listOf(
        Regex("(?:文艺晚会|文艺汇演|歌唱比赛|歌手大赛|舞蹈大赛|音乐节|演唱会|合唱展演|书画展|摄影展|艺术展|朗诵会).{0,24}(?:精彩回顾|圆满|落幕|成功举办)"),
        Regex("活动回顾|风采展示|文娱宣传|运动会(?:开幕式|闭幕式).{0,16}(?:圆满|举行|落幕)"),
        Regex("(?:校领导|学校领导|党委书记|校长|副校长|领导班子).{0,24}(?:调研|走访|视察|考察|座谈|出席|莅临)"),
        Regex("(?:召开|举行).{0,24}(?:工作会|专题会|推进会|部署会|总结会|座谈会|研讨会)|会议简报"),
    )

    fun project(notices: List<Notice>, source: NoticeSource = NoticeSource.CAMPUS): List<Notice> {
        val titles = mutableSetOf<Pair<String, Long>>()
        val urls = mutableSetOf<String>()
        return notices.sortedByDescending { it.time }.filter { notice ->
            val title = normalizedTitle(notice.title)
            if (source == NoticeSource.CAMPUS && !protectedTitle.containsMatchIn(title) &&
                noiseTitle.any { it.containsMatchIn(title) }) return@filter false
            // 同标题跨日期可能是重复提醒；只合并同日转载，日期未知单独一组。
            val titleKey = title to if (notice.time > 0L) notice.time / 86_400_000L else 0L
            val url = articleUrl(notice.url)
            val duplicate = titleKey in titles || (url != null && url in urls)
            titles += titleKey
            if (url != null) urls += url
            !duplicate
        }
    }

    private fun normalizedTitle(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFKC)
        .filterNot { it.isWhitespace() || Character.isSpaceChar(it) }.lowercase(Locale.ROOT)

    private fun articleUrl(raw: String): String? {
        val accepted = noticeWebUrl(raw) ?: return null
        val uri = runCatching { URI(accepted).normalize() }.getOrNull() ?: return null
        val path = uri.rawPath.orEmpty()
        val query = uri.rawQuery
        // 门户所有通知可能共用 listNotice 或栏目入口，不能据此误合并不同通知。
        if (path.lowercase(Locale.ROOT).contains("listnotice") ||
            path in setOf("", "/") ||
            (query.isNullOrBlank() && Regex("/(?:index|list|notice|news)(?:\\.[a-z]+)?/?$", RegexOption.IGNORE_CASE).containsMatchIn(path))) return null
        return buildString {
            append(uri.scheme.lowercase(Locale.ROOT)).append("://").append(uri.host.lowercase(Locale.ROOT))
            if (uri.port != -1 && !((uri.scheme.equals("https", true) && uri.port == 443) ||
                    (uri.scheme.equals("http", true) && uri.port == 80))) append(':').append(uri.port)
            append(path)
            if (!query.isNullOrBlank()) append('?').append(query)
        }
    }
}
