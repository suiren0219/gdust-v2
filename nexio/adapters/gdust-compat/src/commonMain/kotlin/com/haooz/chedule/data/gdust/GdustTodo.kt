package com.haooz.chedule.data.gdust

import com.haooz.chedule.data.storage.AppStorage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** 本地待办条目（考试/作业/事务）。 */
data class GdustTodoItem(
    val id: Long,
    val text: String,
    val done: Boolean = false,
    val createdAt: Long,
)

/**
 * 待办清单的存取（AppStorage KV + JSON 数组，commonMain 纯净实现）。
 * id 用时间戳毫秒 + 计数避免同毫秒撞车；不用 java.util.UUID（KMP purity）。
 */
object GdustTodoStore {

    private var idCounter = 0L

    private fun nextId(): Long {
        idCounter = (idCounter + 1) % 1000
        return kotlinx.datetime.Clock.System.now().toEpochMilliseconds() * 1000 + idCounter
    }

    fun load(): List<GdustTodoItem> {
        val raw = AppStorage.store("gdust_todo").getString("items", "").ifEmpty { return emptyList() }
        return runCatching {
            Json.parseToJsonElement(raw).jsonArray.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                GdustTodoItem(
                    id = o["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null,
                    text = o["text"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    done = o["done"]?.jsonPrimitive?.content == "true",
                    createdAt = o["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(items: List<GdustTodoItem>) {
        val arr = buildJsonArray {
            items.forEach { item ->
                add(buildJsonObject {
                    put("id", item.id)
                    put("text", item.text)
                    put("done", item.done.toString())
                    put("createdAt", item.createdAt)
                })
            }
        }
        AppStorage.store("gdust_todo").edit { putString("items", arr.toString()) }
    }

    fun add(text: String): List<GdustTodoItem> {
        val list = load().toMutableList()
        list.add(0, GdustTodoItem(id = nextId(), text = text, createdAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()))
        save(list)
        return list
    }

    fun toggle(id: Long): List<GdustTodoItem> {
        val list = load().map { if (it.id == id) it.copy(done = !it.done) else it }
        save(list)
        return list
    }

    fun remove(id: Long): List<GdustTodoItem> {
        val list = load().filter { it.id != id }
        save(list)
        return list
    }

    fun clearDone(): List<GdustTodoItem> {
        val list = load().filter { !it.done }
        save(list)
        return list
    }
}

/** 兼容旧版调用点的薄壳（JSON 编解码）。 */
object GdustTodoCodec {
    fun encode(items: List<GdustTodoItem>): String = buildJsonArray {
        items.forEach {
            add(buildJsonObject {
                put("id", it.id)
                put("text", it.text)
                put("done", it.done.toString())
                put("createdAt", it.createdAt)
            })
        }
    }.toString()

    fun decode(raw: String?): List<GdustTodoItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            Json.parseToJsonElement(raw).jsonArray.mapNotNull { el ->
                val o = el.jsonObject
                GdustTodoItem(
                    id = o["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null,
                    text = o["text"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    done = o["done"]?.jsonPrimitive?.content == "true",
                    createdAt = o["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                )
            }
        }.getOrDefault(emptyList())
    }
}
