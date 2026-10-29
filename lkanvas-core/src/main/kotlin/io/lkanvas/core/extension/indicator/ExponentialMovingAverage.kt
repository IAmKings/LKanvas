package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.IndicatorSeries
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType

/** EMA 指数移动平均（上游 `exponentialMovingAverage.ts` 镜像，I-04）。 */
class ExponentialMovingAverageIndicator : IndicatorDefinition {
    override val name = "EMA"
    override val shortName = "EMA"
    override val calcParams = listOf(6.0, 12.0, 20.0)
    override val precision = 2
    override val series = IndicatorSeries.PRICE
    override val incremental = IncrementalCapability.REPLACE_APPEND
    override val shouldOhlc = true

    override val plots: List<PlotDef> = plotsFor(calcParams)

    override fun plotsFor(params: List<Double>): List<PlotDef> =
        params.mapIndexed { i, p -> PlotDef("ema${i + 1}", "EMA${p.toInt()}: ", PlotType.LINE) }

    /** 上游 calc 镜像：closeSum 循环外累计，emaValues 数组逐参数递推（种子 = 前 p 根 SMA）。 */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        val keys = plotsFor(params).map { it.key }
        val emaValues = DoubleArray(params.size)
        var closeSum = 0.0
        return candles.mapIndexed { i, kLineData ->
            val ema = HashMap<String, Double>(params.size)
            val close = kLineData.close
            closeSum += close
            params.forEachIndexed { index, p ->
                if (i >= p - 1) {
                    emaValues[index] = if (i > p - 1) {
                        (2 * close + (p - 1) * emaValues[index]) / (p + 1)
                    } else {
                        closeSum / p
                    }
                    ema[keys[index]] = emaValues[index]
                }
            }
            ema
        }
    }

    internal class Scratch(val closeSum: Double, val emaValues: DoubleArray)

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0, DoubleArray(params.size))

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val closeSum = s.closeSum + candles[i].close
        val emaValues = s.emaValues.copyOf()
        val close = candles[i].close
        for (k in params.indices) {
            val p = params[k]
            if (i >= p - 1) {
                emaValues[k] = if (i > p - 1) {
                    (2 * close + (p - 1) * emaValues[k]) / (p + 1)
                } else {
                    closeSum / p
                }
            }
        }
        return Scratch(closeSum, emaValues)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val row = HashMap<String, Double>(params.size)
        for (k in params.indices) {
            if (i >= params[k] - 1) row["ema${k + 1}"] = s.emaValues[k]
        }
        return row
    }
}
