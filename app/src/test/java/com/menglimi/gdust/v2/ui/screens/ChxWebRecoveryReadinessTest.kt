package com.menglimi.gdust.v2.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChxWebRecoveryReadinessTest {
    private val courseUrl = "https://mooc2-ans.chaoxing.com/mooc2-ans/visit/interaction"
    private val liveCookie = "_uid=synthetic-current-cookie; JSESSIONID=synthetic"

    private fun ready(
        currentSession: Boolean = true,
        url: String? = courseUrl,
        pageFinished: Boolean = true,
        mainDocumentFailed: Boolean = false,
        verificationPage: Boolean = false,
        candidateCookie: String? = liveCookie,
    ) = isChxWebRecoveryReady(currentSession, url, pageFinished, mainDocumentFailed,
        verificationPage, candidateCookie)

    @Test fun completedHealthyCurrentPageWithLiveWebCookieIsReadyWithoutCookieRotation() {
        assertTrue(ready())
    }

    @Test fun requiresCurrentSessionCompletedPageAndSuccessfulMainDocument() {
        assertFalse(ready(currentSession = false))
        assertFalse(ready(pageFinished = false))
        assertFalse(ready(mainDocumentFailed = true))
    }

    @Test fun rejectsLoginVerificationAndCaptchaPagesEvenIfTheyHaveCookies() {
        assertFalse(ready(url = "https://passport2.chaoxing.com/login"))
        assertFalse(ready(url = "https://mooc2-ans.chaoxing.com/mooc2-ans/verify"))
        assertFalse(ready(url = "https://mooc2-ans.chaoxing.com/mooc2-ans/captcha"))
        assertFalse(ready(verificationPage = true))
    }

    @Test fun requiresLiveIdentityCookieNotAnonymousOrMissingCookie() {
        assertFalse(ready(candidateCookie = null))
        assertFalse(ready(candidateCookie = "JSESSIONID=synthetic"))
        assertFalse(ready(candidateCookie = "_uid=; JSESSIONID=synthetic"))
    }

    @Test fun rejectsUntrustedCleartextAndPathTraversalPages() {
        assertFalse(ready(url = "https://example.invalid/course"))
        assertFalse(ready(url = "http://mooc2-ans.chaoxing.com/course"))
        assertFalse(ready(url = "https://mooc2-ans.chaoxing.com/a/../course"))
    }
}
