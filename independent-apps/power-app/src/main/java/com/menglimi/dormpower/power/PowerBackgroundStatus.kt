package com.menglimi.dormpower.power

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PowerBackgroundOutcome(val description: String) {
    NEVER("尚未执行"), RUNNING("执行已开始，尚未确认完成"), SUCCESS("已查询并保存"),
    FAILURE("查询失败，保留原记录"), SKIPPED("已跳过，没有新查询"), CANCELLED("执行已取消，没有确认新查询"),
}

data class PowerBackgroundStatus(
    val lastRunAt: Long = 0L,
    val lastSuccessAt: Long = 0L,
    val lastFailureAt: Long = 0L,
    val lastSkippedAt: Long = 0L,
    val lastOutcome: PowerBackgroundOutcome = PowerBackgroundOutcome.NEVER,
    val durationMs: Long = 0L,
    val outcomeExplanation: String = "尚无开机查询记录",
    val lastFinishedAt: Long = 0L,
    val schedulingError: String? = null,
)

internal fun interface PowerBackgroundDiagnostics {
    fun update(transform: (PowerBackgroundStatus) -> PowerBackgroundStatus)
}

object PowerBackgroundStatusStore {
    private const val PREFERENCES = "dorm_power_background_status_v1"
    private val lock = Any()
    private val mutableStatus = MutableStateFlow(PowerBackgroundStatus())
    val status = mutableStatus.asStateFlow()

    fun read(context: Context): PowerBackgroundStatus = synchronized(lock) {
        val value = try { decode(preferences(context)) } catch (_: Exception) { mutableStatus.value }
        mutableStatus.value = value
        value
    }

    internal fun diagnostics(context: Context): PowerBackgroundDiagnostics {
        val application = context.applicationContext
        return PowerBackgroundDiagnostics { transform -> synchronized(lock) {
            try {
                val prefs = preferences(application)
                val next = transform(decode(prefs))
                mutableStatus.value = next
                encode(prefs, next)
            } catch (_: Exception) { }
        } }
    }

    private fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private fun decode(prefs: SharedPreferences): PowerBackgroundStatus = PowerBackgroundStatus(
        lastRunAt = prefs.getLong("last_run", 0L).coerceAtLeast(0L),
        lastSuccessAt = prefs.getLong("last_success", 0L).coerceAtLeast(0L),
        lastFailureAt = prefs.getLong("last_failure", 0L).coerceAtLeast(0L),
        lastSkippedAt = prefs.getLong("last_skipped", 0L).coerceAtLeast(0L),
        lastOutcome = PowerBackgroundOutcome.entries.firstOrNull { it.name == prefs.getString("outcome", null) }
            ?: PowerBackgroundOutcome.NEVER,
        durationMs = prefs.getLong("duration", 0L).coerceAtLeast(0L),
        outcomeExplanation = prefs.getString("outcome_explanation", null) ?: "尚无开机查询记录",
        lastFinishedAt = prefs.getLong("last_finished", 0L).coerceAtLeast(0L),
        schedulingError = prefs.getString("scheduling_error", null),
    )

    private fun encode(prefs: SharedPreferences, value: PowerBackgroundStatus) {
        prefs.edit().putLong("last_run", value.lastRunAt).putLong("last_success", value.lastSuccessAt)
            .putLong("last_failure", value.lastFailureAt).putLong("last_skipped", value.lastSkippedAt)
            .putString("outcome", value.lastOutcome.name).putLong("duration", value.durationMs)
            .putString("outcome_explanation", value.outcomeExplanation)
            .putLong("last_finished", value.lastFinishedAt).putString("scheduling_error", value.schedulingError)
            .commit()
    }
}

internal fun PowerBackgroundDiagnostics.safelyUpdate(transform: (PowerBackgroundStatus) -> PowerBackgroundStatus) {
    try { update(transform) } catch (_: Exception) { }
}
