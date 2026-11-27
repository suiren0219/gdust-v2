package com.menglimi.dormpower.power

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.menglimi.dormpower.MainActivity
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerBackgroundUiInstrumentationTest {
    @Test fun settingsShowsQueryRhythmPresetSwitchAndStatus() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertTrue(context.packageName.endsWith(".backgroundtest"))
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("tab", 2))
        try {
            instrumentation.waitForIdleSync()
            val seen = mutableSetOf<String>()
            for (attempt in 0 until 16) {
                SystemClock.sleep(300)
                val visible = mutableSetOf<String>()
                collect(instrumentation.uiAutomation.rootInActiveWindow, visible)
                seen.addAll(visible)
                if (listOf("更新提示节奏", "手动", "均衡", "低电量关注", "自定间隔", "此设置只决定缓存何时标记为“待更新”", "开机自动查询", "后台启动记录")
                        .all { title -> visible.any { it.contains(title) } }) {
                    saveScreenshot("settings-rhythm.png")
                    break
                }
            }
            for (title in listOf("更新提示节奏", "手动", "均衡", "低电量关注", "自定间隔", "此设置只决定缓存何时标记为“待更新”", "开机自动查询", "后台启动记录")) {
                assertTrue("Missing $title: $seen", seen.any { it.contains(title) })
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun restoredLedgerAndAllHistoryHaveVisibleScreens() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertTrue(context.packageName.endsWith(".backgroundtest"))
        try {
            for ((screen, expected) in listOf("history" to "全部记录", "ledger" to "电费账本")) {
                context.startActivity(Intent().setClassName(context.packageName,
                    "com.menglimi.dormpower.debug.PreviewActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra("screen", screen))
                val seen = mutableSetOf<String>()
                for (attempt in 0 until 20) {
                    SystemClock.sleep(250)
                    collect(instrumentation.uiAutomation.rootInActiveWindow, seen)
                    if (seen.any { it.contains(expected) }) break
                }
                assertTrue("Missing $screen UI: $seen", seen.any { it.contains(expected) })
                saveScreenshot("upgrade-$screen.png")
            }
        } finally {
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
        }
    }

    @Test fun settingsShowsBackgroundAccessAndExecutionHistory() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertTrue(context.packageName.endsWith(".backgroundtest"))
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("tab", 2))
        try {
            instrumentation.waitForIdleSync()
            // 系统可能恢复上次列表位置，先回顶部再按可见内容逐段检查。
            repeat(5) { swipe(down = true); SystemClock.sleep(100) }
            val seen = mutableSetOf<String>()
            for (step in 0 until 12) {
                SystemClock.sleep(350)
                val visible = mutableSetOf<String>()
                collect(instrumentation.uiAutomation.rootInActiveWindow, visible)
                seen.addAll(visible)
                if (visible.any { it.contains("开机自动查询") }) saveScreenshot("background-access.png")
                if (visible.any { it.contains("最近一次：") }) saveScreenshot("background-status.png")
                if (seen.any { it.contains("开机自动查询") } && seen.any { it.contains("最近一次：") }) break
                swipe(down = false)
            }
            assertTrue("Missing startup switch: $seen", seen.any { it.contains("开机自动查询") })
            assertTrue("Missing auto-start entry: $seen", seen.any { it.contains("厂商自启动") })
            assertTrue("Missing execution status: $seen", seen.any { it.contains("最近一次：") })
            assertTrue("Startup query should be off by default: $seen", seen.any { it.contains("已关闭：不会在开机时查询") })
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun collect(node: AccessibilityNodeInfo?, target: MutableSet<String>) {
        if (node == null) return
        node.text?.toString()?.let(target::add)
        node.contentDescription?.toString()?.let(target::add)
        repeat(node.childCount) { collect(node.getChild(it), target) }
    }

    private fun swipe(down: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bounds = instrumentation.targetContext.resources.displayMetrics
        val x = bounds.widthPixels * 0.5f
        val start = bounds.heightPixels * if (down) 0.36f else 0.72f
        val end = bounds.heightPixels * if (down) 0.72f else 0.36f
        val downAt = SystemClock.uptimeMillis()
        fun send(action: Int, y: Float, time: Long) {
            val event = MotionEvent.obtain(downAt, time, action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, start, downAt)
        repeat(10) { index ->
            send(MotionEvent.ACTION_MOVE, start + (end - start) * (index + 1) / 10f, downAt + (index + 1) * 25L)
        }
        send(MotionEvent.ACTION_UP, end, downAt + 280L)
    }

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val path = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val directory = File(path).apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
