package com.menglimi.gdust.v2.work

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** 只处理本应用的勿扰规则；通知动作附带课时，过期卡片不能打开一个永不结束的勿扰。 */
class ClassDndToggleReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.menglimi.gdust.v2.CLASS_DND_TOGGLE"
        const val EXTRA_START = "class_start"
        const val EXTRA_END = "class_end"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            ACTION -> {
                val message = ClassDndAlarms.startForLesson(
                    ctx, intent.getLongExtra(EXTRA_START, 0), intent.getLongExtra(EXTRA_END, 0),
                )
                Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
            }
            ClassDndAlarms.ACTION_BOUNDARY,
            NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED -> ClassDndAlarms.reschedule(ctx)
            NotificationManager.ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED -> ClassDndAlarms.onRuleStatus(ctx, intent)
        }
    }
}
