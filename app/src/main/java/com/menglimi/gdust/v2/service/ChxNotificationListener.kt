package com.menglimi.gdust.v2.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.menglimi.gdust.v2.data.ChxNotification
import com.menglimi.gdust.v2.data.Store

class ChxNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != ChxNotification.PACKAGE || !sbn.isClearable) return
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val store = Store(applicationContext)
        if (!store.chxNotificationsEnabled) return
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val body = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val todo = ChxNotification.todo(title, body) ?: return
        store.addChxNotification("${sbn.key}:${todo.hashCode()}", todo)
    }
}
