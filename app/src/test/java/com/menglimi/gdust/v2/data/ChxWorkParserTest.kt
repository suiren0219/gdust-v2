package com.menglimi.gdust.v2.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 学习通作业页解析（fixture 改编自真实抓包，密钥已脱敏）。
 * 结构约定见 docs/EXTENDING.md §3.4 与 ChxWorkParser 注释。
 */
class ChxWorkParserTest {

    private val tabsHtml = """
        <html><body>
        <input type="hidden" id="courseId" value="111111111"/>
        <input type="hidden" id="classId" value="222222222"/>
        <input type="hidden" id="cpi" value="333333333"/>
        <input type="hidden" id="enc" value="aaaa1111bbbb2222cccc3333dddd4444"/>
        <input type="hidden" id="oldenc" value="eeee5555ffff6666aaaa7777bbbb8888"/>
        <input type="hidden" id="userId" value="419500000"/>
        <input type="hidden" id="moocDomainName" value="https://mooc1.chaoxing.com"/>
        <input type="hidden" id="workEnc" value="cccc9999dddd8888eeee7777ffff6666"/>
        <input type="hidden" id="examEnc" value="dddd0000eeee1111ffff2222aaaa3333"/>
        <input type="hidden" id="t" value="1794503665207"/>
        </body></html>
    """.trimIndent()

    private val workListHtml = """
        <html><head><title>作业列表</title></head><body>
        <div class="main task-list"><h2>25集成+通信本科1班</h2>
        <div class="work-progress-box"><span>1/2</span></div>
        <div class="bottomList" aria-label="作业列表"><ul>
        <li onclick="goTask(this);" data="https://mooc1.chaoxing.com/mooc-ans/mooc2/work/task?courseId=111111111&classId=222222222&cpi=333333333&workId=57711010&answerId=56734583&enc=cccccccc111122223333444455556666" aria-label="课程小论文写作 ; 未交">
            <div class="tag work-icon icon-zy"></div>
            <div class="right-content">
                <p class="overHidden2 fl">《测试课程》课程小论文写作</p>
                <div class="clear"></div>
                <p class="status fl">未交</p>
            </div>
            <div class="time notOver"><img src="//mooc1.chaoxing.com/mooc-ans/mooc2/images/endTime.png" />剩余1125小时55分钟</div>
        </li>
        <li onclick="goTask(this);" data="https://mooc1.chaoxing.com/mooc-ans/mooc2/work/task?courseId=111111111&classId=222222222&cpi=333333333&workId=57726171&answerId=56734582&enc=dddddddd111122223333444455556666" aria-label="第一次作业 ; 已完成">
            <div class="tag work-icon icon-zy"></div>
            <div class="right-content">
                <p class="overHidden2 fl">第一次作业——测试章节</p>
                <div class="clear"></div>
                <p class="status fl">已完成</p>
            </div>
        </li>
        </ul></div></div></body></html>
    """.trimIndent()

    private val coursesHtml = """
        <html><body>
        <div id="stuTopCourseListDiv">
            <!-- 置顶区：同一门课会出现两次，按 courseid+classId 去重 -->
            <div class="course clearfix learnCourse" id="c_111111111">
                <input type="hidden" class="clazzId" name="clazzId" value="222222222"/>
                <input type="hidden" class="courseId" name="courseId" value="111111111"/>
                <input type="hidden" class="curPersonId" value="333333333"/>
                <a href="https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=111111111&clazzid=222222222&cpi=333333333&ismooc2=1&v=2"><img src="cover.png"></a>
                <h3><a class="color1" href="https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=111111111&clazzid=222222222&cpi=333333333&ismooc2=1&v=2">
                <span class="course-name overHidden2" title="测试课程名">测试课程名</span></a></h3>
            </div>
        </div>
        <div id="stuNormalCourseListDiv">
            <div class="course clearfix learnCourse" id="c_111111111">
                <input type="hidden" class="clazzId" name="clazzId" value="222222222"/>
                <input type="hidden" class="courseId" name="courseId" value="111111111"/>
                <input type="hidden" class="curPersonId" value="333333333"/>
                <a href="https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=111111111&clazzid=222222222&cpi=333333333&ismooc2=1&v=2"><img src="cover.png"></a>
                <h3><a class="color1" href="https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=111111111&clazzid=222222222&cpi=333333333&ismooc2=1&v=2">
                <span class="course-name overHidden2" title="测试课程名">测试课程名</span></a></h3>
            </div>
            <div class="course clearfix learnCourse" id="c_999999999">
                <input type="hidden" class="clazzId" name="clazzId" value="888888888"/>
                <input type="hidden" class="courseId" value="999999999"/>
                <!-- 无课程名的异常卡：跳过 -->
            </div>
        </div>
        </body></html>
    """.trimIndent()

