package io.lkanvas.core.diagnostics

import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.ChangeKind
import io.lkanvas.core.indicator.IndicatorEngine
import io.lkanvas.core.indicator.builtin.MacdIndicator
import io.lkanvas.core.indicator.builtin.MovingAverageIndicator
import io.lkanvas.core.indicator.builtin.VolumeIndicator
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A-44 基础：诊断开关零开销语义、记录定位；B-01 初测：10k candle 末根更新增量耗时。 */
class ChartDiagnosticsTest {

    // ---------- A-44：开关语义 ----------

    @Test
    fun `默认关闭时不记录且开启后可定位session与revision`() {
        val d = ChartDiagnostics()
        assertTrue(!d.isEnabled())
        d.record(1, 2, "compute", "x")
        assertEquals(0, d.entries().size, "关闭时零记录")

        d.setEnabled(true)
        d.record(7, 9, "compute", "MA took=1.2ms")
        val e = d.entries().single()
        assertEquals(7, e.sessionId)
        assertEquals(9, e.revision)
        assertEquals("compute", e.kind)
        assertTrue(d.summary().contains("计算"))
    }

    @Test
    fun `环形缓冲有界不无限增长`() {
        val d = ChartDiagnostics(capacity = 8).apply { setEnabled(true) }
        repeat(100) { d.record(1, it.toLong(), "k", "d$it") }
        assertEquals(8, d.entries().size)
        assertEquals("d99", d.entries().last().detail, "保留最近")
        assertEquals("d92", d.entries().first().detail)
    }

    @Test
    fun `timeCompute关闭时直通不计时`() {
        val d = ChartDiagnostics()
        val r = d.timeCompute(1, 1, { "x" }) { 42 }
        assertEquals(42, r)
        assertEquals(0, d.computeCount.get())
        d.setEnabled(true)
        d.timeCompute(1, 1, { "y" }) { "ok" }
        assertEquals(1, d.computeCount.get())
        assertTrue(d.lastComputeNanos.get() >= 0)
    }

    // ---------- B-01 初测：10k candle 高频末根更新的增量计算耗时 ----------

    private fun genCandles(n: Int, seed: Long): MutableList<Candle> {
        val rand = Random(seed)
        var p = 100.0
        return (0 until n).map {
            p += (rand.nextDouble() - 0.5)
            Candle(it.toLong(), p, p + 1, p - 1, p, 1000.0 + rand.nextInt(500))
        }.toMutableList()
    }

    @Test
    fun `B01初测_1万根三指标末根更新增量P95在门槛内`() {
        val defs = listOf(
            MovingAverageIndicator() to listOf(5.0, 10.0, 30.0, 60.0),
            VolumeIndicator() to listOf(5.0, 10.0, 20.0),
            MacdIndicator() to listOf(12.0, 26.0, 9.0),
        )
        val n = 10_000
        val candles = genCandles(n, seed = 7)
        // 预热 + 建立增量状态
        val states = defs.map { IndicatorEngine.recompute(it.first, it.second, candles) }.toMutableList()

        val rand = Random(2026)
        val samples = mutableListOf<Long>()
        var price = candles.last().close
        repeat(400) { round ->
            val tickStart = System.nanoTime()
            // 模拟一轮行情：末根替换（B-01 每秒 20 次）
            price += (rand.nextDouble() - 0.5) * 0.3
            candles[n - 1] = Candle(
                candles[n - 1].timestamp,
                candles[n - 1].open,
                maxOf(candles[n - 1].open, price) + 0.1,
                minOf(candles[n - 1].open, price) - 0.1,
                price,
                candles[n - 1].volume,
            )
            for ((i, d) in defs.withIndex()) {
                states[i] = IndicatorEngine.advance(d.first, states[i], candles, ChangeKind.ReplaceLatest)
            }
            samples += System.nanoTime() - tickStart
            // 预热后才开始采样（前 50 轮 JIT）
            if (round == 50) samples.clear()
        }

        samples.sort()
        val p50 = samples[samples.size / 2] / 1e6
        val p95 = samples[(samples.size * 0.95).toInt().coerceAtMost(samples.size - 1)] / 1e6
        val p99 = samples[(samples.size * 0.99).toInt().coerceAtMost(samples.size - 1)] / 1e6
        println(
            "B-01 初测（JVM，10k candle，MA+VOL+MACD 三指标末根替换总耗时）：" +
                "P50=%.3fms P95=%.3fms P99=%.3fms（D1 目标 ≤4ms）".format(p50, p95, p99),
        )
        // PRD 7.3：内置增量计算 P95 ≤ 4ms（D1 目标；JVM 桌面环境为初测口径，
        // 真机冻结测量在 T-34）。放宽到 4ms 断言以捕获性能回退。
        assertTrue(p95 < 4.0, "P95=%.3fms 超出 4ms 初测门槛".format(p95))
    }
}
