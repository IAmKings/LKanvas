package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType
import kotlin.math.abs

/** CCI（上游 `commodityChannelIndex.ts` 镜像，I-16）：(TP-MA(TP,N))/MD/0.015；MD=0 取 0。 */
class CommodityChannelIndexIndicator : IndicatorDefinition {
    override val name = "CCI"
    override val shortName = "CCI"
    override val calcParams = listOf(20.0)
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots = listOf(PlotDef("cci", "CCI: ", PlotType.LINE))

    private fun tp(candles: List<Candle>, i: Int): Double =
        (candles[i].high + candles[i].low + candles[i].close) / 3

    /** 上游 calc 镜像：tpSum 窗口满先输出 cci、再减滑出根的 TP。 */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        val p = params[0].toInt() - 1
        var tpSum = 0.0
        return candles.mapIndexed { i, kLineData ->
            val cci = HashMap<String, Double>(1)
            val currentTp = (kLineData.high + kLineData.low + kLineData.close) / 3
            tpSum += currentTp
            if (i >= p) {
                val maTp = tpSum / params[0]
                var sum = 0.0
                for (j in i - p..i) sum += abs(tp(candles, j) - maTp)
                val md = sum / params[0]
                cci["cci"] = if (md != 0.0) (currentTp - maTp) / md / 0.015 else 0.0
                tpSum -= tp(candles, i - p)
            }
            cci
        }
    }

    internal class Scratch(val tpSum: Double)

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0)

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        var tpSum = s.tpSum + tp(candles, i)
        val p = params[0].toInt()
        if (i >= p) tpSum -= tp(candles, i - p)
        return Scratch(tpSum)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val p = params[0].toInt()
        if (i < p - 1) return emptyMap()
        val maTp = s.tpSum / p
        var sum = 0.0
        for (j in i - (p - 1)..i) sum += abs(tp(candles, j) - maTp)
        val md = sum / p
        val currentTp = tp(candles, i)
        return mapOf("cci" to if (md != 0.0) (currentTp - maTp) / md / 0.015 else 0.0)
    }
}
