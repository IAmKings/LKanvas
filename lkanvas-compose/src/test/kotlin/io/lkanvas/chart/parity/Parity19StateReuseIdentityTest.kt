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
 * parity-19：state 复用后提交身份生命周期差分（第八轮审计 R8-01 验收）。
 *
 * R8-01（P1）：dispose 旧门面后以同一 [ChartState] 重建门面（公开契约：
 * destroy 注释明确 state 可复用）——修复前 dataEpoch 随门面归零（新 C 的
 * INIT epoch=1），而 state 内引擎的 rejectedInitWatermark 仍是旧门面 B
 * 失败留下的 2 → C 的合法推送被 SESSION_MISMATCH 静默拒绝（实时停更）。
 * 修复：身份计数与水位统一移入 state（与引擎同生命周期、跨门面连续）。
 */
class Parity19StateReuseIdentityTest {

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

    /** 非法 OHLC：high < open。 */
    private fun invalidCandle(ts: Long) = Candle(ts, open = 200.0, high = 190.0, low = 210.0, close = 200.0, volume = 10.0)

    private class ScriptedLoader : io.lkanvas.chart.DataLoader {
        private val pendingInits = ArrayDeque<(List<Candle>, DataLoadMore?) -> Unit>()
        val subscribeCallbacks = mutableListOf<(Candle) -> Unit>()

        override fun getBars(params: GetBarsParams) {
            if (params.type == DataLoadType.INIT) pendingInits.addLast(params.callback)
        }

        override fun subscribeBar(params: SubscribeBarParams) {
            subscribeCallbacks += params.callback
        }

        override fun unsubscribeBar(params: io.lkanvas.chart.UnsubscribeBarParams) {}

        fun respondInit(data: List<Candle>, more: DataLoadMore?) {
            pendingInits.removeFirst()(data, more)
        }
    }

    // ---------- R8-01 主复现 ----------

    @Test
    fun `复用state重建门面后新会话推送不被旧水位拦截（R8-01）`() {
        // 门面 1：A 就绪 [100]；切 B；B INIT 非法失败（引擎水位=2，随 state 存续）
        val loader1 = ScriptedLoader()
        val chart1 = LKanvasChart.init()
        chart1.setSymbol(SymbolInfo("A"))
        chart1.setPeriod(Period.ofMinutes(1))
        chart1.setDataLoader(loader1)
        drain()
        loader1.respondInit(listOf(candle(0L, 100.0)), DataLoadMore.NONE)
        drain()
        chart1.setSymbol(SymbolInfo("B"))
        drain()
        loader1.respondInit(listOf(invalidCandle(2_000L)), DataLoadMore.NONE)
        drain()
        assertEquals(listOf(100.0), chart1.getDataList().map { it.close }, "失败 B 不污染 A（R7-01 基准）")

        // 保存 state、dispose 门面 1、以同一 state 重建门面 2（公开契约）
        val reusedState = chart1.state
        LKanvasChart.dispose(chart1)

        val loader2 = ScriptedLoader()
        val chart2 = LKanvasChart.init(reusedState)
        chart2.setSymbol(SymbolInfo("C"))
        chart2.setPeriod(Period.ofMinutes(1))
        chart2.setDataLoader(loader2)
        drain()
        loader2.respondInit(listOf(candle(5_000L, 300.0)), DataLoadMore.NONE)
        drain()
        assertEquals(listOf(300.0), chart2.getDataList().map { it.close }, "新门面 C 合法 INIT 成功")
        assertEquals("C", chart2.state.dataSnapshot.marketKey?.symbol)

        // 修复断言：C 推送 close=301 被接纳（修复前旧水位 2 拦住新门面 epoch=1
        // ——实时行情静默停更）
        loader2.subscribeCallbacks.last()(candle(6_000L, 301.0))
        drain()
        assertEquals(listOf(300.0, 301.0), chart2.getDataList().map { it.close },
            "复用 state 后新会话实时更新正常（R8-01 复现收口）")
        LKanvasChart.dispose(chart2)
    }

    // ---------- 基准保持：复用后失败 INIT 拒绝语义仍然成立 ----------

    @Test
    fun `复用state后失败INIT已入队动作仍被拒（R8-01基准）`() {
        val loader1 = ScriptedLoader()
        val chart1 = LKanvasChart.init()
        chart1.setSymbol(SymbolInfo("A"))
        chart1.setPeriod(Period.ofMinutes(1))
        chart1.setDataLoader(loader1)
        drain()
        loader1.respondInit(listOf(candle(0L, 100.0)), DataLoadMore.NONE)
        drain()
        chart1.setSymbol(SymbolInfo("B"))
        drain()
        loader1.respondInit(listOf(invalidCandle(2_000L)), DataLoadMore.NONE)
        drain()

        val reusedState = chart1.state
        LKanvasChart.dispose(chart1)

        // 门面 2 的 D INIT 也失败：拒绝语义不因生命周期统一而失效
        val loader2 = ScriptedLoader()
        val chart2 = LKanvasChart.init(reusedState)
        chart2.setSymbol(SymbolInfo("D"))
        chart2.setPeriod(Period.ofMinutes(1))
        chart2.setDataLoader(loader2)
        drain()
        loader2.respondInit(listOf(invalidCandle(3_000L)), DataLoadMore.NONE)
        // 排空前经 D 订阅推送（失败会话已入队动作）
        loader2.subscribeCallbacks.last()(candle(4_000L, 999.0))
        drain()
        // A 数据保持（快照 key=A、值 [100]）——拒绝语义跨门面一致
        assertEquals("A", chart2.state.dataSnapshot.marketKey?.symbol)
        assertEquals(listOf(100.0), chart2.getDataList().map { it.close },
            "复用 state 后失败 INIT 已入队推送仍被拒（R7-01 基准跨生命周期保持）")
        // 合法重试恢复（新代次必然大于水位）
        chart2.resetData()
        drain()
        loader2.respondInit(listOf(candle(7_000L, 500.0)), DataLoadMore.NONE)
        drain()
        assertEquals(listOf(500.0), chart2.getDataList().map { it.close }, "重试恢复")
        loader2.subscribeCallbacks.last()(candle(8_000L, 501.0))
        drain()
        assertEquals(listOf(500.0, 501.0), chart2.getDataList().map { it.close })
        LKanvasChart.dispose(chart2)
    }
}
