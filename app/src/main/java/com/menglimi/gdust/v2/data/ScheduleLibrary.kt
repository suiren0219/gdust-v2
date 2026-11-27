package com.menglimi.gdust.v2.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.UUID

internal data class IndependentSchedule(val id: String, val name: String, val revision: Long,
    val savedAt: Long, val schedule: ScheduleShareCodec.ParsedSchedule)
internal data class ScheduleCatalog(val selectedId: String = SCHOOL, val schedules: List<IndependentSchedule> = emptyList()) {
    val selected: IndependentSchedule? get() = schedules.firstOrNull { it.id == selectedId }
    companion object { const val SCHOOL = "school" }
}

/** 单条原子写入：课程、作息/学期 JSON 与选中 id 一起保存；绝不写学校缓存键。 */
internal class ScheduleLibrary(private val read: () -> String?, private val write: (String) -> Unit) {
    private var cachedText: String? = null
    private var cachedCatalog: ScheduleCatalog? = null

    fun snapshot(): ScheduleCatalog = synchronized(lock) {
        val text = read() ?: return@synchronized ScheduleCatalog().also { cachedText = null; cachedCatalog = it }
        if (text == cachedText) cachedCatalog?.let { return@synchronized it }
        val root = McpJson.objectFrom(text, MAX_BYTES)
        require(McpJson.int(root, "version", 1..1) == 1)
        val list = root.getAsJsonArray("schedules") ?: error("独立课表数据损坏")
        require(list.size() <= 20)
        val schedules = list.map { item ->
            val row = item.asJsonObject
            val id = McpJson.text(row, "id", 80)
            require(id.isNotBlank() && id != ScheduleCatalog.SCHOOL)
            IndependentSchedule(id, McpJson.text(row, "name", 128), McpJson.long(row, "revision"), McpJson.long(row, "savedAt"),
                ScheduleShareCodec.parse(McpJson.text(row, "document", 2 * 1024 * 1024)))
        }
        require(schedules.map { it.id }.distinct().size == schedules.size)
        val selected = McpJson.text(root, "selectedId", 80)
        require(selected == ScheduleCatalog.SCHOOL || schedules.any { it.id == selected })
        ScheduleCatalog(selected, schedules).also { cachedText = text; cachedCatalog = it }
    }

    fun import(document: String, revision: Long, now: Long = System.currentTimeMillis()): IndependentSchedule = synchronized(lock) {
        val parsed = ScheduleShareCodec.parse(document)
        require(revision >= 0)
        val before = snapshot()
        require(before.schedules.size < 20) { "独立课表数量已达上限" }
        val used = before.schedules.map { it.name }.toSet() + "学校课表"
        var name = parsed.name
        var index = 2
        while (name in used) name = "${parsed.name.take(116)} · ${index++}"
        val item = IndependentSchedule(UUID.randomUUID().toString(), name, revision, now, parsed)
        persist(ScheduleCatalog(item.id, before.schedules + item))
        item
    }

    fun select(id: String) = synchronized(lock) {
        val before = snapshot()
        require(id == ScheduleCatalog.SCHOOL || before.schedules.any { it.id == id }) { "课表不存在" }
        if (before.selectedId != id) persist(before.copy(selectedId = id))
    }

    private fun persist(catalog: ScheduleCatalog) {
        val text = JsonObject().apply {
            addProperty("version", 1); addProperty("selectedId", catalog.selectedId)
            add("schedules", JsonArray().apply {
                catalog.schedules.forEach { item -> add(JsonObject().apply {
                    addProperty("id", item.id); addProperty("name", item.name); addProperty("revision", item.revision)
                    addProperty("savedAt", item.savedAt); addProperty("document", item.schedule.document)
                }) }
            })
        }.toString()
        require(text.toByteArray().size <= MAX_BYTES) { "独立课表总大小超限" }
        write(text)
        cachedText = text
        cachedCatalog = catalog
    }

    companion object {
        const val KEY = "schedule_library_v1"
        private const val MAX_BYTES = 8 * 1024 * 1024
        private val lock = Any()
    }
}
