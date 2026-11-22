package com.menglimi.dormpower

import android.app.Application
import com.menglimi.dormpower.power.PowerScheduling
import com.menglimi.dormpower.widget.PowerWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PowerApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // debug 预览在独立进程中运行；不实例化存储，不补排任务，也不创建网络客户端。
        if (getProcessName() != packageName) return
        com.menglimi.dormpower.power.PowerNotifications(this).ensureChannel()
        PowerScheduling.ensure(this)
        // 进程启动时把最新快照推给桌面小部件；没有实例时是空操作。
        scope.launch { PowerWidgetUpdater.updateAll(this@PowerApplication) }
    }
}
