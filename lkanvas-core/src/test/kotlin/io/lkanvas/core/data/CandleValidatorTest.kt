package io.lkanvas.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CandleValidatorTest {

    private fun candle(
        ts: Long = 1_000L,
        o: Double = 10.0,
        h: Double = 11.0,
        l: Double = 9.0,
        c: Double = 10.5,
        v: Double? = 100.0,
    ) = Candle(timestamp = ts, open = o, high = h, low = l, close = c, volume = v)

    // ---------- A-01：非法输入定位错误 ----------

    @Test
    fun `重复时间戳返回定位错误`() {
        val bad = listOf(candle(ts = 100), candle(ts = 100))
        val issues = CandleValidator.validateBatch(bad)
        assertEquals(1, issues.size)
        assertEquals(1, issues[0].index)
        assertEquals(CandleInvalidReason.TIMESTAMP_DUPLICATED, issues[0].reason)
    }

    @Test
    fun `逆序时间戳返回定位错误`() {
        val bad = listOf(candle(ts = 200), candle(ts = 150))
        val issues = CandleValidator.validateBatch(bad)
        assertEquals(1, issues.size)
        assertEquals(1, issues[0].index)
        assertEquals(CandleInvalidReason.TIMESTAMP_NOT_INCREASING, issues[0].reason)
    }

    @Test
    fun `衔接既有数据时首根必须大于末根时间戳`() {
        val append = listOf(candle(ts = 100))
        val issues = CandleValidator.validateBatch(append, lastTimestamp = 100)
        assertEquals(CandleInvalidReason.TIMESTAMP_DUPLICATED, issues.single().reason)

        val earlier = listOf(candle(ts = 50))
        assertEquals(
            CandleInvalidReason.TIMESTAMP_NOT_INCREASING,
            CandleValidator.validateBatch(earlier, lastTimestamp = 100).single().reason,
        )
    }

    @Test
    fun `非有限 OHLC 与非法高低顺序都返回定位错误`() {
        val bad = listOf(
            candle(ts = 100, o = Double.NaN),
            candle(ts = 101, h = Double.POSITIVE_INFINITY),
            candle(ts = 102, l = 5.0, h = 10.0, o = 12.0, c = 9.0), // open=12 > high=10 违反
        )
        val issues = CandleValidator.validateBatch(bad)
        assertEquals(3, issues.size)
        assertEquals(0, issues[0].index)
        assertEquals(CandleInvalidReason.OHLC_NOT_FINITE, issues[0].reason)
        assertEquals(CandleInvalidReason.OHLC_NOT_FINITE, issues[1].reason)
        assertEquals(CandleInvalidReason.OHLC_ORDER_VIOLATED, issues[2].reason)
    }

    @Test
    fun `非法 volume 与 turnover 返回定位错误`() {
        val bad = listOf(candle(ts = 100, v = -1.0), candle(ts = 101, v = Double.NaN))
        val issues = CandleValidator.validateBatch(bad)
        assertEquals(2, issues.size)
        assertEquals(CandleInvalidReason.VOLUME_NEGATIVE_OR_NOT_FINITE, issues[0].reason)
        assertEquals(CandleInvalidReason.VOLUME_NEGATIVE_OR_NOT_FINITE, issues[1].reason)
    }

    @Test
    fun `合法批次返回空问题列表`() {
        val ok = listOf(candle(ts = 100), candle(ts = 101, o = 10.5, h = 12.0, l = 10.0, c = 11.5))
        assertTrue(CandleValidator.validateBatch(ok).isEmpty())
    }

    // ---------- A-02：边界形态不崩溃、Double 精度保持 ----------

    @Test
    fun `空列表与单根合法`() {
        assertTrue(CandleValidator.validateBatch(emptyList()).isEmpty())
        assertTrue(CandleValidator.validateBatch(listOf(candle())).isEmpty())
    }

    @Test
    fun `全部同价合法`() {
        val flat = (0 until 10).map { candle(ts = 100L + it, o = 5.0, h = 5.0, l = 5.0, c = 5.0) }
        assertTrue(CandleValidator.validateBatch(flat).isEmpty())
    }

    @Test
    fun `负价格合法`() {
        val neg = listOf(
            candle(ts = 100, o = -10.0, h = -8.0, l = -12.0, c = -9.0),
            candle(ts = 101, o = -9.0, h = -7.0, l = -11.0, c = -8.0),
        )
        assertTrue(CandleValidator.validateBatch(neg).isEmpty())
    }

    @Test
    fun `正负跨零合法`() {
        val cross = listOf(
            candle(ts = 100, o = -1.0, h = 2.0, l = -2.0, c = 1.0),
        )
        assertTrue(CandleValidator.validateBatch(cross).isEmpty())
    }

    @Test
    fun `volume 缺失以 null 表达且合法`() {
        val noVol = listOf(candle(ts = 100, v = null), candle(ts = 101, v = null))
        assertTrue(CandleValidator.validateBatch(noVol).isEmpty())
    }

    @Test
    fun `大价格小波动的相邻报价仍可区分（不先转 Float）`() {
        // F-05：大价格 + 小波动。若实现先转 Float，相邻差 1e-7 会丢失。
        val base = 98_765.4321
        val a = Candle(base.toLong(), base, base + 1.0, base - 1.0, base)
        val b = Candle(base.toLong() + 1, base + 1e-7, base + 1.0, base - 1.0, base + 1e-7)
        assertTrue(CandleValidator.validateBatch(listOf(a, b)).isEmpty())
        assertTrue(b.close > a.close, "Double 精度下相邻报价必须可区分")
        assertEquals(base + 1e-7, b.close)
    }

    @Test
    fun `极小价格合法`() {
        val tiny = listOf(candle(ts = 100, o = 1e-8, h = 2e-8, l = 0.5e-8, c = 1.5e-8))
        assertTrue(CandleValidator.validateBatch(tiny).isEmpty())
    }
}
