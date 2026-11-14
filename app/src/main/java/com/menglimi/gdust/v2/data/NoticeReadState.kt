package com.menglimi.gdust.v2.data

import java.security.MessageDigest

/** URL 与发布时间标识一条公告；缺 URL 时退回完整元数据，避免同一时刻的公告互相覆盖。 */
val Notice.readId: String
    get() = if (url.isNotBlank()) noticeDigest(url.trim(), time.toString())
    else noticeDigest(title, type, issuer.orEmpty(), time.toString())

internal fun noticeReadStorageKey(account: String): String? = account.trim()
    .takeIf(String::isNotEmpty)?.let { "notice_read_ids_${noticeDigest(it)}" }

private fun noticeDigest(vararg fields: String): String {
    val input = fields.joinToString("") { "${it.length}:$it" }
    return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
