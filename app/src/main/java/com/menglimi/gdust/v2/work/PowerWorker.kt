package com.menglimi.gdust.v2.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.menglimi.gdust.v2.MainActivity
import com.menglimi.gdust.v2.data.GdustApi
import com.menglimi.gdust.v2.ui.schedule.ScheduleTimes
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * 水电后台预警：按作息采样的链式调度。
 *
 * - 采样只在「作息窗口」内按用户间隔联网（广科作息 ±1h = 07:30–22:05）；
 *   夜间宿舍用电≈0，直接睡到下一个窗口开始，不再空转唤醒。
 * - 每次采样结束排下一次（OneTime 链），另有 6h 自愈心跳兜底：链被系统清掉也能补上。
 * - 低电量/掉电速率双预警、12 小时冷却、小时级采样去重逻辑不变。
 * - 水电接口无登录态，与门户会话完全独立。
 */
class PowerWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    private val prefs = ctx.getSharedPreferences("gdust_power", Context.MODE_PRIVATE)
    private val api = GdustApi()

    override suspend fun doWork(): Result {
        // 未明确允许后台采集、解绑或配置镜像不完整时，取消链路且不联网。
        val account = prefs.getString("account", null)
        val code = prefs.getInt("code", 0)
        val verify = prefs.getString("verify", null)
        val enabled = prefs.getBoolean("auto_collect", false)
        if (!PowerCollectionPolicy.hasWorkerConfig(enabled, account, code, verify)) {
            cancel(applicationContext)
            return Result.success()
        }
        // 先排下一次（REPLACE：链继续），保证链条无论本次成败都不断
        scheduleNextSample(applicationContext, prefs)
        val safeAccount = account.orEmpty()
        val safeVerify = verify.orEmpty()
        val name = prefs.getString("name", null).orEmpty()

        val now = System.currentTimeMillis()
        val last = prefs.getLong("last_sample", 0L)
        // 作息内按哥哥自选间隔采样；节流同时防心跳与链重复采样
        val intervalMin = prefs.getInt("interval", 60).coerceAtLeast(15)
        if (now - last < intervalMin * 60L * 1000) return Result.success()

        val detail = runCatching { api.powerDetail(safeAccount, code, safeVerify) }.getOrNull()
            ?: return Result.success()
        if (!isCollectionStillEnabled()) return Result.success()
        val odd = detail.odd ?: return Result.success()
        // 当前接口的同一份详情已经包含剩余电量与周耗；不再重复请求详情。
        com.menglimi.gdust.v2.data.Store(applicationContext).addSample(odd)
        val threshold = prefs.getFloat("threshold", 20f).toDouble()
        val dropLimit = prefs.getFloat("drop_rate", 5f).toDouble()
        val prev = prefs.getFloat("prev_odd", -1f).toDouble()

        val decision = PowerWarningPolicy.evaluate(
            odd = odd,
            previousOdd = prev,
            elapsedMs = now - last,
            lowThreshold = threshold,
            dropLimitPerHour = dropLimit,
            lowCooldownReady = cooldown("low"),
            dropCooldownReady = cooldown("drop"),
            hasPreviousSample = last > 0L,
        )
        val daysLeft = detail.weekUse.map { it.second }.takeIf { it.isNotEmpty() }
            ?.average()?.takeIf { it > 0.01 }?.let { odd / it }
        when (decision.kind) {
            PowerWarningKind.LOW -> {
                val suffix = daysLeft?.let { "，约还能用 %.0f 天".format(it) }.orEmpty()
                notify("宿舍电量不足", "$name 剩余 $odd 度（阈值 $threshold）$suffix，记得充值", 1)
                mark("low")
            }
            PowerWarningKind.DROP -> {
                val suffix = daysLeft?.let { "，约还能用 %.0f 天".format(it) }.orEmpty()
                notify("宿舍掉电异常", "$name 每小时掉电 %.1f 度$suffix，注意大功率电器".format(decision.dropRatePerHour), 2)
                mark("drop")
            }
            PowerWarningKind.NONE -> Unit
        }

        prefs.edit().putFloat("prev_odd", odd.toFloat()).putLong("last_sample", now).apply()
        prefs.edit().putString("widget_refresh_status", "").apply()
        // 电量变化 → 刷新全部桌面小组件
        runCatching {
            com.menglimi.gdust.v2.widget.WidgetRefresher.refreshAll(applicationContext)
        }
        return Result.success()
    }

    private fun isCollectionStillEnabled(): Boolean = PowerCollectionPolicy.hasWorkerConfig(
        prefs.getBoolean("auto_collect", false),
        prefs.getString("account", null),
        prefs.getInt("code", 0),
        prefs.getString("verify", null),
    )

    private fun cooldown(k: String) =
        System.currentTimeMillis() - prefs.getLong("${k}_warn", 0L) >= 12L * 60 * 60 * 1000

    private fun mark(k: String) = prefs.edit().putLong("${k}_warn", System.currentTimeMillis()).apply()

    private fun notify(title: String, text: String, id: Int) {
        val ctx = applicationContext
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "宿舍水电预警", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        nm.notify(id, NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi).setAutoCancel(true).build())
    }

    /** 自愈心跳：KEEP 策略补排采样链——链活着则原样保留，被系统清理则按当前作息重排。不联网。 */
    class PowerHeartbeatWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result {
            val context = applicationContext
            val prefs = context.getSharedPreferences("gdust_power", Context.MODE_PRIVATE)
            if (!PowerCollectionPolicy.hasWorkerConfig(
                    prefs.getBoolean("auto_collect", false),
                    prefs.getString("account", null),
                    prefs.getInt("code", 0),
                    prefs.getString("verify", null),
                )) {
                cancel(context)
                return Result.success()
            }
            scheduleNextSample(context, prefs, policy = ExistingWorkPolicy.KEEP)
            return Result.success()
        }
    }

    companion object {
        const val CHANNEL = "gdust_power_warn"
        private const val CHAIN = "gdust_power_chain"
        private const val HEARTBEAT = "gdust_power_heartbeat"
        private const val LEGACY_PERIODIC = "gdust_power"

        private val chainConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** 作息窗口（广科作息 ±1h：首节前 1h 到末节后 1h）。夜间宿舍用电≈0，采样推迟到窗口开始。 */
        internal fun awakeWindow(): Pair<LocalTime, LocalTime> {
            val start = LocalTime.parse(ScheduleTimes.slots.first().first).minusMinutes(60)
            val end = LocalTime.parse(ScheduleTimes.slots.last().second).plusMinutes(60)
            return if (end.isAfter(start)) start to end else start to start.plusHours(14)
        }

        /** 计算并排下一次采样：作息内=用户间隔；窗口外=睡到下一个窗口开始。返回延迟分钟。 */
        internal fun scheduleNextSample(
            ctx: Context,
            prefs: android.content.SharedPreferences,
            policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
        ) : Long {
            val now = LocalTime.now()
            val (start, end) = awakeWindow()
            val base = prefs.getInt("interval", 60).coerceAtLeast(15).toLong()
            val delayMin = if (!now.isBefore(start) && now.isBefore(end)) {
                base
            } else {
                // 距下一个窗口开始的分钟数（跨午夜：负值 +24h）
                val toStart = java.time.Duration.between(now, start).toMinutes()
                (if (toStart < 0) toStart + 24 * 60 else toStart)
            }.coerceIn(15L, 12L * 60)
            enqueueChain(ctx, delayMin, policy)
            return delayMin
        }

        /** 解绑或关闭预警时取消采样链与心跳（避免无绑定用户继续被唤醒）。 */
        fun cancel(ctx: Context) {
            val wm = WorkManager.getInstance(ctx)
            wm.cancelUniqueWork(CHAIN)
            wm.cancelUniqueWork(HEARTBEAT)
            wm.cancelUniqueWork(LEGACY_PERIODIC)
        }

        /** App 启动 / 绑定与间隔变更时调用。 */
        fun schedule(ctx: Context) {
            val wm = WorkManager.getInstance(ctx)
            val prefs = ctx.getSharedPreferences("gdust_power", Context.MODE_PRIVATE)
            if (!PowerCollectionPolicy.hasWorkerConfig(
                    prefs.getBoolean("auto_collect", false),
                    prefs.getString("account", null),
                    prefs.getInt("code", 0),
                    prefs.getString("verify", null),
                )) {
                cancel(ctx)
                return
            }
            // 迁移：清掉旧的 15 分钟周期任务（v6 及以前的调度）
            wm.cancelUniqueWork(LEGACY_PERIODIC)
            wm.enqueueUniquePeriodicWork(
                HEARTBEAT, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PowerHeartbeatWorker>(6, TimeUnit.HOURS).build(),
            )
            // 排链的第一环（延迟按当前作息计算）
            scheduleNextSample(ctx, prefs)
        }

        internal fun enqueueChain(ctx: Context, delayMin: Long, policy: ExistingWorkPolicy) {
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                CHAIN, policy,
                OneTimeWorkRequestBuilder<PowerWorker>()
                    .setInitialDelay(delayMin, TimeUnit.MINUTES)
                    .setConstraints(chainConstraints)
                    .build(),
            )
        }
    }
}
