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
        add("ledger", JsonObject().apply {
            addProperty("unitPrice", snapshot.ledger.unitPrice)
            add("entries", JsonArray().apply {
                snapshot.ledger.entries.forEach { entry -> add(JsonObject().apply {
                    addProperty("id", entry.id); addProperty("time", entry.time); addProperty("fromTime", entry.fromTime)
                    addNumber("kwh", entry.kwh); addProperty("price", entry.unitPrice)
                    addProperty("paid", entry.paidYuan); addProperty("payer", entry.payer); addProperty("note", entry.note)
                    addProperty("detected", entry.detected); addProperty("ignored", entry.ignored)
                }) }
            })
            add("days", JsonArray().apply {
                snapshot.ledger.days.forEach { day -> add(JsonObject().apply {
                    addProperty("date", day.date); addNumber("kwh", day.kwh); addProperty("yuan", day.yuan)
                    addProperty("intervals", day.intervals); addProperty("firstAt", day.firstAt); addProperty("lastAt", day.lastAt)
                }) }
            })
        })
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
        val json = root.getAsJsonObject("config")
        val binding = json.value("binding")?.asJsonObject?.let {
            PowerBinding(it.text("account").orEmpty(), it.get("code").asInt, it.text("verify").orEmpty())
                .also { value -> require(value.isValid) { "宿舍绑定数据无效" } }
        }
        val config = PowerMonitorConfig(
            binding = binding,
            preset = PowerQueryPreset.fromKey(json.text("preset")),
            customIntervalMinutes = (json.get("interval")?.asInt ?: 60).coerceIn(15, 120),
            lowThreshold = json.number("lowThreshold")?.takeIf { it >= 0 } ?: 20.0,
            dropRateThreshold = json.number("dropThreshold")?.takeIf { it > 0 } ?: 5.0,
            awakeWindow = json.text("awakeStart")?.let { start ->
                LocalTime.parse(start) to LocalTime.parse(json.text("awakeEnd"))
            },
            zone = json.text("zone")?.let(ZoneId::of) ?: ZoneId.systemDefault(),
        )
        val monitor = root.getAsJsonObject("monitor")
        val key = monitor.text("cachedBindingKey")
        val state = if (binding == null || (key != null && key != binding.key)) PowerMonitorState() else {
            PowerMonitorState(
                cachedBindingKey = key,
                reading = monitor.value("reading")?.asJsonObject?.let { reading ->
                    val odd = reading.number("odd") ?: return@let null
                    val updatedAt = reading.time("updatedAt").takeIf { it > 0 } ?: return@let null
                    PowerReading(PowerDetail(
                        odd = odd,
                        todayUse = reading.number("todayUse"),
                        weekUse = reading.getAsJsonArray("weekUse")?.mapNotNull { entry ->
                            val day = entry.asJsonObject.text("day") ?: return@mapNotNull null
                            val use = entry.asJsonObject.number("use") ?: return@mapNotNull null
                            day to use
                        }.orEmpty(),
                        monthUse = reading.number("monthUse"),
                        sumBuy = reading.number("sumBuy"),
                    ), updatedAt)
                }?.takeIf { key == binding.key },
                lastSampleAt = monitor.time("lastSampleAt"),
                lastAttemptAt = monitor.time("lastAttemptAt"),
                samples = monitor.getAsJsonArray("samples")?.mapNotNull { entry ->
                    val sample = entry.asJsonObject
                    val time = sample.time("time").takeIf { it > 0 } ?: return@mapNotNull null
                    val odd = sample.number("odd") ?: return@mapNotNull null
                    PowerSample(time, odd)
                }.orEmpty(),
                lowWarnAt = monitor.time("lowWarnAt"),
                dropWarnAt = monitor.time("dropWarnAt"),
            )
        }
        val ledgerJson = root.value("ledger")?.asJsonObject
        val ledger = ledgerJson?.let { data ->
            PowerLedgerState(
                unitPrice = data.text("unitPrice")?.takeIf { PowerLedger.price(it) != null } ?: "0.6",
                entries = data.getAsJsonArray("entries")?.map { value ->
                    val entry = value.asJsonObject
                    PowerLedgerEntry(entry.text("id").orEmpty(), entry.time("time"), entry.time("fromTime"),
                        requireNotNull(entry.number("kwh")), entry.text("price") ?: "0.6", entry.text("paid"),
                        entry.text("payer").orEmpty(), entry.text("note").orEmpty(),
                        entry.value("detected")?.asBoolean ?: false, entry.value("ignored")?.asBoolean ?: false)
                        .also(PowerLedger::validate)
                }.orEmpty(),
                days = data.getAsJsonArray("days")?.map { value ->
                    val day = value.asJsonObject
                    val date = java.time.LocalDate.parse(day.text("date")).toString()
                    val kwh = requireNotNull(day.number("kwh")).also { require(it >= 0) }
                    val yuan = requireNotNull(day.text("yuan")).also { require(it.toBigDecimal().signum() >= 0) }
                    PowerDayCost(date, kwh, yuan, day.get("intervals").asInt, day.time("firstAt"), day.time("lastAt"))
                }.orEmpty(),
            )
        } ?: PowerLedgerState()
        return PowerSnapshot(config, if (binding == null) "" else root.text("roomName").orEmpty(), state,
            root.time("bindingRevision"), if (binding == null) PowerLedgerState(ledger.unitPrice) else ledger)
    }

    private fun JsonObject.addNumber(key: String, value: Double?) {
        addProperty(key, value?.takeIf { it.isFinite() })
    }

    private fun JsonObject.value(key: String): JsonElement? = get(key)?.takeUnless { it.isJsonNull }
    private fun JsonObject.text(key: String): String? = value(key)?.asString
    private fun JsonObject.number(key: String): Double? = value(key)?.asDouble?.takeIf { it.isFinite() }
    private fun JsonObject.time(key: String): Long = (value(key)?.asLong ?: 0L).coerceAtLeast(0L)
}
