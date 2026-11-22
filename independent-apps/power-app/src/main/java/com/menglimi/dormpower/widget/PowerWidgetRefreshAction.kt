package com.menglimi.dormpower.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/** 点击小部件"刷新"：先把状态切到刷新中，再排队一次性查询；不等任务完成就重绘。 */
class PowerWidgetRefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PowerWidgetRefreshWorker.request(context)
        PowerWidgetUpdater.updateAll(context)
    }
}
