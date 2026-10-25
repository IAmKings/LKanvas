package io.lkanvas.core.indicator.builtin

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IndicatorEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** T-12 均值/窗口指标组（I-04~I-12）：oracle 对照 + 手算样例 + 增量一致性。 */
class WindowIndicatorsTest {

    // ---------- A-22：oracle 逐位对照 ----------

    @Test
    fun `EMA与上游逐位一致`() = IndicatorTestSupport.assertAgainstFixture("EMA", ExponentialMovingAverageIndicator())

    @Test
    fun `SMA与上游逐位一致`() = IndicatorTestSupport.assertAgainstFixture("SMA", SimpleMovingAverageIndicator())

    @Test
    fun `BOLL与上游逐位一致`() = IndicatorTestSupport.assertAgainstFixture("BOLL", BollingerBandsIndicator())

    @Test
    fun `BBI与上游逐位一致`() = IndicatorTestSupport.assertAgainstFixture("BBI", BullAndBearIndexIndicator())

    @Test
    fun `BIAS与上游逐位一致`() = IndicatorTestSupport.assertAgainstFixture("BIAS", BiasIndicator())

    @Test
    fun `AO与上游逐位一致`() = IndicatorTestSupport.assertAgainstFixture("AO", AwesomeOscillatorIndicator())

    @Test
    fun `MTM与上游逐位一致`() = IndicatorTestSupport.assertAgainstFixture("MTM", MomentumIndicator())

    @Test
    fun `ROC与上游逐位一致`() = IndicatorTestSupport.assertAgainstFixture("ROC", RateOfChangeIndicator())

    @Test
    fun `PSY与上游逐位一致`() = IndicatorTestSupport.assertAgainstFixture("PSY", PsychologicalLineIndicator())

    // ---------- 手算独立样例（第一层验证） ----------

    private fun c(ts: Long, close: Double, high: Double = close + 1, low: Double = close - 1) =
        Candle(ts, close, high, low, close, 100.0)

    @Test
    fun `EMA种子为前p根SMA且首值位置正确`() {
        // close 1..10，EMA6：种子 = mean(1..6) = 3.5 @ i=5
        val candles = (0 until 10).map { c(it.toLong(), (it + 1).toDouble()) }
        val state = IndicatorEngine.recompute(ExponentialMovingAverageIndicator(), listOf(6.0), candles)
        val ema = state.result["ema1"]
        assertTrue(ema[4].isNaN())
        assertEquals(3.5, ema[5], 1e-12)
        // i=6: (2*7 + 5*3.5)/7
        assertEquals((2 * 7 + 5 * 3.5) / 7, ema[6], 1e-12)
    }

    @Test
    fun `SMA递推口径手算（N=5,w=2）`() {
        val candles = (0 until 8).map { c(it.toLong(), (it + 1).toDouble()) }
        val state = IndicatorEngine.recompute(SimpleMovingAverageIndicator(), listOf(5.0, 2.0), candles)
        val sma = state.result["sma"]
        assertTrue(sma[3].isNaN())
        assertEquals(3.0, sma[4], 1e-12, "种子 = mean(1..5)")
        // i=5: (6*2 + 3*(5-2+1))/(5+1) = (12+12)/6 = 4
        assertEquals((6 * 2 + 3 * (5 - 2 + 1)) / 6.0, sma[5], 1e-12)
    }

    @Test
    fun `BOLL中轨与标准差手算`() {
        // 全部 close=10 → MID=10，MD=0，UP=DN=10（平盘）
        val flat = (0 until 25).map { c(it.toLong(), 10.0) }
        val state = IndicatorEngine.recompute(BollingerBandsIndicator(), listOf(20.0, 2.0), flat)
        val mid = state.result["mid"]
        val up = state.result["up"]
        val dn = state.result["dn"]
        assertEquals(10.0, mid[19], 1e-12)
        assertEquals(10.0, up[19], 1e-12, "平盘 MD=0")
        assertEquals(10.0, dn[19], 1e-12)
        // 手算方差样例：close = 1..20，MID=10.5，MD=sqrt(Σ(i-10.5)²/20)=sqrt(665/20)
        val seq = (0 until 20).map { c(it.toLong(), (it + 1).toDouble()) }
        val s2 = IndicatorEngine.recompute(BollingerBandsIndicator(), listOf(20.0, 2.0), seq)
        val expectMd = kotlin.math.sqrt(665.0 / 20)
        assertEquals(10.5, s2.result["mid"][19], 1e-12)
        assertEquals(10.5 + 2 * expectMd, s2.result["up"][19], 1e-12)
        assertEquals(10.5 - 2 * expectMd, s2.result["dn"][19], 1e-12)
    }

