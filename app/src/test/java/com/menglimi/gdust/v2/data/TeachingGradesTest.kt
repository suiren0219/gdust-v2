package com.menglimi.gdust.v2.data

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TeachingGradesTest {
    @Test
    fun nonNumericResultsAndOriginalDecimalStringsArePreserved() {
        val item = gradeFixture().apply {
            addProperty("cj", "合格")
            addProperty("xf", "2.00")
            addProperty("jd", "")
            addProperty("xfjd", "—")
        }
        val grade = parseGradePage(gradePageFixture(listOf(item))).entries.single()
        assertEquals("合格", grade.score)
        assertEquals("2.00", grade.credits)
        assertEquals("", grade.gradePoint)
        assertEquals("—", grade.creditGradePoint)
        assertNull(grade.courseCategory)
    }

    @Test
    fun missingRequiredFieldsFailInsteadOfDroppingTheRecord() {
        // 课程名与成绩是识别一条成绩的最低要求；缺失必须报错，不能静默丢记录。
        for (field in listOf("kcmc", "cj")) {
            val item = gradeFixture().apply { remove(field) }
            val error = assertThrows(GradeLoadException::class.java) {
                parseGradePage(gradePageFixture(listOf(gradeFixture("valid"), item)))
            }
            assertEquals(GradeFailure.STRUCTURE, error.reason)
        }
    }

    @Test
    fun missingOptionalFieldsKeepTheRecordWithBlankValues() {
        // 教师、学分等字段个别缺失（不同学期/记录）不应让整页「格式无法识别」。
        val item = gradeFixture().apply { remove("jsxm"); remove("xf"); remove("kkbmmc") }
        val grade = parseGradePage(gradePageFixture(listOf(item))).entries.single()
        assertEquals("", grade.teacher.orEmpty())
        assertEquals("", grade.credits)
        assertEquals("", grade.department)
        assertEquals("测试课程 C001", grade.courseName)
    }

    @Test
    fun wrongFieldTypesFailAndOptionalNullIsAllowed() {
        val number = gradeFixture().apply { addProperty("cj", 90) }
        assertThrows(GradeLoadException::class.java) { parseGradePage(gradePageFixture(listOf(number))) }
        val optional = gradeFixture().apply {
            add("kclbmc", null)
            addProperty("kcgsmc", "通识")
            addProperty("khfsmc", "考查")
            addProperty("jxbmc", "测试教学班")
            addProperty("jsxm", "测试教师")
            addProperty("sfxwkc", "否")
            addProperty("kcbj", "辅修")
        }
        val result = parseGradePage(gradePageFixture(listOf(optional))).entries.single()
        assertNull(result.courseCategory)
        assertEquals("通识", result.courseAttribute)
        assertEquals("考查", result.assessmentMethod)
        assertEquals("测试教学班", result.teachingClass)
        assertEquals("测试教师", result.teacher)
        assertEquals("否", result.degreeCourse)
        assertEquals("辅修", result.courseMark)
    }

    @Test
    fun unrelatedIdentityAndNestedQueryModelAreNotCopiedIntoModel() {
        val item = gradeFixture().apply {
            addProperty("xh", "fixture-student")
            addProperty("xm", "fixture-name")
            add("userModel", JsonObject().apply { addProperty("account", "fixture-account") })
            add("queryModel", JsonObject().apply {
                addProperty("currentPage", 88)
                addProperty("totalPage", 999)
                addProperty("totalResult", 999)
            })
        }
        val page = parseGradePage(gradePageFixture(listOf(item)))
        assertEquals(1, page.currentPage)
        assertEquals(1, page.totalPages)
        assertEquals(1, page.total)
        val serialized = Gson().toJson(page.entries)
        assertFalse(serialized.contains("fixture-student"))
        assertFalse(serialized.contains("fixture-name"))
        assertFalse(serialized.contains("fixture-account"))
        assertFalse(serialized.contains("userModel"))
    }

    @Test
    fun topLevelPaginationIsRequiredEvenWhenAnItemContainsQueryModel() {
        val item = gradeFixture().apply {
            add("queryModel", JsonObject().apply {
                addProperty("currentPage", 1)
                addProperty("totalPage", 1)
                addProperty("totalResult", 1)
            })
        }
        assertThrows(GradeLoadException::class.java) {
            parseGradePage(JsonObject().apply { add("items", JsonArray().apply { add(item) }) }.toString())
        }
        for (field in listOf("currentPage", "totalPage", "totalResult")) {
            val root = com.google.gson.JsonParser.parseString(gradePageFixture(listOf(item))).asJsonObject
            root.addProperty(field, "1.5")
            assertThrows(GradeLoadException::class.java) { parseGradePage(root.toString()) }
        }
    }

    @Test
    fun explicitEmptyItemsParseButUnknownOrErrorStructuresDoNot() {
        val empty = parseGradePage(gradePageFixture(emptyList(), total = 0, totalPages = 0))
        assertTrue(empty.entries.isEmpty())
        assertEquals(0, empty.total)
        for (body in listOf(
            "{}", "[]", "null", "not-json", "<html>维护中</html>",
            """{"items":null,"currentPage":1,"totalPage":0,"totalResult":0}""",
            """{"success":false,"items":[],"currentPage":1,"totalPage":0,"totalResult":0}""",
            """{"code":500,"items":[],"currentPage":1,"totalPage":0,"totalResult":0}""",
            """{"items":[],"currentPage":1,"totalPage":0,"totalResult":0} trailing-private-data""",
        )) {
            val error = assertThrows(GradeLoadException::class.java) { parseGradePage(body) }
            assertNull(error.cause)
            assertFalse(error.message.orEmpty().contains("private-data"))
        }
    }

    @Test
    fun partialReportCannotBeConstructedAsComplete() {
        assertThrows(IllegalArgumentException::class.java) { GradeReport(emptyList(), 1) }
        assertThrows(IllegalArgumentException::class.java) { GradeReport(emptyList(), -1) }
        assertEquals(0, GradeReport(emptyList(), 0).total)
    }

    @Test
    fun pkeyIsReadAtRuntimeIncludingEmptyAndEscapedValues() {
        assertEquals("", parseGradeForm(gradeFormFixture("")).pkey)
        val form = parseGradeForm(gradeFormFixture("fresh&amp;&quot;+"))
        assertEquals("fresh&\"+", form.pkey)
        assertEquals(setOf("2026"), form.years)
        assertEquals(setOf("3", "12"), form.semesters)
        for (html in listOf(
            gradeFormFixture("").replace("<input id='pkey' value=''>", ""),
            gradeFormFixture("").replace("id='pkey' value=''", "id='pkey'"),
            gradeFormFixture("").replace("id='xnm'", "id='other'"),
            gradeFormFixture("").replace("value='2026'", "disabled value='2026'"),
            "<html><body>暂不可用</body></html>",
        )) {
            assertThrows(GradeLoadException::class.java) { parseGradeForm(html) }
        }
    }
}

