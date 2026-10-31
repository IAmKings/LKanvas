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

/**
 * parity-13：会话失败归属差分（第六轮审计 M01 验收）。
 *
 * M01：旧 INIT 失败清理无会话归属——decrementAndGet 改写全局代次 +
 * 无归属 unsubscribeData 退订较新有效会话。修复：myEpoch 捕获 +
 * Rejected 分支归属复核（仅本会话仍当前才清理）+ 代次单调不回退。
 */
class Parity13SessionOwnershipTest {

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

    @Test
    fun `旧B失败清理不退订较新C会话（M01）`() {
        val loader = ScriptedLoader()
        val chart = newChart(loader)
        drain()
        // A 就绪
        loader.respondInit((0 until 3).map { candle(it * 60_000L, 300.0 + it) }, DataLoadMore.NONE)
        drain()
        assertEquals(3, chart.getDataList().size)
        // B INIT 响应（非法数据）——rejected 延续在协程中排队
        chart.setSymbol(SymbolInfo("B"))
        drain()
        loader.respondInit(listOf(invalidCandle(2_000L)), DataLoadMore.NONE)
        // B 的 rejected 延续尚未执行（scope.launch 排队）；立即切 C
        chart.setSymbol(SymbolInfo("C"))
        drain()
        loader.respondInit(listOf(candle(5_000L, 300.0)), DataLoadMore.NONE)
        drain()
        assertEquals("C", chart.getSymbol()?.ticker)
        // M01 修复断言：B 失败延续不退订 C（修复前 unsubscribes=[A,B,C]）
        assertEquals(listOf("A", "B"), loader.unsubscribes.map { it.symbol.ticker },
            "退订记录不含 C（B 失败延续无归属清理已修复）")
        assertEquals(1, chart.getDataList().size)
        // M01 修复断言：C 会话不被 B 失败清理退订
        // 修复前：B Rejected 延续 decrement epoch + unsubscribeData 退订 C → C 推送被丢
        loader.subscribeCallbacks.last()(candle(6_000L, 301.0))
        drain()
        assertEquals(2, chart.getDataList().size, "C 推送仍生效（M01 复现收口）")
        assertEquals(301.0, chart.getDataList().last().close)
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `单B失败仍正常退订且合法重试恢复（M01 基准保持）`() {
        val loader = ScriptedLoader()
        val chart = newChart(loader)
        drain()
        loader.respondInit((0 until 5).map { candle(it * 60_000L, 100.0 + it) }, DataLoadMore.NONE)
        drain()
        // B 非法 INIT（本会话仍当前 → 清理生效）
        chart.setSymbol(SymbolInfo("B"))
        drain()
        val unsubBefore = loader.unsubscribes.size
        loader.respondInit(listOf(invalidCandle(2_000L)), DataLoadMore.NONE)
        drain()
        assertEquals(unsubBefore + 1, loader.unsubscribes.size, "本会话失败清理仍退订")
        // 合法重试恢复
        chart.resetData()
        drain()
        loader.respondInit((0 until 4).map { candle(2_000_000L + it * 60_000L, 70.0 + it) }, DataLoadMore.NONE)
        drain()
        assertEquals(4, chart.getDataList().size, "合法重试恢复正常")
        assertEquals(70.0, chart.getDataList().first().close)
        loader.subscribeCallbacks.last()(candle(3_000_000L, 90.0))
        drain()
        assertEquals(5, chart.getDataList().size)
        LKanvasChart.dispose(chart)
    }
}
