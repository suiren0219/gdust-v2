package com.menglimi.gdust.v2.work

import android.app.AlarmManager
import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.notification.Condition
import android.service.notification.ConditionProviderService
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.menglimi.gdust.v2.data.Store
import com.menglimi.gdust.v2.ui.schedule.ScheduleTimes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 独立的课程勿扰规则：Android 35+ 也不修改用户全局勿扰，退出只释放本 App 的规则。 */
object ClassDndAlarms {
    internal const val ACTION_BOUNDARY = "com.menglimi.gdust.v2.CLASS_DND_BOUNDARY"
    private const val WORK_BOUNDARY = "gdust_class_dnd_boundary"
    private const val PREFS = "gdust_class_dnd"
    private var receiverRegistered = false
    // 权限变化只发给动态注册的接收器，不能仅靠 manifest。
    private val policyReceiver = ClassDndToggleReceiver()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun manager(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)
    internal fun conditionId(ctx: Context): Uri = Condition.newId(ctx).appendPath("class-time").build()

    @Synchronized
    fun initialize(ctx: Context) {
        val app = ctx.applicationContext
        if (!receiverRegistered) {
            app.registerReceiver(
                policyReceiver,
                IntentFilter(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED),
                Context.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }
        reschedule(app)
    }

    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("enabled", false)
    fun hasPendingSession(ctx: Context): Boolean = prefs(ctx).getLong("manual_end", 0) > 0
    fun hasPolicyAccess(ctx: Context): Boolean = manager(ctx).isNotificationPolicyAccessGranted

    fun openPolicySettings(ctx: Context) {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun setEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("enabled", enabled)
            .remove("manual_start").remove("manual_end").remove("skipped_until").remove("error").apply()
        // 只有明确打开开关才允许恢复被用户在系统设置中停用的本规则。
        if (enabled && hasPolicyAccess(ctx)) runCatching {
            val owned = findRule(ctx)
            if (owned == null) prefs(ctx).edit().remove("rule_id").apply()
            owned?.let { (id, rule) ->
                if (!rule.isEnabled) {
                    rule.isEnabled = true
                    manager(ctx).updateAutomaticZenRule(id, rule)
                }
            }
        }
        reschedule(ctx)
    }

    /** 通知动作只能选择本机仍存在且尚未结束的真实课时，过期/测试通知不会改变系统。 */
    fun startForLesson(ctx: Context, startMillis: Long, endMillis: Long): String {
        val now = System.currentTimeMillis()
        val window = windows(Store(ctx)).firstOrNull {
            it.startMillis == startMillis && it.endMillis == endMillis && it.endMillis > now &&
                it.startMillis - now <= TimeUnit.HOURS.toMillis(1)
        } ?: return "该课时已结束或课表已变更"
        if (!hasPolicyAccess(ctx)) return "请先到课程提醒设置授予勿扰权限"
        val existingRule = runCatching { findRule(ctx) }
        if (existingRule.isFailure) return "请检查系统勿扰权限后重试"
        if (existingRule.getOrNull()?.second?.isEnabled == false) return "请先在设置中重新开启上课自动勿扰"
        prefs(ctx).edit().putLong("manual_start", window.startMillis).putLong("manual_end", window.endMillis)
            .remove("skipped_until").remove("error").apply()
        reschedule(ctx, userAction = true)
        val error = prefs(ctx).getString("error", null)
        return error ?: if (now >= window.startMillis) "已开启本次上课勿扰，下课自动退出" else "已安排本次上课勿扰，上课时开启"
    }

    /** 本次取消保留后续自动计划；重叠课程作为同一段，避免立即被另一门课重新开启。 */
    fun cancelCurrent(ctx: Context) {
        val p = prefs(ctx)
        val now = System.currentTimeMillis()
        val current = planClassDnd(windows(Store(ctx)), isEnabled(ctx), manual(ctx), 0, now).active
        val until = maxOf(current?.endMillis ?: 0, p.getLong("manual_end", 0), p.getLong("active_end", 0))
        p.edit().remove("manual_start").remove("manual_end").putLong("skipped_until", until).apply()
        reschedule(ctx)
    }

    private fun manual(ctx: Context): ClassDndWindow? = prefs(ctx).let { p ->
        val start = p.getLong("manual_start", 0)
        val end = p.getLong("manual_end", 0)
        if (end > start && start > 0) ClassDndWindow(start, end) else null
    }

    private fun windows(store: Store): List<ClassDndWindow> {
        val termStart = ScheduleTimes.parseDate(store.termStartDate.ifBlank { store.loadCalendar()?.startDate.orEmpty() })
        return classDndWindows(store.loadBlocks(), termStart)
    }

    /** 可在任意恢复入口调用；不依赖提醒总开关或 POST_NOTIFICATIONS。 */
    @Synchronized
    fun reschedule(ctx: Context, store: Store = Store(ctx), nowMillis: Long = System.currentTimeMillis(), userAction: Boolean = false) {
        val p = prefs(ctx)
        val alarm = ctx.getSystemService(AlarmManager::class.java)
        val pi = boundaryIntent(ctx)
        alarm.cancel(pi)
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK_BOUNDARY)
        val windows = windows(store)
        var manual = manual(ctx)
        if (manual != null && (manual.endMillis <= nowMillis || manual !in windows)) {
            p.edit().remove("manual_start").remove("manual_end").apply()
            manual = null
        }
        if (p.getLong("skipped_until", 0) <= nowMillis) p.edit().remove("skipped_until").apply()
        if (!hasPolicyAccess(ctx)) {
            // 系统收回策略访问权时会停用所属规则；保留自动开关，以便重新授权后校准。
            p.edit().remove("manual_start").remove("manual_end").remove("active_start").remove("active_end")
                .putBoolean("permission_missing", true)
                .putString("error", "勿扰权限未授权，自动勿扰已暂停").apply()
            LessonReminderAlarms.cancelFallback(ctx)
            return
        }
        val permissionRestored = p.getBoolean("permission_missing", false)
        p.edit().remove("permission_missing").apply()
        val plan = planClassDnd(windows, isEnabled(ctx), manual, p.getLong("skipped_until", 0), nowMillis)
        if (isEnabled(ctx) || manual != null) LessonReminderAlarms.scheduleFallback(ctx)
        else LessonReminderAlarms.cancelFallback(ctx)
        // 先确保退出/下次上课有持久化唤醒；WorkManager 在精确闹钟被撤销时也能兜底。
        plan.nextAtMillis?.let { at ->
            runCatching {
                if (LessonReminderAlarms.canScheduleExact(ctx)) {
                    try {
                        alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                    } catch (_: SecurityException) {
                        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                    }
                } else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                WORK_BOUNDARY, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ClassDndBoundaryWorker>()
                    .setInitialDelay((at - nowMillis).coerceAtLeast(0), TimeUnit.MILLISECONDS).build(),
            )
        }
        runCatching {
            var owned = findRule(ctx)
            if (owned == null && (isEnabled(ctx) || manual != null)) {
                if (p.contains("rule_id") && !permissionRestored) {
                    p.edit().putBoolean("enabled", false).remove("manual_start").remove("manual_end").apply()
                    alarm.cancel(pi)
                    WorkManager.getInstance(ctx).cancelUniqueWork(WORK_BOUNDARY)
                    error("本应用勿扰规则已在系统中移除")
                }
                val rule = AutomaticZenRule(
                    "上课勿扰", ComponentName(ctx, ClassDndConditionProvider::class.java), conditionId(ctx),
                    NotificationManager.INTERRUPTION_FILTER_ALARMS, true,
                )
                val id = checkNotNull(manager(ctx).addAutomaticZenRule(rule)) { "无法创建勿扰规则" }
                p.edit().putString("rule_id", id).commit()
                owned = id to rule
            }
            owned?.let { (id, rule) ->
                if (!rule.isEnabled && permissionRestored && (isEnabled(ctx) || manual != null)) {
                    rule.isEnabled = true
                    check(manager(ctx).updateAutomaticZenRule(id, rule))
                }
                if (!rule.isEnabled) {
                    p.edit().putBoolean("enabled", false).remove("manual_start").remove("manual_end").apply()
                    report(ctx, id, false)
                    alarm.cancel(pi)
                    WorkManager.getInstance(ctx).cancelUniqueWork(WORK_BOUNDARY)
                    error("本应用勿扰规则已在系统中停用")
                }
                val active = plan.active
                // 进程错过下课后又在另一节课重启，也先结清旧课时的 snooze 状态。
                if (active != null && p.getLong("active_start", 0) != active.startMillis) report(ctx, id, false)
                report(ctx, id, active != null, userAction)
            }
            p.edit().putLong("active_start", plan.active?.startMillis ?: 0)
                .putLong("active_end", plan.active?.endMillis ?: 0).remove("error").apply()
        }.onFailure {
            p.edit().remove("active_start").remove("active_end")
                .putString("error", "勿扰未生效，请检查系统勿扰权限与本应用规则").apply()
        }
    }

