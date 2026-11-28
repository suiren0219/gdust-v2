package com.haooz.chedule.data.gdust

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 学校信息门户 smart-admin-api 客户端。
 *
 * 协议（2026-11-07 HAR + 校内 Python 全链实测，portal 公网可达，无需 WebVPN）：
 * - 换 TOKEN：GET {BASE}/smart-admin-api/user/login?loginCode=<ticket>&appId=portalRemote
 *   （ticket 必须走 query 参数；塞 Authorization 头会 500）
 * - 业务请求：请求头 TOKEN: <userBase.token>（形如 USER###学号###...）
 * - 响应壳：{code:1, msg, success, data}
 */
class PortalClient(
    private val http: OkHttpClient = GdustHttp.client,
) {

    companion object {
        const val BASE = "https://portal.gdust.edu.cn"
        const val API = "$BASE/smart-admin-api"
    }

    class PortalException(message: String) : Exception(message)

    /** CAS ticket → 门户 TOKEN。 */
    suspend fun exchangeToken(ticket: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$API/user/login?loginCode=$ticket&appId=portalRemote")
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            val obj = JsonParser.parseString(body).asJsonObject
            if (resp.code != 200 || obj.get("success")?.asBoolean != true) {
                throw PortalException("门户 SSO 失败(${resp.code}): ${obj.get("msg")?.takeIf { !it.isJsonNull }?.asString ?: body.take(120)}")
            }
            obj.getAsJsonObject("data")
                .getAsJsonObject("userBase")
                .get("token").asString
        }
    }

    /** 校历：当前周次、总周数、学期起止。 */
    suspend fun calendar(token: String, jobNumber: String): GdustCalendar = withContext(Dispatchers.IO) {
        val data = getData("app/zf/get_school_calendar?jobNumber=$jobNumber", token)
        GdustCalendar(
            year = data.get("year").asString,
            semester = data.get("semester").asString,
            week = data.get("week").asInt,
            allWeek = data.get("allWeek").asInt,
            startTime = data.get("startTime").asString,
            endTime = data.get("endTime").asString,
        )
    }

    /**
     * 课表：按天展开的课程列表。
     * @param week 指定周次（空串=全部）
     */
    suspend fun courses(token: String, jobNumber: String, week: String = ""): List<GdustLesson> =
        withContext(Dispatchers.IO) {
            val data = getData("app/zf/get_student_course?jobNumber=$jobNumber&week=$week&year=&semester=", token)
            val list = data.getAsJsonArray("courseList") ?: return@withContext emptyList()
            list.map { e ->
                val o = e.asJsonObject
                GdustLesson(
                    week = o.get("week")?.asInt ?: 0,
                    dayWeek = o.get("dayWeek")?.asInt ?: 0,
                    whichSection = o.get("whichSection")?.asInt ?: 0,
                    classroomName = o.get("classroomName")?.takeIf { !it.isJsonNull }?.asString,
                    courseName = o.get("courseName")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                    teacher = o.get("teacher")?.takeIf { !it.isJsonNull }?.asString,
                    courseDate = o.get("courseDate")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                )
            }
        }

    /** 学校通知公告（含保卫处反诈提醒），通知总结的数据源。 */
    suspend fun announcements(token: String, jobNumber: String): List<GdustAnnouncement> =
        withContext(Dispatchers.IO) {
            val data = getData("app/ding/get_announcement_items?jobNumber=$jobNumber", token)
            if (!data.isJsonArray) return@withContext emptyList()
            data.asJsonArray.map { e ->
                val o = e.asJsonObject
                GdustAnnouncement(
                    title = o.get("title")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                    url = o.get("url")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                    announcementType = o.get("announcementType")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                    issuer = o.get("issuer")?.takeIf { !it.isJsonNull }?.asString,
                    gmtCreate = o.get("gmtCreate")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
                )
            }
        }

    /** 用户信息（班级、院系）。 */
    suspend fun userInfo(token: String): GdustUserInfo = withContext(Dispatchers.IO) {
        val data = getData("app/userInfo/addition", token)
        GdustUserInfo(
            deptName = data.get("deptName")?.takeIf { !it.isJsonNull }?.asString,
            jobNum = data.get("jobNum")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
            realName = data.get("realName")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
        )
    }

    /** 取响应壳的 data 节点，失败统一抛 PortalException（含 TOKEN 失效 401 检测）。 */
    private fun getData(path: String, token: String): JsonObject {
        val req = Request.Builder()
            .url("$API/$path")
            .header("TOKEN", token)
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            val obj = JsonParser.parseString(body).asJsonObject
            val success = obj.get("success")?.asBoolean == true
            if (resp.code == 401 || !success) {
                throw PortalException(
                    if (resp.code == 401) "TOKEN_EXPIRED"
                    else "门户接口失败: ${obj.get("msg")?.takeIf { !it.isJsonNull }?.asString ?: body.take(120)}"
                )
            }
            return obj.getAsJsonObject("data") ?: obj.get("data")?.asJsonArray?.let {
                // data 为数组的接口（公告）包一层便于调用方处理
                JsonObject().apply { add("array", it) }
            } ?: JsonObject()
        }
    }
}

/** 共享的 OkHttpClient 实例（各 Client 默认复用同一连接池）。 */
object CasClientDefaults {
    val http: OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
}
