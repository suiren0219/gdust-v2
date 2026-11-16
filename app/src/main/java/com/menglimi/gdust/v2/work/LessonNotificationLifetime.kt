package com.menglimi.gdust.v2.work

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.menglimi.gdust.v2.data.CourseBlock
import java.time.LocalTime
import java.time.ZoneId

/** 通知槽位会复用；token 标识一次发布，旧闹钟只能撤销它对应的那一次。 */
internal data class LessonNotificationLease(
    val token: String,
    val expiresAtMillis: Long,
    val kind: ReminderKind? = null,
    val blocks: List<CourseBlock> = emptyList(),
    val island: Boolean = false,
)

/** 显式字段避免 release 混淆改变持久化格式；CourseBlock 沿用现有数据模型的保留规则。 */
internal fun encodeLessonNotificationLease(lease: LessonNotificationLease, gson: Gson): String = JsonObject().apply {
    addProperty("token", lease.token)
    addProperty("expiresAtMillis", lease.expiresAtMillis)
    lease.kind?.let { addProperty("kind", it.name) }
    add("blocks", gson.toJsonTree(lease.blocks))
    addProperty("island", lease.island)
}.toString()

internal fun decodeLessonNotificationLease(value: String?, gson: Gson): LessonNotificationLease? = runCatching {
    val json = JsonParser.parseString(requireNotNull(value)).asJsonObject
    val token = json.get("token").asString.also { require(it.isNotBlank()) }
    val end = json.get("expiresAtMillis").asLong.also { require(it > 0) }
    val kind = json.get("kind")?.takeUnless { it.isJsonNull }?.asString?.let(ReminderKind::valueOf)
    LessonNotificationLease(token, end, kind,
        gson.fromJson(json.get("blocks"), Array<CourseBlock>::class.java).toList(), json.get("island").asBoolean)
}.getOrNull()

/** 课前和课中通知使用同一个真实课末，不因晚发或倒计时取整额外延长。 */
internal fun lessonNotificationDeadline(event: ReminderEvent, zone: ZoneId = ZoneId.systemDefault()): Long? =
    if (event.kind == ReminderKind.NEXT_DAY) event.atMillis + 12 * 60 * 60_000L
    else runCatching {
        val start = LocalTime.parse(event.startText)
        val end = LocalTime.parse(event.endText)
        if (!end.isAfter(start)) null
        else event.date.atTime(end).atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()

internal fun lessonNotificationTimeout(expiresAtMillis: Long, nowMillis: Long): Long =
    (expiresAtMillis - nowMillis).coerceAtLeast(0)

internal fun lessonIslandTimeoutSeconds(expiresAtMillis: Long, nowMillis: Long): Int =
    ((lessonNotificationTimeout(expiresAtMillis, nowMillis) + 999) / 1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

internal fun canExpireLessonNotification(
    lease: LessonNotificationLease?,
    expectedToken: String,
    visibleToken: String?,
    nowMillis: Long,
): Boolean = lease != null && lease.token == expectedToken && visibleToken == expectedToken &&
    nowMillis >= lease.expiresAtMillis

/** 测试通知不受提醒总开关限制；真实课时在关闭分类或课表被移除时立即撤销。 */
internal fun keepLessonNotification(
    lease: LessonNotificationLease,
    nowMillis: Long,
    notificationAllowed: Boolean,
    reminderEnabled: Boolean,
    inClassEnabled: Boolean,
    nextDayEnabled: Boolean,
    islandEnabled: Boolean,
    blocks: List<CourseBlock>,
): Boolean {
    if (!notificationAllowed || nowMillis >= lease.expiresAtMillis || (lease.island && !islandEnabled)) return false
    if (lease.kind == null) return true
    if (!reminderEnabled || lease.blocks.isEmpty() || !blocks.containsAll(lease.blocks)) return false
    return when (lease.kind) {
        ReminderKind.IN_CLASS -> inClassEnabled
        ReminderKind.NEXT_DAY -> nextDayEnabled
        ReminderKind.BEFORE_CLASS -> true
    }
}
