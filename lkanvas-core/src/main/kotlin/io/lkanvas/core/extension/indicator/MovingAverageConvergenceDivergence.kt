package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.IndicatorSeries
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType

/**
 * MACD（上游 `src/extension/indicator/movingAverageConvergenceDivergence.ts` 镜像，I-03）。
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
        PlotDef(
            "macd", "MACD: ", PlotType.BAR, baseValue = 0.0,
            // Q05：上游 MACD bar styles 镜像——值正负着色（stroke/fill 增强为 Compose
            // 描边语义延后；色值同 defaultStyles bars[0]）
            styles = { _: Int, _: List<Double>, value: Double, _: List<io.lkanvas.core.data.Candle> ->
                val color = when {
                    value > 0 -> "#2DC08E"
                    value < 0 -> "#F92855"
                    else -> "#76808F"
                }
                mapOf("color" to color)
            },
        ),
    )

    /** 上游 calc 镜像：循环外 EMA/DIF/DEA 递推变量，种子与预热条件照抄。 */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        val short = params[0].toInt()
        val long = params[1].toInt()
        val signal = params[2].toInt()
        val maxPeriod = maxOf(short, long)
        var closeSum = 0.0
        var emaShort = 0.0
        var emaLong = 0.0
        var dif = 0.0
        var difSum = 0.0
        var dea = 0.0
        return candles.mapIndexed { i, kLineData ->
            val macd = HashMap<String, Double>(3)
            val close = kLineData.close
            closeSum += close
            if (i >= short - 1) {
                emaShort = if (i > short - 1) {
                    (2 * close + (short - 1) * emaShort) / (short + 1)
                } else {
                    closeSum / short
                }
            }
            if (i >= long - 1) {
                emaLong = if (i > long - 1) {
                    (2 * close + (long - 1) * emaLong) / (long + 1)
                } else {
                    closeSum / long
                }
            }
            if (i >= maxPeriod - 1) {
                dif = emaShort - emaLong
                macd["dif"] = dif
                difSum += dif
                if (i >= maxPeriod + signal - 2) {
                    dea = if (i > maxPeriod + signal - 2) {
                        (dif * 2 + dea * (signal - 1)) / (signal + 1)
                    } else {
                        difSum / signal
                    }
                    macd["macd"] = (dif - dea) * 2
                    macd["dea"] = dea
                }
            }
            macd
        }
    }

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
