package com.menglimi.chaoxing

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.EventListener
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ChxOkHttpTransportTest {
    @Test fun manualRedirectsReloadCookiesForEachExactHostAndSaveOnlyValidResponseCookies() = runBlocking {
        val first = "https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=201&clazzid=101&cpi=301&ismooc2=1&v=2"
        val next = "https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/stu?courseid=201&clazzid=101&cpi=301"
        val jar = RecordingJar(listOf(
            cookie("first", "one", "mooc1.chaoxing.com"),
            cookie("second", "two", "mooc2-ans.chaoxing.com"),
            cookie("unrelated", "private", "other.example"),
            cookie("wrongPath", "private", "mooc1.chaoxing.com", path = "/private"),
            cookie("expired", "private", "mooc1.chaoxing.com", expiresAt = 1),
        ))
        val requests = Collections.synchronizedList(mutableListOf<Request>())
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            if (requests.size == 1) response(request, 302, "").newBuilder()
                .header("Location", next)
                .addHeader("Set-Cookie", "rotated=synthetic; Path=/; Secure")
                .addHeader("Set-Cookie", "invalid=synthetic; Domain=other.example; Path=/")
                .build()
            else response(request, 200, javaClass.getResource("/chaoxing/course-tabs-lowercase.html")!!.readText())
        }.build()
        try {
            val transport = ChxOkHttpTransport(jar, client)
            val course = ChxWorkCourse(201, 101, 301, "合成课程", first)
            assertEquals("synthetic-work", ChxApi(execute = transport::execute).courseTabs(course).workEnc)
            assertEquals(listOf(first, next), requests.map { it.url.toString() })
            assertEquals(listOf("first=one", "second=two"), requests.map { it.header("Cookie") })
            assertEquals(listOf(first, next), jar.loaded.map { it.toString() })
            assertEquals(first.toHttpUrl(), jar.saved.single().first)
            assertEquals(listOf("rotated"), jar.saved.single().second.map { it.name })
        } finally { client.dispatcher.executorService.shutdownNow() }
    }

    @Test fun stripsCallerCookieAndRejectsUntrustedTargetsBeforeReadingTheJar() = runBlocking {
        val jar = RecordingJar(emptyList())
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            assertEquals(null, chain.request().header("Cookie"))
            response(chain.request(), 200, "synthetic")
        }.build()
        try {
            val transport = ChxOkHttpTransport(jar, client)
            transport.execute(Request.Builder().url(ChxSso.HOME).header("Cookie", "private=caller").build())
            assertEquals(1, calls.get())
            for (url in listOf("https://other.example/", "http://mooc1.chaoxing.com/",
                "https://mooc1.chaoxing.com:8443/", "https://user@mooc1.chaoxing.com/")) {
                assertThrows(IllegalArgumentException::class.java) { runBlocking {
                    transport.execute(Request.Builder().url(url).build())
                } }
            }
            assertEquals(1, calls.get())
            assertEquals(listOf(ChxSso.HOME.toHttpUrl()), jar.loaded)
        } finally { client.dispatcher.executorService.shutdownNow() }
    }

    @Test fun changedSessionDoesNotSaveResponseCookies() {
        val active = AtomicBoolean(true)
        val jar = RecordingJar(emptyList())
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            active.set(false)
            response(chain.request(), 200, "synthetic").newBuilder()
                .header("Set-Cookie", "stale=synthetic; Path=/; Secure").build()
        }.build()
        try {
            val transport = ChxOkHttpTransport(jar, client, checkSession = {
                if (!active.get()) throw CancellationException("合成会话切换")
            })
            assertThrows(CancellationException::class.java) { runBlocking {
                transport.execute(Request.Builder().url(ChxSso.HOME).build())
            } }
            assertTrue(jar.saved.isEmpty())
        } finally { client.dispatcher.executorService.shutdownNow() }
    }

    @Test fun oversizedResponseFailsWithoutWritingCookies() {
        val jar = RecordingJar(emptyList())
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            response(chain.request(), 200, "x".repeat(4 * 1024 * 1024 + 1)).newBuilder()
                .header("Set-Cookie", "large=synthetic; Path=/; Secure").build()
        }.build()
        try {
            val error = assertThrows(ChxCourseReadException::class.java) { runBlocking {
                ChxOkHttpTransport(jar, client).execute(Request.Builder().url(ChxSso.HOME).build())
            } }
            assertTrue(error.message.orEmpty().contains("数据过大"))
            assertTrue(jar.saved.isEmpty())
        } finally { client.dispatcher.executorService.shutdownNow() }
    }

    @Test fun actualHttpRedirectIsReturnedWithoutFollowingEvenWhenInjectedClientEnablesIt() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val followed = AtomicInteger()
        server.createContext("/start") { exchange ->
            exchange.responseHeaders.add("Location", "/destination")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/destination") { exchange ->
            followed.incrementAndGet()
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        val client = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()
        try {
            val transport = ChxOkHttpTransport(CookieJar.NO_COOKIES, client, isAllowedUrl = {
                it.scheme == "http" && it.host == "127.0.0.1" && it.port == server.address.port
            })
            val result = transport.execute(Request.Builder().url("http://127.0.0.1:${server.address.port}/start").build())
            assertEquals(302, result.status)
            assertEquals("/destination", result.location)
            assertEquals(0, followed.get())
        } finally {
            server.stop(0)
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdownNow()
        }
    }

    @Test fun coroutineCancellationCancelsTheActiveHttpCall() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/slow") { exchange ->
            started.complete(Unit)
            release.await(5, TimeUnit.SECONDS)
            exchange.close()
        }
        server.start()
        val client = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun canceled(call: Call) { cancelled.complete(Unit) }
        }).build()
        val jar = RecordingJar(emptyList())
        try {
            val transport = ChxOkHttpTransport(jar, client, isAllowedUrl = {
                it.scheme == "http" && it.host == "127.0.0.1" && it.port == server.address.port
            })
            val job = launch {
                transport.execute(Request.Builder().url("http://127.0.0.1:${server.address.port}/slow").build())
            }
            withTimeout(5_000) { started.await() }
            job.cancelAndJoin()
            withTimeout(5_000) { cancelled.await() }
            assertTrue(job.isCancelled)
            assertTrue(jar.saved.isEmpty())
        } finally {
            release.countDown()
            server.stop(0)
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdownNow()
        }
    }

    private fun response(request: Request, status: Int, body: String) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(status).message("Synthetic")
        .body(body.toResponseBody()).build()

    private fun cookie(name: String, value: String, host: String, path: String = "/", expiresAt: Long = Long.MAX_VALUE) =
        Cookie.Builder().name(name).value(value).hostOnlyDomain(host).path(path).secure().expiresAt(expiresAt).build()

    /** 故意返回所有域名的 Cookie，验证 transport 即使面对不筛选的 Jar 也不会泄漏。 */
    private class RecordingJar(private val cookies: List<Cookie>) : CookieJar {
        val loaded = Collections.synchronizedList(mutableListOf<HttpUrl>())
        val saved = Collections.synchronizedList(mutableListOf<Pair<HttpUrl, List<Cookie>>>())
        override fun loadForRequest(url: HttpUrl): List<Cookie> { loaded += url; return cookies }
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { saved += url to cookies }
    }
}
