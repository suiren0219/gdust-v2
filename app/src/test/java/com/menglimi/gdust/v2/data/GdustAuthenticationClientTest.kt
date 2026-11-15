package com.menglimi.gdust.v2.data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 仅连接 127.0.0.1 假服务器，验证一次票请求的网络策略；不使用真实账号或学校服务。 */
class GdustAuthenticationClientTest {
    @Test
    fun immediate503RetryAfterCannotReplayOneShotRequest() {
        for (retryAfter in listOf("0", "000")) {
            withResponse(503, mapOf("Retry-After" to retryAfter), "fixture-maintenance") { client, url, count ->
                client.newCall(Request.Builder().url("$url/exchange?loginCode=fixture-ticket").build())
                    .execute().use { response ->
                        assertEquals(503, response.code)
                        assertEquals("fixture-maintenance", response.body!!.string())
                        assertEquals(1, count.get())
                        assertTrue(response.header("Retry-After")!!.toInt() > 0)
                    }
            }
        }
    }

    @Test
    fun redirectsCannotReplayTicketOrForwardPortalToken() {
        for (status in listOf(301, 302, 303, 307, 308)) {
            withResponse(status, mapOf("Location" to "/redirect-target"), "fixture-redirect") { client, url, count ->
                client.newCall(Request.Builder().url("$url/identity")
                    .header("TOKEN", "fixture-token").build()).execute().use { response ->
                    assertEquals(status, response.code)
                    assertEquals("fixture-redirect", response.body!!.string())
                    assertEquals(1, count.get())
                    assertEquals("/identity", response.request.url.encodedPath)
                }
            }
        }
    }

    @Test
    fun requestTimeoutAndOrdinaryErrorsAreDeliveredWithoutRetry() {
        for (status in listOf(401, 408, 500, 503)) {
            withResponse(status, emptyMap(), "fixture-error") { client, url, count ->
                client.newCall(Request.Builder().url("$url/exchange").build()).execute().use { response ->
                    assertEquals(status, response.code)
                    assertEquals("fixture-error", response.body!!.string())
                    assertEquals(1, count.get())
                }
            }
        }
    }

    @Test
    fun successfulResponseAndClientTimeoutsRemainUsable() {
        withResponse(200, emptyMap(), "fixture-response") { client, url, count ->
            assertFalse(client.retryOnConnectionFailure)
            assertFalse(client.followRedirects)
            assertFalse(client.followSslRedirects)
            assertEquals(20_000, client.readTimeoutMillis)
            assertEquals(25_000, client.callTimeoutMillis)
            client.newCall(Request.Builder().url("$url/exchange").build()).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals("fixture-response", response.body!!.string())
                assertEquals(1, count.get())
            }
        }
    }

    private fun withResponse(
        status: Int,
        headers: Map<String, String>,
        body: String,
        block: (OkHttpClient, String, AtomicInteger) -> Unit,
    ) {
        val count = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            exchange.use {
                count.incrementAndGet()
                headers.forEach { (key, value) -> it.responseHeaders.set(key, value) }
                val bytes = body.toByteArray(Charsets.UTF_8)
                it.sendResponseHeaders(status, bytes.size.toLong())
                it.responseBody.use { output -> output.write(bytes) }
            }
        }
        val client = schoolAuthenticationClient(OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build())
        server.start()
        try {
            block(client, "http://127.0.0.1:${server.address.port}", count)
        } finally {
            server.stop(0)
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }
}
