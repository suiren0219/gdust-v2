package com.menglimi.dormpower.power

import com.menglimi.power.PowerBinding
import com.menglimi.power.PowerDetail
import com.menglimi.power.PowerDetailSource
import com.menglimi.power.PowerMonitorState
import com.menglimi.power.PowerQueryPreset
import com.menglimi.power.PowerReading
import com.menglimi.power.PowerRoom
import com.menglimi.power.PowerSample
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PowerControllerTest {
    private val binding = PowerBinding("synthetic-account", 1, "synthetic-room")
    private val room = PowerRoom("合成宿舍", binding.verify)
    private val now = Instant.parse("2026-11-16T04:00:00Z").toEpochMilli()
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneId.of("Asia/Shanghai"))
    private val detail = PowerDetail(42.0, 2.0, emptyList(), null, null)
    private class Memory : PowerPersistence {
        var value: String? = null
        override fun read() = value
        override fun write(value: String) { this.value = value }
    }
    private fun storage(bound: Boolean = false) = PowerStorage(Memory()).apply {
        if (bound) bind(binding, room.name, snapshot().bindingRevision)
    }
    private fun TestScope.controller(store: PowerStorage, source: PowerDetailSource = PowerDetailSource { detail },
        rooms: suspend (String, Int) -> List<PowerRoom> = { _, _ -> listOf(room) }): PowerController =
        PowerController(store, source, rooms, this, clock = clock)

    @Test fun roomLookupAloneNeverBindsAndEmptyResultKeepsTheQueriedInputs() = runTest {
        val store = storage()
        val controller = controller(store, rooms = { _, _ -> emptyList() })
        advanceUntilIdle()
        controller.queryRooms("  synthetic-account  ", 123)
        advanceUntilIdle()
        assertNull(store.config().binding)
        assertEquals("synthetic-account", controller.state.value.roomQueryAccount)
        assertEquals(123, controller.state.value.roomQueryCode)
        assertTrue(controller.state.value.rooms.isEmpty())
        assertNotNull(controller.state.value.roomError)
        assertFalse(controller.state.value.roomBusy)
    }

    @Test fun cancelledLookupCannotRestoreSelectionEvenIfSourceIgnoresCancellation() = runTest {
        val store = storage()
        val release = CompletableDeferred<Unit>()
        val controller = controller(store, rooms = { _, _ ->
            withContext(NonCancellable) { release.await() }
            listOf(room)
        })
        advanceUntilIdle()
        val job = controller.queryRooms(binding.account, binding.code)
        runCurrent()
        assertTrue(controller.state.value.roomBusy)
        controller.cancelRooms()
        assertFalse(controller.state.value.roomBusy)
        release.complete(Unit)
        advanceUntilIdle()
        assertTrue(job.isCancelled)
        assertTrue(controller.state.value.rooms.isEmpty())
        assertNull(store.config().binding)
        controller.bind(binding.account, binding.code, room)
        advanceUntilIdle()
        assertNull(store.config().binding)
        assertNotNull(controller.state.value.roomError)
    }

    @Test fun roomFailureDoesNotReplaceRefreshFeedbackOrBlockSettings() = runTest {
        val store = storage(bound = true)
        val release = CompletableDeferred<Unit>()
        val controller = controller(store, source = PowerDetailSource { release.await(); detail },
            rooms = { _, _ -> throw IOException("synthetic network failure") })
        advanceUntilIdle()
        controller.refresh()
        runCurrent()
        assertTrue(controller.state.value.busy)
        controller.queryRooms(binding.account, binding.code)
        controller.setPreset(PowerQueryPreset.MANUAL)
        runCurrent()
        assertTrue(controller.state.value.busy)
        assertFalse(controller.state.value.roomBusy)
        assertNotNull(controller.state.value.roomError)
        assertNull(controller.state.value.error)
        assertEquals(PowerQueryPreset.MANUAL, store.config().preset)
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(42.0, controller.state.value.reading!!.detail.odd!!, 0.0)
        assertNotNull(controller.state.value.roomError)
    }

    @Test fun failedRefreshRetainsReadingSamplesAndLastSuccessTime() = runTest {
        val store = storage(bound = true)
        val old = now - 600_000
        val reading = PowerReading(detail, old)
        val samples = listOf(PowerSample(old, 42.0))
        store.update(binding) { PowerMonitorState(binding.key, reading, old, old, samples) }
        val controller = controller(store, source = PowerDetailSource { throw IOException("synthetic failure") })
        advanceUntilIdle()
        controller.refresh()
        advanceUntilIdle()
        val state = controller.state.value
        assertEquals(reading, state.reading)
        assertEquals(samples, state.samples)
        assertEquals(old, state.lastSampleAt)
        assertEquals(now, state.lastAttemptAt)
        assertNotNull(state.error)
        assertFalse(state.busy)
    }

    @Test fun repeatedRefreshSharesRequestAndCancellationKeepsPersistedHistory() = runTest {
        val store = storage(bound = true)
        val old = now - 600_000L
        val reading = PowerReading(detail, old)
        val samples = listOf(PowerSample(old, 42.0))
        store.update(binding) { PowerMonitorState(binding.key, reading, old, old, samples) }
        val release = CompletableDeferred<Unit>()
        var requests = 0
        val controller = controller(store, source = PowerDetailSource {
            requests++
            release.await()
            detail.copy(odd = 39.0)
        })
        advanceUntilIdle()
        val first = controller.refresh()
        runCurrent()
        assertSame(first, controller.refresh())
        assertEquals(1, requests)
        assertEquals(reading, controller.state.value.reading)
        controller.cancelRefresh()
        runCurrent()
        assertTrue(first.isCancelled)
        assertFalse(controller.state.value.busy)
        assertEquals(reading, store.state().reading)
        assertEquals(samples, store.state().samples)
        release.complete(Unit)
        controller.refresh()
        advanceUntilIdle()
        assertEquals(2, requests)
        assertEquals(39.0, controller.state.value.reading!!.detail.odd!!, 0.0)
    }

    @Test fun unbindingDuringRefreshCannotRestoreReadingOrSendAnOldWarning() = runTest {
        val store = storage(bound = true)
        val release = CompletableDeferred<Unit>()
        var warnings = 0
        var schedules = 0
        val controller = PowerController(store, PowerDetailSource {
            withContext(NonCancellable) { release.await() }
            detail.copy(odd = 1.0)
        }, { _, _ -> listOf(room) }, this, onWarning = { warnings++; true },
            onSchedule = { schedules++ }, clock = clock)
        advanceUntilIdle()
        controller.refresh()
        runCurrent()
        controller.unbind()
        runCurrent()
        assertNull(controller.state.value.config.binding)
        assertFalse(controller.state.value.busy)
        release.complete(Unit)
        advanceUntilIdle()
        assertNull(controller.state.value.reading)
        assertTrue(controller.state.value.samples.isEmpty())
        assertEquals(PowerMonitorState(), store.state())
        assertEquals(0, warnings)
        assertEquals(1, schedules)
    }

    @Test fun thresholdsCommitTogetherAndInvalidInputCannotPartiallySave() = runTest {
        val store = storage()
        val controller = controller(store)
        advanceUntilIdle()
        controller.setThresholds(12.5, 2.5)
        advanceUntilIdle()
        assertEquals(12.5, store.config().lowThreshold, 0.0)
        assertEquals(2.5, store.config().dropRateThreshold, 0.0)
        controller.setThresholds(3.0, Double.NaN)
        advanceUntilIdle()
        assertEquals(12.5, store.config().lowThreshold, 0.0)
        assertEquals(2.5, store.config().dropRateThreshold, 0.0)
        assertNotNull(controller.state.value.settingsError)
    }

    @Test fun confirmingRoomClearsOldCacheBeforeRefreshingNewBinding() = runTest {
        val store = storage(bound = true)
        val old = now - 600_000
        store.update(binding) { PowerMonitorState(binding.key, PowerReading(detail, old), old, old,
            listOf(PowerSample(old, 42.0)), old, old) }
        val otherRoom = PowerRoom("另一合成宿舍", "other-room")
        val release = CompletableDeferred<Unit>()
        val controller = controller(store, source = PowerDetailSource { release.await(); detail.copy(odd = 99.0) },
            rooms = { _, _ -> listOf(otherRoom) })
        advanceUntilIdle()
        controller.queryRooms("other-account", 2)
        advanceUntilIdle()
        assertEquals(binding, store.config().binding)
        controller.bind("other-account", 2, otherRoom)
        runCurrent()
        assertNull(controller.state.value.reading)
        assertTrue(controller.state.value.samples.isEmpty())
        assertEquals(0L, store.state().lowWarnAt)
        assertEquals(0L, store.state().dropWarnAt)
        assertEquals(PowerBinding("other-account", 2, "other-room"), store.config().binding)
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(99.0, controller.state.value.reading!!.detail.odd!!, 0.0)
    }

    @Test fun ledgerFormFromPreviousRoomCannotWriteIntoNewRoom() = runTest {
        val store = storage(bound = true)
        val controller = controller(store)
        advanceUntilIdle()
        val revision = controller.state.value.bindingRevision
        store.bind(PowerBinding("other", 2, "other"), "另一宿舍", revision)
        controller.reload()
        advanceUntilIdle()
        controller.saveEntry(PowerLedgerEntry(time = now, kwh = 100.0, paidYuan = "60.00"), revision)
        advanceUntilIdle()
        assertTrue(store.snapshot().ledger.entries.isEmpty())
        assertNotNull(controller.state.value.ledgerError)
        assertEquals(0L, controller.state.value.ledgerSavedVersion)
    }

    @Test fun ledgerSuccessfulSavePublishesVersionAndInvalidInputPreservesIt() = runTest {
        val store = storage(bound = true)
        val controller = controller(store)
        advanceUntilIdle()
        controller.saveEntry(PowerLedgerEntry(time = now, kwh = 100.0, paidYuan = "60.00", payer = "室友甲"))
        advanceUntilIdle()
        assertEquals(1L, controller.state.value.ledgerSavedVersion)
        assertEquals("室友甲", controller.state.value.ledger.entries.single().payer)
        controller.setPrice("NaN")
        advanceUntilIdle()
        assertEquals("0.6", controller.state.value.ledger.unitPrice)
        assertEquals(1L, controller.state.value.ledgerSavedVersion)
        assertNotNull(controller.state.value.ledgerError)
    }
}
