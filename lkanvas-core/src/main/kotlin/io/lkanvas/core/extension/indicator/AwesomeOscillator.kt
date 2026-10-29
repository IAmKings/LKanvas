package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType

/** AO 动量振荡（上游 `awesomeOscillator.ts` 镜像，I-09）：MA(HL/2, S) - MA(HL/2, L)。 */
class AwesomeOscillatorIndicator : IndicatorDefinition {
    override val name = "AO"
    override val shortName = "AO"
    override val calcParams = listOf(5.0, 34.0)
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots = listOf(PlotDef("ao", "AO: ", PlotType.BAR, baseValue = 0.0))

    /** 上游 calc 镜像：双窗口和、窗口满先取均值并缓存、再减滑出中价。 */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        val maxPeriod = maxOf(params[0], params[1]).toInt()
        var shortSum = 0.0
        var longSum = 0.0
        var short = 0.0
        var long = 0.0
        return candles.mapIndexed { i, kLineData ->
            val ao = HashMap<String, Double>(1)
            val middle = (kLineData.low + kLineData.high) / 2
            shortSum += middle
            longSum += middle
            if (i >= params[0] - 1) {
                short = shortSum / params[0]
                val ago = candles[i - (params[0].toInt() - 1)]
                shortSum -= (ago.low + ago.high) / 2
            }
            if (i >= params[1] - 1) {
                long = longSum / params[1]
                val ago = candles[i - (params[1].toInt() - 1)]
                longSum -= (ago.low + ago.high) / 2
            }
            if (i >= maxPeriod - 1) {
                ao["ao"] = short - long
            }
            ao
        }
    }

    internal class Scratch(val shortSum: Double, val longSum: Double)

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0, 0.0)

    private fun middle(candles: List<Candle>, i: Int): Double =
        (candles[i].low + candles[i].high) / 2

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        var shortSum = s.shortSum + middle(candles, i)
        var longSum = s.longSum + middle(candles, i)
        val ps = params[0].toInt()
        val pl = params[1].toInt()
        if (i >= ps) shortSum -= middle(candles, i - ps)
        if (i >= pl) longSum -= middle(candles, i - pl)
        return Scratch(shortSum, longSum)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val maxPeriod = maxOf(params[0], params[1]).toInt()
        if (i < maxPeriod - 1) return emptyMap()
        return mapOf("ao" to (s.shortSum / params[0] - s.longSum / params[1]))
    }
}
