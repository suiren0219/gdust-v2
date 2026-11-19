package com.menglimi.gdust.v2.work

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Android 没有统一的后台自启动运行时权限；优先打开 MIUI/HyperOS 自启动页，否则退到本应用详情页。 */
object PowerBackgroundSettings {
    fun openAutostart(context: Context): Boolean {
        val app = context.applicationContext
        val vendorIntent = Intent("miui.intent.action.OP_AUTO_START").setComponent(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
        )
        if (runCatching { app.startActivity(vendorIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }
                .getOrDefault(false)
        ) return true

        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { app.startActivity(details); true }.getOrDefault(false)
    }

    /** 只打开系统电池优化列表，不直接申请白名单豁免。 */
    fun openBatterySettings(context: Context): Boolean = runCatching {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
