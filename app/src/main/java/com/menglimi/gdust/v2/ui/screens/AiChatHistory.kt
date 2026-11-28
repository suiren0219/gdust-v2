package com.menglimi.gdust.v2.ui.screens

import com.google.gson.Gson
import com.google.gson.JsonParser

internal fun restoreAiChat(json: String?): List<AiChatLine> = runCatching {
    if (json.isNullOrBlank()) return@runCatching emptyList()
    JsonParser.parseString(json).asJsonArray.map { element ->
        val row = element.asJsonObject
        val id = row.get("id").asLong
        val role = row.get("role").asString
        val content = row.get("content").asString
        require(id > 0 && role in setOf("user", "assistant") && content.isNotBlank())
        AiChatLine(id, role, content)
    }.also { rows -> require(rows.map { it.id }.distinct().size == rows.size) }
}.getOrDefault(emptyList())

internal fun encodeAiChat(messages: List<AiChatLine>): String = Gson().toJson(messages)
