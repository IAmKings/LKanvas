package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType
import kotlin.math.max
import kotlin.math.min

/** KDJ（上游 `stoch.ts` 镜像，I-13）：RSV 窗口高低 + K/D 递推（无前值用 50），J=3K-2D。 */
class StochIndicator : IndicatorDefinition {
    override val name = "KDJ"
    override val shortName = "KDJ"
    override val calcParams = listOf(9.0, 3.0, 3.0)
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots = listOf(
        PlotDef("k", "K: ", PlotType.LINE),
        PlotDef("d", "D: ", PlotType.LINE),
        PlotDef("j", "J: ", PlotType.LINE),
    )

    /** 上游 calc 镜像：kPrev/dPrev 循环外递推（首有效行用 50 种子 = 上游 result[i-1]?.k ?? 50）。 */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        val p = params[0].toInt()
        val k1 = params[1]
        val d1 = params[2]
        var prevK = 50.0
        var prevD = 50.0
        return candles.mapIndexed { i, kLineData ->
            val kdj = HashMap<String, Double>(3)
            if (i >= p - 1) {
                var hn = Double.NEGATIVE_INFINITY
                var ln = Double.POSITIVE_INFINITY
                for (j in i - (p - 1)..i) {
                    hn = max(hn, candles[j].high)
                    ln = min(ln, candles[j].low)
                }
                val sub = hn - ln
                val rsv = ((kLineData.close - ln) / (if (sub == 0.0) 1.0 else sub)) * 100
                val k = ((k1 - 1) * prevK + rsv) / k1
                val d = ((d1 - 1) * prevD + k) / d1
                kdj["k"] = k
                kdj["d"] = d
                kdj["j"] = 3.0 * k - 2.0 * d
                prevK = k
                prevD = d
            }
            kdj
        }
    }

    internal class Scratch(val prevK: Double, val prevD: Double, val hasPrev: Boolean)

    override fun initScratch(params: List<Double>): Any? = Scratch(50.0, 50.0, false)

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val p = params[0].toInt()
        val k1 = params[1]
        val d1 = params[2]
        if (i < p - 1) return Scratch(s.prevK, s.prevD, s.hasPrev)
        var hn = Double.NEGATIVE_INFINITY
        var ln = Double.POSITIVE_INFINITY
        for (j in i - (p - 1)..i) {
            hn = max(hn, candles[j].high)
            ln = min(ln, candles[j].low)
        }
        val sub = hn - ln
        val rsv = ((candles[i].close - ln) / (if (sub == 0.0) 1.0 else sub)) * 100
        val prevK = if (s.hasPrev) s.prevK else 50.0
        val prevD = if (s.hasPrev) s.prevD else 50.0
        val k = ((k1 - 1) * prevK + rsv) / k1
        val d = ((d1 - 1) * prevD + k) / d1
        return Scratch(k, d, true)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        if (!s.hasPrev) return emptyMap()
        val j = 3.0 * s.prevK - 2.0 * s.prevD
        return mapOf("k" to s.prevK, "d" to s.prevD, "j" to j)
    }
}