    @Test fun tabsParseExtractsAllSecretsAndParams() {
        val tabs = ChxWorkParser.parseCourseTabs(tabsHtml)!!
        assertEquals("aaaa1111bbbb2222cccc3333dddd4444", tabs.enc)
        assertEquals("cccc9999dddd8888eeee7777ffff6666", tabs.workEnc)
        assertEquals("dddd0000eeee1111ffff2222aaaa3333", tabs.examEnc)
        assertEquals("1794503665207", tabs.t)
        assertEquals("419500000", tabs.userId)
        assertEquals("https://mooc1.chaoxing.com", tabs.moocDomain)
    }

    @Test fun tabsParseReturnsNullWhenStructureChanges() {
        assertNull(ChxWorkParser.parseCourseTabs("<html><body>没有隐藏域</body></html>"))
    }

    @Test fun workListParsesItemsWithStates() {
        val items = ChxWorkParser.parseWorkList(workListHtml)
        assertEquals(2, items.size)
        // 未交：带详情地址三件套与相对剩余时间
        val pending = items[0]
        assertEquals("《测试课程》课程小论文写作", pending.title)
        assertEquals("未交", pending.status)
        assertEquals(57711010L, pending.workId)
        assertEquals(56734583L, pending.answerId)
        assertEquals("cccccccc111122223333444455556666", pending.enc)
        assertTrue(pending.remainingText.contains("剩余1125小时55分钟"))
        assertTrue(pending.detailUrl.contains("/mooc2/work/task?"))
        // 已完成：无剩余时间块
        val done = items[1]
        assertEquals("已完成", done.status)
        assertEquals(57726171L, done.workId)
        assertEquals("", done.remainingText)
    }

    @Test fun remainingTimeCanBeConvertedToMillis() {
        assertEquals((1125 * 3600 + 55 * 60) * 1000L, ChxWorkParser.remainingMillis("剩余1125小时55分钟"))
        assertEquals((2 * 24 * 3600 + 3 * 3600) * 1000L, ChxWorkParser.remainingMillis("剩余2天3小时"))
        assertEquals(5 * 60_000L, ChxWorkParser.remainingMillis("剩余5分钟"))
        // 无「剩余」字样或空串 → 0（无截止信息）
        assertEquals(0L, ChxWorkParser.remainingMillis("已完成"))
        assertEquals(0L, ChxWorkParser.remainingMillis(""))
    }

    @Test fun courseListParsesCourseIdsAndName() {
        val courses = ChxWorkParser.parseCourses(coursesHtml)
        assertEquals(1, courses.size)
        val c = courses[0]
        assertEquals(111111111L, c.courseId)
        assertEquals(222222222L, c.classId)
        assertEquals(333333333L, c.cpi)
        assertEquals("测试课程名", c.name)
    }

    @Test fun oldTodoJsonWithoutDueFieldStaysCompatible() {
        // 旧版本待办没有 due 字段：反序列化后 due=0，不丢数据不崩
        val t = Gson().fromJson<Store.Todo>(
            """{"id":123,"text":"手动待办","done":false}""",
            object : TypeToken<Store.Todo>() {}.type,
        )
        assertEquals(123L, t.id)
        assertEquals("手动待办", t.text)
        assertEquals(false, t.done)
        assertEquals(0L, t.due)
    }
}
