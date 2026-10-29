package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.IndicatorSeries
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType

/**
 * MA 移动平均（上游 `src/extension/indicator/movingAverage.ts` 镜像，I-01）。
 *
 * [calc] 逐行对照上游模板（SLA-002）：`closeSums` 为循环外滚动和数组，
 * 窗口满后**先输出** `sum/p` **再减**滑出根（上游 `closeSums[index] -= dataList[i-(p-1)].close`
 * 位于输出之后）；Scratch 路径（stepScratch 内先加后减）为增量优化路径，
 * 两路径数值一致性由 DualPathParityTest 保证。
 */
class MovingAverageIndicator : IndicatorDefinition {
    override val name = "MA"
    override val shortName = "MA"
    override val calcParams = listOf(5.0, 10.0, 30.0, 60.0)
    override val precision = 2
    override val series = IndicatorSeries.PRICE
    override val incremental = IncrementalCapability.REPLACE_APPEND
    override val shouldOhlc = true

    override val plots: List<PlotDef> = plotsFor(calcParams)

    /** 上游 regenerateFigures：输出列随参数个数重建（SLA-008）。 */
    override fun plotsFor(params: List<Double>): List<PlotDef> =
        params.mapIndexed { i, p -> PlotDef("ma${i + 1}", "MA${p.toInt()}: ", PlotType.LINE) }

    /** 上游 calc 镜像：closeSums 外提、窗口满先输出后减滑出值。 */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        val keys = plotsFor(params).map { it.key }
        val closeSums = DoubleArray(params.size)
        return candles.mapIndexed { i, kLineData ->
            val ma = HashMap<String, Double>(params.size)
            val close = kLineData.close
            params.forEachIndexed { index, p ->
                closeSums[index] = closeSums[index] + close
                val period = p.toInt()
                if (i >= period - 1) {
                    ma[keys[index]] = closeSums[index] / period
                    // 上游：输出之后才减去滑出根（时序与 Scratch 路径不同、数值等价）
                    closeSums[index] -= candles[i - (period - 1)].close
                }
            }
            ma
        }
    }

    /** sums[k]：以当前根结尾、长度 period[k] 的窗口 close 和。 */
    internal class Scratch(val sums: DoubleArray, val periods: IntArray)

    override fun initScratch(params: List<Double>): Any? =
        Scratch(DoubleArray(params.size), params.map { it.toInt() }.toIntArray())

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val sums = s.sums.copyOf()
        val close = candles[i].close
        for (k in s.periods.indices) {
            val p = s.periods[k]
            sums[k] += close
            if (i >= p) sums[k] -= candles[i - p].close
        }
        return Scratch(sums, s.periods)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val row = HashMap<String, Double>(s.periods.size)
        for (k in s.periods.indices) {
            if (i >= s.periods[k] - 1) row["ma${k + 1}"] = s.sums[k] / s.periods[k]
        }
        return row
    }
}
