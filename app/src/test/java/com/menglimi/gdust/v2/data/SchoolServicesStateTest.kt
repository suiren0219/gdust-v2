package com.menglimi.gdust.v2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 三服务连接状态纯转换：单服务隔离、不确定保留认证、旧版本/旧 probe 拒绝、退出重置。 */
class SchoolServicesStateTest {

    private val restored = SchoolServicesState(
        portal = SchoolServiceStatus(auth = ServiceAuthStatus.VERIFIED),
        teaching = SchoolServiceStatus(auth = ServiceAuthStatus.SAVED),
        chx = SchoolServiceStatus(auth = ServiceAuthStatus.NOT_CONNECTED),
    )

    // ── 模型语义 ──

    @Test
    fun `默认状态无候选且无已验证服务`() {
        val state = SchoolServicesState()
        for (service in SchoolService.entries) {
            assertEquals(ServiceAuthStatus.NOT_CONNECTED, state[service].auth)
            assertFalse(state[service].checking)
            assertNull(state[service].problem)
            assertEquals(0L, state[service].checkedAt)
        }
        assertFalse(state.anyVerified)
        assertFalse(state.hasRestorableSession)
    }

    @Test
    fun `operator get 返回对应服务`() {
        assertEquals(restored.portal, restored[SchoolService.PORTAL])
        assertEquals(restored.teaching, restored[SchoolService.TEACHING])
        assertEquals(restored.chx, restored[SchoolService.CHX])
    }

    @Test
    fun `anyVerified 与 hasRestorableSession 按认证态判定`() {
        assertTrue(restored.anyVerified)
        assertTrue(restored.hasRestorableSession)
        val onlySaved = SchoolServicesState(teaching = SchoolServiceStatus(auth = ServiceAuthStatus.SAVED))
        assertFalse(onlySaved.anyVerified)
        assertTrue(onlySaved.hasRestorableSession)
    }

    // ── tracker：单服务隔离 ──

    @Test
    fun `单服务补丁不影响其他服务`() {
        val tracker = SchoolServicesTracker(restored)
        tracker.bindAccount(1L) { restored }
        val next = tracker.patch(SchoolService.PORTAL, 1L, null, ServiceAuthStatus.AUTH_REQUIRED, null, 100L)!!
        assertEquals(ServiceAuthStatus.AUTH_REQUIRED, next[SchoolService.PORTAL].auth)
        // 教务与学习通保持原状（补连 A 不清 B）
        assertEquals(ServiceAuthStatus.SAVED, next[SchoolService.TEACHING].auth)
        assertEquals(ServiceAuthStatus.NOT_CONNECTED, next[SchoolService.CHX].auth)
        assertEquals(next, tracker.snapshot())
    }

    @Test
    fun `不确定结果保持认证并标记问题`() {
        val tracker = SchoolServicesTracker(restored)
        tracker.bindAccount(1L) { restored }
        val next = tracker.patch(SchoolService.TEACHING, 1L, null, null, ServiceProblem.UNCONFIRMED, 200L)!!
        // null auth 保持 SAVED：业务读取失败不否认凭据
        assertEquals(ServiceAuthStatus.SAVED, next[SchoolService.TEACHING].auth)
        assertEquals(ServiceProblem.UNCONFIRMED, next[SchoolService.TEACHING].problem)
        assertEquals(200L, next[SchoolService.TEACHING].checkedAt)
        // checkedAt=0 保持原值
        val untouched = tracker.patch(SchoolService.TEACHING, 1L, null, null, ServiceProblem.READ, 0L)!!
        assertEquals(200L, untouched[SchoolService.TEACHING].checkedAt)
    }

    // ── tracker：版本校验 ──

    @Test
    fun `未绑定前一切写入被拒`() {
        val tracker = SchoolServicesTracker()
        assertNull(tracker.patch(SchoolService.PORTAL, 1L, null, ServiceAuthStatus.VERIFIED, null, 1L))
        assertNull(tracker.beginProbe(SchoolService.PORTAL, 1L, null))
    }

