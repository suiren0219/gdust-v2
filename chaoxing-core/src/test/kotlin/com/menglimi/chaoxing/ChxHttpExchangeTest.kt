package com.menglimi.chaoxing

import kotlinx.coroutines.runBlocking
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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipException
import javax.net.ssl.SSLHandshakeException

class ChxHttpExchangeTest {
    @Test fun getDisconnectBeforeHeadersRecoversOnceOnAFreshConnection() = runBlocking {
        val attempts = AtomicInteger()
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor {
            attempts.incrementAndGet()
            throw SocketException("synthetic disconnect")
        }.build()
        try {
            val error = assertThrows(ChxTransportFailure::class.java) { runBlocking {
                ChxHttpExchange(client, beforeRecovery = {}).execute(Request.Builder()
                    .url("https://mooc2-ans.chaoxing.com/mooc2-ans/visit/interaction").build())
            } }
            assertEquals(2, attempts.get())
            assertTrue(error.recoveryAttempted)
            assertFalse(error.responseStarted)
            assertEquals(ChxIoFailure.SOCKET, error.kind)
            assertTrue(error.diagnostic.contains("新连接恢复=1/1"))
            assertTrue(error.diagnostic.contains("阶段=等待响应头"))
        } finally { client.dispatcher.executorService.shutdownNow() }
    }

    @Test fun wrappedEofBeforeHeadersIsAlsoRecovered() = runBlocking {
        val attempts = AtomicInteger()
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor {
            attempts.incrementAndGet()
            throw IOException("synthetic", EOFException("synthetic"))
        }.build()
        try {
            val error = assertThrows(ChxTransportFailure::class.java) { runBlocking {
                ChxHttpExchange(client, beforeRecovery = {}).execute(Request.Builder()
                    .url("https://mooc2-ans.chaoxing.com/mooc2-ans/visit/interaction").build())
            } }
            assertEquals(ChxIoFailure.EOF, error.kind)
            assertEquals(2, attempts.get())
            assertTrue(error.recoveryAttempted)
        } finally { client.dispatcher.executorService.shutdownNow() }
    }

    @Test fun postIsNeverRetried() = runBlocking {
        val attempts = AtomicInteger()
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor {
            attempts.incrementAndGet()
            throw SocketException("synthetic disconnect")
        }.build()
        try {
            val error = assertThrows(ChxTransportFailure::class.java) { runBlocking {
                ChxHttpExchange(client, beforeRecovery = {}).execute(Request.Builder()
                    .url("https://mooc2-ans.chaoxing.com/mooc2-ans/visit/courselistdata")
                    .post("synthetic=1".toRequestBody()).build())
            } }
            assertEquals(1, attempts.get())
            assertFalse(error.recoveryAttempted)
        } finally { client.dispatcher.executorService.shutdownNow() }
    }

