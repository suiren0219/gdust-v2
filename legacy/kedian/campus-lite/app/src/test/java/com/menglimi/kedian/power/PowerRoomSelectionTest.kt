package com.menglimi.kedian.power

import com.menglimi.power.PowerRoom
import org.junit.Assert.*
import org.junit.Test

class PowerRoomSelectionTest {
    private val room = PowerRoom("测试宿舍", "synthetic-verify")
    private val result = PowerRoomSelection.Result("synthetic-account", 1, listOf(room), 7L)

    @Test fun bindingRequiresAccountCodeAndWholeRoomFromSuccessfulQuery() {
        val selection = PowerRoomSelection()
        assertThrows(IllegalArgumentException::class.java) { selection.requireMatch(result.account, result.code, room) }
        val ticket = selection.begin()
        assertTrue(selection.complete(ticket, result))
        assertEquals(result, selection.requireMatch(result.account, result.code, room))
        assertThrows(IllegalArgumentException::class.java) { selection.requireMatch("other-account", result.code, room) }
        assertThrows(IllegalArgumentException::class.java) { selection.requireMatch(result.account, 2, room) }
        assertThrows(IllegalArgumentException::class.java) {
            selection.requireMatch(result.account, result.code, room.copy(verify = "forged-verify"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            selection.requireMatch(result.account, result.code, room.copy(name = "伪造房间名称"))
        }
    }

    @Test fun startingAnotherQueryInvalidatesOldSelectionEvenIfNewQueryFails() {
        val selection = PowerRoomSelection()
        selection.complete(selection.begin(), result)
        selection.begin()
        assertThrows(IllegalArgumentException::class.java) { selection.requireMatch(result.account, result.code, room) }
    }

    @Test fun lateQueryCannotReplaceNewerSuccessfulResult() {
        val selection = PowerRoomSelection()
        val old = selection.begin()
        val current = selection.begin()
        val replacement = result.copy(account = "other-account")
        assertTrue(selection.complete(current, replacement))
        assertFalse(selection.complete(old, result))
        assertEquals(replacement, selection.requireMatch(replacement.account, replacement.code, room))
    }

    @Test fun invalidationPreventsInFlightQueryFromRestoringSelection() {
        val selection = PowerRoomSelection()
        val ticket = selection.begin()
        selection.invalidate()
        assertFalse(selection.complete(ticket, result))
        assertThrows(IllegalArgumentException::class.java) { selection.requireMatch(result.account, result.code, room) }
    }
}
