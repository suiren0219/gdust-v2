package com.menglimi.gdust.v2.data

import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.*
import org.junit.Test

class SessionPolicyTest {
    @Test fun networkAndMaintenanceDoNotInvalidateSavedLogin() {
        assertTrue(shouldKeepSession(true, SocketTimeoutException("timeout")))
        assertTrue(shouldKeepSession(true, IOException("offline")))
        assertTrue(shouldKeepSession(true, IllegalStateException("maintenance")))
        assertNull(schoolAuthFailure(500, "服务器维护"))
        assertNull(schoolAuthFailure(503, ""))
        assertNull(schoolAuthFailure(403, "网关拒绝访问"))
    }

    @Test fun explicitPortalExpiryIsRecognizedEvenInsideHttp500() {
        assertEquals(117, schoolAuthFailure(500, """{"code":117,"msg":"自动离线"}""")?.code)
        assertEquals(116, schoolAuthFailure(200, """{"code":116}""")?.code)
        assertEquals(401, schoolAuthFailure(401, "")?.code)
        assertFalse(shouldKeepSession(true, AuthFailed(117, "expired")))
    }

    @Test fun successfulResponseAndNullCodeAreNotExpiry() {
        assertNull(schoolAuthFailure(200, """{"code":0,"data":{}}"""))
        assertNull(schoolAuthFailure(200, """{"code":null}"""))
        assertFalse(shouldKeepSession(false, null))
    }

    @Test fun oneShotTicketIsOnlyReplayableWhenRequestNeverReachedSchool() {
        // 请求未发出：连接/DNS/TLS 握手失败、连接超时 → 重放安全
        assertTrue(java.net.ConnectException("Connection refused").isOneShotRetrySafe())
        assertTrue(java.net.UnknownHostException("cas.gdust.edu.cn").isOneShotRetrySafe())
        assertTrue(javax.net.ssl.SSLHandshakeException("handshake failed").isOneShotRetrySafe())
        assertTrue(SocketTimeoutException("connect timed out").isOneShotRetrySafe())
        // OkHttp 把底层连接失败包在 IOException 里，cause 链也要认出来
        assertTrue(IOException(java.net.ConnectException("refused")).isOneShotRetrySafe())
        // 请求已发出：读超时意味着票据可能已被消费 → 重放必然失败
        assertFalse(SocketTimeoutException("timeout").isOneShotRetrySafe())
        assertFalse(SocketTimeoutException("Read timed out").isOneShotRetrySafe())
        assertFalse(IOException(SocketTimeoutException("timeout")).isOneShotRetrySafe())
        // 业务错误与未知异常一律不重放
        assertFalse(AuthFailed(401, "expired").isOneShotRetrySafe())
        assertFalse(IllegalStateException("gate").isOneShotRetrySafe())
    }
}
