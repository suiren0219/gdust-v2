package com.menglimi.chaoxing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChxRequestGateTest {
    @Test fun retryAfterSupportsSecondsHttpDatesAndMissingHeaders() {
        val now = 1_000_000L
        assertEquals(now + 120_000, ChxResponse(429, "", retryAfter = "120").requestPause(now)!!.retryAt)
        assertEquals(now + 90_000, ChxResponse(429, "", retryAfter = "Tue, 17 Nov 2026 10:01:30 GMT",
            serverDate = "Tue, 17 Nov 2026 10:00:00 GMT").requestPause(now)!!.retryAt)
        for (value in listOf(null, "bad", "-1")) {
            assertEquals(now + 60_000, ChxResponse(429, "private", retryAfter = value).requestPause(now)!!.retryAt)
        }
        assertEquals(Long.MAX_VALUE, ChxResponse(429, "", retryAfter = Long.MAX_VALUE.toString()).requestPause(now)!!.retryAt)
        assertNull(ChxResponse(403, "拒绝访问").requestPause(now))
        assertFalse(ChxResponse(429, "private").requestPause(now)!!.exception().message!!.contains("private"))
    }

    @Test fun responsesPauseQueuedRequestsBeforeAnyMoreNetworkCalls() = runBlocking {
        val gate = ChxRequestGate(intervalMillis = 0)
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<ChxResponse>()
        var calls = 0
        val first = async { runCatching { gate.execute("work", {}) { calls++; started.complete(Unit); response.await() } } }
        started.await()
        val queued = async { runCatching { gate.execute("progress", {}) { calls++; ChxResponse(200, "") } } }
        yield()
        response.complete(ChxResponse(200, """{"code":9010}"""))
        assertTrue((first.await().exceptionOrNull() as ChxCourseReadException).verificationRequired)
        assertTrue((queued.await().exceptionOrNull() as ChxCourseReadException).verificationRequired)
        assertEquals(1, calls)
        assertTrue(gate.causedPause("work"))
        assertFalse(gate.causedPause("progress"))
    }

    @Test fun cooldownRequiresManualRecoveryAndFailedCheckKeepsThePause() = runBlocking {
        var now = 10_000L
        val gate = ChxRequestGate({ now }, intervalMillis = 0)
        runCatching { gate.execute("first", {}) { ChxResponse(429, "", retryAfter = "2") } }
        assertThrows(ChxCourseReadException::class.java) { gate.beginRecovery("early", true) }
        now += 2_000
        assertThrows(ChxCourseReadException::class.java) { gate.check("normal") }
        gate.beginRecovery("recheck", false)
        assertThrows(ChxCourseReadException::class.java) { gate.check("other") }
        gate.execute("recheck", {}) { ChxResponse(200, "") }
        gate.finishRecovery("recheck", false)
        assertNotNull(gate.pause.value)
        gate.beginRecovery("again", false)
        gate.execute("again", {}) { ChxResponse(200, "") }
        gate.finishRecovery("again", true)
        assertNull(gate.pause.value)
    }

    @Test fun verificationCannotBeClearedByRefreshOrAFailedVerification() = runBlocking {
        val gate = ChxRequestGate(intervalMillis = 0)
        gate.restore(ChxRequestPause(true))
        assertThrows(ChxCourseReadException::class.java) { gate.beginRecovery("refresh", false) }
        gate.beginRecovery("verified", true)
        runCatching { gate.execute("verified", {}) { ChxResponse(200, """{"errorCode":"9010"}""") } }
        gate.finishRecovery("verified", true)
        assertTrue(gate.pause.value!!.verificationRequired)
    }

    @Test fun requestsAreSpacedAndCancellationNeverReleasesAPause() = runBlocking {
        var now = 10_000L
        val waits = mutableListOf<Long>()
        val gate = ChxRequestGate({ now }, wait = { waits += it; now += it })
        gate.execute("one", {}) { ChxResponse(200, "") }
        gate.execute("two", {}) { ChxResponse(200, "") }
        assertEquals(listOf(1_000L), waits)
        gate.restore(ChxRequestPause(true))
        gate.beginRecovery("cancelled", true)
        val failure = runCatching { gate.execute("cancelled", {}) { throw CancellationException("cancel") } }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        gate.finishRecovery("cancelled", false)
        assertTrue(gate.pause.value!!.verificationRequired)
        assertThrows(ChxCourseReadException::class.java) { gate.check() }
        Unit
    }
}
