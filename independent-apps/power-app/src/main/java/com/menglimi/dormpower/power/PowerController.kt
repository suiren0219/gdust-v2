package com.menglimi.dormpower.power

import com.menglimi.power.PowerBinding
import com.menglimi.power.PowerDetailSource
import com.menglimi.power.PowerMonitor
import com.menglimi.power.PowerMonitorConfig
import com.menglimi.power.PowerQueryPreset
import com.menglimi.power.PowerReading
import com.menglimi.power.PowerRoom
import com.menglimi.power.PowerSample
import com.menglimi.power.PowerWarning
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 各操作独立反馈。历史只依赖已发布快照，不等待网络。 */
data class PowerUiState(
    val config: PowerMonitorConfig = PowerMonitorConfig(),
    val roomName: String = "",
    val reading: PowerReading? = null,
    val samples: List<PowerSample> = emptyList(),
    val bindingRevision: Long = 0L,
    val lastAttemptAt: Long = 0L,
    val lastSampleAt: Long = 0L,
    val localLoading: Boolean = false,
    val localError: String? = null,
    val recoveryMessage: String? = null,
    val busy: Boolean = false,
    val roomBusy: Boolean = false,
    val bindingBusy: Boolean = false,
    val rooms: List<PowerRoom> = emptyList(),
    val roomQueryAccount: String = "",
    val roomQueryCode: Int = 0,
    val error: String? = null,
    val roomError: String? = null,
    val settingsError: String? = null,
    val message: String? = null,
)

