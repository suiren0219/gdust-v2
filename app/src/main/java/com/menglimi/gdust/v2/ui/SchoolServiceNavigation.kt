package com.menglimi.gdust.v2.ui

import com.menglimi.gdust.v2.data.SchoolService
import com.menglimi.gdust.v2.data.SchoolServicesState
import com.menglimi.gdust.v2.data.ServiceAuthStatus

/** 单项补连只刷新对应来源，不被另一项旧 Cookie 抢走。 */
internal fun selectScheduleService(
    preferred: SchoolService?,
    state: SchoolServicesState,
    portalSaved: Boolean,
    teachingSaved: Boolean,
): SchoolService? {
    fun available(service: SchoolService): Boolean = state[service].auth != ServiceAuthStatus.AUTH_REQUIRED &&
        (state[service].auth == ServiceAuthStatus.VERIFIED || when (service) {
            SchoolService.PORTAL -> portalSaved
            SchoolService.TEACHING -> teachingSaved
            SchoolService.CHX -> false
        })
    if (preferred != null) return preferred.takeIf { it != SchoolService.CHX && available(it) }
    return listOf(SchoolService.TEACHING, SchoolService.PORTAL)
        .firstOrNull { available(it) && state[it].auth == ServiceAuthStatus.VERIFIED }
        ?: listOf(SchoolService.TEACHING, SchoolService.PORTAL).firstOrNull(::available)
}

internal fun serviceConnectionSummary(state: SchoolServicesState): String = SchoolService.entries.joinToString(" · ") { service ->
    val status = state[service]
    service.label + when {
        status.checking -> "验证中"
        status.auth == ServiceAuthStatus.AUTH_REQUIRED -> "需认证"
        status.problem != null -> "待复查"
        status.auth == ServiceAuthStatus.VERIFIED -> "已连接"
        status.auth == ServiceAuthStatus.SAVED -> "待验证"
        else -> "未连接"
    }
}
