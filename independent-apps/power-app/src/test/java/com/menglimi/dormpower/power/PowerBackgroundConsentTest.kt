package com.menglimi.dormpower.power

import com.menglimi.power.PowerBinding
import com.menglimi.power.PowerMonitorConfig
import com.menglimi.power.PowerQueryPolicy
import com.menglimi.power.PowerQueryPreset
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class PowerBackgroundConsentTest {
    @Test fun newInstallDefaultsToManualUntilUserChoosesPreset() {
        val snapshot = PowerSnapshot()
        assertEquals(PowerQueryPreset.MANUAL, snapshot.config.preset)
    }

    @Test fun backgroundSchedulingRequiresExplicitNonManualPresetAndValidBinding() {
        val binding = PowerBinding("account", 1000145, "room-verify")
        val manual = PowerMonitorConfig(binding = binding, preset = PowerQueryPreset.MANUAL)
        val auto = manual.copy(preset = PowerQueryPreset.BALANCED)
        assertEquals(com.menglimi.power.PowerScheduleAction.CANCEL,
            PowerQueryPolicy.scheduleAction(true, manual.preset, true))
        assertEquals(com.menglimi.power.PowerScheduleAction.REPLACE,
            PowerQueryPolicy.scheduleAction(true, auto.preset, true))
        assertEquals(com.menglimi.power.PowerScheduleAction.CANCEL,
            PowerQueryPolicy.scheduleAction(false, auto.preset, true))
        assertEquals(ZoneId.systemDefault(), manual.zone)
    }
}
