package com.menglimi.gdust.v2.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ChxWebSessionTest {
    @Test fun candidateComesOnlyFromTheRequestTargetNotOtherHosts() {
        val requested = mutableListOf<String>()
        val candidate = ChxWebSession.readCandidateCookie { url ->
            requested += url
            if (url == "https://i.chaoxing.com/") "_uid=host-only-test" else "JSESSIONID=anonymous"
        }
        assertNull(candidate)
        assertEquals(listOf(ChxSso.HOME), requested)
        assertEquals("_uid=target-test", ChxWebSession.readCandidateCookie { "_uid=target-test" })
    }

    @Test fun missingWebSessionDoesNotReuseAnOldStoredHeaderOrProbe() = runBlocking {
        var saved: String? = "_uid=old-test"
        var probes = 0
        val result = ChxWebSession.verifyCandidate(
            readCookie = { "JSESSIONID=anonymous" },
            probe = { probes++; true },
            checkCurrent = {},
            saveCandidate = { saved = it },
            clearCandidate = { saved = null },
        )
        assertEquals(false, result)
        assertEquals(0, probes)
        assertNull(saved)
    }

    @Test fun verifiedCurrentWebSessionReplacesTheStoredSnapshot() = runBlocking {
        var saved = "_uid=old-test"
        val result = ChxWebSession.verifyCandidate(
            readCookie = { "_uid=current-test" }, probe = { true }, checkCurrent = {},
            saveCandidate = { saved = it }, clearCandidate = { fail("Valid session must be kept") },
        )
        assertEquals(true, result)
        assertEquals("_uid=current-test", saved)
    }

    @Test fun onlyTwoExplicitRejectionsClearTheUnchangedCandidate() = runBlocking {
        for (responses in listOf(listOf<Boolean?>(null), listOf(false, null), listOf(false, true), listOf(false, false))) {
            var index = 0
            var cleared = false
            val result = ChxWebSession.verifyCandidate(
                readCookie = { "_uid=unchanged-test" },
                probe = { responses[index++] },
                checkCurrent = {}, saveCandidate = {}, clearCandidate = { cleared = true },
            )
            assertEquals(responses.last(), result)
            assertEquals(responses.size, index)
            assertEquals(responses == listOf(false, false), cleared)
        }
    }

    @Test fun cookieRotationCannotConfirmOrDeleteTheNewSession() = runBlocking {
        for (response in listOf(true, false, null)) {
            var cookie = "_uid=old-test"
            var saved = cookie
            var cleared = false
            val result = ChxWebSession.verifyCandidate(
                readCookie = { cookie },
                probe = { cookie = "_uid=new-test"; response },
                checkCurrent = {}, saveCandidate = { saved = it }, clearCandidate = { cleared = true },
            )
            assertNull(result)
            assertEquals("_uid=new-test", saved)
            assertFalse(cleared)
        }
    }

    @Test fun logoutWhileCheckingCancelsBeforeAnotherProbeOrWrite() = runBlocking {
        val guard = SessionGuard()
        val version = guard.current()
        var probes = 0
        var saves = 0
        var cleared = false
        try {
            ChxWebSession.verifyCandidate(
                readCookie = { "_uid=old-test" },
                probe = { probes++; guard.invalidate(); false },
                checkCurrent = { guard.withCurrent(version) {} },
                saveCandidate = { saves++ }, clearCandidate = { cleared = true },
            )
            fail("Session change must cancel the old check")
        } catch (_: CancellationException) {
            assertEquals(1, probes)
            assertEquals(1, saves)
            assertFalse(cleared)
        }
    }

    @Test fun cancellationDoesNotClearTheCandidate() = runBlocking {
        var cleared = false
        try {
            ChxWebSession.verifyCandidate(
                readCookie = { "_uid=current-test" }, probe = { throw CancellationException("test") },
                checkCurrent = {}, saveCandidate = {}, clearCandidate = { cleared = true },
            )
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertTrue(!cleared)
        }
    }
}
