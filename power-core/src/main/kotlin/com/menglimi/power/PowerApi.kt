package com.menglimi.power

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.IOException
import java.io.StringReader
import java.net.ProtocolException
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.net.ssl.SSLException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** 监控只要求详情来源；测试或其他客户端可注入内存实现。 */
fun interface PowerDetailSource {
    suspend fun detail(binding: PowerBinding): PowerDetail
}

/**
 * 完美校园 SWAEServlet 的已验证协议，不依赖学校账号登录、Android 或应用存储。
 * rooms 使用 getbindroom；odd 与 detail 共用 h5_getstuindexpage，不增加额外端点。
 * 注入的 OkHttpClient 由调用方管理；协程取消会取消对应 HTTP Call。
 */
class PowerApi(
    client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build(),
    private val clock: Clock = Clock.systemDefaultZone(),
    private val retryBackoff: suspend () -> Unit = { delay(250) },
) : PowerDetailSource {
    // 派生客户端共享连接池与 dispatcher，由调用方管理，避免内建重试与显式重试叠加。
    private val client = client.newBuilder().retryOnConnectionFailure(false).build()
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    suspend fun rooms(account: String, customerCode: Int): List<PowerRoom> {
        val body = post("getbindroom", linkedMapOf("cmd" to "getbindroom", "account" to account), customerCode)
        return body.get("roomlist").entries().mapNotNull { entry ->
            val room = entry.objectOrNull() ?: return@mapNotNull null
            PowerRoom(room.string("roomfullname").orEmpty(), room.string("roomverify").orEmpty())
        }
    }

    override suspend fun detail(binding: PowerBinding): PowerDetail =
        detail(binding.account, binding.code, binding.verify)

    suspend fun detail(account: String, customerCode: Int, roomVerify: String): PowerDetail {
        val body = post(
            "h5_getstuindexpage",
            linkedMapOf(
                "cmd" to "h5_getstuindexpage",
                "account" to account,
                "roomverify" to roomVerify,
                "timestamp" to LocalDateTime.now(clock).format(TIMESTAMP),
            ),
            customerCode,
        )
        // 不扫描后续设备；第一项不可用时只允许回退 payload 的已知余额字段。
        val meter = body.get("modlist").entries().firstOrNull().objectOrNull() ?: body
        meter.checkBusinessFailure()
        val balance = meter.number(*BALANCE_KEYS) ?: body.number(*BALANCE_KEYS)
            ?: throw IOException("电费响应缺少有效余额")
        val week = meter.get("weekuselist").entries().mapNotNull { entry ->
            val day = entry.objectOrNull() ?: return@mapNotNull null
            if (day.isBusinessFailure()) return@mapNotNull null
            val label = day.string("weekday") ?: return@mapNotNull null
            val use = day.nonNegativeNumber("dayuse", "use") ?: return@mapNotNull null
            label.removePrefix("星期").takeIf { it.isNotBlank() }?.let { it to use }
        }
        return PowerDetail(
            odd = balance,
            todayUse = meter.nonNegativeNumber("todayuse", "todayUse"),
            weekUse = week,
            // 保留最后一个月语义，单个损坏统计项不影响较早的有效项。
            monthUse = meter.get("monthuselist").entries().asReversed().firstNotNullOfOrNull { entry ->
                entry.objectOrNull()?.takeUnless { it.isBusinessFailure() }?.nonNegativeNumber("monthuse")
            },
            sumBuy = meter.nonNegativeNumber("sumbuy"),
        )
    }

    suspend fun odd(account: String, customerCode: Int, roomVerify: String): Double? =
        detail(account, customerCode, roomVerify).odd

    private suspend fun post(method: String, parameters: Map<String, String>, customerCode: Int): JsonObject {
        val form = FormBody.Builder()
            .add("param", gson.toJson(parameters))
            .add("customercode", customerCode.toString())
            .add("method", method)
            .add("command", "JBSWaterElecService")
            .build()
        val request = Request.Builder().url(SERVICE_URL)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
            .post(form).build()
        val text = execute(request)
        val envelope = parseJson(text)?.objectOrNull() ?: throw IOException("电费响应格式无效")
        envelope.checkBusinessFailure()
        val payload = if (envelope.has("body")) {
            envelope.get("body").objectOrNull() ?: throw IOException("电费响应格式无效")
        } else envelope
        payload.checkBusinessFailure()
        return payload
    }

    private suspend fun execute(request: Request): String {
        var retried = false
        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                return executeOnce(request)
            } catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                val retryable = when (error) {
                    is HttpFailure -> error.status in RETRY_HTTP_STATUSES
                    is ProtocolException, is SSLException -> false
                    else -> true
                }
                if (retried || !retryable) {
                    // 不附加底层异常：客户端或拦截器错误可能包含请求地址、账号或响应内容。
                    throw IOException(if (error is HttpFailure) "电费请求失败（HTTP ${error.status}）" else "电费网络请求失败")
                }
                retried = true
                retryBackoff()
                currentCoroutineContext().ensureActive()
            }
        }
    }

    private class HttpFailure(val status: Int) : IOException()

    private suspend fun executeOnce(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (!it.isSuccessful) throw HttpFailure(it.code)
                        it.body?.string().orEmpty()
                    }
                }
                result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }

    /** 不保留 Gson 原始异常，避免错误信息带出响应或账号。 */
    private fun parseJson(text: String): JsonElement? = try {
        JsonReader(StringReader(text)).use { reader ->
            reader.strictness = Strictness.STRICT
            val result = gson.fromJson<JsonElement>(reader, JsonElement::class.java)
            result.takeIf { reader.peek() == JsonToken.END_DOCUMENT }
        }
    } catch (_: Exception) {
        null
    }

    private fun JsonElement?.decoded(): JsonElement? =
        if (this?.isJsonPrimitive == true && asJsonPrimitive.isString) parseJson(asString) else this

    private fun JsonElement?.objectOrNull(): JsonObject? =
        decoded()?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonElement?.entries(): List<JsonElement> = when (val value = decoded()) {
        null -> emptyList()
        else -> when {
            value.isJsonArray -> value.asJsonArray.toList()
            value.isJsonObject -> listOf(value)
            else -> emptyList()
        }
    }

    private fun JsonObject.string(key: String): String? = get(key)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.number(vararg keys: String): Double? =
        keys.firstNotNullOfOrNull { key -> get(key).powerNumber() }

    private fun JsonObject.nonNegativeNumber(vararg keys: String): Double? =
        keys.firstNotNullOfOrNull { key -> get(key).powerNumber()?.takeIf { it >= 0.0 } }

    private fun JsonElement?.powerNumber(): Double? {
        val primitive = this?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return null
        if (!primitive.isString && !primitive.isNumber) return null
        // 仅折叠全角 ASCII 和全角空格，不将任意 Unicode 字符“猜测”为数字。
        val text = primitive.asString.map { char ->
            when (char) {
                '\u3000' -> ' '
                in '\uFF01'..'\uFF5E' -> (char.code - 0xFEE0).toChar()
                else -> char
            }
        }.joinToString("").trim()
        val match = POWER_NUMBER.matchEntire(text) ?: return null
        val numericText = match.groupValues[1].replace(",", "")
        val number = numericText.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        // 极小非零值不能因 Double 下溢而变成“零余额”或有效零耗电。
        return number.takeUnless { it == 0.0 && numericText.toBigDecimalOrNull()?.signum() != 0 }
    }

    private fun JsonObject.checkBusinessFailure() {
        if (isBusinessFailure()) throw IOException("电费服务返回失败状态")
    }

    private fun JsonObject.isBusinessFailure(): Boolean {
        fun token(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }
            ?.asString?.trim()?.lowercase(Locale.ROOT)
        if (token("success") == "false") return true
        // 仅识别明确失败标识和常见 HTTP 式错误码，不把未知业务 code 当作失败。
        return listOf("code", "status", "statusCode").any { key ->
            val value = token(key)
            value in ERROR_STATES || value in ERROR_CODES || (key == "status" && value == "false")
        }
    }

    private companion object {
        const val SERVICE_URL = "https://xqh5.17wanxiao.com/smartWaterAndElectricityService/SWAEServlet"
        val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS", Locale.ROOT)
        val BALANCE_KEYS = arrayOf("odd", "surpluselec", "surplusElec", "surplus_elec", "remain", "balance")
        // 支持规范千位分组、小数和科学计数；内部空白仅允许出现在单位之前。
        val POWER_NUMBER = Regex(
            """([+-]?(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)(?:\s*(?:度|kWh))?""",
            RegexOption.IGNORE_CASE,
        )
        val ERROR_STATES = setOf("false", "fail", "failed", "failure", "error")
        val ERROR_CODES = setOf("-1", "400", "401", "403", "404", "408", "429", "500", "502", "503", "504")
        val RETRY_HTTP_STATUSES = setOf(408, 502, 503, 504)
    }
}
