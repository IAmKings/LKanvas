package io.lkanvas.core.accessibility

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IndicatorResultState

/**
 * 无障碍摘要（PRD R-13/T-27，A-40 基础）：纯函数生成 TalkBack 可读文本。
 *
 * 涨跌不只依赖颜色：摘要包含文本方向描述（涨/跌/平，PRD R-13）。
 * Missing 指标值显示「—」。
 */
object AccessibilitySummary {

    /** 单根 candle 摘要（含与前一收盘比较的方向词）。 */
    fun candleSummary(
        candle: Candle,
        prevClose: Double?,
        timeText: String,
        priceFormat: (Double) -> String = { defaultPrice(it) },
        volumeFormat: (Double?) -> String = { defaultVolume(it) },
    ): String {
        val direction = when {
            prevClose == null -> ""
            candle.close > prevClose -> "，较前一根上涨"
            candle.close < prevClose -> "，较前一根下跌"
            else -> "，与前一根持平"
        }
        val volume = if (candle.volume != null) "，成交量 ${volumeFormat(candle.volume)}" else ""
        return "$timeText：开 ${priceFormat(candle.open)}，高 ${priceFormat(candle.high)}，" +
            "低 ${priceFormat(candle.low)}，收 ${priceFormat(candle.close)}$volume$direction"
    }

    /** 指标行摘要（Missing → 「—」）。 */
    fun indicatorSummary(
        definitionName: String,
        plots: List<String>,
        values: List<Double>,
        priceFormat: (Double) -> String = { defaultPrice(it) },
    ): String {
        val parts = plots.mapIndexed { i, plot ->
            val v = values.getOrNull(i)
            "$plot ${if (v != null && v.isFinite()) priceFormat(v) else "—"}"
        }
        return "$definitionName：${parts.joinToString("，")}"
    }

    /** 图表整体摘要（选中位置上下文）。 */
    fun chartSummary(selectedText: String?, latestText: String?, candleCount: Int): String =
        buildString {
            append("K线图，共 $candleCount 根数据。")
            if (selectedText != null) append("当前选中：$selectedText")
            else if (latestText != null) append("最新：$latestText")
            append("可使用无障碍动作切换上一根/下一根、放大缩小。")
        }

    private fun defaultPrice(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.2f", v)

    private fun defaultVolume(v: Double?): String = when {
        v == null -> "—"
        v >= 1e8 -> String.format("%.2f亿", v / 1e8)
        v >= 1e4 -> String.format("%.2f万", v / 1e4)
        else -> String.format("%.0f", v)
    }

    /** 从指标状态提取行值（行内 NaN → null）。 */
    fun indicatorValues(state: IndicatorResultState?, index: Int): List<Double>? {
        state ?: return null
        if (index < 0 || index >= state.result.size()) return null
        return state.result.keys.map { k ->
            state.result[k][index].takeIf { it.isFinite() } ?: Double.NaN
        }
    }
}
