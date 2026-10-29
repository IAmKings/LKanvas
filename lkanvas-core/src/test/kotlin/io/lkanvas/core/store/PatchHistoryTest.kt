package io.lkanvas.core.store

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.ChangeKind
import io.lkanvas.core.indicator.IndicatorEngine
import io.lkanvas.core.extension.indicator.MacdIndicator
import io.lkanvas.core.extension.indicator.MovingAverageIndicator
import io.lkanvas.core.extension.indicator.StopAndReverseIndicator
import io.lkanvas.core.extension.indicator.TrixIndicator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A-06：PatchHistory 修改最早/中间/末根后，受影响指标与从头全量计算一致。 */
class PatchHistoryTest {

    private fun candle(ts: Long, close: Double) =
        Candle(ts, close, close + 1, close - 1, close, 100.0)

    private val key = MarketKey("TEST", "1m")

    private fun randomCandles(n: Int, seed: Long): MutableList<Candle> {
        val rand = kotlin.random.Random(seed)
        var p = 100.0
        return (0 until n).map {
            p += (rand.nextDouble() - 0.5) * 2
            candle(it.toLong(), p)
        }.toMutableList()
    }

    @Test
    fun `修改最早中间末根后所有指标与全量重算一致`() {
        val store = ChartDataStore()
        val candles = randomCandles(120, seed = 7)
        store.reset(key, candles)

        val defs = mapOf(
            "MA" to (MovingAverageIndicator() to listOf(5.0, 10.0, 30.0, 60.0)),
            "MACD" to (MacdIndicator() to listOf(12.0, 26.0, 9.0)),
            "SAR" to (StopAndReverseIndicator() to listOf(2.0, 2.0, 20.0)),
            "TRIX" to (TrixIndicator() to listOf(12.0, 9.0)),
        )
        // 基线：当前数据全量
        val baselines = defs.mapValues { (_, d) ->
            IndicatorEngine.recompute(d.first, d.second, store.candlesView())
        }

        // 纠正最早(0)/中间(60)/末根(119)
        val patches = listOf(
            candle(0, 50.0),
            candle(60, 130.0),
            candle(119, 88.0),
        )
        val r = store.patchHistory(patches)
        assertTrue(r is ChangeResult.Accepted)
        assertEquals(store.candleCount, 120, "纠正不改变数量")

        // patch 语义：直接推进（ChangeKind.Patch → 引擎回退全量）
        for ((name, d) in defs) {
            val prev = baselines.getValue(name)
            val next = IndicatorEngine.advance(d.first, prev, store.candlesView(), ChangeKind.Patch)
            // 与「从头全量」逐位一致（A-06 核心）
            val fresh = IndicatorEngine.recompute(d.first, d.second, store.candlesView())
            for (k in fresh.result.keys) {
                val a = next.result[k]
                val b = fresh.result[k]
                for (i in a.indices) {
                    assertTrue(
                        (a[i].isNaN() && b[i].isNaN()) || a[i] == b[i],
                        "$name 列 $k 第 $i 位 patch 推进 ${a[i]} vs 全量 ${b[i]}",
                    )
                }
            }
        }
    }

    @Test
    fun `不存在的時間戳被拒绝且数据原子不变`() {
        val store = ChartDataStore()
        store.reset(key, randomCandles(50, seed = 1))
        val before = store.snapshot()
        val r = store.patchHistory(listOf(candle(0, 10.0), candle(999, 5.0)))
        assertTrue(r is ChangeResult.Rejected && r.reason == RejectReason.TIMESTAMP_MISMATCH)
        assertEquals(before, store.snapshot(), "部分失败整批不变")
    }

    @Test
    fun `非法OHLC的patch被拒绝`() {
        val store = ChartDataStore()
        store.reset(key, randomCandles(50, seed = 2))
        val bad = Candle(10, 10.0, 9.0, 11.0, 10.5) // high < low 违反
        val r = store.patchHistory(listOf(bad))
        assertTrue(r is ChangeResult.Rejected && r.reason == RejectReason.VALIDATION_FAILED)
        assertEquals(50, store.candleCount)
    }

    @Test
    fun `patch推进revision且不改变时间戳序列`() {
        val store = ChartDataStore()
        val candles = randomCandles(30, seed = 3)
        store.reset(key, candles)
        val rev = store.revision
        val ts = store.candlesView().map { it.timestamp }
        store.patchHistory(listOf(candle(15, 77.0)))
        assertEquals(rev + 1, store.revision)
        assertEquals(ts, store.candlesView().map { it.timestamp })
        assertEquals(77.0, store.candlesView()[15].close)
    }
}
