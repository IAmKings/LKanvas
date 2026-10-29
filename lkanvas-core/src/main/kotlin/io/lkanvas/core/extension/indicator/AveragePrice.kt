package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.IndicatorSeries
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType

/** AVP（上游 `averagePrice.ts` 镜像，I-26）：累计 turnover/volume；零累计量无定义（Missing）。 */
class AveragePriceIndicator : IndicatorDefinition {
    override val name = "AVP"
    override val shortName = "AVP"
    override val calcParams = emptyList<Double>()
    override val series = IndicatorSeries.PRICE
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots = listOf(PlotDef("avp", "AVP: ", PlotType.LINE))

    /** 上游 calc 镜像：turnover/volume 累计（缺失按 0），totalVolume==0 → 缺键。 */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        var totalTurnover = 0.0
        var totalVolume = 0.0
        return candles.map { kLineData ->
            val avp = HashMap<String, Double>(1)
            totalTurnover += kLineData.turnover ?: 0.0
            totalVolume += kLineData.volume ?: 0.0
            if (totalVolume != 0.0) {
                avp["avp"] = totalTurnover / totalVolume
            }
            avp
        }
    }

    internal class Scratch(val totalTurnover: Double, val totalVolume: Double)

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0, 0.0)

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val c = candles[i]
        return Scratch(
            s.totalTurnover + (c.turnover ?: 0.0),
            s.totalVolume + (c.volume ?: 0.0),
        )
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        // totalVolume==0（含缺失记 0 的情形）无定义 → Missing（与上游 undefined 一致）
        if (s.totalVolume == 0.0) return emptyMap()
        return mapOf("avp" to s.totalTurnover / s.totalVolume)
    }
}
