package com.menglimi.chaoxing

import okhttp3.HttpUrl
import org.jsoup.Jsoup

/** 只读取课程页明确声明的导航地址，不执行脚本、不解析任意 JS 表达式。 */
internal object ChxCourseNavigation {
    private val scriptNavigation = Regex(
        """(?:(?:(?:window|top|self|parent)\s*\.\s*)?location(?:\s*\.\s*href)?\s*=\s*(["'])([^"'\r\n]+)\1\s*;?|(?:(?:window|top|self|parent)\s*\.\s*)?location\s*\.\s*(?:replace|assign)\s*\(\s*(["'])([^"'\r\n]+)\3\s*\)\s*;?)""",
    )
    private val coursePaths = setOf(
        "/visit/stucoursemiddle", "/mycourse/stu", "/mycourse/studentcourse",
        "/mooc-ans/mycourse/stu", "/mooc-ans/mycourse/stu.html", "/mooc-ans/mycourse/studentcourse",
        "/mooc2-ans/mycourse/stu", "/mooc2-ans/mycourse/studentcourse",
    )

    fun next(html: String, base: HttpUrl, course: ChxWorkCourse): HttpUrl? {
        val document = Jsoup.parse(html)
        document.select("template, noscript").remove()
        val declared = mutableListOf<String>()
        document.select("meta[http-equiv]").filter { it.attr("http-equiv").equals("refresh", true) }
            .forEach { meta ->
                Regex("(?i)^\\s*\\d+(?:\\.\\d+)?\\s*;\\s*url\\s*=\\s*(.+)$")
                    .matchEntire(meta.attr("content"))?.groupValues?.get(1)
                    ?.trim()?.removeSurrounding("\"")?.removeSurrounding("'")?.let(declared::add)
            }
        document.select("script:not([src])").forEach { script ->
            // 注释中的示例不能触发网络跳转。
            val body = script.data().replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
                .lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
            scriptNavigation.matchEntire(body.trim())?.let { match ->
                val raw = match.groupValues[2].ifEmpty { match.groupValues[4] }
                // 只解码 URL 字符串中的常见转义，不接受代码、模板或任意表达式。
                if (raw.contains('$') || raw.contains("\\x", true)) return@let
                val value = raw.replace("\\/", "/").replace("\\u0026", "&", true)
                    .replace("\\u003d", "=", true).replace("\\u003f", "?", true)
                declared += value
            }
        }
        val explicit = declared.map { resolve(base, it, course) }.distinct()
        if (explicit.isNotEmpty()) {
            if (explicit.any { it == null } || explicit.size != 1) {
                throw ChxCourseReadException("课程页导航地址不支持或存在冲突，已停止读取。")
            }
            return explicit.single()
        }
        val frames = document.select("iframe[src], frame[src]").mapNotNull {
            resolve(base, it.attr("src"), course)
        }.distinct()
        if (frames.size > 1) throw ChxCourseReadException("课程页包含多个不同正文入口，已停止读取。")
        return frames.singleOrNull()
    }

    private fun resolve(base: HttpUrl, value: String, course: ChxWorkCourse): HttpUrl? {
        val url = ChxApi.redirectTarget(base, value) ?: return null
        if (url.encodedPath !in coursePaths) return null
        val expected = mapOf("courseid" to course.courseId, "clazzid" to course.classId,
            "classid" to course.classId, "cpi" to course.cpi)
        url.queryParameterNames.forEach { key ->
            val id = expected[key.lowercase()] ?: return@forEach
            val values = url.queryParameterValues(key)
            if (values.size != 1 || values.single()?.toLongOrNull() != id) return null
        }
        return url
    }

    /** 固定结构提示，不包含标题、任意字段名、参数值、Cookie 或页面正文。 */
    fun describe(html: String): String {
        val document = Jsoup.parse(html)
        document.select("script, style, template").remove()
        val inputs = document.select("input")
        fun present(vararg names: String): Boolean = inputs.any { input -> names.any {
            it.equals(input.id(), true) || it.equals(input.attr("name"), true)
        } }
        return listOf(
            "courseid=${if (present("courseid")) "有" else "无"}",
            "enc=${if (present("enc")) "有" else "无"}",
            "workEnc=${if (present("workEnc")) "有" else "无"}",
            "框架=${document.select("iframe, frame").size}",
        ).joinToString("，")
    }
}
