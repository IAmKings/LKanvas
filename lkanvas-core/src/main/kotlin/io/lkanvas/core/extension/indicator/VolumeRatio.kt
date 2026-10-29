package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType

/**
 * VR（上游 `volumeRatio.ts` 镜像，I-20）：涨/跌/平量分类滚动和。
 * 差异 D-03（登记）：窗口内含缺失 volume → Missing（算术以 0 占位）；
 * 上游 `volume ?? 0` 无掩码——calc 沿用 D-03 与 Scratch 一致。
 */
class VolumeRatioIndicator : IndicatorDefinition {
    override val name = "VR"
    override val shortName = "VR"
    override val calcParams = listOf(26.0, 6.0)
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots = listOf(
        PlotDef("vr", "VR: ", PlotType.LINE),
        PlotDef("maVr", "MAVR: ", PlotType.LINE),
    )

    private fun classify(candles: List<Candle>, j: Int): Int {
        val prevClose = if (j > 0) candles[j - 1].close else candles[j].close
        val c = candles[j]
        return when {
            c.close > prevClose -> 0
            c.close < prevClose -> 1
            else -> 2
        }
    }

    private fun vrAt(uvs: Double, dvs: Double, pvs: Double): Double {
        val halfPvs = pvs / 2
        return if (dvs + halfPvs == 0.0) 0.0 else (uvs + halfPvs) / (dvs + halfPvs) * 100
    }


    /**
     * 上游 calc 镜像（单遍 map 形态）+ D-03 掩码。vrSum 维护与 Scratch 语义一致：
     * 无掩码累加 vr（滚动 uvs/dvs/pvs）、滑出扣减 vrAtCandles(i-m)（窗口含缺失
     * volume 时该旧值无定义按 0 扣减）；D-03 掩码仅作用于输出行。
     * （上游原版 `volume ?? 0` 无掩码，差异登记 D-03。）
     */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        val n = params[0].toInt()
        val m = params[1].toInt()
        var uvs = 0.0
        var dvs = 0.0
        var pvs = 0.0
        var vrSum = 0.0
        var lastNullVolume = Int.MIN_VALUE / 2
        return candles.mapIndexed { i, kLineData ->
            val vr = HashMap<String, Double>(2)
            if (kLineData.volume == null) lastNullVolume = i
            val v = kLineData.volume ?: 0.0
            when (classify(candles, i)) {
                0 -> uvs += v
                1 -> dvs += v
                else -> pvs += v
            }
            if (i >= n) {
                val vOld = candles[i - n].volume ?: 0.0
                when (classify(candles, i - n)) {
                    0 -> uvs -= vOld
                    1 -> dvs -= vOld
                    else -> pvs -= vOld
                }
            }
            if (i >= n - 1) {
                vrSum += vrAt(uvs, dvs, pvs)
                val oldIdx = i - m
                if (oldIdx >= n - 1) {
                    vrSum -= vrAtCandles(candles, oldIdx, n) ?: 0.0
                }
                // D-03：当前窗口含缺失 volume → vr/maVr 缺键（Missing）
                if (i - lastNullVolume >= n) {
                    vr["vr"] = vrAt(uvs, dvs, pvs)
                    if (i >= n + m - 2) vr["maVr"] = vrSum / m
                }
            }
            vr
        }
    }

    internal class Scratch(
        val uvs: Double,
        val dvs: Double,
        val pvs: Double,
        val vrSum: Double,
        val lastNullVolume: Int,
    )

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0, 0.0, 0.0, 0.0, Int.MIN_VALUE / 2)

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val n = params[0].toInt()
        val m = params[1].toInt()
        val cur = candles[i]
        val lastNull = if (cur.volume == null) i else s.lastNullVolume
        val v = cur.volume ?: 0.0
        var uvs = s.uvs
        var dvs = s.dvs
        var pvs = s.pvs
        when (classify(candles, i)) {
            0 -> uvs += v
            1 -> dvs += v
            else -> pvs += v
        }
        if (i >= n) {
            val vOld = candles[i - n].volume ?: 0.0
            when (classify(candles, i - n)) {
                0 -> uvs -= vOld
                1 -> dvs -= vOld
                else -> pvs -= vOld
            }
        }
        // vr 滚动和：维持最近 m 个 vr 之和
        var vrSum = s.vrSum
        val vrNow = vrAt(uvs, dvs, pvs)
        if (i >= n - 1 && vrNow != null) {
            vrSum += vrNow
            val oldIdx = i - m
            if (oldIdx >= n - 1) {
                // 旧 vr 值需按当时窗口重算（FULL 回退语义在增量路径禁用，此处仅全量逐步可达）
                vrSum -= vrAtCandles(candles, oldIdx, n) ?: 0.0
            }
        }
        return Scratch(uvs, dvs, pvs, vrSum, lastNull)
    }

    /** 以 candle 序列重算第 j 行的 vr（滚动扣减用；窗口含缺失 volume 时无定义）。 */
    private fun vrAtCandles(candles: List<Candle>, j: Int, n: Int): Double? {
        var uvs = 0.0
        var dvs = 0.0
        var pvs = 0.0
        for (k in j - (n - 1)..j) {
            val v = candles[k].volume ?: return null
            when (classify(candles, k)) {
                0 -> uvs += v
                1 -> dvs += v
                else -> pvs += v
            }
        }
        return vrAt(uvs, dvs, pvs)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val n = params[0].toInt()
        val m = params[1].toInt()
        if (i < n - 1) return emptyMap()
        // 窗口内含缺失 volume → Missing（D-03）
        if (i - s.lastNullVolume < n) return emptyMap()
        val row = HashMap<String, Double>(2)
        val vr = vrAt(s.uvs, s.dvs, s.pvs) ?: return emptyMap()
        row["vr"] = vr
        if (i >= n + m - 2) row["maVr"] = s.vrSum / m
        return row
    }
}
