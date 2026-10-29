package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType

/** PSY 心理线（上游 `psychologicalLine.ts` 镜像，I-12）：N 日上涨天数占比*100；MAPSY 为 M 日均值。 */
class PsychologicalLineIndicator : IndicatorDefinition {
    override val name = "PSY"
    override val shortName = "PSY"
    override val calcParams = listOf(12.0, 6.0)
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots = listOf(
        PlotDef("psy", "PSY: ", PlotType.LINE),
        PlotDef("maPsy", "MAPSY: ", PlotType.LINE),
    )

    private fun upFlag(candles: List<Candle>, i: Int): Int {
        if (i <= 0) return 0
        return if (candles[i].close > candles[i - 1].close) 1 else 0
    }

    /** 上游 calc 镜像：psySum 先输出均值、后减 result[i-(M-1)].psy（缺省按 0）、末了扣减窗口滑出的上涨标记。 */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        val p = params[0].toInt()
        val q = params[1].toInt()
        var upCount = 0
        var psySum = 0.0
        return candles.mapIndexed { i, kLineData ->
            val psy = HashMap<String, Double>(2)
            upCount += upFlag(candles, i)
            if (i >= p - 1) {
                val psyValue = upCount.toDouble() / p * 100
                psy["psy"] = psyValue
                psySum += psyValue
                if (i >= p + q - 2) {
                    psy["maPsy"] = psySum / q
                    // 上游：result[i-(q-1)].psy ?? 0（该行 psy 必有效）
                    psySum -= if (i - (q - 1) >= p - 1) {
                        upCountWindow(candles, i - (q - 1), p)
                    } else {
                        0.0
                    }
                }
                // 上游：upCount -= upList[i-(p-1)]
                upCount -= upFlag(candles, i - (p - 1))
            }
            psy
        }
    }

    /** 窗口 [j-p+1, j] 内上涨天数占比*100（重建第 j 行 psy 值）。 */
    private fun upCountWindow(candles: List<Candle>, j: Int, p: Int): Double {
        var c = 0
        for (k in j - (p - 1)..j) if (upFlag(candles, k) == 1) c++
        return c.toDouble() / p * 100
    }

    internal class Scratch(val upCount: Int, val psySum: Double)

    override fun initScratch(params: List<Double>): Any? = Scratch(0, 0.0)

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val p = params[0].toInt()
        val q = params[1].toInt()
        // 状态约定：upCount = 窗口 [i-p+1, i] 的上涨天数（含当前根）
        var upCount = s.upCount + upFlag(candles, i)
        if (i >= p) upCount -= upFlag(candles, i - p)
        var psySum = s.psySum
        if (i >= p - 1) {
            psySum += upCount.toDouble() / p * 100
            // 维持「最近 q 个 psy 之和」：滑出的是 psy(i-q)
            if (i - q >= p - 1) {
                psySum -= upCountWindow(candles, i - q, p)
            }
        }
        return Scratch(upCount, psySum)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val p = params[0].toInt()
        val q = params[1].toInt()
        if (i < p - 1) return emptyMap()
        val row = HashMap<String, Double>(2)
        row["psy"] = s.upCount.toDouble() / p * 100
        if (i >= p + q - 2) row["maPsy"] = s.psySum / q
        return row
    }
}
