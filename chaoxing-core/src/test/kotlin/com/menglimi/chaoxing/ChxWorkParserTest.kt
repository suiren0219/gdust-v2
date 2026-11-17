package com.menglimi.chaoxing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 学习通作业页解析：既有样本沿用仓库的脱敏结构，新增 DOM 变体为明确标注的合成 fixture。
 * 不读取真实 HAR 或账号数据，也不以合成测试声称 issue #6 的真实页面已回归。
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

    @Test fun hiddenFieldsSupportAttributeOrderAndSingleQuotesButRejectDuplicatesAndTemplates() {
        val reordered = tabsHtml.replace(Regex("id=\"([^\"]+)\" value=\"([^\"]*)\"")) {
            "value='${it.groupValues[2]}' id='${it.groupValues[1]}'"
        }
        assertEquals(ChxWorkParser.parseCourseTabs(tabsHtml), ChxWorkParser.parseCourseTabs(reordered))
        assertNull(ChxWorkParser.parseCourseTabs("<script>const example = `$tabsHtml`;</script>"))
        assertNull(ChxWorkParser.parseCourseTabs(tabsHtml + "<input type='hidden' id='enc' value='duplicate'>"))
        assertNull(ChxWorkParser.parseCourseTabs(tabsHtml.replace("id=\"t\"", "id=\"other\"")))
        assertNull(ChxWorkParser.parseCourseTabs(tabsHtml.replace("id=\"moocDomainName\"", "id=\"other\"")))
    }

    @Test fun actualLowercasePageAllowsRepeatedIdenticalCourseIdentity() {
        val html = javaClass.getResource("/chaoxing/course-tabs-lowercase.html")!!.readText()
        val course = ChxWorkCourse(201, 101, 301, "示例课程", "")
        val tabs = ChxWorkParser.readCourseTabs(html, course)
        assertEquals("synthetic-stu", tabs.enc)
        assertEquals("synthetic-work", tabs.workEnc)
        assertEquals("1700000000000", tabs.t)
        assertEquals("https://mooc1.chaoxing.com", tabs.moocDomain)
        assertNull(ChxWorkParser.parseCourseTabs(html.replaceFirst("id=\"clazzid\" value=\"101\"", "id=\"clazzid\" value=\"102\""), course))
        assertNull(ChxWorkParser.parseCourseTabs(html, course.copy(cpi = 999)))
    }

    @Test fun courseHiddenInputsSupportNamesAndOmittedTypeWithoutTrustingScriptsOrTemplates() {
        val course = ChxWorkCourse(111111111, 222222222, 333333333, "示例", "")
        val named = tabsHtml.replace("type=\"hidden\" ", "").replace("id=", "name=")
        assertEquals(ChxWorkParser.readCourseTabs(tabsHtml, course), ChxWorkParser.readCourseTabs(named, course))
        val malformed = named + "<input name='courseId' value='999'>"
        assertThrows(ChxCourseReadException::class.java) { ChxWorkParser.readCourseTabs(malformed, course) }
        assertThrows(ChxCourseReadException::class.java) { ChxWorkParser.readCourseTabs("<template>$named</template>", course) }
    }

    @Test fun landingIdentityRejectsDuplicatesAndCannotInventMissingSignatureFields() {
        val course = ChxWorkCourse(111111111, 222222222, 333333333, "示例", "")
        val page = "https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/stu?courseid=111111111&clazzid=222222222&cpi=333333333"
        val noIds = tabsHtml.replace(Regex("<input[^>]+id=\"(?:courseId|classId|cpi)\"[^>]*>"), "")
        assertEquals(ChxWorkParser.readCourseTabs(tabsHtml, course), ChxWorkParser.readCourseTabs(noIds, course, page))
        for (url in listOf(page + "&courseid=111111111", page.replace("courseid=111111111", "courseid=999"),
            page.replace("mooc2-ans.chaoxing.com", "unknown.example"))) {
            assertThrows(ChxCourseReadException::class.java) { ChxWorkParser.readCourseTabs(noIds, course, url) }
        }
        assertThrows(ChxCourseReadException::class.java) {
            ChxWorkParser.readCourseTabs(noIds.replace("id=\"workEnc\"", "id=\"other\""), course, page)
        }
    }

    @Test fun missingAndConflictingFieldsAreNamedWithoutPrintingTheirValues() {
        val html = javaClass.getResource("/chaoxing/course-tabs-lowercase.html")!!.readText()
        val course = ChxWorkCourse(201, 101, 301, "示例课程", "")
        val missing = assertThrows(ChxCourseReadException::class.java) {
            ChxWorkParser.readCourseTabs(html.replace("id=\"workEnc\"", "id=\"other\"")
                .replace("name=\"workEnc\"", "name=\"other\""), course)
        }
        assertTrue(missing.message.orEmpty().contains("workEnc"))
        assertTrue(!missing.message.orEmpty().contains("synthetic"))
        val conflict = assertThrows(ChxCourseReadException::class.java) {
            ChxWorkParser.readCourseTabs(html + "<input type='hidden' id='courseId' value='999'>", course)
        }
        assertTrue(conflict.message.orEmpty().contains("courseid"))
        assertTrue(!conflict.message.orEmpty().contains("999"))
    }

    @Test fun unknownOrMalformedCourseListsDoNotBecomeAnEmptySuccess() {
        listOf("", "<html>维护</html>", "<div id='courseList'>暂无课程</div>",
            coursesHtml.replace("value=\"333333333\"", "value=\"0\""),
            coursesHtml.replace("cpi=333333333", "cpi=999999999"),
            coursesHtml.replace("https://mooc1.chaoxing.com/", "https://example.org/"),
        ).forEach {
            assertThrows(ChxCourseReadException::class.java) { ChxWorkParser.parseCourses(it) }
        }
        val malformed = "<div class='course learnCourse'><input type='hidden' class='courseId' value='999'/></div>"
        assertThrows(ChxCourseReadException::class.java) {
            ChxWorkParser.parseCourses(coursesHtml.replace("<div id=\"stuNormalCourseListDiv\">",
                "<div id=\"stuNormalCourseListDiv\">$malformed"))
        }
    }

    @Test fun unknownOrIncompleteWorkPagesNeverClaimNoHomework() {
        listOf("", "<html>维护</html>", "<title>作业列表</title>",
            "<title>作业列表</title><div class='main task-list'><div class='bottomList' aria-label='作业列表'><ul></ul></div></div>",
            "<script>const example = `$workListHtml`;</script>",
            workListHtml.replace("workId=57711010", "workId=0"),
            workListHtml.replace("<p class=\"status fl\">未交</p>", "<p class=\"status fl\">未知状态</p>"),
            workListHtml.replace("https://mooc1.chaoxing.com/", "https://unknown.example/"),
        ).forEach {
            assertThrows(ChxCourseReadException::class.java) { ChxWorkParser.parseWorkList(it) }
        }
        val wrongCourse = ChxWorkCourse(1, 2, 3, "示例", "")
        assertThrows(ChxCourseReadException::class.java) { ChxWorkParser.parseWorkList(workListHtml, wrongCourse) }
        assertNull(ChxWorkParser.parseCourseTabs(tabsHtml, wrongCourse))
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

    @Test fun semanticWorkRowsSupportDataUrlAnchorsRelativeLinksAndStatusAliases() {
        val html = javaClass.getResource("/chaoxing/work-list-variants-synthetic.html")!!.readText()
        val course = ChxWorkCourse(201, 101, 301, "合成课程", "")
        val items = ChxWorkParser.parseWorkList(html, course,
            "https://mooc1.chaoxing.com/mooc-ans/mooc2/work/list.html?enc=synthetic-page")
        assertEquals(listOf(401L, 402L), items.map { it.workId })
        assertEquals(listOf("未交", "已完成"), items.map { it.status })
        assertEquals("合成待交作业完整标题", items.first().title)
        assertEquals("剩余2小时5分钟", items.first().remainingText)
        assertEquals("", items.last().remainingText)
        assertTrue(items.first().detailUrl.startsWith("https://mooc1.chaoxing.com/mooc-ans/mooc2/work/task?"))
        assertTrue(items.last().detailUrl.startsWith("https://mooc1.chaoxing.com/mooc2/work/task?"))
    }

    @Test fun cosmeticTagAndHandlerChangesDoNotHideValidWorkRows() {
        val variants = listOf(
            workListHtml.replace("onclick=\"goTask(this);\"", "onclick=\"goTask(this, event);\""),
            workListHtml.replace("onclick=\"goTask(this);\"", "").replace("<p ", "<span ").replace("</p>", "</span>"),
            workListHtml.replace("aria-label=\"作业列表\"", "").replace("<title>作业列表</title>", "<title>课程作业 - 学习通</title>"),
        )
        val expected = ChxWorkParser.parseWorkList(workListHtml)
        variants.forEach { assertEquals(expected, ChxWorkParser.parseWorkList(it)) }
    }

    @Test fun missingConflictingAndUnsafeWorkFieldsRemainFailuresWithoutLeakingValues() {
        val html = javaClass.getResource("/chaoxing/work-list-variants-synthetic.html")!!.readText()
        val course = ChxWorkCourse(201, 101, 301, "合成课程", "")
        val page = "https://mooc1.chaoxing.com/mooc-ans/mooc2/work/list.html" +
            "?enc=private-enc&cookie=private-cookie&account=private-account"
        val invalid = listOf(
            html.replace("work-title", "unknown-title") to "标题",
            html.replace("work-status", "unknown-status") to "status",
            html.replace("未提交", "private-status") to "status",
            html.replace("workId=401", "workId=0") to "workId",
            html.replace("answerId=501", "answerId=0") to "answerId",
            html.replace("enc=synthetic-pending", "missing=private-enc") to "enc",
            html.replace("courseId=201", "courseId=999") to "courseId",
            html.replace("classId=101", "classId=999") to "classId",
            html.replace("cpi=301", "cpi=999") to "cpi",
            html.replace("workId=401", "workId=401&amp;workId=401") to "workId",
            html.replace("enc=synthetic-pending", "enc=synthetic-pending&amp;enc=private-enc") to "enc",
            html.replace("data-url=\"task?", "data-url=\"https://unknown.example/task?") to "详情地址",
            html.replace("data-url=\"task?", "data-url=\"javascript:openTask?") to "详情地址",
            html.replace("class=\"work-item\"", "class=\"work-item\" data=\"/mooc-ans/mooc2/work/task?enc=private-conflict\"") to "详情地址冲突",
            html.replace("workId=402", "workId=401") to "workId",
            html.replace("</body>", "<div id='taskList'></div></body>") to "作业容器不唯一",
        )
        invalid.forEach { (body, field) ->
            val error = assertThrows(ChxCourseReadException::class.java) { ChxWorkParser.parseWorkList(body, course, page) }
            assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(field))
            assertTrue(!error.message.orEmpty().contains("private"))
            assertTrue(!error.message.orEmpty().contains("synthetic"))
            assertTrue(!error.message.orEmpty().contains("999"))
        }
    }

    @Test fun validRowsCannotMaskIncompleteRowsLoginOrTemplateOnlyPages() {
        // HTML 注释不可嵌套，先去除 fixture 的说明注释，保证后面的整个页面确实处于注释内。
        val html = javaClass.getResource("/chaoxing/work-list-variants-synthetic.html")!!.readText()
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        // template 包裹 body fragment；完整文档的 html/head/body 标签会触发 DOM 重排。
        val fragment = org.jsoup.Jsoup.parse(html).body().html()
        val login = "<form><input type='password'><button>用户登录</button></form>"
        for ((index, body) in listOf(
            html.replace("<article class=\"task-item\">", "<article class=\"work-item\"></article><article class=\"task-item\">"),
            html.replace("<body>", "<body>$login"),
            "<template>$fragment</template>",
            "<!-- $fragment -->",
            "<div id='workList'><template>$fragment</template>暂无作业</div>",
        ).withIndex()) {
            assertThrows("invalid page variant $index", ChxCourseReadException::class.java) {
                ChxWorkParser.parseWorkList(body)
            }
        }
    }

    @Test fun statisticsAnchorsDoNotBecomeWorkDetailLinks() {
        val statistics = "<a href='https://stat2-ans.chaoxing.com/study-knowledge/ans?courseid=111111111&amp;clazzid=222222222&amp;cpi=333333333&amp;relationId=1&amp;type=work&amp;ut=s'>查看统计</a>"
        val html = workListHtml.replace("<div class=\"clear\"></div>", statistics + "<div class=\"clear\"></div>")
        assertEquals(ChxWorkParser.parseWorkList(workListHtml), ChxWorkParser.parseWorkList(html))
        for (invalid in listOf(statistics.replace("stat2-ans.chaoxing.com", "unknown.example"),
            statistics.replace("/study-knowledge/ans", "/work/task"),
            statistics.replace("relationId=1", "workId=999"))) {
            assertThrows(ChxCourseReadException::class.java) {
                ChxWorkParser.parseWorkList(workListHtml.replace("<div class=\"clear\"></div>", invalid))
            }
        }
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

    @Test fun jobInfoBatchResponseParsesPerClassCounts() {
        // 任务点统计只用于展示，不能作为作业数量或预过滤依据。
        val json = """
            {"jobArray": [
                {"jobFinishCount": 10, "clazzId": 111111112, "jobRate": 17.0, "jobCount": 58},
                {"jobFinishCount": 0, "clazzId": 111111113, "jobRate": 0.0, "jobCount": 62},
                {"jobFinishCount": 160, "clazzId": 111111114, "jobRate": 98.0, "jobCount": 162}
            ], "status": true}
        """.trimIndent()
        val info = ChxWorkParser.parseJobInfo(json)
        assertEquals(3, info.size)
        assertEquals(58 to 10, info[111111112L])
        assertEquals(62 to 0, info[111111113L])
        assertEquals(162 to 160, info[111111114L])
        // 坏数据/空数据不抛异常，返回空 map（调用方退回全量检查）
        assertTrue(ChxWorkParser.parseJobInfo("not json").isEmpty())
        assertTrue(ChxWorkParser.parseJobInfo("").isEmpty())
    }

    @Test fun deadlineParsesFromDetailPage() {
        // 实测结构：<div class="infoHead"><p>Time:<em>10-28 15:54</em>to<em>11-22 23:59</em></p>
        val html = """<div class="infoHead"><p>Time:<em>10-28 15:54</em>to<em>11-22 23:59</em></p></div>"""
        val now = java.time.LocalDateTime.of(2026, 11, 13, 9, 0)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val due = ChxWorkParser.parseDeadlineMillis(html, now)
        val expect = java.time.LocalDateTime.of(2026, 11, 22, 23, 59)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expect, due)
    }

    @Test fun deadlineYearInferenceHandlesYearBoundary() {
        val zone = java.time.ZoneId.systemDefault()
        // 现在是 12 月底，截止写 01-05 → 应该是明年
        val nowDec = java.time.LocalDateTime.of(2026, 12, 28, 10, 0).atZone(zone).toInstant().toEpochMilli()
        val jan = ChxWorkParser.parseMonthDayTime("01-05 23:59", nowDec)
        assertEquals(2027, java.time.Instant.ofEpochMilli(jan).atZone(zone).year)
        // 现在是 1 月初，截止写 12-30 → 应该是去年
        val nowJan = java.time.LocalDateTime.of(2027, 1, 3, 10, 0).atZone(zone).toInstant().toEpochMilli()
        val dec = ChxWorkParser.parseMonthDayTime("12-30 23:59", nowJan)
        assertEquals(2026, java.time.Instant.ofEpochMilli(dec).atZone(zone).year)
        // 完整年份格式直接采用
        val full = ChxWorkParser.parseMonthDayTime("2027-03-01 08:00", nowJan)
        assertEquals(2027, java.time.Instant.ofEpochMilli(full).atZone(zone).year)
        // 解析不了 → 0
        assertEquals(0L, ChxWorkParser.parseDeadlineMillis("<p>没有时间</p>", nowJan))
    }
}
