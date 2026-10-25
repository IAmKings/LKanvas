package io.lkanvas.core.axis

import io.lkanvas.core.data.Candle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinearYAxisTest {

    // ---------- 值↔像素往返（A-12 的 y 维） ----------

    @Test
    fun `线性轴值与像素往返一致`() {
        val cases = listOf(
            Triple(0.0, 100.0, 10.0 to 200.0), // (min,max,(topY,bottomY))
            Triple(-50.0, 50.0, 0.0 to 300.0),
            Triple(98765.4321, 98765.4322, 20.0 to 480.0),
            Triple(-0.001, 0.001, 0.0 to 100.0),
        )
        for ((min, max, yRange) in cases) {
            for (v in sequenceOf(min, max, (min + max) / 2, min + (max - min) * 0.13)) {
                val y = LinearYAxis.valueToY(v, min, max, yRange.first, yRange.second)
                val back = LinearYAxis.yToValue(y, min, max, yRange.first, yRange.second)
                assertEquals(v, back, 1e-9, "min=$min max=$max v=$v")
            }
        }
    }

    @Test
    fun `高值在上低值在下`() {
        val yHigh = LinearYAxis.valueToY(100.0, 0.0, 100.0, 0.0, 200.0)
        val yLow = LinearYAxis.valueToY(10.0, 0.0, 100.0, 0.0, 200.0)
        assertTrue(yHigh < yLow)
        assertEquals(0.0, yHigh, 1e-9)
        assertEquals(180.0, yLow, 1e-9)
    }

    // ---------- 范围净化（A-17 前置：同值/空/极端范围有限默认） ----------

    @Test
    fun `全部同值产生有限默认范围`() {
        val (lo, hi) = LinearYAxis.sanitizeRange(5.0, 5.0)
        assertTrue(lo < 5.0 && hi > 5.0)
        assertTrue(lo.isFinite() && hi.isFinite())

        val zero = LinearYAxis.sanitizeRange(0.0, 0.0)
        assertTrue(zero.first < 0.0 && zero.second > 0.0)
    }

    @Test
    fun `非有限输入产生有限默认范围`() {
        val nan = LinearYAxis.sanitizeRange(Double.NaN, Double.NaN)
        assertTrue(nan.first.isFinite() && nan.second.isFinite())
        val inf = LinearYAxis.sanitizeRange(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)
        assertTrue(inf.first.isFinite() && inf.second.isFinite())
    }

    @Test
    fun `负价格范围合法且方向不翻转`() {
        val (lo, hi) = LinearYAxis.sanitizeRange(-20.0, -10.0)
        assertTrue(lo < hi)
        assertEquals(-20.0, lo)
        assertEquals(-10.0, hi)
    }

    // ---------- 刻度 ----------

    @Test
    fun `刻度单调递增且在范围内`() {
        val ticks = LinearYAxis.ticks(0.0, 100.0, maxTickCount = 6)
        assertTrue(ticks.size >= 2 && ticks.size <= 8)
        assertTrue(ticks.zipWithNext().all { (a, b) -> b > a }, "刻度必须单调：$ticks")
        assertTrue(ticks.first() >= 0.0 - 1e-9 && ticks.last() <= 100.0 + 1e-9)
    }

    @Test
    fun `刻度采用一二五步长`() {
        val t1 = LinearYAxis.ticks(0.0, 10.0, 6)
        assertTrue(t1.size == 6)
        listOf(0.0, 2.0, 4.0, 6.0, 8.0, 10.0).forEachIndexed { i, expect ->
            assertEquals(expect, t1[i], 1e-9)
        }
        val t2 = LinearYAxis.ticks(0.0, 1.0, 6)
        listOf(0.0, 0.2, 0.4, 0.6, 0.8, 1.0).forEachIndexed { i, expect ->
            assertEquals(expect, t2[i], 1e-9)
        }
        val t3 = LinearYAxis.ticks(0.0, 50.0, 6)
        listOf(0.0, 10.0, 20.0, 30.0, 40.0, 50.0).forEachIndexed { i, expect ->
            assertEquals(expect, t3[i], 1e-9)
        }
    }

    @Test
    fun `同值范围也能产出至少两个刻度且不无限循环`() {
        val ticks = LinearYAxis.sanitizeRange(7.0, 7.0).let { LinearYAxis.ticks(it.first, it.second, 5) }
        assertTrue(ticks.size >= 2)
        assertTrue(ticks.all { it.isFinite() })
    }

    // ---------- candle 范围 ----------

    @Test
    fun `可见candle高低范围含净化`() {
        val candles = listOf(
            Candle(0, 10.0, 12.0, 9.0, 11.0),
            Candle(1, 11.0, 13.0, 10.5, 12.5),
        )
        val (lo, hi) = LinearYAxis.rangeOfCandles(candles)!!
        assertEquals(9.0, lo)
        assertEquals(13.0, hi)
        assertEquals(null, LinearYAxis.rangeOfCandles(emptyList()))
    }
}
