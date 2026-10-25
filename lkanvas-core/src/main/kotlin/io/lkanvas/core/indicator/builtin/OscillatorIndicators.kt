package io.lkanvas.core.indicator.builtin

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * T-13 振荡/方向/量指标组（I-13~I-21）。公式与上游 v10.1.0 逐位对齐。
 *
 * 增量能力（PRD R-08 能力清单）：DMI（ADXR 需历史 ADX 回看）与 CR
 * （MA 线需带前移的历史序列回看）声明 FULL_ONLY，其余支持增量。
 * volume 缺失按 D-03：VR/EMV 窗口内含缺失 → Missing（算术以 0 占位）。
 */

/** KDJ（I-13，stoch.ts）：RSV 窗口高低 + K/D 递推（无前值用 50），J=3K-2D。 */
class StochIndicator : IndicatorDefinition {
    override val name = "KDJ"
    override val shortName = "KDJ"
    override val calcParams = listOf(9.0, 3.0, 3.0)
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots = listOf(
        PlotDef("k", "K: ", PlotType.LINE),
        PlotDef("d", "D: ", PlotType.LINE),
        PlotDef("j", "J: ", PlotType.LINE),
    )

    internal class Scratch(val prevK: Double, val prevD: Double, val hasPrev: Boolean)

    override fun initScratch(params: List<Double>): Any? = Scratch(50.0, 50.0, false)

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val p = params[0].toInt()
        val k1 = params[1]
        val d1 = params[2]
        if (i < p - 1) return Scratch(s.prevK, s.prevD, s.hasPrev)
        var hn = Double.NEGATIVE_INFINITY
        var ln = Double.POSITIVE_INFINITY
        for (j in i - (p - 1)..i) {
            hn = max(hn, candles[j].high)
            ln = min(ln, candles[j].low)
        }
        val sub = hn - ln
        val rsv = ((candles[i].close - ln) / (if (sub == 0.0) 1.0 else sub)) * 100
        val prevK = if (s.hasPrev) s.prevK else 50.0
        val prevD = if (s.hasPrev) s.prevD else 50.0
        val k = ((k1 - 1) * prevK + rsv) / k1
        val d = ((d1 - 1) * prevD + k) / d1
        return Scratch(k, d, true)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        if (!s.hasPrev) return emptyMap()
        val j = 3.0 * s.prevK - 2.0 * s.prevD
        return mapOf("k" to s.prevK, "d" to s.prevD, "j" to j)
    }
}

/** RSI（I-14，relativeStrengthIndex.ts）：RMA 均涨/均跌递推（种子=前 p 个变化均值，i>=p 有效）。 */
class RelativeStrengthIndexIndicator : IndicatorDefinition {
    override val name = "RSI"
    override val shortName = "RSI"
    override val calcParams = listOf(6.0, 12.0, 24.0)
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots: List<PlotDef> = plotsFor(calcParams)

    override fun plotsFor(params: List<Double>): List<PlotDef> =
        params.mapIndexed { i, _ -> PlotDef("rsi${i + 1}", "RSI${i + 1}: ", PlotType.LINE) }

    internal class Scratch(
        val gainSums: DoubleArray,
        val lossSums: DoubleArray,
        val avgGains: DoubleArray,
        val avgLosses: DoubleArray,
        val seeded: BooleanArray,
    )

    override fun initScratch(params: List<Double>): Any? = Scratch(
        DoubleArray(params.size), DoubleArray(params.size),
        DoubleArray(params.size), DoubleArray(params.size),
        BooleanArray(params.size),
    )

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val gainSums = s.gainSums.copyOf()
        val lossSums = s.lossSums.copyOf()
        val avgGains = s.avgGains.copyOf()
        val avgLosses = s.avgLosses.copyOf()
        val seeded = s.seeded.copyOf()
        val change = if (i == 0) 0.0 else candles[i].close - candles[i - 1].close
        val gain = max(change, 0.0)
        val loss = max(-change, 0.0)
        for (k in params.indices) {
            val p = params[k]
            gainSums[k] += gain
            lossSums[k] += loss
            if (i < p) continue
            if (!seeded[k]) {
                avgGains[k] = gainSums[k] / p
                avgLosses[k] = lossSums[k] / p
                seeded[k] = true
            } else {
                avgGains[k] = (avgGains[k] * (p - 1) + gain) / p
                avgLosses[k] = (avgLosses[k] * (p - 1) + loss) / p
            }
        }
        return Scratch(gainSums, lossSums, avgGains, avgLosses, seeded)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val row = HashMap<String, Double>(params.size)
        for (k in params.indices) {
            if (!s.seeded[k]) continue
            val rsi = when {
                s.avgLosses[k] == 0.0 -> 100.0
                s.avgGains[k] == 0.0 -> 0.0
                else -> 100 - 100 / (1 + s.avgGains[k] / s.avgLosses[k])
            }
            row["rsi${k + 1}"] = rsi
        }
        return row
    }
}

