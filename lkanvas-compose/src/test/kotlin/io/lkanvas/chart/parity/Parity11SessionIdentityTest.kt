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
 * parity-11：**接受时点会话身份**差分（第四轮审计 Q01 验收）。
 *
 * - ABA：锁等待旧实时推送（第一段 A）不得落入第二次 A（key 相同、代次不同）；
 * - 首推：INIT 接受后立即到达的合法首推不得丢失（dataEpoch 在接受时点递增，
 *   不依赖 Reset 协程已执行）；
 * - 拒绝订阅：INIT 被锁内拒绝后不再产生 subscribeBar 生命周期副作用。
 */
class Parity11SessionIdentityTest {

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

    private class ScriptedLoader : io.lkanvas.chart.DataLoader {
        val requests = mutableListOf<DataLoadType>()
        val subscribes = mutableListOf<SubscribeBarParams>()
        val unsubscribes = mutableListOf<io.lkanvas.chart.UnsubscribeBarParams>()
        private val pendingInits = ArrayDeque<(List<Candle>, DataLoadMore?) -> Unit>()
        private val pendingUpdates = ArrayDeque<(List<Candle>, DataLoadMore?) -> Unit>()

        override fun getBars(params: GetBarsParams) {
            requests += params.type
            when (params.type) {
                DataLoadType.INIT -> pendingInits.addLast(params.callback)
                DataLoadType.UPDATE -> pendingUpdates.addLast(params.callback)
                else -> {}
            }
        }

        override fun subscribeBar(params: SubscribeBarParams) {
            subscribes += params
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

    // ---------- Q01-a：ABA——锁等待旧推送不得落入第二次 A ----------

    @Test
    fun `ABA锁等待旧实时推送被丢弃（Q01）`() {
        val loader = ScriptedLoader()
        val chart = newChart(loader)
        drain()
        // 第一段 A 就绪 + 订阅建立
        loader.respondInit((0 until 5).map { candle(it * 60_000L, 300.0) }, DataLoadMore.NONE)
        drain()
        assertEquals(1, loader.subscribes.size)
        val pushFromFirstA = loader.subscribes[0].callback
        // A→B→A：两次切换后第二次 A 就绪（close=30..）
        chart.setSymbol(SymbolInfo("B"))
        drain()
        loader.respondInit((0 until 4).map { candle(1_000_000L + it * 60_000L, 70.0 + it) }, DataLoadMore.NONE)
        drain()
        chart.setSymbol(SymbolInfo("A"))
        drain()
        loader.respondInit((0 until 6).map { candle(2_000_000L + it * 60_000L, 30.0 + it) }, DataLoadMore.NONE)
        drain()
        assertEquals(6, chart.getDataList().size)
        // 第一段 A 的旧推送（close=999）到达：两次 A 的 key 相同——
        // 修复前 currentSubscriptionGenFor 以 key 相等放行（ABA 污染）
        pushFromFirstA(candle(999_000_000L, 999.0))
        drain()
        assertEquals(6, chart.getDataList().size, "ABA 旧推送被订阅代次丢弃（Q01-a 收口）")
        assertEquals(30.0 + 5, chart.getDataList().last().close)
        // 第二次 A 的新订阅推送正常
        loader.subscribes.last().callback(candle(3_000_000L, 35.0))
        drain()
        assertEquals(7, chart.getDataList().size, "新会话推送正常追加")
        LKanvasChart.dispose(chart)
    }

    // ---------- Q01-b：INIT 后立即首推不丢失 ----------

    @Test
    fun `INIT接受后立即首推不丢失（Q01）`() {
        val loader = ScriptedLoader()
        val chart = newChart(loader)
        drain()
        // INIT 回调到达（applyInit 递增 dataEpoch 于接受时点）；
        // 在 drain（协程执行）之前**立即**注入首推——模拟订阅在 Reset 协程
        // 执行前先入队的公开支持交错
        loader.respondInit(listOf(candle(1_000L, 100.0)), DataLoadMore.NONE)
        // 未 drain：subscribeBar 尚未发生——通过 pending 订阅不可得；
        // 改为直接验证等价路径：B 切换前，A 的第二个 INIT+立即推送
        // （构造首推丢失需订阅已建立；此处覆盖：INIT 建立订阅后，新 INIT 的
        //  首推经 dataEpoch 语义应被接纳）
        drain()
        assertEquals(1, loader.subscribes.size)
        // 新 INIT：回调内（drain 前）先手动触发旧订阅推送将按代次被拒；
        // 新订阅建立后立即推送（同一 drain 周期内）
        chart.setSymbol(SymbolInfo("B"))
        drain()
        loader.respondInit(listOf(candle(5_000L, 200.0)), DataLoadMore.NONE)
        // drain 前注入 B 的首推（subscribeBar 在 INIT 接受时同步发生——
        // ScriptedLoader.subscribeBar 立即记录回调，可立即推送）
        loader.subscribes.last().callback(candle(6_000L, 201.0))
        drain()
        println("DEBUG-Q01b marketKey=${chart.state.dataSnapshot.marketKey} size=${chart.getDataList().size} candles=${chart.getDataList().map { it.close }}")
        // 修复前：epoch 读协程执行时引擎状态（Reset 未执行 → 旧 session）→ 首推被拒
        assertEquals(2, chart.getDataList().size, "新会话首推被接纳（Q01-b 收口）")
        assertEquals(201.0, chart.getDataList().last().close)
        LKanvasChart.dispose(chart)
    }

    // ---------- Q01-c：锁内拒绝的 INIT 不产生订阅副作用 ----------

    @Test
    fun `锁内拒绝的INIT不再订阅（Q01）`() {
        val loader = ScriptedLoader()
        val chart = newChart(loader)
        drain()
        // 第一段 A 的 INIT 发出（不消费，pending 首个=A）；切 B（B INIT 入队）
        assertEquals(1, loader.requests.size)
        chart.setSymbol(SymbolInfo("B"))
        drain()
        assertEquals(2, loader.requests.size)
        // 旧 A INIT 到达：锁内代次复核拒绝 → 不得产生 subscribeBar（此刻 B 未回调，
        // 订阅数应为 0；修复前旧实现无条件订阅会得到 [B_pending 前的 A 订阅]）
        loader.respondInit((0 until 3).map { candle(it * 60_000L, 50.0) }, DataLoadMore.NONE)
        drain()
        assertEquals(0, loader.subscribes.size, "拒绝的 INIT 不订阅（Q01-c 收口）")
        // B 的 INIT 正常回调 → 建立唯一订阅（B）
        loader.respondInit((0 until 4).map { candle(1_000_000L + it * 60_000L, 70.0 + it) }, DataLoadMore.NONE)
        drain()
        assertEquals(1, loader.subscribes.size)
        assertEquals("B", loader.subscribes[0].symbol.ticker, "唯一订阅属 B 会话")
        LKanvasChart.dispose(chart)
    }
}
