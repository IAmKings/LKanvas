package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.IndicatorSeries
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType

/**
 * VOL 成交量（上游 `src/extension/indicator/volume.ts` 镜像，I-02）。
 *
 * [calc] 逐行对照上游：volSums 循环外滚动和、窗口满先输出后减滑出根。
 * 差异 D-03（登记）：volume 缺失时均线输出 Missing（掩码）；上游按 0 计入
 * 无掩码——calc 与 Scratch 沿用 D-03，双路径一致。
 */
class VolumeIndicator : IndicatorDefinition {
    override val name = "VOL"
    override val shortName = "VOL"
    override val calcParams = listOf(5.0, 10.0, 20.0)
    override val precision = 0
    override val series = IndicatorSeries.VOLUME
    override val incremental = IncrementalCapability.REPLACE_APPEND
    // 上游元数据对齐（F1 diff）：minValue=0、shouldFormatBigNumber=true
    override val minValue: Double = 0.0
    override val shouldFormatBigNumber = true

    override val plots: List<PlotDef> = plotsFor(calcParams)

    override fun plotsFor(params: List<Double>): List<PlotDef> =
        params.mapIndexed { i, p -> PlotDef("ma${i + 1}", "MA${p.toInt()}: ", PlotType.LINE) } +
            PlotDef(
                "volume", "VOLUME: ", PlotType.BAR, baseValue = 0.0,
                // Q05（第四轮复审 2026-10-30）：上游 volume.ts getVolumeFigure styles
                // 镜像——按 compareRule 涨跌着色（函数约定 (row, params, value, candles)）
                styles = { row: Int, _: List<Double>, _: Double, candles: List<io.lkanvas.core.data.Candle> ->
                    val c = candles.getOrNull(row)
                    val color = when {
                        c == null -> "#76808F"
                        c.close > c.open -> "#2DC08E"
                        c.close < c.open -> "#F92855"
                        else -> "#76808F"
                    }
                    mapOf("color" to color)
                },
            )

    /** 上游 calc 镜像 + D-03 掩码：窗口内含缺失 volume → 均线缺键（Missing）。 */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        val keys = plotsFor(params).map { it.key }
        val volSums = DoubleArray(params.size)
        var lastNullIndex = Int.MIN_VALUE / 2
        return candles.mapIndexed { i, kLineData ->
            val volume = kLineData.volume
            if (volume == null) lastNullIndex = i
            val row = HashMap<String, Double>(params.size + 1)
            // D-03：缺失 volume 行输出 Missing（有效 0 正常输出）
            row["volume"] = volume ?: Double.NaN
            val vv = volume ?: 0.0
            params.forEachIndexed { index, p ->
                volSums[index] += vv
                val period = p.toInt()
                // 上游：i >= p-1 时先输出后减 dataList[i-(p-1)]（同一分支内）
                if (i >= period - 1) {
                    // D-03：窗口内含缺失 volume → 该窗口均线缺键（Missing）
                    if (i - lastNullIndex > period - 1) {
                        row[keys[index]] = volSums[index] / period
                    }
                    volSums[index] -= candles[i - (period - 1)].volume ?: 0.0
                }
            }
            row
        }
    }

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
