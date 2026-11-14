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
        // 先排下一次（REPLACE：链继续），保证链条无论本次成败都不断
        scheduleNextSample(applicationContext, prefs)

        // 绑定信息在加密存储里，Worker 直接读普通缓存里的镜像（App 保存绑定时同步写）
        val account = prefs.getString("account", null).orEmpty()
        val code = prefs.getInt("code", 0)
        val verify = prefs.getString("verify", null).orEmpty()
        val name = prefs.getString("name", null).orEmpty()
        if (account.isEmpty() || code == 0 || verify.isEmpty()) return Result.success()

        val now = System.currentTimeMillis()
        val last = prefs.getLong("last_sample", 0L)
        // 作息内按哥哥自选间隔采样；节流同时防心跳与链重复采样
        val intervalMin = prefs.getInt("interval", 60).coerceAtLeast(15)
        if (now - last < intervalMin * 60L * 1000) return Result.success()

        val odd = runCatching { api.powerOdd(account, code, verify) }.getOrNull() ?: return Result.success()
        // 记录小时采样点（供 App 内折线图）
        com.menglimi.gdust.v2.data.Store(applicationContext).addSample(odd)
        val threshold = prefs.getFloat("threshold", 20f).toDouble()
        val dropLimit = prefs.getFloat("drop_rate", 5f).toDouble()
        val prev = prefs.getFloat("prev_odd", -1f).toDouble()

        // 预计可用天数：随预警一起带出，比单纯“电量低了”更可行动
        val weekUse = runCatching {
            api.powerDetail(account, code, verify).weekUse.map { it.second }
        }.getOrDefault(emptyList())
        val daysLeft = weekUse.takeIf { it.isNotEmpty() }
            ?.let { list -> list.average().takeIf { it > 0.01 }?.let { avg -> odd / avg } }

        if (cooldown("low") && odd <= threshold) {
            val suffix = daysLeft?.let { "，约还能用 %.0f 天".format(it) }.orEmpty()
            notify("宿舍电量不足", "$name 剩余 $odd 度（阈值 $threshold）$suffix，记得充值", 1)
            mark("low")
        } else if (cooldown("drop") && prev >= 0 && last > 0) {
            val hours = ((now - last) / 3600000.0).coerceAtLeast(0.25)
            val rate = (prev - odd) / hours
            if (rate >= dropLimit) {
                val suffix = daysLeft?.let { "，约还能用 %.0f 天".format(it) }.orEmpty()
                notify("宿舍掉电异常", "$name 每小时掉电 %.1f 度$suffix，注意大功率电器".format(rate), 2)
                mark("drop")
            }
        }

        prefs.edit().putFloat("prev_odd", odd.toFloat()).putLong("last_sample", now).apply()
        // 电量变化 → 刷新全部桌面小组件
        runCatching {
            com.menglimi.gdust.v2.widget.WidgetRefresher.refreshAll(applicationContext)
        }
        return Result.success()
    }

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
            scheduleNextSample(
                applicationContext,
                applicationContext.getSharedPreferences("gdust_power", Context.MODE_PRIVATE),
                policy = ExistingWorkPolicy.KEEP,
            )
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

        /** App 启动 / 绑定与间隔变更时调用。 */
        fun schedule(ctx: Context) {
            val wm = WorkManager.getInstance(ctx)
            // 迁移：清掉旧的 15 分钟周期任务（v6 及以前的调度）
            wm.cancelUniqueWork(LEGACY_PERIODIC)
            wm.enqueueUniquePeriodicWork(
                HEARTBEAT, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PowerHeartbeatWorker>(6, TimeUnit.HOURS).build(),
            )
            // 排链的第一环（延迟按当前作息计算）
            scheduleNextSample(ctx, ctx.getSharedPreferences("gdust_power", Context.MODE_PRIVATE))
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
