package com.menglimi.kedian.learning

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException

internal data class LearningSnapshot(
    val account: String,
    val courses: List<CourseHomework>,
    val failedCourses: Int,
)

/** 只存作业快照与账号指纹，不存 Cookie、密码或 HTML；手动 JSON 不依赖反射字段名。 */
@Suppress("DEPRECATION")
internal class LearningStore(context: Context) {
    private val appContext = context.applicationContext
    private val invalidated = appContext.noBackupFilesDir.resolve("campus_lite_learning_invalidated")
    private val preferences by lazy {
        val key = MasterKey.Builder(appContext, "campus_lite_learning_master_v1")
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            appContext,
            "campus_lite_learning_secure_v1",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun read(): LearningSnapshot? {
        if (invalidated.exists()) return null
        val encoded = preferences.getString("snapshot", null) ?: return null
        val root = JsonParser.parseString(encoded).asJsonObject
        require(root.get("version").asInt == 1)
        val account = root.get("account").asString.also { require(it.matches(Regex("[0-9a-f]{64}"))) }
        val courses = root.getAsJsonArray("courses").map { entry ->
            val row = entry.asJsonObject
            val courseKey = row.get("key").asString.also { require(it.matches(Regex("[0-9]+:[0-9]+:[0-9]+"))) }
            CourseHomework(
                courseKey,
                row.getAsJsonArray("works").map { item ->
                    val work = item.asJsonObject
                    Homework(
                        key = work.get("key").asString.also { require(it.startsWith("$courseKey:")) },
                        courseName = work.get("courseName").asString,
                        title = work.get("title").asString,
                        status = work.get("status").asString,
                        dueAt = work.get("dueAt").asLong.coerceAtLeast(0L),
                        detailUrl = work.get("detailUrl").asString.also { require(LearningUrls.trusted(it) != null) },
                    )
                },
                row.get("updatedAt").asLong.coerceAtLeast(0L),
            )
        }
        return LearningSnapshot(account, courses, root.get("failedCourses").asInt.coerceAtLeast(0))
    }

    fun write(snapshot: LearningSnapshot) {
        val root = JsonObject().apply {
            addProperty("version", 1)
            addProperty("account", snapshot.account)
            addProperty("failedCourses", snapshot.failedCourses)
            add("courses", JsonArray().apply {
                snapshot.courses.forEach { course ->
                    add(JsonObject().apply {
                        addProperty("key", course.courseKey)
                        addProperty("updatedAt", course.updatedAt)
                        add("works", JsonArray().apply {
                            course.works.forEach { work ->
                                add(JsonObject().apply {
                                    addProperty("key", work.key)
                                    addProperty("courseName", work.courseName)
                                    addProperty("title", work.title)
                                    addProperty("status", work.status)
                                    addProperty("dueAt", work.dueAt)
                                    addProperty("detailUrl", work.detailUrl)
                                })
                            }
                        })
                    })
                }
            })
        }
        if (!preferences.edit().putString("snapshot", root.toString()).commit()) throw IOException("作业缓存未能保存")
        if (invalidated.exists() && !invalidated.delete()) throw IOException("作业缓存未能启用")
    }

    fun clear() {
        // 即使加密存储损坏，重启后也不能重新显示退出前的缓存。
        invalidated.writeText("invalid")
        if (!preferences.edit().clear().commit()) throw IOException("旧作业缓存未能清除")
        if (!invalidated.delete()) throw IOException("作业缓存清理未完成")
    }
}
