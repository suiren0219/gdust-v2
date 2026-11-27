package com.menglimi.gdust.v2.service

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.BufferedOutputStream

class LocalMcpHttpTest {
    @Test fun notificationsAndResponseHeaderLimitsAreWellDefined() {
        val response = CaptureOutput()
        LocalMcpContract.writeResponse(BufferedOutputStream(response), 202, "")
        assertTrue(response.value().contains("202 Accepted"))
        assertTrue(response.value().contains("Content-Length: 0"))
        assertEquals(26621, LocalMcpContract.PORT)
        assertEquals("127.0.0.1", LocalMcpContract.BIND_ADDRESS)
        assertEquals(null, LocalMcpContract.readRequestBodyLength(null))
        assertEquals(null, LocalMcpContract.readRequestBodyLength("not-a-number"))
        assertEquals(null, LocalMcpContract.readRequestBodyLength((LocalMcpContract.MAX_REQUEST_BYTES + 1).toString()))
        assertEquals(2, LocalMcpContract.readRequestBodyLength("2"))
        val negotiated = LocalMcpContract.initialize(JsonObject().apply { addProperty("protocolVersion", "future-version") })
        assertEquals("2025-11-25", negotiated.get("protocolVersion").asString)
    }

    @Test fun readToolsNeverIncludeLoginOrCredentialFields() {
        val methods = LocalMcpContract.tools().asList().associateBy { it.asJsonObject.get("name").asString }
        assertEquals(setOf("get_week_courses", "get_courses_on_date", "search_courses", "get_semester_info", "get_todos"), methods.keys)
        assertTrue(methods.values.all { it.asJsonObject.getAsJsonObject("annotations").get("readOnlyHint").asBoolean })
    }

    private class CaptureOutput : OutputStream() {
        private val bytes = ByteArrayOutputStream()
        override fun write(b: Int) = bytes.write(b)
        fun value() = bytes.toString(Charsets.UTF_8.name())
    }
}
