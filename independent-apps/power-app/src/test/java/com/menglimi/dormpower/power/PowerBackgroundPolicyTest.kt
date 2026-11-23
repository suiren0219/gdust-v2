package com.menglimi.dormpower.power

import com.menglimi.power.PowerBinding
import com.menglimi.power.PowerMonitorConfig
import com.menglimi.power.PowerQueryPreset
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PowerBackgroundPolicyTest {
    private val config = PowerMonitorConfig(PowerBinding("synthetic", 1, "room"), zone = ZoneId.of("Asia/Shanghai"))

    @Test fun bootQueryIsEligibleForAnyPresetIncludingManual() {
        for (preset in PowerQueryPreset.entries) {
            val current = config.copy(preset = preset,
                awakeWindow = LocalTime.of(13, 0) to LocalTime.of(14, 0))
            assertTrue(PowerBackgroundPolicy.isBootQueryEligible(current))
        }
    }

    @Test fun missingOrInvalidBindingIsNotEligibleForAnyPreset() {
        for (preset in PowerQueryPreset.entries) {
            for (binding in listOf(null, config.binding!!.copy(account = ""),
                config.binding!!.copy(code = 0), config.binding!!.copy(verify = ""))) {
                assertFalse(PowerBackgroundPolicy.isBootQueryEligible(config.copy(binding = binding, preset = preset)))
            }
        }
    }

    @Test fun receiverRestoresOnlyBootAndOwnPackageReplacement() = runTest {
        var calls = 0
        for (action in listOf(null, "", "android.intent.action.PACKAGE_REPLACED", "android.intent.action.TIME_TICK")) {
            assertFalse(PowerBackgroundPolicy.isRestoreAction(action))
            assertFalse(PowerBootReceiver.restore(action) { calls++ })
        }
        assertEquals(0, calls)
        for (action in listOf(PowerBackgroundPolicy.BOOT_COMPLETED, PowerBackgroundPolicy.MY_PACKAGE_REPLACED)) {
            assertTrue(PowerBackgroundPolicy.isRestoreAction(action))
            assertTrue(PowerBootReceiver.restore(action) { calls++ })
        }
        assertEquals(2, calls)
    }
}
