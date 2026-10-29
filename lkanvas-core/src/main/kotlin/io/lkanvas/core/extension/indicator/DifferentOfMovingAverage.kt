package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType

/** DMA（上游 `differentOfMovingAverage.ts` 镜像，I-27）：双 MA 差 + AMA 均值。 */
class DifferentOfMovingAverageIndicator : IndicatorDefinition {
    override val name = "DMA"
    override val shortName = "DMA"
    override val calcParams = listOf(10.0, 50.0, 10.0)
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots = listOf(
        PlotDef("dma", "DMA: ", PlotType.LINE),
        PlotDef("ama", "AMA: ", PlotType.LINE),
    )

    private fun difAt(candles: List<Candle>, j: Int, p1: Int, p2: Int): Double? {
        if (j < p2 - 1) return null
        var s1 = 0.0
        for (k in j - (p1 - 1)..j) s1 += candles[k].close
        var s2 = 0.0
        for (k in j - (p2 - 1)..j) s2 += candles[k].close
        return s1 / p1 - s2 / p2
    }

    /** 上游 calc 镜像：closeSum1/2 窗口满先取 MA 并减滑出根；difSum 先输出 AMA 后减 result[i-(P3-1)].dma。 */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        val p1 = params[0].toInt()
        val p2 = params[1].toInt()
        val p3 = params[2].toInt()
        val maxPeriod = maxOf(p1, p2)
        var closeSum1 = 0.0
        var closeSum2 = 0.0
        var dmaSum = 0.0
        val dmaValues = ArrayList<Double?>()
        return candles.mapIndexed { i, kLineData ->
            val dma = HashMap<String, Double>(2)
            val close = kLineData.close
            closeSum1 += close
            closeSum2 += close
            var ma1 = 0.0
            var ma2 = 0.0
            if (i >= p1 - 1) {
                ma1 = closeSum1 / p1
                closeSum1 -= candles[i - (p1 - 1)].close
            }
            if (i >= p2 - 1) {
                ma2 = closeSum2 / p2
                closeSum2 -= candles[i - (p2 - 1)].close
            }
            if (i >= maxPeriod - 1) {
                val dif = ma1 - ma2
                dma["dma"] = dif
                dmaValues.add(dif)
                dmaSum += dif
                if (i >= maxPeriod + p3 - 2) {
                    dma["ama"] = dmaSum / p3
                    // 上游：result[i-(P3-1)].dma ?? 0（该行 dma 必有效）
                    dmaSum -= dmaValues[i - (p3 - 1)] ?: 0.0
                }
            } else {
                dmaValues.add(null)
            }
            dma
        }
    }

    internal class Scratch(
        val closeSum1: Double,
        val closeSum2: Double,
        val dmaSum: Double,
    )

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0, 0.0, 0.0)

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val p1 = params[0].toInt()
        val p2 = params[1].toInt()
        val p3 = params[2].toInt()
        val maxPeriod = maxOf(p1, p2)
        val close = candles[i].close
        var closeSum1 = s.closeSum1 + close
        var closeSum2 = s.closeSum2 + close
        if (i >= p1) closeSum1 -= candles[i - p1].close
        if (i >= p2) closeSum2 -= candles[i - p2].close

        var dmaSum = s.dmaSum
        difAt(candles, i, p1, p2)?.let { dmaSum += it }
        // 维持最近 p3 个 dif 之和：滑出的是 dif(i-p3)
        if (i - p3 >= maxPeriod - 1) {
            difAt(candles, i - p3, p1, p2)?.let { dmaSum -= it }
        }
        return Scratch(closeSum1, closeSum2, dmaSum)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val p1 = params[0].toInt()
        val p2 = params[1].toInt()
        val maxPeriod = maxOf(p1, p2)
        val dif = difAt(candles, i, p1, p2) ?: return emptyMap()
        val row = HashMap<String, Double>(2)
        row["dma"] = dif
        if (i >= maxPeriod + params[2] - 2) row["ama"] = s.dmaSum / params[2]
        return row
    }
}
