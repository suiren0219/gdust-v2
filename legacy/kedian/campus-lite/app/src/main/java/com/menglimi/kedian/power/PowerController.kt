package com.menglimi.kedian.power

import android.content.Context
import com.menglimi.power.PowerApi
import com.menglimi.power.PowerBinding
import com.menglimi.power.PowerMonitor
import com.menglimi.power.PowerMonitorConfig
import com.menglimi.power.PowerQueryPreset
import com.menglimi.power.PowerReading
import com.menglimi.power.PowerRoom
import com.menglimi.power.PowerSample
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class PowerUiState(
    val config: PowerMonitorConfig,
    val roomName: String = "",
    val reading: PowerReading? = null,
    val samples: List<PowerSample> = emptyList(),
    val busy: Boolean = false,
    val roomBusy: Boolean = false,
    val rooms: List<PowerRoom> = emptyList(),
    val roomQueryAccount: String = "",
    val roomQueryCode: Int = 0,
    /** 上次查询用的账号/代码，供绑定表单预填；只含非敏感查询条件。 */
    val lastQueryAccount: String = "",
    val lastQueryCode: Int = 0,
    val error: String? = null,
)

/** 一份页面/Activity 持有一份实例；构造只启动 IO 读取，onResume 调用 reload 即可。 */
class PowerController(context: Context) {
    private val application = context.applicationContext
    private val store = PowerStore(application)
    private val api by lazy { PowerApi() }
    private val notifications by lazy { PowerNotifications(application) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutationMutex = Mutex()
    private val selection = PowerRoomSelection()
    private var activeRefreshes = 0
    private val mutableState = MutableStateFlow(PowerUiState(PowerMonitorConfig()))
    val state: StateFlow<PowerUiState> = mutableState.asStateFlow()

    init { scope.launch { reload() } }

    /** 只读本地一致快照；不会触发网络或把缓存当新采样。 */
    suspend fun reload() = withContext(Dispatchers.IO) {
        try {
            mutationMutex.withLock {
                currentCoroutineContext().ensureActive()
                publish(store.snapshot())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            fail("无法读取电量数据，请稍后重试")
        }
    }

    suspend fun queryRooms(account: String, code: Int) = withContext(Dispatchers.IO) {
        val normalized = account.trim()
        val ticket = mutationMutex.withLock {
            selection.begin().also {
                mutableState.update { state -> state.copy(
                    roomBusy = true, rooms = emptyList(), roomQueryAccount = "", roomQueryCode = 0, error = null,
                ) }
            }
        }
        try {
            if (normalized.isBlank() || code <= 0) throw InputFailure("请填写账号和有效的客户代码")
            val revision = mutationMutex.withLock { store.snapshot().bindingRevision }
            val rooms = api.rooms(normalized, code)
                .filter { it.name.isNotBlank() && it.verify.isNotBlank() }
                .distinctBy { it.verify }
            currentCoroutineContext().ensureActive()
            mutationMutex.withLock {
                if (selection.isCurrent(ticket)) {
                    if (store.snapshot().bindingRevision != revision) throw CancellationException("宿舍绑定已变更")
                    selection.complete(ticket, PowerRoomSelection.Result(normalized, code, rooms, revision))
                    // 查询成功即记住条件（无论有无房间），下次打开表单自动预填；不含密码或房间校验值。
                    publish(store.rememberQuery(normalized, code))
                    mutableState.update { it.copy(
                        rooms = rooms, roomQueryAccount = normalized, roomQueryCode = code,
                        error = if (rooms.isEmpty()) {
                            "该账号在当前学校代码下未查到宿舍。请确认账号无误，或先在完美校园 App 绑定宿舍后再试。"
                        } else null,
                    ) }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            mutationMutex.withLock {
                if (selection.isCurrent(ticket)) {
                    // 记住失败前的查询条件，方便用户改错时不必重输未变的那一项；无效输入不记。
                    if (normalized.isNotBlank() && code > 0) publish(store.rememberQuery(normalized, code))
                    fail((failure as? InputFailure)?.message
                        ?: "查询房间失败：网络不可用或返回数据异常，请检查网络后重试")
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                mutationMutex.withLock {
                    if (selection.isCurrent(ticket)) mutableState.update { it.copy(roomBusy = false) }
                }
            }
        }
    }

    suspend fun bind(account: String, code: Int, room: PowerRoom) = withContext(Dispatchers.IO) {
        var saved = false
        try {
            mutationMutex.withLock {
                currentCoroutineContext().ensureActive()
                val result = try {
                    selection.requireMatch(account.trim(), code, room)
                } catch (_: IllegalArgumentException) {
                    throw InputFailure("请先查询当前账号的房间，再选择查询结果中的宿舍")
                }
                val snapshot = store.bind(PowerBinding(result.account, result.code, room.verify), room.name, result.bindingRevision)
                selection.invalidate()
                publish(snapshot)
                mutableState.update { it.copy(
                    roomBusy = false, rooms = emptyList(), roomQueryAccount = "", roomQueryCode = 0, error = null,
                ) }
                saved = true
                PowerScheduling.ensure(application)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            fail((failure as? InputFailure)?.message ?: "保存宿舍失败，请重新查询房间后重试")
        }
        if (saved) refresh()
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            activeRefreshes++
            mutableState.update { it.copy(busy = true, error = null) }
        }
        try {
            currentCoroutineContext().ensureActive()
            val monitorStore = mutationMutex.withLock { store.monitorStore() }
            if (monitorStore.config().binding?.isValid != true) throw InputFailure("请先绑定宿舍")
            val jobContext = currentCoroutineContext()
            PowerMonitor(monitorStore, api, onWarning = { warning ->
                jobContext.ensureActive()
                notifications.send(warning)
            }).query(force = true)
            currentCoroutineContext().ensureActive()
            mutationMutex.withLock { publish(store.snapshot()) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            fail((failure as? InputFailure)?.message ?: "刷新电量失败，请检查网络后重试")
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                mutationMutex.withLock {
                    activeRefreshes--
                    mutableState.update { it.copy(busy = activeRefreshes > 0) }
                }
            }
        }
    }

    fun setPreset(preset: PowerQueryPreset) = configure { it.copy(preset = preset) }

    fun setThreshold(value: Double) = configure {
        if (!value.isFinite() || value < 0) throw InputFailure("低电量预警值必须是大于或等于 0 的数值")
        it.copy(lowThreshold = value)
    }

    fun setDropThreshold(value: Double) = configure {
        if (!value.isFinite() || value <= 0) throw InputFailure("掉电预警值必须是大于 0 的数值")
        it.copy(dropRateThreshold = value)
    }

    fun setInterval(minutes: Int) = configure {
        if (minutes !in 15..120) throw InputFailure("查询间隔应为 15 至 120 分钟")
        it.copy(customIntervalMinutes = minutes)
    }

    fun unbind() {
        scope.launch {
            try {
                mutationMutex.withLock {
                    val snapshot = store.unbind()
                    selection.invalidate()
                    publish(snapshot)
                    mutableState.update { it.copy(
                        roomBusy = false, rooms = emptyList(), roomQueryAccount = "", roomQueryCode = 0, error = null,
                    ) }
                    PowerScheduling.ensure(application)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                fail("解除绑定失败，请稍后重试")
            }
        }
    }

    fun clearError() { mutableState.update { it.copy(error = null) } }

    private fun configure(transform: (PowerMonitorConfig) -> PowerMonitorConfig) {
        scope.launch {
            try {
                mutationMutex.withLock {
                    publish(store.configure(transform))
                    clearError()
                    PowerScheduling.ensure(application)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                fail((failure as? InputFailure)?.message ?: "保存电量设置失败，请稍后重试")
            }
        }
    }

    private fun publish(snapshot: PowerSnapshot) {
        val binding = snapshot.config.binding
        val reading = snapshot.monitor.reading?.takeIf {
            binding != null && snapshot.monitor.cachedBindingKey == binding.key &&
                it.updatedAt > 0 && it.detail.odd?.isFinite() == true
        }
        mutableState.update { it.copy(
            config = snapshot.config,
            roomName = snapshot.roomName,
            reading = reading,
            samples = if (binding == null) emptyList() else snapshot.monitor.samples,
            lastQueryAccount = snapshot.lastQuery?.account.orEmpty(),
            lastQueryCode = snapshot.lastQuery?.code ?: 0,
        ) }
    }

    private fun fail(message: String) { mutableState.update { it.copy(error = message) } }
    private class InputFailure(message: String) : IllegalArgumentException(message)
}
