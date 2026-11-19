package com.menglimi.dormpower.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/** 点击小部件"刷新"：先把状态切到刷新中，再排队一次性查询；不等任务完成就重绘。 */
class PowerWidgetRefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (PowerWidgetRefreshWorker.request(context)) {
            DormPowerWidget().update(context, glanceId)
            return
        }
        // 未绑定等原因：Worker 未排队，直接把可读原因画到小部件上。
        PowerWidgetUpdater.updateAll(context)
    }
}
