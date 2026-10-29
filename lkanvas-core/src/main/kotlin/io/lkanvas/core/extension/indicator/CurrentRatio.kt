package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType
import kotlin.math.ceil
import kotlin.math.max

/** CR（上游 `currentRatio.ts` 镜像，I-19）：中间价能量比 + 四条带前移的 CR 均线（FULL_ONLY）。 */
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

    /**
     * 上游 calc 镜像：四条 MA 各自 sum+list，前移期 forward = ceil(M/2.5+1)，
     * `maK = list[list.size-1-forward]`（上游读 undefined → 缺键 Missing，逐位复现）；
     * 滚动扣减 = result[i-(M-1)].cr ?? 0（等价于按 crHistory 回看，缺省 0）。
     */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        val n = params[0].toInt()
        val forwards = Array(4) { k -> ceil(params[k + 1] / 2.5 + 1).toInt() }
        val maSums = DoubleArray(4)
        val maLists = Array(4) { ArrayList<Double>() }
        var highSubSum = 0.0
        var preMidSubSum = 0.0
        val crHistory = ArrayList<Double>()
        return candles.mapIndexed { i, kLineData ->
            val cr = HashMap<String, Double>(5)
            val prev = if (i > 0) candles[i - 1] else kLineData
            val prevMid = (prev.high + prev.low) / 2
            highSubSum += max(0.0, kLineData.high - prevMid)
            preMidSubSum += max(0.0, prevMid - kLineData.low)
            if (i >= n) {
                // 上游：outPrevData = dataList[i-n-1] ?? outData
                val out = candles[i - n]
                val outPrev = if (i - n - 1 >= 0) candles[i - n - 1] else out
                val outPrevMid = (outPrev.high + outPrev.low) / 2
                highSubSum -= max(0.0, out.high - outPrevMid)
                preMidSubSum -= max(0.0, outPrevMid - out.low)
            }
            if (i >= n - 1) {
                val crValue = if (preMidSubSum != 0.0) highSubSum / preMidSubSum * 100 else 0.0
                cr["cr"] = crValue
                crHistory += crValue
                for (k in 0 until 4) {
                    val m = params[k + 1].toInt()
                    maSums[k] += crValue
                    if (i >= n + m - 2) {
                        maLists[k].add(maSums[k] / m)
                        val forward = forwards[k]
                        if (i >= n + m + forward - 3) {
                            val idx = maLists[k].size - 1 - forward
                            // 上游在条件首行读 list[-1] → undefined（Missing），逐位复现
                            if (idx >= 0) cr["ma${k + 1}"] = maLists[k][idx]
                        }
                        // 上游：maSum -= result[i-(M-1)].cr ?? 0
                        val oldIdx = (i - (m - 1)) - (n - 1)
                        maSums[k] -= crHistory.getOrNull(oldIdx) ?: 0.0
                    }
                }
            }
            cr
        }
    }

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
