package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType
import kotlin.math.max
import kotlin.math.min

/** WR 威廉（上游 `williamsR.ts` 镜像，I-15）：100*(C-HH(N))/(HH(N)-LL(N))；高低相同取 0。 */
class WilliamsRIndicator : IndicatorDefinition {
    override val name = "WR"
    override val shortName = "WR"
    override val calcParams = listOf(6.0, 10.0, 14.0)
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots: List<PlotDef> = plotsFor(calcParams)

    override fun plotsFor(params: List<Double>): List<PlotDef> =
        params.mapIndexed { i, _ -> PlotDef("wr${i + 1}", "WR${i + 1}: ", PlotType.LINE) }

    /** 上游 calc 镜像：逐参数窗口高低（上游 getMaxMin(slice) 同式）。 */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        val keys = plotsFor(params).map { it.key }
        return candles.mapIndexed { i, kLineData ->
            val wr = HashMap<String, Double>(params.size)
            val close = kLineData.close
            params.forEachIndexed { index, param ->
                val p = param.toInt() - 1
                if (i >= p) {
                    var hn = Double.NEGATIVE_INFINITY
                    var ln = Double.POSITIVE_INFINITY
                    for (j in i - p..i) {
                        hn = max(hn, candles[j].high)
                        ln = min(ln, candles[j].low)
                    }
                    val sub = hn - ln
                    wr[keys[index]] = if (sub == 0.0) 0.0 else ((close - hn) / sub) * 100
                }
            }
            wr
        }
    }

    internal object Scratch

    override fun initScratch(params: List<Double>): Any? = Scratch

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? = Scratch

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val row = HashMap<String, Double>(params.size)
        val close = candles[i].close
        for (k in params.indices) {
            val p = params[k].toInt()
            if (i < p - 1) continue
            var hn = Double.NEGATIVE_INFINITY
            var ln = Double.POSITIVE_INFINITY
            for (j in i - (p - 1)..i) {
                hn = max(hn, candles[j].high)
                ln = min(ln, candles[j].low)
            }
            val sub = hn - ln
            row["wr${k + 1}"] = if (sub == 0.0) 0.0 else ((close - hn) / sub) * 100
        }
        return row
    }
}
