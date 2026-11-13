package com.menglimi.gdust.v2.work

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * 「上课勿扰」动作：锁屏/息屏焦点通知卡上的按钮。
 * 只取消本次课程提醒、把本 App 的课程提醒渠道降到 IMPORTANCE_LOW（无需系统 DND 权限，
 * 不动用户全局勿扰状态）；下次课前提醒发出前会由 [LessonReminderWorker] 恢复渠道重要性。
 */
class ClassDndToggleReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.menglimi.gdust.v2.CLASS_DND_TOGGLE"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        runCatching {
            nm.createNotificationChannel(
                android.app.NotificationChannel(
                    LessonReminderWorker.CHANNEL, "课程提醒", NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "上课勿扰期间静音" }
            )
        }
        runCatching { nm.cancel(21); nm.cancel(23) }
        runCatching { Toast.makeText(ctx, "已静音课程提醒", Toast.LENGTH_SHORT).show() }
    }
}
