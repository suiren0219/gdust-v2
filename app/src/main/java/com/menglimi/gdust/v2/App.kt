package com.menglimi.gdust.v2

import android.app.Application
import android.content.SharedPreferences
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.widget.WidgetRefreshScheduler
import com.menglimi.gdust.v2.widget.WidgetRefresher
import com.menglimi.gdust.v2.work.LessonReminderAlarms

class App : Application() {

    lateinit var repo: Repository
        private set

    private val scheduleKeys = setOf("calendar", "blocks", "schedule_source", "term_start", "term_weeks", "term_week")
    private val widgetKeys = scheduleKeys + setOf(
        "todos", "samples", "warn_th", "widget_power_range", "ui_style", "monet_enabled",
    )
    // SharedPreferences 仅弱引用 listener，由 Application 持有至进程结束；同步/通知/导入等所有写入都覆盖。
    private val cacheChanges = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in scheduleKeys) LessonReminderAlarms.reschedule(this, repo.store)
        if (key == null || key == "todos") LessonReminderAlarms.rescheduleTodo(this, repo.store)
        if (key == null || key in widgetKeys) WidgetRefresher.refreshAll(this)
    }
    private val powerChanges = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in setOf("prev_odd", "last_sample", "name", "threshold")) {
            WidgetRefresher.refreshAll(this)
        }
    }

    override fun onCreate() {
        super.onCreate()
        // 研究线诊断：debug 包开放 WebView 远程调试（chrome://inspect / CDP），
        // 用于查看 CAS/学习通页面白屏时的 DOM、控制台与网络失败；release 不开。
        val debuggable = applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable) {
            android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        }
        repo = Repository(this)
        getSharedPreferences("gdust_cache", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(cacheChanges)
        getSharedPreferences("gdust_power", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(powerChanges)
        WidgetRefreshScheduler.ensureScheduled(this)
        // 加密存储初始化较重（读 keystore 100–500ms）：挪到后台线程预热，主线程不再卡
        Thread { repo.store.warmSecure() }.start()
        repo.store.publishPowerWorkerConfig()
        // 省电：水电预警只在绑定了宿舍电号时才需要后台巡检——没绑定不排程（原来无条件 15 分钟唤醒）
        if (repo.store.hasPowerBinding) {
            com.menglimi.gdust.v2.work.PowerWorker.schedule(this)
        } else {
            com.menglimi.gdust.v2.work.PowerWorker.cancel(this)
        }
        // 登录过就开启定时同步整学期课表（12 小时一次；会话失效会通知重登）
        if (repo.store.autoSyncEnabled && (repo.store.hasSession || repo.store.casTicket.isNotBlank())) {
            com.menglimi.gdust.v2.work.ScheduleSyncWorker.schedule(this)
        }
        // 课程提醒（课前 + 次日）；开关在设置里控制
        if (repo.store.reminderEnabled) {
            com.menglimi.gdust.v2.work.LessonReminderWorker.schedule(this)
        }
        if (repo.store.todoReminderEnabled) {
            LessonReminderAlarms.rescheduleTodo(this, repo.store)
            LessonReminderAlarms.scheduleFallback(this)
        }
    }
}
