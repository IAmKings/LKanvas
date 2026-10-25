package io.lkanvas.core.indicator.builtin

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IndicatorEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** T-13 振荡/方向/量指标组（I-13~I-21）：oracle 对照 + 手算样例 + 增量一致性。 */
class OscillatorIndicatorsTest {

    // ---------- A-22：oracle 逐位对照 ----------

    @Test
    fun `KDJ与上游逐位一致`() = IndicatorTestSupport.assertAgainstFixture("KDJ", StochIndicator())

    @Test
    fun `RSI与上游逐位一致`() = IndicatorTestSupport.assertAgainstFixture("RSI", RelativeStrengthIndexIndicator())

    @Test
    fun `WR与上游逐位一致`() = IndicatorTestSupport.assertAgainstFixture("WR", WilliamsRIndicator())

    @Test
    fun `CCI与上游逐位一致`() = IndicatorTestSupport.assertAgainstFixture("CCI", CommodityChannelIndexIndicator())

    @Test
    fun `DMI与上游逐位一致`() = IndicatorTestSupport.assertAgainstFixture("DMI", DirectionalMovementIndexIndicator())

    @Test
    fun `BRAR与上游逐位一致`() = IndicatorTestSupport.assertAgainstFixture("BRAR", BrarIndicator())

    @Test
    fun `CR与上游逐位一致`() = IndicatorTestSupport.assertAgainstFixture("CR", CurrentRatioIndicator())

    @Test
    fun `VR与上游逐位一致`() = IndicatorTestSupport.assertAgainstFixture("VR", VolumeRatioIndicator())

    @Test
    fun `EMV与上游逐位一致`() = IndicatorTestSupport.assertAgainstFixture("EMV", EaseOfMovementValueIndicator())

    // ---------- 手算/边界样例 ----------

    private fun c(ts: Long, o: Double, h: Double, l: Double, cl: Double, v: Double? = 100.0) =
        Candle(ts, o, h, l, cl, v)

    @Test
    fun `KDJ窗口高低相同时RSV分母取一且种子为五十`() {
        // 单调上涨：首行 K = (2*50 + RSV)/3
        val candles = (0 until 10).map {
            c(it.toLong(), it.toDouble(), it + 1.0, it - 1.0, it + 0.5)
        }
        val state = IndicatorEngine.recompute(StochIndicator(), listOf(9.0, 3.0, 3.0), candles)
        val k = state.result["k"]
        // 窗口 [0..8]: hn=max(h)=9, ln=min(l)=-1, close(8)=8.5 → rsv = 9.5/10*100 = 95
        val rsv = (8.5 - (-1.0)) / (9.0 - (-1.0)) * 100
        assertEquals(95.0, rsv, 1e-12)
        assertEquals((2 * 50 + rsv) / 3, k[8], 1e-12)
        val d = state.result["d"]
        assertEquals((2 * 50 + k[8]) / 3, d[8], 1e-12)
        assertEquals(3 * k[8] - 2 * d[8], state.result["j"][8], 1e-12)
        assertTrue(k[7].isNaN(), "预热期 Missing")

        // 全部同价：高低差=0 → rsv 分母按 1
        val flat = (0 until 10).map { c(it.toLong(), 5.0, 5.0, 5.0, 5.0) }
        val s2 = IndicatorEngine.recompute(StochIndicator(), listOf(9.0, 3.0, 3.0), flat)
        assertEquals((2 * 50 + 0.0) / 3, s2.result["k"][8], 1e-12)
    }

    @Test
    fun `RSI连涨为100连跌为0平盘为0`() {
        // 连涨：avgLoss=0 → 100
        val up = (0 until 30).map { c(it.toLong(), it.toDouble(), it + 1.0, it - 1.0, it + 0.5) }
        val sUp = IndicatorEngine.recompute(RelativeStrengthIndexIndicator(), listOf(6.0), up)
        assertEquals(100.0, sUp.result["rsi1"][6], 1e-12)

        // 连跌：avgGain=0 → 0
        val down = (0 until 30).map { c(it.toLong(), 30 - it.toDouble(), 31.0 - it, 29.0 - it, 29.5 - it) }
        val sDown = IndicatorEngine.recompute(RelativeStrengthIndexIndicator(), listOf(6.0), down)
        assertEquals(0.0, sDown.result["rsi1"][6], 1e-12)

        // 平盘：gain=loss=0 → avgLoss=0 → 100（上游口径）
        val flat = (0 until 30).map { c(it.toLong(), 5.0, 5.0, 5.0, 5.0) }
        val sFlat = IndicatorEngine.recompute(RelativeStrengthIndexIndicator(), listOf(6.0), flat)
        assertEquals(100.0, sFlat.result["rsi1"][6], 1e-12)
    }

    @Test
    fun `WR高低相同取零且取值域正确`() {
        val candles = (0 until 20).map {
            c(it.toLong(), it.toDouble(), it + 2.0, it - 1.0, it + 1.0)
        }
        val state = IndicatorEngine.recompute(WilliamsRIndicator(), listOf(6.0), candles)
        val wr = state.result["wr1"]
        assertTrue(wr[4].isNaN())
        // 窗口 [4..9]: hn=11, ln=3, close=10 → (10-11)/8*100 = -12.5
        assertEquals(-12.5, wr[9], 1e-12)

        val flat = (0 until 10).map { c(it.toLong(), 5.0, 5.0, 5.0, 5.0) }
        assertEquals(0.0, IndicatorEngine.recompute(WilliamsRIndicator(), listOf(6.0), flat).result["wr1"][9], 1e-12)
    }

    @Test
    fun `VR缺失volume窗口输出Missing（D-03）而有效零量正常`() {
        // 第 5 根 volume 缺失：窗口 [i-25, i] 含第 5 根（i ∈ [25,30]）→ Missing
        val candles = (0 until 40).map {
            if (it == 5) c(it.toLong(), 5.0, 6.0, 4.0, 5.5, v = null)
            else c(it.toLong(), it.toDouble(), it + 1.0, it - 1.0, it + 0.3, v = 100.0)
        }
        val state = IndicatorEngine.recompute(VolumeRatioIndicator(), listOf(26.0, 6.0), candles)
        val vr = state.result["vr"]
        assertTrue(vr[25].isNaN(), "首行窗口 [0..25] 含缺失 → Missing")
        assertTrue(vr[30].isNaN(), "窗口 [5..30] 仍含缺失 → Missing")
        assertTrue(vr[31].isFinite(), "缺失滑出窗口后恢复（窗口 [6..31]）")

        // volume=0（有效零量）：按上游公式参与（不 Missing）
        val zeroVol = (0 until 30).map { c(it.toLong(), it.toDouble(), it + 1.0, it - 1.0, it + 0.3, v = 0.0) }
        val s2 = IndicatorEngine.recompute(VolumeRatioIndicator(), listOf(26.0, 6.0), zeroVol)
        assertEquals(0.0, s2.result["vr"][25], 1e-12, "全零量：dvs+halfPvs=0 → 0")
    }

    @Test
    fun `EMV缺失volume输出Missing有效零量为零`() {
        val candles = (0 until 30).map {
            if (it == 3) c(it.toLong(), 5.0, 6.0, 4.0, 5.5, v = null)
            else c(it.toLong(), it.toDouble(), it + 1.0, it - 1.0, it + 0.3, v = 1000.0)
        }
        val state = IndicatorEngine.recompute(EaseOfMovementValueIndicator(), listOf(14.0, 9.0), candles)
        val emv = state.result["emv"]
        assertTrue(emv[3].isNaN(), "缺失根 Missing")
        assertTrue(emv[4].isFinite(), "其余正常")

        // volume=0 → emv=0（上游显式）
        val zeroVol = (0 until 5).map { c(it.toLong(), it.toDouble(), it + 1.0, it - 1.0, it + 0.3, v = 0.0) }
        val s2 = IndicatorEngine.recompute(EaseOfMovementValueIndicator(), listOf(14.0, 9.0), zeroVol)
        assertEquals(0.0, s2.result["emv"][1], 1e-12)
    }

    // ---------- A-23：增量一致性（FULL_ONLY 模板验证回退正确性） ----------

    @Test
    fun `KDJ增量与全量一致`() =
        IndicatorTestSupport.randomReplaceAppendSequence(StochIndicator(), listOf(9.0, 3.0, 3.0), 1_200, seed = 21)

    @Test
    fun `RSI增量与全量一致`() =
        IndicatorTestSupport.randomReplaceAppendSequence(RelativeStrengthIndexIndicator(), listOf(6.0, 12.0, 24.0), 1_200, seed = 22)

    @Test
    fun `WR增量与全量一致`() =
        IndicatorTestSupport.randomReplaceAppendSequence(WilliamsRIndicator(), listOf(6.0, 10.0, 14.0), 1_200, seed = 23)

    @Test
    fun `CCI增量与全量一致`() =
        IndicatorTestSupport.randomReplaceAppendSequence(CommodityChannelIndexIndicator(), listOf(20.0), 1_200, seed = 24)

    @Test
    fun `BRAR增量与全量一致`() =
        IndicatorTestSupport.randomReplaceAppendSequence(BrarIndicator(), listOf(26.0), 1_200, seed = 25)

    @Test
    fun `VR增量与全量一致`() =
        IndicatorTestSupport.randomReplaceAppendSequence(VolumeRatioIndicator(), listOf(26.0, 6.0), 1_200, seed = 26)

    @Test
    fun `EMV增量与全量一致`() =
        IndicatorTestSupport.randomReplaceAppendSequence(EaseOfMovementValueIndicator(), listOf(14.0, 9.0), 1_200, seed = 27)

    @Test
    fun `DMI与CR全量only模板回退全量且结果正确`() {
        // FULL_ONLY：advance 必须回退全量；直接与 recompute 对照
        val rand = kotlin.random.Random(28)
        val candles = ArrayList<Candle>()
        var p = 100.0
        repeat(80) { p += (rand.nextDouble() - 0.5); candles += c(it.toLong(), p, p + 1, p - 1, p, 1000.0) }
        for (def in listOf(DirectionalMovementIndexIndicator(), CurrentRatioIndicator())) {
            var state = IndicatorEngine.recompute(def, def.calcParams, candles)
            repeat(50) {
                val last = candles.last()
                candles[candles.size - 1] = c(last.timestamp, last.close + 0.1, last.close + 1.1, last.close - 0.9, last.close + 0.1, last.volume)
                state = IndicatorEngine.advance(def, state, candles, io.lkanvas.core.indicator.ChangeKind.ReplaceLatest)
            }
            IndicatorTestSupport.assertResultsEqual(
                IndicatorEngine.recompute(def, def.calcParams, candles), state, def.name,
            )
        }
    }
}
