package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.IndicatorSeries
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType

/**
 * SMA（上游 `simpleMovingAverage.ts` 镜像，I-05）：
 * 递推口径 (C*w + prev*(N-w+1))/(N+1)，种子 SMA(N)。
 */
class SimpleMovingAverageIndicator : IndicatorDefinition {
    override val name = "SMA"
    override val shortName = "SMA"
    override val calcParams = listOf(12.0, 2.0)
    override val precision = 2
    override val series = IndicatorSeries.PRICE
    override val incremental = IncrementalCapability.REPLACE_APPEND
    override val shouldOhlc = true

    override val plots = listOf(PlotDef("sma", "SMA: ", PlotType.LINE))

    /** 上游 calc 镜像：closeSum 循环外累计，smaValue 递推（种子 = 前 N 根 SMA）。 */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        val n = params[0]
        val w = params[1]
        var closeSum = 0.0
        var smaValue = 0.0
        return candles.mapIndexed { i, kLineData ->
            val sma = HashMap<String, Double>(1)
            val close = kLineData.close
            closeSum += close
            if (i >= n - 1) {
                smaValue = if (i > n - 1) {
                    (close * w + smaValue * (n - w + 1)) / (n + 1)
                } else {
                    closeSum / n
                }
                sma["sma"] = smaValue
            }
            sma
        }
    }

    internal class Scratch(val closeSum: Double, val smaValue: Double)

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0, 0.0)

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val close = candles[i].close
        val closeSum = s.closeSum + close
        val n = params[0]
        val w = params[1]
        val smaValue = when {
            i.toDouble() > n - 1 -> (close * w + s.smaValue * (n - w + 1)) / (n + 1)
            i.toDouble() == n - 1 -> closeSum / n
            else -> s.smaValue
        }
        return Scratch(closeSum, smaValue)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        return if (i >= params[0] - 1) mapOf("sma" to s.smaValue) else emptyMap()
    }
}
