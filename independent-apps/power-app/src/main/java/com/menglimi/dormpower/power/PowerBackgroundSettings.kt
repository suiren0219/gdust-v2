package com.menglimi.dormpower.power

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Android 没有统一的后台自启动运行时权限；优先打开厂商自启动页（目前覆盖 MIUI/HyperOS），
 * 其余机型退到本应用详情页，由用户自行找到"自启动/后台运行"。
 */
object PowerBackgroundSettings {
    fun openAutostart(context: Context): Boolean {
        val app = context.applicationContext
        val vendor = Intent("miui.intent.action.OP_AUTO_START").setComponent(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
        )
        if (runCatching {
                app.startActivity(vendor.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
        ) return true
        return openAppDetails(app)
    }

    /** 只打开系统电池优化列表，不直接申请白名单豁免。 */
    fun openBatterySettings(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)

    private fun openAppDetails(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)
}
