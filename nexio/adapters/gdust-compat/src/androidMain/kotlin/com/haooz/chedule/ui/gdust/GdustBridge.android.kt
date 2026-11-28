package com.haooz.chedule.ui.gdust

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.haooz.chedule.data.gdust.GdustSseEvent
import com.haooz.chedule.data.gdust.GdustService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Android 侧广科桥：包 [GdustService]（core androidMain，OkHttp 直连学校系统），
 * 把结果翻译成 commonMain 数据类型。Context 由 LocalContext 取。
 */
@Composable
actual fun rememberGdustBridge(): GdustBridge? {
    val context = LocalContext.current
    return remember { AndroidGdustBridge(context) }
}

private class AndroidGdustBridge(private val context: Context) : GdustBridge {

    private val service = GdustService(context)

    override suspend fun isLoggedIn(): Boolean =
        service.store.hasSession && service.probeSession()

    override suspend fun fetchCaptcha(): Result<GdustCaptcha> = runCatching {
        val c = service.fetchCaptcha()
        val bytes = java.util.Base64.getDecoder().decode(c.base64Jpeg)
        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("验证码图解码失败")
        GdustCaptcha(image = bmp.asImageBitmap(), uuid = c.uuid)
    }

    override suspend fun login(
        account: String,
        password: String,
        captchaCode: String,
        captchaUuid: String,
    ): Result<Unit> = runCatching {
        service.loginWithPassword(account, password, captchaCode, captchaUuid)
    }

    override suspend fun pullSchedule(): Result<GdustScheduleData> = runCatching {
        val cal = service.calendar()
        val lessons = service.lessons()
        GdustScheduleData(
            calendar = GdustCalendarData(
                week = cal.week,
                allWeek = cal.allWeek,
                startDate = cal.startTime,
            ),
            courses = lessons.map { l ->
                GdustCourseData(
                    name = l.courseName,
                    classroom = l.classroomName.orEmpty(),
                    teacher = l.teacher.orEmpty(),
                    dayOfWeek = l.dayWeek,
                    startSection = l.whichSection,
                    endSection = l.whichSection,
                    weeks = listOf(l.week),
                )
            },
        )
    }

    override suspend fun powerRooms(account: String, customerCode: Int): Result<List<GdustPowerRoomData>> =
        runCatching {
            service.powerRooms(account, customerCode).map {
                GdustPowerRoomData(
                    fullName = it.roomfullname ?: it.roomverify.orEmpty(),
                    roomVerify = it.roomverify.orEmpty(),
                )
            }
        }

    override suspend fun powerReading(): Result<Double?> = runCatching {
        service.powerReading().odd
    }

    override fun powerBinding(): GdustPowerBindingData? {
        val st = service.store
        if (!st.hasPowerBinding) return null
        return GdustPowerBindingData(
            account = st.powerAccount,
            customerCode = st.powerCustomerCode,
            roomVerify = st.powerRoomVerify,
            roomName = st.powerRoomName,
        )
    }

    override fun savePowerBinding(account: String, customerCode: Int, roomVerify: String, roomName: String) {
        service.savePowerBinding(account, customerCode, roomVerify, roomName)
    }

    override fun llmConfig(): GdustLlmConfigData {
        val st = service.store
        return GdustLlmConfigData(base = st.llmBase, key = st.llmKey, model = st.llmModel)
    }

    override fun saveLlmConfig(base: String, key: String, model: String) {
        val st = service.store
        st.llmBase = base
        st.llmKey = key
        st.llmModel = model
    }

    override suspend fun summarizeAnnouncements(): Result<String> =
        runCatching { service.summarizeAnnouncements() }

    override suspend fun finishSsoWithTicket(ticket: String): Result<Unit> =
        runCatching { service.finishSso(ticket) }

    override fun subscribeSse(): Flow<GdustSseEventData> =
        service.subscribeSse().map { ev ->
            when (ev) {
                is GdustSseEvent.Hello -> GdustSseEventData.Hello(ev.clientId)
                is GdustSseEvent.Ticket -> GdustSseEventData.Ticket(ev.ticket)
                is GdustSseEvent.NeedBind -> GdustSseEventData.NeedBind(ev.unionId)
                is GdustSseEvent.Error -> GdustSseEventData.Error(ev.message)
            }
        }

    override fun openDingTalkAuth(clientId: String): Boolean {
        val url = service.mobieAuthUrl(clientId)
        val ctx = context
        val ding = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.alibaba.android.rimet")
        return try {
            ctx.startActivity(ding.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (e: Exception) {
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
            } catch (e2: Exception) { false }
        }
    }
}
