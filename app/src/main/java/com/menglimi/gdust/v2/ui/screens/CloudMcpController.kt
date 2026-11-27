package com.menglimi.gdust.v2.ui.screens

import com.menglimi.gdust.v2.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class CloudMcpPageState(
    val draft: CloudMcpConfig = CloudMcpConfig(), val saved: CloudMcpConfig? = null,
    val busy: Boolean = true, val confirming: Boolean = false,
    val message: String = "正在读取本机配置", val error: String? = null,
    val upload: CloudUploadPreview? = null, val download: CloudDownloadPreview? = null,
    val importedName: String? = null,
) {
    val dirty: Boolean get() = saved != draft
    val canRequest: Boolean get() = !busy && !dirty && draft.enabled
    override fun toString() = "CloudMcpPageState(busy=$busy, dirty=$dirty)"
}

/** UI 与测试驱动同一个状态机；保存/打开不联网，配置变化立即作废所有旧预览。 */
internal class CloudMcpController(
    private val scope: CoroutineScope, private val repo: CloudMcpRepository,
    private val exportSchedule: suspend () -> String,
    private val importSchedule: suspend (CloudDownloadPreview) -> String,
) {
    private val mutable = MutableStateFlow(CloudMcpPageState())
    val state = mutable.asStateFlow()
    private var job: Job? = null

    fun load() = run(allowUnsaved = true) {
        val config = repo.loadConfig()
        mutable.value = CloudMcpPageState(draft = config, saved = config, busy = true, message = "配置已加载 · 尚未联网")
    }

    fun edit(config: CloudMcpConfig) {
        val before = mutable.value
        if (before.busy) return
        mutable.value = before.copy(draft = config, upload = null, download = null,
            error = null, message = "配置有修改，请保存后再操作")
    }

    fun save() = run(allowUnsaved = true) {
        val config = mutable.value.draft
        val saved = repo.saveConfig(config.url, config.token, config.enabled)
        mutable.value = mutable.value.copy(draft = saved, saved = saved, upload = null, download = null,
            message = "配置已加密保存 · 未发送网络请求")
    }

    fun inspect() = run {
        val status = repo.inspect(requireNotNull(mutable.value.saved))
        mutable.value = mutable.value.copy(message = "连接正常 · 云端版本 ${status.revision} · ${if (status.hasSchedule) "已有课表" else "云端为空"}")
    }

    fun previewUpload() = run {
        val preview = repo.previewUpload(requireNotNull(mutable.value.saved), exportSchedule())
        mutable.value = mutable.value.copy(upload = preview, download = null, message = "上传预览已生成，尚未提交")
    }

    fun confirmUpload() {
        val before = mutable.value
        val preview = before.upload ?: return
        run(confirming = true) {
            val status = repo.confirmUpload(requireNotNull(before.saved), preview, exportSchedule())
            mutable.value = mutable.value.copy(upload = null, message = "已上传 · 云端版本 ${status.revision}")
        }
    }

    fun previewDownload() = run {
        val preview = repo.previewDownload(requireNotNull(mutable.value.saved))
        mutable.value = mutable.value.copy(download = preview, upload = null, message = "下载预览已就绪 · 尚未写入本机")
    }

    fun confirmDownload() {
        val preview = mutable.value.download ?: return
        run(confirming = true) {
            val name = importSchedule(preview)
            mutable.value = mutable.value.copy(download = null, importedName = name, message = "已导入独立课表「$name」；原课表保留")
        }
    }

    fun cancelPreview() {
        if (!mutable.value.busy) mutable.value = mutable.value.copy(upload = null, download = null, error = null, message = "已取消，数据未改动")
    }

    fun close() { job?.cancel() }

    private fun run(allowUnsaved: Boolean = false, confirming: Boolean = false, block: suspend () -> Unit) {
        if (job?.isActive == true || (!allowUnsaved && !mutable.value.canRequest)) return
        mutable.value = mutable.value.copy(busy = true, confirming = confirming, error = null, importedName = null)
        job = scope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = mutable.value.copy(
                upload = null, download = null, error = if (confirming)
                "操作未完成或结果未确认。请核对状态后重新预览；不会自动重试提交。" else
                "读取或保存失败，请检查配置、网络与课表格式。原课表已保留。") }
            finally { mutable.value = mutable.value.copy(busy = false, confirming = false) }
        }
    }
}