/** WR 威廉（I-15，williamsR.ts）：100*(C-HH(N))/(HH(N)-LL(N))；高低相同取 0。 */
class WilliamsRIndicator : IndicatorDefinition {
    override val name = "WR"
    override val shortName = "WR"
    override val calcParams = listOf(6.0, 10.0, 14.0)
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots: List<PlotDef> = plotsFor(calcParams)

    override fun plotsFor(params: List<Double>): List<PlotDef> =
        params.mapIndexed { i, _ -> PlotDef("wr${i + 1}", "WR${i + 1}: ", PlotType.LINE) }

    internal object Scratch

    override fun initScratch(params: List<Double>): Any? = Scratch

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? = Scratch

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val row = HashMap<String, Double>(params.size)
        val close = candles[i].close
        for (k in params.indices) {
            val p = params[k].toInt()
            if (i < p - 1) continue
            var hn = Double.NEGATIVE_INFINITY
            var ln = Double.POSITIVE_INFINITY
            for (j in i - (p - 1)..i) {
                hn = max(hn, candles[j].high)
                ln = min(ln, candles[j].low)
            }
            val sub = hn - ln
            row["wr${k + 1}"] = if (sub == 0.0) 0.0 else ((close - hn) / sub) * 100
        }
        return row
    }
}

/** CCI（I-16，commodityChannelIndex.ts）：(TP-MA(TP,N))/MD/0.015；MD=0 取 0。 */
class CommodityChannelIndexIndicator : IndicatorDefinition {
    override val name = "CCI"
    override val shortName = "CCI"
    override val calcParams = listOf(20.0)
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots = listOf(PlotDef("cci", "CCI: ", PlotType.LINE))

    internal class Scratch(val tpSum: Double)

    private fun tp(candles: List<Candle>, i: Int): Double =
        (candles[i].high + candles[i].low + candles[i].close) / 3

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0)

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        var tpSum = s.tpSum + tp(candles, i)
        val p = params[0].toInt()
        if (i >= p) tpSum -= tp(candles, i - p)
        return Scratch(tpSum)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val p = params[0].toInt()
        if (i < p - 1) return emptyMap()
        val maTp = s.tpSum / p
        var sum = 0.0
        for (j in i - (p - 1)..i) sum += abs(tp(candles, j) - maTp)
        val md = sum / p
        val currentTp = tp(candles, i)
        return mapOf("cci" to if (md != 0.0) (currentTp - maTp) / md / 0.015 else 0.0)
    }
}

/** DMI（I-17，directionalMovementIndex.ts）：EXPMEMA 体系 + ADXR 历史回看（FULL_ONLY）。 */
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
        var pdi = 0.0
        var mdi = 0.0
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
                pdi = dmp * 100 / mtr
                mdi = dmm * 100 / mtr
            }
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

/** BRAR（I-18，brar.ts）：N 日 (H-CY)/(CY-L) 与 (H-O)/(O-L) 滚动和比值。 */
class BrarIndicator : IndicatorDefinition {
    override val name = "BRAR"
    override val shortName = "BRAR"
    override val calcParams = listOf(26.0)
    override val precision = 2
    override val incremental = IncrementalCapability.REPLACE_APPEND

    override val plots = listOf(
        PlotDef("br", "BR: ", PlotType.LINE),
        PlotDef("ar", "AR: ", PlotType.LINE),
    )

    internal class Scratch(val hcy: Double, val cyl: Double, val ho: Double, val ol: Double)

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0, 0.0, 0.0, 0.0)

    /** candle j 的四项贡献。 */
    private fun contributions(candles: List<Candle>, j: Int): DoubleArray {
        val c = candles[j]
        val prevClose = if (j > 0) candles[j - 1].close else c.close
        return doubleArrayOf(
            c.high - prevClose,
            prevClose - c.low,
            c.high - c.open,
            c.open - c.low,
        )
    }

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val n = params[0].toInt()
        val add = contributions(candles, i)
        var hcy = s.hcy + add[0]
        var cyl = s.cyl + add[1]
        var ho = s.ho + add[2]
        var ol = s.ol + add[3]
        if (i >= n) {
            val sub = contributions(candles, i - n)
            hcy -= sub[0]
            cyl -= sub[1]
            ho -= sub[2]
            ol -= sub[3]
        }
        return Scratch(hcy, cyl, ho, ol)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val n = params[0].toInt()
        if (i < n - 1) return emptyMap()
        val row = HashMap<String, Double>(2)
        row["ar"] = if (s.ol != 0.0) (s.ho / s.ol) * 100 else 0.0
        row["br"] = if (s.cyl != 0.0) (s.hcy / s.cyl) * 100 else 0.0
        return row
    }
}

