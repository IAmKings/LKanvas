package io.lkanvas.chart

import io.lkanvas.compose.state.ChartState
import io.lkanvas.core.data.Candle
import io.lkanvas.core.overlay.OverlayPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 批次 2b：门面指标/覆盖物 CRUD（对齐 Chart.ts 指标组/覆盖物组语义）。 */
class LKanvasChartCrudTest {

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
        repeat(200) { mainDispatcher.scheduler.advanceUntilIdle(); Thread.yield() }
    }

    private fun newChartWithData(): LKanvasChart {
        val chart = LKanvasChart.init()
        chart.setSymbol(SymbolInfo("T"))
        chart.setPeriod(Period.ofMinutes(1))
        chart.setDataLoader(object : DataLoader {
            override fun getBars(params: DataLoader.GetBarsParams) {
                if (params.type == DataLoader.DataLoadType.INIT) {
                    params.callback(
                        (0 until 120).map {
                            Candle(it.toLong(), 100.0 + it, 101.0 + it, 99.0 + it, 100.5 + it, 1000.0 + it)
                        },
                        DataLoader.DataLoadMore.NONE,
                    )
                }
            }
        })
        drain()
        return chart
    }

    // ---------- 指标 CRUD ----------

    @Test
    fun `createIndicator按名创建返回instanceId且结果就绪`() {
        val chart = newChartWithData()
        val id = chart.createIndicator("EMA")
        assertTrue(id != null && id.startsWith("ind_"))
        val list = chart.getIndicators(ChartState.IndicatorFilter(name = "EMA"))
        assertEquals(1, list.size)
        assertEquals(120, list[0].state?.computedCount, "创建即全量计算")
        // 内置三个仍在
        assertEquals(4, chart.getIndicators().size)
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `createIndicator带参数与未知模板返回`() {
        val chart = newChartWithData()
        assertNull(chart.createIndicator("NOPE"), "未知模板 null")
        val id = chart.createIndicator("MA", calcParams = listOf(7.0))
        assertEquals(1, chart.getIndicators(ChartState.IndicatorFilter(name = "MA", id = id)).size)
        assertEquals(listOf(7.0), chart.getIndicatorByInstanceId(id!!)?.params)
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `overrideIndicator与removeIndicator语义`() {
        val chart = newChartWithData()
        val id = chart.createIndicator("BOLL")!!
        assertTrue(chart.overrideIndicator(id, calcParams = listOf(30.0, 3.0), visible = false))
        val inst = chart.getIndicatorByInstanceId(id)!!
        assertEquals(listOf(30.0, 3.0), inst.params)
        assertFalse(inst.visible)
        // 内置实例不可删
        assertFalse(chart.removeIndicator(ChartState.IndicatorFilter(id = "builtin-ma")))
        // 用户实例可删
        assertTrue(chart.removeIndicator(ChartState.IndicatorFilter(id = id)))
        assertNull(chart.getIndicatorByInstanceId(id))
        LKanvasChart.dispose(chart)
    }

    // ---------- 覆盖物 CRUD ----------

    @Test
    fun `createOverlay编程式两点直接完成且getOverlays可查`() {
        val chart = newChartWithData()
        val id = chart.createOverlay(
            OverlayCreate(
                name = "segment",
                points = listOf(OverlayPoint(5L, 105.0), OverlayPoint(50L, 115.0)),
            ),
        )
        assertTrue(id != null)
        val overlays = chart.getOverlays(OverlayFilter(name = "segment"))
        assertEquals(1, overlays.size)
        assertEquals(2, overlays[0].points.size)
        assertTrue(overlays[0].points[0].timestamp == 5L)
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `createOverlay数组批量与失败位`() {
        val chart = newChartWithData()
        val ids = chart.createOverlay(
            listOf(
                OverlayCreate("segment", points = listOf(OverlayPoint(1L, 100.0), OverlayPoint(2L, 101.0))),
                OverlayCreate("NOPE"),
            ),
        )
        assertEquals(2, ids.size)
        assertTrue(ids[0] != null)
        assertNull(ids[1], "未知模板失败位")
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `overrideOverlay与removeOverlay`() {
        val chart = newChartWithData()
        val id = chart.createOverlay(
            OverlayCreate("segment", points = listOf(OverlayPoint(1L, 100.0), OverlayPoint(2L, 101.0))),
        )!!
        assertTrue(chart.overrideOverlay(id, OverlayOverride(visible = false, zLevel = 5)))
        val o = chart.getOverlays(OverlayFilter(id = id)).single()
        assertFalse(o.visible)
        assertEquals(5, o.zLevel)

        assertTrue(chart.removeOverlay(OverlayFilter(name = "segment")))
        assertTrue(chart.getOverlays().none { it.id == id })
        assertFalse(chart.removeOverlay(OverlayFilter(id = id)), "再删返回 false")
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `OverlayMode枚举映射磁吸`() {
        val chart = newChartWithData()
        chart.overlayMode = OverlayMode.STRONG_MAGNET
        assertEquals(io.lkanvas.core.overlay.MagnetMode.STRONG, chart.state.overlays.magnetMode)
        chart.overlayMode = OverlayMode.NORMAL
        assertEquals(io.lkanvas.core.overlay.MagnetMode.NORMAL, chart.state.overlays.magnetMode)
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `用户指标叠加进主pane结果列表`() {
        val chart = newChartWithData()
        val before = chart.state.mainPaneIndicatorResults().size
        chart.createIndicator("BOLL")
        drain()
        assertEquals(before + 1, chart.state.mainPaneIndicatorResults().size, "price 系实例进入主 pane")
        LKanvasChart.dispose(chart)
    }
}
