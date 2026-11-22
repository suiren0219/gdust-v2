package com.menglimi.gdust.v2.data

import java.io.IOException
import java.net.SocketTimeoutException
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleSyncFailureTest {
    private val night = LocalTime.of(23, 5)

    private fun message(
        error: Throwable,
        now: LocalTime = night,
        enabled: Boolean = true,
    ) = syncFailureMessage(
        error, now, MaintenanceWindow.DEFAULT_START_MIN, MaintenanceWindow.DEFAULT_END_MIN, enabled,
    )

    @Test
    fun portalStreamFailureAtNightDoesNotImplyTeachingMaintenance() {
        val expected = "学校门户连接中断，请重试；如需整学期课表，可使用教务学期导入。"
        // 不依赖 URL、主机名或 ticket 才能识别网络错误。
        assertEquals(expected, message(IOException("unexpected end of stream")))
        assertEquals(expected, message(IOException(
            "unexpected end of stream on https://portal.example.test/api?ticket=synthetic-ticket",
        )))
        assertEquals(expected, message(IllegalStateException("请求失败", IOException("unexpected end of stream"))))
        assertFalse(expected.contains("维护"))
        assertFalse(expected.contains("停服"))
        assertFalse(expected.contains("关闭"))
    }

    @Test
    fun teachingFailureKeepsCauseAndNetworkDetection() {
        val original = IOException("unexpected end of stream")
        val failure = original.asTeachingSyncFailure()
        assertTrue(failure is TeachingSyncFailure)
        assertSame(original, failure.cause)
        assertTrue(failure.isNetworkDown())
        assertSame(failure, failure.asTeachingSyncFailure())
    }

    @Test
    fun onlyTeachingMarkerAddsAnAdvisoryAtNight() {
        // 即使异常里的地址写的是门户，也只根据实际读取时加的类型标记决定来源。
        val failure = TeachingSyncFailure(IOException("unexpected end of stream on https://portal.example.test"))
        val text = message(failure)
        assertTrue(text.startsWith("教务连接中断，请重试或换入口。\n\n"))
        assertTrue(text.contains("设置的维护提醒时段（23:00–06:30）"))
        assertTrue(text.contains("可能正在维护"))
        assertTrue(text.contains("仅供参考，不代表教务停服，可继续重试或换入口"))
        assertFalse(text.contains("夜间关闭"))
        assertFalse(text.contains("停止服务"))
        assertFalse(text.contains("请白天再试"))
    }

    @Test
    fun teachingFailureDuringDayShowsActualErrorOnly() {
        assertEquals(
            "教务连接超时，请稍后重试。",
            message(TeachingSyncFailure(SocketTimeoutException("timeout")), LocalTime.NOON),
        )
    }

    @Test
    fun disabledReminderShowsActualTeachingErrorAtNight() {
        assertEquals(
            "教务连接超时，请稍后重试。",
            message(TeachingSyncFailure(SocketTimeoutException("timeout")), enabled = false),
        )
    }

    @Test
    fun invalidOrEmptyWindowDoesNotAddReminder() {
        val failure = TeachingSyncFailure(IOException("connection reset"))
        for ((start, end) in listOf(600 to 600, -1 to 390, 1380 to 1440)) {
            assertEquals("教务网络连接失败，请检查网络后重试。", syncFailureMessage(failure, night, start, end, true))
        }
    }

    @Test
    fun authFailureIsNotWrappedAndKeepsOriginalMessage() {
        val failure = AuthFailed(401, "教务会话已失效，请完成学校统一登录。")
        assertSame(failure, failure.asTeachingSyncFailure())
        assertEquals(failure.message, message(failure))
        assertEquals(failure.message, message(failure, enabled = false))
        // 即使其他调用者误加标记，认证错误也不应追加维护推断。
        assertEquals(failure.message, message(TeachingSyncFailure(failure)))
    }

    @Test
    fun cancellationIsNotWrappedOrTurnedIntoFailureMessage() {
        val cancellation = CancellationException("会话已变更")
        assertSame(cancellation, cancellation.asTeachingSyncFailure())
        assertSame(cancellation, assertThrows(CancellationException::class.java) { message(cancellation) })
        assertSame(cancellation, assertThrows(CancellationException::class.java) {
            message(TeachingSyncFailure(cancellation))
        })
    }

    @Test
    fun publicHttpFailureRetainsStatusWithoutTimeConclusion() {
        val failure = IllegalStateException("学校请求失败（HTTP 502）")
        assertEquals(failure.message, message(failure))
        assertEquals(failure.message, message(failure, LocalTime.NOON))
    }

    @Test
    fun teachingHttpFailureKeepsStatusAheadOfAdvisory() {
        val failure = TeachingSyncFailure(IllegalStateException("教务 HTTP 503"))
        assertTrue(message(failure).startsWith("教务 HTTP 503\n\n"))
        assertEquals("教务 HTTP 503", message(failure, enabled = false))
    }

    @Test
    fun errorMessagesDoNotExposeRequestUrlsOrTickets() {
        val url = "https://portal.example.test/user/login?ticket=synthetic-ticket&token=synthetic-token"
        val networkText = message(IOException("connection reset on $url"))
        val httpText = message(IllegalStateException("学校请求失败（HTTP 500） $url"))
        assertEquals("学校门户网络连接失败，请检查网络后重试。", networkText)
        assertTrue(httpText.contains("HTTP 500"))
        for (text in listOf(networkText, httpText)) {
            assertFalse(text.contains("https://"))
            assertFalse(text.contains("synthetic-ticket"))
            assertFalse(text.contains("synthetic-token"))
        }
    }

    @Test
    fun messageLessFailureHasReadableFallback() {
        assertEquals("课表同步失败，请重试。", message(Exception()))
    }
}
