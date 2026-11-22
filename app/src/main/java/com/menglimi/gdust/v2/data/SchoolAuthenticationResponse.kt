package com.menglimi.gdust.v2.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Base64

internal enum class SchoolAuthenticationStage(val label: String) {
    CAPTCHA("验证码加载"), ACCOUNT("账号认证"), PORTAL("门户登录"),
}

/** 只展示当前阶段与客户端定义的原因，不把响应正文或一次性票据带入提示。 */
internal class SchoolAuthenticationException(
    val stage: SchoolAuthenticationStage,
    detail: String,
    cause: Throwable? = null,
) : Exception("${stage.label}未完成：$detail", cause)

internal fun schoolAuthenticationResponse(body: String, stage: SchoolAuthenticationStage): JsonObject {
    val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
        ?: throw SchoolAuthenticationException(stage, "学校返回的数据格式异常，请稍后重试。")
    val code = runCatching {
        root.get("code").takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull()
    }.getOrNull() ?: throw SchoolAuthenticationException(stage, "学校未返回明确的认证结果，请稍后重试。")
    if (code != 0) {
        if (stage == SchoolAuthenticationStage.PORTAL) {
            if (code == 116 || code == 117) throw AuthFailed(code, "门户未接受这次登录，请重新完成学校统一登录。")
            throw SchoolAuthenticationException(stage, "学校返回错误码 $code，请重试或使用学校统一登录。")
        }
        val message = root.stringField("msg")?.takeIf { it.isNotBlank() } ?: "学校未接受这次请求（错误码 $code）。"
        throw AuthFailed(code, message)
    }
    return root
}

internal fun parseSchoolCaptcha(body: String): Captcha {
    val stage = SchoolAuthenticationStage.CAPTCHA
    val root = schoolAuthenticationResponse(body, stage)
    val data = root.objectField("data")
    val uuid = data?.stringField("uuid")?.takeIf { it.isNotBlank() }
    val image = data?.stringField("codeUrl")?.let { value ->
        runCatching { Base64.getDecoder().decode(value.substringAfter("base64,")) }.getOrNull()
    }?.takeIf { it.isNotEmpty() }
    if (uuid == null || image == null) {
        throw SchoolAuthenticationException(stage, "学校返回的验证码不完整，请重新加载。")
    }
    return Captcha(image, uuid)
}

internal fun parseSchoolLoginTicket(body: String): String =
    schoolAuthenticationResponse(body, SchoolAuthenticationStage.ACCOUNT).stringField("data")
        ?.takeIf { it.isNotBlank() }
        ?: throw SchoolAuthenticationException(SchoolAuthenticationStage.ACCOUNT,
            "学校未返回有效登录票据，请重试或使用学校统一登录。")

internal fun parseSchoolPortalToken(body: String): String =
    schoolAuthenticationResponse(body, SchoolAuthenticationStage.PORTAL)
        .objectField("data")?.objectField("userBase")?.stringField("token")?.takeIf { it.isNotBlank() }
        ?: throw SchoolAuthenticationException(SchoolAuthenticationStage.PORTAL,
            "学校未返回有效门户会话，请重新完成学校统一登录。")

private fun JsonObject.stringField(name: String): String? = get(name)
    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

private fun JsonObject.objectField(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject
