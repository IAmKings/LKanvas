package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType

/**
 * BIAS 乖离率（上游 `bias.ts` 镜像，I-08）：(close-MA)/MA*100。
 * MA 均值为零 → 缺键（Missing，禁止 Infinity，R-08；上游输出 Infinity）。
 */
class BiasIndicator : IndicatorDefinition {
    override val name = "BIAS"
    override val shortName = "BIAS"
    override val calcParams = listOf(6.0, 12.0, 24.0)
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots: List<PlotDef> = plotsFor(calcParams)

    override fun plotsFor(params: List<Double>): List<PlotDef> =
        params.mapIndexed { i, p -> PlotDef("bias${i + 1}", "BIAS${p.toInt()}: ", PlotType.LINE) }

    /** 上游 calc 镜像（先输出后减滑出根）；mean==0 → 缺键（D-03/禁止 Infinity）。 */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        val keys = plotsFor(params).map { it.key }
        val closeSums = DoubleArray(params.size)
        return candles.mapIndexed { i, kLineData ->
            val bias = HashMap<String, Double>(params.size)
            val close = kLineData.close
            params.forEachIndexed { index, p ->
                val period = p.toInt()
                closeSums[index] += close
                if (i >= period - 1) {
                    val mean = closeSums[index] / period
                    if (mean != 0.0) bias[keys[index]] = ((close - mean) / mean) * 100
                    closeSums[index] -= candles[i - (period - 1)].close
                }
            }
            bias
        }
    }

    internal class Scratch(val closeSums: DoubleArray)

    override fun initScratch(params: List<Double>): Any? = Scratch(DoubleArray(params.size))

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val closeSums = s.closeSums.copyOf()
        val close = candles[i].close
        for (k in params.indices) {
            val p = params[k].toInt()
            closeSums[k] += close
            if (i >= p) closeSums[k] -= candles[i - p].close
        }
        return Scratch(closeSums)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val row = HashMap<String, Double>(params.size)
        val close = candles[i].close
        for (k in params.indices) {
            val p = params[k].toInt()
            if (i >= p - 1) {
                val mean = s.closeSums[k] / p
                if (mean != 0.0) row["bias${k + 1}"] = ((close - mean) / mean) * 100
            }
        }
        return row
    }
}
