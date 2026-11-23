package com.menglimi.gdust.v2.ui

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.menglimi.gdust.v2.data.Repository
import com.menglimi.gdust.v2.data.SchoolService
import com.menglimi.gdust.v2.ui.screens.SchoolLoginActivity
import java.util.UUID

private val schoolLoginHostProcess = UUID.randomUUID().toString()

/** 宿主只保存结果与请求标识，不将网页凭据或回跳 URL 放进页面状态。 */
data class SchoolLoginResult(
    val requestId: String,
    val sessionVersion: Long,
    val portal: Boolean,
    val teaching: Boolean,
    val chx: Boolean,
    val message: String?,
) {
    val anyReady: Boolean get() = portal || teaching || chx
    val scheduleService: SchoolService? get() = when {
        teaching -> SchoolService.TEACHING
        portal -> SchoolService.PORTAL
        else -> null
    }
}

internal fun acceptsSchoolLoginResult(
    pendingId: String?, resultId: String?, pendingVersion: Long,
    resultVersion: Long, currentVersion: Long,
): Boolean = !pendingId.isNullOrBlank() && pendingId == resultId &&
    pendingVersion == resultVersion && resultVersion == currentVersion

class SchoolLoginHost internal constructor(
    val result: SchoolLoginResult?,
    val launch: (SchoolService?) -> Unit,
    val consume: () -> Unit,
)

/** 注册在 MainActivity 的导航 key 外，小部件跳转不会销毁尚未接收的登录结果。 */
@Composable
fun rememberSchoolLoginHost(repo: Repository): SchoolLoginHost {
    val context = LocalContext.current
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingVersion by rememberSaveable { mutableStateOf(-1L) }
    var pendingProcess by rememberSaveable { mutableStateOf(schoolLoginHostProcess) }
    var completed by rememberSaveable { mutableStateOf<Bundle?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { returned ->
        val data = returned.data
        val resultId = data?.getStringExtra(SchoolLoginActivity.EXTRA_REQUEST_ID)
        val resultVersion = data?.getLongExtra(SchoolLoginActivity.EXTRA_SESSION_VERSION, -1L) ?: -1L
        if (pendingProcess == schoolLoginHostProcess &&
            acceptsSchoolLoginResult(pendingId, resultId, pendingVersion, resultVersion, repo.sessionVersion.value)) {
            completed = Bundle().apply {
                putString("process", schoolLoginHostProcess)
                putString("request", resultId)
                putLong("version", resultVersion)
                putBoolean("portal", data?.getBooleanExtra(SchoolLoginActivity.EXTRA_PORTAL_READY, false) == true)
                putBoolean("teaching", data?.getBooleanExtra(SchoolLoginActivity.EXTRA_TEACHING_READY, false) == true)
                putBoolean("chx", data?.getBooleanExtra(SchoolLoginActivity.EXTRA_CHX_READY, false) == true)
                putString("message", data?.getStringExtra(SchoolLoginActivity.EXTRA_MESSAGE))
            }
        }
        if (resultId == pendingId || data == null) pendingId = null
    }
    val result = completed?.takeIf { it.getString("process") == schoolLoginHostProcess }?.let {
        SchoolLoginResult(
            requestId = it.getString("request").orEmpty(), sessionVersion = it.getLong("version"),
            portal = it.getBoolean("portal"), teaching = it.getBoolean("teaching"), chx = it.getBoolean("chx"),
            message = it.getString("message")?.takeIf(String::isNotBlank),
        )
    }
    return SchoolLoginHost(result, launch = { service ->
        if (pendingId == null || pendingProcess != schoolLoginHostProcess ||
            pendingVersion != repo.sessionVersion.value) {
            val requestId = UUID.randomUUID().toString()
            pendingId = requestId
            pendingVersion = repo.sessionVersion.value
            pendingProcess = schoolLoginHostProcess
            completed = null
            val intent = if (service == null) SchoolLoginActivity.intent(context)
                else SchoolLoginActivity.serviceIntent(context, service)
            try {
                launcher.launch(intent.putExtra(SchoolLoginActivity.EXTRA_REQUEST_ID, requestId))
            } catch (error: Exception) {
                pendingId = null
                throw error
            }
        }
    }, consume = { completed = null })
}
