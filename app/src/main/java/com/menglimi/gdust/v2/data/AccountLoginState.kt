package com.menglimi.gdust.v2.data

/** 页面登录颜色只认本次在线验证，保存凭据与离线可用都不代表会话有效。 */
enum class AccountLoginState(val label: String) {
    SIGNED_OUT("未登录"),
    UNVERIFIED("待验证"),
    CHECKING("验证中"),
    VALID("登录有效"),
    EXPIRED("登录已失效"),
    UNAVAILABLE("暂时无法验证"),
}

internal fun accountLoginState(expectedAccount: String, returnedAccount: String?, failure: Throwable?): AccountLoginState = when {
    expectedAccount.isBlank() -> AccountLoginState.SIGNED_OUT
    failure is AuthFailed -> AccountLoginState.EXPIRED
    failure != null -> AccountLoginState.UNAVAILABLE
    returnedAccount?.trim() == expectedAccount.trim() -> AccountLoginState.VALID
    else -> AccountLoginState.UNAVAILABLE
}
