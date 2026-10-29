package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType

/**
 * MTM 动量（上游 `momentum.ts` 镜像，I-10）：MTM = C - C[i-N]；MAMTM 为 M 日均值。
 * 上游 subtract 的是 result[i-(M-1)].mtm（输出后减）；Scratch 在 step 内减 mtm(i-M)
 * ——时序不同、每行输出等价（窗口均为最近 M 个 mtm）。
 */
class MomentumIndicator : IndicatorDefinition {
    override val name = "MTM"
    override val shortName = "MTM"
    override val calcParams = listOf(12.0, 6.0)
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots = listOf(
        PlotDef("mtm", "MTM: ", PlotType.LINE),
        PlotDef("maMtm", "MAMTM: ", PlotType.LINE),
    )

    /** 第 j 根的 mtm（j >= n 有效，否则 null）。 */
    private fun mtmAt(candles: List<Candle>, j: Int, n: Int): Double? =
        if (j >= n) candles[j].close - candles[j - n].close else null

    /** 上游 calc 镜像：mtmSum 先输出均值、后减 result[i-(M-1)].mtm（缺省按 0）。 */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        val n = params[0].toInt()
        val m = params[1].toInt()
        var mtmSum = 0.0
        return candles.mapIndexed { i, kLineData ->
            val mtm = HashMap<String, Double>(2)
            if (i >= n) {
                val value = kLineData.close - candles[i - n].close
                mtm["mtm"] = value
                mtmSum += value
                if (i >= n + m - 1) {
                    mtm["maMtm"] = mtmSum / m
                    // 上游：result[i-(m-1)].mtm ?? 0（该行 mtm 必有效：i-m+1 >= n）
                    mtmSum -= mtmAt(candles, i - (m - 1), n) ?: 0.0
                }
            }
            mtm
        }
    }

    internal class Scratch(val mtmSum: Double)

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0)

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val n = params[0].toInt()
        val m = params[1].toInt()
        var mtmSum = s.mtmSum
        mtmAt(candles, i, n)?.let { mtmSum += it }
        // 维持「最近 m 个 mtm 之和」：滑出窗口的是 mtm(i-m)
        if (i - m >= n) mtmAt(candles, i - m, n)?.let { mtmSum -= it }
        return Scratch(mtmSum)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val n = params[0].toInt()
        val m = params[1].toInt()
        val mtm = mtmAt(candles, i, n) ?: return emptyMap()
        val row = HashMap<String, Double>(2)
        row["mtm"] = mtm
        if (i >= n + m - 1) row["maMtm"] = s.mtmSum / m
        return row
    }
}
