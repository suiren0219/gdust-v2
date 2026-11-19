package com.menglimi.gdust.v2.work

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerCollectionPolicyTest {
    @Test fun schedulingNeedsExplicitConsentAndBinding() {
        assertTrue(PowerCollectionPolicy.shouldSchedule(enabled = true, hasBinding = true))
        assertFalse(PowerCollectionPolicy.shouldSchedule(enabled = false, hasBinding = true))
        assertFalse(PowerCollectionPolicy.shouldSchedule(enabled = true, hasBinding = false))
        assertFalse(PowerCollectionPolicy.shouldSchedule(enabled = false, hasBinding = false))
    }

    @Test fun workerConfigMustBeEnabledAndComplete() {
        assertTrue(PowerCollectionPolicy.hasWorkerConfig(true, "account", 1000145, "room-verify"))
        assertFalse(PowerCollectionPolicy.hasWorkerConfig(false, "account", 1000145, "room-verify"))
        assertFalse(PowerCollectionPolicy.hasWorkerConfig(true, null, 1000145, "room-verify"))
        assertFalse(PowerCollectionPolicy.hasWorkerConfig(true, " ", 1000145, "room-verify"))
        assertFalse(PowerCollectionPolicy.hasWorkerConfig(true, "account", 0, "room-verify"))
        assertFalse(PowerCollectionPolicy.hasWorkerConfig(true, "account", 1000145, ""))
    }
}
