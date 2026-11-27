package com.menglimi.chaoxing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.ProtocolException
import java.net.SocketException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipException
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 诊断只使用固定类型和阶段，不保留异常原文中的地址、Cookie 或代理信息。 */
internal enum class ChxIoFailure(val description: String) {
    TIMEOUT("请求超时"), DNS("域名解析失败"), TLS("安全连接失败"), CONNECT("无法连接服务器"),
    EOF("连接提前关闭"), SOCKET("连接中断"), HTTP2("HTTP/2 连接中断"),
    PROTOCOL("响应协议异常"), DECODE("响应解压失败"), IO("网络传输失败"),
}

internal fun classifyChxIoFailure(error: IOException): ChxIoFailure = when (error) {
    is InterruptedIOException -> ChxIoFailure.TIMEOUT
    is UnknownHostException -> ChxIoFailure.DNS
    is SSLException -> ChxIoFailure.TLS
    is ConnectException -> ChxIoFailure.CONNECT
    is EOFException -> ChxIoFailure.EOF
    is SocketException -> ChxIoFailure.SOCKET
    is ProtocolException -> ChxIoFailure.PROTOCOL
    is ZipException -> ChxIoFailure.DECODE
    else -> when {
        error.javaClass.name in setOf("okhttp3.internal.http2.StreamResetException",
            "okhttp3.internal.http2.ConnectionShutdownException") -> ChxIoFailure.HTTP2
        generateSequence(error.cause) { it.cause }.take(8).any { it is EOFException } -> ChxIoFailure.EOF
        else -> ChxIoFailure.IO
    }
}

class ChxTransportFailure internal constructor(
    internal val kind: ChxIoFailure,
    val responseStarted: Boolean,
    val status: Int? = null,
    val protocol: String? = null,
    val recoveryAttempted: Boolean = false,
) : IOException(kind.description) {
    val diagnostic: String get() = buildString {
        append(kind.description)
        append("；阶段=").append(if (responseStarted) "读取正文" else "等待响应头")
        append("；类型=NET_").append(kind.name)
        append("；HTTP=").append(status?.toString() ?: "未收到完整响应")
        if (protocol != null) append("；协议=").append(protocol)
        append("；新连接恢复=").append(if (recoveryAttempted) "1/1" else "0/1")
    }
}

/** Cookie 的读取与提交仍由宿主控制；只有完整、限长的响应才能交回宿主。 */
data class ChxHttpResult(val response: ChxResponse, val cookies: List<String>)

class ChxHttpExchange(
    private val client: OkHttpClient,
    private val checkSession: () -> Unit = {},
    private val beforeRecovery: suspend () -> Unit = { delay(1_000) },
    private val onResponseReceived: (ChxResponse) -> Unit = {},
) {
    suspend fun execute(request: Request): ChxHttpResult {
        currentCoroutineContext().ensureActive()
        checkSession()
        try {
            return client.newCall(request).readResponse(recoveryAttempted = false)
        } catch (failure: ChxTransportFailure) {
            currentCoroutineContext().ensureActive()
            checkSession()
            if (request.method != "GET" || failure.responseStarted ||
                failure.kind !in setOf(ChxIoFailure.EOF, ChxIoFailure.SOCKET)) throw failure
        }
        // 只恢复收到完整响应头之前的断连；不重放 POST，不重试任何 HTTP 响应或正文解析。
        beforeRecovery()
        currentCoroutineContext().ensureActive()
        checkSession()
        val pool = ConnectionPool(0, 1, TimeUnit.SECONDS)
        val fresh = client.newBuilder().connectionPool(pool).retryOnConnectionFailure(false).build()
        return try { fresh.newCall(request).readResponse(recoveryAttempted = true) }
        finally { pool.evictAll() }
    }

    private suspend fun Call.readResponse(recoveryAttempted: Boolean): ChxHttpResult = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(
                    ChxTransportFailure(classifyChxIoFailure(e), false, recoveryAttempted = recoveryAttempted))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        // 429 正文也可能要求验证码；限长读取失败时仍保留限流响应和响应头。
                        val body = if (it.code in setOf(301, 302, 303, 307, 308, 401)) "" else try {
                            val bytes = it.peekBody(MAX_BODY_BYTES + 1).bytes()
                            if (bytes.size > MAX_BODY_BYTES) {
                                if (it.code != 429) throw ChxCourseReadException("服务返回的数据过大（BODY_TOO_LARGE；上限 4 MiB），请在网页查看。")
                                ""
                            } else bytes.toString(it.body?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
                        } catch (failure: IOException) {
                            if (it.code != 429) throw failure
                            ""
                        }
                        ChxHttpResult(ChxResponse(it.code, body, it.header("Location"),
                            it.header("Retry-After"), it.header("Date")), it.headers("Set-Cookie"))
                    }
                    checkSession()
                    onResponseReceived(result.response)
                    checkSession()
                    if (continuation.isActive) continuation.resume(result)
                } catch (failure: Exception) {
                    val safe = when (failure) {
                        is CancellationException -> failure
                        is IOException -> ChxTransportFailure(classifyChxIoFailure(failure), true, response.code,
                            response.protocol.toString(), recoveryAttempted)
                        else -> failure
                    }
                    if (continuation.isActive) continuation.resumeWithException(safe)
                }
            }
        })
    }

    private companion object { const val MAX_BODY_BYTES = 4L * 1024 * 1024 }
}
