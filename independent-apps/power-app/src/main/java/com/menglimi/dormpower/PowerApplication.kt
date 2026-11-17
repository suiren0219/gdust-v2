package com.menglimi.dormpower

import android.app.Application
import com.menglimi.dormpower.power.PowerScheduling

class PowerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // debug 预览在独立进程中运行；不实例化存储，不补排任务，也不创建网络客户端。
        if (getProcessName() != packageName) return
        com.menglimi.dormpower.power.PowerNotifications(this).ensureChannel()
        PowerScheduling.ensure(this)
    }
}
