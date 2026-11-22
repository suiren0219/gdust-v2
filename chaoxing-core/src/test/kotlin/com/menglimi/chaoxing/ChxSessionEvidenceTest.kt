package com.menglimi.chaoxing

import okhttp3.HttpUrl.Companion.toHttpUrl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChxSessionEvidenceTest {
    // 仅保留已有 HAR 的结构；测试值为人工构造，不含账号、Cookie 或原始校验值。
    private val signedInMarkup = """
        <html><body>
        <input type="hidden" id="stuPageHeader" value="1">
        <input type="hidden" id="loadStuJobInfo" value="1">
        <input type="hidden" id="checkUserToken" value="0123456789abcdef0123456789abcdef">
        </body></html>
    """.trimIndent()

    @Test fun verifiedHomepageStructureIsPositiveEvidence() {
        assertEquals(true, ChxApi.sessionEvidence(200, ChxSso.HOME, signedInMarkup))
        assertEquals(true, ChxApi.sessionEvidence(200, ChxSso.HOME, signedInMarkup.replace('"', '\'')))
    }

    @Test fun arbitraryHtmlEmptyResponsesAndTemplatePlaceholdersRemainUnknown() {
        listOf(null, "", " ", "<html>系统维护，请稍后重试</html>", "<html>欢迎使用学习通</html>",
            "<a>用户登录</a>", "<p>已完成登录后可进入首页</p>",
            signedInMarkup.replace("0123456789abcdef0123456789abcdef", ""),
            signedInMarkup.replace("0123456789abcdef0123456789abcdef", "00000000000000000000000000000000"),
            signedInMarkup.replace("0123456789abcdef0123456789abcdef", "\${checkUserToken}"),
            signedInMarkup.replace("id=\"stuPageHeader\"", "id=\"other\""),
            "<!-- $signedInMarkup -->", "<script>const sample = `$signedInMarkup`;</script>",
        ).forEach { assertNull(ChxApi.sessionEvidence(200, ChxSso.HOME, it)) }
    }

    @Test fun onlyKnownLoginRedirectsAreExplicitExpiry() {
        listOf("https://passport2.chaoxing.com/login?refer=anything", "/login",
            "https://login.example.org/cas/", "https://passport2.chaoxing.com/mlogin",
            "https://login.example.org/lyuapServer/login?service=anything").forEach {
            assertEquals(false, ChxApi.sessionEvidence(302, ChxSso.HOME, null, it) { url ->
                url.toHttpUrl().host == "login.example.org"
            })
        }
        listOf(null, "/maintenance", "https://example.org/login", "/home?refer=login",
            "https://notchaoxing.com/login", "https://passport2.chaoxing.com/privacy",
            "http://passport2.chaoxing.com/login", "https://passport2.chaoxing.com:8443/login",
            "https://user@passport2.chaoxing.com/login", "http://login.example.org/lyuapServer/login",
            "https://login.example.org:8443/lyuapServer/login").forEach {
            assertNull(ChxApi.sessionEvidence(302, ChxSso.HOME, null, it) { url ->
                url.toHttpUrl().host == "login.example.org"
            })
        }
    }

    @Test fun additionalAuthenticationOriginsAreOptInAndStillRequireSecureUrls() {
        val login = "https://login.example.org/cas/login"
        assertNull(ChxApi.sessionEvidence(302, ChxSso.HOME, null, login))
        assertEquals(false, ChxApi.sessionEvidence(302, ChxSso.HOME, null, login) { it == login })
        listOf("http://login.example.org/cas/login", "https://login.example.org:8443/cas/login",
            "https://user@login.example.org/cas/login").forEach { target ->
            assertNull(ChxApi.sessionEvidence(302, ChxSso.HOME, null, target) { true })
        }
    }

    @Test fun explicitLoginFormBeatsStaleHomepageMarkers() {
        val form = "<form><input type='password' name='pwd'><button>用户登录</button></form>"
        assertEquals(false, ChxApi.sessionEvidence(200, ChxSso.HOME, form))
        assertEquals(false, ChxApi.sessionEvidence(200, ChxSso.HOME, signedInMarkup + form))
    }

    @Test fun httpFailuresAndUnexpectedOriginsCannotValidateASession() {
        assertEquals(false, ChxApi.sessionEvidence(401, ChxSso.HOME, null))
        listOf(403, 404, 429, 500, 502, 503).forEach {
            assertNull(ChxApi.sessionEvidence(it, ChxSso.HOME, signedInMarkup))
        }
        listOf("https://example.org/mooc2-ans/visit/interaction", "http://mooc2-ans.chaoxing.com/mooc2-ans/visit/interaction",
            "https://mooc2-ans.chaoxing.com/login", "https://mooc2-ans.chaoxing.com:8443/mooc2-ans/visit/interaction").forEach {
            assertNull(ChxApi.sessionEvidence(200, it, signedInMarkup))
        }
    }
}
