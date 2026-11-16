package com.menglimi.gdust.v2.ui.screens

import com.menglimi.gdust.v2.data.AuthFailed
import com.menglimi.gdust.v2.data.Captcha
import com.menglimi.gdust.v2.data.SchoolAuthenticationException
import com.menglimi.gdust.v2.data.isNetworkDown
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class LegacyLoginState(
    val captcha: Captcha? = null,
    val code: String = "",
    val loadingCaptcha: Boolean = false,
    val submitting: Boolean = false,
    val loginError: String? = null,
    val captchaError: String? = null,
) {
    fun canSubmit(account: String, password: String): Boolean = !loadingCaptcha && !submitting &&
        captcha != null && account.isNotBlank() && password.isNotBlank() && code.isNotBlank()
}

/** 登录结果独立于验证码加载；同一页面内只允许一个请求，取消后的响应不能覆盖新图片。 */
internal class LegacyLoginController(
    private val scope: CoroutineScope,
    private val fetchCaptcha: suspend () -> Captcha,
    private val login: suspend (String, String, String, String) -> Unit,
) {
    private val mutableState = MutableStateFlow(LegacyLoginState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var revision = 0L

    fun changeCode(code: String) {
        if (!state.value.loadingCaptcha && !state.value.submitting) {
            mutableState.value = state.value.copy(code = code)
        }
    }

    fun refreshCaptcha() {
        if (state.value.submitting) return
        val current = ++revision
        job?.cancel()
        // 立即清掉旧图片、输入和 uuid，加载失败也不能继续提交过期验证码。
        mutableState.value = state.value.copy(captcha = null, code = "", loadingCaptcha = true, captchaError = null)
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                loadCaptcha(current)
            } finally {
                if (revision == current) mutableState.value = state.value.copy(loadingCaptcha = false)
            }
        }.also { it.start() }
    }

    fun submit(account: String, password: String, onLoggedIn: () -> Unit) {
        val request = state.value
        if (!request.canSubmit(account, password)) return
        val captcha = request.captcha ?: return
        val current = ++revision
        job?.cancel()
        mutableState.value = request.copy(submitting = true, loginError = null, captchaError = null)
        job = scope.launch(start = CoroutineStart.LAZY) {
            var authenticated = false
            try {
                try {
                    login(account.trim(), password, request.code.trim(), captcha.uuid)
                    currentCoroutineContext().ensureActive()
                    if (revision != current) return@launch
                    authenticated = true
                    mutableState.value = state.value.copy(captcha = null, code = "")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (revision != current) return@launch
                    mutableState.value = state.value.copy(
                        captcha = null, code = "", loadingCaptcha = true,
                        loginError = when {
                            error is AuthFailed && error.code == 30001 -> "验证码错误或已过期，请输入新验证码后重试。"
                            error is AuthFailed -> "登录失败：${error.message?.takeIf { it.isNotBlank() } ?: "学校未接受当前账号和密码。"}"
                            error is SchoolAuthenticationException -> error.message
                            error.isNetworkDown() -> "连接学校登录服务失败，请检查网络后重试。"
                            else -> "登录未完成，请稍后重试或使用学校统一登录。"
                        },
                    )
                    loadCaptcha(current)
                }
            } finally {
                if (revision == current) mutableState.value = state.value.copy(submitting = false, loadingCaptcha = false)
            }
            // 页面跳转或后续同步不能被当作账号认证失败，再次刷新验证码。
            if (authenticated && revision == current) onLoggedIn()
        }.also { it.start() }
    }

    private suspend fun loadCaptcha(current: Long) {
        try {
            val captcha = fetchCaptcha()
            currentCoroutineContext().ensureActive()
            if (revision != current) return
            check(captcha.uuid.isNotBlank() && captcha.imageBytes.isNotEmpty())
            mutableState.value = state.value.copy(captcha = captcha, captchaError = null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            if (revision == current) mutableState.value = state.value.copy(
                captcha = null, captchaError = when (error) {
                    is SchoolAuthenticationException -> error.message
                    is AuthFailed -> "验证码加载失败：${error.message?.takeIf { it.isNotBlank() } ?: "请点击图片区域重试。"}"
                    else -> "验证码加载失败，请点击图片区域重试。"
                },
            )
        }
    }

    fun cancel() {
        ++revision
        job?.cancel()
        job = null
        mutableState.value = state.value.copy(captcha = null, code = "", loadingCaptcha = false, submitting = false)
    }
}
