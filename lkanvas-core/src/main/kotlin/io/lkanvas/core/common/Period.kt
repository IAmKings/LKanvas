package io.lkanvas.core.common

/**
 * 上游 `common/Period.ts` 的 Kotlin 镜像：周期。
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
