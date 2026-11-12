package com.menglimi.gdust.v2.data

import java.net.URLEncoder

/**
 * 学习通单点入口。
 *
 * 来源（不是猜的）：
 * - 门户前端 `portal.gdust.edu.cn/index.js` 的应用磁贴：
 *   `cas.gdust.edu.cn/lyuapServer/login?service=http://gdkjxy.fysso.chaoxing.com/sso/gdkjxy`（超星学习通 / 广科智课）
 *   `gdkj.mh.chaoxing.com/sso/login/3rd?wfwfid=420&refer=…`（新版超星课程中心，isSinglePoint）
 * - 实测：未登录时 `http://gdkjxy.fysso.chaoxing.com/sso/gdkjxy` 302 回 `cas.gdust.edu.cn/lyuapServer/login`，
 *   登录页标题「广科统一身份认证」——学习通与教务共用同一套 CAS。
 *
 * 因此联动方式是复用 CAS 网页会话（TGC 存在 WebView 的 CookieManager 里）：
 * 用户在同一 WebView 里登录一次，之后学习通和教务网页都不再要密码。
 * CAS ticket 一次性，不能拿同一张票换两个系统，所以这里不做 ticket 复用。
 */
object ChxSso {
    private const val CAS_LOGIN = "https://cas.gdust.edu.cn/lyuapServer/login?service="

    /** 泛雅 / 广科智课（机构 fid=420）。该加速域没开 HTTPS，实测 443 无响应，必须走 http。 */
    private const val FYSSO_SERVICE = "http://gdkjxy.fysso.chaoxing.com/sso/gdkjxy"

    /** 新版超星课程中心。 */
    private const val MH_SERVICE =
        "https://gdkj.mh.chaoxing.com/sso/login/3rd?wfwfid=420&refer=https%3A%2F%2Fgdkj.mh.chaoxing.com"

    /** 教务（正方）网页入口，同样走 CAS，验证「一次登录双通」用。 */
    private const val TEACHING_SERVICE = "http://gkjx.gdust.edu.cn/sso/lyiotlogin"

    private fun sso(service: String) = CAS_LOGIN + URLEncoder.encode(service, "UTF-8")

    val fyssoUrl: String get() = sso(FYSSO_SERVICE)
    val mhUrl: String get() = sso(MH_SERVICE)
    val teachingWebUrl: String get() = sso(TEACHING_SERVICE)

    /** 探测超星会话用的域名；有 JSESSIONID 类 Cookie 即视为已生效。 */
    val cookieProbes = listOf(
        "https://mooc2-ans.chaoxing.com",
        "https://i.chaoxing.com",
        "https://www.fanya.chaoxing.com",
    )

    fun isChaoxingHost(host: String?): Boolean {
        val h = host.orEmpty().lowercase()
        return h.endsWith("chaoxing.com") || h.endsWith("chaoxing.cn")
    }
}
