package com.menglimi.gdust.v2.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.URISyntaxException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.time.temporal.ChronoField
import java.util.Locale

/** 门户通知仍使用钉钉接口；教务处与官网新闻使用门户 news/list。 */
enum class NoticeSource { PORTAL, ACADEMIC, CAMPUS }

internal val NoticeSource.noticeLabel: String
    get() = when (this) {
        NoticeSource.PORTAL -> "门户通知"
        NoticeSource.ACADEMIC -> "教务处公告"
        NoticeSource.CAMPUS -> "官网新闻"
    }

/** home 已有成功响应证据；jiaowu 仅有前端配置，仍待在线验证。 */
internal object PortalNews {
    private val articleDirectory = Regex("article/[0-9]+")
    private val articleId = Regex("[0-9]+")
    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val dateFormat = DateTimeFormatterBuilder()
        .appendPattern("uuuu-MM-dd HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
        .toFormatter(Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)

    fun requestUrl(source: NoticeSource): String {
        val channel = when (source) {
            NoticeSource.ACADEMIC -> "jiaowu"
            NoticeSource.CAMPUS -> "home"
            NoticeSource.PORTAL -> error("门户通知请使用 notices 接口")
        }
        return "https://portal.gdust.edu.cn/smart-admin-api/app/news/list?source=$channel"
    }

    fun parse(body: String, source: NoticeSource): List<Notice> {
        val prefix = when (source) {
            NoticeSource.ACADEMIC -> "https://www.gdust.edu.cn/jwc/"
            NoticeSource.CAMPUS -> "https://www.gdust.edu.cn/"
            NoticeSource.PORTAL -> error("门户通知请使用 notices 接口")
        }
        val label = source.noticeLabel
        return noticeArray(body, label, setOf(1)).map { element ->
            check(element.isJsonObject) { "$label 响应条目必须是对象" }
            val row = element.asJsonObject
            val id = row.noticeString("id", required = true)!!
            val directory = row.noticeString("staticDir", required = true)!!
            check(articleId.matches(id) && articleDirectory.matches(directory)) {
                "$label 原文路径格式异常"
            }
            Notice(
                title = row.noticeString("title", required = true)!!.also {
                    check(it.isNotBlank()) { "$label 标题不能为空" }
                },
                // 只使用固定栏目及已观察到的数字路径，忽略响应中的 url 等任意链接。
                url = "$prefix$directory/$id.html",
                type = label,
                issuer = if (source == NoticeSource.ACADEMIC) "教务处" else "学校官网",
                time = parseDate(row.noticeString("thedate")),
            )
        }
    }

    private fun parseDate(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return try {
            LocalDateTime.parse(raw, dateFormat).atZone(shanghai).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            error("新闻发布时间格式异常")
        }
    }
}

/** 缺 data 或业务失败不能伪装成「暂无公告」；不按通用列表 URL 合并条目。 */
internal fun parsePortalNotices(body: String): List<Notice> =
    noticeArray(body, NoticeSource.PORTAL.noticeLabel, setOf(0, 1)).map { element ->
        check(element.isJsonObject) { "门户通知响应条目必须是对象" }
        val row = element.asJsonObject
        val timestamp = row.get("gmtCreate")?.takeUnless { it.isJsonNull }
        val time = if (timestamp == null) 0L else {
            check(timestamp.isJsonPrimitive && timestamp.asJsonPrimitive.isNumber) {
                "门户通知发布时间必须是整数"
            }
            timestamp.asString.toLongOrNull()?.takeIf { it >= 0L }
                ?: error("门户通知发布时间格式异常")
        }
        Notice(
            title = row.noticeString("title", required = true)!!.also {
                check(it.isNotBlank()) { "门户通知标题不能为空" }
            },
            url = row.noticeString("url", required = true)!!,
            type = row.noticeString("announcementType")?.takeIf { it.isNotBlank() } ?: "门户通知",
            issuer = row.noticeString("issuer"),
            time = time,
        )
    }

private fun noticeArray(body: String, label: String, successCodes: Set<Int>): JsonArray {
    schoolAuthFailure(200, body)?.let { throw it }
    val root = try {
        JsonParser.parseString(body)
    } catch (_: RuntimeException) {
        error("$label 响应不是有效 JSON")
    }
    check(root.isJsonObject) { "$label 响应必须是对象" }
    val envelope = root.asJsonObject
    val code = envelope.get("code")
    check(code != null && code.isJsonPrimitive && code.asJsonPrimitive.isNumber &&
        code.asString.toIntOrNull() in successCodes) { "$label 返回失败或状态码异常" }
    val success = envelope.get("success")
    check(success != null && success.isJsonPrimitive && success.asJsonPrimitive.isBoolean && success.asBoolean) {
        "$label 返回失败或缺少成功标志"
    }
    val data = envelope.get("data")
    check(data != null && data.isJsonArray) { "$label 响应 data 必须是数组" }
    return data.asJsonArray
}

private fun JsonObject.noticeString(key: String, required: Boolean = false): String? {
    val value = get(key)?.takeUnless { it.isJsonNull }
    if (value == null) {
        check(!required) { "公告响应缺少 $key" }
        return null
    }
    check(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "公告响应 $key 必须是字符串" }
    return value.asString
}

/** 浏览器仅接受正常网页地址；拒绝非网页协议、相对路径及 userinfo 伪装主机。 */
internal fun noticeWebUrl(raw: String): String? {
    if (raw.isBlank() || raw.any { it.isWhitespace() || it.isISOControl() }) return null
    val uri = try {
        URI(raw)
    } catch (_: URISyntaxException) {
        return null
    }
    if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https") || uri.isOpaque ||
        uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
        (uri.port != -1 && uri.port !in 1..65535)) return null
    return uri.toASCIIString()
}
