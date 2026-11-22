package com.menglimi.power

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 本机历史按天聚合，不请求网络；余额为 0 或负数仍是有效历史。 */
object PowerHistory {
    data class DayStat(
        val date: LocalDate,
        val samples: List<PowerSample>,
        val lastOdd: Double,
        /** 当天末值减首值；正数可能来自充值，不能直接当作耗电量。 */
        val dayChange: Double,
    )

    fun group(samples: List<PowerSample>, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, DayStat> =
        samples.filter { it.time > 0 && it.odd.isFinite() }
            .groupBy { Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() }
            .mapValues { (date, list) ->
                val sorted = list.sortedBy { it.time }
                DayStat(date, sorted, sorted.last().odd, sorted.last().odd - sorted.first().odd)
            }
}
