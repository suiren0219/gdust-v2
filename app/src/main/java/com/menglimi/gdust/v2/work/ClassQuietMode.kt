package com.menglimi.gdust.v2.work

/** 两种模式都由本应用的系统勿扰规则承载，不修改全局音量或用户的手动勿扰。 */
enum class ClassQuietMode(val value: String, val label: String) {
    OFF("off", "关闭"),
    DND("dnd", "勿扰"),
    SILENT("silent", "静音");

    companion object {
        /** 旧版 enabled=true 沿用原来的勿扰；已存模式优先，未知值安全关闭。 */
        internal fun fromStored(value: String?, legacyEnabled: Boolean): ClassQuietMode =
            if (value == null) {
                if (legacyEnabled) DND else OFF
            } else entries.firstOrNull { it.value == value } ?: OFF
    }
}
