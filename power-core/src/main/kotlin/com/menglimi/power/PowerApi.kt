package com.menglimi.power

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build(),
    private val clock: Clock = Clock.systemDefaultZone(),
) : PowerDetailSource {
    private val gson = Gson()

    suspend fun rooms(account: String, customerCode: Int): List<PowerRoom> {
        val body = post("getbindroom", linkedMapOf("cmd" to "getbindroom", "account" to account), customerCode)
        val rooms = body.getAsJsonArray("roomlist") ?: return emptyList()
        return rooms.map { entry ->
            val room = entry.asJsonObject
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
        val meter = body.getAsJsonArray("modlist")?.firstOrNull()?.asJsonObject ?: body
        val week = meter.getAsJsonArray("weekuselist")?.mapNotNull { entry ->
            val day = entry.asJsonObject
            val label = day.string("weekday") ?: return@mapNotNull null
            val use = day.number("dayuse", "use") ?: return@mapNotNull null
            label.replace("星期", "") to use
        }.orEmpty()
        return PowerDetail(
            odd = meter.number("odd", "surpluselec", "surplusElec", "surplus_elec", "remain", "balance"),
            todayUse = meter.number("todayuse", "todayUse"),
            weekUse = week,
            monthUse = meter.getAsJsonArray("monthuselist")?.lastOrNull()?.asJsonObject?.number("monthuse"),
            sumBuy = meter.number("sumbuy"),
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
        val envelope = JsonParser.parseString(text).asJsonObject
        return JsonParser.parseString(envelope.string("body").orEmpty()).asJsonObject
    }

    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (!it.isSuccessful) throw IOException("电费请求失败（HTTP ${it.code}）")
                        it.body?.string().orEmpty()
                    }
                }
                result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }

    private fun JsonObject.string(key: String): String? = get(key)?.takeIf { !it.isJsonNull }?.asString

    private fun JsonObject.number(vararg keys: String): Double? {
        for (key in keys) string(key)?.toDoubleOrNull()?.let { return it }
        return null
    }

    private companion object {
        const val SERVICE_URL = "https://xqh5.17wanxiao.com/smartWaterAndElectricityService/SWAEServlet"
        val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS", Locale.ROOT)
    }
}
