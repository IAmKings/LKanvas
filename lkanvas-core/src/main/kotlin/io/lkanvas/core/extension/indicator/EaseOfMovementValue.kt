package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType

/**
 * EMV（上游 `easeOfMovementValue.ts` 镜像，I-21）：EM 累和与均值。
 * 差异 D-03（登记）：volume 缺失 → 该行 emv Missing、均值窗口掩码；
 * 上游 `volume ?? 0` → emv=0 无掩码——calc 沿用 D-03 与 Scratch 一致。
 */
class EaseOfMovementValueIndicator : IndicatorDefinition {
    override val name = "EMV"
    override val shortName = "EMV"
    override val calcParams = listOf(14.0, 9.0)
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots = listOf(
        PlotDef("emv", "EMV: ", PlotType.LINE),
        PlotDef("maEmv", "MAEMV: ", PlotType.LINE),
    )

    /** 第 j 行 EM（j>=1 有效）；volume 缺失 → null（Missing）；volume=0 或平盘 → 0（上游显式）。 */
    private fun emAt(candles: List<Candle>, j: Int): Double? {
        if (j < 1) return null
        val c = candles[j]
        val volume = c.volume ?: return null
        val prev = candles[j - 1]
        val distanceMoved = (c.high + c.low) / 2 - (prev.high + prev.low) / 2
        if (volume == 0.0 || c.high - c.low == 0.0) return 0.0
        val ratio = volume / 100000000.0 / (c.high - c.low)
        return distanceMoved / ratio
    }

    /** 上游 calc 镜像 + D-03：emvSum 先输出均值、后减 emvValueList[i-N]（缺失行为 0 扣减）。 */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        val n = params[0].toInt()
        var emvSum = 0.0
        val emvValues = ArrayList<Double?>()
        return candles.mapIndexed { i, kLineData ->
            val emv = HashMap<String, Double>(2)
            if (i > 0) {
                val value = emAt(candles, i)
                if (value != null) {
                    emv["emv"] = value
                    emvSum += value
                }
                emvValues.add(value)
                if (i >= n) {
                    // D-03：均值窗口 [i-n+1, i] 内含缺失 volume → maEmv Missing
                    var hasNull = false
                    for (k in i - n + 1..i) if (candles[k].volume == null) {
                        hasNull = true
                        break
                    }
                    if (!hasNull) emv["maEmv"] = emvSum / n
                    // 上游：emvSum -= emvValueList[i-n]（缺失行未计入和、扣减按 0）
                    emvSum -= emvValues[i - n] ?: 0.0
                }
            }
            emv
        }
    }

    internal class Scratch(val emvSum: Double, val lastNullVolume: Int)

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0, Int.MIN_VALUE / 2)

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val n = params[0].toInt()
        val lastNull = if (candles[i].volume == null) i else s.lastNullVolume
        var emvSum = s.emvSum
        emAt(candles, i)?.let { emvSum += it }
        if (i - n >= 1) emAt(candles, i - n)?.let { emvSum -= it }
        return Scratch(emvSum, lastNull)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val n = params[0].toInt()
        if (i < 1) return emptyMap()
        val emv = emAt(candles, i) ?: return emptyMap()
        val row = HashMap<String, Double>(2)
        row["emv"] = emv
        // MA 窗口 [i-n+1, i] 内含缺失 volume → Missing
        if (i >= n && i - s.lastNullVolume < n) {
            return row // emv 有效但 maEmv 无定义
        }
        if (i >= n) row["maEmv"] = s.emvSum / n
        return row
    }
}