    @Test
    fun `旧账号版本写入被拒`() {
        val tracker = SchoolServicesTracker()
        assertTrue(tracker.bindAccount(2L) { restored })
        assertNull(tracker.patch(SchoolService.PORTAL, 1L, null, ServiceAuthStatus.VERIFIED, null, 1L))
        assertNull(tracker.beginProbe(SchoolService.TEACHING, 1L, null))
        assertEquals(restored, tracker.snapshot())
    }

    @Test
    fun `退出重置全部服务并拒绝旧版本`() {
        val tracker = SchoolServicesTracker()
        tracker.bindAccount(1L) { restored }
        assertEquals(ServiceAuthStatus.VERIFIED, tracker.snapshot()[SchoolService.PORTAL].auth)
        // 账号切换/退出：整体回到无候选
        assertTrue(tracker.bindAccount(2L) { SchoolServicesState() })
        for (service in SchoolService.entries) {
            assertEquals(ServiceAuthStatus.NOT_CONNECTED, tracker.snapshot()[service].auth)
        }
        assertNull(tracker.patch(SchoolService.PORTAL, 1L, null, ServiceAuthStatus.VERIFIED, null, 1L))
    }

    @Test
    fun `bindAccount 供应商仅在版本变化时调用`() {
        var calls = 0
        val tracker = SchoolServicesTracker()
        assertTrue(tracker.bindAccount(1L) { calls++; restored })
        assertEquals(1, calls)
        // 版本相同：幂等，不再读恢复态
        assertFalse(tracker.bindAccount(1L) { calls++; restored })
        assertEquals(1, calls)
    }

    // ── tracker：probe 代数 ──

    @Test
    fun `旧 probe 结果被新 probe 拒绝`() {
        val tracker = SchoolServicesTracker()
        tracker.bindAccount(1L) { SchoolServicesState() }
        val first = tracker.beginProbe(SchoolService.TEACHING, 1L, null)!!
        val second = tracker.beginProbe(SchoolService.TEACHING, 1L, null)!!
        assertTrue(second > first)
        // 旧探活晚到：拒绝且不覆盖新检查过程
        assertNull(tracker.finishProbe(SchoolService.TEACHING, 1L, null, first,
            ServiceAuthStatus.AUTH_REQUIRED, null, 1L))
        assertTrue(tracker.snapshot()[SchoolService.TEACHING].checking)
        val next = tracker.finishProbe(SchoolService.TEACHING, 1L, null, second,
            ServiceAuthStatus.VERIFIED, null, 5L)!!
        assertEquals(ServiceAuthStatus.VERIFIED, next[SchoolService.TEACHING].auth)
        assertFalse(next[SchoolService.TEACHING].checking)
    }

    @Test
    fun `取消探活只清检查过程不改认证`() {
        val tracker = SchoolServicesTracker(restored)
        tracker.bindAccount(1L) { restored }
        val generation = tracker.beginProbe(SchoolService.TEACHING, 1L, null)!!
        assertTrue(tracker.cancelProbe(SchoolService.TEACHING, 1L, null, generation))
        val status = tracker.snapshot()[SchoolService.TEACHING]
        assertFalse(status.checking)
        assertEquals(ServiceAuthStatus.SAVED, status.auth)
        assertNull(status.problem)
        // 同一代数不能重复收尾，也不会误清新 probe 的检查过程
        assertFalse(tracker.cancelProbe(SchoolService.TEACHING, 1L, null, generation))
    }

    @Test
    fun `版本变化后旧 probe 被拒且检查过程被重置`() {
        val tracker = SchoolServicesTracker()
        tracker.bindAccount(1L) { SchoolServicesState() }
        tracker.bindChx(5L)
        val generation = tracker.beginProbe(SchoolService.CHX, 1L, 5L)!!
        assertTrue(tracker.snapshot()[SchoolService.CHX].checking)
        // 学习通版本推进：旧 probe 作废，checking 一并清理
        assertTrue(tracker.bindChx(6L, SchoolServiceStatus()))
        assertFalse(tracker.snapshot()[SchoolService.CHX].checking)
        assertFalse(tracker.cancelProbe(SchoolService.CHX, 1L, 5L, generation))
        assertNull(tracker.finishProbe(SchoolService.CHX, 1L, 5L, generation,
            ServiceAuthStatus.VERIFIED, null, 1L))
    }

