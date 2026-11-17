package com.menglimi.power

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    private fun api(inner: String, status: Int = 200): PowerApi {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            response(chain.request(), gson.toJson(mapOf("body" to inner)), status)
        }.build().also(clients::add)
        return PowerApi(client, clock)
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

    @Test fun absentFieldsProduceNullableDetailsAndEmptyRooms() = runBlocking {
        assertTrue(api("{}").rooms("a", 1).isEmpty())
        assertEquals(PowerDetail(null, null, emptyList(), null, null),
            api("""{"odd":null,"todayuse":"bad","monthuselist":[]}""").detail("a", 1, "v"))
    }

    @Test fun httpFailureIsTransportErrorWithoutLeakingBinding() {
        val error = assertThrows(IOException::class.java) {
            runBlocking { api("{}", 503).detail("private-account", 999, "private-verify") }
        }
        assertTrue(error.message.orEmpty().contains("HTTP 503"))
        assertFalse(error.message.orEmpty().contains("private"))
        assertEquals(1, requests.size)
    }

    @Test fun malformedEnvelopeFailsInsteadOfInventingBalance() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking { api("not-an-object").detail("a", 1, "v") }
        }
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
