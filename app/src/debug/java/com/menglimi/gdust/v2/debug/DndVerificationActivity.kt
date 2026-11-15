package com.menglimi.gdust.v2.debug

import android.app.Activity
import android.app.NotificationManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.menglimi.gdust.v2.App
import com.menglimi.gdust.v2.data.CalendarInfo
import com.menglimi.gdust.v2.data.CourseBlock
import com.menglimi.gdust.v2.work.ClassDndAlarms
import com.menglimi.gdust.v2.work.LessonReminderAlarms
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * 仅 debug：--es step seed/enable/before/start/overlap/end/reconcile/disable/cancel/clear/restore/status/policy。
 * 所有步骤只用设备真实时钟；before/start/overlap/end 只读断言，不重新启用或强推规则状态。
 * seed 备份课表并固定 fixture 日期；主代理临时修改模拟器时钟后按真实边界观察，结束执行 restore。
 * 不读取/写入账号、Cookie 或其他凭据，不为 release 添加虚拟时钟。
 */
class DndVerificationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = (application as App).repo.store
        val cache = getSharedPreferences("gdust_cache", MODE_PRIVATE)
        val backup = getSharedPreferences("gdust_dnd_verification", MODE_PRIVATE)
        val keys = listOf("calendar", "blocks", "schedule_source", "term_start")
        val step = intent.getStringExtra("step") ?: "status"
        val today = LocalDate.now()
        val nm = getSystemService(NotificationManager::class.java)
        val conditionId = ClassDndAlarms.conditionId(this)
        var observation = "未执行边界断言"
        val result = runCatching {
            when (step) {
                "seed" -> {
                    if (!backup.getBoolean("saved", false)) {
                        val edit = backup.edit().putBoolean("saved", true)
                            .putBoolean("dnd_enabled", ClassDndAlarms.isEnabled(this))
                        for (key in keys) edit.putString(key, cache.getString(key, null))
                        edit.commit()
                    }
                    ClassDndAlarms.setEnabled(this, false)
                    backup.edit().putString("fixture_date", today.toString()).commit()
                    store.termStartDate = today.toString()
                    store.saveCalendar(CalendarInfo(1, 20, today.toString(), "2026", "debug"))
                    store.saveBlocks(listOf(
                        CourseBlock(1, today.dayOfWeek.value, 1, 2, "勿扰验证 A", "离线", "", today.toString()),
                        CourseBlock(1, today.dayOfWeek.value, 2, 4, "勿扰验证 B（重叠）", "离线", "", today.toString()),
                        CourseBlock(1, today.plusDays(1).dayOfWeek.value, 1, 2, "勿扰验证 C", "离线", "", today.plusDays(1).toString()),
                    ), "dnd_debug")
                }
                "enable", "reconcile" -> {
                    check(backup.getBoolean("saved", false)) { "请先执行 seed" }
                    check(ClassDndAlarms.hasPolicyAccess(this)) { "请先授予勿扰策略访问权限" }
                    if (step == "enable") ClassDndAlarms.setEnabled(this, true)
                    else ClassDndAlarms.reschedule(this, store)
                    observation = "已按真实系统时间校准；此动作不能证明后台闹钟自动交付"
                }
                "before", "start", "overlap", "end" -> {
                    check(backup.getBoolean("saved", false)) { "请先执行 seed" }
                    check(ClassDndAlarms.hasPolicyAccess(this)) { "请先授予勿扰策略访问权限" }
                    check(ClassDndAlarms.isEnabled(this)) { "请先执行 enable" }
                    val now = ZonedDateTime.now()
                    check(now.toLocalDate().toString() == backup.getString("fixture_date", null)) {
                        "设备日期与 fixture_date 不同，先校准设备时钟或重新 seed"
                    }
                    val range = when (step) {
                        "before" -> LocalTime.of(8, 29) to LocalTime.of(8, 30)
                        "start" -> LocalTime.of(8, 30) to LocalTime.of(9, 20)
                        "overlap" -> LocalTime.of(10, 5) to LocalTime.of(12, 0)
                        else -> LocalTime.of(12, 0) to LocalTime.of(14, 40)
                    }
                    val time = now.toLocalTime()
                    check(!time.isBefore(range.first) && time.isBefore(range.second)) {
                        "设备真实时间 $time 不在 $step 窗口 [${range.first}, ${range.second})，未修改规则"
                    }
                    check(android.os.Build.VERSION.SDK_INT >= 35) { "边界断言需 API35+ 读取本规则激活状态" }
                    val owned = nm.automaticZenRules.entries.filter { it.value.conditionId == conditionId }
                    check(owned.size == 1) { "预期1条本应用课时规则，实际${owned.size}" }
                    val state = if (android.os.Build.VERSION.SDK_INT >= 35) {
                        nm.getAutomaticZenRuleState(owned.single().key)
                    } else error("当前系统不支持读取规则状态")
                    val expected = if (step == "start" || step == "overlap") 1 else 0
                    check(state == expected) { "边界未通过：expected=$expected actual=$state（只读观测）" }
                    observation = "只读状态符合预期 expected=$expected actual=$state；是否由 Alarm 唤醒须结合日志"
                }
                "disable" -> ClassDndAlarms.setEnabled(this, false)
                "cancel" -> ClassDndAlarms.cancelCurrent(this)
                "clear" -> {
                    store.saveBlocks(emptyList(), "dnd_debug")
                    ClassDndAlarms.reschedule(this, store)
                }
                "restore" -> {
                    ClassDndAlarms.setEnabled(this, false)
                    if (backup.getBoolean("saved", false)) {
                        val edit = cache.edit()
                        for (key in keys) edit.putString(key, backup.getString(key, null))
                        edit.commit()
                        ClassDndAlarms.setEnabled(this, backup.getBoolean("dnd_enabled", false))
                        backup.edit().clear().commit()
                    }
                }
                "policy" -> ClassDndAlarms.openPolicySettings(this)
                "status" -> Unit
                else -> error("未知验证步骤")
            }
            "完成"
        }.getOrElse { it.message ?: "失败" }
        val rules = if (ClassDndAlarms.hasPolicyAccess(this)) runCatching {
            nm.automaticZenRules.entries.joinToString("\n") { (id, rule) ->
                val state = if (android.os.Build.VERSION.SDK_INT >= 35) nm.getAutomaticZenRuleState(id) else -1
                "$id name=${rule.name} enabled=${rule.isEnabled} state=$state condition=${rule.conditionId}"
            }
        }.getOrElse { it.javaClass.simpleName } else "策略权限未授予"
        val report = "step=$step result=$result\nwallClock=${ZonedDateTime.now()} epochMillis=${System.currentTimeMillis()}\n" +
            "fixture_date=${backup.getString("fixture_date", null)}\n$observation\n" +
            "policy=${ClassDndAlarms.hasPolicyAccess(this)} enabled=${ClassDndAlarms.isEnabled(this)} " +
            "exact=${LessonReminderAlarms.canScheduleExact(this)} filter=${nm.currentInterruptionFilter}\n$rules\n" +
            "边界步骤不调用校准；冷启动仍可能触发 App/服务恢复。自动闹钟必须在打开探针前采集系统状态。\n" +
            "完成后执行 restore 并恢复系统时间/自动校时设置。"
        Log.i("DndVerification", report)
        setContentView(TextView(this).apply {
            text = report
            textSize = 16f
            setPadding(24, 48, 24, 24)
        })
    }
}
