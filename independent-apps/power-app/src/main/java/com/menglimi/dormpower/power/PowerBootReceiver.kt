package com.menglimi.dormpower.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 只处理本机开机与本包覆盖更新；广播仅排队单次查询，由 WorkManager 执行网络请求。 */
class PowerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!PowerBackgroundPolicy.isRestoreAction(intent.action)) return
        val application = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                // 广播只给配置读取和工作入队一个短预算；查询不在广播进程内运行。
                withTimeoutOrNull(8_000L) {
                    restore(intent.action) { PowerScheduling.restore(application) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** 同一过滤逻辑供测试注入；生产回调只做一次 WorkManager enqueue。 */
        internal suspend fun restore(action: String?, enqueue: suspend () -> Unit): Boolean {
            if (!PowerBackgroundPolicy.isRestoreAction(action)) return false
            enqueue()
            return true
        }
    }
}
