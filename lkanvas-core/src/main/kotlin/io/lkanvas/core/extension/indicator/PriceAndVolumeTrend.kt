package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType

/** PVT（上游 `priceAndVolumeTrend.ts` 镜像，I-25）：X 累计；volume 缺失按上游显式取 1。 */
class PriceAndVolumeTrendIndicator : IndicatorDefinition {
    override val name = "PVT"
    override val shortName = "PVT"
    override val calcParams = emptyList<Double>()
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots = listOf(PlotDef("pvt", "PVT: ", PlotType.LINE))

    /** 上游 calc 镜像：sum 累计（volume ?? 1、prevClose 取自身兜底）。 */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        var sum = 0.0
        return candles.mapIndexed { i, kLineData ->
            val close = kLineData.close
            val volume = kLineData.volume ?: 1.0
            val prevClose = if (i > 0) candles[i - 1].close else close
            val x = if (prevClose != 0.0) ((close - prevClose) / prevClose) * volume else 0.0
            sum += x
            mapOf("pvt" to sum)
        }
    }

    internal class Scratch(val sum: Double)

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0)

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val close = candles[i].close
        val volume = candles[i].volume ?: 1.0
        val prevClose = if (i > 0) candles[i - 1].close else close
        val x = if (prevClose != 0.0) ((close - prevClose) / prevClose) * volume else 0.0
        return Scratch(s.sum + x)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> =
        mapOf("pvt" to (scratch as Scratch).sum)
}
