package io.lkanvas.chart.parity

import io.lkanvas.chart.DataLoadMore
import io.lkanvas.chart.DataLoadType
import io.lkanvas.chart.DataLoader
import io.lkanvas.chart.GetBarsParams
import io.lkanvas.chart.LKanvasChart
import io.lkanvas.chart.Period
import io.lkanvas.chart.SubscribeBarParams
import io.lkanvas.chart.SymbolInfo
import io.lkanvas.chart.UnsubscribeBarParams
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
 * parity-4：数据路径差分（SLA-005/006 验收）。
 *
 * 共同输入驱动门面，断言请求方向/次数/上下文与最终数据符合上游契约
 * （Store._processDataLoad/_addData/_adjustVisibleRange 边界触发/resetData 顺序），
 * 并覆盖本地代次门禁增强（在途切品种）。
 */
class Parity4DataPathTest {

    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** 驱动门面协程至队列收敛。 */
    private fun drain() {
        mainDispatcher.scheduler.apply {
            repeat(200) { advanceUntilIdle() }
        }
    }

    private fun candle(ts: Long, close: Double) = Candle(ts, close, close + 1, close - 1, close, 100.0)

    /** 可编程 loader：记录请求顺序，允许测试手动分批回调（模拟异步数据源）。 */
    private class ScriptedLoader : DataLoader {
        /** (type, timestamp) 请求序录。 */
        val requests = mutableListOf<Pair<DataLoadType, Long?>>()
        val unsubscribes = mutableListOf<UnsubscribeBarParams>()
        val subscribeCallbacks = mutableListOf<(Candle) -> Unit>()
        private val pendingInits = ArrayDeque<(List<Candle>, DataLoadMore?) -> Unit>()
        private val pendingForwards = ArrayDeque<(List<Candle>, DataLoadMore?) -> Unit>()
        private val pendingBackwards = ArrayDeque<(List<Candle>, DataLoadMore?) -> Unit>()

        override fun getBars(params: GetBarsParams) {
            requests += params.type to params.timestamp
            when (params.type) {
                DataLoadType.INIT -> pendingInits.addLast(params.callback)
                DataLoadType.FORWARD -> pendingForwards.addLast(params.callback)
                DataLoadType.BACKWARD -> pendingBackwards.addLast(params.callback)
                DataLoadType.UPDATE -> {}
            }
        }

        override fun subscribeBar(params: SubscribeBarParams) {
            subscribeCallbacks += params.callback
        }

        override fun unsubscribeBar(params: UnsubscribeBarParams) {
            unsubscribes += params
        }

        fun respondInit(data: List<Candle>, more: DataLoadMore?) {
            check(pendingInits.isNotEmpty()) { "pendingInits 空；requests=$requests pendingInit=${pendingInits.size} fwd=${pendingForwards.size} bwd=${pendingBackwards.size}" }
            pendingInits.removeFirst()(data, more)
        }

        fun respondForward(data: List<Candle>, more: DataLoadMore?) {
            pendingForwards.removeFirst()(data, more)
        }

        fun respondBackward(data: List<Candle>, more: DataLoadMore?) {
            pendingBackwards.removeFirst()(data, more)
        }
    }

