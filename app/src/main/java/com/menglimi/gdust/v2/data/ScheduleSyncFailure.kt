package com.menglimi.gdust.v2.data

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalTime
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException

/** 只标记实际教务课表读取失败；保留 cause 供网络错误识别使用。 */
class TeachingSyncFailure(cause: Exception) : Exception(cause.message, cause)

/** 认证失效和协程取消必须保持原异常，不能变成维护提醒。 */
internal fun Exception.asTeachingSyncFailure(): Exception = when (this) {
    is AuthFailed, is CancellationException, is TeachingSyncFailure -> this
    else -> TeachingSyncFailure(this)
}

/** 同步错误按实际来源展示，用户设置的时间段不能作为停服证据。 */
fun syncFailureMessage(
    error: Throwable,
    now: LocalTime,
    startMin: Int,
    endMin: Int,
    reminderEnabled: Boolean,
): String {
    val teaching = error is TeachingSyncFailure
    val failure = if (teaching) error.cause ?: error else error
    if (failure is CancellationException) throw failure
    if (failure is AuthFailed) return failure.message.orEmpty()

    val network = generateSequence(failure) { it.cause }.filterIsInstance<IOException>().firstOrNull()
    val source = if (teaching) "教务" else "学校门户"
    val message = when {
        network is SocketTimeoutException -> "${source}连接超时，请稍后重试。"
        network is UnknownHostException -> "无法解析${source}地址，请检查网络后重试。"
        network is ConnectException -> "无法连接${source}，请检查网络或稍后重试。"
        network is SSLException -> "${source}安全连接失败，请稍后重试或换入口。"
        network?.message?.contains("unexpected end of stream", ignoreCase = true) == true ->
            if (teaching) "教务连接中断，请重试或换入口。"
            else "学校门户连接中断，请重试；如需整学期课表，可使用教务学期导入。"
        network != null -> "${source}网络连接失败，请检查网络后重试。"
        else -> failure.message?.takeIf { it.isNotBlank() }
            ?.replace(Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE), "[请求地址已隐藏]")
            ?: "课表同步失败，请重试。"
    }
    return if (teaching && reminderEnabled) {
        MaintenanceWindow.notice(now, startMin, endMin, message) ?: message
    } else message
}
