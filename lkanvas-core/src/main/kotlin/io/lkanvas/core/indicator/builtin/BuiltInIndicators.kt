package io.lkanvas.core.indicator.builtin

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.IndicatorSeries
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType

/**
 * MA 移动平均（I-01，上游 src/extension/indicator/movingAverage.ts）。
 *
 * 公式：第 i 根的 MA(p) = close[i-p+1..i] 的算术平均；i < p-1 为 Missing。
 * 状态：以 i 结尾的精确窗口和（滚动求和，减去滑出元素）。
 */
class MovingAverageIndicator : IndicatorDefinition {
    override val name = "MA"
    override val shortName = "MA"
    override val calcParams = listOf(5.0, 10.0, 30.0, 60.0)
    override val precision = 2
    override val series = IndicatorSeries.PRICE
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots: List<PlotDef> = plotsFor(calcParams)

    override fun plotsFor(params: List<Double>): List<PlotDef> =
        params.mapIndexed { i, p -> PlotDef("ma${i + 1}", "MA${p.toInt()}: ", PlotType.LINE) }

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

/**
 * VOL 成交量（I-02，上游 volume.ts）。
 *
 * 差异 D-03：volume 缺失时输出 Missing（NaN）；上游按 0 计入均线。
 * 输入 volume 为有效 0 时按公式正常处理，两者不混淆。
 */
class VolumeIndicator : IndicatorDefinition {
    override val name = "VOL"
    override val shortName = "VOL"
    override val calcParams = listOf(5.0, 10.0, 20.0)
    override val precision = 0
    override val series = IndicatorSeries.VOLUME
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots: List<PlotDef> = plotsFor(calcParams)

    override fun plotsFor(params: List<Double>): List<PlotDef> =
        params.mapIndexed { i, p -> PlotDef("ma${i + 1}", "MA${p.toInt()}: ", PlotType.LINE) } +
            PlotDef("volume", "VOLUME: ", PlotType.BAR, baseValue = 0.0)

    internal class Scratch(val sums: DoubleArray, val periods: IntArray, val lastNullIndex: Int)

    override fun initScratch(params: List<Double>): Any? = Scratch(
        DoubleArray(params.size),
        params.map { it.toInt() }.toIntArray(),
        lastNullIndex = Int.MIN_VALUE / 2,
    )

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val volume = candles[i].volume
        val lastNull = if (volume == null) i else s.lastNullIndex
        // 算术占位：null 记 0（窗口内含 null 的输出被 lastNull 掩码为 Missing，
        // null 滑出窗口后和值恢复精确）
        val vv = volume ?: 0.0
        val sums = s.sums.copyOf()
        for (k in s.periods.indices) {
            val p = s.periods[k]
            sums[k] += vv
            if (i >= p) sums[k] -= candles[i - p].volume ?: 0.0
        }
        return Scratch(sums, s.periods, lastNull)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val volume = candles[i].volume
        val row = HashMap<String, Double>(s.periods.size + 1)
        row["volume"] = volume ?: Double.NaN
        for (k in s.periods.indices) {
            val p = s.periods[k]
            // 窗口内含缺失 volume → Missing（D-03）；有效 0 正常参与
            if (i >= p - 1 && i - s.lastNullIndex > p - 1) {
                row["ma${k + 1}"] = s.sums[k] / p
            }
        }
        return row
    }
}

/**
 * MACD（I-03，上游 movingAverageConvergenceDivergence.ts）。
 *
 * 公式（与上游逐位一致）：
 * - EMA(p) 种子 = 前 p 根 close 的 SMA（i == p-1 时），其后标准 EMA 递推
 *   `ema = (2*close + (p-1)*emaPrev) / (p+1)`；
 * - DIF 从 i >= maxPeriod-1 起有效（EMA12 与 EMA26 均就绪）；
 * - DEA 种子 = 首批 p2 个 DIF 的 SMA（i == maxPeriod+p2-2），其后 EMA 递推；
 * - MACD 柱 = (DIF - DEA) * 2。
 */
class MacdIndicator : IndicatorDefinition {
    override val name = "MACD"
    override val shortName = "MACD"
    override val calcParams = listOf(12.0, 26.0, 9.0)
    override val precision = 2
    override val series = IndicatorSeries.NORMAL
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots = listOf(
        PlotDef("dif", "DIF: ", PlotType.LINE),
        PlotDef("dea", "DEA: ", PlotType.LINE),
        PlotDef("macd", "MACD: ", PlotType.BAR, baseValue = 0.0),
    )

    internal class Scratch(
        val closeSum: Double,
        val emaShort: Double,
        val emaLong: Double,
        val dif: Double,
        val difSum: Double,
        val dea: Double,
    )

    override fun initScratch(params: List<Double>): Any? =
        Scratch(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val short = params[0].toInt()
        val long = params[1].toInt()
        val signal = params[2].toInt()
        val maxPeriod = maxOf(short, long)
        val close = candles[i].close
        val closeSum = s.closeSum + close

        var emaShort = s.emaShort
        var emaLong = s.emaLong
        var dif = s.dif
        var difSum = s.difSum
        var dea = s.dea

        if (i >= short - 1) {
            emaShort = if (i > short - 1) {
                (2 * close + (short - 1) * s.emaShort) / (short + 1)
            } else {
                closeSum / short
            }
        }
        if (i >= long - 1) {
            emaLong = if (i > long - 1) {
                (2 * close + (long - 1) * s.emaLong) / (long + 1)
            } else {
                closeSum / long
            }
        }
        if (i >= maxPeriod - 1) {
            dif = emaShort - emaLong
            difSum += dif
            if (i >= maxPeriod + signal - 2) {
                dea = if (i > maxPeriod + signal - 2) {
                    (dif * 2 + s.dea * (signal - 1)) / (signal + 1)
                } else {
                    difSum / signal
                }
            }
        }
        return Scratch(closeSum, emaShort, emaLong, dif, difSum, dea)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val short = params[0].toInt()
        val long = params[1].toInt()
        val signal = params[2].toInt()
        val maxPeriod = maxOf(short, long)
        val row = HashMap<String, Double>(3)
        if (i >= maxPeriod - 1) {
            row["dif"] = s.dif
            if (i >= maxPeriod + signal - 2) {
                row["dea"] = s.dea
                row["macd"] = (s.dif - s.dea) * 2
            }
        }
        return row
    }
}
