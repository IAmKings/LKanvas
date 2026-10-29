package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.IndicatorSeries
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType

/** BBI 多空指标（上游 `bullAndBearIndex.ts` 镜像，I-07）：多条 MA 的均值。 */
class BullAndBearIndexIndicator : IndicatorDefinition {
    override val name = "BBI"
    override val shortName = "BBI"
    override val calcParams = listOf(3.0, 6.0, 12.0, 24.0)
    override val precision = 2
    override val series = IndicatorSeries.PRICE
    override val incremental = IncrementalCapability.REPLACE_APPEND
    override val shouldOhlc = true

    override val plots = listOf(PlotDef("bbi", "BBI: ", PlotType.LINE))

    /** 上游 calc 镜像：closeSums 先输出（mas 缓存）后减滑出根；maxPeriod 就绪后求均值。 */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        val maxPeriod = params.max().toInt()
        val closeSums = DoubleArray(params.size)
        val mas = DoubleArray(params.size)
        return candles.mapIndexed { i, kLineData ->
            val bbi = HashMap<String, Double>(1)
            val close = kLineData.close
            params.forEachIndexed { index, p ->
                val period = p.toInt()
                closeSums[index] += close
                if (i >= period - 1) {
                    mas[index] = closeSums[index] / period
                    closeSums[index] -= candles[i - (period - 1)].close
                }
            }
            if (i >= maxPeriod - 1) {
                var maSum = 0.0
                for (ma in mas) maSum += ma
                bbi["bbi"] = if (params.isNotEmpty()) maSum / params.size else 0.0
            }
            bbi
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
        val maxPeriod = params.max().toInt()
        if (i < maxPeriod - 1) return emptyMap()
        return mapOf("bbi" to params.indices.sumOf { s.closeSums[it] / params[it] } / params.size)
    }
}
