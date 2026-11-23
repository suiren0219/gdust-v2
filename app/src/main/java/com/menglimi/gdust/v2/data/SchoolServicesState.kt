package com.menglimi.gdust.v2.data

/**
 * 单个学校服务的连接状态。
 *
 * [auth] 只认本次进程内的真实结果：[ServiceAuthStatus.VERIFIED] 不持久化，
 * 进程恢复一律 [ServiceAuthStatus.SAVED]（凭据待验证）。[problem] 与 auth 独立：
 * 业务读取失败保留过去 VERIFIED，只把问题标出来；明确拒绝才降到 AUTH_REQUIRED。
 */
public data class SchoolServiceStatus(
    val auth: ServiceAuthStatus = ServiceAuthStatus.NOT_CONNECTED,
    val checking: Boolean = false,
    val problem: ServiceProblem? = null,
    val checkedAt: Long = 0L,
)

/** 认证态：NOT_CONNECTED 无候选；SAVED 仅凭据待验证；VERIFIED 业务验证通过；AUTH_REQUIRED 明确拒绝。 */
public enum class ServiceAuthStatus { NOT_CONNECTED, SAVED, VERIFIED, AUTH_REQUIRED }

/** 非认证问题：NETWORK=网络不可达；READ=业务/格式读取失败；UNCONFIRMED=结果不确定，此前的认证保持。 */
public enum class ServiceProblem { NETWORK, READ, UNCONFIRMED }

/** 门户/教务/学习通三服务独立连接状态；任一服务的变化不影响其他服务。 */
public data class SchoolServicesState(
    val portal: SchoolServiceStatus = SchoolServiceStatus(),
    val teaching: SchoolServiceStatus = SchoolServiceStatus(),
    val chx: SchoolServiceStatus = SchoolServiceStatus(),
) {
    public operator fun get(service: SchoolService): SchoolServiceStatus = when (service) {
        SchoolService.PORTAL -> portal
        SchoolService.TEACHING -> teaching
        SchoolService.CHX -> chx
    }

    /** 任一服务通过本次业务验证。 */
    public val anyVerified: Boolean
        get() = SchoolService.entries.any { get(it).auth == ServiceAuthStatus.VERIFIED }

    /** 存在 SAVED 或 VERIFIED：本机有可继续使用的会话材料。 */
    public val hasRestorableSession: Boolean
        get() = SchoolService.entries.any {
            get(it).auth in setOf(ServiceAuthStatus.SAVED, ServiceAuthStatus.VERIFIED)
        }
}

/**
 * 三服务状态的纯转换器：所有写入带账号版本与学习通版本校验，旧探活晚到不能覆盖
 * 新登录/退出/同服务新验证。锁内完成状态更新与 [publish]（StateFlow 唯一源，不会写回旧快照）。
 *
 * 探活代数按服务单调递增，永不复用：beginProbe +1；finishProbe 校验后应用（不递增）；
 * cancelProbe 与不带令牌的 patch 都 +1 作废旧 token。
 *
 * @param accountVersion 初始为 MIN：首次 bindAccount 前一切写入被拒。
 */
