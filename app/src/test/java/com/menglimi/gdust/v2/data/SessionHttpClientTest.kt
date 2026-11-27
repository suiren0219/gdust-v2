package com.menglimi.gdust.v2.data

import com.menglimi.chaoxing.ChxCourseReadException
import com.menglimi.chaoxing.ChxRequestGate
import com.menglimi.chaoxing.ChxTransportFailure
import kotlinx.coroutines.runBlocking
import okhttp3.CookieJar
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketException
import java.util.concurrent.atomic.AtomicInteger

/** Exercises the CHX adapter with synthetic responses only; no socket is opened. */
class SessionHttpClientTest {
    @Test fun optedInChxGetRecoversOnePreHeaderSocketFailure() = runBlocking {
        val attempts = AtomicInteger()
        val waits = AtomicInteger()
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            if (attempts.incrementAndGet() == 1) throw SocketException("synthetic disconnect")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("Synthetic").body("ok".toResponseBody()).build()
        }.build()
        try {
            val response = client(ChxRequestControl(ChxRequestGate(intervalMillis = 0)), transport) {
                waits.incrementAndGet()
            }.execute(Request.Builder().url(URL).get().build())

            assertEquals(200, response.status)
            assertEquals("ok", response.body)
            assertEquals(2, attempts.get())
            assertEquals(1, waits.get())
        } finally {
            transport.dispatcher.executorService.shutdownNow()
        }
    }

    @Test fun secondPreHeaderDisconnectEndsRecovery() = runBlocking {
        val attempts = AtomicInteger()
        val waits = AtomicInteger()
        val transport = OkHttpClient.Builder().addInterceptor {
            attempts.incrementAndGet()
            throw SocketException("synthetic disconnect")
        }.build()
        try {
            val failure = runCatching {
                client(ChxRequestControl(ChxRequestGate(intervalMillis = 0)), transport) {
                    waits.incrementAndGet()
                }.execute(Request.Builder().url(URL).get().build())
            }.exceptionOrNull()

            assertTrue(failure is ChxTransportFailure)
            assertTrue((failure as ChxTransportFailure).recoveryAttempted)
            assertFalse(failure.responseStarted)
            assertEquals(2, attempts.get())
            assertEquals(1, waits.get())
        } finally {
            transport.dispatcher.executorService.shutdownNow()
        }
    }

    @Test fun coreRecoveryRequiresBothChxControlAndExplicitCallback() = runBlocking {
        listOf(false, true).forEach { withControl ->
            val attempts = AtomicInteger()
            val waits = AtomicInteger()
            val transport = OkHttpClient.Builder().addInterceptor {
                attempts.incrementAndGet()
                throw SocketException("synthetic disconnect")
            }.build()
            try {
                val failure = runCatching {
                    SessionHttpClient(
                        allowedUrl = { it.host == "synthetic.invalid" },
                        checkSession = {},
                        cookieJar = CookieJar.NO_COOKIES,
                        chxRequests = if (withControl) ChxRequestControl(ChxRequestGate(intervalMillis = 0)) else null,
                        chxBeforeRecovery = if (withControl) null else ({ waits.incrementAndGet(); Unit }),
                        clientBuilder = transport.newBuilder(),
                    ).execute(Request.Builder().url(URL).get().build())
                }.exceptionOrNull()

                assertTrue(failure is SocketException)
                assertEquals(1, attempts.get())
                assertEquals(0, waits.get())
            } finally {
                transport.dispatcher.executorService.shutdownNow()
            }
        }
    }

    @Test fun postIsNotRecovered() = runBlocking {
        val attempts = AtomicInteger()
        val transport = OkHttpClient.Builder().addInterceptor {
            attempts.incrementAndGet()
            throw SocketException("synthetic disconnect")
        }.build()
        try {
            val failure = runCatching {
                client(ChxRequestControl(ChxRequestGate(intervalMillis = 0)), transport) {}
                    .execute(Request.Builder().url(URL).post("x=1".toRequestBody()).build())
            }.exceptionOrNull()

            assertTrue(failure is ChxTransportFailure)
            assertFalse((failure as ChxTransportFailure).recoveryAttempted)
            assertEquals(1, attempts.get())
        } finally {
            transport.dispatcher.executorService.shutdownNow()
        }
    }

    @Test fun responseBodyFailureIsNotRecovered() = runBlocking {
        val attempts = AtomicInteger()
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            attempts.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("Synthetic").body(failingBody()).build()
        }.build()
        try {
            val failure = runCatching {
                client(ChxRequestControl(ChxRequestGate(intervalMillis = 0)), transport) {}
                    .execute(Request.Builder().url(URL).get().build())
            }.exceptionOrNull()

            assertTrue(failure is ChxTransportFailure)
            assertTrue((failure as ChxTransportFailure).responseStarted)
            assertFalse(failure.recoveryAttempted)
            assertEquals(1, attempts.get())
        } finally {
            transport.dispatcher.executorService.shutdownNow()
        }
    }

    @Test fun rateLimitResponseIsNotRecoveredAndPauseIsLatched() = runBlocking {
        val attempts = AtomicInteger()
        val control = ChxRequestControl(ChxRequestGate(intervalMillis = 0))
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            attempts.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(429).message("Synthetic").header("Retry-After", "30")
                .body("""{"code":9010,"msg":"验证码"}""".toResponseBody()).build()
        }.build()
        try {
            val failure = runCatching {
                client(control, transport) {}.execute(Request.Builder().url(URL).get().build())
            }.exceptionOrNull()

            assertTrue(failure is ChxCourseReadException)
            assertTrue((failure as ChxCourseReadException).verificationRequired)
            assertTrue(control.pause.value?.verificationRequired == true)
            assertEquals(1, attempts.get())
        } finally {
            transport.dispatcher.executorService.shutdownNow()
        }
    }

    private fun client(
        control: ChxRequestControl,
        transport: OkHttpClient,
        beforeRecovery: suspend () -> Unit,
    ) = SessionHttpClient(
        allowedUrl = { it.host == "synthetic.invalid" },
        checkSession = {},
        cookieJar = CookieJar.NO_COOKIES,
        chxRequests = control,
        chxBeforeRecovery = beforeRecovery,
        clientBuilder = transport.newBuilder(),
    )

    private fun failingBody(): ResponseBody = object : ResponseBody() {
        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = -1
        override fun source(): BufferedSource = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long = throw IOException("synthetic body failure")
            override fun timeout(): Timeout = Timeout.NONE
            override fun close() {}
        }.buffer()
    }

    private companion object {
        const val URL = "https://synthetic.invalid/chx"
    }
}
