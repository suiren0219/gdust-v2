package com.menglimi.gdust.v2

import android.app.Application
import com.menglimi.gdust.v2.data.Repository

class App : Application() {

    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repository(this)
        repo.store.publishPowerWorkerConfig()
        com.menglimi.gdust.v2.work.PowerWorker.schedule(this)
        // 登录过就开启定时同步整学期课表（12 小时一次；会话失效会通知重登）
        if (repo.store.autoSyncEnabled && (repo.store.hasSession || repo.store.casTicket.isNotBlank())) {
            com.menglimi.gdust.v2.work.ScheduleSyncWorker.schedule(this)
        }
        // 课程提醒（课前 + 次日）；开关在设置里控制
        if (repo.store.reminderEnabled) {
            com.menglimi.gdust.v2.work.LessonReminderWorker.schedule(this)
        }
    }
}
