package com.menglimi.gdust.v2.ui.screens

import com.menglimi.gdust.v2.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class CloudMcpControllerTest {
    @Test fun loadSaveAndDraftChangesNeverConnectAndInvalidatePreviews() = runBlocking {
        val fixture = CloudMcpFixture()
        val controller = CloudMcpController(this, fixture.repository(), { fixture.document }, { error("No import expected") })
        controller.load(); controller.awaitIdle()
        assertTrue(controller.state.value.canRequest)
        assertTrue(fixture.requests.isEmpty())
        controller.edit(fixture.config.copy(token = "new-synthetic-token"))
        assertTrue(controller.state.value.dirty)
        controller.inspect(); controller.previewUpload()
        assertTrue(fixture.requests.isEmpty())
        controller.save(); controller.awaitIdle()
        assertFalse(controller.state.value.dirty)
        assertEquals(1, fixture.saves)
        assertTrue(fixture.requests.isEmpty())
        controller.previewUpload(); controller.awaitIdle()
        assertNotNull(controller.state.value.upload)
        controller.edit(fixture.config.copy(url = "https://other.test/mcp"))
        assertNull(controller.state.value.upload)
        assertFalse(controller.state.value.canRequest)
        controller.close()
    }

    @Test fun cancelDownloadWritesNothingAndConfirmImportsExactlyOneIndependentSchedule() = runBlocking {
        val fixture = CloudMcpFixture()
        var catalog: String? = null
        var writes = 0
        val library = ScheduleLibrary({ catalog }, { catalog = it; writes++ })
        val controller = CloudMcpController(this, fixture.repository(), { fixture.document }, {
            library.import(it.document, it.revision).name
        })
        controller.load(); controller.awaitIdle()
        controller.previewDownload(); controller.awaitIdle()
        assertEquals("Synthetic Schedule", controller.state.value.download?.scheduleName)
        assertEquals(0, writes)
        controller.cancelPreview()
        assertNull(controller.state.value.download)
        assertEquals(0, writes)
        controller.previewDownload(); controller.awaitIdle()
        controller.confirmDownload(); controller.confirmDownload(); controller.awaitIdle()
        assertEquals(1, writes)
        assertEquals(1, library.snapshot().schedules.size)
        assertNotEquals(ScheduleCatalog.SCHOOL, library.snapshot().selectedId)
        assertEquals("Synthetic Schedule", controller.state.value.importedName)
        assertTrue(fixture.requests.filter { it["method"].asString == "tools/call" }
            .all { it.getAsJsonObject("params")["name"].asString == "export_schedule_file" })
        controller.close()
    }

    @Test fun requestErrorsAreMaskedAndDoNotLeaveConfirmablePreviews() = runBlocking {
        val fixture = CloudMcpFixture()
        val controller = CloudMcpController(this, fixture.repository(), { error("synthetic-private-content") }, { "unused" })
        controller.load(); controller.awaitIdle()
        controller.previewUpload(); controller.awaitIdle()
        assertNotNull(controller.state.value.error)
        assertFalse(controller.state.value.error!!.contains("synthetic-private-content"))
        assertNull(controller.state.value.upload)
        controller.close()
    }

    private suspend fun CloudMcpController.awaitIdle() { withTimeout(5000) { state.first { !it.busy } } }
}
