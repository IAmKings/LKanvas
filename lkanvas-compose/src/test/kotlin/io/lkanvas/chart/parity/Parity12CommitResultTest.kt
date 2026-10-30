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
 * parity-12：**真实提交结果反馈**差分（第五轮审计 N01 验收）。
 *
 * - 非法 INIT（OHLC 校验失败）被 ChartDataStore 拒绝后：
 *   - 不建立订阅（N01 主复现：修复前 Host 恒 true、订阅照常建立）；
 *   - B 订阅推送不得写进旧 A 会话（修复前 getSymbol=B、candles=[A,999]）；
 *   - 后续合法 INIT 恢复正常。
 */
class Parity12CommitResultTest {

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

        override fun unsubscribeBar(params: io.lkanvas.chart.UnsubscribeBarParams) {}

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
    fun `非法INIT被拒后订阅不建立且推送不写旧会话（N01）`() {
        val loader = ScriptedLoader()
        val chart = newChart(loader)
        drain()
        // A 合法 INIT（5 根，ts=0..）
        loader.respondInit((0 until 5).map { candle(it * 60_000L, 100.0 + it) }, DataLoadMore.NONE)
        drain()
        assertEquals(5, chart.getDataList().size)
        // 切 B；B INIT 返回非法 OHLC → ChartDataStore 拒绝（修复前 Host 恒 true、订阅照常建立且推送写进 A 会话）
        chart.setSymbol(SymbolInfo("B"))
        drain()
        loader.respondInit(listOf(invalidCandle(2_000L)), DataLoadMore.NONE)
        drain()
        // subscribeBar 在受理时同步发生（subscribeCallbacks=2），但 N01 反馈链已退订：
        // B 订阅回调推送应被代次门禁丢弃（审计 §7.1——"失败时不能对旧内核接纳新会话推送"）
        loader.subscribeCallbacks.last()(candle(3_000_000L, 999.0))
        drain()
        assertEquals(5, chart.getDataList().size, "非法 INIT 后旧会话不接纳新推送（N01 主复现收口）")
        assertEquals(100.0 + 4, chart.getDataList().last().close, "末根仍为 A 第 5 根")
        assertEquals("B", chart.getSymbol()?.ticker, "门面 symbol=B（配置已切换）")
        // 合法 B INIT 重试 → 恢复正常（新订阅建立且推送生效）
        chart.resetData() // 触发新 INIT（拒绝路径不自动重试；宿主决定重试时机）
        drain()
        loader.respondInit((0 until 4).map { candle(2_000_000L + it * 60_000L, 70.0 + it) }, DataLoadMore.NONE)
        drain()
        assertEquals(4, chart.getDataList().size, "合法 B INIT 恢复正常")
        assertEquals(70.0, chart.getDataList().first().close)
        loader.subscribeCallbacks.last()(candle(3_000_000L, 90.0))
        drain()
        assertEquals(5, chart.getDataList().size)
        assertEquals(90.0, chart.getDataList().last().close)
        LKanvasChart.dispose(chart)
    }
}