    @Test
    fun `BIAS均值为零输出Missing而非Infinity`() {
        // close 全 0（合法：同价 0）：mean=0 → Missing
        val zeros = (0 until 10).map { c(it.toLong(), 0.0, 0.0, 0.0) }
        val state = IndicatorEngine.recompute(BiasIndicator(), listOf(5.0), zeros)
        assertTrue(state.result["bias1"][4].isNaN(), "除零无定义 → Missing（R-08）")
        // 正常样例：close 1..10，BIAS5@4 = (5-3)/3*100
        val seq = (0 until 10).map { c(it.toLong(), (it + 1).toDouble()) }
        val s2 = IndicatorEngine.recompute(BiasIndicator(), listOf(5.0), seq)
        assertEquals((5 - 3) / 3.0 * 100, s2.result["bias1"][4], 1e-12)
    }

    @Test
    fun `ROC回看值为零按上游约定取零`() {
        // 前 3 根 close=0（回看为零），i=3 时 REF=close[0]=0 → roc=0（上游显式代码）
        val candles = listOf(c(0L, 0.0, 0.0, 0.0), c(1L, 0.0, 0.0, 0.0), c(2L, 0.0, 0.0, 0.0), c(3L, 5.0))
        val state = IndicatorEngine.recompute(RateOfChangeIndicator(), listOf(3.0, 2.0), candles)
        assertEquals(0.0, state.result["roc"][3], 1e-12)
    }

    @Test
    fun `PSY上涨天数占比手算`() {
        // close: 1,2,3,1,2,3,1,2,3,1,2,3
        // upFlags: 0,1,1,0,1,1,0,1,1,0,1,1
        val closes = listOf(1.0, 2.0, 3.0, 1.0, 2.0, 3.0, 1.0, 2.0, 3.0, 1.0, 2.0, 3.0)
        val candles = closes.mapIndexed { i, v -> c(i.toLong(), v) }
        val state = IndicatorEngine.recompute(PsychologicalLineIndicator(), listOf(6.0, 3.0), candles)
        val psy = state.result["psy"]
        // 窗口 [0..5]：0,1,1,0,1,1 → 4/6
        assertEquals(4.0 / 6 * 100, psy[5], 1e-12)
        // 窗口 [6..11]：0,1,1,0,1,1 → 4/6
        assertEquals(4.0 / 6 * 100, psy[11], 1e-12)
        // MAPSY 首个有效位 i=p+q-2=7：mean(psy[5],psy[6],psy[7])；各窗口均 4 涨
        assertEquals(4.0 / 6 * 100, state.result["maPsy"][7], 1e-12)
        assertEquals(4.0 / 6 * 100, state.result["maPsy"][8], 1e-12)
    }

    // ---------- A-23：增量一致性 ----------

    @Test
    fun `EMA增量与全量一致`() =
        IndicatorTestSupport.randomReplaceAppendSequence(ExponentialMovingAverageIndicator(), listOf(6.0, 12.0, 20.0), 1_500, seed = 11)

    @Test
    fun `SMA增量与全量一致`() =
        IndicatorTestSupport.randomReplaceAppendSequence(SimpleMovingAverageIndicator(), listOf(12.0, 2.0), 1_500, seed = 12)

    @Test
    fun `BOLL增量与全量一致`() =
        IndicatorTestSupport.randomReplaceAppendSequence(BollingerBandsIndicator(), listOf(20.0, 2.0), 1_500, seed = 13)

    @Test
    fun `BBI增量与全量一致`() =
        IndicatorTestSupport.randomReplaceAppendSequence(BullAndBearIndexIndicator(), listOf(3.0, 6.0, 12.0, 24.0), 1_500, seed = 14)

    @Test
    fun `BIAS增量与全量一致`() =
        IndicatorTestSupport.randomReplaceAppendSequence(BiasIndicator(), listOf(6.0, 12.0, 24.0), 1_500, seed = 15)

    @Test
    fun `AO增量与全量一致`() =
        IndicatorTestSupport.randomReplaceAppendSequence(AwesomeOscillatorIndicator(), listOf(5.0, 34.0), 1_500, seed = 16)

    @Test
    fun `MTM增量与全量一致`() =
        IndicatorTestSupport.randomReplaceAppendSequence(MomentumIndicator(), listOf(12.0, 6.0), 1_500, seed = 17)

    @Test
    fun `ROC增量与全量一致`() =
        IndicatorTestSupport.randomReplaceAppendSequence(RateOfChangeIndicator(), listOf(12.0, 6.0), 1_500, seed = 18)

    @Test
    fun `PSY增量与全量一致`() =
        IndicatorTestSupport.randomReplaceAppendSequence(PsychologicalLineIndicator(), listOf(12.0, 6.0), 1_500, seed = 19)
}
