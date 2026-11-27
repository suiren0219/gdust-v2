package com.menglimi.gdust.v2.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataSnapshotsTest {
    @Test fun gradesRoundTripKeepsCompleteReportAndSuccessfulUpdateTime() {
        val snapshot = DataSnapshot(gradeReport(), UPDATED_AT)

        val restored = DataSnapshots.grades(DataSnapshots.encode(snapshot), YEAR, SEMESTER)

        assertEquals(snapshot, restored)
        assertEquals(UPDATED_AT, requireNotNull(restored).updatedAt)
        assertEquals(listOf("缓考", "优秀"), restored.data.entries.map { it.score })
    }

    @Test fun gradesRejectMismatchedTermsAndTamperedIncompleteReports() {
        val json = DataSnapshots.encode(DataSnapshot(gradeReport(), UPDATED_AT))
        assertNull(DataSnapshots.grades(json, "2025", SEMESTER))
        assertNull(DataSnapshots.grades(json, YEAR, "12"))

        listOf<Pair<String, JsonObject.() -> Unit>>(
            "mixed year" to { addProperty("year", "2025") },
            "mixed semester" to { addProperty("semester", "12") },
            "blank course name" to { addProperty("courseName", " ") },
            "missing course name" to { remove("courseName"); Unit },
        ).forEach { (reason, mutate) ->
            val root = JsonParser.parseString(json).asJsonObject
            root.getAsJsonObject("data").getAsJsonArray("entries")[1].asJsonObject.apply(mutate)
            assertNull(reason, DataSnapshots.grades(root.toString(), YEAR, SEMESTER))
        }
        listOf(-1, 0, 1, 3).forEach { total ->
            val root = JsonParser.parseString(json).asJsonObject
            root.getAsJsonObject("data").addProperty("total", total)
            assertNull("tampered total=$total", DataSnapshots.grades(root.toString(), YEAR, SEMESTER))
        }
    }

    @Test fun emptyGradesAndNoticesAreValidSnapshots() {
        val grades = DataSnapshot(GradeReport(emptyList(), 0), UPDATED_AT)
        val notices = DataSnapshot<List<Notice>>(emptyList(), UPDATED_AT)

        assertEquals(grades, DataSnapshots.grades(DataSnapshots.encode(grades), YEAR, SEMESTER))
        assertEquals(notices, DataSnapshots.notices(DataSnapshots.encode(notices)))
    }

    @Test fun corruptMissingDataAndInvalidUpdateTimesAreRejected() {
        val decoders: List<(String?) -> Any?> = listOf(
            { DataSnapshots.grades(it, YEAR, SEMESTER) },
            { DataSnapshots.courses(it) },
            { DataSnapshots.notices(it) },
        )
        listOf<String?>(
            null, "", "{", "null", "[]", "{}",
            """{"updatedAt":$UPDATED_AT}""",
            """{"data":null,"updatedAt":$UPDATED_AT}""",
        ).forEach { json ->
            decoders.forEach { decode -> assertNull("corrupt envelope: $json", decode(json)) }
        }
        val snapshots = listOf(
            DataSnapshots.encode(DataSnapshot(gradeReport(), UPDATED_AT)),
            DataSnapshots.encode(DataSnapshot(courseOverview(), UPDATED_AT)),
            DataSnapshots.encode(DataSnapshot<List<Notice>>(emptyList(), UPDATED_AT)),
        )
        snapshots.zip(decoders).forEach { (json, decode) ->
            listOf<Pair<String, JsonObject.() -> Unit>>(
                "missing timestamp" to { remove("updatedAt"); Unit },
                "zero timestamp" to { addProperty("updatedAt", 0L) },
                "negative timestamp" to { addProperty("updatedAt", -1L) },
                "missing data" to { remove("data"); Unit },
            ).forEach { (reason, mutate) ->
                val root = JsonParser.parseString(json).asJsonObject.apply(mutate)
                assertNull(reason, decode(root.toString()))
            }
        }
        val grades = JsonParser.parseString(snapshots[0]).asJsonObject
        grades.getAsJsonObject("data").remove("entries")
        assertNull(DataSnapshots.grades(grades.toString(), YEAR, SEMESTER))
        val courses = JsonParser.parseString(snapshots[1]).asJsonObject
        courses.getAsJsonObject("data").remove("courses")
        assertNull(DataSnapshots.courses(courses.toString()))
    }

    @Test fun coursesRoundTripKeepsTrustedIdentityAndOptionalProgress() {
        val overview = courseOverview()
        listOf(overview, overview.copy(courses = overview.courses.map { it.copy(progress = null) }))
            .forEach { data ->
                val snapshot = DataSnapshot(data, UPDATED_AT)
                val restored = DataSnapshots.courses(DataSnapshots.encode(snapshot))
                assertEquals(snapshot, restored)
                assertEquals(UPDATED_AT, requireNotNull(restored).updatedAt)
                assertEquals("101_301", restored.data.courses.single().info)
            }
    }

    @Test fun coursesRejectUntrustedIdentityUrlsAndIllegalProgress() {
        val json = DataSnapshots.encode(DataSnapshot(courseOverview(), UPDATED_AT))
        listOf<Pair<String, String>>(
            "name" to " ",
            "clazzId" to "",
            "clazzId" to "102",
            "courseId" to "0",
            "courseId" to "202",
            "curPersonId" to "not-an-id",
            "curPersonId" to "302",
            "url" to COURSE_URL.replace("https://", "http://"),
            "url" to COURSE_URL.replace("mooc1.chaoxing.com", "example.invalid"),
            "url" to "$COURSE_URL&courseid=999",
        ).forEach { (field, value) ->
            val root = JsonParser.parseString(json).asJsonObject
            root.getAsJsonObject("data").getAsJsonArray("courses")[0].asJsonObject
                .addProperty(field, value)
            assertNull("untrusted $field=$value", DataSnapshots.courses(root.toString()))
        }
        listOf<Pair<String, Number>>(
            "jobCount" to -1,
            "jobFinishCount" to -1,
            "jobFinishCount" to 5,
            "jobRate" to -0.1,
            "jobRate" to 100.1,
        ).forEach { (field, value) ->
            val root = JsonParser.parseString(json).asJsonObject
            root.getAsJsonObject("data").getAsJsonArray("courses")[0].asJsonObject
                .getAsJsonObject("progress").addProperty(field, value)
            assertNull("illegal progress $field=$value", DataSnapshots.courses(root.toString()))
        }
        val empty = courseOverview().copy(courses = emptyList())
        assertNull(DataSnapshots.courses(DataSnapshots.encode(DataSnapshot(empty, UPDATED_AT))))
    }

    @Test fun progressFailureRetainsMatchingHistoryWithoutHidingCurrentAuthenticationFailure() {
        val previous = courseOverview().copy(courses = courseOverview().courses.map {
            it.copy(progress = ChxCourseProgress(4, 4, 100.0))
        })
        val current = previous.copy(courses = previous.courses.map { it.copy(progress = null) },
            loadedAt = UPDATED_AT, progressError = "学习通会话已失效", progressAuthRequired = true)

        val merged = DataSnapshots.mergeCourseProgress(previous, current)

        assertEquals(previous.courses.single().progress, merged.courses.single().progress)
        assertEquals(true, merged.progressAuthRequired)
        assertEquals(true, requireNotNull(merged.progressError).contains("会话已失效"))
        assertEquals(true, requireNotNull(merged.progressError).contains("上次成功结果"))
        assertEquals(UPDATED_AT, merged.loadedAt)
        assertEquals(merged, DataSnapshots.courses(DataSnapshots.encode(DataSnapshot(merged, UPDATED_AT)))?.data)
    }

    @Test fun progressMergeDoesNotResurrectRemovedCoursesOrOverwriteNewProgressOrCrossIdentity() {
        val previous = courseOverview()
        val old = previous.courses.single()
        val fresh = old.copy(name = "新标题", progress = ChxCourseProgress(4, 2, 50.0))
        val otherCourse = old.copy(courseId = "202", progress = null)
        val otherClass = old.copy(clazzId = "102", progress = null)
        val otherPerson = old.copy(curPersonId = "302", progress = null)
        val current = previous.copy(courses = listOf(fresh, otherCourse, otherClass, otherPerson))

        assertEquals(current, DataSnapshots.mergeCourseProgress(previous, current))
        assertEquals(current, DataSnapshots.mergeCourseProgress(null, current))
        assertEquals(emptyList<ChxCourse>(), DataSnapshots.mergeCourseProgress(previous,
            current.copy(courses = emptyList())).courses)
    }

    @Test fun snapshotOwnerSurvivesCookieLossButNeverFallsBackAcrossAnExplicitNewUid() {
        assertEquals("101", DataSnapshots.courseSnapshotOwner(null, null, "101"))
        assertEquals("101", DataSnapshots.courseSnapshotOwner("", "expired=yes", "101"))
        assertEquals("202", DataSnapshots.courseSnapshotOwner("_uid=202", "_uid=101", "101"))
        assertEquals("202", DataSnapshots.courseSnapshotOwner(null, "UID=202", "101"))
        assertNull(DataSnapshots.courseSnapshotOwner(null, null, null))
        assertNull(DataSnapshots.cookieOwner("_uid=101; UID=202"))
    }

    @Test fun targetSemesterAndManualCalendarOverridesAreAppliedWithoutMutatingOriginal() {
        val raw = CalendarInfo(3, 20, "2026-08-31", "2026", "3")
        assertEquals(CalendarInfo(8, 18, "2027-02-22", "2026", "12"),
            DataSnapshots.calendarForSync(raw, "12", "2027-02-22", 18, 8))
        assertEquals(raw, DataSnapshots.calendarForSync(raw, null, "", 0, 0))
        assertEquals("3", raw.semester)
    }

    @Test fun powerRoomSnapshotsRoundTripAndRejectIncompleteRooms() {
        val rooms = DataSnapshot(listOf(PowerRoom("合成宿舍", "synthetic-room-id")), UPDATED_AT)
        assertEquals(rooms, DataSnapshots.powerRooms(DataSnapshots.encode(rooms)))
        assertNull(DataSnapshots.powerRooms(DataSnapshots.encode(DataSnapshot(listOf(PowerRoom("", "id")), UPDATED_AT))))
        assertNull(DataSnapshots.powerRooms("{}"))
    }

    private fun gradeReport(): GradeReport {
        val grade = TeachingGrade(
            year = YEAR,
            semester = SEMESTER,
            yearLabel = "2026-2027",
            semesterLabel = "第一学期",
            courseCode = "TEST001",
            courseName = "合成测试课程",
            courseNature = "必修",
            score = "缓考",
            credits = "2.0",
            gradePoint = "",
            creditGradePoint = "",
            examType = "正常考试",
            department = "测试学院",
            courseCategory = "通识课程",
            courseAttribute = "理论课",
            assessmentMethod = "考试",
            teachingClass = "测试教学班",
            teacher = "测试教学团队",
            degreeCourse = "是",
            courseMark = "测试标记",
        )
        return GradeReport(listOf(grade, grade.copy(
            courseCode = "TEST002", courseName = "另一门合成课程", score = "优秀",
            gradePoint = "4.0", creditGradePoint = "8.0", teacher = null,
        )), 2)
    }

    private fun courseOverview() = ChxCourseOverview(
        courses = listOf(ChxCourse(
            clazzId = "101",
            courseId = "201",
            curPersonId = "301",
            name = "合成测试课程",
            description = "测试教学团队\n测试课程说明",
            url = COURSE_URL,
            progress = ChxCourseProgress(4, 1, 25.0),
        )),
        progressError = null,
        loadedAt = UPDATED_AT - 5_000L,
        progressAuthRequired = false,
    )

    private companion object {
        const val YEAR = "2026"
        const val SEMESTER = "3"
        const val UPDATED_AT = 1_800_000_000_123L
        const val COURSE_URL = "https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=201&clazzid=101&cpi=301&ismooc2=1&v=2"
    }
}
