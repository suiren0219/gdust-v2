package com.haooz.chedule.ui.gdust

import androidx.compose.runtime.Composable

/**
 * 广科数据桥：commonMain 接口 + 平台 actual。
 * 只有 Android 提供 [rememberGdustBridge] 的真实现（OkHttp 直连学校系统）；
 * 桌面/iOS 返回 null，同步页显示「该平台不支持」。
 *
 * 全部方法挂 Result：网络失败/业务失败都由页面统一提示，桥内不弹 UI。
 */

/** 校历（当前周次等），startDate 为学期第一周周一 yyyy-MM-dd。 */
data class GdustCalendarData(
    val week: Int,
    val allWeek: Int,
    val startDate: String,
)

/** 单节课：按周次列表表达（对应拾光/Nexio 的 weeks 数组）。 */
data class GdustCourseData(
    val name: String,
    val classroom: String,
    val teacher: String,
    val dayOfWeek: Int,      // 1=周一
    val startSection: Int,
    val endSection: Int,
    val weeks: List<Int>,
)

/** 拉取课表结果：校历 + 课程列表。 */
data class GdustScheduleData(
    val calendar: GdustCalendarData,
    val courses: List<GdustCourseData>,
)

/** 验证码：已解码位图（平台侧解码）+ 本轮 uuid（登录时原样回传）。 */
data class GdustCaptcha(
    val image: androidx.compose.ui.graphics.ImageBitmap,
    val uuid: String,
)

interface GdustBridge {
    /** 是否已有登录态（TOKEN 存在且探活成功）。 */
    suspend fun isLoggedIn(): Boolean

    /** 取新验证码。 */
    suspend fun fetchCaptcha(): Result<GdustCaptcha>

    /** 账号密码登录（CAS 明文 JSON + 门户 SSO）。验证码错误会以失败返回，直接换码重试。 */
    suspend fun login(account: String, password: String, captchaCode: String, captchaUuid: String): Result<Unit>

    /** 登录后拉校历+课表。 */
    suspend fun pullSchedule(): Result<GdustScheduleData>

    /** 水电：按账号+学校代码拉房间列表（完美校园 SWAEServlet，无登录态）。 */
    suspend fun powerRooms(account: String, customerCode: Int): Result<List<GdustPowerRoomData>>

    /** 水电：查已绑定房间的剩余电量。 */
    suspend fun powerReading(): Result<Double?>

    /** 水电：当前绑定信息；未绑定为 null。 */
    fun powerBinding(): GdustPowerBindingData?

    /** 水电：保存绑定三静态值。 */
    fun savePowerBinding(account: String, customerCode: Int, roomVerify: String, roomName: String)

    /** LLM：当前配置；未配置字段为空串。 */
    fun llmConfig(): GdustLlmConfigData

    /** LLM：保存配置（用户自填，存本机）。 */
    fun saveLlmConfig(base: String, key: String, model: String)

    /** 通知：拉最近公告并让 LLM 总结要点（需已配置 LLM）。 */
    suspend fun summarizeAnnouncements(): Result<String>

    /** 扫码授权成功后用 ticket 换门户 TOKEN 并落会话。 */
    suspend fun finishSsoWithTicket(ticket: String): Result<Unit>

    /** 钉钉扫码事件流（仅 Android 有实现，返回 null 表示不支持）。 */
    fun subscribeSse(): kotlinx.coroutines.flow.Flow<GdustSseEventData>?

    /** 打开钉钉授权页（跳系统/钉钉）。 */
    fun openDingTalkAuth(clientId: String): Boolean

}

/** 平台桥。仅 Android 返回实例；桌面/iOS 返回 null（同步页显示不支持）。 */
@Composable
expect fun rememberGdustBridge(): GdustBridge?


/** 水电房间（绑定列表条目）。 */
data class GdustPowerRoomData(
    val fullName: String,
    val roomVerify: String,
)

/** 水电绑定信息。 */
data class GdustPowerBindingData(
    val account: String,
    val customerCode: Int,
    val roomVerify: String,
    val roomName: String,
)

/** LLM 总结配置（OpenAI 兼容中转站），全用户自填。 */
data class GdustLlmConfigData(
    val base: String,
    val key: String,
    val model: String,
)


/** 扫码事件（commonMain 抽象）。 */
sealed class GdustSseEventData {
    data class Hello(val clientId: String) : GdustSseEventData()
    data class Ticket(val ticket: String) : GdustSseEventData()
    data class NeedBind(val unionId: String) : GdustSseEventData()
    data class Error(val message: String) : GdustSseEventData()
}
