package io.lkanvas.core.indicator.builtin

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IndicatorEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** T-14 递推/累计指标组（I-22~I-27）：oracle 对照 + 手算样例 + 增量一致性。 */
class RecursiveIndicatorsTest {

    // ---------- A-22：oracle 逐位对照 ----------

    @Test
    fun `SAR与上游逐位一致`() = IndicatorTestSupport.assertAgainstFixture("SAR", StopAndReverseIndicator())

    @Test
    fun `TRIX与上游逐位一致`() = IndicatorTestSupport.assertAgainstFixture("TRIX", TrixIndicator())

    @Test
    fun `OBV与上游逐位一致`() = IndicatorTestSupport.assertAgainstFixture("OBV", OnBalanceVolumeIndicator())

    @Test
    fun `PVT与上游逐位一致`() = IndicatorTestSupport.assertAgainstFixture("PVT", PriceAndVolumeTrendIndicator())

    @Test
    fun `AVP与上游逐位一致`() = IndicatorTestSupport.assertAgainstFixture("AVP", AveragePriceIndicator())

    @Test
    fun `DMA与上游逐位一致`() = IndicatorTestSupport.assertAgainstFixture("DMA", DifferentOfMovingAverageIndicator())

    // ---------- 手算/边界样例 ----------

    private fun c(ts: Long, o: Double, h: Double, l: Double, cl: Double, v: Double? = 100.0, t: Double? = null) =
        Candle(ts, o, h, l, cl, v, t)

    @Test
    fun `SAR首根与趋势反转手算`() {
        // 首根（下跌分支，ep=-100）：ep=low, af=start+step, sar=pre+af*(ep-pre)
        val candles = listOf(
            c(0, 10.0, 11.0, 9.0, 10.5, 100.0),
            c(1, 10.5, 11.5, 10.0, 11.0, 100.0),
        )
        val state = IndicatorEngine.recompute(StopAndReverseIndicator(), listOf(2.0, 2.0, 20.0), candles)
        val sar = state.result["sar"]
        // i=0: ep=9, af=0.04, sar = 0 + 0.04*9 = 0.36; sar < high(11) → 反转 sar=ep=9, 涨势
        assertEquals(9.0, sar[0], 1e-12)
        // i=1（涨势）：pre=9, ep=-100 → ep=high(11.5), af=0.04+0.02=0.06
        // sar = 9 + 0.06*(11.5-9) = 9.15; sar > low(10)? 9.15 < 10 → 不反转; lowMin=min(low(0),10)=9 → sar>lowMin? 9.15>9 → sar=9.15... 上游分支：sar>low? no; sar>lowMin? yes → sar=lowMin=9
        assertEquals(9.0, sar[1], 1e-12)
    }

    @Test
    fun `OBV平价不计入且累计方向正确`() {
        val candles = listOf(
            c(0, 10.0, 11.0, 9.0, 10.0, 100.0), // 平（与自身比）→ 0
            c(1, 10.0, 11.0, 9.0, 11.0, 50.0),  // 涨 → +50
            c(2, 11.0, 12.0, 10.0, 11.0, 30.0), // 平 → 0
            c(3, 11.0, 12.0, 10.0, 10.5, 20.0), // 跌 → -20
        )
        val state = IndicatorEngine.recompute(OnBalanceVolumeIndicator(), listOf(3.0), candles)
        val obv = state.result["obv"]
        assertEquals(0.0, obv[0], 1e-12)
        assertEquals(50.0, obv[1], 1e-12)
        assertEquals(50.0, obv[2], 1e-12, "平价不计入")
        assertEquals(30.0, obv[3], 1e-12)
        // maObv 窗口 [1..3] = (0+50+50+30 - obv[0])/3 → 上游口径：Σ obv[1..3]/3
        assertEquals((50.0 + 50.0 + 30.0) / 3, state.result["maObv"][3], 1e-12)
    }

