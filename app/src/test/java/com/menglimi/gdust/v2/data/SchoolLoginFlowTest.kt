package com.menglimi.gdust.v2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolLoginFlowTest {
    @Test
    fun firstPageAndEverySubsequentServiceRequireTheirOwnBusinessProbe() {
        val flow = SchoolLoginFlow()
        assertNull(flow.beginProbe())
        assertFalse(flow.schoolReady)
        for (service in SchoolService.entries) {
            assertEquals(service, flow.current)
            val probe = land(flow, "https://fixture.test/${service.name}")
            assertFalse(flow.isVerified(service))
            assertFalse(flow.complete)
            assertTrue(flow.verified(probe))
            assertFalse(flow.verified(probe))
        }
        assertTrue(flow.complete)
        assertTrue(flow.schoolReady)
        assertTrue(SchoolService.entries.all(flow::isVerified))
        assertNull(flow.beginProbe())
    }

    @Test
    fun finishedPageCannotBeLostWhileCancelledOldProbeIsStillRunning() {
        val flow = SchoolLoginFlow()
        val old = land(flow, "https://fixture.test/old")
        assertTrue(flow.pageStarted("https://fixture.test/new"))
        assertTrue(flow.pageFinished("https://fixture.test/new", "https://fixture.test/new"))
        val current = requireNotNull(flow.beginProbe())
        assertFalse(flow.endProbe(old))
        assertFalse(flow.verified(old))
        assertFalse(flow.unavailable(old))
        assertTrue(flow.accepts(current))
        assertNull(flow.beginProbe())
        assertTrue(flow.verified(current))
    }

    @Test
    fun nextServiceDoesNotWaitForPreviousProbeFinally() {
        val flow = SchoolLoginFlow()
        val portal = land(flow, "https://fixture.test/portal")
        assertTrue(flow.verified(portal))
        val teaching = land(flow, "https://fixture.test/teaching")
        assertFalse(flow.endProbe(portal))
        assertTrue(flow.accepts(teaching))
        assertTrue(flow.verified(teaching))
    }

    @Test
    fun retryOfSameUrlInvalidatesOldResultsBeforeNewPageStarts() {
        val flow = SchoolLoginFlow()
        val url = "https://fixture.test/portal"
        val old = land(flow, url)
        assertTrue(flow.navigating())
        assertFalse(flow.pageFinished(url, url))
        assertFalse(flow.verified(old))
        assertNull(flow.beginProbe())
        val retry = land(flow, url)
        assertFalse(flow.endProbe(old))
        assertTrue(flow.verified(retry))
    }

    @Test
    fun staleFinishedUrlAndActualWebViewMismatchCannotStartProbe() {
        val flow = SchoolLoginFlow()
        flow.pageStarted("https://fixture.test/new")
        assertFalse(flow.pageFinished("https://fixture.test/old", "https://fixture.test/new"))
        assertFalse(flow.pageFinished("https://fixture.test/new", "https://other.test/new"))
        assertFalse(flow.pageFinished("https://fixture.test/new", null))
        assertNull(flow.beginProbe())
        assertTrue(flow.pageFinished("https://fixture.test/new", "https://fixture.test/new"))
        val probe = requireNotNull(flow.beginProbe())
        assertTrue(flow.pageFinished("https://fixture.test/new", "https://fixture.test/new"))
        assertNull(flow.beginProbe())
        assertTrue(flow.accepts(probe))
    }

    @Test
    fun progressAfterAuthenticationPageFinishedCannotRestartLoading() {
        val flow = SchoolLoginFlow()
        val url = "https://cas.gdust.edu.cn/cas/?service=fixture"
        flow.pageStarted(url)
        assertTrue(flow.isLoadingPage(url, url))
        assertTrue(flow.pageFinished(url, url))
        flow.authenticationRequired()
        assertFalse(flow.isLoadingPage(url, url))
        assertEquals(SchoolConnection.AUTH_REQUIRED, flow.connection(SchoolService.PORTAL))
        assertFalse(flow.schoolReady)
    }

    @Test
    fun onlyTheCurrentUnfinishedPageAcceptsLoadingProgress() {
        val flow = SchoolLoginFlow()
        val oldUrl = "https://fixture.test/old"
        val newUrl = "https://fixture.test/new"
        flow.pageStarted(oldUrl)
        flow.navigating()
        assertFalse(flow.isLoadingPage(oldUrl, oldUrl))
        flow.pageStarted(newUrl)
        assertFalse(flow.isLoadingPage(oldUrl, newUrl))
        assertFalse(flow.isLoadingPage(newUrl, oldUrl))
        assertTrue(flow.isLoadingPage(newUrl, "$newUrl#/route"))
        flow.unavailable()
        assertFalse(flow.isLoadingPage(newUrl, newUrl))
        flow.pageStarted(newUrl)
        assertTrue(flow.isLoadingPage(newUrl, newUrl))
        flow.close()
        assertFalse(flow.isLoadingPage(newUrl, newUrl))
    }

    @Test
    fun unavailableTeachingCanBeSkippedWithoutLosingVerifiedPortal() {
        val flow = SchoolLoginFlow()
        flow.verified(land(flow, "https://portal.gdust.edu.cn/"))
        flow.pageStarted("https://cas.gdust.edu.cn/cas/?service=fixture")
        flow.unavailable()
        flow.skip()
        assertTrue(flow.isVerified(SchoolService.PORTAL))
        assertFalse(flow.isVerified(SchoolService.TEACHING))
        assertEquals(SchoolService.CHX, flow.current)
        assertTrue(flow.schoolReady)
    }

    @Test
    fun portalHashRoutingKeepsSameDocumentButQueryChangeDoesNot() {
        val flow = SchoolLoginFlow()
        flow.pageStarted("https://portal.gdust.edu.cn/")
        assertTrue(flow.pageFinished("https://portal.gdust.edu.cn/", "https://portal.gdust.edu.cn/#/home"))
        assertNotNull(flow.beginProbe())
        flow.pageStarted("https://portal.gdust.edu.cn/?loginCode=old-fixture")
        assertFalse(flow.pageFinished("https://portal.gdust.edu.cn/?loginCode=old-fixture",
            "https://portal.gdust.edu.cn/?loginCode=new-fixture"))
    }

    @Test
    fun authenticationRequiredOnAnyServiceWaitsForNewPage() {
        val flow = SchoolLoginFlow()
        for (service in SchoolService.entries) {
            flow.pageStarted("https://cas.gdust.edu.cn/cas/")
            flow.pageFinished("https://cas.gdust.edu.cn/cas/", "https://cas.gdust.edu.cn/cas/")
            flow.authenticationRequired()
            assertEquals(SchoolConnection.AUTH_REQUIRED, flow.connection(service))
            assertNull(flow.beginProbe())
            assertFalse(flow.isVerified(service))
            assertTrue(flow.verified(land(flow, "https://fixture.test/${service.name}")))
        }
    }

    @Test
    fun unavailableAndSkipNeverCountAsVerified() {
        val flow = SchoolLoginFlow()
        val portal = land(flow, "https://fixture.test/portal")
        assertTrue(flow.unavailable(portal))
        assertFalse(flow.verified(portal))
        assertNull(flow.beginProbe())
        flow.skip()
        assertEquals(SchoolConnection.SKIPPED, flow.connection(SchoolService.PORTAL))
        assertFalse(flow.schoolReady)
        val teaching = land(flow, "https://fixture.test/teaching")
        flow.skip()
        assertFalse(flow.verified(teaching))
        assertFalse(flow.schoolReady)
        assertTrue(flow.verified(land(flow, "https://fixture.test/chx")))
        assertTrue(flow.complete)
        assertFalse(flow.schoolReady)
    }

    @Test
    fun closeInvalidatesPreparationAndProbeCallbacksButKeepsVerifiedPartialResult() {
        val empty = SchoolLoginFlow()
        empty.close()
        assertFalse(empty.navigating())
        assertFalse(empty.pageStarted("https://fixture.test/late"))
        assertNull(empty.beginProbe())
        assertFalse(empty.schoolReady)

        val flow = SchoolLoginFlow()
        flow.verified(land(flow, "https://fixture.test/portal"))
        val teaching = land(flow, "https://fixture.test/teaching")
        flow.close()
        assertTrue(flow.closed)
        assertFalse(flow.accepts(teaching))
        assertFalse(flow.verified(teaching))
        assertFalse(flow.unavailable(teaching))
        assertFalse(flow.endProbe(teaching))
        flow.skip()
        assertEquals(SchoolService.TEACHING, flow.current)
        assertTrue(flow.schoolReady)
        assertFalse(flow.isVerified(SchoolService.TEACHING))
    }

    @Test
    fun rotationRestoresOnlyCompletedPrefixAndRestartsCurrentService() {
        val previous = SchoolLoginFlow()
        previous.verified(land(previous, "https://fixture.test/portal"))
        val oldTeaching = land(previous, "https://fixture.test/teaching")
        val restored = SchoolLoginFlow(previous.snapshot())
        assertEquals(SchoolService.TEACHING, restored.current)
        assertTrue(restored.isVerified(SchoolService.PORTAL))
        assertEquals(SchoolConnection.PENDING, restored.connection(SchoolService.TEACHING))
        assertFalse(restored.verified(oldTeaching))
        assertFalse(restored.pageFinished("https://fixture.test/teaching", "https://fixture.test/teaching"))
        assertTrue(restored.verified(land(restored, "https://fixture.test/teaching")))
        restored.skip()
        assertTrue(SchoolLoginFlow(restored.snapshot()).complete)
    }

    @Test
    fun missingMalformedOrNonContiguousSavedStateCannotInventVerifiedResults() {
        for (saved in listOf(null, emptyList(), listOf("VERIFIED"),
            listOf("bad", "VERIFIED", "VERIFIED"), listOf("PENDING", "VERIFIED", "VERIFIED"))) {
            val flow = SchoolLoginFlow(saved)
            assertEquals(SchoolService.PORTAL, flow.current)
            assertFalse(flow.schoolReady)
            assertFalse(flow.isVerified(SchoolService.CHX))
        }
        val flow = SchoolLoginFlow(listOf("SKIPPED", "AUTH_REQUIRED", "VERIFIED"))
        assertEquals(SchoolService.TEACHING, flow.current)
        assertFalse(flow.schoolReady)
        assertEquals(SchoolConnection.PENDING, flow.connection(SchoolService.CHX))
    }

    @Test
    fun redirectFailureIsAcceptedBeforeWebViewCommitsTheTarget() {
        val flow = SchoolLoginFlow(startAt = SchoolService.TEACHING)
        val cas = ChxSso.teachingWebUrl
        val callback = "http://gkjx.gdust.edu.cn/sso/lyiotlogin?ticket=fixture"
        flow.pageStarted(cas)
        flow.navigating(callback)
        assertTrue(flow.matchesFailure(callback))
        assertFalse(flow.matchesFailure(cas))
        assertFalse(flow.matchesPage(callback, cas))
        assertTrue(flow.unavailable())
        assertFalse(flow.pageFinished(callback, callback))
        assertFalse(flow.matchesFailure(callback))
        assertNull(flow.beginProbe())
        assertFalse(flow.schoolReady)
    }

    @Test
    fun fallbackDoesNotAcceptTheFailedPublicEntryOrItsOldProbe() {
        val flow = SchoolLoginFlow(startAt = SchoolService.TEACHING)
        val old = land(flow, "http://gkjx.gdust.edu.cn/xtgl/index_initMenu.html")
        flow.unavailable(old)
        flow.navigating(ChxSso.campusTeachingWebUrl)
        assertFalse(flow.matchesFailure("http://gkjx.gdust.edu.cn/xtgl/index_initMenu.html"))
        assertFalse(flow.verified(old))
        assertTrue(flow.matchesFailure(ChxSso.campusTeachingWebUrl))
    }

    @Test
    fun learningCanConnectWithoutDependingOnPortalOrTeaching() {
        val flow = SchoolLoginFlow(startAt = SchoolService.CHX)
        assertEquals(SchoolService.CHX, flow.current)
        assertFalse(flow.schoolReady)
        assertTrue(flow.verified(land(flow, ChxSso.HOME)))
        assertTrue(flow.complete)
        assertTrue(flow.isVerified(SchoolService.CHX))
        assertFalse(flow.schoolReady)
    }

    @Test
    fun failedTeachingCanContinueWithoutClaimingSuccess() {
        val flow = SchoolLoginFlow()
        flow.verified(land(flow, "https://portal.gdust.edu.cn/"))
        assertFalse(flow.continueAfterFailure())
        flow.navigating(ChxSso.teachingWebUrl)
        flow.unavailable()
        assertTrue(flow.continueAfterFailure())
        assertEquals(SchoolService.CHX, flow.current)
        assertEquals(SchoolConnection.UNAVAILABLE, flow.connection(SchoolService.TEACHING))
        assertTrue(flow.isVerified(SchoolService.PORTAL))
        assertFalse(flow.isVerified(SchoolService.TEACHING))
    }

    @Test
    fun restoringFailedCurrentServiceDoesNotSkipItsRetry() {
        val previous = SchoolLoginFlow(startAt = SchoolService.TEACHING)
        previous.navigating(ChxSso.teachingWebUrl)
        previous.unavailable()
        val restored = SchoolLoginFlow(previous.snapshot(), startAt = SchoolService.TEACHING)
        assertEquals(SchoolService.TEACHING, restored.current)
        assertFalse(restored.schoolReady)
        assertFalse(restored.complete)
    }

    @Test
    fun restoringLearningAfterTeachingFailureKeepsThePartialResult() {
        val previous = SchoolLoginFlow()
        previous.verified(land(previous, "https://portal.gdust.edu.cn/"))
        previous.unavailable()
        previous.continueAfterFailure()
        previous.navigating(ChxSso.fyssoUrl)
        val restored = SchoolLoginFlow(previous.snapshot())
        assertEquals(SchoolService.CHX, restored.current)
        assertTrue(restored.isVerified(SchoolService.PORTAL))
        assertEquals(SchoolConnection.UNAVAILABLE, restored.connection(SchoolService.TEACHING))
        assertFalse(restored.isVerified(SchoolService.TEACHING))
    }

    @Test
    fun portalOnlyCompletesWithoutConnectingOtherServices() {
        val flow = SchoolLoginFlow(only = SchoolService.PORTAL)
        assertEquals(SchoolService.PORTAL, flow.current)
        assertEquals(SchoolConnection.SKIPPED, flow.connection(SchoolService.TEACHING))
        assertEquals(SchoolConnection.SKIPPED, flow.connection(SchoolService.CHX))
        assertTrue(flow.verified(land(flow, "https://portal.gdust.edu.cn/")))
        assertTrue(flow.complete)
        assertTrue(flow.isVerified(SchoolService.PORTAL))
        assertTrue(flow.schoolReady)
        assertFalse(flow.isVerified(SchoolService.TEACHING))
        assertFalse(flow.isVerified(SchoolService.CHX))
    }

    @Test
    fun teachingOnlyCompletesAndCountsAsSchoolReady() {
        val flow = SchoolLoginFlow(only = SchoolService.TEACHING)
        assertEquals(SchoolService.TEACHING, flow.current)
        assertEquals(SchoolConnection.SKIPPED, flow.connection(SchoolService.PORTAL))
        assertEquals(SchoolConnection.SKIPPED, flow.connection(SchoolService.CHX))
        assertTrue(flow.verified(land(flow, ChxSso.teachingWebUrl)))
        assertTrue(flow.complete)
        assertFalse(flow.isVerified(SchoolService.PORTAL))
        assertTrue(flow.schoolReady)
        // 对应 finishFlow 的 RESULT_OK 判定：任一服务验证成功即为成功。
        assertTrue(flow.anyReady)
    }

    @Test
    fun chxOnlyIsIndependentlyUsableWithoutSchoolReady() {
        val flow = SchoolLoginFlow(only = SchoolService.CHX)
        assertEquals(SchoolService.CHX, flow.current)
        assertEquals(SchoolConnection.SKIPPED, flow.connection(SchoolService.PORTAL))
        assertEquals(SchoolConnection.SKIPPED, flow.connection(SchoolService.TEACHING))
        assertTrue(flow.verified(land(flow, ChxSso.HOME)))
        assertTrue(flow.complete)
        assertTrue(flow.isVerified(SchoolService.CHX))
        assertFalse(flow.schoolReady)
        // 学习通不算”学校服务就绪”，但独立成功仍应算 RESULT_OK。
        assertFalse(flow.schoolReady)
        assertTrue(flow.anyReady)
    }

    @Test
    fun singleServiceSkipEndsWithoutEnteringOtherServices() {
        for (only in SchoolService.entries) {
            val flow = SchoolLoginFlow(only = only)
            flow.skip()
            assertTrue(flow.complete)
            assertFalse(flow.isVerified(only))
            assertEquals(SchoolConnection.SKIPPED, flow.connection(only))
        }
    }

    @Test
    fun singleServiceContinueAfterFailureEndsWithoutOtherServices() {
        val flow = SchoolLoginFlow(only = SchoolService.TEACHING)
        flow.navigating(ChxSso.teachingWebUrl)
        assertTrue(flow.unavailable())
        assertEquals(SchoolService.TEACHING, flow.current)
        assertTrue(flow.continueAfterFailure())
        assertTrue(flow.complete)
        assertNull(flow.current)
        assertFalse(flow.isVerified(SchoolService.PORTAL))
        assertFalse(flow.isVerified(SchoolService.CHX))
    }

    @Test
    fun fullFlowPartialSuccessKeepsPortalWhenTeachingUnavailable() {
        val flow = SchoolLoginFlow()
        assertTrue(flow.verified(land(flow, "https://portal.gdust.edu.cn/")))
        flow.navigating(ChxSso.teachingWebUrl)
        assertTrue(flow.unavailable())
        assertTrue(flow.continueAfterFailure())
        assertEquals(SchoolService.CHX, flow.current)
        assertTrue(flow.isVerified(SchoolService.PORTAL))
        assertEquals(SchoolConnection.UNAVAILABLE, flow.connection(SchoolService.TEACHING))
        assertTrue(flow.schoolReady)
    }

    @Test
    fun singleServiceRotationRestoresOnlyCompletedPrefixAndStartAtBoundary() {
        val previous = SchoolLoginFlow(only = SchoolService.TEACHING)
        assertTrue(previous.verified(land(previous, ChxSso.teachingWebUrl)))
        val restored = SchoolLoginFlow(previous.snapshot(), only = SchoolService.TEACHING)
        assertTrue(restored.complete)
        assertTrue(restored.isVerified(SchoolService.TEACHING))
        assertTrue(restored.schoolReady)

        val fromChx = SchoolLoginFlow(startAt = SchoolService.CHX)
        assertEquals(SchoolService.CHX, fromChx.current)
        assertEquals(SchoolConnection.SKIPPED, fromChx.connection(SchoolService.PORTAL))
        assertEquals(SchoolConnection.SKIPPED, fromChx.connection(SchoolService.TEACHING))
    }

    @Test
    fun singleServiceStaleProbeCannotOverrideVerification() {
        val flow = SchoolLoginFlow(only = SchoolService.PORTAL)
        val stale = land(flow, "https://portal.gdust.edu.cn/")
        assertTrue(flow.navigating())
        assertFalse(flow.verified(stale))
        assertFalse(flow.endProbe(stale))
        val fresh = land(flow, "https://portal.gdust.edu.cn/")
        assertTrue(flow.verified(fresh))
        assertTrue(flow.complete)
        assertTrue(flow.isVerified(SchoolService.PORTAL))
    }

    @Test
    fun singleServiceSummaryReportsOnlyItsTarget() {
        val chxOnly = SchoolLoginFlow(only = SchoolService.CHX)
        assertTrue(chxOnly.verified(land(chxOnly, ChxSso.HOME)))
        assertEquals("学习通已连接", chxOnly.summary())

        val teachingOnly = SchoolLoginFlow(only = SchoolService.TEACHING)
        assertEquals("教务待连接", teachingOnly.summary())

        val portalOnly = SchoolLoginFlow(only = SchoolService.PORTAL)
        assertTrue(portalOnly.navigating("https://portal.gdust.edu.cn/"))
        assertTrue(portalOnly.unavailable())
        // 失败是"未验证"；其余服务未尝试，摘要不得把它们描述为"未连接"。
        assertEquals("门户未验证", portalOnly.summary())
        assertFalse(portalOnly.summary().contains("未连接"))

        // 完整流程摘要保持三段。
        assertEquals("门户待连接 · 教务待连接 · 学习通待连接", SchoolLoginFlow().summary())
    }

    @Test
    fun startAtBoundaryRejectsVerifiedEntriesOutsideItsActiveServices() {
        // 脏/跨入口快照：startAt=CHX 只恢复 CHX，起点之前的 VERIFIED 不能发明成功。
        val dirty = SchoolLoginFlow(listOf("VERIFIED", "VERIFIED", "PENDING"), startAt = SchoolService.CHX)
        assertEquals(SchoolService.CHX, dirty.current)
        assertEquals(SchoolConnection.SKIPPED, dirty.connection(SchoolService.PORTAL))
        assertEquals(SchoolConnection.SKIPPED, dirty.connection(SchoolService.TEACHING))
        assertFalse(dirty.schoolReady)
        assertFalse(dirty.complete)
        assertFalse(dirty.isVerified(SchoolService.CHX))

        // 单服务入口同样拒绝为序列外服务发明结果。
        val crossEntry = SchoolLoginFlow(listOf("VERIFIED", "SKIPPED", "PENDING"), only = SchoolService.CHX)
        assertEquals(SchoolService.CHX, crossEntry.current)
        assertFalse(crossEntry.anyReady)
        assertFalse(crossEntry.complete)
    }

    private fun land(flow: SchoolLoginFlow, url: String): SchoolLoginFlow.Probe {
        assertTrue(flow.navigating())
        assertTrue(flow.pageStarted(url))
        assertTrue(flow.pageFinished(url, url))
        return requireNotNull(flow.beginProbe())
    }
}
