package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.IndicatorSeries
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType
import kotlin.math.max
import kotlin.math.min

/** SAR 抛物线（上游 `stopAndReverse.ts` 镜像，I-22）：趋势状态机 + 加速因子，逐行有值。 */
class StopAndReverseIndicator : IndicatorDefinition {
    override val name = "SAR"
    override val shortName = "SAR"
    override val calcParams = listOf(2.0, 2.0, 20.0)
    override val precision = 2
    override val series = IndicatorSeries.PRICE
    override val incremental = IncrementalCapability.REPLACE_APPEND
    override val shouldOhlc = true

    override val plots = listOf(PlotDef("sar", "SAR: ", PlotType.LINE))

    /** 上游 calc 镜像：af/ep/isIncreasing/sar 循环外递推，趋势翻转条件照抄。 */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        val startAf = params[0] / 100
        val step = params[1] / 100
        val maxAf = params[2] / 100
        var af = startAf
        var ep = -100.0
        var isIncreasing = false
        var sar = 0.0
        return candles.mapIndexed { i, kLineData ->
            val preSar = sar
            val high = kLineData.high
            val low = kLineData.low
            // 上游：dataList[Math.max(1, i) - 1] → i=0 取自身，i>=1 取前一根
            val ref = candles[max(1, i) - 1]
            if (isIncreasing) {
                if (ep == -100.0 || ep < high) {
                    ep = high
                    af = min(af + step, maxAf)
                }
                sar = preSar + af * (ep - preSar)
                val lowMin = min(ref.low, low)
                if (sar > low) {
                    sar = ep
                    af = startAf
                    ep = -100.0
                    isIncreasing = !isIncreasing
                } else if (sar > lowMin) {
                    sar = lowMin
                }
            } else {
                if (ep == -100.0 || ep > low) {
                    ep = low
                    af = min(af + step, maxAf)
                }
                sar = preSar + af * (ep - preSar)
                val highMax = max(ref.high, high)
                if (sar < high) {
                    sar = ep
                    af = startAf
                    ep = -100.0
                    isIncreasing = !isIncreasing
                } else if (sar < highMax) {
                    sar = highMax
                }
            }
            mapOf("sar" to sar)
        }
    }

    internal class Scratch(
        val af: Double,
        val ep: Double,
        val isIncreasing: Boolean,
        val sar: Double,
    )

    override fun initScratch(params: List<Double>): Any? =
        Scratch(params[0] / 100, -100.0, false, 0.0)

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val startAf = params[0] / 100
        val step = params[1] / 100
        val maxAf = params[2] / 100
        val preSar = s.sar
        val high = candles[i].high
        val low = candles[i].low
        val ref = candles[max(1, i) - 1]

        var af = s.af
        var ep = s.ep
        var isIncreasing = s.isIncreasing
        var sar: Double

        if (isIncreasing) {
            if (ep == -100.0 || ep < high) {
                ep = high
                af = min(af + step, maxAf)
            }
            sar = preSar + af * (ep - preSar)
            val lowMin = min(ref.low, low)
            if (sar > low) {
                sar = ep
                af = startAf
                ep = -100.0
                isIncreasing = !isIncreasing
            } else if (sar > lowMin) {
                sar = lowMin
            }
        } else {
            if (ep == -100.0 || ep > low) {
                ep = low
                af = min(af + step, maxAf)
            }
            sar = preSar + af * (ep - preSar)
            val highMax = max(ref.high, high)
            if (sar < high) {
                sar = ep
                af = startAf
                ep = -100.0
                isIncreasing = !isIncreasing
            } else if (sar < highMax) {
                sar = highMax
            }
        }
        return Scratch(af, ep, isIncreasing, sar)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> =
        mapOf("sar" to (scratch as Scratch).sar)
}
