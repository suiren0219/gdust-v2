package com.menglimi.study.learning

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.menglimi.chaoxing.ChxCourse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningLibraryTest {
    private val autumn = LearningTerm("2026_1", "2026-2027 · 学期 1", "2026-09-01", "2026-09-30")
    private val winter = LearningTerm("2026_2", "2026-2027 · 学期 2", "2026-10-01", "2027-01-10")
    private val key = "1:2:3"

    private fun course(courseId: String = "1", joinedOn: String? = null) =
        ChxCourse("2", courseId, "3", "课程$courseId", "说明",
            "https://mooc1.chaoxing.com/visit/stucoursemiddle", joinedOn = joinedOn)

    @Test fun folderNamesAreTrimmedUniqueAndBounded() {
        val library = LearningLibrary().createFolder(" 高数 ", null)
        assertEquals("高数", library.folders.single().name)
        assertThrows(IllegalArgumentException::class.java) { library.createFolder("高数", null) }
        assertThrows(IllegalArgumentException::class.java) { library.createFolder("   ", null) }
        assertThrows(IllegalArgumentException::class.java) { library.createFolder("x".repeat(31), null) }
        assertThrows(IllegalArgumentException::class.java) { library.createFolder("线代", "missing") }
    }

    @Test fun removingFolderOnlyClearsManualAssignmentAndFallsBackToAll() {
        val library = LearningLibrary().createFolder("高数", null).let { base ->
            base.move(key, base.folders.single().id).select(LearningScope.FOLDER, base.folders.single().id)
        }
        val cleared = library.removeFolder(library.folders.single().id)
        assertTrue(cleared.folders.isEmpty())
        assertTrue(cleared.assignments.isEmpty())
        assertEquals(LearningScope.ALL, cleared.scope)
        assertNull(cleared.selectedId)
    }

    @Test fun manualFolderWinsOverCalendarEvenWhenDatesPointElsewhere() {
        val base = LearningLibrary().withTerm(autumn).withTerm(winter)
        val library = base.createFolder("重修", winter.id).let { added ->
            added.move(key, added.folders.single().id)
        }
        // 加入日期落在秋季学期，但手动放进绑定春季学期的文件夹，按手动结果。
        assertEquals(winter, library.term(key, "2026-09-15"))
        assertEquals("手动 · 重修 · ${winter.name}", library.explanation(course(joinedOn = "2026-09-15")))
    }

    @Test fun automaticTermNeedsSingleCalendarMatchAndRealJoinDate() {
        val base = LearningLibrary().withTerm(autumn)
        assertEquals(autumn, base.term(key, "2026-09-15"))
        assertNull(base.term(key, null))
        assertNull(base.term(key, "2026-08-31"))
        assertEquals("自动 · ${autumn.name} · 加入日期 2026-09-15", base.explanation(course(joinedOn = "2026-09-15")))
        assertEquals("待确认 · 加入日期未匹配校历", base.explanation(course(joinedOn = "2026-08-31")))
        assertEquals("待确认 · 尚未导入校历", LearningLibrary().explanation(course(joinedOn = "2026-09-15")))
        assertEquals("待确认 · 学习通未提供加入时间", base.explanation(course()))
    }

    @Test fun scopesOnlyNarrowTheVisibleCoursesAndNeverInventAssignments() {
        val library = LearningLibrary().withTerm(autumn).createFolder("高数", null).let { base ->
            base.move(key, base.folders.single().id).select(LearningScope.FOLDER, base.folders.single().id)
        }
        val manual = course("1")
        val automatic = course("2", joinedOn = "2026-09-15")
        val unknown = course("3")
        assertEquals(listOf(manual), library.copy(scope = LearningScope.FOLDER)
            .visible(listOf(manual, automatic, unknown)))
        assertEquals(listOf(automatic), library.copy(scope = LearningScope.TERM, selectedId = autumn.id)
            .visible(listOf(manual, automatic, unknown)))
        assertEquals(listOf(unknown), library.copy(scope = LearningScope.UNASSIGNED, selectedId = null)
            .visible(listOf(manual, automatic, unknown)))
        assertEquals(3, library.copy(scope = LearningScope.ALL, selectedId = null)
            .visible(listOf(manual, automatic, unknown)).size)
    }

    @Test fun overlappingTermsAreRejectedButSameTermCanBeUpdated() {
        val library = LearningLibrary().withTerm(autumn)
        assertThrows(IllegalArgumentException::class.java) {
            library.withTerm(LearningTerm("2026_2", "学年 2", "2026-09-15", "2027-01-10"))
        }
        val updated = library.withTerm(autumn.copy(name = "改过的名称"))
        assertEquals(1, updated.terms.size)
        assertEquals("改过的名称", updated.terms.single().name)
    }

    @Test fun codecRoundTripKeepsFoldersTermsAndScope() {
        val term = LearningTerm("2026_1", "2026-2027 · 学期 1", "2026-09-01", "2027-01-10")
        val library = LearningLibrary().withTerm(term).createFolder("A", null).let { base ->
            val second = base.createFolder("B", term.id)
            second.move(key, second.folders.first().id)
                .select(LearningScope.FOLDER, second.folders.first().id)
        }
        assertEquals(library, LearningLibraryCodec.decode(LearningLibraryCodec.encode(library)))
        assertEquals(LearningLibrary(), LearningLibraryCodec.decode(null))
        assertFalse(LearningLibraryCodec.encode(library).toString().contains("Cookie"))
    }

    @Test fun codecRejectsAssignmentsToMissingFoldersAndUnknownScopes() {
        val folder = LearningLibrary().createFolder("A", null)
        val encoded = LearningLibraryCodec.encode(folder)
        val strayAssignment = JsonParser.parseString(encoded.toString()).asJsonObject
        strayAssignment.getAsJsonObject("assignments").addProperty(key, "missing-folder")
        assertThrows(IllegalArgumentException::class.java) {
            LearningLibraryCodec.decode(strayAssignment)
        }
        val unknownScope = JsonParser.parseString(encoded.toString()).asJsonObject
        unknownScope.addProperty("scope", "EVERYTHING")
        assertThrows(IllegalArgumentException::class.java) {
            LearningLibraryCodec.decode(unknownScope)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LearningLibraryCodec.decode(JsonObject().apply { addProperty("scope", "FOLDER") })
        }
    }

    @Test fun calendarImportReadsOnlyTermFieldsAndRejectsOtherFiles() {
        val backup = """
            {"app":"gdust-v2","schema":1,"term_start":"2026-09-07","term_weeks":20,
             "calendar":"{\"year\":\"2026\",\"semester\":\"1\",\"startDate\":\"2026-09-07\",\"allWeek\":18}"}
        """.trimIndent()
        // term_start/term_weeks 优先，calendar 内字段作后备。
        assertEquals(LearningTerm("2026_1", "2026-2027 · 学期 1", "2026-09-07", "2027-01-24"),
            LearningCalendarImport.parse(backup))
        val fallback = """{"app":"gdust-v2","schema":1,"calendar":{"year":"2025","semester":"2","startDate":"2026-02-23","allWeek":18}}"""
        assertEquals(LearningTerm("2025_2", "2025-2026 · 学期 2", "2026-02-23", "2026-06-28"),
            LearningCalendarImport.parse(fallback))
        assertThrows(IllegalArgumentException::class.java) { LearningCalendarImport.parse("not json") }
        assertThrows(IllegalArgumentException::class.java) { LearningCalendarImport.parse("{\"app\":\"other\"}") }
        assertThrows(IllegalArgumentException::class.java) { LearningCalendarImport.parse("{\"app\":\"gdust-v2\",\"schema\":1}") }
        assertThrows(IllegalArgumentException::class.java) {
            LearningCalendarImport.parse("""{"app":"gdust-v2","schema":1,"calendar":{"year":"2026","semester":"1","startDate":"2026-09-07","allWeek":0}}""")
        }
    }

    @Test fun snapshotRoundTripKeepsLibrarySoRefreshCannotDropManualFolders() {
        val term = LearningTerm("2026_1", "2026-2027 · 学期 1", "2026-09-01", "2027-01-10")
        val library = LearningLibrary().withTerm(term).createFolder("高数", null).let { base ->
            base.move(key, base.folders.single().id)
        }
        val snapshot = LearningSnapshot("a".repeat(64), library = library)
        assertEquals(snapshot, LearningSnapshotCodec.decode(LearningSnapshotCodec.encode(snapshot)))
        // 旧快照没有 library 字段时按空分类读取，不丢课程与作业。
        val old = JsonParser.parseString(LearningSnapshotCodec.encode(snapshot)).asJsonObject
        old.remove("library")
        val restored = LearningSnapshotCodec.decode(old.toString())
        assertEquals(LearningLibrary(), restored.library)
        assertEquals(snapshot.copy(library = LearningLibrary()), restored)
    }
}
