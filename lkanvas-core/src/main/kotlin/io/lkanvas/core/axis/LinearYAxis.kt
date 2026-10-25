package io.lkanvas.core.axis

import io.lkanvas.core.data.Candle
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * 线性纵轴（normal，PRD R-06 Alpha 部分）。
 *
 * 数值计算全部使用 Double，Float 转换只在 Compose 绘制最后一步。
 * 纯函数无状态，任意线程可调用。
 */
object LinearYAxis {

    /** 数值 → y 像素（Double）。topY < bottomY，值大者 y 小。 */
    fun valueToY(value: Double, min: Double, max: Double, topY: Double, bottomY: Double): Double {
        val range = max - min
        if (range == 0.0) return (topY + bottomY) / 2.0
        val t = (value - min) / range
        return topY + (bottomY - topY) * (1.0 - t)
    }

    /** y 像素 → 数值（往返一致性测试用）。 */
    fun yToValue(y: Double, min: Double, max: Double, topY: Double, bottomY: Double): Double {
        val range = max - min
        if (range == 0.0) return min
        val t = 1.0 - (y - topY) / (bottomY - topY)
        return min + range * t
    }

    /**
     * 计算轴刻度（1/2/5 步长），最多 [maxTickCount] 个。
     * min==max 或非有限输入由 [sanitizeRange] 处理后进入。
     */
    fun ticks(min: Double, max: Double, maxTickCount: Int = 6): List<Double> {
        val (lo, hi) = sanitizeRange(min, max)
        if (maxTickCount < 2 || hi <= lo) return listOf(lo, hi).distinct()
        val rawStep = (hi - lo) / (maxTickCount - 1)
        val mag = 10.0.pow(floor(log10(rawStep)))
        val norm = rawStep / mag
        val step = mag * when {
            norm <= 1.0 -> 1.0
            norm <= 2.0 -> 2.0
            norm <= 5.0 -> 5.0
            else -> 10.0
        }
        val start = ceil(lo / step) * step
        val out = mutableListOf<Double>()
        var v = start
        while (v <= hi + step * 1e-9 && out.size < maxTickCount + 2) {
            out += v
            v += step
        }
        return if (out.isEmpty()) listOf(lo, hi).distinct() else out
    }

    /**
     * 范围净化（PRD R-06）：全部同值、空结果与极端范围必须有有限的默认范围。
     * 单值 v → [v-1, v+1]（v=0 时 [-1,1]）；保持符号语义不翻转。
     */
    fun sanitizeRange(min: Double, max: Double): Pair<Double, Double> {
        val lo: Double
        val hi: Double
        when {
            !min.isFinite() && !max.isFinite() -> {
                lo = -1.0; hi = 1.0
            }
            !min.isFinite() -> {
                hi = max; lo = if (max == 0.0) -1.0 else max - abs(max) * 0.1 - 1.0
            }
            !max.isFinite() -> {
                lo = min; hi = if (min == 0.0) 1.0 else min + abs(min) * 0.1 + 1.0
            }
            else -> {
                lo = min; hi = max
            }
        }
        if (lo == hi) {
            val pad = if (lo == 0.0) 1.0 else abs(lo) * 0.01 + 1e-9
            return (lo - pad) to (hi + pad)
        }
        return lo to hi
    }

    /** 可见数据范围（含 candle 影线），空数据返回 null。 */
    fun rangeOfCandles(candles: List<Candle>): Pair<Double, Double>? {
        var lo = Double.POSITIVE_INFINITY
        var hi = Double.NEGATIVE_INFINITY
        for (c in candles) {
            if (c.low < lo) lo = c.low
            if (c.high > hi) hi = c.high
        }
        return if (lo == Double.POSITIVE_INFINITY) null else sanitizeRange(lo, hi)
    }
}
