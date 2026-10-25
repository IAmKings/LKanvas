package io.lkanvas.core.data

/**
 * 单根 K 线数据点（不可变，PRD R-01）。
 *
 * - 时间为 epoch 毫秒（UTC 存储，不随显示时区改变）。
 * - OHLC 使用 Double，必须有限；允许负价格（线性轴上正常显示）。
 * - volume/turnover 缺失以 null 表达，不自动当作 0（上游部分指标按 0 处理，见差异 D-03）。
 *   已提供的 volume/turnover 要求有限且非负。
 */
data class Candle(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double? = null,
    val turnover: Double? = null,
)

/** 单根 candle 或批次校验失败的原因。 */
enum class CandleInvalidReason {
    /** open/high/low/close 出现 NaN 或 Infinity */
    OHLC_NOT_FINITE,

    /** volume/turnover 已提供但为 NaN/Infinity 或负数 */
    VOLUME_NEGATIVE_OR_NOT_FINITE,

    /** 不满足 low <= min(open, close) <= max(open, close) <= high */
    OHLC_ORDER_VIOLATED,

    /** 与前一根时间戳相同（重复） */
    TIMESTAMP_DUPLICATED,

    /** 时间戳小于等于批次中已存在的更晚时间戳（逆序） */
    TIMESTAMP_NOT_INCREASING,
}

/**
 * 定位错误：包含发生位置（批次内索引或 -1 表示批次级）、原因与可读说明（PRD R-01：
 * 「错误返回包含操作、索引和原因」；操作名由提交方在 Store 层附加）。
 */
data class CandleValidationIssue(
    val index: Int,
    val reason: CandleInvalidReason,
    val detail: String,
)

/**
 * candle 批次校验器（纯函数，无状态，任意线程可调用）。
 *
 * 校验规则：
 * 1. 每根 OHLC 有限；已提供的 volume/turnover 有限且非负。
 * 2. 满足 low <= min(open, close) <= max(open, close) <= high。
 * 3. 批次内时间戳严格递增（不接受重复；禁止静默排序掩盖错误来源）。
 * 4. 若批次衔接既有数据（[lastTimestamp] 非空），首根时间戳必须大于它。
 *
 * 返回全部问题（不因首个失败短路，便于宿主一次性修复）；空列表表示通过。
 */
object CandleValidator {

    fun validateBatch(
        candles: List<Candle>,
        lastTimestamp: Long? = null,
    ): List<CandleValidationIssue> {
        val issues = mutableListOf<CandleValidationIssue>()
        var prevTs: Long? = lastTimestamp
        candles.forEachIndexed { i, c ->
            if (!c.open.isFinite() || !c.high.isFinite() || !c.low.isFinite() || !c.close.isFinite()) {
                issues += CandleValidationIssue(
                    index = i,
                    reason = CandleInvalidReason.OHLC_NOT_FINITE,
                    detail = "第 $i 根 OHLC 含非有限值：open=${c.open} high=${c.high} low=${c.low} close=${c.close}",
                )
            }
            c.volume?.let { v ->
                if (!v.isFinite() || v < 0) {
                    issues += CandleValidationIssue(
                        index = i,
                        reason = CandleInvalidReason.VOLUME_NEGATIVE_OR_NOT_FINITE,
                        detail = "第 $i 根 volume 非有限或为负：$v",
                    )
                }
            }
            c.turnover?.let { t ->
                if (!t.isFinite() || t < 0) {
                    issues += CandleValidationIssue(
                        index = i,
                        reason = CandleInvalidReason.VOLUME_NEGATIVE_OR_NOT_FINITE,
                        detail = "第 $i 根 turnover 非有限或为负：$t",
                    )
                }
            }
            if (c.open.isFinite() && c.high.isFinite() && c.low.isFinite() && c.close.isFinite()) {
                val bodyLow = minOf(c.open, c.close)
                val bodyHigh = maxOf(c.open, c.close)
                if (!(c.low <= bodyLow && bodyHigh <= c.high)) {
                    issues += CandleValidationIssue(
                        index = i,
                        reason = CandleInvalidReason.OHLC_ORDER_VIOLATED,
                        detail = "第 $i 根不满足 low<=min(o,c)<=max(o,c)<=high：o=${c.open} h=${c.high} l=${c.low} c=${c.close}",
                    )
                }
            }
            val pt = prevTs
            when {
                pt != null && c.timestamp == pt -> issues += CandleValidationIssue(
                    index = i,
                    reason = CandleInvalidReason.TIMESTAMP_DUPLICATED,
                    detail = "第 $i 根时间戳 ${c.timestamp} 与前一根重复",
                )
                pt != null && c.timestamp < pt -> issues += CandleValidationIssue(
                    index = i,
                    reason = CandleInvalidReason.TIMESTAMP_NOT_INCREASING,
                    detail = "第 $i 根时间戳 ${c.timestamp} 小于前一根 $pt（逆序）",
                )
            }
            prevTs = c.timestamp
        }
        return issues
    }
}
