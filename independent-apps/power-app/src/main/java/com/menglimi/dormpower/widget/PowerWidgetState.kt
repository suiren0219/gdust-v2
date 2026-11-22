package com.menglimi.dormpower.widget

import android.content.Context

/**
 * 小部件刷新结果只用于显示，不写入电量快照；这里只存"正在刷新/失败原因"这类无敏感信息的状态。
 * 同一份状态由刷新 Action 与 Worker 共享，读取时把超时未结束的任务视为已结束，避免永久显示"刷新中"。
 */
internal object PowerWidgetState {
    private const val PREFS = "power_widget_state"
    private const val RUNNING = "running"
    private const val STARTED_AT = "started_at"
    private const val MESSAGE = "message"
    private const val STALE_AFTER_MS = 3 * 60_000L

    internal data class Snapshot(val running: Boolean, val message: String?)

    fun read(context: Context): Snapshot {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val running = prefs.getBoolean(RUNNING, false) &&
            System.currentTimeMillis() - prefs.getLong(STARTED_AT, 0L) < STALE_AFTER_MS
        return Snapshot(running, prefs.getString(MESSAGE, null)?.takeIf { it.isNotBlank() })
    }

    fun markRunning(context: Context) {
        prefs(context).edit()
            .putBoolean(RUNNING, true)
            .putLong(STARTED_AT, System.currentTimeMillis())
            .remove(MESSAGE)
            .apply()
    }

    /** 任务已排队但实际未开始（例如未绑定），直接给出可读原因。 */
    fun markMessage(context: Context, message: String) {
        prefs(context).edit().putBoolean(RUNNING, false).putString(MESSAGE, message).apply()
    }

    fun markFailed(context: Context) = markMessage(context, "刷新失败，点按重试")

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
