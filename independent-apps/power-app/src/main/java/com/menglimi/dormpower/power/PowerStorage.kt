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
    val recoveryMessage: String? = null,
    // null means this version of the snapshot did not write any ledger field.
    val ledger: PowerLedgerState? = null,
)

/** 主快照保留最新设置。只从同一次宿舍绑定的旧快照补回缺失记录。 */
internal fun recoverMissingHistory(current: PowerSnapshot, previous: PowerSnapshot?): PowerSnapshot {
    val binding = current.config.binding ?: return current
    if (previous == null || previous.config.binding != binding || previous.bindingRevision != current.bindingRevision) {
        return current
    }
    val quarterHour = 15 * 60_000L
    val currentBuckets = current.monitor.samples.mapTo(HashSet()) { it.time / quarterHour }
    val missingSamples = previous.monitor.samples.asSequence()
        .filter { it.time / quarterHour !in currentBuckets }
        .sortedByDescending { it.time }
        .distinctBy { it.time / quarterHour }
        .toList()
    val reading = current.monitor.reading ?: previous.monitor.reading
    val ledger = current.ledger ?: previous.ledger
    if (missingSamples.isEmpty() && reading == current.monitor.reading && ledger == current.ledger) return current
    return current.copy(
        monitor = current.monitor.copy(
            cachedBindingKey = if (reading == null) current.monitor.cachedBindingKey else binding.key,
            reading = reading,
            lastSampleAt = maxOf(current.monitor.lastSampleAt, reading?.updatedAt ?: 0L),
            samples = (current.monitor.samples + missingSamples).sortedBy { it.time },
        ),
        ledger = ledger,
        recoveryMessage = "已从本机加密备份补回同一宿舍的记录。",
    )
}

internal interface PowerPersistence {
    fun read(): String?
    fun readBackup(): String? = null
    fun write(value: String)
}

internal interface PowerRepository : PowerMonitorStore {
    fun snapshot(): PowerSnapshot
    fun monitorStore(background: Boolean = false): PowerMonitorStore
    fun configure(transform: (PowerMonitorConfig) -> PowerMonitorConfig): PowerSnapshot
    fun bind(binding: PowerBinding, roomName: String, expectedRevision: Long): PowerSnapshot
    fun unbind(): PowerSnapshot
    fun editLedger(expectedRevision: Long, transform: (PowerLedgerState) -> PowerLedgerState): PowerSnapshot =
        error("此存储不支持编辑账本")
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
            ledger = if (before.config.binding == binding) before.ledger else before.ledger?.let { PowerLedgerState(it.unitPrice) },
            bindingRevision = before.bindingRevision + 1,
        ))
    }

    override fun unbind(): PowerSnapshot = synchronized(storageLock) {
        val before = current()
        save(before.copy(
            config = before.config.copy(binding = null),
            roomName = "",
            monitor = PowerMonitorState(),
            ledger = before.ledger?.let { PowerLedgerState(it.unitPrice) },
            bindingRevision = before.bindingRevision + 1,
        ))
    }

    override fun editLedger(expectedRevision: Long, transform: (PowerLedgerState) -> PowerLedgerState): PowerSnapshot =
        synchronized(storageLock) {
            val before = current()
            check(before.bindingRevision == expectedRevision) { "宿舍绑定已变化，请重新打开记账" }
            val ledger = transform(before.ledger ?: PowerLedgerState())
            require(PowerLedger.price(ledger.unitPrice) != null) { "请输入有效电价" }
            ledger.entries.forEach(PowerLedger::validate)
            save(before.copy(ledger = ledger))
        }

    private fun updateLocked(
        binding: PowerBinding,
        revision: Long?,
        transform: (PowerMonitorState) -> PowerMonitorState,
    ): Boolean {
        val before = current()
        if (before.config.binding != binding || (revision != null && revision != before.bindingRevision)) return false
        // transform 可能发送通知，必须在绑定锁内且只执行一次；持久化失败也不得重试。
        val monitor = transform(before.monitor)
        val ledger = before.ledger?.let {
            PowerLedger.record(it, before.monitor.reading, monitor.reading, before.config.zone)
        }
        save(before.copy(monitor = monitor, ledger = ledger))
        return true
    }

    private fun current(): PowerSnapshot = cached ?: load().also { cached = it }

    private fun load(): PowerSnapshot {
        val primary = runCatching { persistence.read() }
        val current = primary.getOrNull()?.let { runCatching { PowerPersistenceCodec.decode(it) }.getOrNull() }
        val backup = runCatching { persistence.readBackup() }
        val previous = backup.getOrNull()?.let { runCatching { PowerPersistenceCodec.decode(it) }.getOrNull() }
        if (current != null) return recoverMissingHistory(current, previous)
        if (previous != null) {
            return previous.copy(recoveryMessage = "已从本机加密备份恢复数据，读数时间以记录为准。")
        }
        // 只有确实从未保存快照时才视为空。已有条目损坏时禁止以默认状态覆盖。
        if (primary.isSuccess && primary.getOrNull() == null && backup.isSuccess && backup.getOrNull() == null) {
            return PowerSnapshot()
        }
        throw java.io.IOException("本机电量数据无法读取，原文件已保留")
    }

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
