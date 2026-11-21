package com.menglimi.dormpower

import android.app.Application
import com.menglimi.dormpower.power.PowerScheduling

class PowerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 预览进程不访问本机数据。普通启动只清理旧调度，不触发查询。
        if (getProcessName() != packageName) return
        com.menglimi.dormpower.power.PowerNotifications(this).ensureChannel()
        PowerScheduling.ensure(this)
    }
}
