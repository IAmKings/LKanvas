package io.lkanvas.chart.parity

import io.lkanvas.chart.DataLoadMore
import io.lkanvas.chart.DataLoadType
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
 * parity-9：数据管线**组合路径**差分（复审 R01/R02/R03 验收；审计 §7.1——
 * 同一组输入验证请求、数据、事件和上下文，而非分别测试每个函数）。
 *
 * - R01：分页不得使同会话实时订阅失效（分页→推送仍更新）；
 * - R02：接受检查点绑定会话 key——排队切换后旧回调数据不得落新 key；
 * - R03：数据提交后自动范围调整/边界续拉（INIT 1 根+more 无手势也填满）。
 */
class Parity9DataPipelineTest {

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

    /** 可编程 loader：记录请求序，手动分批回调（模拟异步数据源）。 */
    private class ScriptedLoader : io.lkanvas.chart.DataLoader {
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
            pendingInits.removeFirst()(data, more)
        }

        fun respondForward(data: List<Candle>, more: DataLoadMore?) {
            pendingForwards.removeFirst()(data, more)
        }

        fun respondBackward(data: List<Candle>, more: DataLoadMore?) {
            pendingBackwards.removeFirst()(data, more)
        }
    }

    private fun newChart(loader: ScriptedLoader): LKanvasChart {
        val chart = LKanvasChart.init()
        chart.setSymbol(SymbolInfo("A"))
        chart.setPeriod(Period.ofMinutes(1))
        chart.setDataLoader(loader)
        // 布局：barSpacing=8、内容宽 80 → 可见 10 根（边界判定需真实可见区）
        chart.state.onLayout(80f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        return chart
    }

    // ---------- R01：分页不杀死实时订阅 ----------

    @Test
    fun `分页后实时订阅仍更新（R01）`() {
        val loader = ScriptedLoader()
        val chart = newChart(loader)
        drain()
        // DataLoadMore(backward, forward)：本用例需要 forward 可分页
        loader.respondInit((0 until 200).map { candle(it * 60_000L, 100.0 + it) }, DataLoadMore(false, true))
        drain()
        assertEquals(200, chart.getDataList().size)
        assertEquals(1, loader.subscribeCallbacks.size, "INIT 后订阅建立")

        // 触发 FORWARD 分页（滚到最左）
        chart.scrollToDataIndex(0)
        drain()
        assertEquals(DataLoadType.FORWARD, loader.requests.last().first, "分页请求发出")
        loader.respondForward(
            (0 until 5).map { candle(it * 60_000L - 300_000L, 90.0 + it) },
            DataLoadMore(false, false),
        )
        drain()
        assertEquals(205, chart.getDataList().size, "forward 头插")

        // 关键断言（R01 修复前：分页 ++generation 使订阅代次过期，推送被丢弃）
        loader.subscribeCallbacks[0](candle(200L * 60_000L, 999.0))
        drain()
        assertEquals(206, chart.getDataList().size, "分页后实时推送仍更新（订阅存活）")
        assertEquals(999.0, chart.getDataList().last().close)
        LKanvasChart.dispose(chart)
    }

    // ---------- R02：接受检查点绑定 key，排队切换不串数据 ----------

    @Test
    fun `排队切换后旧回调数据落旧key不污染新会话（R02）`() {
        val loader = ScriptedLoader()
        val chart = newChart(loader)
        drain()
        // A 的 INIT 回调到达（应用动作排队）→ 紧接着主线程 setSymbol(B)（切换排队/内联）
        loader.respondInit((0 until 5).map { candle(it * 60_000L, 50.0 + it) }, DataLoadMore.NONE)
        chart.setSymbol(SymbolInfo("B"))
        drain()
        // B 的 INIT 请求已发出（setSymbol → resetData → 新拉取）
        assertTrue(loader.requests.any { it.first == DataLoadType.INIT }, "请求记录含 INIT")
        // B 的 INIT 回调（B 数据 7 根，close=70..）
        loader.respondInit((0 until 7).map { candle(1_000_000L + it * 60_000L, 70.0 + it) }, DataLoadMore.NONE)
        drain()
        // 最终会话 = B：symbol=B、数据=B 的 7 根（A 数据不得以 key=B 落库）
        assertEquals("B", chart.getSymbol()?.ticker)
        assertEquals(7, chart.getDataList().size, "B 数据落库")
        assertEquals(70.0, chart.getDataList().first().close, "首根为 B 数据（非 A 的 50）")
        // 实时推送归属 B 会话
        loader.subscribeCallbacks.last()(candle(2_000_000L, 105.0))
        drain()
        assertEquals(8, chart.getDataList().size)
        assertEquals(105.0, chart.getDataList().last().close)
        LKanvasChart.dispose(chart)
    }

    // ---------- R03：数据提交后自动范围调整/边界续拉 ----------

    @Test
    fun `INIT不足一根无手势自动续拉填满窗口（R03）`() {
        val loader = ScriptedLoader()
        val chart = newChart(loader)
        drain()
        // INIT 只回 1 根 + 双向 more：视口在两端边界 → 无手势也应自动续拉
        loader.respondInit(listOf(candle(0L, 100.0)), DataLoadMore(true, true))
        drain()
        // 修复前：仅 1 次 INIT；修复后：边界触发链自动发出 FORWARD（from==0 分支优先）
        val forwardCount = loader.requests.count { it.first == DataLoadType.FORWARD }
        val backwardCount = loader.requests.count { it.first == DataLoadType.BACKWARD }
        assertTrue(forwardCount >= 1 || backwardCount >= 1, "数据提交后自动触发边界拉取（请求序=$loader.requests）")
        // 回调 forward：2 根更早数据 + forward 已无更多
        loader.respondForward(
            listOf(candle(-120_000L, 90.0), candle(-60_000L, 95.0)),
            DataLoadMore(true, false),
        )
        drain()
        assertEquals(3, chart.getDataList().size, "forward 头插完成")
        // forward more=false 后 from==0 不再触发 forward；to==total 且 backward=true → backward 续拉
        val backwardAfter = loader.requests.count { it.first == DataLoadType.BACKWARD }
        assertTrue(backwardAfter >= 1, "forward 停止后 backward 边界续拉（链式补齐）")
        loader.respondBackward(
            listOf(candle(60_000L, 110.0), candle(120_000L, 120.0)),
            DataLoadMore(false, false),
        )
        drain()
        assertEquals(5, chart.getDataList().size)
        // 双向 more 均为 false：不再有任何新请求（链终止）
        val total = loader.requests.size
        drain()
        assertEquals(total, loader.requests.size, "more 双 false 后链终止（防重入门禁）")
        LKanvasChart.dispose(chart)
    }

    // ---------- 组合：分页→实时→切换→旧回调（审计 §7.1 同一输入组） ----------

    @Test
    fun `组合路径_init分页实时切换旧回调`() {
        val loader = ScriptedLoader()
        val chart = newChart(loader)
        drain()
        // 1) init：200 根
        loader.respondInit((0 until 200).map { candle(it * 60_000L, 100.0 + it) }, DataLoadMore(false, true))
        drain()
        // 2) 分页（forward）
        chart.scrollToDataIndex(0)
        drain()
        loader.respondForward(
            (0 until 5).map { candle(it * 60_000L - 300_000L, 90.0 + it) },
            DataLoadMore(false, false),
        )
        drain()
        assertEquals(205, chart.getDataList().size)
        // 3) 实时推送存活（R01）
        loader.subscribeCallbacks[0](candle(200L * 60_000L, 999.0))
        drain()
        assertEquals(206, chart.getDataList().size)
        // 4) 切品种 B（A 已订阅 → 退订 A、B INIT）
        chart.setSymbol(SymbolInfo("B"))
        drain()
        loader.respondInit((0 until 7).map { candle(1_000_000L + it * 60_000L, 70.0 + it) }, DataLoadMore.NONE)
        drain()
        assertEquals("B", chart.getSymbol()?.ticker)
        assertEquals(7, chart.getDataList().size)
        assertEquals(1, loader.unsubscribes.size, "A 已退订")
        // 5) 旧会话（A）的迟到推送被订阅代次丢弃，不混入 B
        val before = chart.getDataList().size
        loader.subscribeCallbacks[0](candle(999_000_000L, 42.0))
        drain()
        assertEquals(before, chart.getDataList().size, "A 迟到推送丢弃（R01+R02 组合）")
        // B 的推送正常
        loader.subscribeCallbacks.last()(candle(2_000_000L, 105.0))
        drain()
        assertEquals(before + 1, chart.getDataList().size)
        LKanvasChart.dispose(chart)
    }
}
