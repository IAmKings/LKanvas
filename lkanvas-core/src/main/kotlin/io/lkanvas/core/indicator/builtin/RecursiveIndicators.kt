package io.lkanvas.core.indicator.builtin

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.IndicatorSeries
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType
import kotlin.math.max
import kotlin.math.min

/**
 * T-14 递推/累计指标组（I-22~I-27）。公式与上游 v10.1.0 逐位对齐。
 * 全部支持 ReplaceLatest/Append 增量：历史回看通过 candle 派生（OBV/DMA）
 * 或小型环形历史（TRIX）实现，不随数据量增长。
 */

/** SAR 抛物线（I-22，stopAndReverse.ts）：趋势状态机 + 加速因子，逐行有值。 */
class StopAndReverseIndicator : IndicatorDefinition {
    override val name = "SAR"
    override val shortName = "SAR"
    override val calcParams = listOf(2.0, 2.0, 20.0)
    override val precision = 2
    override val series = IndicatorSeries.PRICE
    override val incremental = IncrementalCapability.REPLACE_APPEND
    override val shouldOhlc = true

    override val plots = listOf(PlotDef("sar", "SAR: ", PlotType.LINE))

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
        // 上游：dataList[Math.max(1, i) - 1] → i=0 取自身，i>=1 取前一根
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

/** TRIX（I-23，tripleExponentiallySmoothedAverage.ts）：三级 EMA + 环形历史 MA。 */
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

/** OBV（I-24，onBalanceVolume.ts）：带符号量累计 + 派生回看的窗口均值。 */
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

    internal class Scratch(val obv: Double, val obvSum: Double)

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0, 0.0)

    /** 第 k 根的带符号量（涨+/跌-/平0；k=0 与自身比较恒为 0）。 */
    private fun signed(candles: List<Candle>, k: Int): Double {
        val prev = if (k > 0) candles[k - 1] else candles[k]
        val v = candles[k].volume ?: 0.0
        return when {
            candles[k].close > prev.close -> v
            candles[k].close < prev.close -> -v
            else -> 0.0
        }
    }

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

/** PVT（I-25，priceAndVolumeTrend.ts）：X 累计；volume 缺失按上游显式取 1。 */
class PriceAndVolumeTrendIndicator : IndicatorDefinition {
    override val name = "PVT"
    override val shortName = "PVT"
    override val calcParams = emptyList<Double>()
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots = listOf(PlotDef("pvt", "PVT: ", PlotType.LINE))

    internal class Scratch(val sum: Double)

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0)

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val close = candles[i].close
        val volume = candles[i].volume ?: 1.0
        val prevClose = if (i > 0) candles[i - 1].close else close
        val x = if (prevClose != 0.0) ((close - prevClose) / prevClose) * volume else 0.0
        return Scratch(s.sum + x)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> =
        mapOf("pvt" to (scratch as Scratch).sum)
}

/** AVP（I-26，averagePrice.ts）：累计 turnover/volume；零累计量无定义（Missing）。 */
class AveragePriceIndicator : IndicatorDefinition {
    override val name = "AVP"
    override val shortName = "AVP"
    override val calcParams = emptyList<Double>()
    override val series = IndicatorSeries.PRICE
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots = listOf(PlotDef("avp", "AVP: ", PlotType.LINE))

    internal class Scratch(val totalTurnover: Double, val totalVolume: Double)

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0, 0.0)

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val c = candles[i]
        return Scratch(
            s.totalTurnover + (c.turnover ?: 0.0),
            s.totalVolume + (c.volume ?: 0.0),
        )
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        // totalVolume==0（含缺失记 0 的情形）无定义 → Missing（与上游 undefined 一致）
        if (s.totalVolume == 0.0) return emptyMap()
        return mapOf("avp" to s.totalTurnover / s.totalVolume)
    }
}

/** DMA（I-27，differentOfMovingAverage.ts）：双 MA 差 + 派生回看的窗口均值。 */
class DifferentOfMovingAverageIndicator : IndicatorDefinition {
    override val name = "DMA"
    override val shortName = "DMA"
    override val calcParams = listOf(10.0, 50.0, 10.0)
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots = listOf(
        PlotDef("dma", "DMA: ", PlotType.LINE),
        PlotDef("ama", "AMA: ", PlotType.LINE),
    )

    internal class Scratch(
        val closeSum1: Double,
        val closeSum2: Double,
        val dmaSum: Double,
    )

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0, 0.0, 0.0)

    /** 第 j 行的 DIF（双窗口均值差；j >= p2-1 有效）。 */
    private fun difAt(candles: List<Candle>, j: Int, p1: Int, p2: Int): Double? {
        if (j < p2 - 1) return null
        var s1 = 0.0
        for (k in j - (p1 - 1)..j) s1 += candles[k].close
        var s2 = 0.0
        for (k in j - (p2 - 1)..j) s2 += candles[k].close
        return s1 / p1 - s2 / p2
    }

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val p1 = params[0].toInt()
        val p2 = params[1].toInt()
        val p3 = params[2].toInt()
        val maxPeriod = maxOf(p1, p2)
        val close = candles[i].close
        var closeSum1 = s.closeSum1 + close
        var closeSum2 = s.closeSum2 + close
        if (i >= p1) closeSum1 -= candles[i - p1].close
        if (i >= p2) closeSum2 -= candles[i - p2].close

        var dmaSum = s.dmaSum
        difAt(candles, i, p1, p2)?.let { dmaSum += it }
        // 维持最近 p3 个 dif 之和：滑出的是 dif(i-p3)
        if (i - p3 >= maxPeriod - 1) {
            difAt(candles, i - p3, p1, p2)?.let { dmaSum -= it }
        }
        return Scratch(closeSum1, closeSum2, dmaSum)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val p1 = params[0].toInt()
        val p2 = params[1].toInt()
        val maxPeriod = maxOf(p1, p2)
        val dif = difAt(candles, i, p1, p2) ?: return emptyMap()
        val row = HashMap<String, Double>(2)
        row["dma"] = dif
        if (i >= maxPeriod + params[2] - 2) row["ama"] = s.dmaSum / params[2]
        return row
    }
}
