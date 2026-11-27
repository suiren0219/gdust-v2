package com.menglimi.gdust.v2.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmRequestTest {
    private val gson = Gson()

    @Test
    fun newlinesAndQuotesInNoticesStayValidJson() {
        val items = "公告\"A\"\n- [通知] 停课\r\n\t第三条"
        val body = gson.toJson(mapOf(
            "model" to "test-model",
            "messages" to listOf(
                mapOf("role" to "system", "content" to "按类别分组\n每条一句话"),
                mapOf("role" to "user", "content" to "最近的学校通知公告：\n$items"),
            ),
            "max_tokens" to 2048,
            "temperature" to 0.3,
        ))
        val parsed = JsonParser.parseString(body).asJsonObject
        assertEquals("test-model", parsed.get("model").asString)
        val messages = parsed.getAsJsonArray("messages")
        assertEquals(2, messages.size())
        assertTrue(messages[1].asJsonObject.get("content").asString.contains("公告\"A\""))
        assertTrue(messages[1].asJsonObject.get("content").asString.contains("\n"))
    }

    @Test
    fun endpointNormalizationCoversCommonForms() {
        val cases = mapOf(
            "https://api.example.com" to "https://api.example.com/v1",
            " https://api.example.com/v1/ " to "https://api.example.com/v1",
            "https://api.example.com/v1/chat/completions/" to "https://api.example.com/v1",
            "https://api.example.com/v1/models" to "https://api.example.com/v1",
            "https://api.example.com/api/v3" to "https://api.example.com/api/v3",
            "https://api.example.com/api/v3/models/" to "https://api.example.com/api/v3",
            "https://api.example.com/openai/v1/chat/completions" to "https://api.example.com/openai/v1",
            "https://api.example.com/models" to "https://api.example.com",
            "http://localhost:1234/chat/completions" to "http://localhost:1234",
        )
        cases.forEach { (input, expected) -> assertEquals(expected, normalizeLlmEndpoint(input)) }
    }

    private val fixtureKey = "fixture-key-do-not-display"
    private val completion = """{"choices":[{"message":{"role":"assistant","content":"OK"}}]}"""

    /** 所有请求在应用拦截器返回，不进行 DNS 查询或访问外网。 */
    private fun api(respond: (Request) -> Pair<Int, String>): GdustApi = GdustApi(
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            check(request.url.host == "api.example.com")
            val (status, body) = respond(request)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
                .body(body.toResponseBody("application/json; charset=utf-8".toMediaType())).build()
        }.build(),
    )

    @Test
    fun unsafeEndpointsAndInvalidKeysFailBeforeAnyRequest() {
        var calls = 0
        val client = api { calls++; 200 to """{"data":[]}""" }
        val invalid = listOf("", "api.example.com", "ftp://api.example.com/v1",
            "https://" + "fixture-user" + '@' + "api.example.com/v1",
            "https://" + "fixture-user:fixture-password" + '@' + "api.example.com/v1",
            "https://" + '@' + "api.example.com/v1",
            "https://user:secret@api.example.com/v1", "https://@api.example.com/v1",
            "https://api.example.com/v1?key=secret", "https://api.example.com/v1?",
            "https://api.example.com/v1#secret", "https://api.example.com/v1#",
            "https://api.example.com/pa\nth", "https://api.example.com\\v1")
        invalid.forEach { base ->
            val error = assertThrows(LlmException::class.java) { runBlocking { client.models(base, fixtureKey) } }
            assertFalse(error.toString().contains("secret"))
            assertEquals(null, error.cause)
        }
        listOf("", "a\nb", "a b", "中文密钥").forEach { key ->
            assertThrows(LlmException::class.java) { runBlocking { client.models("https://api.example.com", key) } }
        }
        assertEquals(0, calls)
    }

    @Test
    fun modelListUsesActualGetAndPreservesPrefix() = runBlocking {
        val client = api { request ->
            assertEquals("https://api.example.com/api/v3/models", request.url.toString())
            assertEquals("GET", request.method)
            assertEquals("Bearer $fixtureKey", request.header("Authorization"))
            assertEquals(null, request.body)
            200 to """{"data":[{"id":"z-model"},{"id":"a-model"},{"id":"z-model"},{"id":" "},{"id":42},{"id":null},null,{"id":"$fixtureKey"}]}"""
        }
        assertEquals(listOf("a-model", "z-model"), client.models("https://api.example.com/api/v3/chat/completions", fixtureKey))
    }

    @Test
    fun completionUsesProductionSerializationAndSharedNormalization() = runBlocking {
        val text = "公告\"A\"\n停课\r\n\t第三条"
        val client = api { request ->
            assertEquals("https://api.example.com/openai/v1/chat/completions", request.url.toString())
            val json = JsonParser.parseString(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).asJsonObject
            assertEquals(text, json.getAsJsonArray("messages")[0].asJsonObject.get("content").asString)
            assertEquals("chosen-model", json.get("model").asString)
            assertEquals(2048, json.get("max_tokens").asInt)
            200 to completion
        }
        assertEquals("OK", client.complete("https://api.example.com/openai/v1/models", fixtureKey,
            "chosen-model", listOf(AiChatMessage("user", text))))
    }

    @Test
    fun probeUsesSelectedModelAndOnlySyntheticContent() = runBlocking {
        var calls = 0
        val client = api { request ->
            calls++
            assertEquals("POST", request.method)
            assertEquals("https://api.example.com/api/v3/chat/completions", request.url.toString())
            val json = JsonParser.parseString(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).asJsonObject
            assertEquals("selected-model", json.get("model").asString)
            assertEquals(8, json.get("max_tokens").asInt)
            assertFalse(json.get("stream").asBoolean)
            assertEquals(setOf("model", "messages", "max_tokens", "stream"), json.keySet())
            val messages = json.getAsJsonArray("messages")
            assertEquals(1, messages.size())
            assertEquals("user", messages[0].asJsonObject.get("role").asString)
            assertEquals("Reply OK.", messages[0].asJsonObject.get("content").asString)
            200 to completion
        }
        val result = client.probe("https://api.example.com/api/v3", fixtureKey, "selected-model")
        assertTrue(result.success)
        assertTrue(result.latencyMs >= 0)
        assertEquals(null, result.error)
        assertEquals(1, calls)
    }

    @Test
    fun httpFailuresReturnOnlyLocalHints() = runBlocking {
        for (status in listOf(301, 302, 307, 308, 400, 401, 402, 403, 404, 429, 503)) {
            val client = api { status to """{"error":{"message":"raw-server-text $fixtureKey private"}}""" }
            val result = client.probe("https://api.example.com", fixtureKey, "model")
            assertFalse(result.success)
            assertTrue(result.error.orEmpty().contains("HTTP $status"))
            assertFalse(result.error.orEmpty().contains(fixtureKey))
            assertFalse(result.error.orEmpty().contains("raw-server-text"))
            val error = assertThrows(LlmException::class.java) { runBlocking { client.models("https://api.example.com", fixtureKey) } }
            generateSequence<Throwable>(error) { it.cause }.forEach { safeError ->
                assertTrue(safeError is LlmException)
                assertFalse(safeError.toString().contains(fixtureKey))
                assertFalse(safeError.toString().contains("raw-server-text"))
            }
        }
    }

    @Test
    fun probeRequiresValidNonemptyCompletionInsteadOfHttpSuccess() = runBlocking {
        val invalid = listOf("null", "[]", "<html>raw-server-text $fixtureKey</html>",
            "{}", """{"choices":[]}""", """{"choices":[{"message":{"content":""}}]}""",
            """{"choices":[{"message":{"content":123}}]}""", """{"data":[{"id":"model"}]}""")
        invalid.forEach { body ->
            val result = api { 200 to body }.probe("https://api.example.com", fixtureKey, "model")
            assertFalse(result.success)
            assertFalse(result.error.orEmpty().contains("raw-server-text"))
            assertFalse(result.error.orEmpty().contains(fixtureKey))
        }
    }

    @Test
    fun modelListDistinguishesEmptyAndIncompatibleResponses() = runBlocking {
        assertEquals(emptyList<String>(), api { 200 to """{"data":[]}""" }.models("https://api.example.com", fixtureKey))
        for (body in listOf("{}", "null", "[]", """{"data":null}""", """{"data":{}}""")) {
            assertThrows(LlmException::class.java) { runBlocking {
                api { 200 to body }.models("https://api.example.com", fixtureKey)
            } }
        }
    }

    @Test
    fun oversizedResponsesFailWithoutReturningAnyServerText() = runBlocking {
        val client = api { 200 to "x".repeat(MAX_LLM_RESPONSE_BYTES.toInt() + 1) }
        val error = assertThrows(LlmException::class.java) { runBlocking { client.models("https://api.example.com", fixtureKey) } }
        assertTrue(error.message.orEmpty().contains("大小限制"))
        val result = api { 200 to "x".repeat(64 * 1024 + 1) }.probe("https://api.example.com", fixtureKey, "model")
        assertFalse(result.success)
        assertTrue(result.error.orEmpty().contains("大小限制"))
    }

    @Test
    fun unknownLengthResponseIsBoundedAndClosed() {
        var closed = false
        val source = object : okio.ForwardingSource(Buffer().writeUtf8("x".repeat(MAX_LLM_RESPONSE_BYTES.toInt() + 1))) {
            override fun close() { closed = true; super.close() }
        }
        val body = object : okhttp3.ResponseBody() {
            private val buffered = source.buffer()
            override fun contentType() = "application/json".toMediaType()
            override fun contentLength() = -1L
            override fun source() = buffered
        }
        val client = GdustApi(OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body(body).build()
        }.build())
        val error = assertThrows(LlmException::class.java) { runBlocking {
            client.models("https://api.example.com", fixtureKey)
        } }
        assertTrue(error.message.orEmpty().contains("大小限制"))
        assertTrue(closed)
    }

    @Test
    fun coroutineCancellationCancelsUnderlyingCall() = runBlocking {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val cancelled = java.util.concurrent.CountDownLatch(1)
        val client = GdustApi(OkHttpClient.Builder()
            .eventListener(object : okhttp3.EventListener() {
                override fun canceled(call: okhttp3.Call) { cancelled.countDown() }
            })
            .addInterceptor { chain ->
                entered.countDown()
                release.await(5, java.util.concurrent.TimeUnit.SECONDS)
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                    .body(completion.toResponseBody("application/json".toMediaType())).build()
            }.build())
        val request = async(Dispatchers.Default) { client.probe("https://api.example.com", fixtureKey, "model") }
        try {
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
            request.cancelAndJoin()
            assertTrue(request.isCancelled)
            assertTrue(cancelled.await(3, java.util.concurrent.TimeUnit.SECONDS))
        } finally {
            request.cancel()
            release.countDown()
        }
    }

    @Test
    fun savingValidationIsEntirelyLocal() {
        validateLlmConfiguration("https://api.example.com/api/v3/models", fixtureKey, "manual-model")
        assertThrows(LlmException::class.java) {
            validateLlmConfiguration("https://api.example.com?secret=value", fixtureKey, "manual-model")
        }
    }
}
