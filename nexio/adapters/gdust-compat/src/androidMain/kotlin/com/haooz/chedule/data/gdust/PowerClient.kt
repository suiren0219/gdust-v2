package com.haooz.chedule.data.gdust

import com.google.gson.JsonParser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 完美校园（支付宝小程序）智能水电客户端。
 *
 * 协议来自哥哥的 astrbot_plugin_dorm_power（github.com/suiren0219/-seed power_service.py）逆向成果：
 * - POST https://xqh5.17wanxiao.com/smartWaterAndElectricityService/SWAEServlet
 * - 表单：param={"cmd":..,...} + customercode + method(=cmd) + command="JBSWaterElecService"
 * - 时间戳 yyyyMMddHHmmssSSS；响应 {"code_":0,"result_":"true","body":"<JSON串>"}，body 二次解析
 * - 无登录态：account（完美校园 outid，一般是身份证号）+ customercode（学校代码）+ roomverify 三个静态值即可
 */
class PowerClient(
    private val http: OkHttpClient = GdustHttp.client,
) {

    companion object {
        const val URL = "https://xqh5.17wanxiao.com/smartWaterAndElectricityService/SWAEServlet"
        const val COMMAND = "JBSWaterElecService"
        private val TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")

        /** 剩余电量字段的候选名（上游改版时的容错顺序，与插件版一致） */
        private val ODD_FIELDS = listOf("odd", "surpluselec", "surplusElec", "surplus_elec", "remain", "balance")
    }

    class PowerException(message: String) : Exception(message)

    /** 账号绑定的房间列表（getbindroom）。 */
    suspend fun bindRooms(account: String, customerCode: Int): List<GdustPowerRoom> =
        withContext(Dispatchers.IO) {
            val body = post("getbindroom", """{"cmd":"getbindroom","account":${jsonStr(account)}}""", customerCode)
            val rooms = body.getAsJsonArray("roomlist") ?: return@withContext emptyList()
            rooms.map { e ->
                val o = e.asJsonObject
                GdustPowerRoom(
                    roomfullname = o.get("roomfullname")?.takeIf { !it.isJsonNull }?.asString,
                    roomverify = o.get("roomverify")?.takeIf { !it.isJsonNull }?.asString,
                )
            }
        }

    /** 单房间电量（h5_getstuindexpage）。 */
    suspend fun roomPower(account: String, roomVerify: String, customerCode: Int): GdustPowerReading =
        withContext(Dispatchers.IO) {
            val ts = LocalDateTime.now().format(TS_FORMAT)
            val param = """{"cmd":"h5_getstuindexpage","account":${jsonStr(account)},""" +
                """"roomverify":${jsonStr(roomVerify)},"timestamp":${jsonStr(ts)}}"""
            val body = post("h5_getstuindexpage", param, customerCode)
            GdustPowerReading(odd = extractOdd(body), raw = body.toString())
        }

    /** 探测账号在某学校代码下是否存在（搜校用，App 内用于首次绑定校验）。 */
    suspend fun checkAccount(account: String, customerCode: Int): Boolean? =
        withContext(Dispatchers.IO) {
            val ts = LocalDateTime.now().format(TS_FORMAT)
            val param = """{"cmd":"login","outid":${jsonStr(account)},"account":${jsonStr(account)},""" +
                """"timestamp":${jsonStr(ts)}}"""
            try {
                val body = post("login", param, customerCode)
                body.get("result")?.asString == "0" || body.get("empname")?.isJsonNull == false
            } catch (e: PowerException) {
                false   // 接口通了，业务上无此人
            } catch (e: Exception) {
                null    // 网络失败，调用方要区分
            }
        }

    /** 发起 SWAEServlet 请求并完成两层 JSON 解析，返回内层 body。 */
    private fun post(cmd: String, param: String, customerCode: Int): com.google.gson.JsonObject {
        val form = FormBody.Builder()
            .add("param", param)
            .add("customercode", customerCode.toString())
            .add("method", cmd)
            .add("command", COMMAND)
            .build()
        val req = Request.Builder()
            .url(URL)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
            .post(form)
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.code == 429 || resp.code >= 500) {
                throw PowerException("上游限流/错误 HTTP ${resp.code}")
            }
            val outer = JsonParser.parseString(text).asJsonObject
            if (outer.get("result_")?.asString != "true") {
                throw PowerException("水电接口业务失败: ${outer.get("message_")?.takeIf { !it.isJsonNull }?.asString ?: text.take(120)}")
            }
            val raw = outer.get("body")?.takeIf { !it.isJsonNull }?.asString
                ?: throw PowerException("响应缺少 body")
            return JsonParser.parseString(raw).asJsonObject
        }
    }

    /** 按 modlist.0.odd 及候选字段名提取剩余电量。 */
    private fun extractOdd(body: com.google.gson.JsonObject): Double? {
        // 路径 modlist.0.odd
        val modlist = body.getAsJsonArray("modlist")
        if (modlist != null && modlist.size() > 0) {
            val first = modlist[0].asJsonObject
            for (f in ODD_FIELDS) {
                val v = first.get(f) ?: continue
                if (!v.isJsonNull) return v.asString.toDoubleOrNull() ?: v.asDouble
            }
        }
        // 顶层候选字段
        for (f in ODD_FIELDS) {
            val v = body.get(f) ?: continue
            if (!v.isJsonNull) return v.asString.toDoubleOrNull() ?: v.asDouble
        }
        return null
    }

    private fun jsonStr(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
