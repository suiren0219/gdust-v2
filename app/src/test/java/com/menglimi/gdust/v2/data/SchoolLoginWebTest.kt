package com.menglimi.gdust.v2.data

import com.google.gson.Gson
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolLoginWebTest {
    @Test
    fun portalEntryUsesFormalCallbackEncodedExactlyOnce() {
        val entry = SchoolLoginWeb.entry(SchoolService.PORTAL).toHttpUrl()
        assertEquals("https", entry.scheme)
        assertEquals("cas.gdust.edu.cn", entry.host)
        assertEquals("/cas/", entry.encodedPath)
        assertEquals(SchoolLoginWeb.PORTAL_CALLBACK, entry.queryParameter("service"))
        val callback = entry.queryParameter("service")!!.toHttpUrl()
        assertEquals("/smart-admin-api/user/ssoLogin/", callback.encodedPath)
        assertEquals("portalRemote", callback.queryParameter("appId"))
        assertNull(callback.queryParameter("ticket"))
        assertNull(callback.queryParameter("loginCode"))
        assertEquals(ChxSso.teachingWebUrl, SchoolLoginWeb.entry(SchoolService.TEACHING))
        assertEquals(ChxSso.fyssoUrl, SchoolLoginWeb.entry(SchoolService.CHX))
    }

    @Test
    fun portalTokenRequiresBothExactPageOriginAndScriptOrigin() {
        val valid = sessionResult(SchoolLoginWeb.PORTAL_ORIGIN, "fixture-token")
        for (url in listOf("https://portal.gdust.edu.cn/", "https://portal.gdust.edu.cn:443/#/home")) {
            assertTrue(SchoolLoginWeb.isPortalPage(url))
            assertEquals("fixture-token", SchoolLoginWeb.portalToken(url, valid))
        }
        for (url in listOf(
            "http://portal.gdust.edu.cn/", "https://portal.gdust.edu.cn:444/",
            "https://portal.gdust.edu.cn.evil.test/", "https://evil.test/?next=https://portal.gdust.edu.cn",
            "https://portal.gdust.edu.cn@evil.test/", "https://user@portal.gdust.edu.cn/",
            "https://portal.gdust.edu.cn./", "https://sub.portal.gdust.edu.cn/",
            "https://cas.gdust.edu.cn/cas/", "file:///portal.gdust.edu.cn", "about:blank",
        )) {
            assertFalse(url, SchoolLoginWeb.isPortalPage(url))
            assertNull(url, SchoolLoginWeb.portalToken(url, valid))
        }
        for (origin in listOf("https://portal.gdust.edu.cn/", "http://portal.gdust.edu.cn",
            "https://portal.gdust.edu.cn:444", "https://portal.gdust.edu.cn.evil.test", "null")) {
            assertNull(SchoolLoginWeb.portalToken(SchoolLoginWeb.PORTAL_ORIGIN, sessionResult(origin, "fixture-token")))
        }
    }

    @Test
    fun malformedOrNonHeaderTokenIsNeverAccepted() {
        for (result in listOf(null, "", "null", "[]", "{", "true", "42", "{}",
            "\"fixture-token\"", "{\"origin\":[],\"token\":\"fixture\"}",
            "{\"origin\":\"https://portal.gdust.edu.cn\",\"token\":123}",
            "x".repeat(20_001))) {
            assertNull(SchoolLoginWeb.portalToken(SchoolLoginWeb.PORTAL_ORIGIN, result))
        }
        for (token in listOf("", " ", " fixture", "a b", "a\nb", "a\rb", "a\tb", "a\u007fb", "a".repeat(16_385))) {
            assertNull(SchoolLoginWeb.portalToken(SchoolLoginWeb.PORTAL_ORIGIN,
                sessionResult(SchoolLoginWeb.PORTAL_ORIGIN, token)))
        }
        val token = "fixture+a/b=c_d-9"
        assertEquals(token, SchoolLoginWeb.portalToken(SchoolLoginWeb.PORTAL_ORIGIN,
            sessionResult(SchoolLoginWeb.PORTAL_ORIGIN, token)))
    }

    @Test
    fun onlyFragmentChangesPreserveTheCurrentDocumentProbe() {
        val page = "https://portal.gdust.edu.cn/"
        assertTrue(SchoolLoginWeb.isFragmentNavigation(page, "$page#/home"))
        assertTrue(SchoolLoginWeb.isFragmentNavigation("$page#/login", "$page#/home"))
        assertFalse(SchoolLoginWeb.isFragmentNavigation(page, page))
        assertFalse(SchoolLoginWeb.isFragmentNavigation(null, "$page#/home"))
        assertFalse(SchoolLoginWeb.isFragmentNavigation(page, "${page}new#/home"))
        assertFalse(SchoolLoginWeb.isFragmentNavigation(page, "$page?ticket=fixture#/home"))
        assertFalse(SchoolLoginWeb.isFragmentNavigation(page, "https://other.test/#/home"))
    }

    @Test
    fun servicePageMatchingRejectsSpoofedOriginsAndWrongPorts() {
        assertTrue(SchoolLoginWeb.isServicePage(SchoolService.TEACHING,
            "http://gkjx.gdust.edu.cn/xtgl/index_initMenu.html"))
        assertTrue(SchoolLoginWeb.isServicePage(SchoolService.CHX, ChxSso.HOME))
        for (url in listOf("https://notchaoxing.com/", "https://chaoxing.com.evil.test/",
            "https://i.chaoxing.com:8443/", "https://user@i.chaoxing.com/", "file://i.chaoxing.com/")) {
            assertFalse(url, SchoolLoginWeb.isServicePage(SchoolService.CHX, url))
        }
        for (url in listOf("http://gkjx.gdust.edu.cn:81/xtgl/index_initMenu.html",
            "http://gkjx.gdust.edu.cn.evil.test/kbcx/", "http://user@gkjx.gdust.edu.cn/kbcx/",
            "https://webvpn.gdust.edu.cn/http/unrelated/kbcx/", "http://gkjx.gdust.edu.cn/sso/lyiotlogin")) {
            assertFalse(url, SchoolLoginWeb.isServicePage(SchoolService.TEACHING, url))
        }
    }

    @Test
    fun authenticationPageIsRecognizedBeforeSavedTokenOrCookiesCanBeProbed() {
        for (url in listOf("https://cas.gdust.edu.cn/cas/", ChxSso.teachingWebUrl,
            "http://gkjx.gdust.edu.cn/xtgl/login_slogin.html", ChxSso.DIRECT_LOGIN)) {
            assertTrue(url, SchoolLoginWeb.isAuthenticationPage(url))
        }
        for (url in listOf("https://cas.gdust.edu.cn.evil.test/cas/",
            "https://cas.gdust.edu.cn:444/cas/", "https://user@cas.gdust.edu.cn/cas/",
            "http://gkjx.gdust.edu.cn/xtgl/index_initMenu.html?next=/xtgl/login_slogin.html",
            "https://portal.gdust.edu.cn/#/login")) {
            assertFalse(url, SchoolLoginWeb.isAuthenticationPage(url))
        }
    }

    private fun sessionResult(origin: String, token: String) = Gson().toJson(mapOf("origin" to origin, "token" to token))
}
