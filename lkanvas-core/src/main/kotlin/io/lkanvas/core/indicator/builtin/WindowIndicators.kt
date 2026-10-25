package io.lkanvas.core.indicator.builtin

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.IndicatorSeries
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * T-12 均值/窗口指标组（I-04~I-12）。公式与上游 v10.1.0 逐位对齐。
 *
 * 状态约定：stepScratch 完成后，scratch 内的滚动和 = **以 i 结尾的完整窗口和**
 * （窗口满后加入当前根的同时扣减滑出根），rowFromScratch 直接以状态取值，
 * 不重复加减当前根。EMA/SMA 的 closeSum 为自首根累计（种子需要）。
 *
 * 除零约定：ROC 回看值为零按上游显式代码取 0（有效值）；BIAS 均值为零
 * 无定义 → Missing（R-08 禁止 Infinity，登记于台账）。
 */

/** EMA 指数移动平均（I-04，exponentialMovingAverage.ts）。 */
class ExponentialMovingAverageIndicator : IndicatorDefinition {
    override val name = "EMA"
    override val shortName = "EMA"
    override val calcParams = listOf(6.0, 12.0, 20.0)
    override val precision = 2
    override val series = IndicatorSeries.PRICE
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots: List<PlotDef> = plotsFor(calcParams)

    override fun plotsFor(params: List<Double>): List<PlotDef> =
        params.mapIndexed { i, p -> PlotDef("ema${i + 1}", "EMA${p.toInt()}: ", PlotType.LINE) }

    internal class Scratch(val closeSum: Double, val emaValues: DoubleArray)

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0, DoubleArray(params.size))

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val closeSum = s.closeSum + candles[i].close
        val emaValues = s.emaValues.copyOf()
        val close = candles[i].close
        for (k in params.indices) {
            val p = params[k]
            if (i >= p - 1) {
                emaValues[k] = if (i > p - 1) {
                    (2 * close + (p - 1) * emaValues[k]) / (p + 1)
                } else {
                    closeSum / p
                }
            }
        }
        return Scratch(closeSum, emaValues)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val row = HashMap<String, Double>(params.size)
        for (k in params.indices) {
            if (i >= params[k] - 1) row["ema${k + 1}"] = s.emaValues[k]
        }
        return row
    }
}

/** SMA（I-05，simpleMovingAverage.ts）：上游递推口径 (C*w + prev*(N-w+1))/(N+1)，种子 SMA(N)。 */
class SimpleMovingAverageIndicator : IndicatorDefinition {
    override val name = "SMA"
    override val shortName = "SMA"
    override val calcParams = listOf(12.0, 2.0)
    override val precision = 2
    override val series = IndicatorSeries.PRICE
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots = listOf(PlotDef("sma", "SMA: ", PlotType.LINE))

    internal class Scratch(val closeSum: Double, val smaValue: Double)

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0, 0.0)

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val close = candles[i].close
        val closeSum = s.closeSum + close
        val n = params[0]
        val w = params[1]
        val smaValue = when {
            i.toDouble() > n - 1 -> (close * w + s.smaValue * (n - w + 1)) / (n + 1)
            i.toDouble() == n - 1 -> closeSum / n
            else -> s.smaValue
        }
        return Scratch(closeSum, smaValue)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        return if (i >= params[0] - 1) mapOf("sma" to s.smaValue) else emptyMap()
    }
}

/** BOLL 布林带（I-06，bollingerBands.ts）：MID=MA(N)，MD=窗口偏差平方和（|Σ|/N 开方）。 */
class BollingerBandsIndicator : IndicatorDefinition {
    override val name = "BOLL"
    override val shortName = "BOLL"
    override val calcParams = listOf(20.0, 2.0)
    override val precision = 2
    override val series = IndicatorSeries.PRICE
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots = listOf(
        PlotDef("up", "UP: ", PlotType.LINE),
        PlotDef("mid", "MID: ", PlotType.LINE),
        PlotDef("dn", "DN: ", PlotType.LINE),
    )

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

/** BBI 多空指标（I-07，bullAndBearIndex.ts）：多条 MA 的均值。 */
class BullAndBearIndexIndicator : IndicatorDefinition {
    override val name = "BBI"
    override val shortName = "BBI"
    override val calcParams = listOf(3.0, 6.0, 12.0, 24.0)
    override val precision = 2
    override val series = IndicatorSeries.PRICE
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots = listOf(PlotDef("bbi", "BBI: ", PlotType.LINE))

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

/** BIAS 乖离率（I-08，bias.ts）：(close-MA)/MA*100；MA=0 → Missing（禁止 Infinity，R-08）。 */
class BiasIndicator : IndicatorDefinition {
    override val name = "BIAS"
    override val shortName = "BIAS"
    override val calcParams = listOf(6.0, 12.0, 24.0)
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots: List<PlotDef> = plotsFor(calcParams)

