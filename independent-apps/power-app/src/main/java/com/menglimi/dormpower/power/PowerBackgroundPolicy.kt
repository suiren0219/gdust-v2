package com.menglimi.dormpower.power

import com.menglimi.power.PowerMonitorConfig

internal object PowerBackgroundPolicy {
    const val BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
    const val MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED"

    fun isRestoreAction(action: String?): Boolean =
        action == BOOT_COMPLETED || action == MY_PACKAGE_REPLACED

    // 旧版查询节奏只保留供配置兼容，不控制新的开机开关。
    fun isBootQueryEligible(config: PowerMonitorConfig): Boolean = config.binding?.isValid == true
}
