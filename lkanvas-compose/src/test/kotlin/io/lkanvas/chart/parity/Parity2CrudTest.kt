package io.lkanvas.chart.parity

import io.lkanvas.compose.state.ChartState
import io.lkanvas.chart.DataLoader
import io.lkanvas.chart.DataLoadType
import io.lkanvas.chart.DataLoadMore
import io.lkanvas.chart.GetBarsParams
import io.lkanvas.chart.SubscribeBarParams
import io.lkanvas.chart.UnsubscribeBarParams
import io.lkanvas.chart.LKanvasChart
import io.lkanvas.chart.OverlayCreate
import io.lkanvas.chart.Period
import io.lkanvas.chart.PeriodType
import io.lkanvas.chart.OverlayFilter
import io.lkanvas.chart.OverlayOverride
import io.lkanvas.chart.SymbolInfo
import io.lkanvas.core.data.Candle
import io.lkanvas.core.overlay.OverlayPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * parity-2（对齐批次 2b 出口）：上游官方文档「指标」「覆盖物」示例迁移。
 * 来源：https://klinecharts.com/en-US/guide/indicator + /guide/overlay（v10 文档）。
 * 迁移规则同 parity-1：仅替换初始化。
 */
class Parity2CrudTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newChart(): LKanvasChart {
        val chart = LKanvasChart.init()
        chart.setSymbol(SymbolInfo("DEMO"))
        chart.setPeriod(Period(PeriodType.MINUTE))
        chart.setDataLoader(object : DataLoader {
            override fun getBars(params: GetBarsParams) {
                if (params.type == DataLoadType.INIT) {
                    params.callback(
                        (0 until 200).map {
                            val close = 100.0 + it * 0.1
                            Candle(it.toLong(), close, close + 1, close - 1, close, 1000.0)
                        },
                        DataLoadMore.NONE,
                    )
                }
            }
        })
        return chart
    }

    /** 上游文档「创建指标」：chart.createIndicator('MA', { calcParams: [5] })。 */
    @Test
    fun `parity2_创建指标文档示例`() {
        val chart = newChart()
        // 上游：const id = chart.createIndicator({ name: 'MA', calcParams: [5] })
        val id = chart.createIndicator("MA", calcParams = listOf(5.0))
        assertNotNull(id, "返回 instanceId")
        // 上游：chart.getIndicators() 可查到 { name: 'MA', calcParams: [5] }
        val ma = chart.getIndicators(ChartState.IndicatorFilter(name = "MA"))
            .firstOrNull { it.id == id }
        assertNotNull(ma)
        assertEquals(listOf(5.0), ma.params)
        // 上游文档：isStack 参数（默认叠加主图）——实例存在即语义成立
        // 上游：chart.removeIndicator({ id })
        assertTrue(chart.removeIndicator(ChartState.IndicatorFilter(id = id)))
        assertEquals(null, chart.getIndicatorByInstanceId(id))
    }

    /** 上游文档「创建覆盖物」：chart.createOverlay({ name: 'segment', points: [...] })。 */
    @Test
    fun `parity2_创建覆盖物文档示例`() {
        val chart = newChart()
        // 上游：const overlayId = chart.createOverlay({
        //   name: 'segment',
        //   points: [{ timestamp: ..., value: ... }, { timestamp: ..., value: ... }]
        // })
        val overlayId = chart.createOverlay(
            OverlayCreate(
                name = "segment",
                points = listOf(
                    OverlayPoint(timestamp = 50, value = 110.0),
                    OverlayPoint(timestamp = 150, value = 95.0),
                ),
            ),
        )
        assertNotNull(overlayId)
        // 上游：chart.getOverlays() 可查
        val overlays = chart.getOverlays(OverlayFilter(name = "segment"))
        assertEquals(1, overlays.size)
        assertEquals(2, overlays[0].points.size)
        // 上游：chart.overrideOverlay({ id, extendData: {...} })
        assertTrue(
            chart.overrideOverlay(
                overlayId!!,
                OverlayOverride(extendData = mapOf("key" to "value"), lock = true),
            ),
        )
        // 上游：chart.removeOverlay({ id })
        assertTrue(chart.removeOverlay(OverlayFilter(id = overlayId)))
        assertEquals(0, chart.getOverlays(OverlayFilter(id = overlayId!!)).size)
    }
}