    @Test fun bodyFailureAfterResponseIsNotRetried() = runBlocking {
        val attempts = AtomicInteger()
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            attempts.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("Synthetic").body(failingBody()).build()
        }.build()
        try {
            val error = assertThrows(ChxTransportFailure::class.java) { runBlocking {
                ChxHttpExchange(client, beforeRecovery = {}).execute(Request.Builder()
                    .url("https://mooc2-ans.chaoxing.com/mooc2-ans/visit/interaction").build())
            } }
            assertEquals(1, attempts.get())
            assertTrue(error.responseStarted)
            assertFalse(error.recoveryAttempted)
            assertEquals(200, error.status)
            assertTrue(error.diagnostic.contains("阶段=读取正文"))
        } finally { client.dispatcher.executorService.shutdownNow() }
    }

    @Test fun redirectAndRateLimitReadOnlyHeadersAndKeepRetryAfter() = runBlocking {
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val code = chain.request().url.encodedPath.substringAfterLast('/').toInt()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("Synthetic").body(failingBody())
                .apply { if (code == 429) header("Retry-After", "30") }.build()
        }.build()
        try {
            val exchange = ChxHttpExchange(client, beforeRecovery = {})
            val limited = exchange.execute(Request.Builder()
                .url("https://mooc2-ans.chaoxing.com/mooc2-ans/visit/429").build())
            assertEquals(429, limited.response.status)
            assertEquals("30", limited.response.retryAfter)
            assertEquals("", limited.response.body)
            val redirect = exchange.execute(Request.Builder()
                .url("https://mooc1.chaoxing.com/mooc2/work/302").build())
            assertEquals(302, redirect.response.status)
        } finally { client.dispatcher.executorService.shutdownNow() }
    }

    @Test fun responseCallbackLatchesCombinedPauseBeforeExecuteResumes() = runBlocking {
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(429).message("Synthetic").header("Retry-After", "30")
                .body("""{"code":9010}""".toResponseBody()).build()
        }.build()
        val gate = ChxRequestGate(now = { 1_000L }, intervalMillis = 0)
        try {
            val exchange = ChxHttpExchange(client, onResponseReceived = { response ->
                response.requestPause(1_000L)?.let { gate.record(it.exception(), "owner") }
            })
            val received = exchange.execute(Request.Builder().url("https://mooc2-ans.chaoxing.com/visit/test").build())
            assertEquals(429, received.response.status)
            assertEquals(ChxRequestPause(true, 31_000L), gate.pause.value)
        } finally { client.dispatcher.executorService.shutdownNow() }
    }

    @Test fun rateLimitBodyReadFailureStillReturnsRetryHeadersAndLatchesCooldown() = runBlocking {
        for (large in listOf(false, true)) {
            val client = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
                val body = if (large) "x".repeat(4 * 1024 * 1024 + 1).toResponseBody() else failingBody()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(429).message("Synthetic").header("Retry-After", "30").body(body).build()
            }.build()
            val gate = ChxRequestGate(now = { 1_000L }, intervalMillis = 0)
            try {
                val exchange = ChxHttpExchange(client, onResponseReceived = { response ->
                    response.requestPause(1_000L)?.let { gate.record(it.exception(), "owner") }
                })
                val received = exchange.execute(Request.Builder().url("https://mooc2-ans.chaoxing.com/visit/test").build())
                assertEquals(429, received.response.status)
                assertEquals("30", received.response.retryAfter)
                assertEquals(ChxRequestPause(false, 31_000L), gate.pause.value)
            } finally { client.dispatcher.executorService.shutdownNow() }
        }
    }

    @Test fun oversizedBodyFailsWithAFixedMessageInsteadOfANetworkError() = runBlocking {
        val attempts = AtomicInteger()
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            attempts.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("Synthetic")
                .body("x".repeat(4 * 1024 * 1024 + 1).toResponseBody()).build()
        }.build()
        try {
            val error = assertThrows(ChxCourseReadException::class.java) { runBlocking {
                ChxHttpExchange(client, beforeRecovery = {}).execute(Request.Builder()
                    .url("https://mooc2-ans.chaoxing.com/mooc2-ans/visit/interaction").build())
            } }
            assertTrue(error.message.orEmpty().contains("BODY_TOO_LARGE"))
            assertEquals(1, attempts.get())
        } finally { client.dispatcher.executorService.shutdownNow() }
    }

    @Test fun diagnosticsUseFixedTypesAndNeverEchoPrivateText() {
        val kinds = mapOf(
            SocketTimeoutException("private-host") to ChxIoFailure.TIMEOUT,
            InterruptedIOException("private-token") to ChxIoFailure.TIMEOUT,
            UnknownHostException("private-host") to ChxIoFailure.DNS,
            SSLHandshakeException("private-cert") to ChxIoFailure.TLS,
            ConnectException("private-host") to ChxIoFailure.CONNECT,
            EOFException("private-body") to ChxIoFailure.EOF,
            SocketException("private-socket") to ChxIoFailure.SOCKET,
            ProtocolException("private-protocol") to ChxIoFailure.PROTOCOL,
            ZipException("private-zip") to ChxIoFailure.DECODE,
            IOException("Cookie: private=1") to ChxIoFailure.IO,
        )
        kinds.forEach { (error, expected) -> assertEquals(expected, classifyChxIoFailure(error)) }
        val diagnostic = ChxTransportFailure(ChxIoFailure.IO, false,
            status = 200, protocol = "h2", recoveryAttempted = false).diagnostic
        listOf("Cookie", "private", "http", "token", "查询").forEach {
            assertFalse(it, diagnostic.contains(it))
        }
        assertTrue(diagnostic.contains("NET_IO"))
    }

    private fun failingBody(): ResponseBody = object : ResponseBody() {
        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = -1
        override fun source(): BufferedSource = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long = throw IOException("synthetic body failure")
            override fun timeout(): Timeout = Timeout.NONE
            override fun close() {}
        }.buffer()
    }

}
