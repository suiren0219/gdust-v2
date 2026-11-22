package com.menglimi.study.learning

import com.menglimi.chaoxing.ChxRequestPause

/** 随账号保存暂停和未读课程；不保存请求签名或网页验证凭据。 */
data class LearningReadControl(
    val pause: ChxRequestPause? = null,
    val blockedCourseKey: String? = null,
    val progressBlocked: Boolean = false,
    val pendingCourseKeys: List<String> = emptyList(),
    val verificationUrl: String? = null,
) {
    val active: Boolean get() = pause != null || pendingCourseKeys.isNotEmpty()
    fun message(): String? = when {
        pause?.verificationRequired == true -> "读取已暂停，请先在网页完成验证，再点“完成检查”。已读作业仍保留。"
        pause != null -> "请求过于频繁，读取已暂停。冷却结束后请先重查受阻项。"
        pendingCourseKeys.isNotEmpty() -> "受阻项已恢复，另有 ${pendingCourseKeys.size} 门课程待继续；尚未读取的课程是否有作业仍未知。"
        else -> null
    }
}
