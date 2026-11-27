package com.menglimi.chaoxing

/** 会话提交失败不能降级为业务部分成功；同一响应的暂停要求仍需保留。 */
class ChxCookieWriteException(
    verificationRequired: Boolean = false,
    retryAt: Long = 0,
    cause: Throwable? = null,
) : ChxCourseReadException("会话更新失败，请重试", verificationRequired = verificationRequired, retryAt = retryAt) {
    init { if (cause != null) initCause(cause) }
}