    private fun findRule(ctx: Context): Pair<String, AutomaticZenRule>? {
        val p = prefs(ctx)
        val nm = manager(ctx)
        val id = p.getString("rule_id", null)
        if (id != null) nm.getAutomaticZenRule(id)?.let { return id to it }
        // 系统规则可能比进程持久：恢复时按独有 conditionId 找回，不创建重复规则。
        return nm.automaticZenRules.entries.firstOrNull { it.value.conditionId == conditionId(ctx) }
            ?.let { p.edit().putString("rule_id", it.key).apply(); it.key to it.value }
    }

    private fun report(ctx: Context, id: String, active: Boolean, userAction: Boolean = false) {
        val state = if (active) Condition.STATE_TRUE else Condition.STATE_FALSE
        val condition = if (Build.VERSION.SDK_INT >= 35) {
            Condition(conditionId(ctx), "按本机课表自动勿扰", state,
                if (userAction) Condition.SOURCE_USER_ACTION else Condition.SOURCE_SCHEDULE)
        } else Condition(conditionId(ctx), "按本机课表自动勿扰", state)
        manager(ctx).setAutomaticZenRuleState(id, condition)
    }

    internal fun onRuleStatus(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra(NotificationManager.EXTRA_AUTOMATIC_ZEN_RULE_ID) ?: return
        if (id != prefs(ctx).getString("rule_id", null)) return
        when (val status = intent.getIntExtra(NotificationManager.EXTRA_AUTOMATIC_ZEN_RULE_STATUS, -1)) {
            NotificationManager.AUTOMATIC_RULE_STATUS_DISABLED,
            NotificationManager.AUTOMATIC_RULE_STATUS_REMOVED -> {
                // 用户在系统中删除/禁用时不反复重建；权限回收则保留自动开关等待重新授权。
                if (hasPolicyAccess(ctx)) {
                    val current = runCatching { manager(ctx).getAutomaticZenRule(id) }.getOrNull()
                    if (status == NotificationManager.AUTOMATIC_RULE_STATUS_DISABLED && current?.isEnabled == true) return
                    if (status == NotificationManager.AUTOMATIC_RULE_STATUS_REMOVED && current != null) return
                    prefs(ctx).edit().putBoolean("enabled", false).remove("manual_start").remove("manual_end").apply()
                }
                reschedule(ctx)
            }
            // 不消费 ACTIVATED/DEACTIVATED：系统 snooze 自己维持到下一次 FALSE，
            // 异步到达的退出广播不能拿当前时间再取消另一课时。
        }
    }