    // ── tracker：CHX 版本只约束 CHX ──

    @Test
    fun `CHX 版本绑定只约束 CHX`() {
        val tracker = SchoolServicesTracker(restored)
        tracker.bindAccount(1L) { restored }
        tracker.bindChx(7L, SchoolServiceStatus(auth = ServiceAuthStatus.SAVED))
        assertNull(tracker.patch(SchoolService.CHX, 1L, 6L, ServiceAuthStatus.VERIFIED, null, 1L))
        assertTrue(tracker.patch(SchoolService.CHX, 1L, 7L, ServiceAuthStatus.VERIFIED, null, 1L) != null)
        // 教务与门户的写入不受学习通版本约束
        assertTrue(tracker.patch(SchoolService.TEACHING, 1L, null, ServiceAuthStatus.VERIFIED, null, 1L) != null)
        assertTrue(tracker.patch(SchoolService.PORTAL, 1L, null, ServiceAuthStatus.VERIFIED, null, 1L) != null)
    }

    @Test
    fun `bindChx 只重置学习通`() {
        val tracker = SchoolServicesTracker(restored)
        tracker.bindAccount(1L) { restored }
        tracker.bindChx(8L, SchoolServiceStatus())
        assertEquals(ServiceAuthStatus.VERIFIED, tracker.snapshot()[SchoolService.PORTAL].auth)
        assertEquals(ServiceAuthStatus.SAVED, tracker.snapshot()[SchoolService.TEACHING].auth)
        assertEquals(ServiceAuthStatus.NOT_CONNECTED, tracker.snapshot()[SchoolService.CHX].auth)
    }

    // ── tracker：并发顺序（旧探活 vs 新登录/业务补丁）──

    @Test
    fun `无令牌补丁作废旧探活且旧 finish 不能覆盖`() {
        val published = mutableListOf<SchoolServicesState>()
        val tracker = SchoolServicesTracker { published.add(it) }
        tracker.bindAccount(1L) { SchoolServicesState() }
        val staleGeneration = tracker.beginProbe(SchoolService.PORTAL, 1L, null)!!
        // 新登录补丁（无令牌）：作废旧探活代数并结束其检查过程
        val patched = tracker.patch(SchoolService.PORTAL, 1L, null, ServiceAuthStatus.VERIFIED, null, 10L)!!
        assertTrue(ServiceAuthStatus.VERIFIED == patched[SchoolService.PORTAL].auth)
        assertFalse(patched[SchoolService.PORTAL].checking)
        // 旧探活晚到 finish：拒绝，新登录结果不被覆盖
        assertNull(tracker.finishProbe(SchoolService.PORTAL, 1L, null, staleGeneration,
            ServiceAuthStatus.AUTH_REQUIRED, null, 5L))
        assertEquals(ServiceAuthStatus.VERIFIED, tracker.snapshot()[SchoolService.PORTAL].auth)
        assertNull(tracker.snapshot()[SchoolService.PORTAL].problem)
    }

