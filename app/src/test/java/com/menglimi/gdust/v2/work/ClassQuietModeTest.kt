package com.menglimi.gdust.v2.work

import org.junit.Assert.assertEquals
import org.junit.Test

class ClassQuietModeTest {
    @Test fun legacyEnabledKeepsOriginalDndBehavior() {
        assertEquals(ClassQuietMode.DND, ClassQuietMode.fromStored(null, legacyEnabled = true))
        assertEquals(ClassQuietMode.OFF, ClassQuietMode.fromStored(null, legacyEnabled = false))
    }

    @Test fun storedChoiceWinsOverLegacyFlagAfterRestart() {
        assertEquals(ClassQuietMode.OFF, ClassQuietMode.fromStored("off", legacyEnabled = true))
        assertEquals(ClassQuietMode.SILENT, ClassQuietMode.fromStored("silent", legacyEnabled = false))
        assertEquals(ClassQuietMode.DND, ClassQuietMode.fromStored("dnd", legacyEnabled = false))
    }

    @Test fun unknownModeDoesNotUnexpectedlySilencePhone() {
        assertEquals(ClassQuietMode.OFF, ClassQuietMode.fromStored("future-mode", legacyEnabled = true))
        assertEquals(ClassQuietMode.OFF, ClassQuietMode.fromStored("", legacyEnabled = true))
    }
}
