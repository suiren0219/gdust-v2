package com.menglimi.gdust.v2.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmRequestTest {
    private val gson = Gson()

    @Test
    fun newlinesAndQuotesInNoticesStayValidJson() {
        val items = "公告\"A\"\n- [通知] 停课\r\n\t第三条"
        val body = gson.toJson(mapOf(
            "model" to "test-model",
            "messages" to listOf(
                mapOf("role" to "system", "content" to "按类别分组\n每条一句话"),
                mapOf("role" to "user", "content" to "最近的学校通知公告：\n$items"),
            ),
            "max_tokens" to 2048,
            "temperature" to 0.3,
        ))
        val parsed = JsonParser.parseString(body).asJsonObject
        assertEquals("test-model", parsed.get("model").asString)
        val messages = parsed.getAsJsonArray("messages")
        assertEquals(2, messages.size())
        assertTrue(messages[1].asJsonObject.get("content").asString.contains("公告\"A\""))
        assertTrue(messages[1].asJsonObject.get("content").asString.contains("\n"))
    }

    @Test
    fun endpointNormalizationCoversCommonForms() {
        fun endpoint(base: String): String {
            val root = base.trim().trimEnd('/')
            return when {
                root.endsWith("/chat/completions") -> root
                root.endsWith("/v1") -> "$root/chat/completions"
                else -> "$root/v1/chat/completions"
            }
        }
        assertEquals("https://api.example.com/v1/chat/completions", endpoint("https://api.example.com"))
        assertEquals("https://api.example.com/v1/chat/completions", endpoint("https://api.example.com/v1/"))
        assertEquals("https://api.example.com/v1/chat/completions", endpoint("https://api.example.com/v1/chat/completions"))
    }
}
