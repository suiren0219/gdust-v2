package com.menglimi.dormpower.power

import com.menglimi.power.PowerBinding
import com.menglimi.power.PowerMonitorConfig
import com.menglimi.power.PowerMonitorState
import com.menglimi.power.PowerMonitorStore
import com.menglimi.power.PowerQueryPreset
import kotlin.coroutines.cancellation.CancellationException

/** 整个绑定、配置和历史始终以一个加密条目提交。 */
internal data class PowerSnapshot(
    val config: PowerMonitorConfig = PowerMonitorConfig(),
    val roomName: String = "",
    val monitor: PowerMonitorState = PowerMonitorState(),
    val bindingRevision: Long = 0L,
)

internal interface PowerPersistence {
    fun read(): String?
    fun write(value: String)
}

internal interface PowerRepository : PowerMonitorStore {
    fun snapshot(): PowerSnapshot
    fun monitorStore(background: Boolean = false): PowerMonitorStore
    fun configure(transform: (PowerMonitorConfig) -> PowerMonitorConfig): PowerSnapshot
    fun bind(binding: PowerBinding, roomName: String, expectedRevision: Long): PowerSnapshot
    fun unbind(): PowerSnapshot
}

/** Android 与纯 JVM 测试共用的存储事务；同一进程的所有 PowerStore 共享此实例。 */
internal class PowerStorage(private val persistence: PowerPersistence) : PowerRepository {
    private val storageLock = Any()
    private var cached: PowerSnapshot? = null

    override fun snapshot(): PowerSnapshot = synchronized(storageLock) { current() }

    override fun config(): PowerMonitorConfig = snapshot().config

    override fun state(): PowerMonitorState = snapshot().monitor

    override fun update(
        binding: PowerBinding,
        transform: (PowerMonitorState) -> PowerMonitorState,
    ): Boolean = synchronized(storageLock) { updateLocked(binding, null, transform) }

    /** 请求固定绑定版本，防止 A → B → A 后把第一次 A 的迟到结果写回。 */
    override fun monitorStore(background: Boolean): PowerMonitorStore = synchronized(storageLock) {
        val revision = current().bindingRevision
        object : PowerMonitorStore {
            override fun config(): PowerMonitorConfig = synchronized(storageLock) {
                current().also {
                    if (it.bindingRevision != revision) throw CancellationException("宿舍绑定已变更")
                }.config
            }
            override fun state(): PowerMonitorState = this@PowerStorage.state()
            override fun update(
                binding: PowerBinding,
                transform: (PowerMonitorState) -> PowerMonitorState,
            ): Boolean = synchronized(storageLock) {
                if (background && current().config.preset == PowerQueryPreset.MANUAL) false
                else updateLocked(binding, revision, transform)
            }
        }
    }

    override fun configure(transform: (PowerMonitorConfig) -> PowerMonitorConfig): PowerSnapshot = synchronized(storageLock) {
        val before = current()
        val config = transform(before.config)
        require(config.binding == before.config.binding) { "更换宿舍请重新查询并选择房间" }
        save(before.copy(config = config))
    }

    override fun bind(binding: PowerBinding, roomName: String, expectedRevision: Long): PowerSnapshot = synchronized(storageLock) {
        val before = current()
        check(before.bindingRevision == expectedRevision) { "宿舍绑定已变化，请重新查询房间" }
        require(binding.isValid && roomName.isNotBlank()) { "请选择有效宿舍" }
        save(before.copy(
            config = before.config.copy(binding = binding),
            roomName = roomName,
            monitor = if (before.config.binding == binding) before.monitor else PowerMonitorState(),
            bindingRevision = before.bindingRevision + 1,
        ))
    }

    override fun unbind(): PowerSnapshot = synchronized(storageLock) {
        val before = current()
        save(before.copy(
            config = before.config.copy(binding = null),
            roomName = "",
            monitor = PowerMonitorState(),
            bindingRevision = before.bindingRevision + 1,
        ))
    }

    private fun updateLocked(
        binding: PowerBinding,
        revision: Long?,
        transform: (PowerMonitorState) -> PowerMonitorState,
    ): Boolean {
        val before = current()
        if (before.config.binding != binding || (revision != null && revision != before.bindingRevision)) return false
        // transform 可能发送通知，必须在绑定锁内且只执行一次；持久化失败也不得重试。
        save(before.copy(monitor = transform(before.monitor)))
        return true
    }

    private fun current(): PowerSnapshot = cached ?: (
        persistence.read()?.let(PowerPersistenceCodec::decode) ?: PowerSnapshot()
    ).also { cached = it }

    private fun save(next: PowerSnapshot): PowerSnapshot {
        if (next != current()) {
            persistence.write(PowerPersistenceCodec.encode(next))
            cached = next
        }
        return next
    }
}

/** 调用方使用同一个 controller mutex，把核验和存储提交放在同一临界区。 */
internal class PowerRoomSelection {
    private var generation = 0L
    private var successful: Result? = null

    internal data class Result(
        val account: String,
        val code: Int,
        val rooms: List<com.menglimi.power.PowerRoom>,
        val bindingRevision: Long,
    )

    fun begin(): Long {
        successful = null
        return ++generation
    }

    fun isCurrent(ticket: Long): Boolean = ticket == generation

    fun complete(ticket: Long, result: Result): Boolean {
        if (!isCurrent(ticket)) return false
        successful = result
        return true
    }

    fun requireMatch(account: String, code: Int, room: com.menglimi.power.PowerRoom): Result {
        val result = successful
        require(result != null && result.account == account && result.code == code &&
            room.verify.isNotBlank() && room.name.isNotBlank() && room in result.rooms) {
            "请先查询当前账号的房间，再选择查询结果中的宿舍"
        }
        return result
    }

    fun invalidate() { begin() }
}
