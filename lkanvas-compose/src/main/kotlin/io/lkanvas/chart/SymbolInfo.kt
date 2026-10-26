package io.lkanvas.chart

/**
 * 品种信息——对齐上游 `common/SymbolInfo.ts`。
 * `precision` 默认 2（上游 SymbolDefaultPrecisionConstants）。
 */
data class SymbolInfo(
    val ticker: String,
    val pricePrecision: Int = 2,
    val volumePrecision: Int = 2,
) {
    init {
        require(pricePrecision >= 0 && volumePrecision >= 0) { "精度须非负" }
    }
}

/**
 * 周期——对齐上游 `common/Period.ts`。
 * [milliseconds] 供自定义周期；内置类型使用上游默认值。
 */
data class Period(
    val type: PeriodType,
    val milliseconds: Long? = null,
) {
    enum class PeriodType { SECOND, MINUTE, HOUR, DAY, WEEK, MONTH, YEAR }

    /** 周期毫秒数（自定义优先；内置类型取上游默认）。 */
    fun toMillis(): Long = milliseconds ?: when (type) {
        PeriodType.SECOND -> 1_000L
        PeriodType.MINUTE -> 60_000L
        PeriodType.HOUR -> 3_600_000L
        PeriodType.DAY -> 86_400_000L
        PeriodType.WEEK -> 7 * 86_400_000L
        PeriodType.MONTH -> 30 * 86_400_000L
        PeriodType.YEAR -> 365 * 86_400_000L
    }

    companion object {
        fun ofMinutes(n: Long) = Period(PeriodType.MINUTE, n * 60_000L)
        fun ofDays(n: Long) = Period(PeriodType.DAY, n * 86_400_000L)
    }
}