    private fun newChartWith(loader: ScriptedLoader): LKanvasChart {
        val chart = LKanvasChart.init()
        chart.setSymbol(SymbolInfo("A"))
        chart.setPeriod(Period.ofMinutes(1))
        chart.setDataLoader(loader)
        // 布局：barSpacing=8、内容宽 80 → 可见 10 根（边界判定需真实可见区）
        chart.state.onLayout(80f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        return chart
    }

    // ---------- SLA-005：双向分页（边界触发 + 同方向 more 更新） ----------

    @Test
    fun `视口边界触发对应方向分页请求`() {
        val loader = ScriptedLoader()
        val chart = newChartWith(loader)
        drain()
        // init：10 根，双向均有更多
        loader.respondInit((0 until 10).map { candle(it * 60_000L, 100.0 + it) }, DataLoadMore(true, true))
        drain()
        assertEquals(listOf<Pair<DataLoadType, Long?>>(DataLoadType.INIT to null), loader.requests, "仅 INIT")

        // 滚到最左（from==0）→ forward 拉取（上游 _adjustVisibleRange：from===0 && forward）
        chart.scrollToDataIndex(0)
        drain()
        assertEquals(2, loader.requests.size, "边界触发 FORWARD")
        assertEquals(DataLoadType.FORWARD, loader.requests[1].first)

        // forward 响应 more(backward=true, forward=false)：头插 5 根更早数据
        loader.respondForward((0 until 5).map { candle(it * 60_000L - 300_000L, 90.0 + it) }, DataLoadMore(true, false))
        drain()
        assertEquals(15, chart.getDataList().size, "forward 头插")

        // forward 已无更多（同方向 more 更新为 false）→ 再滚到最左不再请求
        chart.scrollToDataIndex(0)
        drain()
        assertEquals(2, loader.requests.size, "more.forward=false 后停止")

        // 滚到最右（to==total）→ backward 拉取
        chart.scrollToRealTime()
        drain()
        assertEquals(3, loader.requests.size, "边界触发 BACKWARD")
        assertEquals(DataLoadType.BACKWARD, loader.requests[2].first)

        // backward 响应 more(backward=false)：尾追后同方向停止
        loader.respondBackward((0 until 3).map { candle(1_000_000L + it * 60_000L, 200.0 + it) }, DataLoadMore(false, false))
        drain()
        chart.scrollToRealTime()
        drain()
        assertEquals(3, loader.requests.size, "more.backward=false 后停止")

        LKanvasChart.dispose(chart)
    }

    @Test
    fun `分页请求时间戳按方向取首末根`() {
        val loader = ScriptedLoader()
        val chart = newChartWith(loader)
        drain()
        loader.respondInit((0 until 10).map { candle(1000L + it * 60_000L, 100.0 + it) }, DataLoadMore(true, true))
        drain()
        // backward 请求携带末根时间戳；forward 携带首根（上游 _processDataLoad switch）
        chart.scrollToRealTime()
        drain()
        assertEquals(1000L + 9 * 60_000L, loader.requests.last().second, "backward=末根时间戳")
        // 回调 backward（上游 loading 门禁：未回调前后续拉取不重入）
        loader.respondBackward(
            (0 until 3).map { candle(600_000L + it * 60_000L, 200.0 + it) },
            DataLoadMore(true, true),
        )
        drain()
        chart.scrollToDataIndex(0)
        drain()
        assertEquals(1000L, loader.requests.last().second, "forward=首根时间戳")
        LKanvasChart.dispose(chart)
    }

    // ---------- SLA-006：在途切品种（代次门禁）与退订顺序 ----------

    @Test
    fun `在途切品种_新请求不被loading跳过且回调按代次丢弃`() {
        val loader = ScriptedLoader()
        val chart = newChartWith(loader)
        drain()
        assertEquals(1, loader.requests.size, "A 的 INIT 已发出（未回调，loading=true）")

        // A 在途时切 B：新 INIT 必须发出（上游 resetData 先清 loading；旧实现被 if(loading) 跳过）
        chart.setSymbol(SymbolInfo("B"))
        drain()
        assertEquals(2, loader.requests.size, "B 的 INIT 发出（不被 loading 门禁跳过）")

        // A 的迟到回调：代次门禁丢弃，A 数据不写入 B 会话（旧实现以 currentKey 写入）
        loader.respondInit((0 until 5).map { candle(it * 60_000L, 50.0 + it) }, DataLoadMore.NONE)
        drain()
        assertEquals(0, chart.getDataList().size, "A 迟到回调整体丢弃")

        // B 回调正常应用
        loader.respondInit((0 until 7).map { candle(it * 60_000L, 70.0 + it) }, DataLoadMore.NONE)
        drain()
        assertEquals(7, chart.getDataList().size)
        assertEquals(70.0, chart.getDataList().first().close, "B 数据落库")
        assertEquals("B", chart.getSymbol()?.ticker)

        LKanvasChart.dispose(chart)
    }

    @Test
    fun `在途旧订阅推送被代次门禁丢弃`() {
        val loader = ScriptedLoader()
        val chart = newChartWith(loader)
        drain()
        loader.respondInit((0 until 5).map { candle(it * 60_000L, 100.0 + it) }, DataLoadMore.NONE)
        drain()
        assertEquals(1, loader.subscribeCallbacks.size, "A 订阅建立")

        // 切 B（退订 A → B INIT；上游 subscribeBar 在 init 回调内建立）
        chart.setSymbol(SymbolInfo("B"))
        drain()
        loader.respondInit((0 until 5).map { candle(it * 60_000L, 105.0 + it) }, DataLoadMore.NONE)
        drain()
        assertEquals(2, loader.subscribeCallbacks.size, "B 订阅建立")
        assertEquals(1, loader.unsubscribes.size, "A 已退订")

        // A 的旧订阅推送：代次门禁丢弃，不混入 B 会话（审计复现：A 行情混入 B 图表）
        loader.subscribeCallbacks[0](candle(500_000L, 999.0))
        drain()
        assertEquals(5, chart.getDataList().size, "A 推送被丢弃")

        // B 的订阅推送正常应用
        loader.subscribeCallbacks[1](candle(500_000L, 105.0))
        drain()
        assertEquals(6, chart.getDataList().size, "B 推送追加")
        assertEquals(105.0, chart.getDataList().last().close)

        LKanvasChart.dispose(chart)
    }

    @Test
    fun `切品种先退订旧上下文再发起新请求`() {
        val loader = ScriptedLoader()
        val chart = newChartWith(loader)
        drain()
        loader.respondInit((0 until 5).map { candle(it * 60_000L, 100.0 + it) }, DataLoadMore.NONE)
        drain()
        loader.requests.clear()
        loader.unsubscribes.clear()

        chart.setSymbol(SymbolInfo("B"))
        drain()
        // 上游 resetData 顺序：退订（旧 symbol）先行 → 新 INIT
        assertEquals(1, loader.unsubscribes.size)
        assertEquals("A", loader.unsubscribes[0].symbol.ticker, "退订旧品种")
        assertEquals(1, loader.requests.size)
        assertEquals(DataLoadType.INIT, loader.requests[0].first, "退订后发起新 INIT")
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `destroy释放数据订阅`() {
        val loader = ScriptedLoader()
        val chart = newChartWith(loader)
        drain()
        loader.respondInit((0 until 5).map { candle(it * 60_000L, 100.0 + it) }, DataLoadMore.NONE)
        drain()
        LKanvasChart.dispose(chart)
        assertEquals(1, loader.unsubscribes.size, "dispose 退订")
        assertTrue(chart.getDataList().isNotEmpty(), "dispose 不清数据（上游 dispose 不清 dataList）")
    }
}
