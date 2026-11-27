package com.menglimi.chaoxing

import com.google.gson.JsonParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.Jsoup
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** 暂停原因不携带请求 URL、签名或响应正文，可由宿主随账号缓存保存。 */
data class ChxRequestPause(val verificationRequired: Boolean, val retryAt: Long = 0) {
    fun exception() = ChxCourseReadException(
        if (verificationRequired) "学习通要求验证码或安全验证，已暂停读取。请在网页完成验证后点“完成检查”。"
        else "学习通要求降低请求频率（HTTP 429），已暂停读取。冷却结束后可手动重查。",
        verificationRequired = verificationRequired, retryAt = retryAt,
    )
}

/** 只识别明确响应；不将普通 403、课程编号里的数字当成风控，验证码判定必须有输入框或 9010+验证码的组合。 */
fun ChxResponse.requestPause(now: Long = System.currentTimeMillis()): ChxRequestPause? {
    val retryAt = if (status == 429) {
        val raw = retryAfter?.trim().orEmpty()
        val seconds = raw.takeIf { it.isNotEmpty() && it.all { char -> char in '0'..'9' } }?.let { it.toLongOrNull() ?: Long.MAX_VALUE }
        val date = runCatching { ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
        val serverTime = runCatching { ZonedDateTime.parse(serverDate, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
        val wait = when {
            seconds != null -> if (seconds > Long.MAX_VALUE / 1000) Long.MAX_VALUE else seconds * 1000
            date != null -> (date - (serverTime ?: now)).coerceAtLeast(0)
            else -> 60_000L
        }.coerceAtLeast(1_000L)
        if (wait > Long.MAX_VALUE - now) Long.MAX_VALUE else now + wait
    } else 0L
    val json = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
    val code = json?.let { value -> listOf("code", "errorCode").any { name ->
        value.get(name)?.takeIf { it.isJsonPrimitive }?.asString == "9010"
    } } == true
    val document = Jsoup.parse(body).apply { select("script, style, template").remove() }
    val text = document.text()
    // 提示页变体：输入框可能叫 ucode（processVerifyPng 页），或正文同时出现 9010 与验证码文案。
    val form = document.select("input").any { input ->
        listOf(input.id(), input.attr("name")).any {
            it.lowercase(java.util.Locale.ROOT) in setOf("captcha", "verifycode", "validatecode", "ucode")
        }
    } && listOf("验证码", "安全验证").any { it in text }
    val flagged = text.contains("9010") && text.contains("操作异常") && text.contains("验证码")
    val verificationRequired = code || form || flagged
    return if (verificationRequired || status == 429) ChxRequestPause(verificationRequired, retryAt) else null
}

/** 所有业务请求共用串行入口；暂停后仅允许明确获准的那次恢复检查发送请求。 */
class ChxRequestGate(
    private val now: () -> Long = System::currentTimeMillis,
    private val intervalMillis: Long = 1_000L,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private val network = Mutex()
    private val lock = Any()
    private val mutablePause = MutableStateFlow<ChxRequestPause?>(null)
    val pause = mutablePause.asStateFlow()
    private var recoveryOwner: Any? = null
    private var pauseOwner: Any? = null
    private var nextRequestAt = 0L

    init { require(intervalMillis >= 0) }

    fun restore(value: ChxRequestPause?) = synchronized(lock) {
        mutablePause.value = value
        recoveryOwner = null
        pauseOwner = null
    }

    fun causedPause(owner: Any): Boolean = synchronized(lock) { pauseOwner == null || pauseOwner == owner }

    fun record(failure: ChxCourseReadException, owner: Any? = null) = synchronized(lock) {
        if (failure.verificationRequired || failure.retryAt > 0) {
            val previous = mutablePause.value
            mutablePause.value = ChxRequestPause(previous?.verificationRequired == true || failure.verificationRequired,
                maxOf(previous?.retryAt ?: 0, failure.retryAt))
            if (previous == null || recoveryOwner == owner || pauseOwner == null) pauseOwner = owner
            recoveryOwner = null
        }
    }

    fun beginRecovery(owner: Any, afterWebVerification: Boolean) = synchronized(lock) {
        val stopped = mutablePause.value ?: return@synchronized
        if (recoveryOwner != null || stopped.retryAt > now() ||
            (stopped.verificationRequired && !afterWebVerification)) throw stopped.exception()
        recoveryOwner = owner
    }

    fun finishRecovery(owner: Any, successful: Boolean) = synchronized(lock) {
        if (recoveryOwner == owner) {
            if (successful) mutablePause.value = null
            recoveryOwner = null
        }
    }

    fun check(owner: Any? = null) = synchronized(lock) {
        mutablePause.value?.let { stopped ->
            if (owner == null || recoveryOwner != owner) throw stopped.exception()
        }
    }

    suspend fun execute(owner: Any, checkSession: () -> Unit, request: suspend () -> ChxResponse): ChxResponse = network.withLock {
        currentCoroutineContext().ensureActive()
        checkSession()
        check(owner)
        val remaining = nextRequestAt - now()
        if (remaining > 0) wait(remaining)
        currentCoroutineContext().ensureActive()
        checkSession()
        check(owner)
        try {
            val response = request()
            currentCoroutineContext().ensureActive()
            checkSession()
            response.requestPause(now())?.let { stopped ->
                record(stopped.exception(), owner)
                throw stopped.exception()
            }
            response
        } catch (failure: ChxCourseReadException) {
            currentCoroutineContext().ensureActive()
            checkSession()
            record(failure, owner)
            throw failure
        } finally {
            nextRequestAt = now().let { if (intervalMillis > Long.MAX_VALUE - it) Long.MAX_VALUE else it + intervalMillis }
        }
    }
}