/** CR（I-19，currentRatio.ts）：中间价能量比 + 四条带前移的 CR 均线（FULL_ONLY）。 */
class CurrentRatioIndicator : IndicatorDefinition {
    override val name = "CR"
    override val shortName = "CR"
    override val calcParams = listOf(26.0, 10.0, 20.0, 40.0, 60.0)
    override val precision = 2
    override val incremental = IncrementalCapability.FULL_ONLY

    override val plots = listOf(
        PlotDef("cr", "CR: ", PlotType.LINE),
        PlotDef("ma1", "MA1: ", PlotType.LINE),
        PlotDef("ma2", "MA2: ", PlotType.LINE),
        PlotDef("ma3", "MA3: ", PlotType.LINE),
        PlotDef("ma4", "MA4: ", PlotType.LINE),
    )

    internal class Scratch(
        val highSubSum: Double,
        val preMidSubSum: Double,
        val maSums: DoubleArray,
        /** 每条 MA 线的均值序列（带前移回看用）。 */
        val maLists: List<ArrayList<Double>>,
        /** 逐行 CR 值（滚动扣减用）。 */
        val crHistory: List<Double>,
    )

    override fun initScratch(params: List<Double>): Any? = Scratch(
        0.0, 0.0, DoubleArray(4),
        List(4) { ArrayList<Double>() },
        emptyList(),
    )

    private fun prevMid(candles: List<Candle>, j: Int): Double {
        val prev = if (j > 0) candles[j - 1] else candles[j]
        return (prev.high + prev.low) / 2
    }

    private fun pair(candles: List<Candle>, j: Int): DoubleArray {
        val mid = prevMid(candles, j)
        val c = candles[j]
        return doubleArrayOf(max(0.0, c.high - mid), max(0.0, mid - c.low))
    }

    override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? {
        val s = prev as Scratch
        val n = params[0].toInt()
        val add = pair(candles, i)
        var highSubSum = s.highSubSum + add[0]
        var preMidSubSum = s.preMidSubSum + add[1]
        if (i >= n) {
            val sub = pair(candles, i - n)
            highSubSum -= sub[0]
            preMidSubSum -= sub[1]
        }
        val maSums = s.maSums.copyOf()
        val maLists = s.maLists.map { ArrayList(it) }
        val crHistory = ArrayList<Double>(s.crHistory)

        if (i >= n - 1) {
            val cr = if (preMidSubSum != 0.0) highSubSum / preMidSubSum * 100 else 0.0
            crHistory += cr
            for (k in 0 until 4) {
                maSums[k] += cr
                val m = params[k + 1]
                val mI = m.toInt()
                if (i >= n + m - 2) {
                    maLists[k].add(maSums[k] / m)
                    // 上游：滚动扣减 result[i-(M-1)].cr
                    val oldCr = crHistory[(i - (mI - 1)) - (n - 1)]
                    maSums[k] -= oldCr
                }
            }
        }
        return Scratch(highSubSum, preMidSubSum, maSums, maLists, crHistory)
    }

    override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double> {
        val s = scratch as Scratch
        val n = params[0].toInt()
        if (i < n - 1) return emptyMap()
        val row = HashMap<String, Double>(5)
        val cr = s.crHistory.last()
        row["cr"] = cr
        for (k in 0 until 4) {
            val m = params[k + 1]
            val forward = ceil(m / 2.5 + 1).toInt()
            if (i >= n + m + forward - 3) {
                val list = s.maLists[k]
                val idx = list.size - 1 - forward
                // 上游在条件首行读 list[-1] → undefined（Missing），逐位复现
                if (idx >= 0) row["ma${k + 1}"] = list[idx]
            }
        }
        return row
    }
}

/** VR（I-20，volumeRatio.ts）：涨/跌/平量分类滚动和；volume 缺失窗口 → Missing（D-03）。 */
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

    internal class Scratch(
        val uvs: Double,
        val dvs: Double,
        val pvs: Double,
        val vrSum: Double,
        val lastNullVolume: Int,
    )

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0, 0.0, 0.0, 0.0, Int.MIN_VALUE / 2)

    /** candle j 的量分类贡献（+uvs/+dvs/+pvs）。 */
    private fun classify(candles: List<Candle>, j: Int): Int {
        val prevClose = if (j > 0) candles[j - 1].close else candles[j].close
        val c = candles[j]
        return when {
            c.close > prevClose -> 0
            c.close < prevClose -> 1
            else -> 2
        }
    }

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

    private fun vrAt(uvs: Double, dvs: Double, pvs: Double): Double? {
        val halfPvs = pvs / 2
        return if (dvs + halfPvs == 0.0) 0.0 else (uvs + halfPvs) / (dvs + halfPvs) * 100
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

/** EMV（I-21，easeOfMovementValue.ts）：EM 累和与均值；volume 缺失窗口 → Missing（D-03）。 */
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

    internal class Scratch(val emvSum: Double, val lastNullVolume: Int)

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0, Int.MIN_VALUE / 2)

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
