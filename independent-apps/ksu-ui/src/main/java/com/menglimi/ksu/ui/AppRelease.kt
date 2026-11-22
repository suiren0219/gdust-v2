package com.menglimi.ksu.ui

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

object SuirenProject {
    const val AUTHOR = "穗稔 · suiren0219"
    const val HOME = "https://github.com/suiren0219/gdust-v2"
    const val RELEASES = "$HOME/releases"
    const val RELEASE_API = "https://api.github.com/repos/suiren0219/gdust-v2/releases?per_page=100"
}

data class AppRelease(val version: String, val page: String, val download: String, val preview: Boolean)

internal data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int = compareValuesBy(this, other,
        AppVersion::major, AppVersion::minor, AppVersion::patch)

    companion object {
        fun parse(value: String): AppVersion? {
            val match = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(value) ?: return null
            val parts = match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
            return AppVersion(parts[0], parts[1], parts[2])
        }
    }
}

/** 只认本应用的附件与本仓库下载来源，主项目版本不能作为独立 App 的升级。 */
internal fun findAppRelease(json: String, artifact: String): AppRelease? {
    require(artifact in setOf("learning-assistant", "dorm-power"))
    val root = JsonParser.parseString(json)
    require(root.isJsonArray) { "更新列表格式无法识别" }
    val filename = Regex("^${Regex.escape(artifact)}-(\\d+\\.\\d+\\.\\d+)(-preview)?\\.apk$")
    return root.asJsonArray.flatMap { element ->
        if (!element.isJsonObject) return@flatMap emptyList()
        val release = element.asJsonObject
        if (release["draft"]?.asBoolean != false) return@flatMap emptyList()
        val page = release["html_url"]?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        if (!isReleaseUrl(page, download = false)) return@flatMap emptyList()
        val assets = release["assets"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return@flatMap emptyList()
        assets.mapNotNull { entry ->
            if (!entry.isJsonObject) return@mapNotNull null
            val asset = entry.asJsonObject
            val name = asset["name"]?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            val match = filename.matchEntire(name) ?: return@mapNotNull null
            val version = match.groupValues[1]
            val number = AppVersion.parse(version) ?: return@mapNotNull null
            val url = asset["browser_download_url"]?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            if (!isReleaseUrl(url, download = true) || URI(url).path.substringAfterLast('/') != name) return@mapNotNull null
            if (asset["state"]?.asString != "uploaded" || (asset["size"]?.asLong ?: 0L) <= 0) return@mapNotNull null
            number to AppRelease(version, page, url, release["prerelease"]?.asBoolean == true || match.groupValues[2].isNotEmpty())
        }
    }.maxWithOrNull(compareBy<Pair<AppVersion, AppRelease>> { it.first }.thenBy { !it.second.preview })?.second
}

private fun isReleaseUrl(value: String, download: Boolean): Boolean = runCatching {
    val uri = URI(value)
    val prefix = if (download) "/suiren0219/gdust-v2/releases/download/" else "/suiren0219/gdust-v2/releases/tag/"
    uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 && uri.userInfo == null &&
        uri.query == null && uri.fragment == null && uri.path.startsWith(prefix) &&
        uri.path.removePrefix(prefix).isNotBlank() && uri.normalize().path == uri.path
}.getOrDefault(false)

internal fun newerAppRelease(release: AppRelease, installed: String): Boolean {
    val current = AppVersion.parse(installed) ?: return false
    return (AppVersion.parse(release.version) ?: return false) > current
}

object AppReleaseClient {
    private val http = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false).build()

    suspend fun latest(artifact: String): AppRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(SuirenProject.RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "suiren-independent-apps").build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                if (response.code == 403 || response.code == 429) "GitHub 暂时限制请求，请稍后重试或打开发布页。"
                else "暂时无法读取 GitHub 更新（HTTP ${response.code}）。"
            }
            val body = response.peekBody(2L * 1024 * 1024 + 1).bytes()
            check(body.size <= 2 * 1024 * 1024) { "更新列表过大，请直接查看 GitHub 发布页。" }
            findAppRelease(body.toString(Charsets.UTF_8), artifact)
        }
    }
}
