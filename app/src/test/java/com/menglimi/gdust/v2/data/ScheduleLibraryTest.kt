package com.menglimi.gdust.v2.data

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class ScheduleLibraryTest {
    @Test fun importingUsesOneAtomicWriteAndKeepsEveryExistingSchedule() {
        val cache = mutableMapOf("blocks" to "original-school-blocks", "calendar" to "original-school-calendar")
        var writes = 0
        val library = ScheduleLibrary({ cache[ScheduleLibrary.KEY] }, { cache[ScheduleLibrary.KEY] = it; writes++ })
        val first = library.import(document("学校课表"), 1, 10)
        assertEquals("学校课表 · 2", first.name)
        assertEquals(1, writes)
        assertEquals(first.id, library.snapshot().selectedId)
        val second = library.import(document("学校课表"), 2, 20)
        assertEquals("学校课表 · 3", second.name)
        assertEquals(2, library.snapshot().schedules.size)
        assertEquals("original-school-blocks", cache["blocks"])
        cache["blocks"] = "new-school-sync"
        assertEquals(second.id, library.snapshot().selectedId)
        library.select(ScheduleCatalog.SCHOOL)
        assertEquals(2, library.snapshot().schedules.size)
        library.select(first.id)
        assertEquals(first.id, library.snapshot().selectedId)
        assertEquals("new-school-sync", cache["blocks"])
    }

    @Test fun invalidImportOrCorruptCatalogNeverWrites() {
        var stored: String? = null
        var writes = 0
        val library = ScheduleLibrary({ stored }, { stored = it; writes++ })
        assertThrows(Exception::class.java) { library.import("{}", 1) }
        assertThrows(Exception::class.java) { library.import(document(), -1) }
        assertEquals(0, writes)
        stored = "{broken"
        assertThrows(Exception::class.java) { library.import(document(), 1) }
        assertEquals("{broken", stored)
        assertEquals(0, writes)
    }

    @Test fun failedPersistenceLeavesPreviousSelectionAndDocumentIntact() {
        var stored: String? = null
        var fail = false
        val library = ScheduleLibrary({ stored }, { if (fail) error("synthetic-write-failure"); stored = it })
        val first = library.import(document(), 1)
        val before = stored
        fail = true
        assertThrows(IllegalStateException::class.java) { library.import(document(), 2) }
        assertEquals(before, stored)
        assertEquals(first.id, library.snapshot().selectedId)
    }

    @Test fun selectedWeeksTakePrecedenceAndOriginalMetadataSurvives() {
        val root = JsonParser.parseString(document()).asJsonObject
        val course = root.getAsJsonArray("courses")[0].asJsonObject
        course.addProperty("weekType", 2)
        course.addProperty("startWeek", 2)
        course.addProperty("endWeek", 4)
        course.addProperty("note", "synthetic-note")
        val parsed = ScheduleShareCodec.parse(root.toString())
        assertEquals(listOf(1), parsed.blocks.map { it.week })
        assertEquals("synthetic-note", JsonParser.parseString(parsed.document).asJsonObject.getAsJsonArray("courses")[0].asJsonObject["note"].asString)
        course.addProperty("startSection", 1.5)
        assertThrows(IllegalArgumentException::class.java) { ScheduleShareCodec.parse(root.toString()) }
    }

    private fun document(name: String = "Synthetic") = ScheduleShareCodec.export(CalendarInfo(1, 4, "2026-09-07", "2026", "1"),
        listOf(CourseBlock(1, 1, 1, 1, "Synthetic", "Room", "Teacher", "2026-09-07")), name)
}
