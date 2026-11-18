package com.menglimi.power

import java.time.Clock
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 由应用层适配 SharedPreferences、数据库或其他持久化；核心不读取 Android。 */
interface PowerMonitorStore {
    /** 返回当前绑定与查询配置的不可变快照。 */
    fun config(): PowerMonitorConfig

    /** 返回当前绑定对应的状态；适配器应在自己的 storageLock 下读取。 */
    fun state(): PowerMonitorState

    /**
     * 在适配器的 storageLock 内重新核验 binding，再把 transform 应用于最新状态。
     * 返回 false 表示绑定已经改变；此时不得调用 transform 或写入旧绑定数据。
     * transform 必须同步执行且仅执行一次，不能在失败后重试（其中可能发送预警）。
     * 配置绑定切换和清理历史必须复用同一个锁。
     */
    fun update(binding: PowerBinding, transform: (PowerMonitorState) -> PowerMonitorState): Boolean
}

data class PowerMonitorConfig(
    val binding: PowerBinding? = null,
    val preset: PowerQueryPreset = PowerQueryPreset.BALANCED,
    val customIntervalMinutes: Int = 60,
    val lowThreshold: Double = 20.0,
    val dropRateThreshold: Double = 5.0,
    /** null 表示不限制查询时间；非 null 时仅 background 查询受窗口限制。 */
    val awakeWindow: Pair<LocalTime, LocalTime>? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    val effectiveLowThreshold: Double
        get() = lowThreshold.takeIf { it.isFinite() && it >= 0 } ?: 20.0
    val effectiveDropRateThreshold: Double
        get() = dropRateThreshold.takeIf { it.isFinite() && it > 0 } ?: 5.0
}

data class PowerMonitorState(
    val cachedBindingKey: String? = null,
    val reading: PowerReading? = null,
    val lastSampleAt: Long = 0L,
    val lastAttemptAt: Long = 0L,
    val samples: List<PowerSample> = emptyList(),
    val lowWarnAt: Long = 0L,
    val dropWarnAt: Long = 0L,
)

data class PowerMonitorResult(
    val reading: PowerReading?,
    /** true 仅表示本次拿到新详情并持久化了新采样。 */
    val sampled: Boolean = false,
)

enum class PowerWarningKind { LOW, DROP }

data class PowerWarning(
    val kind: PowerWarningKind,
    val current: PowerSample,
    /** LOW 为低电量阈值，DROP 为每小时掉电阈值。 */
    val threshold: Double,
    val dropRate: Double? = null,
    val daysLeft: Double? = null,
)

/** 纯报警决策；是否实际发出通知由 onWarning 返回值决定。 */
object PowerWarningPolicy {
    const val COOLDOWN_MILLIS: Long = 12 * 60 * 60_000L

    fun decide(
        previous: PowerSample?,
        current: PowerSample,
        history: List<PowerSample>,
        lowThreshold: Double,
        dropRateThreshold: Double,
        lowWarnAt: Long,
        dropWarnAt: Long,
        evaluatedAt: Long = current.time,
    ): PowerWarning? {
        if (current.time <= 0 || !current.odd.isFinite()) return null
        val low = lowThreshold.takeIf { it.isFinite() && it >= 0 } ?: 20.0
        val dropLimit = dropRateThreshold.takeIf { it.isFinite() && it > 0 } ?: 5.0
        val days = PowerQueryPolicy.estimate(history, current.time).daysLeft
        // 与旧 Worker 一致：低电量且冷却已过时优先发 LOW；否则仍可检查 DROP。
        if (current.odd <= low && evaluatedAt - lowWarnAt >= COOLDOWN_MILLIS) {
            return PowerWarning(PowerWarningKind.LOW, current, low, daysLeft = days)
        }
        val rate = PowerQueryPolicy.dropRate(previous, current)
        return if (rate != null && rate >= dropLimit && evaluatedAt - dropWarnAt >= COOLDOWN_MILLIS) {
            PowerWarning(PowerWarningKind.DROP, current, dropLimit, dropRate = rate, daysLeft = days)
        } else null
    }
}

/**
 * 纯 JVM 电费监控协调器。调度、通知渠道和 Android 生命周期由调用方负责。
 * 同一 JVM 内所有实例共享 queryMutex，因此前台、Worker 或多个 App 适配器不会重复请求。
 */
