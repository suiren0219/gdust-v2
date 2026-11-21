package com.menglimi.dormpower.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/** 后台 Worker 与刷新回调共用：所有已放置实例一起重绘，失败不影响查询流程。 */
internal object PowerWidgetUpdater {
    suspend fun updateAll(context: Context) {
        runCatching { DormPowerWidget().updateAll(context) }
        runCatching { PowerCalendarWidget().updateAll(context) }
        runCatching { PowerLedgerWidget().updateAll(context) }
    }
}