    @Test
    fun `PVT昨收为零首根不计入`() {
        val candles = listOf(
            c(0, 0.0, 1.0, -1.0, 0.0, 100.0),   // prevClose=self=0 → x=0
            c(1, 0.0, 1.0, -1.0, 0.5, 100.0),   // prevClose=0 → x=0（上游显式）
            c(2, 0.5, 1.5, -0.5, 1.0, 200.0),   // x = 0.5/0.5*200 = 200
        )
        val state = IndicatorEngine.recompute(PriceAndVolumeTrendIndicator(), emptyList(), candles)
        val pvt = state.result["pvt"]
        assertEquals(0.0, pvt[0], 1e-12)
        assertEquals(0.0, pvt[1], 1e-12, "昨收为零不计入")
        assertEquals(200.0, pvt[2], 1e-12)
    }

    @Test
    fun `AVP累计零量无定义而有效数据正常`() {
        // 全部 volume/turnover 缺失 → totalVolume=0 → Missing
        val missing = (0 until 5).map { c(it.toLong(), 1.0, 2.0, 0.5, 1.5, v = null, t = null) }
        val s1 = IndicatorEngine.recompute(AveragePriceIndicator(), emptyList(), missing)
        assertTrue(s1.result["avp"][4].isNaN(), "累计零量 Missing")

        // 手算：turnover/volume 均匀 → avp = Σt/Σv
        val candles = listOf(
            c(0, 10.0, 11.0, 9.0, 10.5, v = 100.0, t = 1050.0),
            c(1, 10.5, 11.5, 10.0, 11.0, v = 300.0, t = 3300.0),
        )
        val s2 = IndicatorEngine.recompute(AveragePriceIndicator(), emptyList(), candles)
        assertEquals(10.5, s2.result["avp"][0], 1e-12)
        assertEquals((1050.0 + 3300.0) / 400.0, s2.result["avp"][1], 1e-12)
    }

    @Test
    fun `DMA双均线差手算`() {
        // close 1..60：MA10@49 = mean(41..50)=45.5；MA50@49=mean(1..50)=25.5 → dif=20
        val candles = (0 until 60).map { c(it.toLong(), it + 1.0, it + 2.0, it.toDouble(), (it + 1).toDouble()) }
        val state = IndicatorEngine.recompute(DifferentOfMovingAverageIndicator(), listOf(10.0, 50.0, 10.0), candles)
        val dma = state.result["dma"]
        assertEquals(45.5 - 25.5, dma[49], 1e-12)
        assertTrue(dma[48].isNaN(), "maxPeriod-1 前预热")
        // ama 首个有效位 i=59：mean(dif[50..59])
        var sum = 0.0
        for (i in 50..59) sum += dma[i]
        assertEquals(sum / 10, state.result["ama"][59], 1e-12)
    }

    // ---------- A-23：增量一致性 ----------

    @Test
    fun `SAR增量与全量一致`() =
        IndicatorTestSupport.randomReplaceAppendSequence(StopAndReverseIndicator(), listOf(2.0, 2.0, 20.0), 1_200, seed = 31)

    @Test
    fun `TRIX增量与全量一致`() =
        IndicatorTestSupport.randomReplaceAppendSequence(TrixIndicator(), listOf(12.0, 9.0), 1_200, seed = 32)

    @Test
    fun `OBV增量与全量一致`() =
        IndicatorTestSupport.randomReplaceAppendSequence(OnBalanceVolumeIndicator(), listOf(30.0), 1_200, seed = 33)

    @Test
    fun `PVT增量与全量一致`() =
        IndicatorTestSupport.randomReplaceAppendSequence(PriceAndVolumeTrendIndicator(), emptyList(), 1_200, seed = 34)

    @Test
    fun `AVP增量与全量一致`() =
        IndicatorTestSupport.randomReplaceAppendSequence(AveragePriceIndicator(), emptyList(), 1_200, seed = 35)

    @Test
    fun `DMA增量与全量一致`() =
        IndicatorTestSupport.randomReplaceAppendSequence(DifferentOfMovingAverageIndicator(), listOf(10.0, 50.0, 10.0), 1_200, seed = 36)
}
