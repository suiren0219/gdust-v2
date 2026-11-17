package com.menglimi.dormpower.power

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.menglimi.dormpower.MainActivity
import com.menglimi.dormpower.R
import com.menglimi.power.PowerWarning
import com.menglimi.power.PowerWarningKind
import java.util.Locale
import kotlinx.coroutines.CancellationException

internal enum class NotificationAccess(val message: String) {
    READY("通知权限与电量预警渠道已开启"),
    PERMISSION_REQUIRED("尚未允许通知权限，电量查询与历史仍可使用"),
    APP_BLOCKED("系统已关闭本应用通知，请在通知设置中开启"),
    CHANNEL_BLOCKED("电量预警渠道已关闭，请进入渠道设置开启"),
    UNAVAILABLE("暂时无法读取通知设置，请重试"),
}

internal class PowerNotifications(context: Context) {
    private val application = context.applicationContext

    /** 提前建渠道，系统设置中始终有可管理的预警项；不覆盖用户修改过的重要性。 */
    fun ensureChannel(): Boolean = runCatching {
        val manager = application.getSystemService(NotificationManager::class.java) ?: return false
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL, "宿舍电量预警", NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "剩余电量偏低或近期掉电过快时提醒，也用于测试通知" })
        true
    }.getOrDefault(false)

    fun access(): NotificationAccess {
        if (!ensureChannel()) return NotificationAccess.UNAVAILABLE
        if (ContextCompat.checkSelfPermission(application, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return NotificationAccess.PERMISSION_REQUIRED
        return runCatching {
            val manager = application.getSystemService(NotificationManager::class.java)
                ?: return NotificationAccess.UNAVAILABLE
            when {
                !manager.areNotificationsEnabled() -> NotificationAccess.APP_BLOCKED
                manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE ->
                    NotificationAccess.CHANNEL_BLOCKED
                else -> NotificationAccess.READY
            }
        }.getOrDefault(NotificationAccess.UNAVAILABLE)
    }

    fun enabled(): Boolean = access() == NotificationAccess.READY

    fun settingsIntent(): Intent = if (access() == NotificationAccess.CHANNEL_BLOCKED) {
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, application.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL)
    } else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, application.packageName)

    fun clear() {
        application.getSystemService(NotificationManager::class.java)?.apply {
            cancel(NOTIFICATION)
            cancel(TEST_NOTIFICATION)
        }
    }

    /** 测试通知不伪造电量、不读账号、不修改预警冷却。 */
    fun sendTest(): Boolean = post(TEST_NOTIFICATION, "宿舍电量通知测试",
        "这是一条测试通知。真实预警仅在取得有效电量、达到阈值且冷却结束时发送。", System.currentTimeMillis())

    /** 仅实际交给已启用的系统通知渠道后返回 true；权限请求由 UI 负责。 */
    fun send(warning: PowerWarning): Boolean {
        val text = when (warning.kind) {
            PowerWarningKind.LOW -> "剩余 ${number(warning.current.odd)} 度，已到 ${number(warning.threshold)} 度预警线。"
            PowerWarningKind.DROP -> "剩余 ${number(warning.current.odd)} 度，近期每小时约减少 ${number(warning.dropRate ?: 0.0)} 度。"
        }
        return post(NOTIFICATION,
            if (warning.kind == PowerWarningKind.LOW) "宿舍电量偏低" else "宿舍掉电较快",
            text, warning.current.time)
    }

    private fun post(id: Int, title: String, text: String, time: Long): Boolean {
        if (!enabled()) return false
        return try {
            val manager = application.getSystemService(NotificationManager::class.java) ?: return false
            val intent = Intent(application, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("tab", 0)
            val pending = PendingIntent.getActivity(application, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(application, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setWhen(time)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            manager.notify(id, notification)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    private fun number(value: Double): String = String.format(Locale.CHINA, "%.1f", value)

    private companion object {
        const val CHANNEL = "dorm_power_alerts_v1"
        const val NOTIFICATION = 7101
        const val TEST_NOTIFICATION = 7102
    }
}