class PowerMonitor(
    private val store: PowerMonitorStore,
    private val source: PowerDetailSource = PowerApi(),
    private val clock: Clock = Clock.systemDefaultZone(),
    private val onWarning: (PowerWarning) -> Boolean = { false },
) {
    fun cachedReading(): PowerReading? {
        val config = store.config()
        val binding = config.binding?.takeIf { it.isValid } ?: return null
        val state = store.state()
        if (store.config().binding != binding) return null
        return cachedReadingFrom(binding, state)
    }

    /**
     * force 跳过新鲜度、作息与手动节流，但相同时间内已完成的请求仍复用其结果。
     * background 成功采样后由调用方依据 sampled 决定调度下一个任务。
     */
    suspend fun query(force: Boolean = false, background: Boolean = false): PowerMonitorResult {
        val requestedAt = clock.millis()
        return queryMutex.withLock {
            val initialConfig = store.config()
            val requestedBinding = initialConfig.binding?.takeIf { it.isValid }
                ?: return@withLock PowerMonitorResult(null)
            val initialState = store.state()
            if (store.config().binding != requestedBinding) throw bindingChanged()
            val cached = cachedReadingFrom(requestedBinding, initialState)
            val now = clock.millis()
            val plan = PowerQueryPolicy.plan(
                initialConfig.preset,
                initialConfig.customIntervalMinutes,
                initialState.samples,
                now,
                initialConfig.effectiveLowThreshold,
                initialConfig.effectiveDropRateThreshold,
            )
            if (!force) {
                if (plan.intervalMinutes == null) return@withLock PowerMonitorResult(cached)
                val awake = initialConfig.awakeWindow
                if (background && awake != null &&
                    !PowerQueryPolicy.inWindow(now, awake.first, awake.second, initialConfig.zone)
                ) return@withLock PowerMonitorResult(cached)
                val due = PowerQueryPolicy.queryDue(
                    now,
                    initialState.lastSampleAt,
                    if (background) initialState.lastAttemptAt else 0L,
                    plan.intervalMinutes,
                )
                if (!due && (cached != null || background)) return@withLock PowerMonitorResult(cached)
            } else if (cached != null && cached.updatedAt >= requestedAt) {
                return@withLock PowerMonitorResult(cached)
            }

            // 失败请求也应锚定下一次后台查询；适配器会在锁内再次检查绑定。
            if (!store.update(requestedBinding) { it.copy(lastAttemptAt = now) }) {
                throw bindingChanged()
            }
            val detail = source.detail(requestedBinding)
            val afterConfig = store.config()
            if (afterConfig.binding != requestedBinding) throw bindingChanged()
            // 后台任务在请求过程中被切换为手动时，丢弃该后台结果；前台 force 不受影响。
            if (background && afterConfig.preset == PowerQueryPreset.MANUAL) {
                return@withLock PowerMonitorResult(cachedReading())
            }
            val odd = detail.odd?.takeIf { it.isFinite() }
                ?: error("接口未返回有效电量，请稍后再试")
            val sampledAt = clock.millis()
            val current = PowerSample(sampledAt, odd)
            val reading = PowerReading(detail, sampledAt)
            var previous: PowerSample? = null
            var warningHistory: List<PowerSample> = emptyList()
            var warningLowAt = 0L
            var warningDropAt = 0L
            val persisted = store.update(requestedBinding) { latest ->
                previous = latest.samples
                    .filter { it.odd.isFinite() && sampledAt - it.time >= 15 * 60_000L }
                    .maxByOrNull { it.time }
                warningHistory = latest.samples + current
                warningLowAt = latest.lowWarnAt
                warningDropAt = latest.dropWarnAt
                latest.copy(
                    cachedBindingKey = requestedBinding.key,
                    reading = reading,
                    lastSampleAt = sampledAt,
                    samples = PowerQueryPolicy.appendSample(latest.samples, current),
                )
            }
            if (!persisted) throw bindingChanged()

            val warning = PowerWarningPolicy.decide(
                previous = previous,
                current = current,
                history = warningHistory,
                lowThreshold = afterConfig.effectiveLowThreshold,
                dropRateThreshold = afterConfig.effectiveDropRateThreshold,
                lowWarnAt = warningLowAt,
                dropWarnAt = warningDropAt,
            )
            if (warning != null) {
                // 通知发送与绑定核验共用存储锁，避免旧宿舍结果在换绑定后发送预警。
                if (!store.update(requestedBinding) { state ->
                    val sent = try {
                        onWarning(warning)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        false
                    }
                    if (!sent) state else when (warning.kind) {
                        PowerWarningKind.LOW -> state.copy(lowWarnAt = current.time)
                        PowerWarningKind.DROP -> state.copy(dropWarnAt = current.time)
                    }
                }) throw bindingChanged()
            }
            PowerMonitorResult(reading, sampled = true)
        }
    }

    /** 权限开启后仅复查最近 15 分钟的有效读数，不额外请求服务；发送失败不消耗冷却。 */
    suspend fun recheckWarnings(): Boolean = queryMutex.withLock {
        val config = store.config()
        val binding = config.binding?.takeIf { it.isValid } ?: return@withLock false
        val now = clock.millis()
        var sent = false
        if (!store.update(binding) { state ->
            val reading = cachedReadingFrom(binding, state)
            if (reading == null || now - reading.updatedAt !in 0..15 * 60_000L) state
            else {
                val current = PowerSample(reading.updatedAt, requireNotNull(reading.detail.odd))
                val previous = state.samples.filter { current.time - it.time >= 15 * 60_000L }
                    .maxByOrNull { it.time }
                val warning = PowerWarningPolicy.decide(previous, current, state.samples,
                    config.effectiveLowThreshold, config.effectiveDropRateThreshold, state.lowWarnAt, state.dropWarnAt,
                    evaluatedAt = now)
                if (warning == null) state else {
                    sent = try { onWarning(warning) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { false }
                    if (!sent) state else when (warning.kind) {
                        PowerWarningKind.LOW -> state.copy(lowWarnAt = now)
                        PowerWarningKind.DROP -> state.copy(dropWarnAt = now)
                    }
                }
            }
        }) throw bindingChanged()
        sent
    }

    private fun cachedReadingFrom(binding: PowerBinding, state: PowerMonitorState): PowerReading? =
        state.reading?.takeIf {
            state.cachedBindingKey == binding.key &&
                it.updatedAt > 0 && it.detail.odd?.isFinite() == true
        }

    private fun bindingChanged(): CancellationException =
        CancellationException("宿舍绑定已变更")

    private companion object {
        /** 跨 PowerMonitor / PowerMonitorStore 实例共享；应用侧存储锁仍负责配置与状态原子性。 */
        val queryMutex = Mutex()
    }
}
