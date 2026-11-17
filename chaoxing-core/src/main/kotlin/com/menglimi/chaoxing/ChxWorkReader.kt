package com.menglimi.chaoxing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.coroutineContext

/**
 * 由接入方实现的并发安全缓存；键须包含 courseId/classId/cpi，且按账号会话隔离。
 * 有效期、退出清理及持久化由接入方负责；不提供缓存时每次重新读取课程页。
 */
interface ChxWorkTabsCache {
    suspend fun get(course: ChxWorkCourse): ChxWorkTabs?
    suspend fun put(course: ChxWorkCourse, tabs: ChxWorkTabs)
}

/** 一条作业及其可选的绝对截止时间；0 表示没有解析到截止时间。 */
data class ChxWorkWithDeadline(val item: ChxWorkItem, val dueAt: Long)

/** 每门课程独立报告，单课结构异常不会丢弃其他课程的成功结果。 */
sealed interface ChxCourseWorkResult {
    val course: ChxWorkCourse

    data class Success(
        override val course: ChxWorkCourse,
        val items: List<ChxWorkWithDeadline>,
    ) : ChxCourseWorkResult

    data class Failure(
        override val course: ChxWorkCourse,
        val error: Exception,
    ) : ChxCourseWorkResult
}

/** 课程作业读取编排；不合并待办，也不保存任何 app 状态。 */
class ChxWorkReader(
    private val api: ChxApi,
    private val tabsCache: ChxWorkTabsCache? = null,
    parallelism: Int = 3,
    private val checkSession: () -> Unit = {},
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val semaphore = Semaphore(parallelism.also { require(it > 0) })

    suspend fun read(): List<ChxCourseWorkResult> {
        coroutineContext.ensureActive()
        checkSession()
        return read(api.courses())
    }

    suspend fun read(courses: List<ChxWorkCourse>): List<ChxCourseWorkResult> = coroutineScope {
        coroutineContext.ensureActive()
        checkSession()
        courses.map { course ->
            async {
                semaphore.withPermit {
                    readOne(course)
                }
            }
        }.awaitAll().also {
            coroutineContext.ensureActive()
            checkSession()
        }
    }

    private suspend fun readOne(course: ChxWorkCourse): ChxCourseWorkResult {
        return try {
            coroutineContext.ensureActive()
            checkSession()
            val tabs = tabsCache?.get(course) ?: api.courseTabs(course).also { loaded ->
                coroutineContext.ensureActive()
                checkSession()
                tabsCache?.put(course, loaded)
            }
            coroutineContext.ensureActive()
            checkSession()
            val items = api.workList(course, tabs)
            val now = nowMillis()
            val detailed = items.map { item ->
                coroutineContext.ensureActive()
                checkSession()
                val absoluteDue = if (item.status != "未交" || item.detailUrl.isBlank()) {
                    0L
                } else {
                    try {
                        ChxWorkParser.parseDeadlineMillis(api.workDetailHtml(item.detailUrl), now)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (auth: ChxAuthFailed) {
                        // 详情页认证失败说明这门课程的读取结果不完整，整课报告失败。
                        throw auth
                    } catch (_: Exception) {
                        0L
                    }
                }
                val remaining = ChxWorkParser.remainingMillis(item.remainingText)
                val dueAt = when {
                    item.status != "未交" -> 0L
                    absoluteDue > 0L -> absoluteDue
                    remaining > 0L -> now + remaining
                    else -> 0L
                }
                ChxWorkWithDeadline(item, dueAt)
            }
            coroutineContext.ensureActive()
            checkSession()
            ChxCourseWorkResult.Success(course, detailed)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            coroutineContext.ensureActive()
            checkSession()
            ChxCourseWorkResult.Failure(course, failure)
        }
    }
}
