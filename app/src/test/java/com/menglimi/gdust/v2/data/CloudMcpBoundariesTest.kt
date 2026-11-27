package com.menglimi.gdust.v2.data

import com.google.gson.JsonParser
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class CloudMcpBoundariesTest {
    @Test fun oldPreviewCannotBeSentToAnotherEndpointTokenOrChangedSchedule() = runBlocking {
        val fixture = CloudMcpFixture()
        var time = 1000L
        val repo = fixture.repository { time }
        val preview = repo.previewUpload(fixture.config, fixture.document)
        val count = fixture.requests.size
        listOf(fixture.config.copy(url = "https://other.test/mcp"), fixture.config.copy(token = "other-token"),
            fixture.config.copy(enabled = false)).forEach {
            assertTrue(runCatching { repo.confirmUpload(it, preview, fixture.document) }.isFailure)
        }
        assertTrue(runCatching { repo.confirmUpload(fixture.config, preview, fixture.document + " ") }.isFailure)
        time += 300_001
        assertTrue(runCatching { repo.confirmUpload(fixture.config, preview, fixture.document) }.isFailure)
        assertEquals(count, fixture.requests.size)
        assertFalse(preview.toString().contains("synthetic-pending-id"))
        assertFalse(fixture.config.toString().contains("synthetic-token"))
    }

    @Test fun fractionalStringDuplicateAndWrongRpcIdsAreRejected() = runBlocking {
        listOf("fraction", "string", "duplicate", "wrong").forEach { mode ->
            val http = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                val input = JsonParser.parseString(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).asJsonObject
                val id = input["id"].asInt
                val field = when (mode) {
                    "fraction" -> "\"id\":${id}.5"; "string" -> "\"id\":\"$id\""
                    "duplicate" -> "\"id\":$id,\"id\":$id"; else -> "\"id\":${id + 1}"
                }
                val text = "{\"jsonrpc\":\"2.0\",$field,\"result\":{\"protocolVersion\":\"2025-11-25\"}}"
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("synthetic")
                    .header("Content-Type", "application/json").body(text.toResponseBody("application/json".toMediaType())).build()
            }.build()
            assertTrue(runCatching { CloudMcpClient(CloudMcpFixture().config, http).initialize() }.isFailure)
        }
    }

    @Test fun cancellationClosesAnInProgressResponseWithoutWaitingForReadTimeout() = runBlocking {
        val started = CountDownLatch(1)
        val released = CountDownLatch(1)
        val closed = AtomicBoolean(false)
        val source = object : Source {
            override fun timeout() = Timeout.NONE
            override fun read(sink: Buffer, byteCount: Long): Long {
                started.countDown(); released.await(5, TimeUnit.SECONDS); return -1
            }
            override fun close() { closed.set(true); released.countDown() }
        }.buffer()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("synthetic")
                .header("Content-Type", "application/json").body(object : ResponseBody() {
                    override fun contentType() = "application/json".toMediaType()
                    override fun contentLength() = -1L
                    override fun source() = source
                }).build()
        }.build()
        val job = launch { CloudMcpClient(CloudMcpFixture().config, http).initialize() }
        try {
            withContext(Dispatchers.IO) { assertTrue(started.await(3, TimeUnit.SECONDS)) }
            withTimeout(1000) { job.cancelAndJoin() }
            assertTrue(closed.get())
        } finally { released.countDown(); job.cancelAndJoin() }
    }
}
