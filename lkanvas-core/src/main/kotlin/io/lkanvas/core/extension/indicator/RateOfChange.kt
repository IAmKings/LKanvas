package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType

/**
 * ROC 变动率（上游 `rateOfChange.ts` 镜像，I-11）：(C-REF)/REF*100。
 * REF=0 按上游显式约定取 0（有效值）。
 */
class RateOfChangeIndicator : IndicatorDefinition {
    override val name = "ROC"
    override val shortName = "ROC"
    override val calcParams = listOf(12.0, 6.0)
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots = listOf(
        PlotDef("roc", "ROC: ", PlotType.LINE),
        PlotDef("maRoc", "MAROC: ", PlotType.LINE),
    )

    private fun rocAt(candles: List<Candle>, j: Int, n: Int): Double? {
        if (j < n) return null
        val ago = candles[j - n].close
        return if (ago != 0.0) ((candles[j].close - ago) / ago) * 100 else 0.0
    }

    /** 上游 calc 镜像：rocSum 先输出均值、后减 result[i-(M-1)].roc（缺省按 0）。 */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        val n = params[0].toInt()
        val m = params[1].toInt()
        var rocSum = 0.0
        return candles.mapIndexed { i, kLineData ->
            val roc = HashMap<String, Double>(2)
            if (i >= n) {
                val ago = candles[i - n].close
                val value = if (ago != 0.0) ((kLineData.close - ago) / ago) * 100 else 0.0
                roc["roc"] = value
                rocSum += value
                if (i >= n + m - 1) {
                    roc["maRoc"] = rocSum / m
                    rocSum -= rocAt(candles, i - (m - 1), n) ?: 0.0
                }
            }
            roc
        }
    }

    internal class Scratch(val rocSum: Double)

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0)

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val n = params[0].toInt()
        val m = params[1].toInt()
        var rocSum = s.rocSum
        rocAt(candles, i, n)?.let { rocSum += it }
        if (i - m >= n) rocAt(candles, i - m, n)?.let { rocSum -= it }
        return Scratch(rocSum)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val n = params[0].toInt()
        val m = params[1].toInt()
        val roc = rocAt(candles, i, n) ?: return emptyMap()
        val row = HashMap<String, Double>(2)
        row["roc"] = roc
        if (i >= n + m - 1) row["maRoc"] = s.rocSum / m
        return row
    }
}
