package io.lkanvas.core.extension.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IncrementalCapability
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType

/** BRAR（上游 `brar.ts` 镜像，I-18）：N 日 (H-CY)/(CY-L) 与 (H-O)/(O-L) 滚动和比值。 */
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

    private fun prevClose(candles: List<Candle>, j: Int): Double =
        if (j > 0) candles[j - 1].close else candles[j].close

    /** 上游 calc 镜像：四项滚动和、窗口满先输出 BR/AR、再减滑出根贡献。 */
    override fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        val n = params[0].toInt()
        var hcy = 0.0
        var cyl = 0.0
        var ho = 0.0
        var ol = 0.0
        return candles.mapIndexed { i, kLineData ->
            val brar = HashMap<String, Double>(2)
            val high = kLineData.high
            val low = kLineData.low
            val open = kLineData.open
            val pc = prevClose(candles, i)
            ho += high - open
            ol += open - low
            hcy += high - pc
            cyl += pc - low
            if (i >= n - 1) {
                brar["ar"] = if (ol != 0.0) (ho / ol) * 100 else 0.0
                brar["br"] = if (cyl != 0.0) (hcy / cyl) * 100 else 0.0
                // 上游：滑出根贡献（agoPreClose = dataList[i-n] ?? dataList[i-(n-1)] 的 close）
                val ago = candles[i - (n - 1)]
                val agoPc = if (i - n >= 0) candles[i - n].close else ago.close
                hcy -= ago.high - agoPc
                cyl -= agoPc - ago.low
                ho -= ago.high - ago.open
                ol -= ago.open - ago.low
            }
            brar
        }
    }

    internal class Scratch(val hcy: Double, val cyl: Double, val ho: Double, val ol: Double)

    override fun initScratch(params: List<Double>): Any? = Scratch(0.0, 0.0, 0.0, 0.0)

    private fun contributions(candles: List<Candle>, j: Int): DoubleArray {
        val c = candles[j]
        val pc = if (j > 0) candles[j - 1].close else c.close
        return doubleArrayOf(
            c.high - pc,
            pc - c.low,
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
