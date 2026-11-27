package com.menglimi.gdust.v2.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.math.BigDecimal

/** MCP 输入不接受重复字段、宽松 JSON、过深结构或隐式数字/布尔转换。 */
internal object McpJson {
    fun objectFrom(text: String, maxBytes: Int = 1024 * 1024): JsonObject {
        require(text.toByteArray(Charsets.UTF_8).size <= maxBytes) { "JSON 超出大小限制" }
        var nodes = 0
        JsonReader(StringReader(text)).use { reader ->
            reader.strictness = Strictness.STRICT
            fun read(depth: Int): JsonElement {
                require(depth <= 24 && ++nodes <= 100_000) { "JSON 结构超出限制" }
                return when (reader.peek()) {
                    JsonToken.BEGIN_OBJECT -> JsonObject().apply {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val key = reader.nextName()
                            require(!has(key)) { "JSON 包含重复字段" }
                            add(key, read(depth + 1))
                        }
                        reader.endObject()
                    }
                    JsonToken.BEGIN_ARRAY -> JsonArray().apply {
                        reader.beginArray()
                        while (reader.hasNext()) add(read(depth + 1))
                        reader.endArray()
                    }
                    JsonToken.STRING -> JsonPrimitive(reader.nextString())
                    JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
                    JsonToken.NUMBER -> {
                        val number = reader.nextString()
                        require(number.length <= 128) { "JSON 数值过长" }
                        val decimal = BigDecimal(number)
                        require(kotlin.math.abs(decimal.scale().toLong()) <= 1024) { "JSON 数值超出限制" }
                        JsonPrimitive(decimal)
                    }
                    JsonToken.NULL -> { reader.nextNull(); JsonNull.INSTANCE }
                    else -> error("JSON 格式无效")
                }
            }
            val root = read(0)
            require(root.isJsonObject && reader.peek() == JsonToken.END_DOCUMENT) { "JSON 必须是单个对象" }
            return root.asJsonObject
        }
    }

    fun text(json: JsonObject, key: String, max: Int = 4096): String {
        val value = json[key]
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) { "字符串字段无效" }
        return value.asString.also { require(it.length <= max) { "字符串字段过长" } }
    }

    fun long(json: JsonObject, key: String): Long {
        val value = json[key]
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "整数字段无效" }
        return try { value.asBigDecimal.longValueExact() } catch (_: ArithmeticException) {
            throw IllegalArgumentException("整数字段超出范围")
        }
    }

    fun int(json: JsonObject, key: String, range: IntRange): Int = long(json, key).also {
        require(it in range.first.toLong()..range.last.toLong()) { "整数字段超出范围" }
    }.toInt()

    fun bool(json: JsonObject, key: String): Boolean {
        val value = json[key]
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "布尔字段无效" }
        return value.asBoolean
    }
}
