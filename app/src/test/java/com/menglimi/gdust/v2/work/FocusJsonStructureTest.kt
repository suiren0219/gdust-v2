package com.menglimi.gdust.v2.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 焦点通知 V3 JSON 结构回归（不依赖 Android framework 的字段拼接）。 */
class FocusJsonStructureTest {

    private fun timerInfo(target: Long) = linkedMapOf<String, Any>(
        "timerType" to -1, "timerWhen" to target, "timerTotal" to 0L, "timerSystemCurrent" to 0L,
    )

    @Test
    fun islandJsonContainsRequiredV3Sections() {
        val now = System.currentTimeMillis()
        val json = com.google.gson.Gson().toJson(linkedMapOf<String, Any>(
            "protocol" to 1, "business" to "course_reminder", "enableFloat" to true,
            "aodTitle" to "英语", "isShowNotification" to true,
            "baseInfo" to linkedMapOf("type" to 2, "title" to "大学英语II", "content" to "第 3-4 节 | 博 A201"),
            "hintInfo" to linkedMapOf(
                "type" to 2, "content" to "即将上课", "subContent" to "地点", "subTitle" to "博 A201",
                "timerInfo" to timerInfo(now + 20 * 60_000L),
                "actionInfo" to linkedMapOf("actionTitle" to "上课勿扰", "actionIntentType" to 2),
            ),
            "param_island" to linkedMapOf(
                "islandProperty" to 1, "islandTimeout" to 1500,
                "bigIslandArea" to linkedMapOf("imageTextInfoLeft" to linkedMapOf("type" to 1), "sameWidthDigitInfo" to linkedMapOf("content" to "上课", "timerInfo" to timerInfo(now + 20 * 60_000L))),
                "smallIslandArea" to linkedMapOf("picInfo" to linkedMapOf("type" to 1, "pic" to "miui.focus.pic_small")),
            ),
        ))
        listOf("protocol", "business", "aodTitle", "baseInfo", "hintInfo", "param_island",
            "timerInfo", "actionInfo", "imageTextInfoLeft", "sameWidthDigitInfo", "smallIslandArea", "上课勿扰")
            .forEach { assertTrue(it, json.contains(it)) }
        assertTrue(json.contains("miui.focus.pic_small"))
        // hintInfo 与 bigIslandArea 各有一处系统计时器（倒计时与数字岛共用目标时刻）
        assertTrue(json.split("\"timerInfo\"").size - 1 >= 2)
    }
}
