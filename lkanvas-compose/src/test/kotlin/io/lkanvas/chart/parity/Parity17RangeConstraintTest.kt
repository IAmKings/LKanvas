package io.lkanvas.chart.parity

import io.lkanvas.chart.LKanvasChart
import io.lkanvas.chart.Period
import io.lkanvas.chart.SymbolInfo
import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * parity-17：范围模板约束差分（第六轮审计 M05 验收）。
 *
 * M05：paneVisibleRange 聚合后不消费模板 minValue/maxValue——内置 VOL
 * minValue=0 恒值 100 范围仍 (99,101) 不含 0；自定义 -50/500 BAR 范围
 * 仍 (0,100)。修复：范围收口处合并可见实例约束（上游 YAxis.ts:163-172/
 * 222-224 specifyMin/Max 语义），绘制与转换共用单一来源。
 */
class Parity17RangeConstraintTest {

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
        mainDispatcher.scheduler.apply { repeat(200) { advanceUntilIdle() } }
    }

    private fun newChart(volume: Double = 100.0): LKanvasChart {
        val chart = LKanvasChart.init()
        chart.setSymbol(SymbolInfo("A"))
        chart.setPeriod(Period.ofMinutes(1))
        chart.setDataLoader(object : io.lkanvas.chart.DataLoader {
            override fun getBars(params: io.lkanvas.chart.GetBarsParams) {
                params.callback(
                    (0 until 30).map { Candle(it * 60_000L, 100.0, 101.0, 99.0, 100.0, volume) },
                    io.lkanvas.chart.DataLoadMore.NONE,
                )
            }
        })
        drain()
        return chart
    }

    /** 约束探针模板：恒值 [v] 的 BAR，声明 minValue/maxValue。 */
    private fun constrainedTemplate(v: Double, min: Double?, max: Double?) = object : IndicatorDefinition {
        override val name = "CONSTRAINED"
        override val shortName = "CT"
        override val calcParams = emptyList<Double>()
        override val precision = 2
        override val plots = listOf(PlotDef("v", "V", PlotType.BAR, baseValue = 0.0))
        override val minValue: Double? = min
        override val maxValue: Double? = max
        override fun initScratch(params: List<Double>) = null
        override fun stepScratch(prev: Any?, c: List<Candle>, p: List<Double>, i: Int) = null
        override fun rowFromScratch(s: Any?, c: List<Candle>, p: List<Double>, i: Int) = mapOf("v" to v)
    }

    // ---------- M05 复现 a：VOL minValue=0 恒值 100 → 范围含 0 ----------

    @Test
    fun `VOL minValue0恒值100范围含0（M05）`() {
        val chart = newChart(volume = 100.0)
        chart.createIndicator("VOL", paneId = "vol")
        drain()
        val range = chart.state.paneVisibleRange("vol")
        assertNotNull(range)
        assertEquals(0.0, range.first, "VOL minValue=0 进入范围下界（修复前 (99,101)）")
        assertTrue(range.second >= 100.0, "上界含成交量")
        // 单一来源：转换与绘制共用（0 基线落在 pane 内）
        chart.state.onLayout(600f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        chart.state.relayoutExtraPanes()
        drain()
        val (t, b) = chart.state.paneBounds("vol")!!
        val y0 = chart.state.paneValueToY("vol", 0.0)
        assertNotNull(y0)
        assertTrue(y0 >= 0f && y0 <= b - t, "0 值 y 落在 pane 相对范围内（$y0）")
        LKanvasChart.dispose(chart)
    }

    // ---------- M05 复现 b：minValue=-50/maxValue=500 恒值 100 → 范围 (-50,500) ----------

    @Test
    fun `自定义约束BAR范围负50到500（M05）`() {
        val chart = newChart()
        chart.state.registry.register(constrainedTemplate(100.0, -50.0, 500.0))
        val paneId = chart.createIndicator("CONSTRAINED")!!
            .let { chart.getIndicatorByInstanceId(it)!!.paneId }
        drain()
        val range = chart.state.paneVisibleRange(paneId)
        assertEquals(-50.0 to 500.0, range, "约束收口（修复前 (0,100)）")
        LKanvasChart.dispose(chart)
    }

    // ---------- 无约束实例不受影响 ----------

    @Test
    fun `无约束实例范围保持自由聚合`() {
        val chart = newChart()
        chart.state.registry.register(constrainedTemplate(123.0, null, null))
        val paneId = chart.createIndicator("CONSTRAINED")!!
            .let { chart.getIndicatorByInstanceId(it)!!.paneId }
        drain()
        val range = chart.state.paneVisibleRange(paneId)
        assertEquals(0.0 to 123.0, range, "无约束=BAR 基线 0 到数据值")
        LKanvasChart.dispose(chart)
    }
}
