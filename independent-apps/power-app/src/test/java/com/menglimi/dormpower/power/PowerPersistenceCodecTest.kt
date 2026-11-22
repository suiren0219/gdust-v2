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

    @Test fun cacheFromAnotherBindingIsDroppedTogetherWithHistoryAndCooldowns() {
        val wrong = PowerSnapshot(
            config = PowerMonitorConfig(binding),
            roomName = "测试宿舍",
            monitor = PowerMonitorState(
                cachedBindingKey = "wrong-key",
                samples = listOf(PowerSample(1_000, 1.0)),
                lowWarnAt = 1_000,
            ),
        )
        val result = PowerPersistenceCodec.decode(PowerPersistenceCodec.encode(wrong))
        assertEquals(binding, result.config.binding)
        assertEquals(PowerMonitorState(), result.monitor)
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

    @Test fun unknownVersionIsRejectedInsteadOfResettingCredentialsSilently() {
        assertThrows(IllegalArgumentException::class.java) { PowerPersistenceCodec.decode("{\"version\":2}") }
    }
}
