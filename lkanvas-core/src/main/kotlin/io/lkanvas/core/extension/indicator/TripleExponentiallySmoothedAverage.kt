package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType

/**
 * TRIX（上游 `tripleExponentiallySmoothedAverage.ts` 镜像，I-23）：三级 EMA + MA。
 * 上游 subtract 的是 result[i-(M-1)].trix（输出后减）；Scratch 用环形历史减
 * trix(i-M)——时序不同、每行输出等价。
 */
class TrixIndicator : IndicatorDefinition {
    override val name = "TRIX"
    override val shortName = "TRIX"
    override val calcParams = listOf(12.0, 9.0)
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots = listOf(
        PlotDef("trix", "TRIX: ", PlotType.LINE),
        PlotDef("maTrix", "MATRIX: ", PlotType.LINE),
    )

    /** 上游 calc 镜像：ema1/ema2/oldTr 递推 + trixValues 历史（先输出均值后减滑出 trix）。 */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        val n = params[0]
        val m = params[1].toInt()
        var closeSum = 0.0
        var ema1 = 0.0
        var ema2 = 0.0
        var oldTr = 0.0
        var ema1Sum = 0.0
        var ema2Sum = 0.0
        var trixSum = 0.0
        val trixValues = ArrayList<Double?>()
        return candles.mapIndexed { i, kLineData ->
            val trix = HashMap<String, Double>(2)
            val close = kLineData.close
            closeSum += close
            if (i >= n - 1) {
                ema1 = if (i > n - 1) (2 * close + (n - 1) * ema1) / (n + 1) else closeSum / n
                ema1Sum += ema1
                if (i >= n * 2 - 2) {
                    ema2 = if (i > n * 2 - 2) (2 * ema1 + (n - 1) * ema2) / (n + 1) else ema1Sum / n
                    ema2Sum += ema2
                    if (i >= n * 3 - 3) {
                        val tr: Double
                        var trixValue = 0.0
                        if (i > n * 3 - 3) {
                            tr = (2 * ema2 + (n - 1) * oldTr) / (n + 1)
                            trixValue = ((tr - oldTr) / oldTr) * 100
                        } else {
                            tr = ema2Sum / n
                        }
                        oldTr = tr
                        trix["trix"] = trixValue
                        trixValues.add(trixValue)
                        trixSum += trixValue
                        if (i >= n * 3 + m - 4) {
                            trix["maTrix"] = trixSum / m
                            // 上游：result[i-(M-1)].trix ?? 0（该行 trix 必有效）
                            trixSum -= trixValues[i - (m - 1)] ?: 0.0
                        }
                    } else {
                        trixValues.add(null)
                    }
                } else {
                    trixValues.add(null)
                }
            } else {
                trixValues.add(null)
            }
            trix
        }
    }

    internal class Scratch(
        val closeSum: Double,
        val ema1: Double,
        val ema2: Double,
        val oldTr: Double,
        val ema1Sum: Double,
        val ema2Sum: Double,
        val trixSum: Double,
        /** 最近 M 个 trix 值（滚动扣减回看）。 */
        val trixHistory: DoubleArray,
    )

    override fun initScratch(params: List<Double>): Any? =
        Scratch(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, DoubleArray(0))

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val n = params[0]
        val m = params[1].toInt()
        val close = candles[i].close
        val closeSum = s.closeSum + close
        var ema1 = s.ema1
        var ema2 = s.ema2
        var oldTr = s.oldTr
        var ema1Sum = s.ema1Sum
        var ema2Sum = s.ema2Sum
        var trixSum = s.trixSum
        var history = s.trixHistory

        var trixValue: Double? = null
        if (i >= n - 1) {
            ema1 = if (i > n - 1) (2 * close + (n - 1) * ema1) / (n + 1) else closeSum / n
            ema1Sum += ema1
            if (i >= n * 2 - 2) {
                ema2 = if (i > n * 2 - 2) (2 * ema1 + (n - 1) * ema2) / (n + 1) else ema1Sum / n
                ema2Sum += ema2
                if (i >= n * 3 - 3) {
                    val tr: Double
                    if (i > n * 3 - 3) {
                        tr = (2 * ema2 + (n - 1) * oldTr) / (n + 1)
                        trixValue = ((tr - oldTr) / oldTr) * 100
                    } else {
                        tr = ema2Sum / n
                        trixValue = 0.0
                    }
                    oldTr = tr
                    trixSum += trixValue
                    // 维持「最近 m 个 trix 之和」：滑出的是 trix(i-m)（被 shift 丢弃的值）
                    val drop = if (history.size >= m) history[0] else null
                    history = if (history.size >= m) {
                        val next = DoubleArray(m)
                        System.arraycopy(history, 1, next, 0, m - 1)
                        next[m - 1] = trixValue
                        next
                    } else {
                        history + trixValue
                    }
                    if (drop != null) trixSum -= drop
                }
            }
        }
        return Scratch(closeSum, ema1, ema2, oldTr, ema1Sum, ema2Sum, trixSum, history)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val n = params[0]
        if (i < n * 3 - 3) return emptyMap()
        val row = HashMap<String, Double>(2)
        row["trix"] = s.trixHistory.last()
        if (i >= n * 3 + params[1] - 4) row["maTrix"] = s.trixSum / params[1]
        return row
    }
}