internal class SchoolServicesTracker(
    initial: SchoolServicesState = SchoolServicesState(),
    private val publish: (SchoolServicesState) -> Unit = {},
) {

    private val lock = Any()
    private var state: SchoolServicesState = initial
    private var accountVersion: Long = Long.MIN_VALUE
    private var chxVersion: Long = Long.MIN_VALUE
    private val probeGenerations = HashMap<SchoolService, Long>()

    /** 锁内发布：保证 StateFlow 不会出现旧快照最后写回。 */
    private fun applyLocked(next: SchoolServicesState): SchoolServicesState {
        state = next
        publish(next)
        return next
    }

    private fun invalidateGenerationLocked(service: SchoolService) {
        probeGenerations[service] = (probeGenerations[service] ?: 0L) + 1L
    }

    fun snapshot(): SchoolServicesState = synchronized(lock) { state }

    /**
     * 绑定账号版本；仅版本变化时应用 [restored]（退出/切换账号重置）。
     * 供应商在锁内调用且仅在需要时执行，避免每次绑定都读存储。
     */
    fun bindAccount(version: Long, restored: () -> SchoolServicesState): Boolean = synchronized(lock) {
        if (version <= accountVersion) return false
        accountVersion = version
        chxVersion = Long.MIN_VALUE
        SchoolService.entries.forEach(::invalidateGenerationLocked)
        applyLocked(restored())
        true
    }

    /** 绑定学习通版本；只作废 CHX 的检查过程，不否认其凭据，也不影响其他服务。 */
    fun bindChx(version: Long, restored: SchoolServiceStatus? = null): Boolean = synchronized(lock) {
        if (version <= chxVersion) return false
        chxVersion = version
        invalidateGenerationLocked(SchoolService.CHX)
        applyLocked(state.copy(chx = (restored ?: state.chx).copy(checking = false)))
        true
    }

    fun accepts(service: SchoolService, accountVersion: Long, chxVersion: Long?, generation: Long): Boolean =
        synchronized(lock) {
            versionsMatch(service, accountVersion, chxVersion) && probeGenerations[service] == generation
        }

    /** 登记一次探活；版本不匹配返回 null，不占用代数。 */
    fun beginProbe(service: SchoolService, accountVersion: Long, chxVersion: Long?): Long? = synchronized(lock) {
        if (!versionsMatch(service, accountVersion, chxVersion)) return null
        val generation = (probeGenerations[service] ?: 0L) + 1L
        probeGenerations[service] = generation
        applyLocked(state.updated(service) { it.copy(checking = true) })
        generation
    }

    /** 结束探活并写入结果；auth=null 保持此前认证（不确定结果不否认已验证会话）。 */
    fun finishProbe(
        service: SchoolService,
        accountVersion: Long,
        chxVersion: Long?,
        generation: Long,
        auth: ServiceAuthStatus?,
        problem: ServiceProblem?,
        checkedAt: Long,
    ): SchoolServicesState? = synchronized(lock) {
        if (!versionsMatch(service, accountVersion, chxVersion) || probeGenerations[service] != generation) return null
        applyLocked(state.updated(service) {
            it.copy(auth = auth ?: it.auth, problem = problem, checkedAt = checkedAt, checking = false)
        })
    }

    /** 探活取消或结果已由内部记录：只结束检查过程，认证与问题保持；代数作废不复用。 */
    fun cancelProbe(service: SchoolService, accountVersion: Long, chxVersion: Long?, generation: Long): Boolean =
        synchronized(lock) {
            if (!versionsMatch(service, accountVersion, chxVersion) || probeGenerations[service] != generation) return false
            invalidateGenerationLocked(service)
            applyLocked(state.updated(service) { it.copy(checking = false) })
            true
        }

    /**
     * 单服务补丁；auth=null 保持此前认证，checkedAt=0 保持原检查时间。
     * generation=null：无令牌提交——作废进行中的探活并结束其检查过程（业务结果比旧探活新）。
     * generation=g：携带探活令牌的内部记录提交——校验通过后应用且不递增，外层 finishProbe 仍有效。
     */
    fun patch(
        service: SchoolService,
        accountVersion: Long,
        chxVersion: Long?,
        auth: ServiceAuthStatus?,
        problem: ServiceProblem?,
        checkedAt: Long,
        generation: Long? = null,
    ): SchoolServicesState? = synchronized(lock) {
        if (!versionsMatch(service, accountVersion, chxVersion)) return null
        if (generation == null) {
            invalidateGenerationLocked(service)
            applyLocked(state.updated(service) {
                it.copy(
                    auth = auth ?: it.auth,
                    problem = problem,
                    checkedAt = if (checkedAt == 0L) it.checkedAt else checkedAt,
                    checking = false,
                )
            })
        } else {
            if (probeGenerations[service] != generation) return null
            applyLocked(state.updated(service) {
                it.copy(
                    auth = auth ?: it.auth,
                    problem = problem,
                    checkedAt = if (checkedAt == 0L) it.checkedAt else checkedAt,
                )
            })
        }
    }

    private fun versionsMatch(service: SchoolService, account: Long, chx: Long?): Boolean =
        account == this.accountVersion && (service != SchoolService.CHX || chx == this.chxVersion)

    private inline fun SchoolServicesState.updated(
        service: SchoolService,
        transform: (SchoolServiceStatus) -> SchoolServiceStatus,
    ): SchoolServicesState = when (service) {
        SchoolService.PORTAL -> copy(portal = transform(portal))
        SchoolService.TEACHING -> copy(teaching = transform(teaching))
        SchoolService.CHX -> copy(chx = transform(chx))
    }
}