    override fun plotsFor(params: List<Double>): List<PlotDef> =
        params.mapIndexed { i, p -> PlotDef("bias${i + 1}", "BIAS${p.toInt()}: ", PlotType.LINE) }

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
        val row = HashMap<String, Double>(params.size)
        val close = candles[i].close
        for (k in params.indices) {
            val p = params[k].toInt()
            if (i >= p - 1) {
                val mean = s.closeSums[k] / p
                if (mean != 0.0) row["bias${k + 1}"] = ((close - mean) / mean) * 100
            }
        }
        return row
    }
}

/** AO 动量振荡（I-09，awesomeOscillator.ts）：MA(HL/2, S) - MA(HL/2, L)。 */
class AwesomeOscillatorIndicator : IndicatorDefinition {
    override val name = "AO"
    override val shortName = "AO"
    override val calcParams = listOf(5.0, 34.0)
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots = listOf(PlotDef("ao", "AO: ", PlotType.BAR, baseValue = 0.0))

    internal class Scratch(val shortSum: Double, val longSum: Double)

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0, 0.0)

    private fun middle(candles: List<Candle>, i: Int): Double =
        (candles[i].low + candles[i].high) / 2

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        var shortSum = s.shortSum + middle(candles, i)
        var longSum = s.longSum + middle(candles, i)
        val ps = params[0].toInt()
        val pl = params[1].toInt()
        if (i >= ps) shortSum -= middle(candles, i - ps)
        if (i >= pl) longSum -= middle(candles, i - pl)
        return Scratch(shortSum, longSum)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val maxPeriod = maxOf(params[0], params[1]).toInt()
        if (i < maxPeriod - 1) return emptyMap()
        return mapOf("ao" to (s.shortSum / params[0] - s.longSum / params[1]))
    }
}

/** MTM 动量（I-10，momentum.ts）：MTM = C - C[i-N]；MAMTM 为 M 日均值。 */
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

    internal class Scratch(val mtmSum: Double)

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0)

    private fun mtmAt(candles: List<Candle>, i: Int, n: Int): Double? =
        if (i >= n) candles[i].close - candles[i - n].close else null

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

/** ROC 变动率（I-11，rateOfChange.ts）：(C-REF)/REF*100；REF=0 按上游显式约定取 0。 */
class RateOfChangeIndicator : IndicatorDefinition {
    override val name = "ROC"
    override val shortName = "ROC"
    override val calcParams = listOf(12.0, 6.0)
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots = listOf(
        PlotDef("roc", "ROC: ", PlotType.LINE),
        PlotDef("maRoc", "MAROC: ", PlotType.LINE),
    )

    internal class Scratch(val rocSum: Double)

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0)

    private fun rocAt(candles: List<Candle>, i: Int, n: Int): Double? {
        if (i < n) return null
        val ago = candles[i - n].close
        return if (ago != 0.0) ((candles[i].close - ago) / ago) * 100 else 0.0
    }

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val n = params[0].toInt()
        val m = params[1].toInt()
        var rocSum = s.rocSum
        rocAt(candles, i, n)?.let { rocSum += it }
        if (i - m >= n) rocAt(candles, i - m, n)?.let { rocSum -= it }
        return Scratch(rocSum)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val n = params[0].toInt()
        val m = params[1].toInt()
        val roc = rocAt(candles, i, n) ?: return emptyMap()
        val row = HashMap<String, Double>(2)
        row["roc"] = roc
        if (i >= n + m - 1) row["maRoc"] = s.rocSum / m
        return row
    }
}

/** PSY 心理线（I-12，psychologicalLine.ts）：N 日上涨天数占比*100；MAPSY 为 M 日均值。 */
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

    internal class Scratch(val upCount: Int, val psySum: Double)

    override fun initScratch(params: List<Double>): Any? = Scratch(0, 0.0)

    private fun upFlag(candles: List<Candle>, i: Int): Int {
        if (i <= 0) return 0
        return if (candles[i].close > candles[i - 1].close) 1 else 0
    }

    /** 窗口 [j-p+1, j] 内上涨天数（供 psy 滑出窗口扣减）。 */
    private fun upCountAt(candles: List<Candle>, j: Int, p: Int): Int {
        var c = 0
        for (k in j - (p - 1)..j) if (upFlag(candles, k) == 1) c++
        return c
    }

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
                psySum -= upCountAt(candles, i - q, p).toDouble() / p * 100
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
