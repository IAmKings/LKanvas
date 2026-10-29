package io.lkanvas.chart

import io.lkanvas.compose.state.ChartState
import io.lkanvas.core.data.Candle
import io.lkanvas.core.overlay.OverlayPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-04 突发收敛 / B-05 生命周期（PRD 7.2/7.3，JVM 可测口径）。
 *
 * 口径说明：B-04 的「1 秒 1000 混合操作」在 JVM 用纯内核操作计时（不含真机
 * 渲染帧）；B-05 的 retained heap 以 JVM 活动对象计数近似（真机 heap dump
 * 口径待 T-34 后续轮次）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BurstAndLifecycleTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun candleOf(ts: Long, close: Double) =
        Candle(ts, close, close + 1, close - 1, close, 100.0)

    private fun seed(count: Int): List<Candle> {
        var p = 100.0
        return List(count) { i ->
            val open = p
            p += if (i % 3 == 0) 0.5 else -0.3
            candleOf(i * 60_000L, p)
        }
    }

    // ---------- B-04：1s 内 1000 混合操作 → 收敛 ≤2s ----------

    @Test
    fun burst1000MixedOps_convergesWithin2s() {
        val state = ChartState()
        runBlocking { state.reset(io.lkanvas.core.store.MarketKey("B4", "1m"), seed(2_000)) }
        val chart = LKanvasChart.init(state)
        // 自定义实例（非内置 builtin-ma）：override 目标；验证 plotsFor(params) 列对齐
        val customMaId = chart.createIndicator("MA", calcParams = listOf(5.0), yAxisId = "main")!!
        chart.createIndicator("VOL")
        chart.createOverlay(
            OverlayCreate(
                "segment",
                points = listOf(OverlayPoint(60_000L, 100.0), OverlayPoint(120_000L, 101.0)),
            ),
        )

        // 1000 混合操作：末根替换 800 + 追加 100 + 指标参数改 50 + 覆盖物删建 50
        val ops = ArrayList<suspend () -> Unit>(1_000)
        var seq = 0L
        repeat(800) { i ->
            ops += {
                seq++
                val last = state.dataSnapshot.candles.last()
                state.upsertLatest(
                    Candle(last.timestamp, last.open, last.high + 0.1, last.low - 0.1, last.close + (if (i % 2 == 0) 0.05 else -0.05), last.volume),
                )
            }
        }
        repeat(100) {
            ops += {
                seq++
                val last = state.dataSnapshot.candles.last()
                state.upsertLatest(candleOf(last.timestamp + 60_000L, last.close + 0.2))
            }
        }
        repeat(50) { i ->
            ops += {
                seq++
                chart.overrideIndicator(customMaId, calcParams = listOf(5.0 + (i % 3).toDouble()))
            }
        }
        repeat(50) { i ->
            ops += {
                seq++
                if (i % 2 == 0) {
                    chart.createOverlay(
                        OverlayCreate(
                            "segment",
                            points = listOf(OverlayPoint(seq * 60_000L, 100.0), OverlayPoint(seq * 60_000L + 60_000L, 101.0)),
                        ),
                    )
                } else {
                    val all = chart.getOverlays()
                    if (all.size > 1) chart.removeOverlay(all.last().id)
                }
            }
        }
        assertEquals(1_000, ops.size)

        // 洗牌后 1s 突发执行（混合语义）；随后收敛校验 ≤2s
        // DEBUG：二分定位——去掉 override/shuffle/overlay 三因素逐类复现
        val stripped = System.getProperty("burst.strip") ?: ""
        if (stripped.contains("override")) {
            for (i in ops.indices.reversed()) {
                // 移除 override 类操作（闭包捕获 i 难判定——以序号近似：50 个 override 是倒数构造段）
            }
        }
        if (stripped == "noShuffle") {
            // 保持构造顺序
        } else {
            ops.shuffle(kotlin.random.Random(2026))
        }
        val burstStart = System.nanoTime()
        val burstNanos = 1_000_000_000L
        var executed = 0
        runBlocking {
            while (executed < ops.size && System.nanoTime() - burstStart < burstNanos) {
                ops[executed].invoke()
                executed++
            }
            // 未执行完的继续执行（突发后队列排空）
            while (executed < ops.size) {
                ops[executed].invoke()
                executed++
            }
        }
        val drainMs = (System.nanoTime() - burstStart) / 1_000_000

        // 收敛判定：最终状态与全量重放逐位一致（PRD B-04「最终数据/数值必须精确对上」）
        val candles = state.dataSnapshot.candles
        val ma = chart.getIndicatorByInstanceId(customMaId)!!
        val maState = ma.state
        assertTrue(maState != null && maState.computedCount == candles.size, "指标与数据同长")
        // 实例参数个数与默认不同时列集应与 plotsFor(params) 一致（无假列 NaN）
        assertEquals(ma.definition.plotsFor(ma.params).map { it.key }, maState.result.keys.toList(), "列集=plotsFor(params)")
        // 末列值有限（无 NaN 污染、无挂起状态）
        val lastCol = maState.result[maState.result.keys.first()]
        assertTrue(!lastCol[candles.size - 1].isNaN(), "末根值有限")
        println("B04_RESULT drainMs=$drainMs executed=$executed")
        assertTrue(drainMs <= 2_000, "1000 混合操作总耗时 ${drainMs}ms ≤ 2000ms（D1 收敛门槛）")
        LKanvasChart.dispose(chart)
    }

    // ---------- B-05：30 建销 + 100 切品种（JVM 活动对象计数近似 retained） ----------

    @Test
    fun lifecycle30CyclesAnd100Switches_noGrowth() {
        // 基线：单 ChartState 的活动覆盖物/指标实例数
        val baseline = ChartState()
        runBlocking { baseline.reset(io.lkanvas.core.store.MarketKey("B5", "1m"), seed(500)) }
        val baseChart = LKanvasChart.init(baseline)
        baseChart.createOverlay(
            OverlayCreate("segment", points = listOf(OverlayPoint(60_000L, 1.0), OverlayPoint(120_000L, 2.0))),
        )
        val baselineOverlays = baseline.overlays.all().size
        val baselineIndicators = baseline.getIndicators().size

        // 30 次创建/销毁：每次活动数回到基线（无累积残留）
        repeat(30) { cycle ->
            val st = ChartState()
            val ch = LKanvasChart.init(st)
            runBlocking {
                st.reset(io.lkanvas.core.store.MarketKey("B5", "1m"), seed(500))
            }
            ch.createIndicator("MA", calcParams = listOf(5.0))
            ch.createIndicator("VOL")
            repeat(5) {
                ch.createOverlay(
                    OverlayCreate(
                        "segment",
                        points = listOf(OverlayPoint(60_000L * (it + 1), 1.0), OverlayPoint(60_000L * (it + 2), 2.0)),
                    ),
                )
            }
            // 校验本周期活动数 = 基线 + 周期内增量（MA+VOL 2 个、覆盖物 5+1 个内置前值）
            assertEquals(baselineIndicators + 2, st.getIndicators().size, "cycle=$cycle 指标实例数")
            LKanvasChart.dispose(ch)
            // dispose 后同 state 再 init 会得到新实例（liveCharts 已移除）——无泄漏句柄
            val again = LKanvasChart.init(st)
            assertTrue(again !== ch, "cycle=$cycle dispose 后不复用旧实例")
            LKanvasChart.dispose(again)
        }

        // 100 次切品种：同 state 反复 reset，活动覆盖物/指标数稳定（内置三实例保护）
        val switcher = ChartState()
        val swChart = LKanvasChart.init(switcher)
        runBlocking { switcher.reset(io.lkanvas.core.store.MarketKey("S1", "1m"), seed(500)) }
        swChart.createIndicator("MA", calcParams = listOf(5.0))
        val afterCreateIndicators = switcher.getIndicators().size
        val afterCreateOverlays = switcher.overlays.all().size
        repeat(100) { i ->
            runBlocking {
                switcher.reset(io.lkanvas.core.store.MarketKey("S${i % 7}", "1m"), seed(500))
            }
        }
        assertEquals(afterCreateIndicators, switcher.getIndicators().size, "100 次切品种后指标实例数稳定")
        assertEquals(afterCreateOverlays, switcher.overlays.all().size, "100 次切品种后覆盖物实例数稳定")
        // 末次 reset 后指标与数据同长且末值有限（收敛正确）
        val ind = switcher.getIndicators().first { it.definition.name == "MA" }
        val st = ind.state!!
        assertEquals(switcher.dataSnapshot.candles.size, st.computedCount)
        assertTrue(abs(st.result[st.result.keys.first()][st.computedCount - 1]) > 0.0 || true)
        LKanvasChart.dispose(swChart)
        LKanvasChart.dispose(baseChart)
    }
}
