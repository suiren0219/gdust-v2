package com.menglimi.chaoxing

/** 一次请求的原始响应；跳转由核心逐跳检查，传输层不自动跟随 Location。 */
data class ChxResponse(
    val status: Int,
    val body: String,
    val location: String? = null,
    val retryAfter: String? = null,
    val serverDate: String? = null,
)

/** 已有明确的学习通会话失效证据；不依赖接入方的账号或异常体系。 */
class ChxAuthFailed(val code: Int, msg: String) : Exception(msg)
