package com.menglimi.dormpower.power

import com.menglimi.power.PowerBinding
import com.menglimi.power.PowerDetail
import com.menglimi.power.PowerMonitorConfig
import com.menglimi.power.PowerMonitorState
import com.menglimi.power.PowerQueryPreset
import com.menglimi.power.PowerReading
import com.menglimi.power.PowerSample
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class PowerPersistenceCodecTest {
    private val binding = PowerBinding("synthetic-account", 123, "synthetic-verify")

    @Test fun roundTripPreservesCoreTypesAndTimesWithoutReflectingJavaTimeOrPair() {
        val now = 1_794_804_000_000L
        val snapshot = PowerSnapshot(
            config = PowerMonitorConfig(binding, PowerQueryPreset.CUSTOM, 45, 12.5, 3.5,
                LocalTime.of(7, 30) to LocalTime.of(23, 0), ZoneId.of("Asia/Shanghai")),
            roomName = "测试宿舍",
            monitor = PowerMonitorState(
                cachedBindingKey = binding.key,
                reading = PowerReading(PowerDetail(-0.5, 1.2, listOf("一" to 2.3, "二" to 3.4), 40.0, 200.0), now),
                lastSampleAt = now,
                lastAttemptAt = now - 100,
                samples = listOf(PowerSample(now - 900_000, 0.0), PowerSample(now, -0.5)),
                lowWarnAt = now - 10_000,
                dropWarnAt = now - 20_000,
            ),
            bindingRevision = 42,
        )
        assertEquals(snapshot, PowerPersistenceCodec.decode(PowerPersistenceCodec.encode(snapshot)))
    }

    @Test fun emptySnapshotRoundTripsWithoutInventingBindingOrWindow() {
        val snapshot = PowerSnapshot()
        assertEquals(snapshot, PowerPersistenceCodec.decode(PowerPersistenceCodec.encode(snapshot)))
    }

    @Test fun cacheFromAnotherBindingIsRejectedWithoutPublishingEmptyHistory() {
        val wrong = PowerSnapshot(
            config = PowerMonitorConfig(binding),
            roomName = "测试宿舍",
            monitor = PowerMonitorState(
                cachedBindingKey = "wrong-key",
                samples = listOf(PowerSample(1_000, 1.0)),
                lowWarnAt = 1_000,
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            PowerPersistenceCodec.decode(PowerPersistenceCodec.encode(wrong))
        }
    }

    @Test fun optionalInvalidReadingsAreNotSerializedAsNaNAndFiniteNegativeBalanceSurvives() {
        val snapshot = PowerSnapshot(
            config = PowerMonitorConfig(binding),
            monitor = PowerMonitorState(
                cachedBindingKey = binding.key,
                reading = PowerReading(PowerDetail(-1.0, Double.NaN, listOf("一" to Double.POSITIVE_INFINITY),
                    null, Double.NEGATIVE_INFINITY), 1_000),
                samples = listOf(PowerSample(1_000, -1.0), PowerSample(2_000, Double.NaN)),
            ),
        )
        val encoded = PowerPersistenceCodec.encode(snapshot)
        assertFalse(encoded.contains("NaN"))
        assertFalse(encoded.contains("Infinity"))
        val result = PowerPersistenceCodec.decode(encoded)
        val restored = requireNotNull(result.monitor.reading).detail
        assertEquals(-1.0, restored.odd!!, 0.0)
        assertNull(restored.todayUse)
        assertTrue(restored.weekUse.isEmpty())
        assertNull(restored.sumBuy)
        assertEquals(listOf(PowerSample(1_000, -1.0)), result.monitor.samples)
    }

    @Test fun damagedOptionalFieldsPreserveBindingBalanceAndValidHistoryEntries() {
        val text = """
            {
              "version":1,"bindingRevision":42,"roomName":"测试宿舍",
              "config":{
                "binding":{"account":"synthetic-account","code":123,"verify":"synthetic-verify"},
                "preset":"custom","interval":45,"lowThreshold":12.5,"dropThreshold":3.5,
                "zone":"Invalid/Zone","awakeStart":"07:30","awakeEnd":"not-a-time"
              },
              "monitor":{
                "cachedBindingKey":"${binding.key}","lastSampleAt":2000,"lastAttemptAt":2100,
                "lowWarnAt":1500,"dropWarnAt":1600,
                "samples":[
                  {"time":1000,"odd":40.9},null,false,[],
                  {"time":{},"odd":99},{"time":1500,"odd":true},
                  {"time":1600,"odd":"not-a-number"},{"time":2000,"odd":37.9}
                ],
                "reading":{
                  "updatedAt":2000,"odd":37.9,"todayUse":{},"monthUse":false,"sumBuy":200,
                  "weekUse":[
                    {"day":"一","use":1.2},null,[],true,
                    {"day":"二","use":{}},{"day":"三","use":false},
                    {"day":"四","use":-1},{"day":"五","use":"bad"},{"day":"六","use":2.3}
                  ]
                }
              }
            }
        """.trimIndent()

        val restored = PowerPersistenceCodec.decode(text)

        assertEquals(binding, restored.config.binding)
        assertEquals("测试宿舍", restored.roomName)
        assertEquals(42L, restored.bindingRevision)
        assertEquals(45, restored.config.customIntervalMinutes)
        assertEquals(12.5, restored.config.lowThreshold, 0.0)
        assertEquals(3.5, restored.config.dropRateThreshold, 0.0)
        assertEquals(ZoneId.systemDefault(), restored.config.zone)
        assertNull(restored.config.awakeWindow)
        assertEquals(listOf(PowerSample(1_000, 40.9), PowerSample(2_000, 37.9)), restored.monitor.samples)
        assertEquals(2_000L, restored.monitor.lastSampleAt)
        assertEquals(2_100L, restored.monitor.lastAttemptAt)
        assertEquals(1_500L, restored.monitor.lowWarnAt)
        assertEquals(1_600L, restored.monitor.dropWarnAt)
        val reading = requireNotNull(restored.monitor.reading)
        assertEquals(2_000L, reading.updatedAt)
        assertEquals(37.9, reading.detail.odd!!, 0.0)
        assertNull(reading.detail.todayUse)
        assertNull(reading.detail.monthUse)
        assertEquals(200.0, reading.detail.sumBuy!!, 0.0)
        assertEquals(listOf("一" to 1.2, "六" to 2.3), reading.detail.weekUse)
    }

    @Test fun invalidNumericTypesCannotInventZeroBalanceOrEraseValidSamples() {
        for (badNumber in listOf("true", "{}", "[]", "\"bad\"", "\"NaN\"", "\"Infinity\"")) {
            val text = """
                {
                  "version":1,"config":{
                    "binding":{"account":"synthetic-account","code":123,"verify":"synthetic-verify"},
                    "interval":$badNumber,"lowThreshold":$badNumber,"dropThreshold":$badNumber
                  },
                  "monitor":{
                    "cachedBindingKey":"${binding.key}","lastAttemptAt":$badNumber,
                    "samples":[{"time":1000,"odd":0},{"time":2000,"odd":$badNumber},
                               {"time":$badNumber,"odd":99},{"time":3000,"odd":-0.5}],
                    "reading":{"updatedAt":3000,"odd":$badNumber,"weekUse":[]}
                  }
                }
            """.trimIndent()

            val restored = PowerPersistenceCodec.decode(text)

            assertEquals(badNumber, binding, restored.config.binding)
            assertEquals(badNumber, 60, restored.config.customIntervalMinutes)
            assertEquals(badNumber, 20.0, restored.config.lowThreshold, 0.0)
            assertEquals(badNumber, 5.0, restored.config.dropRateThreshold, 0.0)
            assertNull(badNumber, restored.monitor.reading)
            assertEquals(badNumber, 0L, restored.monitor.lastAttemptAt)
            assertEquals(badNumber, listOf(PowerSample(1_000, 0.0), PowerSample(3_000, -0.5)), restored.monitor.samples)
        }
    }

    @Test fun incompleteOrInvalidAwakeWindowDoesNotPreventReadingTheSnapshot() {
        for (window in listOf(
            "\"awakeStart\":\"07:30\"",
            "\"awakeStart\":\"25:00\",\"awakeEnd\":\"23:00\"",
            "\"awakeStart\":{},\"awakeEnd\":false",
        )) {
            val restored = PowerPersistenceCodec.decode("""
                {"version":1,"config":{
                  "binding":{"account":"synthetic-account","code":123,"verify":"synthetic-verify"},
                  "zone":"Asia/Shanghai",$window
                },"monitor":{"cachedBindingKey":"${binding.key}",
                  "reading":{"updatedAt":1000,"odd":42,"weekUse":[]},
                  "samples":[{"time":1000,"odd":42}]}}
            """.trimIndent())

            assertEquals(window, binding, restored.config.binding)
            assertNull(window, restored.config.awakeWindow)
            assertEquals(window, ZoneId.of("Asia/Shanghai"), restored.config.zone)
            assertEquals(window, 42.0, restored.monitor.reading!!.detail.odd!!, 0.0)
            assertEquals(window, listOf(PowerSample(1_000, 42.0)), restored.monitor.samples)
        }
    }

    @Test fun missingIdentityOrMonitorCannotTurnExistingHistoryIntoWritableEmptySnapshot() {
        val valid = PowerPersistenceCodec.encode(PowerSnapshot(config = PowerMonitorConfig(binding),
            monitor = PowerMonitorState(binding.key, PowerReading(PowerDetail(42.0, null, emptyList(), null, null), 1_000),
                samples = listOf(PowerSample(1_000, 42.0)))))
        for (mutation in listOf<(com.google.gson.JsonObject) -> Unit>(
            { it.remove("monitor") },
            { it.addProperty("monitor", "invalid") },
            { it.getAsJsonObject("config").remove("binding") },
            { it.getAsJsonObject("monitor").addProperty("cachedBindingKey", false) },
        )) {
            val root = com.google.gson.JsonParser.parseString(valid).asJsonObject
            mutation(root)
            assertThrows(IllegalArgumentException::class.java) { PowerPersistenceCodec.decode(root.toString()) }
        }
    }

    @Test fun unknownVersionIsRejectedInsteadOfResettingCredentialsSilently() {
        assertThrows(IllegalArgumentException::class.java) { PowerPersistenceCodec.decode("{\"version\":2}") }
    }
}
