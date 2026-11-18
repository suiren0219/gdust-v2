package com.menglimi.study.learning

import com.google.gson.JsonArray
import com.google.gson.JsonObject

internal object LearningLibraryCodec {
    fun encode(library: LearningLibrary) = JsonObject().apply {
        addProperty("scope", library.scope.name)
        addProperty("selectedId", library.selectedId)
        add("folders", JsonArray().apply {
            library.folders.forEach { folder -> add(JsonObject().apply {
                addProperty("id", folder.id)
                addProperty("name", folder.name)
                addProperty("termId", folder.termId)
            }) }
        })
        add("assignments", JsonObject().apply { library.assignments.forEach { (key, id) -> addProperty(key, id) } })
        add("terms", JsonArray().apply {
            library.terms.forEach { term -> add(JsonObject().apply {
                addProperty("id", term.id)
                addProperty("name", term.name)
                addProperty("start", term.start)
            }) }
        })
    }

    fun decode(value: JsonObject?): LearningLibrary {
        if (value == null) return LearningLibrary()
        val folders = value.arrayOrEmpty("folders").map { entry ->
            val row = entry.asJsonObject
            LearningFolder(row.get("id").asString, row.get("name").asString, row.optionalText("termId")).also {
                require(it.id.matches(Regex("[A-Za-z0-9_-]{1,80}")))
                require(it.name.isNotBlank() && it.name.length <= 30)
            }
        }
        require(folders.size <= 100 && folders.distinctBy { it.id }.size == folders.size)
        require(folders.distinctBy { it.name }.size == folders.size)
        var library = LearningLibrary()
        value.arrayOrEmpty("terms").forEach { entry ->
            val row = entry.asJsonObject
            // 旧版缓存带有 end 字段；新模型只保留个人设置的开学日，忽略旧的推算结束日期。
            val term = LearningTerm(row.get("id").asString, row.get("name").asString, row.get("start").asString)
            require(library.terms.none { it.id == term.id })
            library = library.withTerm(term)
        }
        require(folders.all { it.termId == null || library.terms.any { term -> term.id == it.termId } })
        val assignments = (value.get("assignments") as? JsonObject ?: JsonObject())
            .entrySet().associate { (key, id) ->
                require(key.matches(Regex("[0-9]+:[0-9]+:[0-9]+")))
                key to id.asString.also { folderId -> require(folders.any { it.id == folderId }) }
            }
        require(assignments.size <= 10_000)
        return library.copy(folders = folders, assignments = assignments)
            .select(LearningScope.valueOf(value.get("scope")?.asString ?: LearningScope.ALL.name),
                value.optionalText("selectedId"))
    }

    private fun JsonObject.arrayOrEmpty(name: String): JsonArray =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
}
