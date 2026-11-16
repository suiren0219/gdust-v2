package com.menglimi.gdust.v2.ui.screens

import com.menglimi.gdust.v2.data.AuthFailed
import com.menglimi.gdust.v2.data.Captcha
import com.menglimi.gdust.v2.data.SchoolAuthenticationException
import com.menglimi.gdust.v2.data.SchoolAuthenticationStage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class LegacyLoginControllerTest {
    private fun captcha(id: String) = Captcha(byteArrayOf(1, 2, 3), id)

    @Test fun loginFailureRemainsVisibleAfterReplacementCaptchaLoads() = runBlocking {
        var loads = 0
        var accepted = false
        val controller = LegacyLoginController(this, { captcha("challenge-${++loads}") }, { _, _, _, _ ->
            throw AuthFailed(30001, "验证码错误")
        })
        controller.refreshCaptcha()
        yield()
        controller.changeCode("1234")
        controller.submit("student", "password") { accepted = true }
        yield()
        val state = controller.state.value
        assertEquals(2, loads)
        assertFalse(accepted)
        assertEquals("challenge-2", state.captcha!!.uuid)
        assertEquals("", state.code)
        assertTrue(state.loginError!!.contains("验证码错误或已过期"))
        assertNull(state.captchaError)
        assertFalse(state.submitting)
        assertFalse(state.loadingCaptcha)
        assertFalse(state.canSubmit("student", "password"))
        controller.cancel()
    }

    @Test fun repeatedClicksAndRefreshCannotChangeTheChallengeDuringSubmission() = runBlocking {
        val finished = CompletableDeferred<Unit>()
        var loads = 0
        var callbacks = 0
        val requests = mutableListOf<List<String>>()
        val controller = LegacyLoginController(this, { loads++; captcha("original") }, { account, password, code, uuid ->
            requests += listOf(account, password, code, uuid)
            finished.await()
        })
        try {
            controller.refreshCaptcha()
            yield()
            controller.changeCode(" 1234 ")
            controller.submit(" student ", " password ") { callbacks++ }
            controller.submit("other", "other") { callbacks++ }
            controller.refreshCaptcha()
            controller.changeCode("9999")
            yield()
            assertEquals(listOf(listOf("student", " password ", "1234", "original")), requests)
            assertEquals(1, loads)
            assertTrue(controller.state.value.submitting)
            assertEquals(" 1234 ", controller.state.value.code)
            finished.complete(Unit)
            yield()
            assertEquals(1, callbacks)
            assertEquals(1, loads)
            assertNull(controller.state.value.captcha)
            assertNull(controller.state.value.loginError)
            assertFalse(controller.state.value.submitting)
        } finally {
            finished.complete(Unit)
            controller.cancel()
        }
    }

    @Test fun captchaLoadingAndMissingFieldsPreventAnyLoginRequest() = runBlocking {
        val pending = CompletableDeferred<Captcha>()
        var requests = 0
        val controller = LegacyLoginController(this, { pending.await() }, { _, _, _, _ -> requests++ })
        try {
            controller.changeCode("1234")
            controller.submit("student", "password") { error("Must not log in") }
            controller.refreshCaptcha()
            controller.changeCode("1234")
            controller.submit("student", "password") { error("Must not log in") }
            yield()
            assertTrue(controller.state.value.loadingCaptcha)
            assertEquals("", controller.state.value.code)
            assertEquals(0, requests)
            pending.complete(captcha("ready"))
            yield()
            controller.changeCode("1234")
            controller.submit(" ", "password") { error("Must not log in") }
            controller.submit("student", " ") { error("Must not log in") }
            yield()
            assertEquals(0, requests)
            assertTrue(controller.state.value.canSubmit("student", "password"))
        } finally {
            pending.cancel()
            controller.cancel()
        }
    }

    @Test fun lateCancelledCaptchaCannotReplaceTheNewChallenge() = runBlocking {
        val first = CompletableDeferred<Captcha>()
        val second = CompletableDeferred<Captcha>()
        var loads = 0
        val controller = LegacyLoginController(this, {
            if (++loads == 1) withContext(NonCancellable) { first.await() } else second.await()
        }, { _, _, _, _ -> error("Not submitting") })
        try {
            controller.refreshCaptcha()
            yield()
            controller.refreshCaptcha()
            yield()
            val latest = captcha("newest")
            second.complete(latest)
            yield()
            controller.changeCode("4321")
            first.complete(captcha("obsolete"))
            yield()
            assertSame(latest, controller.state.value.captcha)
            assertEquals("4321", controller.state.value.code)
            assertNull(controller.state.value.captchaError)
            assertFalse(controller.state.value.loadingCaptcha)
        } finally {
            first.complete(captcha("finished"))
            second.cancel()
            controller.cancel()
        }
    }

    @Test fun failedReplacementKeepsLoginErrorAndInvalidatesOldChallenge() = runBlocking {
        var loads = 0
        val controller = LegacyLoginController(this, {
            if (++loads == 2) throw IOException("private response")
            captcha("challenge-$loads")
        }, { _, _, _, _ -> throw AuthFailed(30002, "账号或密码错误") })
        controller.refreshCaptcha()
        yield()
        controller.changeCode("1234")
        controller.submit("student", "password") { error("Must not log in") }
        yield()
        val failed = controller.state.value
        assertEquals("登录失败：账号或密码错误", failed.loginError)
        assertNotNull(failed.captchaError)
        assertNull(failed.captcha)
        assertEquals("", failed.code)
        assertFalse(failed.canSubmit("student", "password"))
        controller.refreshCaptcha()
        yield()
        assertEquals(failed.loginError, controller.state.value.loginError)
        assertNull(controller.state.value.captchaError)
        assertEquals("challenge-3", controller.state.value.captcha!!.uuid)
        controller.cancel()
    }

    @Test fun manualRefreshImmediatelyRemovesOldImageCodeAndUuid() = runBlocking {
        val pending = CompletableDeferred<Captcha>()
        var loads = 0
        val controller = LegacyLoginController(this, {
            if (++loads == 1) captcha("first") else pending.await()
        }, { _, _, _, _ -> error("Must not submit during refresh") })
        try {
            controller.refreshCaptcha()
            yield()
            controller.changeCode("1234")
            assertTrue(controller.state.value.canSubmit("student", "password"))
            controller.refreshCaptcha()
            assertNull(controller.state.value.captcha)
            assertEquals("", controller.state.value.code)
            assertTrue(controller.state.value.loadingCaptcha)
            assertFalse(controller.state.value.canSubmit("student", "password"))
        } finally {
            pending.cancel()
            controller.cancel()
        }
    }

    @Test fun cancelledLoginCannotReportSuccessOrFetchAnotherCaptcha() = runBlocking {
        val pending = CompletableDeferred<Unit>()
        var loads = 0
        var callbacks = 0
        val controller = LegacyLoginController(this, { loads++; captcha("ready") }, { _, _, _, _ ->
            withContext(NonCancellable) { pending.await() }
        })
        try {
            controller.refreshCaptcha()
            yield()
            controller.changeCode("1234")
            controller.submit("student", "password") { callbacks++ }
            yield()
            controller.cancel()
            pending.complete(Unit)
            yield()
            assertEquals(0, callbacks)
            assertEquals(1, loads)
            assertNull(controller.state.value.loginError)
            assertNull(controller.state.value.captchaError)
            assertNull(controller.state.value.captcha)
            assertFalse(controller.state.value.submitting)
        } finally {
            pending.complete(Unit)
            controller.cancel()
        }
    }

    @Test fun malformedChallengesNeverEnableSubmission() = runBlocking {
        for (challenge in listOf(captcha(""), Captcha(byteArrayOf(), "nonempty"))) {
            var requests = 0
            val controller = LegacyLoginController(this, { challenge }, { _, _, _, _ -> requests++ })
            controller.refreshCaptcha()
            yield()
            controller.changeCode("1234")
            controller.submit("student", "password") { error("Must not log in") }
            yield()
            assertEquals(0, requests)
            assertNull(controller.state.value.captcha)
            assertNotNull(controller.state.value.captchaError)
            assertFalse(controller.state.value.loadingCaptcha)
            controller.cancel()
        }
    }

    @Test fun portalFailureRemainsDistinctAfterCaptchaRefresh() = runBlocking {
        val failure = SchoolAuthenticationException(SchoolAuthenticationStage.PORTAL, "学校请求失败（HTTP 503）。")
        var loads = 0
        var success = false
        val controller = LegacyLoginController(this, { captcha("challenge-${++loads}") }, { _, _, _, _ -> throw failure })
        controller.refreshCaptcha()
        yield()
        controller.changeCode("1234")
        controller.submit("student", "password") { success = true }
        yield()
        assertFalse(success)
        assertEquals(failure.message, controller.state.value.loginError)
        assertFalse(controller.state.value.loginError!!.contains("验证码错误"))
        assertEquals(2, loads)
        assertNotNull(controller.state.value.captcha)
        controller.cancel()
    }

    @Test fun captchaResponseFailureDisplaysItsStageAndCanBeRetried() = runBlocking {
        val failure = SchoolAuthenticationException(SchoolAuthenticationStage.CAPTCHA, "学校请求失败（HTTP 503）。")
        var loads = 0
        val controller = LegacyLoginController(this, {
            if (++loads == 1) throw failure else captcha("ready")
        }, { _, _, _, _ -> error("Not submitting") })
        controller.refreshCaptcha()
        yield()
        assertEquals(failure.message, controller.state.value.captchaError)
        assertNull(controller.state.value.loginError)
        assertNull(controller.state.value.captcha)
        controller.refreshCaptcha()
        yield()
        assertNull(controller.state.value.captchaError)
        assertNotNull(controller.state.value.captcha)
        controller.cancel()
    }

    @Test fun networkFailureRemainsVisibleWithoutRawResponseDetails() = runBlocking {
        val controller = LegacyLoginController(this, { captcha("ready") }, { _, _, _, _ ->
            throw IOException("private account or response")
        })
        controller.refreshCaptcha()
        yield()
        controller.changeCode("1234")
        controller.submit("student", "password") { error("Must not log in") }
        yield()
        assertEquals("连接学校登录服务失败，请检查网络后重试。", controller.state.value.loginError)
        assertFalse(controller.state.value.loginError!!.contains("private"))
        assertNotNull(controller.state.value.captcha)
        assertFalse(controller.state.value.submitting)
        controller.cancel()
    }
}
