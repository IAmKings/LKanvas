package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType

/** OBV（上游 `onBalanceVolume.ts` 镜像，I-24）：带符号量累计 + 均值（volume 缺失按 0，无掩码）。 */
class OnBalanceVolumeIndicator : IndicatorDefinition {
    override val name = "OBV"
    override val shortName = "OBV"
    override val calcParams = listOf(30.0)
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots = listOf(
        PlotDef("obv", "OBV: ", PlotType.LINE),
        PlotDef("maObv", "MAOBV: ", PlotType.LINE),
    )

    private fun signed(candles: List<Candle>, k: Int): Double {
        val prev = if (k > 0) candles[k - 1] else candles[k]
        val v = candles[k].volume ?: 0.0
        return when {
            candles[k].close > prev.close -> v
            candles[k].close < prev.close -> -v
            else -> 0.0
        }
    }

    /** 上游 calc 镜像：oldObv 累计、obvSum 先输出均值、后减 result[i-(N-1)].obv。 */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        val n = params[0].toInt()
        var obvSum = 0.0
        var oldObv = 0.0
        val obvValues = ArrayList<Double>()
        return candles.mapIndexed { i, kLineData ->
            val prev = if (i > 0) candles[i - 1] else kLineData
            val v = kLineData.volume ?: 0.0
            if (kLineData.close < prev.close) {
                oldObv -= v
            } else if (kLineData.close > prev.close) {
                oldObv += v
            }
            obvValues.add(oldObv)
            val obv = HashMap<String, Double>(2)
            obv["obv"] = oldObv
            obvSum += oldObv
            if (i >= n - 1) {
                obv["maObv"] = obvSum / n
                // 上游：result[i-(N-1)].obv ?? 0（该行 obv 必有效）
                obvSum -= obvValues[i - (n - 1)]
            }
            obv
        }
    }

    internal class Scratch(val obv: Double, val obvSum: Double)

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0, 0.0)

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val n = params[0].toInt()
        val obv = s.obv + signed(candles, i)
        var obvSum = s.obvSum + obv
        if (i >= n) {
            // obv(i-n) = obv(i) - Σ signed(k), k∈[i-n+1, i]
            var scan = 0.0
            for (k in i - n + 1..i) scan += signed(candles, k)
            obvSum -= obv - scan
        }
        return Scratch(obv, obvSum)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val row = HashMap<String, Double>(2)
        row["obv"] = s.obv
        if (i >= params[0] - 1) row["maObv"] = s.obvSum / params[0]
        return row
    }
}
