package com.menglimi.gdust.v2.data

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** 在 OkHttp 拦截器中返回合成响应，调用真实 API 方法；不会连接学校或提交真实凭据。 */
class SchoolAuthenticationApiTest {
    private val captchaBody = """{"code":0,"data":{"codeUrl":"data:image/png;base64,AQID","uuid":"fixture-uuid"}}"""
    private val ticketBody = """{"code":0,"data":"fixture-ticket+/="}"""
    private val portalBody = """{"code":0,"data":{"userBase":{"token":"fixture-token"}}}"""

    private fun api(respond: (Request) -> Pair<Int, String>): GdustApi = GdustApi(
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            check(request.url.host in setOf("cas.gdust.edu.cn", "portal.gdust.edu.cn"))
            val (status, body) = respond(request)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
                .body(body.toResponseBody("application/json; charset=utf-8".toMediaType())).build()
        }.build(),
    )

    @Test fun captchaLoginAndExchangeUseVerifiedFieldsAndEncodeTicketOnce() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = api { request ->
            requests += request
            200 to when (request.url.encodedPath) {
                "/cas-api/cas/loginCode" -> captchaBody
                "/cas-api/cas/loginByAccount" -> ticketBody
                "/smart-admin-api/user/login" -> portalBody
                else -> error("Unexpected endpoint")
            }
        }
        val challenge = client.captcha()
        assertArrayEquals(byteArrayOf(1, 2, 3), challenge.imageBytes)
        assertEquals("fixture-uuid", challenge.uuid)
        val password = " fixture\"\\\t\n\r\b\u0001密码 "
        val ticket = client.login("fixture-student", password, "1234", challenge.uuid)
        assertEquals("fixture-ticket+/=", ticket)
        assertEquals("fixture-token", client.exchangeToken(ticket))
        assertEquals(listOf("GET", "POST", "GET"), requests.map { it.method })
        assertTrue(requests.all { it.url.scheme == "https" && it.header("TOKEN") == null && it.header("Cookie") == null })
        val submitted = Buffer().also { requests[1].body!!.writeTo(it) }.readUtf8()
        // Gson 的严格解析验证控制字符正确转义，字段和密码不能被手拼 JSON 改写。
        val reader = com.google.gson.stream.JsonReader(submitted.reader()).apply { strictness = com.google.gson.Strictness.STRICT }
        val json = JsonParser.parseReader(reader).asJsonObject
        assertEquals(setOf("loginName", "loginPwd", "code", "uuid"), json.keySet())
        assertEquals(password, json.get("loginPwd").asString)
        assertEquals("fixture-student", json.get("loginName").asString)
        assertEquals("1234", json.get("code").asString)
        assertEquals(challenge.uuid, json.get("uuid").asString)
        assertTrue(requests[1].body!!.contentType().toString().startsWith("application/json"))
        assertEquals(ticket, requests[2].url.queryParameter("loginCode"))
        assertEquals("portalRemote", requests[2].url.queryParameter("appId"))
        assertEquals(3, requests.size)
    }

    @Test fun captchaRequiresExplicitSuccessAndCompleteChallenge() {
        val invalid = listOf("{}", "null", "[]", "<html>maintenance</html>",
            """{"data":{"codeUrl":"AQID","uuid":"fixture"}}""",
            """{"code":null,"data":{}}""", """{"code":"unknown","data":{}}""",
            """{"code":0,"data":null}""", """{"code":0,"data":[]}""",
            captchaBody.replace("fixture-uuid", " "), captchaBody.replace("AQID", ""),
            captchaBody.replace("AQID", "not base64!"), captchaBody.replace("\"fixture-uuid\"", "123"))
        for (body in invalid) {
            var count = 0
            val error = assertThrows(SchoolAuthenticationException::class.java) { runBlocking {
                api { count++; 200 to body }.captcha()
            } }
            assertEquals(SchoolAuthenticationStage.CAPTCHA, error.stage)
            assertEquals(1, count)
            assertFalse(error.message.orEmpty().contains("fixture"))
        }
    }

    @Test fun captchaBusinessRejectionKeepsCodeAndReason() {
        val error = assertThrows(AuthFailed::class.java) { runBlocking {
            api { 200 to """{"code":429,"msg":"请稍后再试","data":null}""" }.captcha()
        } }
        assertEquals(429, error.code)
        assertEquals("请稍后再试", error.message)
    }

    @Test fun accountRejectionsKeepBusinessCodeAndMessage() {
        for (code in listOf(30001, 30002)) {
            val error = assertThrows(AuthFailed::class.java) { runBlocking {
                api { 200 to """{"code":$code,"msg":"学校拒绝此请求","data":null}""" }
                    .login("fixture", "password", "1234", "fixture-uuid")
            } }
            assertEquals(code, error.code)
            assertEquals("学校拒绝此请求", error.message)
        }
    }

    @Test fun malformedAccountResponseNeverBecomesAnEmptyOrGuessedTicket() {
        for (body in listOf("{}", "<html>private-body</html>",
            """{"data":"private-ticket"}""", """{"code":null,"data":"private-ticket"}""",
            """{"code":false,"data":"private-ticket"}""", """{"code":0.5,"data":"private-ticket"}""",
            """{"code":0,"data":null}""", """{"code":0,"data":" "}""",
            """{"code":0,"data":42}""", """{"code":0,"data":{"ticket":"private-ticket"}}""")) {
            var count = 0
            val error = assertThrows(SchoolAuthenticationException::class.java) { runBlocking {
                val client = api { count++; 200 to body }
                client.exchangeToken(client.login("fixture", "password", "1234", "fixture-uuid"))
            } }
            assertEquals(SchoolAuthenticationStage.ACCOUNT, error.stage)
            assertEquals(1, count)
            assertFalse(error.message.orEmpty().contains("private"))
        }
    }

    @Test fun emptyInputIsRejectedBeforeTransport() {
        var count = 0
        val client = api { count++; 200 to ticketBody }
        for (args in listOf(listOf("", "p", "c", "u"), listOf("a", " ", "c", "u"),
            listOf("a", "p", "", "u"), listOf("a", "p", "c", ""))) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking {
                client.login(args[0], args[1], args[2], args[3])
            } }
        }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { client.exchangeToken(" ") } }
        assertEquals(0, count)
    }

    @Test fun portalAuthenticationRejectionHasPortalSpecificReason() {
        for ((status, body, code) in listOf(
            Triple(401, "private-body", 401),
            Triple(200, """{"code":116,"msg":"private-ticket","data":null}""", 116),
            Triple(200, """{"code":117,"msg":"private-ticket","data":null}""", 117),
        )) {
            val error = assertThrows(AuthFailed::class.java) { runBlocking {
                api { status to body }.exchangeToken("fixture-ticket")
            } }
            assertEquals(code, error.code)
            assertTrue(error.message.orEmpty().contains("门户"))
            assertFalse(error.message.orEmpty().contains("private"))
        }
    }

    @Test fun portalBusinessFailuresCannotReturnTokenEvenWithSuccessShapedData() {
        val error = assertThrows(SchoolAuthenticationException::class.java) { runBlocking {
            api { 200 to portalBody.replace("\"code\":0", "\"code\":500") }.exchangeToken("fixture-ticket")
        } }
        assertEquals(SchoolAuthenticationStage.PORTAL, error.stage)
        assertTrue(error.message.orEmpty().contains("500"))
        assertFalse(error.message.orEmpty().contains("fixture-token"))
    }

    @Test fun malformedPortalDataIsNotAUsableSession() {
        for (body in listOf("{}", "null", "private-body", """{"code":0}""",
            """{"code":0,"data":null}""", """{"code":0,"data":[]}""",
            """{"code":0,"data":{"userBase":null}}""",
            """{"code":0,"data":{"userBase":{"token":false}}}""",
            portalBody.replace("fixture-token", " "))) {
            val error = assertThrows(SchoolAuthenticationException::class.java) { runBlocking {
                api { 200 to body }.exchangeToken("fixture-ticket")
            } }
            assertEquals(SchoolAuthenticationStage.PORTAL, error.stage)
            assertFalse(error.message.orEmpty().contains("private"))
        }
    }

    @Test fun httpFailuresKeepTheirStageAndDoNotExposeResponseBody() {
        for (status in listOf(302, 401, 403, 429, 503)) {
            val client = api { status to "private-body" }
            val captcha = assertThrows(SchoolAuthenticationException::class.java) { runBlocking { client.captcha() } }
            val account = assertThrows(SchoolAuthenticationException::class.java) { runBlocking {
                client.login("fixture", "password", "1234", "fixture-uuid")
            } }
            assertEquals(SchoolAuthenticationStage.CAPTCHA, captcha.stage)
            assertEquals(SchoolAuthenticationStage.ACCOUNT, account.stage)
            for (error in listOf(captcha, account)) {
                assertTrue(error.message.orEmpty().contains("HTTP $status"))
                assertFalse(error.message.orEmpty().contains("private"))
            }
        }
    }

    @Test fun transportFailuresKeepStageWithoutExposingPrivateUrl() {
        val client = api { throw IOException("private-ticket in URL") }
        val errors = listOf(
            assertThrows(SchoolAuthenticationException::class.java) { runBlocking { client.captcha() } },
            assertThrows(SchoolAuthenticationException::class.java) { runBlocking {
                client.login("fixture", "password", "1234", "fixture-uuid")
            } },
            assertThrows(SchoolAuthenticationException::class.java) { runBlocking { client.exchangeToken("fixture-ticket") } },
        )
        assertEquals(SchoolAuthenticationStage.entries.toList(), errors.map { it.stage })
        assertTrue(errors.all { it.isNetworkDown() })
        assertTrue(errors.all { !it.message.orEmpty().contains("private") })
    }
}
