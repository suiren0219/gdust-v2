package com.menglimi.gdust.v2.work

/** 后台采集必须同时由用户明确开启并存在完整宿舍绑定。 */
internal object PowerCollectionPolicy {
    fun hasBindingConfig(account: String?, code: Int, verify: String?): Boolean =
        !account.isNullOrBlank() && code > 0 && !verify.isNullOrBlank()

    fun shouldSchedule(enabled: Boolean, hasBinding: Boolean): Boolean = enabled && hasBinding

    fun hasWorkerConfig(enabled: Boolean, account: String?, code: Int, verify: String?): Boolean =
        enabled && hasBindingConfig(account, code, verify)
}
