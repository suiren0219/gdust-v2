package com.menglimi.power

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.IOException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerApiTest {
    private val gson = Gson()
    private val clock = Clock.fixed(Instant.parse("2026-11-15T04:05:06.007Z"), ZoneId.of("Asia/Shanghai"))
    private val clients = mutableListOf<OkHttpClient>()
    private val requests = CopyOnWriteArrayList<Request>()

    @After fun closeClients() {
        clients.forEach {
            it.dispatcher.executorService.shutdownNow()
            it.connectionPool.evictAll()
        }
    }

    private fun response(request: Request, body: String, status: Int = 200): Response = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
        .body(body.toResponseBody("application/json".toMediaType())).build()

    /** 所有测试均在应用拦截器结束，绝不 chain.proceed 或连接真实服务。 */
    private fun api(inner: String, status: Int = 200): PowerApi =
        rawApi(gson.toJson(mapOf("body" to inner)), status)

    private fun rawApi(body: String, status: Int = 200): PowerApi {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            response(chain.request(), body, status)
        }.build().also(clients::add)
        return PowerApi(client, clock)
    }

    private fun assertPrivateFailure(body: String, expectedMessage: String? = null) {
        val error = assertThrows(IOException::class.java) {
            runBlocking { rawApi(body).detail("private-account", 999, "private-verify") }
        }
        assertFalse(error.message.orEmpty().contains("private"))
        assertNull(error.cause)
        if (expectedMessage != null) assertEquals(expectedMessage, error.message)
    }

    private fun scriptedApi(
        backoff: suspend () -> Unit,
        reply: (Request, Int) -> Response,
    ): PowerApi {
        val attempts = AtomicInteger()
        val client = OkHttpClient.Builder().retryOnConnectionFailure(true).addInterceptor { chain ->
            requests += chain.request()
            reply(chain.request(), attempts.incrementAndGet())
        }.build().also(clients::add)
        return PowerApi(client, clock, backoff)
    }

    private fun form(request: Request): Map<String, String> {
        val body = request.body as FormBody
        return (0 until body.size).associate { body.name(it) to body.value(it) }
    }

    @Test fun roomsUsesVerifiedFormAndEscapesAccountWithoutSchoolLogin() = runBlocking {
        val account = "学号+\"\\\n&=<>"
        val rooms = api("""{"roomlist":[{"roomfullname":"北区 101","roomverify":"v+1"},{"roomfullname":null,"roomverify":null}]}""")
            .rooms(account, 123)
        assertEquals(listOf(PowerRoom("北区 101", "v+1"), PowerRoom("", "")), rooms)
        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("https://xqh5.17wanxiao.com/smartWaterAndElectricityService/SWAEServlet", request.url.toString())
        assertEquals("Mozilla/5.0 (Linux; Android 13)", request.header("User-Agent"))
        assertNull(request.header("TOKEN"))
        assertNull(request.header("Authorization"))
        assertNull(request.header("Cookie"))
        val fields = form(request)
        assertEquals(setOf("param", "customercode", "method", "command"), fields.keys)
        assertEquals("123", fields["customercode"])
        assertEquals("getbindroom", fields["method"])
        assertEquals("JBSWaterElecService", fields["command"])
        val parameter = JsonParser.parseString(fields.getValue("param")).asJsonObject
        assertEquals(setOf("cmd", "account"), parameter.keySet())
        assertEquals(account, parameter["account"].asString)
        assertEquals("getbindroom", parameter["cmd"].asString)
    }

    @Test fun detailUsesInjectedClockAndFirstMeterWithLastMonth() = runBlocking {
        val detail = api("""{"modlist":[{"odd":"12.50","todayuse":"2.25","weekuselist":[{"weekday":"星期一","dayuse":"1.5"},{"weekday":"星期二","use":2},{"dayuse":99},{"weekday":"星期三","dayuse":null}],"monthuselist":[{"monthuse":"1"},{"monthuse":"42.5"}],"sumbuy":"300"},{"odd":"999"}]}""")
            .detail("a+\"\n", 321, "r+&=\\\"")
        assertEquals(PowerDetail(12.5, 2.25, listOf("一" to 1.5, "二" to 2.0), 42.5, 300.0), detail)
        val fields = form(requests.single())
        assertEquals("h5_getstuindexpage", fields["method"])
        assertEquals("321", fields["customercode"])
        val parameter = JsonParser.parseString(fields.getValue("param")).asJsonObject
        assertEquals(setOf("cmd", "account", "roomverify", "timestamp"), parameter.keySet())
        assertEquals("h5_getstuindexpage", parameter["cmd"].asString)
        assertEquals("a+\"\n", parameter["account"].asString)
        assertEquals("r+&=\\\"", parameter["roomverify"].asString)
        assertEquals("20261115120506007", parameter["timestamp"].asString)
    }

    @Test fun verifiedBalanceAliasesAndTopLevelFallbackRemainSupported() = runBlocking {
        for (key in listOf("odd", "surpluselec", "surplusElec", "surplus_elec", "remain", "balance")) {
            val detail = api("""{"$key":"-1.25","todayUse":"0.5"}""").detail(PowerBinding("a", 1, "v"))
            assertEquals(-1.25, detail.odd!!, 0.0)
            assertEquals(0.5, detail.todayUse!!, 0.0)
            assertTrue(detail.weekUse.isEmpty())
            assertNull(detail.monthUse)
            assertNull(detail.sumBuy)
        }
    }

    @Test fun oddMakesOneDetailRequestAndPreservesZero() = runBlocking {
        assertEquals(0.0, api("""{"modlist":[],"odd":"0"}""").odd("a", 2, "v")!!, 0.0)
        assertEquals(1, requests.size)
        assertEquals("h5_getstuindexpage", form(requests.single())["method"])
    }

    @Test fun absentRoomsRemainEmptyButMissingBalanceIsNotSuccess() = runBlocking {
        assertTrue(api("{}").rooms("a", 1).isEmpty())
        for (payload in listOf("{}", """{"odd":null,"todayuse":"bad","monthuselist":[]}""",
            """{"success":true,"todayuse":2,"sumbuy":500}""", """{"remainingPower":99}""")) {
            assertPrivateFailure(payload, "电费响应缺少有效余额")
        }
    }

    @Test fun objectStringAndDirectPayloadsSupportListAndSingletonFormats() = runBlocking {
        val meter = mapOf(
            "odd" to "12.5", "todayuse" to "2.25", "sumbuy" to "300",
            "weekuselist" to gson.toJson(mapOf("weekday" to "星期一", "dayuse" to "1.5")),
            "monthuselist" to gson.toJson(listOf(mapOf("monthuse" to "42.5"))),
        )
        val payloads = listOf(
            meter,
            mapOf("modlist" to meter),
            mapOf("modlist" to listOf(meter)),
            mapOf("modlist" to gson.toJson(meter)),
            mapOf("modlist" to gson.toJson(listOf(meter))),
            mapOf("modlist" to listOf(gson.toJson(meter))),
        )
        for (payload in payloads) {
            val json = gson.toJson(payload)
            for (wire in listOf(json, gson.toJson(mapOf("body" to payload)), gson.toJson(mapOf("body" to json)))) {
                assertEquals(PowerDetail(12.5, 2.25, listOf("一" to 1.5), 42.5, 300.0),
                    rawApi(wire).detail("a", 1, "v"))
            }
        }
    }

    @Test fun roomListsSupportSingletonAndEncodedFormatsAndSkipMalformedEntries() = runBlocking {
        val room = mapOf("roomfullname" to "北区 101", "roomverify" to "v+1")
        for (list in listOf(room, listOf(room), gson.toJson(room), gson.toJson(listOf(room)))) {
            assertEquals(listOf(PowerRoom("北区 101", "v+1")),
                rawApi(gson.toJson(mapOf("roomlist" to list))).rooms("a", 1))
        }
        assertEquals(listOf(PowerRoom("", ""), PowerRoom("", "")),
            api("""{"roomlist":[null,3,false,"bad",[],{"roomfullname":{},"roomverify":true},{}]}""")
                .rooms("a", 1))
    }

    @Test fun numericValuesAcceptStrictGroupingUnitsAndFullWidthAscii() = runBlocking {
        val samples = listOf(
            "0" to 0.0, "-0" to 0.0, "12.5" to 12.5, "-12.5" to -12.5,
            "  +12.50  " to 12.5, "1,234,567.89" to 1234567.89,
            "-1,234.50 度" to -1234.5, "1,234.50 kWh" to 1234.5,
            "１２，３４５．６７　ｋＷｈ" to 12345.67, "－０．２５度" to -0.25,
            "1.25e2" to 125.0, "1E-2 KWH" to 0.01,
        )
        for ((value, expected) in samples) {
            val detail = api(gson.toJson(mapOf("odd" to value))).detail("a", 1, "v")
            assertEquals(value, expected, detail.odd!!, 0.000001)
        }
        assertEquals(-2.5, api("""{"odd":-2.5}""").odd("a", 1, "v")!!, 0.0)
        assertEquals(125.0, api("""{"odd":1.25e2}""").odd("a", 1, "v")!!, 0.0)
    }

    @Test fun malformedNumbersNeverBecomeBalances() {
        val invalid = listOf(
            "", " ", "NaN", "Infinity", "-Infinity", "1e309", "1e-999", "-1e-999", "1,23", "12,34,567", "1,,234",
            "1,234,", "1 234", "1 .2", "1. 2", "1e 2", "1_000", "0x10", "1/2", "12元",
            "12度abc", "12kWh度", "kWh12", "--1", "1.2.3", "１２，３４", "①", "²", "١٢",
        )
        for (value in invalid) {
            assertPrivateFailure(gson.toJson(mapOf("odd" to value)), "电费响应缺少有效余额")
        }
        for (value in listOf("true", "false", "{}", "[]", "null", "[12]", "1e309", "NaN")) {
            assertPrivateFailure("""{"odd":$value,"message":"private-response"}""")
        }
    }

    @Test fun badAliasesMayFallThroughWithoutLosingZeroOrNegativeBalances() = runBlocking {
        assertEquals(0.0, api("""{"odd":{},"surpluselec":true,"surplusElec":"bad","remain":"0","balance":99}""")
            .odd("a", 1, "v")!!, 0.0)
        assertEquals(-1.0, api("""{"odd":"-1","balance":99}""").odd("a", 1, "v")!!, 0.0)
    }

    @Test fun firstMeterWinsAndOnlyTopLevelMaySupplyFallbackBalance() = runBlocking {
        for (modlist in listOf("[]", "null", "false", "{}", "\"bad\"", "[null,{\"odd\":999}]",
            "[{}, {\"odd\":999}]", "[{\"odd\":false},{\"odd\":999}]")) {
            assertEquals(7.0, api("""{"modlist":$modlist,"balance":7}""").odd("a", 1, "v")!!, 0.0)
        }
        for (first in listOf("0", "-1")) {
            assertEquals(first.toDouble(),
                api("""{"modlist":[{"odd":$first},{"odd":999}],"odd":7}""").odd("a", 1, "v")!!, 0.0)
        }
        for (modlist in listOf("[null,{\"odd\":999}]", "[{}, {\"odd\":999}]", "[false,{\"odd\":999}]")) {
            assertPrivateFailure("""{"modlist":$modlist}""", "电费响应缺少有效余额")
        }
        val detail = api("""{"balance":7,"todayuse":99,"modlist":[{"todayuse":2}]}""")
            .detail("a", 1, "v")
        assertEquals(7.0, detail.odd!!, 0.0)
        assertEquals(2.0, detail.todayUse!!, 0.0)
    }

    @Test fun damagedStatisticsAreSkippedAndNegativeUsageIsNeverReported() = runBlocking {
        val detail = api("""{"odd":-3,"todayuse":-2,"todayUse":"0","sumbuy":-1,
            "weekuselist":[null,false,[],"bad",{"weekday":{},"dayuse":2},
                {"weekday":" ","dayuse":2},{"weekday":"星期一","dayuse":-1},
                {"weekday":"星期二","dayuse":false},{"weekday":"星期三","dayuse":"0"},
                {"weekday":"星期四","dayuse":{},"use":"１．５度"},
                {"weekday":"星期五","success":false,"dayuse":3}],
            "monthuselist":[{"monthuse":1},{"monthuse":"2 kWh"},null,{"monthuse":-1},false,
                {"monthuse":{}},{"monthuse":"Infinity"},{"status":"error","monthuse":99}] }""")
            .detail("a", 1, "v")
        assertEquals(PowerDetail(-3.0, 0.0, listOf("三" to 0.0, "四" to 1.5), 2.0, null), detail)
        val invalidStats = api("""{"odd":1,"todayuse":-1,"sumbuy":false,
            "weekuselist":"not json","monthuselist":{"monthuse":-1}}""").detail("a", 1, "v")
        assertEquals(PowerDetail(1.0, null, emptyList(), null, null), invalidStats)
        assertEquals(0.0, api("""{"odd":1,"monthuselist":{"monthuse":0},"sumbuy":0}""")
            .detail("a", 1, "v").monthUse!!, 0.0)
    }

    @Test fun explicitBusinessFailuresOverrideBalancesAtEveryResponseLevel() {
        val failures = listOf(
            "\"success\":false", "\"success\":\" false \"", "\"status\":false", "\"status\":\"ERROR\"",
            "\"code\":-1", "\"code\":500", "\"statusCode\":\"401\"", "\"status\":\"fail\"",
        )
        for (failure in failures) {
            val payload = """{$failure,"odd":12,"message":"private-account private-response"}"""
            for (wire in listOf(payload, """{"body":$payload}""", gson.toJson(mapOf("body" to payload)),
                """{$failure,"body":{"odd":12}}""", """{"odd":7,"modlist":[$payload,{"odd":999}]}""")) {
                assertPrivateFailure(wire, "电费服务返回失败状态")
            }
        }
        val roomError = assertThrows(IOException::class.java) {
            runBlocking { rawApi("""{"success":false,"roomlist":[]}""").rooms("private-account", 1) }
        }
        assertEquals("电费服务返回失败状态", roomError.message)
    }

    @Test fun unknownCodesAndSuccessConventionsAreNotGuessed() = runBlocking {
        for (code in listOf("0", "1", "200", "\"0000\"", "\"vendor-new-code\"", "7654321")) {
            assertEquals(2.0, api("""{"code":$code,"success":true,"odd":2}""").odd("a", 1, "v")!!, 0.0)
        }
        assertEquals(2.0, api("""{"status":0,"odd":2}""").odd("a", 1, "v")!!, 0.0)
        assertEquals(2.0, api("""{"modlist":[{"odd":2},{"success":false,"odd":999}]}""")
            .odd("a", 1, "v")!!, 0.0)
    }

    @Test fun httpFailureIsTransportErrorWithoutLeakingBinding() {
        val error = assertThrows(IOException::class.java) {
            runBlocking { api("{}", 503).detail("private-account", 999, "private-verify") }
        }
        assertTrue(error.message.orEmpty().contains("HTTP 503"))
        assertFalse(error.message.orEmpty().contains("private"))
        assertEquals(2, requests.size)
    }

    @Test fun malformedEnvelopeFailsInsteadOfInventingBalanceAndNeverLeaksResponse() {
        for (body in listOf("private-response", "{private-response", "[]", "null", "true",
            """{"body":null,"odd":7}""", """{"body":false,"odd":7}""", """{"body":[]}""",
            """{"body":"private-account"}""", """{"odd":1} private-response""",
            """{"odd":01}""", """{"odd":1,}""", """{'odd':1}""")) {
            assertPrivateFailure(body, "电费响应格式无效")
        }
        assertEquals(13, requests.size)
    }

    @Test fun transientTransportFailureRetriesOnceWithSameProtocolForm() = runBlocking {
        for (failure in listOf(IOException("private-network-response"), SocketTimeoutException("private-account"))) {
            requests.clear()
            var backoffs = 0
            val source = scriptedApi({ backoffs++ }) { request, attempt ->
                if (attempt == 1) throw failure
                response(request, """{"odd":0}""")
            }
            assertEquals(0.0, source.odd("private-account", 1, "private-verify")!!, 0.0)
            assertEquals(2, requests.size)
            assertEquals(1, backoffs)
            assertEquals(form(requests[0]), form(requests[1]))
            assertEquals(requests[0].url, requests[1].url)
        }
    }

    @Test fun transientHttpStatusesRecoverAfterOneBackoff() = runBlocking {
        for (status in listOf(408, 502, 503, 504)) {
            requests.clear()
            var backoffs = 0
            val source = scriptedApi({ backoffs++ }) { request, attempt ->
                if (attempt == 1) response(request, "private-response", status)
                else response(request, """{"odd":12.5}""")
            }
            assertEquals(12.5, source.odd("a", 1, "v")!!, 0.0)
            assertEquals(2, requests.size)
            assertEquals(1, backoffs)
        }
    }

    @Test fun retriesHaveHardLimitAndFinalTransportErrorsAreSanitized() {
        for (httpStatus in listOf<Int?>(null, 408, 502, 503, 504)) {
            requests.clear()
            var backoffs = 0
            val source = scriptedApi({ backoffs++ }) { request, _ ->
                if (httpStatus == null) throw IOException("private-account private-response")
                response(request, "private-response", httpStatus)
            }
            val error = assertThrows(IOException::class.java) {
                runBlocking { source.odd("private-account", 1, "private-verify") }
            }
            assertEquals(2, requests.size)
            assertEquals(1, backoffs)
            assertFalse(error.message.orEmpty().contains("private"))
            assertNull(error.cause)
        }
    }

    @Test fun ordinaryHttpFailuresAndPermanentTransportFailuresNeverRetry() {
        for (status in listOf(400, 401, 403, 404, 429, 500, 501)) {
            requests.clear()
            val source = scriptedApi({ throw AssertionError("unexpected retry") }) { request, _ ->
                response(request, "private-response", status)
            }
            val error = assertThrows(IOException::class.java) {
                runBlocking { source.detail("private-account", 1, "private-verify") }
            }
            assertEquals("电费请求失败（HTTP $status）", error.message)
            assertEquals(1, requests.size)
        }
        for (failure in listOf(ProtocolException("private-response"), SSLHandshakeException("private-account"))) {
            requests.clear()
            val source = scriptedApi({ throw AssertionError("unexpected retry") }) { _, _ -> throw failure }
            val error = assertThrows(IOException::class.java) { runBlocking { source.odd("a", 1, "v") } }
            assertEquals("电费网络请求失败", error.message)
            assertNull(error.cause)
            assertEquals(1, requests.size)
        }
    }

    @Test fun businessAndFormatErrorsNeverRetryIncludingAfterTransportRecovery() {
        for (body in listOf("""{"success":false,"odd":9}""", "private-invalid-json", """{"odd":null}""")) {
            for (recoverFirst in listOf(false, true)) {
                requests.clear()
                var backoffs = 0
                val source = scriptedApi({ backoffs++ }) { request, attempt ->
                    if (recoverFirst && attempt == 1) throw IOException("temporary")
                    response(request, body)
                }
                assertThrows(IOException::class.java) { runBlocking { source.odd("a", 1, "v") } }
                assertEquals(if (recoverFirst) 2 else 1, requests.size)
                assertEquals(if (recoverFirst) 1 else 0, backoffs)
            }
        }
    }

    @Test fun cancellationDuringBackoffPreventsSecondRequest() = runBlocking {
        val enteredBackoff = CompletableDeferred<Unit>()
        val releaseBackoff = CompletableDeferred<Unit>()
        val source = scriptedApi({ enteredBackoff.complete(Unit); releaseBackoff.await(); Unit }) { _, _ ->
            throw SocketTimeoutException("private-response")
        }
        val query = async { source.detail("a", 1, "v") }
        withTimeout(5_000) { enteredBackoff.await() }
        query.cancelAndJoin()
        releaseBackoff.complete(Unit)
        assertTrue(query.isCancelled)
        assertEquals(1, requests.size)
    }

    @Test fun coroutineCancellationCancelsInjectedClientCall() = runBlocking {
        val entered = CompletableDeferred<Call>()
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            entered.complete(chain.call())
            try {
                check(release.await(5, TimeUnit.SECONDS))
                response(chain.request(), gson.toJson(mapOf("body" to "{}")))
            } finally {
                finished.countDown()
            }
        }.build().also(clients::add)
        val query = async(start = CoroutineStart.UNDISPATCHED) { PowerApi(client, clock).rooms("a", 1) }
        try {
            val call = withTimeout(5_000) { entered.await() }
            query.cancelAndJoin()
            assertTrue(call.isCanceled())
        } finally {
            release.countDown()
            assertTrue(finished.await(5, TimeUnit.SECONDS))
        }
    }
}
