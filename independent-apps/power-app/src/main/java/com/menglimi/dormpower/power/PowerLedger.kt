package com.menglimi.dormpower.power

import com.menglimi.power.PowerReading
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** 金额以十进制字符串保存；每笔保留识别时电价，修改电价不改写旧账。 */
data class PowerLedgerEntry(
    val id: String = UUID.randomUUID().toString(),
    val time: Long,
    val fromTime: Long = time,
    val kwh: Double,
    val unitPrice: String = "0.6",
    val paidYuan: String? = null,
    val payer: String = "",
    val note: String = "",
    val detected: Boolean = false,
    val ignored: Boolean = false,
)

data class PowerDayCost(
    val date: String,
    val kwh: Double,
    val yuan: String,
    val intervals: Int,
    val firstAt: Long,
    val lastAt: Long,
)

data class PowerLedgerState(
    val unitPrice: String = "0.6",
    val entries: List<PowerLedgerEntry> = emptyList(),
    val days: List<PowerDayCost> = emptyList(),
)

object PowerLedger {
    fun price(text: String): BigDecimal? = decimal(text, 4)?.takeIf { it > BigDecimal.ZERO && it <= BigDecimal("100") }
    fun payment(text: String): BigDecimal? = decimal(text, 2)?.takeIf { it >= BigDecimal.ZERO && it <= BigDecimal("1000000") }
    private fun decimal(text: String, scale: Int): BigDecimal? = text.trim()
        .takeIf { it.matches(Regex("[0-9]{1,7}(\\.[0-9]{1,$scale})?")) }
        ?.toBigDecimalOrNull()
    fun yuan(kwh: Double?, unitPrice: String): String =
        if (kwh == null || !kwh.isFinite()) "—" else money(BigDecimal.valueOf(kwh).multiply(price(unitPrice) ?: BigDecimal("0.6")))
    fun money(value: BigDecimal): String = value.setScale(2, RoundingMode.HALF_UP).toPlainString()
    fun amount(entry: PowerLedgerEntry): BigDecimal = entry.paidYuan?.let(::payment)
        ?: BigDecimal.valueOf(entry.kwh).multiply(price(entry.unitPrice) ?: BigDecimal("0.6"))
    fun date(time: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

    /** 仅比较连续成功读数，失败/重复/时钟回退均不生成账目。上涨只是疑似充值。 */
    fun record(state: PowerLedgerState, before: PowerReading?, after: PowerReading?, zone: ZoneId): PowerLedgerState {
        val old = before?.detail?.odd ?: return state
        val current = after?.detail?.odd ?: return state
        if (!old.isFinite() || !current.isFinite() || before.updatedAt <= 0 || after.updatedAt <= before.updatedAt) return state
        val change = BigDecimal.valueOf(current).subtract(BigDecimal.valueOf(old))
        if (change.abs() < BigDecimal("0.000001")) return state
        if (change.signum() > 0) {
            val id = "observed:${before.updatedAt}:${after.updatedAt}"
            if (state.entries.any { it.id == id }) return state
            return state.copy(entries = state.entries + PowerLedgerEntry(id, after.updatedAt, before.updatedAt,
                change.toDouble(), state.unitPrice, detected = true))
        }
        val used = change.negate()
        val day = date(after.updatedAt, zone).toString()
        val previous = state.days.find { it.date == day }
        val total = (previous?.yuan?.toBigDecimalOrNull() ?: BigDecimal.ZERO)
            .add(used.multiply(price(state.unitPrice) ?: BigDecimal("0.6")))
        val next = PowerDayCost(day, (previous?.kwh ?: 0.0) + used.toDouble(), total.toPlainString(),
            (previous?.intervals ?: 0) + 1, previous?.firstAt ?: before.updatedAt, after.updatedAt)
        return state.copy(days = (state.days.filterNot { it.date == day } + next).sortedBy { it.date })
    }

    fun validate(entry: PowerLedgerEntry) {
        require(entry.id.isNotBlank() && entry.id.length <= 100 && entry.time > 0) { "记录日期无效" }
        require(entry.kwh.isFinite() && entry.kwh > 0 && entry.kwh <= 10_000_000) { "请输入有效购电度数" }
        require(price(entry.unitPrice) != null) { "电价应大于 0 且不超过 100 元/度，最多四位小数" }
        require(entry.paidYuan == null || payment(entry.paidYuan) != null) { "请输入有效实付金额，最多两位小数" }
        require(entry.payer.length <= 40 && entry.note.length <= 500) { "充值人最多 40 字，备注最多 500 字" }
    }
}
