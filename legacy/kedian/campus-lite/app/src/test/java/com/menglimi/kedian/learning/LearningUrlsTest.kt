package com.menglimi.kedian.learning

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningUrlsTest {
    @Test fun acceptsNormalLoginAndHttpsHomework() {
        listOf(
            LearningUrls.LOGIN,
            "https://i.chaoxing.com",
            "https://mooc1.chaoxing.com/mooc-ans/mooc2/work/task?workId=42&enc=synthetic",
            "https://passport2.chaoxing.com:443/login",
            "https://chaoxing.cn/",
            "https://sso.chaoxing.cn/login#section",
        ).forEach { assertNotNull(it, LearningUrls.trusted(it)) }
    }

    @Test fun rejectsOtherSchemesAndDomains() {
        listOf(
            "http://passport2.chaoxing.com/login",
            "javascript:alert(1)", "file:///data/data/private", "content://private",
            "intent://login#Intent;scheme=chaoxing;end", "data:text/html,login",
            "https://chaoxing.com.attacker.example/login",
            "https://notchaoxing.com/login", "https://chaoxing.com./login",
            "https://wallpaper.example/image", "https://www.gdust.edu.cn/",
            "https://127.0.0.1/login", "//passport2.chaoxing.com/login",
        ).forEach { assertNull(it, LearningUrls.trusted(it)) }
    }

    @Test fun rejectsCredentialsPortsAndAmbiguousSyntax() {
        listOf(
            "https://user:password@passport2.chaoxing.com/login",
            "https://@passport2.chaoxing.com/login",
            "https://passport2.chaoxing.com:8443/login",
            "https://passport2.chaoxing.com:80/login",
            "https://passport2.chaoxing.com\\@attacker.example/login",
            "https://passport2.chaoxing.com/../login",
            "https://passport2.chaoxing.com/./login",
            " https://passport2.chaoxing.com/login",
            "https://passport2.chaoxing.com/login\n",
            "https://passport2.chaoxing.com/%zz",
        ).forEach { assertNull(it, LearningUrls.trusted(it)) }
        assertNull(LearningUrls.trusted(null))
        assertNull(LearningUrls.trusted(""))
    }

    @Test fun webAllowanceDoesNotWidenReadOnlyApiHosts() {
        assertTrue(LearningUrls.allowedRequest("https://mooc2-ans.chaoxing.com/mooc2-ans/visit/interaction".toHttpUrl()))
        assertTrue(LearningUrls.allowedRequest("https://mooc1.chaoxing.com/mooc-ans/mooc2/work/task?workId=42".toHttpUrl()))
        assertFalse(LearningUrls.allowedRequest(LearningUrls.LOGIN.toHttpUrl()))
        assertFalse(LearningUrls.allowedRequest("https://i.chaoxing.com/".toHttpUrl()))
        assertFalse(LearningUrls.allowedRequest("https://mooc1.chaoxing.com:8443/mooc-ans/mooc2/work/task".toHttpUrl()))
    }

    @Test fun loginIsNotBoundToAnySchool() {
        val url = LearningUrls.LOGIN.toHttpUrl()
        assertTrue(url.host == "passport2.chaoxing.com")
        assertTrue(url.queryParameter("refer") == "https://i.chaoxing.com")
        assertFalse("fid" in url.queryParameterNames)
    }
}
