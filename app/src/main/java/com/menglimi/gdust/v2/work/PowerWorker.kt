package com.menglimi.gdust.v2.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.gson.Gson
import com.menglimi.gdust.v2.App
import com.menglimi.gdust.v2.MainActivity
import com.menglimi.gdust.v2.data.GdustApi
import com.menglimi.gdust.v2.data.PowerDetail
import com.menglimi.gdust.v2.data.PowerQueryPolicy
import com.menglimi.gdust.v2.data.PowerQueryPreset
import com.menglimi.gdust.v2.data.PowerSample
import com.menglimi.gdust.v2.data.PowerScheduleAction
import com.menglimi.gdust.v2.data.ScheduleSyncPolicy
import com.menglimi.gdust.v2.data.Store
import com.menglimi.gdust.v2.ui.schedule.ScheduleTimes
import java.security.MessageDigest
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 同一绑定的最新详情；前台读缓存不再重复记录采样。 */
data class PowerReading(val detail: PowerDetail, val updatedAt: Long)

/** 按预设与作息串行查询，并复用一次性 chain 维护学校会话、同步课表。 */
class PowerWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        try {
            try {
                query(applicationContext, background = true)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // 电量失败也按当前节奏等待，不阻止学校同步。
            }
            // 学校网络请求不在电量 queryMutex 内；夜间只跳过电量查询。
            ScheduleSyncWorker.syncIfDue(applicationContext)
        } finally {
            // 续接当前 generation，避免配置重排后旧 Worker 再追加重复唤醒。
            if (!isStopped) scheduleAfterRun(applicationContext, powerChain = true,
                generation = inputData.getString(GENERATION))
        }
        return Result.success()
    }

    class PowerHeartbeatWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result {
            schedule(applicationContext)
            return Result.success()
        }
    }

    companion object {
        const val CHANNEL = "gdust_power_warn"
        private const val CHAIN = "gdust_power_chain"
        private const val HEARTBEAT = "gdust_power_heartbeat"
        private const val LEGACY_PERIODIC = "gdust_power"
        private const val GENERATION = "generation"
        private val queryMutex = Mutex()
        private val scheduleLock = Any()
        private val gson = Gson()
        private val api by lazy { GdustApi() }
        private val chainConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build()

        private data class Binding(val account: String, val code: Int, val verify: String) {
            val key: String get() = digest(gson.toJson(listOf(account, code, verify)))
        }

        private fun preferences(ctx: Context) = ctx.getSharedPreferences("gdust_power", Context.MODE_PRIVATE)
        private fun binding(prefs: SharedPreferences): Binding? {
            val account = prefs.getString("account", "").orEmpty()
            val code = prefs.getInt("code", 0)
            val verify = prefs.getString("verify", "").orEmpty()
            return if (account.isBlank() || code <= 0 || verify.isBlank()) null else Binding(account, code, verify)
        }
        private fun preset(prefs: SharedPreferences) = PowerQueryPreset.fromKey(prefs.getString("query_preset", null))
        private fun digest(value: String) = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        private fun samples(ctx: Context) = Store(ctx).loadSamples().map { PowerSample(it.time, it.odd) }
        private fun plan(ctx: Context, prefs: SharedPreferences, now: Long) = PowerQueryPolicy.plan(
            preset(prefs), prefs.getInt("interval", 60), samples(ctx), now,
            prefs.getFloat("threshold", 20f).toDouble(), prefs.getFloat("drop_rate", 5f).toDouble(),
        )

        fun cachedReading(ctx: Context): PowerReading? = cachedReading(preferences(ctx))

        private fun cachedReading(prefs: SharedPreferences): PowerReading? {
            val current = binding(prefs) ?: return null
            if (prefs.getString("cached_binding", null) != current.key) return null
            val time = prefs.getLong("last_sample", 0L).takeIf { it > 0 } ?: return null
            val json = prefs.getString("cached_detail", null) ?: return null
            val detail = runCatching { gson.fromJson(json, PowerDetail::class.java) }.getOrNull() ?: return null
            if (detail.odd?.isFinite() != true) return null
            return PowerReading(detail, time)
        }

        /**
         * 前台与 Worker 共用一次详情请求和采样写入，锁内重新检查新鲜度。
         * force 只跳过新鲜度/作息节流；同一时刻已在执行的查询结果可以复用。
         */
        suspend fun query(ctx: Context, force: Boolean = false, background: Boolean = false): PowerReading? {
            val requestedAt = System.currentTimeMillis()
            return queryMutex.withLock {
                val prefs = preferences(ctx)
                val requestedBinding = binding(prefs) ?: return@withLock null
                val cached = cachedReading(prefs)
                val now = System.currentTimeMillis()
                val queryPlan = plan(ctx, prefs, now)
                if (!force) {
                    if (queryPlan.intervalMinutes == null) return@withLock cached
                    val (start, end) = awakeWindow()
                    if (background && !PowerQueryPolicy.inWindow(now, start, end, ZoneId.systemDefault())) return@withLock cached
                    if (!PowerQueryPolicy.queryDue(now, prefs.getLong("last_sample", 0L),
                            if (background) prefs.getLong("last_attempt", 0L) else 0L, queryPlan.intervalMinutes)) {
                        if (cached != null || background) return@withLock cached
                    }
                } else if (cached != null && cached.updatedAt >= requestedAt) {
                    return@withLock cached
                }
                prefs.edit().putLong("last_attempt", now).apply()
                // powerOdd 内部也调用 powerDetail；只取一次详情，避免过去每轮的双请求。
                val detail = api.powerDetail(requestedBinding.account, requestedBinding.code, requestedBinding.verify)
                if (binding(prefs) != requestedBinding) throw CancellationException("宿舍绑定已变更")
                if (background && preset(prefs) == PowerQueryPreset.MANUAL) return@withLock cachedReading(prefs)
                val odd = detail.odd?.takeIf { it.isFinite() } ?: error("接口未返回有效电量，请稍后再试")
                val sampledAt = System.currentTimeMillis()
                val history = samples(ctx)
                val current = PowerSample(sampledAt, odd)
                val previous = history.filter { it.odd.isFinite() && sampledAt - it.time >= 15 * 60_000L }
                    .maxByOrNull { it.time }
                Store(ctx).addSample(odd)
                prefs.edit()
                    .putFloat("prev_odd", odd.toFloat()).putLong("last_sample", sampledAt)
                    .putString("cached_detail", gson.toJson(detail)).putString("cached_binding", requestedBinding.key).apply()
                checkWarnings(ctx, prefs, previous, current, history + current)
                // 只有实际取得新读数才调整已有到期时间；重开页面复用缓存不会重排。
                if (!background) schedule(ctx, afterQuery = true)
                PowerReading(detail, sampledAt)
            }
        }

        private fun checkWarnings(
            ctx: Context, prefs: SharedPreferences, previous: PowerSample?, current: PowerSample, history: List<PowerSample>,
        ) {
            val threshold = prefs.getFloat("threshold", 20f).toDouble().takeIf { it.isFinite() && it >= 0 } ?: 20.0
            val dropLimit = prefs.getFloat("drop_rate", 5f).toDouble().takeIf { it.isFinite() && it > 0 } ?: 5.0
            val name = prefs.getString("name", "").orEmpty().ifBlank { "宿舍" }
            val days = PowerQueryPolicy.estimate(history, current.time).daysLeft
            val suffix = days?.let { "，按近期用量粗估约 %.1f 天（仅供参考）".format(it) }.orEmpty()
            fun cooldown(key: String) = current.time - prefs.getLong("${key}_warn", 0L) >= 12 * 60 * 60_000L
            fun send(key: String, title: String, text: String, id: Int) {
                // 没有通知权限时仍保留成功采样，不把预警发送失败变成网络失败。
                if (runCatching { notify(ctx, title, text, id) }.getOrDefault(false)) {
                    prefs.edit().putLong("${key}_warn", current.time).apply()
                }
            }
            if (current.odd <= threshold && cooldown("low")) {
                send("low", "宿舍电量不足", "$name 剩余 %.1f 度（阈值 %.1f）%s，记得充值".format(current.odd, threshold, suffix), 1)
            } else {
                val rate = PowerQueryPolicy.dropRate(previous, current)
                if (rate != null && rate >= dropLimit && cooldown("drop")) {
                    send("drop", "宿舍掉电异常", "$name 每小时掉电 %.1f 度%s，注意大功率电器".format(rate, suffix), 2)
                }
            }
        }

        private fun notify(ctx: Context, title: String, text: String, id: Int): Boolean {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "宿舍水电预警", NotificationManager.IMPORTANCE_DEFAULT))
            if (!nm.areNotificationsEnabled()) return false
            val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            nm.notify(id, NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(pi).setAutoCancel(true).build())
            return true
        }

        /** 保留原有后台作息窗口；不据此假设夜间宿舍不耗电。手动查询不受限制。 */
        internal fun awakeWindow(): Pair<LocalTime, LocalTime> {
            val start = LocalTime.parse(ScheduleTimes.slots.first().first).minusMinutes(60)
            val end = LocalTime.parse(ScheduleTimes.slots.last().second).plusMinutes(60)
            return start to end
        }

        internal fun scheduleNextSample(
            ctx: Context, prefs: SharedPreferences, policy: ExistingWorkPolicy, now: Long, syncAt: Long?,
        ): Long? {
            if (binding(prefs) == null || preset(prefs) == PowerQueryPreset.MANUAL) return null
            val (start, end) = awakeWindow()
            val powerAt = PowerQueryPolicy.nextSampleAt(now, prefs.getLong("last_sample", 0L),
                prefs.getLong("last_attempt", 0L), plan(ctx, prefs, now).intervalMinutes, start, end, ZoneId.systemDefault())
            val at = ScheduleSyncPolicy.nextWakeAt(powerAt, syncAt) ?: return null
            val generation = if (policy == ExistingWorkPolicy.REPLACE) UUID.randomUUID().toString()
                else prefs.getString("scheduled_generation", null) ?: UUID.randomUUID().toString()
            prefs.edit().putString("scheduled_generation", generation).apply()
            WorkManager.getInstance(ctx).enqueueUniqueWork(CHAIN, policy,
                OneTimeWorkRequestBuilder<PowerWorker>()
                    .setInputData(workDataOf(GENERATION to generation))
                    .setInitialDelay((at - now).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                    .setConstraints(chainConstraints).build())
            return at
        }

        private fun cancelPowerChain(ctx: Context) {
            val wm = WorkManager.getInstance(ctx)
            wm.cancelUniqueWork(CHAIN)
            wm.cancelUniqueWork(HEARTBEAT)
            wm.cancelUniqueWork(LEGACY_PERIODIC)
            preferences(ctx).edit().remove("scheduled_config").remove("scheduled_generation").apply()
        }

        fun cancel(ctx: Context) = synchronized(scheduleLock) {
            cancelPowerChain(ctx)
            // 只取消电量时，不能丢掉已开启的学校自动同步。
            ScheduleSyncWorker.scheduleStandalone(ctx, ExistingWorkPolicy.KEEP)
            Unit
        }

        /** 同一配置 KEEP；绑定、预设、间隔、阈值或学校同步开关改变才重排。 */
        fun schedule(ctx: Context) = schedule(ctx, afterQuery = false)

        private fun schedule(ctx: Context, afterQuery: Boolean) = synchronized(scheduleLock) {
            scheduleLocked(ctx, afterQuery)
        }

        internal fun scheduleAfterRun(ctx: Context, powerChain: Boolean, generation: String?) = synchronized(scheduleLock) {
            val current = if (powerChain) generation != null &&
                generation == preferences(ctx).getString("scheduled_generation", null)
                else ScheduleSyncWorker.isCurrentRun(ctx, generation)
            if (current) scheduleLocked(ctx, afterQuery = false, continuingPowerChain = powerChain)
        }

        private fun scheduleLocked(ctx: Context, afterQuery: Boolean, continuingPowerChain: Boolean? = null) {
            val prefs = preferences(ctx)
            val current = binding(prefs)
            val now = System.currentTimeMillis()
            val repo = (ctx.applicationContext as App).repo
            val syncAt = repo.nextAutomaticSyncAt(now)
            val threshold = prefs.getFloat("threshold", 20f).takeIf { it.isFinite() && it >= 0 } ?: 20f
            val dropRate = prefs.getFloat("drop_rate", 5f).takeIf { it.isFinite() && it > 0 } ?: 5f
            val signature = digest(gson.toJson(listOf("power-v3", current?.key, preset(prefs).key,
                prefs.getInt("interval", 60), threshold, dropRate, repo.store.autoSyncEnabled, syncAt != null)))
            val action = PowerQueryPolicy.scheduleAction(current != null, preset(prefs),
                signature != prefs.getString("scheduled_config", null))
            if (action == PowerScheduleAction.CANCEL) {
                cancelPowerChain(ctx)
                ScheduleSyncWorker.scheduleStandalone(ctx,
                    if (continuingPowerChain == false) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP)
                return
            }
            val wm = WorkManager.getInstance(ctx)
            wm.cancelUniqueWork(LEGACY_PERIODIC)
            wm.cancelUniqueWork(HEARTBEAT) // 历史修复心跳不再额外唤醒。
            ScheduleSyncWorker.cancelStandalone(ctx)
            scheduleNextSample(ctx, prefs, when {
                continuingPowerChain == true -> ExistingWorkPolicy.APPEND_OR_REPLACE
                action == PowerScheduleAction.REPLACE || afterQuery -> ExistingWorkPolicy.REPLACE
                else -> ExistingWorkPolicy.KEEP
            }, now, syncAt)
            if (action == PowerScheduleAction.REPLACE) prefs.edit().putString("scheduled_config", signature).apply()
        }
    }
}
