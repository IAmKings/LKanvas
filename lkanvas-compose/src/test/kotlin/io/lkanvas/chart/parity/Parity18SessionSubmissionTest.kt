package io.lkanvas.chart.parity

import io.lkanvas.chart.DataLoadMore
import io.lkanvas.chart.DataLoadType
import io.lkanvas.chart.GetBarsParams
import io.lkanvas.chart.LKanvasChart
import io.lkanvas.chart.Period
import io.lkanvas.chart.SubscribeBarParams
import io.lkanvas.chart.SymbolInfo
import io.lkanvas.core.data.Candle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * parity-18：失败会话**提交归属**差分（第七轮审计 R7-01 验收）。
 *
 * R7-01（P1）：失败 INIT 前已入队的实时推送仍串写旧会话——A 就绪（close=100）
 * → 切 B → B INIT 回调返回非法 OHLC → **调度器执行前**立即经刚建立的 B 订阅
 * 推 close=999 → 排空后门面品种 B、快照 key 仍 A，但 A 行情被污染为
 * `[100,999]`。修复：拒绝水位（lastRejectedEpoch）——Reset 拒绝点同步抬升，
 * 同一串行队列中排在其后的 Update 在引擎提交前被拒。
 *
 * 基准保持：合法 INIT 同步首推（Q01-b）、旧失败不退订新 C（M01/parity-13）、
 * ABA 旧回调门禁（Q01-a）。
 */
class Parity18SessionSubmissionTest {

    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun drain() {
        mainDispatcher.scheduler.apply { repeat(400) { advanceUntilIdle() } }
    }

    private fun candle(ts: Long, close: Double) = Candle(ts, close, close + 1, close - 1, close, 100.0)

    /** 非法 OHLC：high < open（CandleValidator 规则 2 拒绝）。 */
    private fun invalidCandle(ts: Long) = Candle(ts, open = 200.0, high = 190.0, low = 210.0, close = 200.0, volume = 10.0)

    private class ScriptedLoader : io.lkanvas.chart.DataLoader {
        val requests = mutableListOf<DataLoadType>()
        val subscribes = mutableListOf<SubscribeBarParams>()
        val unsubscribes = mutableListOf<io.lkanvas.chart.UnsubscribeBarParams>()
        private val pendingInits = ArrayDeque<(List<Candle>, DataLoadMore?) -> Unit>()
        val subscribeCallbacks = mutableListOf<(Candle) -> Unit>()

        override fun getBars(params: GetBarsParams) {
            requests += params.type
            if (params.type == DataLoadType.INIT) pendingInits.addLast(params.callback)
        }

        override fun subscribeBar(params: SubscribeBarParams) {
            subscribes += params
            subscribeCallbacks += params.callback
        }

        override fun unsubscribeBar(params: io.lkanvas.chart.UnsubscribeBarParams) {
            unsubscribes += params
        }

        fun respondInit(data: List<Candle>, more: DataLoadMore?) {
            pendingInits.removeFirst()(data, more)
        }
    }

    private fun newChart(loader: ScriptedLoader): LKanvasChart {
        val chart = LKanvasChart.init()
        chart.setSymbol(SymbolInfo("A"))
        chart.setPeriod(Period.ofMinutes(1))
        chart.setDataLoader(loader)
        chart.state.onLayout(80f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        return chart
    }

    // ---------- R7-01 主复现 ----------

    @Test
    fun `失败INIT已入队推送不串写旧会话（R7-01）`() {
        val loader = ScriptedLoader()
        val chart = newChart(loader)
        drain()
        // A 就绪：单根 close=100
        loader.respondInit(listOf(candle(0L, 100.0)), DataLoadMore.NONE)
        drain()
        assertEquals(listOf(100.0), chart.getDataList().map { it.close })
        // 切 B；B INIT 回调返回非法 OHLC——受理即建订阅（epoch=2）、Reset 入队
        chart.setSymbol(SymbolInfo("B"))
        drain()
        loader.respondInit(listOf(invalidCandle(2_000L)), DataLoadMore.NONE)
        // **排空前**立即经 B 订阅推送 close=999（审计复现的交错顺序）
        loader.subscribeCallbacks.last()(candle(3_000L, 999.0))
        drain()
        assertEquals("B", chart.getSymbol()?.ticker, "门面品种=B（配置已切换）")
        assertEquals("A", chart.state.dataSnapshot.marketKey?.symbol, "快照 key 仍为 A（Reset 被拒）")
        // 修复断言：A 行情保持 [100]（修复前 [100,999]——已入队 UpsertLatest
        // 在 Reset 拒绝后写入旧会话）
        assertEquals(listOf(100.0), chart.getDataList().map { it.close },
            "失败会话已入队推送被拒水位拦截（R7-01 复现收口）")
        // 失败 B 的清理仍发生（本会话失败——M01 双门内）
        assertTrue(loader.unsubscribes.any { it.symbol.ticker == "B" }, "失败会话仍退订")
        // 后续合法重试恢复（拒绝路径后宿主重试）
        chart.resetData()
        drain()
        loader.respondInit(listOf(candle(5_000L, 300.0)), DataLoadMore.NONE)
        drain()
        assertEquals(listOf(300.0), chart.getDataList().map { it.close }, "合法重试恢复")
        loader.subscribeCallbacks.last()(candle(6_000L, 301.0))
        drain()
        assertEquals(listOf(300.0, 301.0), chart.getDataList().map { it.close }, "重试后推送正常")
        LKanvasChart.dispose(chart)
    }

    // ---------- 基准保持：合法 INIT 同步首推（失败水位不得误伤） ----------

    @Test
    fun `合法INIT同步首推保持且先前失败不误伤新会话（R7-01基准）`() {
        val loader = ScriptedLoader()
        val chart = newChart(loader)
        drain()
        // 第一段 A 失败（epoch=1 → 水位=1）
        loader.respondInit(listOf(invalidCandle(0L)), DataLoadMore.NONE)
        drain()
        assertEquals(0, chart.getDataList().size)
        // B 合法 INIT（epoch=2 > 水位）——受理后排空前同步首推
        chart.setSymbol(SymbolInfo("B"))
        drain()
        loader.respondInit(listOf(candle(5_000L, 200.0)), DataLoadMore.NONE)
        loader.subscribeCallbacks.last()(candle(6_000L, 201.0))
        drain()
        // Q01-b 基准：首推不丢（[200,201]）；先前失败水位（1）不拦截新会话（2）
        assertEquals(listOf(200.0, 201.0), chart.getDataList().map { it.close },
            "合法 INIT 同步首推保持（Q01-b 基准）")
        // M01 基准：失败后切 C——C 会话正常
        chart.setSymbol(SymbolInfo("C"))
        drain()
        loader.respondInit((0 until 3).map { candle(1_000_000L + it * 60_000L, 300.0 + it) }, DataLoadMore.NONE)
        drain()
        assertEquals(3, chart.getDataList().size, "旧失败不干扰新 C 会话（M01 基准）")
        loader.subscribeCallbacks.last()(candle(2_000_000L, 400.0))
        drain()
        assertEquals(400.0, chart.getDataList().last().close, "C 推送正常")
        LKanvasChart.dispose(chart)
    }
}