/** 基于合并 App 适配器；依赖可注入，以内存来源验证取消/换绑而不访问真实服务。 */
internal class PowerController(
    private val store: PowerRepository,
    private val source: PowerDetailSource,
    private val roomsSource: suspend (String, Int) -> List<PowerRoom>,
    private val scope: CoroutineScope,
    private val onSchedule: () -> Unit = {},
    private val onWarning: (PowerWarning) -> Boolean = { false },
    private val onUnbound: () -> Unit = {},
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val mutationLock = Any()
    private val selection = PowerRoomSelection()
    private var roomJob: Job? = null
    private var refreshJob: Job? = null
    private var refreshGeneration = 0L
    private val mutableState = MutableStateFlow(PowerUiState(localLoading = true))
    val state = mutableState.asStateFlow()

    init { reload() }

    /** 只读取本机一致快照；启动和返回前台均不自动发起网络。 */
    fun reload(): Job = scope.launch {
        try {
            synchronized(mutationLock) { publish(store.snapshot()) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.update { it.copy(localError = "无法读取本机电量数据，原文件已保留。请重试读取，勿卸载或清除应用数据。") }
        } finally {
            mutableState.update { it.copy(localLoading = false) }
        }
    }

    fun queryRooms(account: String, code: Int): Job {
        val normalized = account.trim()
        val job = synchronized(mutationLock) {
            roomJob?.cancel()
            val ticket = selection.begin()
            mutableState.update { it.copy(roomBusy = true, rooms = emptyList(), roomError = null,
                roomQueryAccount = "", roomQueryCode = 0) }
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    if (normalized.isBlank() || code <= 0) throw InputFailure("请填写账号和有效的学校代码")
                    val revision = synchronized(mutationLock) { store.snapshot().bindingRevision }
                    val rooms = roomsSource(normalized, code)
                        .filter { it.name.isNotBlank() && it.verify.isNotBlank() }.distinctBy { it.verify }
                    coroutineContext.ensureActive()
                    synchronized(mutationLock) {
                        if (selection.isCurrent(ticket)) {
                            if (store.snapshot().bindingRevision != revision) throw CancellationException("宿舍绑定已变更")
                            selection.complete(ticket, PowerRoomSelection.Result(normalized, code, rooms, revision))
                            mutableState.update { it.copy(rooms = rooms, roomQueryAccount = normalized, roomQueryCode = code,
                                roomError = if (rooms.isEmpty()) "未查到可绑定的房间，请核对账号和学校代码后重试。" else null) }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    synchronized(mutationLock) {
                        if (selection.isCurrent(ticket)) mutableState.update {
                            it.copy(roomError = requestFailure(failure, "查询房间"))
                        }
                    }
                } finally {
                    synchronized(mutationLock) {
                        if (selection.isCurrent(ticket)) mutableState.update { it.copy(roomBusy = false) }
                    }
                }
            }.also { roomJob = it }
        }
        job.start()
        return job
    }

    /** 关闭表单/改输入时同步使查询代次失效，再取消 HTTP 协程。绝不写入绑定。 */
    fun cancelRooms() = synchronized(mutationLock) {
        invalidateRooms()
        mutableState.update { it.copy(roomBusy = false, rooms = emptyList(), roomQueryAccount = "",
            roomQueryCode = 0, roomError = null) }
    }

    fun bind(account: String, code: Int, room: PowerRoom): Job {
        mutableState.update { it.copy(bindingBusy = true, roomError = null) }
        return scope.launch {
            var saved = false
            try {
                synchronized(mutationLock) {
                    coroutineContext.ensureActive()
                    val result = try { selection.requireMatch(account.trim(), code, room) }
                    catch (_: IllegalArgumentException) { throw InputFailure("请重新查询当前账号的房间并明确选择宿舍") }
                    val snapshot = store.bind(PowerBinding(result.account, result.code, room.verify), room.name, result.bindingRevision)
                    invalidateRefresh()
                    invalidateRooms()
                    publish(snapshot)
                    mutableState.update { it.copy(roomBusy = false, rooms = emptyList(), roomQueryAccount = "",
                        roomQueryCode = 0, error = null, roomError = null, message = "宿舍已绑定，正在获取读数") }
                    saved = true
                    onSchedule()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update { it.copy(roomError = (failure as? InputFailure)?.message
                    ?: "保存宿舍失败，请重新查询房间后重试；原绑定保持不变。") }
            } finally {
                mutableState.update { it.copy(bindingBusy = false) }
            }
            if (saved) refresh()
        }
    }

    fun refresh(): Job {
        val job = synchronized(mutationLock) {
            refreshJob?.takeIf { it.isActive }?.let { return it }
            val revision = mutableState.value.bindingRevision
            val ticket = ++refreshGeneration
            mutableState.update { it.copy(busy = true, error = null, message = null) }
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    coroutineContext.ensureActive()
                    val monitorStore = synchronized(mutationLock) {
                        if (store.snapshot().bindingRevision != revision) throw CancellationException("宿舍绑定已变更")
                        store.monitorStore()
                    }
                    if (monitorStore.config().binding?.isValid != true) throw InputFailure("请先绑定宿舍")
                    val jobContext = currentCoroutineContext()
                    val result = PowerMonitor(monitorStore, source, clock, onWarning = {
                        jobContext.ensureActive()
                        onWarning(it)
                    }).query(force = true)
                    coroutineContext.ensureActive()
                    synchronized(mutationLock) {
                        if (ticket == refreshGeneration) {
                            publish(store.snapshot())
                            mutableState.update { it.copy(error = null,
                                message = if (result.sampled) "电量已更新" else "已使用刚刚更新的读数") }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    synchronized(mutationLock) {
                        if (ticket == refreshGeneration) {
                            // 失败时间已经由 core 持久化；不丢掉上次成功数据。
                            runCatching { publish(store.snapshot()) }
                            mutableState.update { it.copy(error = requestFailure(failure, "刷新电量"), message = null) }
                        }
                    }
                } finally {
                    synchronized(mutationLock) {
                        if (ticket == refreshGeneration) mutableState.update { it.copy(busy = false) }
                    }
                }
            }.also { refreshJob = it }
        }
        job.start()
        return job
    }

    fun cancelRefresh() = synchronized(mutationLock) {
        invalidateRefresh()
        mutableState.update { it.copy(message = "刷新已取消，保留上次成功数据") }
    }

    /** 只重查近期缓存预警，供通知权限开启后使用，不触发网络查询。 */
    fun recheckWarnings(): Job = scope.launch {
        try {
            PowerMonitor(store.monitorStore(), source, clock, onWarning).recheckWarnings()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.update { it.copy(settingsError = "预警复查未完成，请稍后刷新电量。") }
        }
    }

    fun setPreset(preset: PowerQueryPreset) = configure { it.copy(preset = preset) }
    fun setInterval(minutes: Int) = configure {
        if (minutes !in 15..120) throw InputFailure("查询间隔应为 15 至 120 分钟")
        it.copy(customIntervalMinutes = minutes)
    }
    fun setThresholds(low: Double, drop: Double) = configure {
        if (!low.isFinite() || low < 0 || !drop.isFinite() || drop <= 0) {
            throw InputFailure("低电量阈值须不小于 0，掉电速率须大于 0")
        }
        it.copy(lowThreshold = low, dropRateThreshold = drop)
    }

    fun unbind(): Job = scope.launch {
        try {
            synchronized(mutationLock) {
                coroutineContext.ensureActive()
                val snapshot = store.unbind()
                invalidateRefresh()
                invalidateRooms()
                publish(snapshot)
                mutableState.update { it.copy(roomBusy = false, bindingBusy = false, rooms = emptyList(),
                    roomQueryAccount = "", roomQueryCode = 0, error = null, roomError = null, settingsError = null,
                    message = "已解绑并清除本机记录，后台监控已停止") }
                onSchedule()
                onUnbound()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.update { it.copy(settingsError = "解除绑定失败，请重试；原数据保持不变。") }
        }
    }

    fun clearError() { mutableState.update { it.copy(error = null) } }
    fun clearSettingsError() { mutableState.update { it.copy(settingsError = null) } }
    fun close() { cancelRooms(); cancelRefresh(); scope.cancel() }

    private fun configure(transform: (PowerMonitorConfig) -> PowerMonitorConfig): Job = scope.launch {
        try {
            synchronized(mutationLock) {
                coroutineContext.ensureActive()
                publish(store.configure(transform))
                mutableState.update { it.copy(settingsError = null, message = "设置已保存") }
                onSchedule()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            mutableState.update { it.copy(settingsError = (failure as? InputFailure)?.message ?: "保存设置失败，请重试。") }
        }
    }

    private fun invalidateRooms() { selection.invalidate(); roomJob?.cancel(); roomJob = null }
    private fun invalidateRefresh() {
        refreshGeneration++
        refreshJob?.cancel()
        refreshJob = null
        mutableState.update { it.copy(busy = false) }
    }
    private fun publish(snapshot: PowerSnapshot) {
        val binding = snapshot.config.binding
        val reading = snapshot.monitor.reading?.takeIf {
            binding != null && snapshot.monitor.cachedBindingKey == binding.key &&
                it.updatedAt > 0 && it.detail.odd?.isFinite() == true
        }
        mutableState.update { it.copy(config = snapshot.config, roomName = snapshot.roomName,
            bindingRevision = snapshot.bindingRevision, reading = reading,
            samples = if (binding == null) emptyList() else snapshot.monitor.samples,
            lastAttemptAt = snapshot.monitor.lastAttemptAt, lastSampleAt = snapshot.monitor.lastSampleAt,
            localError = null, recoveryMessage = snapshot.recoveryMessage) }
    }

    private class InputFailure(message: String) : IllegalArgumentException(message)
    private fun requestFailure(failure: Exception, action: String): String = when (failure) {
        is InputFailure -> failure.message.orEmpty()
        is UnknownHostException -> "${action}失败：网络不可用，请检查连接后重试。"
        is SocketTimeoutException -> "${action}超时，请稍后重试；已有数据仍保留。"
        is IOException -> "${action}失败：连接或存储不可用，请稍后重试。"
        else -> "${action}失败：服务未返回有效数据，请核对查询信息后重试。"
    }
}
