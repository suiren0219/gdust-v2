package com.menglimi.gdust.v2.data

/** Built-in university study guidance, distilled from public university learning-center resources. */
object UniversityKnowledge {
    const val systemPrompt = """你是广东科技学院学生的大学学习助手，回答简体中文、清晰、友善、务实。
        优先基于用户问题、当前课表/待办上下文和下方高校学习建议；区分已知事实与推测，信息不足时先简短追问，不编造课程政策、成绩、学校办事流程或截止日期。
        擅长技能：
        1. 任务拆解：把大作业变成可执行的小步骤，指出下一步、预估时间和需要确认的信息；不代写可直接提交的作业。
        2. 周计划：结合课表和截止日期，排出有缓冲的可调整计划；不把没有截止时间的事项误说成临近截止。
        3. 复习辅导：用主动回忆、间隔复习、交错练习和自测来设计复习方案；先帮助理解，不替用户参加考试。
        4. 学术规范：帮助理解题目、构思提纲、检查表达和引用；不捏造文献、数据或引文，不协助抄袭、作弊或伪造实验结果。
        5. 求助沟通：必要时建议向任课教师、助教、学校官方部门或可靠支持资源确认；不冒充校方权威。
        当前本地数据只是可能不完整的导入快照；若用户指出与实际不符，优先相信用户并说明数据范围。

        高校学习建议：
        - Cornell Learning Strategies Center 建议使用主动回忆（合上材料先回忆/自测）、间隔练习（分散多次复习）和交错练习（穿插相关主题），并用学习计划分配时间。
        - 备考先核对考试时间、地点、形式和要求；使用练习题后复盘错误与策略，而不是只重复阅读。
        - 时间安排可以同时查看学期大日历和每周计划，将大任务拆小，并给变动留余量。
        
        参考来源：
        Cornell Learning Strategies Center, Effective Study Strategies:
        https://lsc.cornell.edu/how-to-study/studying-for-and-taking-exams/effective-study-strategies/
        Cornell Learning Strategies Center, Time Management:
        https://lsc.cornell.edu/how-to-study/time-management/
        Cornell Learning Strategies Center, Studying for and Taking Exams:
        https://lsc.cornell.edu/how-to-study/studying-for-and-taking-exams/
        广东科技学院心理辅导中心公开页面（需确认联系方式及开放时间，以官网最新信息为准）：
        https://www.gdust.edu.cn/xlfd/"""
}

private const val MAX_CONTEXT_CHARS = 8000
private const val COURSE_SECTION_CHAR_BUDGET = 3900
private const val TODO_SECTION_CHAR_BUDGET = 3600
private const val MAX_COURSE_NAME_CHARS = 100
private const val MAX_COURSE_ROOM_CHARS = 80
private const val MAX_TODO_TEXT_CHARS = 180
private const val MAX_TODO_COURSE_CHARS = 100
private val externalWhitespace = Regex("[\\p{Z}\\p{Cc}]+")

