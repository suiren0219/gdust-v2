package com.menglimi.dormpower.power

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.menglimi.power.PowerBinding
import com.menglimi.power.PowerMonitorConfig
import com.menglimi.power.PowerMonitorState
import com.menglimi.power.PowerMonitorStore
import java.io.IOException

/** 构造不读磁盘；所有存储方法须在 IO 调度器调用。绝不读取原 App 的偏好文件。 */
internal class PowerStore(context: Context) : PowerRepository {
    private val application = context.applicationContext
    private val storage: PowerStorage get() = sharedStorage(application)

    override fun config(): PowerMonitorConfig = storage.config()
    override fun state(): PowerMonitorState = storage.state()
    override fun update(binding: PowerBinding, transform: (PowerMonitorState) -> PowerMonitorState): Boolean =
        storage.update(binding, transform)

    override fun snapshot(): PowerSnapshot = storage.snapshot()
    override fun monitorStore(background: Boolean): PowerMonitorStore = storage.monitorStore(background)
    override fun configure(transform: (PowerMonitorConfig) -> PowerMonitorConfig): PowerSnapshot = storage.configure(transform)
    override fun bind(binding: PowerBinding, roomName: String, expectedRevision: Long): PowerSnapshot = storage.bind(binding, roomName, expectedRevision)
    override fun unbind(): PowerSnapshot = storage.unbind()
    override fun editLedger(expectedRevision: Long, transform: (PowerLedgerState) -> PowerLedgerState): PowerSnapshot =
        storage.editLedger(expectedRevision, transform)

    fun monitor(requestedBinding: PowerBinding? = config().binding): PowerMonitor =
        PowerMonitor(storage.monitorStore(), com.menglimi.power.PowerApi(),
            onWarning = { warning -> PowerNotifications(application).send(warning) })

    fun cachedReading(): com.menglimi.power.PowerReading? = monitor().cachedReading()

    suspend fun refresh(): com.menglimi.power.PowerMonitorResult = monitor().query(force = true)

    private companion object {
        const val PREFERENCES = "dorm_power_secure_v1"
        const val MASTER_KEY = "dorm_power_master_v1"
        const val SNAPSHOT = "snapshot_v1"
        const val BACKUP = "snapshot_backup_v1"
        private val initializationLock = Any()
        @Volatile private var shared: PowerStorage? = null

        @Suppress("DEPRECATION")
        fun sharedStorage(context: Context): PowerStorage = shared ?: synchronized(initializationLock) {
            shared ?: PowerStorage(object : PowerPersistence {
                private val preferences by lazy {
                    val key = MasterKey.Builder(context, MASTER_KEY)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                    EncryptedSharedPreferences.create(
                        context, PREFERENCES, key,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                    )
                }

                override fun read(): String? = preferences.getString(SNAPSHOT, null)
                override fun readBackup(): String? = preferences.getString(BACKUP, null)
                override fun write(value: String) {
                    val next = PowerPersistenceCodec.decode(value)
                    val previous = runCatching { read() }.getOrNull()?.takeIf { text ->
                        runCatching { PowerPersistenceCodec.decode(text).config.binding == next.config.binding }
                            .getOrDefault(false)
                    }
                    val backup = if (next.config.binding == null) value else previous
                        ?: runCatching { readBackup() }.getOrNull()?.takeIf { text ->
                            runCatching { PowerPersistenceCodec.decode(text).config.binding == next.config.binding }
                                .getOrDefault(false)
                        } ?: value
                    // 同一次提交保存新快照和上次可读快照；换绑/解绑不留下旧宿舍备份。
                    if (!preferences.edit().putString(SNAPSHOT, value).putString(BACKUP, backup).commit()) {
                        throw IOException("电量数据保存失败")
                    }
                }
            }).also { shared = it }
        }
    }
}
