package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType
import kotlin.math.abs
import kotlin.math.max

/** DMI（上游 `directionalMovementIndex.ts` 镜像，I-17）：EXPMEMA 体系 + ADXR 历史回看（FULL_ONLY）。 */
class DirectionalMovementIndexIndicator : IndicatorDefinition {
    override val name = "DMI"
    override val shortName = "DMI"
    override val calcParams = listOf(14.0, 6.0)
    override val precision = 2
    override val incremental = IncrementalCapability.FULL_ONLY

    override val plots = listOf(
        PlotDef("pdi", "PDI: ", PlotType.LINE),
        PlotDef("mdi", "MDI: ", PlotType.LINE),
        PlotDef("adx", "ADX: ", PlotType.LINE),
        PlotDef("adxr", "ADXR: ", PlotType.LINE),
    )

    /**
     * 上游 calc 镜像：mtr/dmp/dmm EXPMEMA 递推、adxHistory 保存历史 ADX。
     * ADXR = (result[i-(M-1)].adx ?? 0 + adx) / 2 —— 上游 `?? 0` 的缺省行为逐位复现
     * （Kotlin 侧历史越界取 0.0）。
     */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        val n = params[0]
        val mI = params[1].toInt()
        var trSum = 0.0
        var hSum = 0.0
        var lSum = 0.0
        var mtr = 0.0
        var dmp = 0.0
        var dmm = 0.0
        var dxSum = 0.0
        var adx = 0.0
        val adxHistory = ArrayList<Double>()
        return candles.mapIndexed { i, kLineData ->
            val dmi = HashMap<String, Double>(4)
            val prev = if (i > 0) candles[i - 1] else kLineData
            val hl = kLineData.high - kLineData.low
            val hcy = abs(kLineData.high - prev.close)
            val lcy = abs(prev.close - kLineData.low)
            val hhy = kLineData.high - prev.high
            val lyl = prev.low - kLineData.low
            val tr = max(max(hl, hcy), lcy)
            val h = if (hhy > 0 && hhy > lyl) hhy else 0.0
            val l = if (lyl > 0 && lyl > hhy) lyl else 0.0
            trSum += tr
            hSum += h
            lSum += l
            if (i >= n - 1) {
                if (i > n - 1) {
                    mtr = mtr - mtr / n + tr
                    dmp = dmp - dmp / n + h
                    dmm = dmm - dmm / n + l
                } else {
                    mtr = trSum
                    dmp = hSum
                    dmm = lSum
                }
                var pdi = 0.0
                var mdi = 0.0
                if (mtr != 0.0) {
                    pdi = dmp * 100 / mtr
                    mdi = dmm * 100 / mtr
                }
                dmi["pdi"] = pdi
                dmi["mdi"] = mdi
                var dx = 0.0
                if (mdi + pdi != 0.0) dx = abs(mdi - pdi) / (mdi + pdi) * 100
                dxSum += dx
                if (i >= n * 2 - 2) {
                    adx = if (i > n * 2 - 2) {
                        (adx * (n - 1) + dx) / n
                    } else {
                        dxSum / n
                    }
                    dmi["adx"] = adx
                    adxHistory += adx
                    if (i >= n * 2 + mI - 3) {
                        // 上游：result[i-(m-1)].adx ?? 0；等价历史回看（越界/未定义按 0）
                        val past = adxHistory.getOrNull((i - (mI - 1)) - (n.toInt() * 2 - 2)) ?: 0.0
                        dmi["adxr"] = (past + adx) / 2
                    }
                }
            }
            dmi
        }
    }

    internal class Scratch(
        val trSum: Double,
        val hSum: Double,
        val lSum: Double,
        val mtr: Double,
        val dmp: Double,
        val dmm: Double,
        val dxSum: Double,
        val adx: Double,
        /** 自首个 ADX 行起的历史 ADX 序列（ADXR 回看用）。 */
        val adxHistory: List<Double>,
    )

    override fun initScratch(params: List<Double>): Any? =
        Scratch(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, emptyList())

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val n = params[0]
        val cur = candles[i]
        val prevC = if (i > 0) candles[i - 1] else cur
        val hl = cur.high - cur.low
        val hcy = abs(cur.high - prevC.close)
        val lcy = abs(prevC.close - cur.low)
        val hhy = cur.high - prevC.high
        val lyl = prevC.low - cur.low
        val tr = max(max(hl, hcy), lcy)
        val h = if (hhy > 0 && hhy > lyl) hhy else 0.0
        val l = if (lyl > 0 && lyl > hhy) lyl else 0.0
        val trSum = s.trSum + tr
        val hSum = s.hSum + h
        val lSum = s.lSum + l

        var mtr = s.mtr
        var dmp = s.dmp
        var dmm = s.dmm
        var dxSum = s.dxSum
        var adx = s.adx
        val adxHistory = ArrayList<Double>(s.adxHistory)

        if (i >= n - 1) {
            if (i > n - 1) {
                mtr = mtr - mtr / n + tr
                dmp = dmp - dmp / n + h
                dmm = dmm - dmm / n + l
            } else {
                mtr = trSum
                dmp = hSum
                dmm = lSum
            }
            if (mtr != 0.0) {
                val pdi = dmp * 100 / mtr
                val mdi = dmm * 100 / mtr
                var dx = 0.0
                if (mdi + pdi != 0.0) dx = abs(mdi - pdi) / (mdi + pdi) * 100
                dxSum += dx
                if (i >= n * 2 - 2) {
                    adx = if (i > n * 2 - 2) {
                        (adx * (n - 1) + dx) / n
                    } else {
                        dxSum / n
                    }
                    adxHistory += adx
                }
            }
        }
        return Scratch(trSum, hSum, lSum, mtr, dmp, dmm, dxSum, adx, adxHistory)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val n = params[0]
        val mI = params[1].toInt()
        val nI = params[0].toInt()
        if (i < n - 1) return emptyMap()
        val row = HashMap<String, Double>(4)
        row["pdi"] = if (s.mtr != 0.0) s.dmp * 100 / s.mtr else 0.0
        row["mdi"] = if (s.mtr != 0.0) s.dmm * 100 / s.mtr else 0.0
        if (i >= nI * 2 - 2) {
            row["adx"] = s.adx
            if (i >= nI * 2 + mI - 3) {
                // adxHistory[k] = 首个 ADX 行（2N-2）之后的第 k 个 ADX
                val idx = (i - (mI - 1)) - (nI * 2 - 2)
                val pastAdx = s.adxHistory.getOrNull(idx) ?: 0.0
                row["adxr"] = (pastAdx + s.adx) / 2
            }
        }
        return row
    }
}
