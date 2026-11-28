package com.menglimi.kedian.power

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.menglimi.power.PowerWarning
import com.menglimi.power.PowerWarningKind
import java.util.Locale
import kotlinx.coroutines.CancellationException

internal class PowerNotifications(context: Context) {
    private val application = context.applicationContext

    /** 仅实际交给已启用的系统通知渠道后返回 true；权限请求由 UI 负责。 */
    fun send(warning: PowerWarning): Boolean {
        if (ContextCompat.checkSelfPermission(application, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return false
        val manager = application.getSystemService(NotificationManager::class.java) ?: return false
        if (!manager.areNotificationsEnabled()) return false
        return try {
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL, "宿舍电量预警", NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "剩余电量偏低或近期掉电过快时提醒" })
            if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return false
            val intent = Intent().setClassName(application, "com.menglimi.kedian.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("tab", 1)
            val pending = PendingIntent.getActivity(application, 7101, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val text = when (warning.kind) {
                PowerWarningKind.LOW -> "剩余 ${number(warning.current.odd)} 度，已到 ${number(warning.threshold)} 度预警线。"
                PowerWarningKind.DROP -> "剩余 ${number(warning.current.odd)} 度，近期每小时约减少 ${number(warning.dropRate ?: 0.0)} 度。"
            }
            val notification = NotificationCompat.Builder(application, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(if (warning.kind == PowerWarningKind.LOW) "宿舍电量偏低" else "宿舍掉电较快")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                // 与采样、冷却锚点保持同一 Unix 毫秒时间，避免展示发送时重置的时间。
                .setWhen(warning.current.time)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build()
            manager.notify(NOTIFICATION, notification)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    private fun number(value: Double): String = String.format(Locale.CHINA, "%.1f", value)

    private companion object {
        const val CHANNEL = "kedian_power_alerts_v1"
        const val NOTIFICATION = 7101
    }
}
