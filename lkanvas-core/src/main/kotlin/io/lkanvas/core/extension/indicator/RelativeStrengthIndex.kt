package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType
import kotlin.math.max

/** RSI（上游 `relativeStrengthIndex.ts` 镜像，I-14）：RMA 均涨/均跌递推（种子=前 p 个变化均值，i>=p 有效）。 */
class RelativeStrengthIndexIndicator : IndicatorDefinition {
    override val name = "RSI"
    override val shortName = "RSI"
    override val calcParams = listOf(6.0, 12.0, 24.0)
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots: List<PlotDef> = plotsFor(calcParams)

    override fun plotsFor(params: List<Double>): List<PlotDef> =
        params.mapIndexed { i, _ -> PlotDef("rsi${i + 1}", "RSI${i + 1}: ", PlotType.LINE) }

    /** 上游 calc 镜像：gain/lossSums 循环外累计，avgGains/avgLosses 种子+RMA 递推（上游 undefined 语义=未播种缺键）。 */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        val keys = plotsFor(params).map { it.key }
        val gainSums = DoubleArray(params.size)
        val lossSums = DoubleArray(params.size)
        val avgGains = arrayOfNulls<Double>(params.size)
        val avgLosses = arrayOfNulls<Double>(params.size)
        return candles.mapIndexed { i, kLineData ->
            val rsi = HashMap<String, Double>(params.size)
            val change = if (i == 0) 0.0 else kLineData.close - candles[i - 1].close
            val gain = max(change, 0.0)
            val loss = max(-change, 0.0)
            params.forEachIndexed { index, p ->
                gainSums[index] += gain
                lossSums[index] += loss
                if (i < p) return@forEachIndexed
                avgGains[index] = if (avgGains[index] == null) {
                    gainSums[index] / p
                } else {
                    (avgGains[index]!! * (p - 1) + gain) / p
                }
                avgLosses[index] = if (avgLosses[index] == null) {
                    lossSums[index] / p
                } else {
                    (avgLosses[index]!! * (p - 1) + loss) / p
                }
                rsi[keys[index]] = when {
                    avgLosses[index] == 0.0 -> 100.0
                    avgGains[index] == 0.0 -> 0.0
                    else -> 100 - 100 / (1 + avgGains[index]!! / avgLosses[index]!!)
                }
            }
            rsi
        }
    }

    internal class Scratch(
        val gainSums: DoubleArray,
        val lossSums: DoubleArray,
        val avgGains: DoubleArray,
        val avgLosses: DoubleArray,
        val seeded: BooleanArray,
    )

    override fun initScratch(params: List<Double>): Any? = Scratch(
        DoubleArray(params.size), DoubleArray(params.size),
        DoubleArray(params.size), DoubleArray(params.size),
        BooleanArray(params.size),
    )

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val gainSums = s.gainSums.copyOf()
        val lossSums = s.lossSums.copyOf()
        val avgGains = s.avgGains.copyOf()
        val avgLosses = s.avgLosses.copyOf()
        val seeded = s.seeded.copyOf()
        val change = if (i == 0) 0.0 else candles[i].close - candles[i - 1].close
        val gain = max(change, 0.0)
        val loss = max(-change, 0.0)
        for (k in params.indices) {
            val p = params[k]
            gainSums[k] += gain
            lossSums[k] += loss
            if (i < p) continue
            if (!seeded[k]) {
                avgGains[k] = gainSums[k] / p
                avgLosses[k] = lossSums[k] / p
                seeded[k] = true
            } else {
                avgGains[k] = (avgGains[k] * (p - 1) + gain) / p
                avgLosses[k] = (avgLosses[k] * (p - 1) + loss) / p
            }
        }
        return Scratch(gainSums, lossSums, avgGains, avgLosses, seeded)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val row = HashMap<String, Double>(params.size)
        for (k in params.indices) {
            if (!s.seeded[k]) continue
            val rsi = when {
                s.avgLosses[k] == 0.0 -> 100.0
                s.avgGains[k] == 0.0 -> 0.0
                else -> 100 - 100 / (1 + s.avgGains[k] / s.avgLosses[k])
            }
            row["rsi${k + 1}"] = rsi
        }
        return row
    }
}
