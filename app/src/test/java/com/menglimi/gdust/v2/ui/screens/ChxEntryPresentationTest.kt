package com.menglimi.gdust.v2.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChxEntryPresentationTest {
    @Test fun partialSyncKeepsBothCourseCountsAndTodoChanges() {
        val result = chxWorkSyncSummary(
            "部分完成：检查了 54 门课（42 门读取失败：\n" +
                "40 门：课程页缺少作业查询参数，请在学习通网页查看。\n" +
                "2 门：请求未完成），新增 3 条、更新 5 条、合并 1 条通知重复，未交 6 条",
        )
        assertEquals(ChxSyncOutcome.PARTIAL, result.outcome)
        assertEquals("部分完成", result.title)
        assertEquals(54, result.successfulCourses)
        assertEquals(42, result.failedCourses)
        assertEquals("新增 3 · 更新 5 · 合并 1 · 未交 6", result.changeSummary)
        assertTrue(result.needsAttention)
        assertFalse(result.authRequired)
    }

    @Test fun partialSyncWithoutWorkIsStillPartial() {
        val result = chxWorkSyncSummary("部分完成：已检查 54 门课程（42 门读取失败：\n42 门：请求未完成）；本地待办已保留")
        assertEquals(ChxSyncOutcome.PARTIAL, result.outcome)
        assertEquals(54, result.successfulCourses)
        assertEquals(42, result.failedCourses)
        assertNull(result.changeSummary)
    }

    @Test fun allCourseFailuresRemainFailedWithZeroSuccesses() {
        val result = chxWorkSyncSummary("作业同步未完成：96 门课程未能读取，本地待办已保留。\n96 门：请求未完成", failed = true)
        assertEquals(ChxSyncOutcome.FAILED, result.outcome)
        assertEquals(0, result.successfulCourses)
        assertEquals(96, result.failedCourses)
        assertTrue(result.needsAttention)
    }

    @Test fun authenticationFailureInsideNormalPartialReturnRequiresReconnection() {
        val result = chxWorkSyncSummary("部分完成：已检查 54 门课程（42 门读取失败：\n42 门：课程页要求重新登录学习通，请在网页完成认证。）；本地待办已保留")
        assertEquals(ChxSyncOutcome.PARTIAL, result.outcome)
        assertTrue(result.authRequired)
        assertTrue(result.needsAttention)
    }

    @Test fun completeSyncDoesNotInventPartialOrFailedCourses() {
        val result = chxWorkSyncSummary("同步完成：检查了 54 门课，新增 0 条、更新 8 条，未交 0 条")
        assertEquals(ChxSyncOutcome.COMPLETE, result.outcome)
        assertEquals(54, result.successfulCourses)
        assertEquals(0, result.failedCourses)
        assertEquals("新增 0 · 更新 8 · 未交 0", result.changeSummary)
        assertFalse(result.needsAttention)
    }

    @Test fun explicitEmptyCoursesAndNoWorkAreValidResults() {
        val empty = chxWorkSyncSummary("已读取课程列表，当前没有课程；本地待办已保留")
        assertEquals(ChxSyncOutcome.COMPLETE, empty.outcome)
        assertEquals(0, empty.successfulCourses)
        assertEquals(0, empty.failedCourses)
        val noWork = chxWorkSyncSummary("已检查 54 门课程，未发现作业；本地待办已保留")
        assertEquals(ChxSyncOutcome.COMPLETE, noWork.outcome)
        assertEquals(54, noWork.successfulCourses)
        assertEquals(0, noWork.failedCourses)
        assertEquals("未发现作业", noWork.changeSummary)
    }

    @Test fun failureBeforeCourseReadDoesNotInventCounts() {
        val result = chxWorkSyncSummary("作业同步未完成，请检查网络后重试；现有待办已保留。", failed = true)
        assertEquals(ChxSyncOutcome.FAILED, result.outcome)
        assertNull(result.successfulCourses)
        assertNull(result.failedCourses)
        assertNull(result.changeSummary)
    }

    @Test fun unknownOrChangedResultNeverLooksSuccessful() {
        listOf("", "服务正常", "同步完成", "已更新 54 条", "同步完成：检查了 999999999999 门课").forEach {
            val result = chxWorkSyncSummary(it)
            assertEquals(ChxSyncOutcome.UNCONFIRMED, result.outcome)
            assertTrue(result.needsAttention)
            assertNull(result.successfulCourses)
            assertNull(result.failedCourses)
        }
    }

    @Test fun diagnosticsDoNotBecomeTopLevelCourseCounts() {
        val result = chxWorkSyncSummary("作业同步未完成。\n诊断：检查了 54 门课（42 门读取失败）", failed = true)
        assertEquals(ChxSyncOutcome.FAILED, result.outcome)
        assertNull(result.successfulCourses)
        assertNull(result.failedCourses)
    }

    @Test fun exceptionFlagWinsEvenIfMessageLooksComplete() {
        val result = chxWorkSyncSummary("同步完成：检查了 54 门课，新增 0 条、更新 8 条，未交 0 条", failed = true)
        assertEquals(ChxSyncOutcome.FAILED, result.outcome)
        assertTrue(result.needsAttention)
        assertNull(result.failedCourses)
    }

    @Test fun savedCandidateAloneAndUnknownNetworkAreNotConnected() {
        val pending = chxConnectionPresentation(saved = true, homeVerified = null)
        assertEquals("待验证", pending.title)
        assertFalse(pending.verified)
        val absent = chxConnectionPresentation(saved = false, homeVerified = true)
        assertEquals("未连接", absent.title)
        assertFalse(absent.verified)
    }

    @Test fun onlyCurrentHomeVerificationEnablesConnectedAppearance() {
        val connected = chxConnectionPresentation(saved = true, homeVerified = true)
        assertEquals("已连接", connected.title)
        assertTrue(connected.verified)
        assertFalse(chxConnectionPresentation(saved = true, homeVerified = true, checking = true).verified)
        assertFalse(chxConnectionPresentation(saved = true, homeVerified = false).verified)
    }

    @Test fun laterAuthenticationFailureOverridesPreviouslyVerifiedHome() {
        val result = chxConnectionPresentation(saved = true, homeVerified = true, authRequired = true)
        assertEquals("需要重新连接", result.title)
        assertFalse(result.verified)
    }
}