internal fun gradeFixture(code: String = "C001", year: String = "2026", semester: String = "3") = JsonObject().apply {
    for ((key, value) in mapOf(
        "xnm" to year, "xqm" to semester, "xnmmc" to "$year-${year.toInt() + 1}", "xqmmc" to "第一学期",
        "kch" to code, "kcmc" to "测试课程 $code", "kcxzmc" to "必修", "cj" to "缓考",
        "xf" to "2.0", "jd" to "", "xfjd" to "", "ksxz" to "正常考试", "kkbmmc" to "测试学院",
    )) addProperty(key, value)
}

internal fun gradePageFixture(
    items: List<JsonObject>, currentPage: Int = 1, total: Int = items.size,
    totalPages: Int = if (total == 0) 0 else (total + GRADE_PAGE_SIZE - 1) / GRADE_PAGE_SIZE,
): String = JsonObject().apply {
    add("items", JsonArray().apply { items.forEach { add(it) } })
    addProperty("currentPage", currentPage)
    addProperty("totalPage", totalPages)
    addProperty("totalResult", total)
}.toString()

internal fun gradeFormFixture(pkey: String = "") = """
    <html><body><input id='pkey' value='$pkey'>
    <select id='xnm'><option value=''>全部</option><option value='2026' selected>2026–2027</option></select>
    <select id='xqm'><option value='3' selected>第一学期</option><option value='12'>第二学期</option></select>
    </body></html>
""".trimIndent()
