package com.menglimi.gdust.v2.ui.screens

import com.menglimi.gdust.v2.data.ServiceAuthStatus
import com.menglimi.gdust.v2.data.ServiceProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 只测纯呈现映射：状态词、颜色语义与按钮动作，不涉及 Repository。 */
class ServiceStatusCardTest {
    @Test fun savedWithoutProblemWaitsForThisVerificationAndDoesNotReconnect() {
        val p = serviceRowPresentation(ServiceAuthStatus.SAVED, checking = false, problem = null)
        assertEquals("已保存连接，等待本次验证", p.statusText)
        assertEquals("验证", p.actionLabel)
        assertFalse(p.actionReconnects)
        assertTrue(p.actionEnabled)
        assertFalse(p.isGood)
        assertFalse(p.isError)
    }

    @Test fun verifiedWithReadProblemStaysNeutralAndDoesNotRequireReconnect() {
        val p = serviceRowPresentation(ServiceAuthStatus.VERIFIED, checking = false, problem = ServiceProblem.READ)
        // 上次验证仍算成功，但本次更新未完成：不绿标也不误报过期。
        assertFalse(p.isGood)
        assertFalse(p.isError)
        assertFalse(p.actionReconnects)
        assertTrue(p.actionEnabled)
        assertTrue(p.statusText.contains("上次已验证"))
        assertTrue(p.statusText.contains("读取未完成"))
    }

    @Test fun verifiedWithNetworkProblemDescribesNetworkInsteadOfExpiry() {
        val p = serviceRowPresentation(ServiceAuthStatus.VERIFIED, checking = false, problem = ServiceProblem.NETWORK)
        assertFalse(p.isGood)
        assertFalse(p.actionReconnects)
        assertTrue(p.statusText.contains("网络读取未完成"))
    }

    @Test fun authRequiredDemandsReconnectEvenWhenProblemIsNetwork() {
        val p = serviceRowPresentation(ServiceAuthStatus.AUTH_REQUIRED, checking = false, problem = ServiceProblem.NETWORK)
        assertTrue(p.actionReconnects)
        assertTrue(p.isError)
        assertTrue(p.statusText.contains("重新连接"))
    }

    @Test fun checkingDisablesTheAction() {
        val p = serviceRowPresentation(ServiceAuthStatus.SAVED, checking = true, problem = null)
        assertFalse(p.actionEnabled)
        assertFalse(p.actionReconnects)
        assertEquals("验证中", p.actionLabel)
    }

    @Test fun unconfirmedNeverClaimsNetworkFailure() {
        // UNCONFIRMED 只说明验证未完成，不冒充已分类的 NETWORK。
        val saved = serviceRowPresentation(ServiceAuthStatus.SAVED, checking = false, problem = ServiceProblem.UNCONFIRMED)
        assertFalse(saved.statusText.contains("网络"))
        val verified = serviceRowPresentation(ServiceAuthStatus.VERIFIED, checking = false, problem = ServiceProblem.UNCONFIRMED)
        assertFalse(verified.statusText.contains("网络"))
        val notConnected = serviceRowPresentation(ServiceAuthStatus.NOT_CONNECTED, checking = false, problem = ServiceProblem.UNCONFIRMED)
        assertFalse(notConnected.statusText.contains("网络"))
    }

    @Test fun notConnectedKeepsReconnectEntryAndShowsLastAttemptProblem() {
        val p = serviceRowPresentation(ServiceAuthStatus.NOT_CONNECTED, checking = false, problem = ServiceProblem.NETWORK)
        assertTrue(p.statusText.contains("未连接"))
        assertTrue(p.statusText.contains("最近一次尝试"))
        assertTrue(p.statusText.contains("网络不通"))
        assertTrue(p.actionReconnects)
    }

    @Test fun verifiedWithoutProblemIsGreenAndOffersRecheck() {
        val p = serviceRowPresentation(ServiceAuthStatus.VERIFIED, checking = false, problem = null)
        assertTrue(p.isGood)
        assertFalse(p.isError)
        assertFalse(p.actionReconnects)
        assertEquals("重新检查", p.actionLabel)
    }

    @Test fun savedWithProblemOffersRetryInsteadOfReconnect() {
        val network = serviceRowPresentation(ServiceAuthStatus.SAVED, checking = false, problem = ServiceProblem.NETWORK)
        assertTrue(network.statusText.contains("网络不通"))
        assertFalse(network.actionReconnects)
        val read = serviceRowPresentation(ServiceAuthStatus.SAVED, checking = false, problem = ServiceProblem.READ)
        assertTrue(read.statusText.contains("服务响应异常"))
        assertFalse(read.actionReconnects)
    }
}
