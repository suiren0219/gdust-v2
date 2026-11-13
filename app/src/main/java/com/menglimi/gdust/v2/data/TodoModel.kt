package com.menglimi.gdust.v2.data

/** 待办类型：普通事项 / 作业 / 考试。旧数据缺字段时归 GENERAL。 */
enum class TodoKind { GENERAL, HOMEWORK, EXAM }

/**
 * 结构化待办：新增字段全部带默认值，旧 JSON（只有 id/text/done）可直接反序列化，
 * 备份文件里的旧字符串也无需迁移。
 *
 * @param course 课程名（作业/考试所属课程，可空）
 * @param dueAt 截止或考试时刻（epoch ms，0 = 未设置）
 * @param source 来源标识（如 "manual" / "chaoxing"）
 */
data class Todo(
    val id: Long,
    val text: String,
    val done: Boolean,
    val kind: TodoKind = TodoKind.GENERAL,
    val course: String = "",
    val dueAt: Long = 0L,
    val source: String = "manual",
)

/** 待办分组：按完成状态 + 时间窗口；`course` 只用于组内小标题。 */
data class TodoGroups(
    val overdue: List<Todo> = emptyList(),
    val today: List<Todo> = emptyList(),
    val thisWeek: List<Todo> = emptyList(),
    val later: List<Todo> = emptyList(),
    val noDue: List<Todo> = emptyList(),
    val completed: List<Todo> = emptyList(),
) {
    val pendingCount: Int get() = overdue.size + today.size + thisWeek.size + later.size + noDue.size
}

/** 纯函数分组，便于 JUnit 测试；不依赖 Android 时间类。 */
fun groupTodos(items: List<Todo>, nowMillis: Long): TodoGroups {
    val overdue = mutableListOf<Todo>()
    val today = mutableListOf<Todo>()
    val week = mutableListOf<Todo>()
    val later = mutableListOf<Todo>()
    val noDue = mutableListOf<Todo>()
    val done = mutableListOf<Todo>()
    // 以本地日历日 midnight 为界（设备时区由调用方传入的 nowMillis 隐含）
    val zone = java.time.ZoneId.systemDefault()
    val startOfToday = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone)
        .toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    val startOfWeekEnd = startOfToday + 7L * 24 * 3600 * 1000
    val endOfToday = startOfToday + 24L * 3600 * 1000
    for (t in items) {
        when {
            t.done -> done += t
            t.dueAt <= 0L -> noDue += t
            t.dueAt < startOfToday -> overdue += t
            t.dueAt < endOfToday -> today += t
            t.dueAt < startOfWeekEnd -> week += t
            else -> later += t
        }
    }
    return TodoGroups(
        overdue = overdue.sortedBy { it.dueAt },
        today = today.sortedBy { it.dueAt },
        thisWeek = week.sortedBy { it.dueAt },
        later = later.sortedBy { it.dueAt },
        noDue = noDue,
        completed = done.sortedByDescending { it.id },
    )
}

/** 从学习通通知文本推断类型与课程名：不做复杂 NLP，只取最稳的信号。 */
fun classifyNotificationTodo(text: String): Pair<TodoKind, String> {
    val t = text.trim()
    val kind = when {
        Regex("考试|测验|机考|笔试").containsMatchIn(t) -> TodoKind.EXAM
        Regex("作业|任务|提交").containsMatchIn(t) -> TodoKind.HOMEWORK
        else -> TodoKind.GENERAL
    }
    // 常见格式：「[学习通] 课程名 · 新作业：...」/「课程名发布了新作业 ...」
    val course = Regex("^(\\[学习通\\]\\s*)?([^·：:，,。\\s]{2,20}?)(?=\\s*[·：:]|发布了|新作业|作业|考试|测验|截止|待提交)")
        .find(t)?.groupValues?.getOrNull(2).orEmpty()
        .removePrefix("[学习通]").trim()
        .takeIf { it.length in 2..20 && !it.startsWith("新") }.orEmpty()
    return kind to course
}

/**
 * Gson 兼容层：反射构造 Kotlin data class 不走默认参数，缺字段会产出 null。
 * 直接从 JsonObject 读可选字段，旧 JSON（id/text/done）因此可无损升级。
 */
fun parseTodos(json: String, gson: com.google.gson.Gson): List<Todo> {
    val arr = runCatching { com.google.gson.JsonParser.parseString(json).asJsonArray }
        .getOrNull() ?: return emptyList()
    return arr.mapNotNull { el ->
        val o = el.asJsonObject ?: return@mapNotNull null
        val id = o.get("id")?.asLong ?: return@mapNotNull null
        val text = o.get("text")?.asString ?: return@mapNotNull null
        val done = o.get("done")?.asBoolean ?: false
        // 兼容研究线早期字段：course/dueAt 曾叫 tag/due（学习通作业同步第一版）
        val course = (o.get("course") ?: o.get("tag"))?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        val dueAt = (o.get("dueAt") ?: o.get("due"))?.takeIf { !it.isJsonNull }?.asLong ?: 0L
        // 有课程来源但没有 kind 的旧数据：按「学习通作业」处理（当时只同步过作业）
        val inferredKind = if (o.get("kind") == null && course.isNotBlank()) TodoKind.HOMEWORK else TodoKind.GENERAL
        val kind = runCatching { TodoKind.valueOf(o.get("kind")?.asString.orEmpty()) }
            .getOrDefault(inferredKind)
        val source = o.get("source")?.asString ?: "manual"
        Todo(id, text, done, kind, course, dueAt, source)
    }
}