/** Builds a bounded, privacy-conscious snapshot from already imported local data only. */
fun buildStudentContext(
    calendar: CalendarInfo?,
    blocks: List<CourseBlock>,
    todos: List<Todo>,
    nowMillis: Long,
): String {
    val out = StringBuilder("当前时间：${java.time.Instant.ofEpochMilli(nowMillis).atZone(java.time.ZoneId.systemDefault())}\n")
    if (calendar == null) {
        out.append("课表：本地没有已导入的学期课表。\n")
    } else {
        val startDate = boundedExternalText(calendar.startDate, 32)
        out.append("学期：${boundedExternalText(calendar.year, 32)} ${boundedExternalText(calendar.semester, 32)}；学期起始日 $startDate；总周数 ${calendar.allWeek}。\n")
        val week = runCatching {
            val start = java.time.LocalDate.parse(calendar.startDate)
            Math.floorDiv(java.time.temporal.ChronoUnit.DAYS.between(
                start,
                java.time.Instant.ofEpochMilli(nowMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
            ), 7L).toInt() + 1
        }.getOrNull()
        if (week != null) out.append("按本地校历推算当前第 $week 周。\n")
        val weekBlocks = blocks.filter { week == null || it.week == week }
            .sortedWith(compareBy<CourseBlock> { it.day }.thenBy { it.startSection })
        val courseBudget = minOf(
            COURSE_SECTION_CHAR_BUDGET,
            (MAX_CONTEXT_CHARS - out.length - TODO_SECTION_CHAR_BUDGET).coerceAtLeast(0),
        )
        appendBoundedSection(
            out = out,
            title = "本周课程（最多 40 条）：",
            emptyText = "本地课表中没有匹配到课程。\n",
            entries = weekBlocks.take(40).map(::formatCourse),
            totalCount = weekBlocks.size,
            noun = "课程",
            charBudget = courseBudget,
        )
    }
    val pending = todos.asSequence().filterNot { it.done }.sortedWith(
        compareBy<Todo> { if (it.dueAt > 0L) it.dueAt else Long.MAX_VALUE }.thenBy { it.text },
    ).toList()
    val todoBudget = minOf(TODO_SECTION_CHAR_BUDGET, (MAX_CONTEXT_CHARS - out.length).coerceAtLeast(0))
    appendBoundedSection(
        out = out,
        title = "未完成待办（最多展示 30 条）：",
        emptyText = "本地列表为空；这不代表学习通远端没有作业，尤其在同步失败或尚未同步时。\n",
        entries = pending.take(30).map(::formatTodo),
        totalCount = pending.size,
        noun = "待办",
        charBudget = todoBudget,
    )
    return out.toString()
}

private fun boundedExternalText(value: String, maxChars: Int): String {
    val cleaned = value.replace(externalWhitespace, " ").trim()
    if (cleaned.length <= maxChars) return cleaned
    return cleaned.take(maxChars - 1).trimEnd() + "…"
}

private fun formatCourse(block: CourseBlock): String {
    val dayName = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
        .getOrElse(block.day) { "第${block.day}天" }
    return buildString {
        append("- $dayName ${boundedExternalText(block.name, MAX_COURSE_NAME_CHARS)}，第 ${block.startSection}-${block.endSection} 节")
        val room = boundedExternalText(block.room, MAX_COURSE_ROOM_CHARS)
        if (room.isNotBlank()) append("，$room")
        append('\n')
    }
}

private fun formatTodo(todo: Todo): String = buildString {
    append("- [${todo.kind}] ${boundedExternalText(todo.text, MAX_TODO_TEXT_CHARS)}")
    val course = boundedExternalText(todo.course, MAX_TODO_COURSE_CHARS)
    if (course.isNotBlank()) append("（课程：$course）")
    if (todo.dueAt > 0L) {
        val due = java.time.Instant.ofEpochMilli(todo.dueAt).atZone(java.time.ZoneId.systemDefault())
        append("；截止 ${due.toLocalDate()} ${due.toLocalTime().withSecond(0).withNano(0)}")
    } else append("；未设置截止时间")
    append("；来源 ${if (todo.source == "chaoxing") "学习通本地同步" else "本地"}\n")
}

private fun appendBoundedSection(
    out: StringBuilder,
    title: String,
    emptyText: String,
    entries: List<String>,
    totalCount: Int,
    noun: String,
    charBudget: Int,
) {
    val section = StringBuilder("$title\n")
    if (totalCount == 0) {
        section.append(emptyText)
        out.append(section)
        return
    }

    var included = 0
    var usedChars = section.length
    for (entry in entries) {
        val omittedAfterEntry = totalCount - included - 1
        val omissionChars = if (omittedAfterEntry > 0) omissionNotice(omittedAfterEntry, noun).length else 0
        if (usedChars + entry.length + omissionChars > charBudget) break
        usedChars += entry.length
        included++
    }
    repeat(included) { section.append(entries[it]) }
    val omitted = totalCount - included
    if (omitted > 0) section.append(omissionNotice(omitted, noun))
    out.append(section)
}

private fun omissionNotice(count: Int, noun: String): String = "（另有 $count 条${noun}未展示。）\n"
