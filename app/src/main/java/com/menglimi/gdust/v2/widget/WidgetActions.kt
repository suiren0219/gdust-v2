package com.menglimi.gdust.v2.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionStartActivity
import com.menglimi.gdust.v2.MainActivity
import com.menglimi.gdust.v2.data.Store
import com.menglimi.gdust.v2.work.LessonReminderAlarms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 使用 Activity PendingIntent 直接进入前台，不经后台 callback 跳转。 */
fun widgetOpenAction(context: Context, tab: Int, todoId: Long? = null): Action {
    require(tab in 1..3)
    val targetTodo = todoId.takeIf { tab == 3 }
    val intent = Intent(context, MainActivity::class.java)
        .setAction(WidgetOpenRequest.ACTION)
        // PendingIntent 的等价判断不比较 extras；data 区分页面和待办条目。
        .setData(Uri.Builder().scheme("gdust-widget").authority("open")
            .appendPath(tab.toString()).apply {
                targetTodo?.let { appendPath(it.toString()) }
            }.build())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(WidgetOpenRequest.EXTRA_TAB, tab)
    targetTodo?.let { intent.putExtra(WidgetOpenRequest.EXTRA_TODO_ID, it) }
    return actionStartActivity(intent)
}

data class WidgetOpenRequest(val tab: Int, val todoId: Long? = null) {
    companion object {
        const val ACTION = "com.menglimi.gdust.v2.OPEN_WIDGET"
        const val EXTRA_TAB = "widget_tab"
        const val EXTRA_TODO_ID = "widget_todo_id"

        fun fromIntent(intent: Intent?): WidgetOpenRequest? {
            if (intent?.action != ACTION) return null
            val tab = intent.getIntExtra(EXTRA_TAB, -1).takeIf { it in 1..3 } ?: return null
            val todoId = if (tab == 3 && intent.hasExtra(EXTRA_TODO_ID)) {
                intent.getLongExtra(EXTRA_TODO_ID, 0L)
            } else null
            return WidgetOpenRequest(tab, todoId)
        }
    }
}

/** MainActivity 在 AppRoot 外提供，避免为一次待办定位改动整条页面参数链。 */
val LocalWidgetTodoId = staticCompositionLocalOf<MutableState<Long?>?> { null }

/** 单向置为完成；重复投递或旧 RemoteViews 点击不会把已完成项重新打开。 */
class CompleteWidgetTodoAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val todoId = parameters[TODO_ID] ?: return
        withContext(Dispatchers.IO) {
            val store = Store(context)
            store.setTodoDone(todoId, true)
            LessonReminderAlarms.rescheduleTodo(context, store)
        }
        // 等待三个待办 provider 的所有实例更新，不能脱离 callback 生命周期。
        WidgetRefresher.updateTodos(context)
    }

    companion object {
        val TODO_ID = ActionParameters.Key<Long>("todo_id")
    }
}