    @Test
    fun `携带令牌的内部记录不递增且外层 finish 仍有效`() {
        val tracker = SchoolServicesTracker()
        tracker.bindAccount(1L) { SchoolServicesState() }
        tracker.bindChx(7L)
        val generation = tracker.beginProbe(SchoolService.CHX, 1L, 7L)!!
        // 探活内部的分类记录（NETWORK）带令牌提交：不递增代数
        val recorded = tracker.patch(SchoolService.CHX, 1L, 7L, null, ServiceProblem.NETWORK, 10L, generation)!!
        assertEquals(ServiceProblem.NETWORK, recorded[SchoolService.CHX].problem)
        assertTrue(recorded[SchoolService.CHX].checking) // 探活仍在进行
        // 外层 finish 用同一令牌仍能提交，并清掉 problem
        val finished = tracker.finishProbe(SchoolService.CHX, 1L, 7L, generation,
            ServiceAuthStatus.VERIFIED, null, 20L)!!
        assertEquals(ServiceAuthStatus.VERIFIED, finished[SchoolService.CHX].auth)
        assertNull(finished[SchoolService.CHX].problem)
        assertFalse(finished[SchoolService.CHX].checking)
        // 错误令牌的补丁被拒
        assertNull(tracker.patch(SchoolService.CHX, 1L, 7L, ServiceAuthStatus.AUTH_REQUIRED, null, 30L, generation + 999))
    }

    @Test
    fun `取消后新探活代数不复用`() {
        val tracker = SchoolServicesTracker()
        tracker.bindAccount(1L) { SchoolServicesState() }
        val first = tracker.beginProbe(SchoolService.TEACHING, 1L, null)!!
        assertTrue(tracker.cancelProbe(SchoolService.TEACHING, 1L, null, first))
        val second = tracker.beginProbe(SchoolService.TEACHING, 1L, null)!!
        assertTrue(second > first) // 代数单调，旧 token 无法匹配新探活
        assertNull(tracker.finishProbe(SchoolService.TEACHING, 1L, null, first,
            ServiceAuthStatus.AUTH_REQUIRED, null, 5L))
        assertTrue(tracker.finishProbe(SchoolService.TEACHING, 1L, null, second,
            ServiceAuthStatus.VERIFIED, null, 6L) != null)
    }

    @Test
    fun `多服务探活与补丁互不干扰`() {
        val tracker = SchoolServicesTracker()
        tracker.bindAccount(1L) { SchoolServicesState() }
        tracker.bindChx(7L)
        val portalGeneration = tracker.beginProbe(SchoolService.PORTAL, 1L, null)!!
        val chxGeneration = tracker.beginProbe(SchoolService.CHX, 1L, 7L)!!
        // PORTAL 的业务补丁只作废 PORTAL 探活
        tracker.patch(SchoolService.PORTAL, 1L, null, ServiceAuthStatus.VERIFIED, null, 10L)
        assertNull(tracker.finishProbe(SchoolService.PORTAL, 1L, null, portalGeneration,
            ServiceAuthStatus.AUTH_REQUIRED, null, 1L))
        // CHX 探活不受影响，仍可正常提交
        val chxFinished = tracker.finishProbe(SchoolService.CHX, 1L, 7L, chxGeneration,
            ServiceAuthStatus.VERIFIED, null, 11L)!!
        assertEquals(ServiceAuthStatus.VERIFIED, chxFinished[SchoolService.CHX].auth)
        assertFalse(chxFinished[SchoolService.CHX].checking)
    }

    @Test
    fun `发布回调始终收到锁内最新快照`() {
        val published = mutableListOf<SchoolServicesState>()
        val tracker = SchoolServicesTracker { published.add(it) }
        tracker.bindAccount(1L) { SchoolServicesState() }
        tracker.patch(SchoolService.PORTAL, 1L, null, ServiceAuthStatus.VERIFIED, null, 10L)
        tracker.patch(SchoolService.TEACHING, 1L, null, null, ServiceProblem.READ, 11L)
        // 每次发布的都是当时的最新状态，且不会出现旧快照最后写回
        assertEquals(tracker.snapshot(), published.last())
        assertEquals(ServiceAuthStatus.VERIFIED, published.last()[SchoolService.PORTAL].auth)
        assertEquals(ServiceProblem.READ, published.last()[SchoolService.TEACHING].problem)
        assertTrue(published.zipWithNext().none { (prev, next) ->
            prev.portal.auth == ServiceAuthStatus.VERIFIED && next.portal.auth != ServiceAuthStatus.VERIFIED
        })
    }
}