    fun status(ctx: Context): String {
        if (!isEnabled(ctx) && !hasPendingSession(ctx)) return "未开启"
        if (!hasPolicyAccess(ctx)) return "勿扰权限未授权，自动勿扰已暂停"
        prefs(ctx).getString("error", null)?.let { return it }
        val p = prefs(ctx)
        val plan = planClassDnd(windows(Store(ctx)), isEnabled(ctx), manual(ctx), p.getLong("skipped_until", 0), System.currentTimeMillis())
        val format = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
        return when {
            plan.active != null -> {
                val state = runCatching {
                    findRule(ctx)?.let { (id, rule) ->
                        rule.isEnabled && if (Build.VERSION.SDK_INT >= 35) {
                            manager(ctx).getAutomaticZenRuleState(id) == Condition.STATE_TRUE &&
                                manager(ctx).currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
                        } else manager(ctx).currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
                    } == true
                }.getOrDefault(false)
                if (state) "本次上课勿扰至 ${format.format(Date(plan.active.endMillis))}"
                else "当前课时勿扰被系统暂停，可取消本次或检查系统规则"
            }
            plan.nextAtMillis != null -> "下次上课勿扰 ${format.format(Date(plan.nextAtMillis))}"
            isEnabled(ctx) -> "已开启，暂无可定时的课程"
            else -> "未开启"
        }
    }

    private fun boundaryIntent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 7719, Intent(ctx, ClassDndToggleReceiver::class.java).setAction(ACTION_BOUNDARY),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** 系统重新绑定/重新订阅规则时同步真实课时，进程退出不等于整天保持勿扰。 */
class ClassDndConditionProvider : ConditionProviderService() {
    override fun onConnected() = ClassDndAlarms.reschedule(this)
    override fun onSubscribe(conditionId: Uri) = ClassDndAlarms.reschedule(this)
    override fun onUnsubscribe(conditionId: Uri) {
        notifyCondition(Condition(conditionId, "课时已退出", Condition.STATE_FALSE))
    }
}

class ClassDndBoundaryWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        ClassDndAlarms.reschedule(applicationContext)
        return Result.success()
    }
}
