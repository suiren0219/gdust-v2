package com.menglimi.gdust.v2.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.menglimi.gdust.v2.MainActivity
import com.menglimi.gdust.v2.data.GdustApi
import java.util.concurrent.TimeUnit

/**
 * 水电后台预警：15 分钟巡检 / 50 分钟采样节流 / 低电量与掉电速率双预警 / 12 小时冷却。
 * 水电接口无登录态，与门户会话完全独立。
 */
class PowerWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    private val prefs = ctx.getSharedPreferences("gdust_power", Context.MODE_PRIVATE)
    private val api = GdustApi()

    override suspend fun doWork(): Result {
        // 绑定信息在加密存储里，Worker 直接读普通缓存里的镜像（App 保存绑定时同步写）
        val account = prefs.getString("account", null).orEmpty()
        val code = prefs.getInt("code", 0)
        val verify = prefs.getString("verify", null).orEmpty()
        val name = prefs.getString("name", null).orEmpty()
        if (account.isEmpty() || code == 0 || verify.isEmpty()) return Result.success()

        val now = System.currentTimeMillis()
        val last = prefs.getLong("last_sample", 0L)
        if (now - last < 50L * 60 * 1000) return Result.success()

        val odd = runCatching { api.powerOdd(account, code, verify) }.getOrNull() ?: return Result.success()
        val threshold = prefs.getFloat("threshold", 20f).toDouble()
        val dropLimit = prefs.getFloat("drop_rate", 5f).toDouble()
        val prev = prefs.getFloat("prev_odd", -1f).toDouble()

        if (cooldown("low") && odd <= threshold) {
            notify("宿舍电量不足", "$name 剩余 $odd 度（阈值 $threshold），记得充值", 1)
            mark("low")
        } else if (cooldown("drop") && prev >= 0 && last > 0) {
            val hours = ((now - last) / 3600000.0).coerceAtLeast(0.25)
            val rate = (prev - odd) / hours
            if (rate >= dropLimit) {
                notify("宿舍掉电异常", "$name 每小时掉电 %.1f 度，注意大功率电器".format(rate), 2)
                mark("drop")
            }
        }

        prefs.edit().putFloat("prev_odd", odd.toFloat()).putLong("last_sample", now).apply()
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

    companion object {
        const val CHANNEL = "gdust_power_warn"

        fun schedule(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "gdust_power", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PowerWorker>(15, TimeUnit.MINUTES).build(),
            )
        }
    }
}
