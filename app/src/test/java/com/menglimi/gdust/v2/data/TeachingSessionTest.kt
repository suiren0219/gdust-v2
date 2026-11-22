package com.menglimi.gdust.v2.data

import java.time.LocalDate
import okhttp3.FormBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TeachingSessionTest {
    @Test
    fun probeUsesFixedBusinessEndpointAtTheExactLandingOrigin() {
        val calendar = CalendarInfo(1, 20, "2026-09-01", "2026", "3")
        for (base in TeachingEndpoints.bases) {
            val request = requireNotNull(teachingProbeRequest(
                "$base/xtgl/index_initMenu.html?ticket=fixture&next=https://evil.test/#login", calendar))
            assertEquals("$base/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151", request.url.toString())
            assertEquals("POST", request.method)
            assertEquals("XMLHttpRequest", request.header("X-Requested-With"))
            assertNull(request.header("Cookie"))
            assertNull(request.header("TOKEN"))
            assertNull(request.url.queryParameter("ticket"))
            val form = request.body as FormBody
            assertEquals(mapOf("xnm" to "2026", "xqm" to "3", "kzlx" to "ck", "xsdm" to "", "kclbdm" to ""),
                (0 until form.size).associate { form.name(it) to form.value(it) })
        }
    }

    @Test
    fun probingWithoutCalendarOnlyComputesReadParameters() {
        for ((date, expected) in listOf(
            LocalDate.of(2026, 1, 15) to ("2025" to "3"),
            LocalDate.of(2026, 3, 15) to ("2025" to "12"),
            LocalDate.of(2026, 9, 15) to ("2026" to "3"),
        )) {
            val request = requireNotNull(teachingProbeRequest(PUBLIC_TEACHING_ENDPOINT, null, date))
            val form = request.body as FormBody
            assertEquals(expected.first, form.value(0))
            assertEquals(expected.second, form.value(1))
        }
    }

    @Test
    fun loginPagesTransitPagesAndUntrustedOriginsCannotCreateProbeRequests() {
        for (url in listOf("http://gkjx.gdust.edu.cn/xtgl/login_slogin.html",
            "http://gkjx.gdust.edu.cn/sso/lyiotlogin?ticket=fixture", "https://cas.gdust.edu.cn/cas/",
            "https://portal.gdust.edu.cn/", "http://gkjx.gdust.edu.cn:81/kbcx/",
            "http://gkjx.gdust.edu.cn.evil.test/kbcx/", "http://user@gkjx.gdust.edu.cn/kbcx/",
            "https://webvpn.gdust.edu.cn/http/unknown/kbcx/", "file:///kbcx/")) {
            assertNull(url, teachingProbeRequest(url, null))
        }
        assertNotNull(teachingProbeRequest(PUBLIC_TEACHING_ENDPOINT, null))
    }

    @Test
    fun emptyAndNonEmptyBusinessArraysAreEvidenceButNotPersistedSchedules() {
        for (body in listOf("{\"kbList\":[]}", "{\"kbList\":[{\"kcmc\":\"测试课程\"}]}",
            "{\"code\":0,\"status\":\"200\",\"success\":true,\"kbList\":[]}",
            " \n\uFEFF{\"kbList\":[]} ")) {
            assertEquals(true, teachingSessionEvidence(response(body)))
        }
    }

    @Test
    fun explicitAuthRefusalIsFalseIncludingWrappedJsonAndLoginRedirects() {
        for (reply in listOf(SessionResponse(401, "", null), SessionResponse(901, "", null),
            response("401"), response("\"901\""), response("{\"code\":401,\"kbList\":[]}"),
            response("{\"statusCode\":\"901\",\"kbList\":[]}"),
            response("{\"code\":0,\"data\":{\"status\":401},\"kbList\":[]}"),
            SessionResponse(302, "", "/xtgl/login_slogin.html"),
            SessionResponse(302, "", "https://cas.gdust.edu.cn/cas/?service=fixture"))) {
            assertEquals(false, teachingSessionEvidence(reply))
        }
    }

    @Test
    fun loginHtmlAtHttp200NeverCountsAsLoggedIn() {
        for (body in listOf("<html><input type='password'></html>",
            "<html><title>广科统一身份认证</title></html>",
            "<html><form action='/xtgl/login_slogin.html'></form></html>",
            "<script>top.location.href='/xtgl/login_slogin.html';</script>",
            "<meta http-equiv='refresh' content='0;URL=/xtgl/login_slogin.html'>")) {
            assertEquals(false, teachingSessionEvidence(response(body)))
        }
    }

    @Test
    fun maintenanceRedirectAndTransportFailuresAreUnknownNotExpired() {
        for (reply in listOf(SessionResponse(302, "", "/maintenance"),
            SessionResponse(302, "", "https://cas.gdust.edu.cn.evil.test/cas/"),
            SessionResponse(302, "", "https://cas.gdust.edu.cn:444/cas/"),
            SessionResponse(302, "", null), SessionResponse(403, "", null),
            SessionResponse(500, "{\"kbList\":[]}", null),
            SessionResponse(503, "{\"code\":401}", null),
            response("<html><h1>系统维护，请稍后登录</h1></html>"),
            response("<html><h1>教务首页</h1></html>"))) {
            assertNull(teachingSessionEvidence(reply))
        }
    }

    @Test
    fun malformedUnknownOrContradictoryJsonNeverCountsAsSuccess() {
        for (body in listOf("", "null", "{}", "[]", "{", "not-json", "{\"kbList\":{}}",
            "{\"kbList\":null}", "{\"kbList\":[null]}", "{\"kbList\":[\"fixture\"]}",
            "{\"data\":{\"kbList\":[]}}", "{\"code\":503,\"kbList\":[]}",
            "{\"code\":{},\"kbList\":[]}", "{\"statusCode\":null,\"kbList\":[]}",
            "{\"success\":false,\"kbList\":[]}", "{\"success\":\"false\",\"kbList\":[]}",
            "{\"success\":{},\"kbList\":[]}")) {
            assertNull(body, teachingSessionEvidence(response(body)))
        }
        assertFalse(teachingSessionEvidence(response("{\"kbList\":[]}")) == false)
    }

    private fun response(body: String) = SessionResponse(200, body, null)
}
