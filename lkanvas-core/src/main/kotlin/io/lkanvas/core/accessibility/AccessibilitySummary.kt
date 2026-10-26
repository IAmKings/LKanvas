package io.lkanvas.core.accessibility

import io.lkanvas.core.data.Candle
import io.lkanvas.core.i18n.ChartLocale
import io.lkanvas.core.indicator.IndicatorResultState

/**
 * 无障碍摘要（PRD R-13/T-27，A-40 基础）：纯函数生成 TalkBack 可读文本。
 *
 * 涨跌不只依赖颜色：摘要包含文本方向描述（词条来自 [ChartLocale]，R-13/R-11）。
 * Missing 指标值显示 locale.missing（「—」）。
 */
object AccessibilitySummary {

    /** 单根 candle 摘要（含与前一收盘比较的方向词）。 */
    fun candleSummary(
        candle: Candle,
        prevClose: Double?,
        timeText: String,
        locale: ChartLocale = ChartLocale.ZH_CN,
        priceFormat: (Double) -> String = { defaultPrice(it) },
        volumeFormat: (Double?) -> String = { defaultVolume(it) },
    ): String {
        val direction = when {
            prevClose == null -> ""
            candle.close > prevClose -> locale.versusPrev(ChartLocale.Direction.UP)
            candle.close < prevClose -> locale.versusPrev(ChartLocale.Direction.DOWN)
            else -> locale.versusPrev(ChartLocale.Direction.FLAT)
        }
        val volume = if (candle.volume != null) "，${locale.volume} ${volumeFormat(candle.volume)}" else ""
        return "$timeText：开 ${priceFormat(candle.open)}，高 ${priceFormat(candle.high)}，" +
            "低 ${priceFormat(candle.low)}，收 ${priceFormat(candle.close)}$volume$direction"
    }

    /** 指标行摘要（Missing → locale.missing）。 */
    fun indicatorSummary(
        definitionName: String,
        plots: List<String>,
        values: List<Double>,
        locale: ChartLocale = ChartLocale.ZH_CN,
        priceFormat: (Double) -> String = { defaultPrice(it) },
    ): String {
        val parts = plots.mapIndexed { i, plot ->
            val v = values.getOrNull(i)
            "$plot ${if (v != null && v.isFinite()) priceFormat(v) else locale.missing}"
        }
        return "$definitionName：${parts.joinToString("，")}"
    }

    /** 图表整体摘要（选中位置上下文）。 */
    fun chartSummary(
        selectedText: String?,
        latestText: String?,
        candleCount: Int,
        locale: ChartLocale = ChartLocale.ZH_CN,
    ): String = buildString {
        append(locale.chartSummaryTemplate.format(candleCount))
        if (selectedText != null) append(locale.selectedPrefix).append(selectedText)
        else if (latestText != null) append(locale.latestPrefix).append(latestText)
        append(locale.hintActions)
    }

    private fun defaultPrice(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.2f", v)

    private fun defaultVolume(v: Double?): String = when {
        v == null -> "—"
        v >= 1e8 -> String.format("%.2f亿", v / 1e8)
        v >= 1e4 -> String.format("%.2f万", v / 1e4)
        else -> String.format("%.0f", v)
    }

    /** 从指标状态提取行值（行内 NaN → NaN 传递）。 */
    fun indicatorValues(state: IndicatorResultState?, index: Int): List<Double>? {
        state ?: return null
        if (index < 0 || index >= state.result.size()) return null
        return state.result.keys.map { k ->
            state.result[k][index].takeIf { it.isFinite() } ?: Double.NaN
        }
    }
}
