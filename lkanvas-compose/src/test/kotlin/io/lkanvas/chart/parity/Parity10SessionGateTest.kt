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
 * parity-10：**写入时会话门禁**差分（第三轮审计 T01 验收；§7.1——同时覆盖
 * 分页、INIT、实时、A→B→A，每个可观察状态不被旧任务污染）。
 *
 * 门禁链：回调入口代次首检（锁外）→ Store 锁内复核（TOCTOU 关闭）→
 * 引擎唯一写入入口 expectedSessionId 复核（异步提交间隙，非 Reset 不匹配丢弃）。
 */
class Parity10SessionGateTest {

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

    /** 可编程 loader：手动分批回调（模拟任意线程异步数据源）。 */
    private class ScriptedLoader : io.lkanvas.chart.DataLoader {
        val requests = mutableListOf<DataLoadType>()
        private val pendingInits = ArrayDeque<(List<Candle>, DataLoadMore?) -> Unit>()
        val subscribeCallbacks = mutableListOf<(Candle) -> Unit>()
        var initCallCount = 0

        override fun getBars(params: GetBarsParams) {
            requests += params.type
            if (params.type == DataLoadType.INIT) {
                initCallCount++
                pendingInits.addLast(params.callback)
            }
        }

        override fun subscribeBar(params: SubscribeBarParams) {
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

    // ---------- 旧实时推送不得写入已切换会话（T01 主复现） ----------

    @Test
    fun `旧实时推送在切换完成后被丢弃（T01）`() {
        val loader = ScriptedLoader()
        val chart = newChart(loader)
        drain()
        // A 会话就绪（5 根，close=100..）
        loader.respondInit((0 until 5).map { candle(it * 60_000L, 100.0 + it) }, DataLoadMore.NONE)
        drain()
        val pushA = loader.subscribeCallbacks[0]
        // 切 B：退订 A → B INIT → B 数据落库（7 根，close=70..）
        chart.setSymbol(SymbolInfo("B"))
        drain()
        loader.respondInit((0 until 7).map { candle(1_000_000L + it * 60_000L, 70.0 + it) }, DataLoadMore.NONE)
        drain()
        // 旧 A 推送（close=999）在切换完成后到达：
        // 引擎门禁按发起时会话快照丢弃（修复前：写入 B 数据 → [200, 999]）
        pushA(candle(999_000_000L, 999.0))
        drain()
        assertEquals(7, chart.getDataList().size, "B 数据未被旧推送污染（T01 主复现收口）")
        assertEquals(70.0 + 6, chart.getDataList().last().close, "末根仍为 B 的第 7 根")
        // B 自身的新推送正常
        loader.subscribeCallbacks.last()(candle(2_000_000L, 105.0))
        drain()
        assertEquals(8, chart.getDataList().size)
        assertEquals(105.0, chart.getDataList().last().close)
        LKanvasChart.dispose(chart)
    }

    // ---------- A→B→A：旧 INIT 不得把会话回退到 A ----------

    @Test
    fun `A到B到A旧INIT不回退会话（T01）`() {
        val loader = ScriptedLoader()
        val chart = newChart(loader)
        drain()
        // 第一段 A 的 INIT 已发出（回调暂不消费，pendingInits 首个=A）
        assertEquals(1, loader.initCallCount)
        // 切 B 建立新会话（退订 A、B INIT 入队——pendingInits 第二个=B）
        chart.setSymbol(SymbolInfo("B"))
        drain()
        assertEquals(2, loader.initCallCount)
        // 旧 A（第一段）的迟到回调：代次/会话门禁必须丢弃——不得把状态 reset 回第一段 A
        loader.respondInit((0 until 5).map { candle(it * 60_000L, 50.0 + it) }, DataLoadMore.NONE)
        drain()
        assertEquals(0, chart.getDataList().size, "旧 A INIT 被丢弃（不回退会话/不落库）")
        // B 的 INIT 正常落库
        loader.respondInit((0 until 7).map { candle(1_000_000L + it * 60_000L, 70.0 + it) }, DataLoadMore.NONE)
        drain()
        assertEquals(7, chart.getDataList().size, "B 会话数据落库")
        assertEquals(70.0, chart.getDataList().first().close)
        // 再切回 A（第二次 A 会话）
        chart.setSymbol(SymbolInfo("A"))
        drain()
        loader.respondInit((0 until 9).map { candle(3_000_000L + it * 60_000L, 30.0 + it) }, DataLoadMore.NONE)
        drain()
        assertEquals(9, chart.getDataList().size, "第二段 A 会话数据落库")
        assertEquals(30.0, chart.getDataList().first().close, "第二段 A 数据（非第一段 50）")
        LKanvasChart.dispose(chart)
    }

    // ---------- 组合：INIT 分页实时混发下状态不被旧任务污染 ----------

    @Test
    fun `组合序列_INIT分页实时混发状态稳定（T01）`() {
        val loader = ScriptedLoader()
        val chart = newChart(loader)
        drain()
        loader.respondInit((0 until 10).map { candle(it * 60_000L, 100.0 + it) }, DataLoadMore.NONE)
        drain()
        // 实时推送若干
        loader.subscribeCallbacks[0](candle(10L * 60_000L, 200.0))
        drain()
        assertEquals(11, chart.getDataList().size)
        // 切 C；C INIT 落库
        chart.setSymbol(SymbolInfo("C"))
        drain()
        loader.respondInit((0 until 6).map { candle(5_000_000L + it * 60_000L, 80.0 + it) }, DataLoadMore.NONE)
        drain()
        assertEquals("C", chart.getSymbol()?.ticker)
        assertEquals(6, chart.getDataList().size)
        // A 的旧推送（第一段订阅回调）被丢弃
        loader.subscribeCallbacks[0](candle(99L * 60_000L, 42.0))
        drain()
        assertEquals(6, chart.getDataList().size, "旧推送被会话门禁丢弃")
        // C 的新推送正常
        loader.subscribeCallbacks.last()(candle(6_000_000L, 90.0))
        drain()
        assertEquals(7, chart.getDataList().size)
        assertEquals(90.0, chart.getDataList().last().close)
        LKanvasChart.dispose(chart)
    }
}
