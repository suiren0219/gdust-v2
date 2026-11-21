package com.menglimi.dormpower.power

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.menglimi.power.PowerBinding
import com.menglimi.power.PowerDetail
import com.menglimi.power.PowerMonitorConfig
import com.menglimi.power.PowerMonitorState
import com.menglimi.power.PowerQueryPreset
import com.menglimi.power.PowerReading
import com.menglimi.power.PowerSample
import java.time.LocalTime
import java.time.ZoneId

/** 显式字段读写，不反射 core/Pair/java.time；R8 不需要保留这些模型的字段名。 */
internal object PowerPersistenceCodec {
    fun encode(snapshot: PowerSnapshot): String = JsonObject().apply {
        addProperty("version", 1)
        addProperty("bindingRevision", snapshot.bindingRevision)
        addProperty("roomName", snapshot.roomName)
        add("config", JsonObject().apply {
            val config = snapshot.config
            config.binding?.let { binding ->
                add("binding", JsonObject().apply {
                    addProperty("account", binding.account)
                    addProperty("code", binding.code)
                    addProperty("verify", binding.verify)
                })
            }
            addProperty("preset", config.preset.key)
            addProperty("interval", config.customIntervalMinutes)
            addProperty("lowThreshold", config.effectiveLowThreshold)
            addProperty("dropThreshold", config.effectiveDropRateThreshold)
            addProperty("zone", config.zone.id)
            config.awakeWindow?.let {
                addProperty("awakeStart", it.first.toString())
                addProperty("awakeEnd", it.second.toString())
            }
        })
        add("monitor", JsonObject().apply {
            val state = snapshot.monitor
            addProperty("cachedBindingKey", state.cachedBindingKey)
            addProperty("lastSampleAt", state.lastSampleAt)
            addProperty("lastAttemptAt", state.lastAttemptAt)
            addProperty("lowWarnAt", state.lowWarnAt)
            addProperty("dropWarnAt", state.dropWarnAt)
            add("samples", JsonArray().apply {
                state.samples.forEach { sample ->
                    add(JsonObject().apply {
                        addProperty("time", sample.time)
                        addNumber("odd", sample.odd)
                    })
                }
            })
            state.reading?.let { reading ->
                add("reading", JsonObject().apply {
                    addProperty("updatedAt", reading.updatedAt)
                    addNumber("odd", reading.detail.odd)
                    addNumber("todayUse", reading.detail.todayUse)
                    addNumber("monthUse", reading.detail.monthUse)
                    addNumber("sumBuy", reading.detail.sumBuy)
                    add("weekUse", JsonArray().apply {
                        reading.detail.weekUse.forEach { (day, use) ->
                            if (use.isFinite()) add(JsonObject().apply {
                                addProperty("day", day)
                                addProperty("use", use)
                            })
                        }
                    })
                })
            }
        })
    }.toString()

    fun decode(text: String): PowerSnapshot {
        val root = JsonParser.parseString(text).asJsonObject
        require(root.get("version")?.asInt == 1) { "电量数据版本不兼容" }
        val json = requireNotNull(root.value("config")?.objectOrNull()) { "电量配置数据无效" }
        val binding = json.value("binding")?.let { entry ->
            val value = requireNotNull(entry.objectOrNull()) { "宿舍绑定数据无效" }
            PowerBinding(value.text("account").orEmpty(), value.integer("code") ?: 0, value.text("verify").orEmpty())
                .also { require(it.isValid) { "宿舍绑定数据无效" } }
        }
        val config = PowerMonitorConfig(
            binding = binding,
            preset = PowerQueryPreset.fromKey(json.text("preset")),
            customIntervalMinutes = (json.integer("interval") ?: 60).coerceIn(15, 120),
            lowThreshold = json.number("lowThreshold")?.takeIf { it >= 0 } ?: 20.0,
            dropRateThreshold = json.number("dropThreshold")?.takeIf { it > 0 } ?: 5.0,
            awakeWindow = json.text("awakeStart")?.let { start ->
                runCatching { LocalTime.parse(start) to LocalTime.parse(json.text("awakeEnd")) }.getOrNull()
            },
            zone = json.text("zone")?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault(),
        )
        val monitor = requireNotNull(root.value("monitor")?.objectOrNull()) { "电量历史数据无效" }
        val key = monitor.text("cachedBindingKey")
        require(monitor.value("cachedBindingKey") == null || key != null) { "缓存身份数据无效" }
        require(binding == null || key == null || key == binding.key) { "缓存与宿舍绑定不匹配" }
        require(binding != null || key == null && monitor.value("reading") == null && monitor.entries("samples").isEmpty()) {
            "宿舍绑定缺失，原记录已保留"
        }
        val state = if (binding == null) PowerMonitorState() else {
            PowerMonitorState(
                cachedBindingKey = key,
                reading = monitor.value("reading")?.objectOrNull()?.let { reading ->
                    val odd = reading.number("odd") ?: return@let null
                    val updatedAt = reading.time("updatedAt").takeIf { it > 0 } ?: return@let null
                    PowerReading(PowerDetail(
                        odd = odd,
                        todayUse = reading.number("todayUse"),
                        weekUse = reading.entries("weekUse").mapNotNull { entry ->
                            val item = entry.objectOrNull() ?: return@mapNotNull null
                            val day = item.text("day") ?: return@mapNotNull null
                            val use = item.number("use")?.takeIf { it >= 0 } ?: return@mapNotNull null
                            day to use
                        },
                        monthUse = reading.number("monthUse"),
                        sumBuy = reading.number("sumBuy"),
                    ), updatedAt)
                }?.takeIf { key == binding.key },
                lastSampleAt = monitor.time("lastSampleAt"),
                lastAttemptAt = monitor.time("lastAttemptAt"),
                samples = monitor.entries("samples").mapNotNull { entry ->
                    val sample = entry.objectOrNull() ?: return@mapNotNull null
                    val time = sample.time("time").takeIf { it > 0 } ?: return@mapNotNull null
                    val odd = sample.number("odd") ?: return@mapNotNull null
                    PowerSample(time, odd)
                },
                lowWarnAt = monitor.time("lowWarnAt"),
                dropWarnAt = monitor.time("dropWarnAt"),
            )
        }
        return PowerSnapshot(config, if (binding == null) "" else root.text("roomName").orEmpty(), state,
            root.time("bindingRevision"))
    }

    private fun JsonObject.addNumber(key: String, value: Double?) {
        addProperty(key, value?.takeIf { it.isFinite() })
    }

    private fun JsonObject.value(key: String): JsonElement? = get(key)?.takeUnless { it.isJsonNull }
    private fun JsonElement.objectOrNull(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.entries(key: String): List<JsonElement> = value(key)
        ?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
    private fun JsonObject.text(key: String): String? = value(key)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    private fun JsonObject.number(key: String): Double? = value(key)
        ?.takeIf { it.isJsonPrimitive && !it.asJsonPrimitive.isBoolean }?.asString
        ?.toDoubleOrNull()?.takeIf { it.isFinite() }
    private fun JsonObject.integer(key: String): Int? = long(key)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
    private fun JsonObject.long(key: String): Long? = value(key)
        ?.takeIf { it.isJsonPrimitive && !it.asJsonPrimitive.isBoolean }?.asString?.toLongOrNull()
    private fun JsonObject.time(key: String): Long = long(key)?.coerceAtLeast(0L) ?: 0L
}
