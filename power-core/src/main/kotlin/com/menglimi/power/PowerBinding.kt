package com.menglimi.power

import com.google.gson.Gson
import java.security.MessageDigest

/** 电费服务只依赖这三个绑定字段，不需要学校门户登录态。 */
data class PowerBinding(val account: String, val code: Int, val verify: String) {
    val isValid: Boolean get() = account.isNotBlank() && code > 0 && verify.isNotBlank()

    /** 与原应用 cached_binding 的 Gson 数组 + SHA-256 算法一致。 */
    val key: String
        get() = MessageDigest.getInstance("SHA-256")
            .digest(gson.toJson(listOf(account, code, verify)).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        val gson = Gson()
    }
}
