package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.IndicatorSeries
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * BOLL 布林带（上游 `bollingerBands.ts` 镜像，I-06）：
 * MID=MA(N)，MD=窗口偏差平方和（|Σ|/N 开方）。
 */
class BollingerBandsIndicator : IndicatorDefinition {
    override val name = "BOLL"
    override val shortName = "BOLL"
    override val calcParams = listOf(20.0, 2.0)
    override val precision = 2
    override val series = IndicatorSeries.PRICE
    override val incremental = IncrementalCapability.REPLACE_APPEND
    override val shouldOhlc = true

    override val plots = listOf(
        PlotDef("up", "UP: ", PlotType.LINE),
        PlotDef("mid", "MID: ", PlotType.LINE),
        PlotDef("dn", "DN: ", PlotType.LINE),
    )

    /** 上游 calc 镜像：窗口满先输出三带，再减滑出根（closeSum 输出后扣减）。 */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        val p = params[0].toInt() - 1
        val k = params[1]
        var closeSum = 0.0
        return candles.mapIndexed { i, kLineData ->
            val boll = HashMap<String, Double>(3)
            val close = kLineData.close
            closeSum += close
            if (i >= p) {
                val mid = closeSum / params[0]
                // MD：窗口 [i-p, i] 偏差平方和的 |Σ|/N 开方（上游 getBollMd 同式）
                var sum = 0.0
                for (j in i - p..i) {
                    val d = candles[j].close - mid
                    sum += d * d
                }
                val md = sqrt(abs(sum) / params[0])
                boll["up"] = mid + k * md
                boll["mid"] = mid
                boll["dn"] = mid - k * md
                closeSum -= candles[i - p].close
            }
            boll
        }
    }

    internal class Scratch(val closeSum: Double)

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0)

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        var closeSum = s.closeSum + candles[i].close
        val p = params[0].toInt()
        if (i >= p) closeSum -= candles[i - p].close
        return Scratch(closeSum)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val p = params[0].toInt()
        if (i < p - 1) return emptyMap()
        val mid = s.closeSum / p
        var sum = 0.0
        for (j in i - (p - 1)..i) {
            val d = candles[j].close - mid
            sum += d * d
        }
        val md = sqrt(abs(sum) / p)
        val k = params[1]
        return mapOf(
            "up" to mid + k * md,
            "mid" to mid,
            "dn" to mid - k * md,
        )
    }
}
