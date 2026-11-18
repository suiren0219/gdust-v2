package com.menglimi.study.learning

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.menglimi.chaoxing.ChxCourse
import java.time.LocalDate
import java.util.UUID

/** 校历学期的日期为首尾均包含；来源只描述校历，不保存门户登录态。 */
data class LearningTerm(val id: String, val name: String, val start: String, val end: String) {
    init {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "学期标识无效" }
        require(name.isNotBlank() && name.length <= 80) { "学期名称无效" }
        require(LocalDate.parse(start) <= LocalDate.parse(end)) { "校历结束日期早于开始日期" }
    }
    fun contains(date: String): Boolean = date >= start && date <= end
}

data class LearningFolder(val id: String, val name: String, val termId: String? = null)

enum class LearningScope { ALL, FOLDER, TERM, UNASSIGNED }

/** 手动归属独立于抓取结果；不使用首次发现时间、开课时间推断加入时间。 */
data class LearningLibrary(
    val folders: List<LearningFolder> = emptyList(),
    val assignments: Map<String, String> = emptyMap(),
    val terms: List<LearningTerm> = emptyList(),
    val scope: LearningScope = LearningScope.ALL,
    val selectedId: String? = null,
) {
    fun folder(courseKey: String): LearningFolder? = assignments[courseKey]?.let { id -> folders.find { it.id == id } }

    fun term(courseKey: String, joinedOn: String?): LearningTerm? {
        // 手动文件夹有独立语义：未关联学期的文件夹也不能被自动分类覆盖。
        folder(courseKey)?.let { manual -> return terms.find { it.id == manual.termId } }
        val date = joinedOn?.let { runCatching { LocalDate.parse(it).toString() }.getOrNull() } ?: return null
        return terms.singleOrNull { it.contains(date) }
    }

    fun includes(courseKey: String, joinedOn: String?): Boolean = when (scope) {
        LearningScope.ALL -> true
        LearningScope.FOLDER -> folder(courseKey)?.id == selectedId
        LearningScope.TERM -> term(courseKey, joinedOn)?.id == selectedId
        LearningScope.UNASSIGNED -> folder(courseKey) == null && term(courseKey, joinedOn) == null
    }

    val label: String get() = when (scope) {
        LearningScope.ALL -> "全部课程"
        LearningScope.FOLDER -> folders.find { it.id == selectedId }?.name ?: "全部课程"
        LearningScope.TERM -> terms.find { it.id == selectedId }?.name ?: "全部课程"
        LearningScope.UNASSIGNED -> "待确认课程"
    }

    fun createFolder(name: String, termId: String?): LearningLibrary {
        val title = name.trim()
        require(title.isNotEmpty() && title.length <= 30) { "文件夹名称需为 1–30 个字符" }
        require(folders.none { it.name == title }) { "已有同名文件夹" }
        require(termId == null || terms.any { it.id == termId }) { "请先导入对应校历" }
        require(folders.size < 100) { "文件夹数量已达上限" }
        return copy(folders = folders + LearningFolder(UUID.randomUUID().toString(), title, termId))
    }

    fun removeFolder(id: String): LearningLibrary = copy(
        folders = folders.filterNot { it.id == id },
        assignments = assignments.filterValues { it != id },
        scope = if (scope == LearningScope.FOLDER && selectedId == id) LearningScope.ALL else scope,
        selectedId = if (scope == LearningScope.FOLDER && selectedId == id) null else selectedId,
    )

    fun move(courseKey: String, folderId: String?): LearningLibrary {
        require(courseKey.matches(Regex("[0-9]+:[0-9]+:[0-9]+"))) { "课程标识无效" }
        require(folderId == null || folders.any { it.id == folderId }) { "文件夹已不存在" }
        return copy(assignments = if (folderId == null) assignments - courseKey else assignments + (courseKey to folderId))
    }

    fun select(scope: LearningScope, id: String?): LearningLibrary {
        require(when (scope) {
            LearningScope.FOLDER -> folders.any { it.id == id }
            LearningScope.TERM -> terms.any { it.id == id }
            else -> id == null
        }) { "选择的课程范围已不存在" }
        return copy(scope = scope, selectedId = id)
    }

    fun withTerm(term: LearningTerm): LearningLibrary {
        val others = terms.filterNot { it.id == term.id }
        require(others.none { term.start <= it.end && term.end >= it.start }) { "校历学期区间重叠，请核对日期" }
        require(others.size < 40) { "校历数量已达上限" }
        return copy(terms = (others + term).sortedByDescending { it.start })
    }
}

internal fun LearningLibrary.visible(courses: List<ChxCourse>): List<ChxCourse> =
    courses.filter { includes(it.libraryKey(), it.joinedOn) }

/** 每门课只给出一句依据：手动优先，其次加入日期匹配校历，其余一律待确认。 */
fun LearningLibrary.explanation(course: ChxCourse): String {
    folder(course.libraryKey())?.let { folder ->
        return "手动 · ${folder.name}" + term(course.libraryKey(), course.joinedOn)?.let { " · ${it.name}" }.orEmpty()
    }
    term(course.libraryKey(), course.joinedOn)?.let { return "自动 · ${it.name} · 加入日期 ${course.joinedOn}" }
    return when {
        course.joinedOn == null -> "待确认 · 学习通未提供加入时间"
        terms.isEmpty() -> "待确认 · 尚未导入校历"
        else -> "待确认 · 加入日期未匹配校历"
    }
}

internal fun ChxCourse.libraryKey(): String = "$courseId:$clazzId:$curPersonId"

/** 读取主应用备份里的校历；其他字段（含可能的绑定配置）全部忽略，不转存。 */
internal object LearningCalendarImport {
    fun parse(text: String): LearningTerm = try {
        parseTerm(text)
    } catch (failure: IllegalArgumentException) {
        throw failure
    } catch (failure: RuntimeException) {
        throw IllegalArgumentException("无法识别校历文件，请选择主应用导出的备份。", failure)
    }

    private fun parseTerm(text: String): LearningTerm {
        require(text.length <= 4 * 1024 * 1024) { "校历文件过大" }
        val root = JsonParser.parseString(text).asJsonObject
        require(root.get("app")?.asString == "gdust-v2" && root.get("schema")?.asInt == 1) { "请选择主应用导出的备份文件" }
        val raw = root.get("calendar")?.takeUnless { it.isJsonNull } ?: throw IllegalArgumentException("备份里没有校历，请先在主应用同步校历")
        val calendar = if (raw.isJsonObject) raw.asJsonObject else JsonParser.parseString(raw.asString).asJsonObject
        val start = LocalDate.parse(root.optionalText("term_start")?.takeIf { it.isNotBlank() }
            ?: calendar.get("startDate").asString)
        val weeks = root.get("term_weeks")?.asInt?.takeIf { it != 0 } ?: calendar.get("allWeek").asInt
        require(weeks in 1..60) { "校历总周数无效" }
        val year = calendar.get("year").asString
        val semester = calendar.get("semester").asString
        require(year.matches(Regex("[0-9]{4}")) && semester.matches(Regex("[0-9]{1,2}"))) { "校历学年或学期无效" }
        return LearningTerm("${year}_$semester", "$year-${year.toInt() + 1} · 学期 $semester",
            start.toString(), start.plusWeeks(weeks.toLong()).minusDays(1).toString())
    }
}

internal fun JsonObject.optionalText(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString
