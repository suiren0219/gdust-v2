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
        // 预览进程不访问本机数据。普通启动只清理旧调度，不触发查询。
        if (getProcessName() != packageName) return
        com.menglimi.dormpower.power.PowerNotifications(this).ensureChannel()
        PowerScheduling.ensure(this)
        // 进程启动时把最新快照推给桌面小部件；没有实例时是空操作。
        scope.launch { PowerWidgetUpdater.updateAll(this@PowerApplication) }
    }
}
