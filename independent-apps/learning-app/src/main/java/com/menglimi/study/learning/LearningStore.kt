package com.menglimi.study.learning

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.menglimi.chaoxing.ChxCourse
import com.menglimi.chaoxing.ChxCourseOverview
import com.menglimi.chaoxing.ChxCourseProgress
import com.menglimi.chaoxing.ChxWorkCourse
import java.io.IOException

internal data class LearningSnapshot(
    val account: String,
    val courses: List<CourseHomework> = emptyList(),
    val workLoaded: Boolean = false,
    val overview: ChxCourseOverview? = null,
    val readControl: LearningReadControl = LearningReadControl(),
)

internal interface LearningPersistence {
    fun read(): LearningSnapshot?
    fun write(snapshot: LearningSnapshot)
    fun clear()
}

/** 独立密钥/文件；不存密码、Cookie、HTML，手工 JSON 不依赖反射字段名。 */
@Suppress("DEPRECATION")
internal class LearningStore(context: Context) : LearningPersistence {
    private val appContext = context.applicationContext
    private val invalidated = appContext.noBackupFilesDir.resolve("study_learning_invalidated_v1")
    private val preferences by lazy {
        val key = MasterKey.Builder(appContext, "study_learning_master_v1")
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(appContext, "study_learning_secure_v1", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    override fun read(): LearningSnapshot? {
        if (invalidated.exists()) return null
        return preferences.getString("snapshot", null)?.let(LearningSnapshotCodec::decode)
    }
    override fun write(snapshot: LearningSnapshot) {
        if (!preferences.edit().putString("snapshot", LearningSnapshotCodec.encode(snapshot)).commit())
            throw IOException("学习缓存未能保存")
        if (invalidated.exists() && !invalidated.delete()) throw IOException("学习缓存未能启用")
    }
    override fun clear() {
        // 存储损坏时也保留失效标记，重启不能复现退出前的数据。
        invalidated.writeText("invalid")
        if (!preferences.edit().clear().commit()) throw IOException("旧学习缓存未能清除")
        if (!invalidated.delete()) throw IOException("学习缓存清理未完成")
    }
}

internal object LearningSnapshotCodec {
    fun encode(snapshot: LearningSnapshot): String = JsonObject().apply {
        addProperty("version", 1)
        addProperty("account", snapshot.account)
        addProperty("workLoaded", snapshot.workLoaded)
        add("readControl", JsonObject().apply {
            snapshot.readControl.let { control ->
                control.pause?.let { pause ->
                    addProperty("verificationRequired", pause.verificationRequired)
                    addProperty("retryAt", pause.retryAt)
                }
                addProperty("blockedCourseKey", control.blockedCourseKey)
                addProperty("progressBlocked", control.progressBlocked)
                addProperty("verificationUrl", control.verificationUrl)
                add("pending", JsonArray().apply { control.pendingCourseKeys.forEach(::add) })
            }
        })
        add("works", JsonArray().apply {
            snapshot.courses.forEach { row -> add(JsonObject().apply {
                addProperty("key", row.courseKey)
                addProperty("updatedAt", row.updatedAt)
                addProperty("failure", row.failure)
                addProperty("authRequired", row.authRequired)
                row.course?.let { course -> add("course", JsonObject().apply {
                    addProperty("courseId", course.courseId)
                    addProperty("classId", course.classId)
                    addProperty("cpi", course.cpi)
                    addProperty("name", course.name)
                    addProperty("url", course.url)
                }) }
                add("items", JsonArray().apply { row.works.forEach { work -> add(JsonObject().apply {
                    addProperty("key", work.key)
                    addProperty("courseName", work.courseName)
                    addProperty("title", work.title)
                    addProperty("status", work.status)
                    addProperty("dueAt", work.dueAt)
                    addProperty("detailUrl", work.detailUrl)
                }) } })
            }) }
        })
        snapshot.overview?.let { overview -> add("overview", JsonObject().apply {
            addProperty("loadedAt", overview.loadedAt)
            addProperty("progressError", overview.progressError)
            addProperty("progressAuthRequired", overview.progressAuthRequired)
            add("courses", JsonArray().apply { overview.courses.forEach { course -> add(JsonObject().apply {
                addProperty("clazzId", course.clazzId)
                addProperty("courseId", course.courseId)
                addProperty("curPersonId", course.curPersonId)
                addProperty("name", course.name)
                addProperty("description", course.description)
                addProperty("url", course.url)
                course.progress?.let { progress -> add("progress", JsonObject().apply {
                    addProperty("count", progress.jobCount)
                    addProperty("finished", progress.jobFinishCount)
                    addProperty("rate", progress.jobRate)
                }) }
            }) } })
        }) }
    }.toString()

    fun decode(encoded: String): LearningSnapshot {
        require(encoded.length <= 4 * 1024 * 1024)
        val root = JsonParser.parseString(encoded).asJsonObject
        require(root.get("version").asInt == 1)
        val account = root.get("account").asString.also { require(it.matches(Regex("[0-9a-f]{64}"))) }
        val courses = root.getAsJsonArray("works").map { entry ->
            val row = entry.asJsonObject
            val key = row.get("key").asString.also { require(it.matches(Regex("[0-9]+:[0-9]+:[0-9]+"))) }
            val course = row.getAsJsonObject("course")?.let {
                ChxWorkCourse(it.get("courseId").asLong, it.get("classId").asLong, it.get("cpi").asLong,
                    it.get("name").asString, safeUrl(it.get("url").asString)).also { value ->
                    require(value.courseId > 0 && value.classId > 0 && value.cpi > 0 && value.cacheKey() == key)
                }
            }
            CourseHomework(key, row.getAsJsonArray("items").map { element ->
                val item = element.asJsonObject
                Homework(
                    item.get("key").asString.also { require(it.startsWith("$key:")) },
                    item.get("courseName").asString, item.get("title").asString,
                    item.get("status").asString.also { require(it in setOf("未交", "已完成", "已提交")) },
                    item.get("dueAt").asLong.coerceAtLeast(0), safeUrl(item.get("detailUrl").asString),
                )
            }.also { require(it.distinctBy(Homework::key).size == it.size) },
                row.get("updatedAt").asLong.coerceAtLeast(0), course, row.textOrNull("failure"), row.get("authRequired").asBoolean)
        }.also { require(it.distinctBy(CourseHomework::courseKey).size == it.size) }
        val overview = root.getAsJsonObject("overview")?.let { value ->
            ChxCourseOverview(value.getAsJsonArray("courses").map { element ->
                val row = element.asJsonObject
                val progress = row.getAsJsonObject("progress")?.let {
                    ChxCourseProgress(it.get("count").asInt, it.get("finished").asInt, it.get("rate").asDouble)
                }
                ChxCourse(id(row, "clazzId"), id(row, "courseId"), id(row, "curPersonId"), row.get("name").asString,
                    row.get("description").asString, safeUrl(row.get("url").asString), progress)
            }.also { require(it.distinctBy(ChxCourse::info).size == it.size) }, value.textOrNull("progressError"),
                value.get("loadedAt").asLong.coerceAtLeast(0), value.get("progressAuthRequired").asBoolean)
        }
        val control = root.getAsJsonObject("readControl")?.let { value ->
            fun courseKey(key: String) = key.also { require(it.matches(Regex("[0-9]+:[0-9]+:[0-9]+"))) }
            LearningReadControl(
                pause = value.get("verificationRequired")?.let {
                    com.menglimi.chaoxing.ChxRequestPause(it.asBoolean, value.get("retryAt").asLong.also { time -> require(time >= 0) })
                },
                blockedCourseKey = value.textOrNull("blockedCourseKey")?.let(::courseKey),
                progressBlocked = value.get("progressBlocked")?.asBoolean ?: false,
                pendingCourseKeys = value.getAsJsonArray("pending")?.map { courseKey(it.asString) }?.distinct().orEmpty(),
                verificationUrl = value.textOrNull("verificationUrl")?.let(::safeUrl),
            )
        } ?: LearningReadControl()
        return LearningSnapshot(account, courses, root.get("workLoaded").asBoolean, overview, control)
    }

    private fun JsonObject.textOrNull(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString
    private fun safeUrl(value: String): String = value.also { require(LearningUrls.trusted(it) != null) }
    private fun id(row: JsonObject, name: String): String = row.get(name).asString.also {
        require(it.all { character -> character in '0'..'9' } && (it.toLongOrNull() ?: 0) > 0)
    }
}
