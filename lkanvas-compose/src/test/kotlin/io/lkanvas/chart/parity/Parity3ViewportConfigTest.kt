package io.lkanvas.chart.parity

import io.lkanvas.compose.state.ChartState
import io.lkanvas.chart.DataLoader
import io.lkanvas.chart.DecimalFold
import io.lkanvas.chart.LKanvasChart
import io.lkanvas.chart.PaneOptions
import io.lkanvas.chart.Period
import io.lkanvas.chart.SymbolInfo
import io.lkanvas.chart.ThousandsSeparator
import io.lkanvas.core.data.Candle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * parity-3（对齐批次 2c 出口）：上游文档「视口控制/样式与配置」示例迁移。
 * 来源：https://klinecharts.com/en-US/api（Chart 类视口与配置方法节）。
 * 迁移规则同 parity-1/2：仅替换初始化。
 */
class Parity3ViewportConfigTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newChart(withLayout: Boolean = true): LKanvasChart {
        val chart = LKanvasChart.init()
        chart.setSymbol(SymbolInfo("DEMO"))
        chart.setPeriod(Period(Period.PeriodType.MINUTE))
        chart.setDataLoader(object : DataLoader {
            override fun getBars(params: DataLoader.GetBarsParams) {
                if (params.type == DataLoader.DataLoadType.INIT) {
                    params.callback(
                        (0 until 300).map {
                            val close = 100.0 + it * 0.1
                            Candle(it.toLong(), close, close + 1, close - 1, close, 1000.0)
                        },
                        DataLoader.DataLoadMore.NONE,
                    )
                }
            }
        })
        if (withLayout) {
            // 模拟 Compose onLayout（无 UI 测试环境）
            chart.state.onLayout(1080f, io.lkanvas.compose.state.ChartState.PaneHeights(1626f, 276f, 276f, 60f))
        }
        return chart
    }

    /** 上游文档「setBarSpace/setOffsetRightDistance/scrollToDataIndex/zoomAtCoordinate」示例。 */
    @Test
    fun `parity3_视口控制示例`() {
        val chart = newChart()
        drainFor(chart)

        // 上游：chart.setBarSpace(10); chart.getBarSpace().bar === 10
        chart.setBarSpace(10.0)
        assertEquals(10.0, chart.getBarSpace().bar, 1e-9)

        // 上游：chart.setOffsetRightDistance(80); chart.getOffsetRightDistance() === 80
        chart.setOffsetRightDistance(80.0)
        assertEquals(80.0, chart.getOffsetRightDistance(), 1e-9)

        // 上游：chart.scrollToDataIndex(100)
        chart.scrollToDataIndex(100)
        val vr = chart.getVisibleRange()
        assertTrue(vr.from <= 100 && 100 <= vr.to, "索引进入可见区: ${vr.from}~${vr.to}")

        // 上游：chart.zoomAtCoordinate(1.5, { x: 100, y: 200 })
        val before = chart.getBarSpace().bar
        chart.zoomAtCoordinate(1.5, Pair(100.0, 200.0))
        assertEquals(before * 1.5, chart.getBarSpace().bar, 0.5, "触点缩放 1.5 倍")

        // 上游：chart.setZoomEnabled(false); chart.isZoomEnabled() === false
        chart.setZoomEnabled(false)
        assertEquals(false, chart.isZoomEnabled())
    }

    /** 上游文档「setLocale/setTimezone/setThousandsSeparator/setDecimalFold」示例。 */
    @Test
    fun `parity3_配置示例`() {
        val chart = newChart()

        // 上游：chart.setLocale('zh-CN') / 'en-US'
        chart.setLocale("zh-CN")
        assertEquals("zh-CN", chart.getLocale())
        chart.setLocale("en-US")
        assertEquals("en-US", chart.getLocale())

        // 上游：chart.setTimezone('America/New_York'); getTimezone 回读
        chart.setTimezone("America/New_York")
        assertEquals("America/New_York", chart.getTimezone())

        // 上游：chart.setThousandsSeparator({ sign: ',' })
        chart.setThousandsSeparator(ThousandsSeparator(sign = ","))
        assertEquals(",", chart.getThousandsSeparator().sign)

        // 上游：chart.setDecimalFold({ threshold: 3 })
        chart.setDecimalFold(DecimalFold(threshold = 3))
        assertEquals(3, chart.getDecimalFold().threshold)
    }

    /** 上游文档「setPaneOptions」示例：调整 pane 高度。 */
    @Test
    fun `parity3_pane高度调整`() {
        val chart = newChart()
        drainFor(chart)
        val before = chart.getPaneOptions("main").single().height!!
        // 上游：chart.setPaneOptions({ id: 'candle_pane', height: 300 })
        chart.setPaneOptions(PaneOptions(id = "main", height = before * 0.5))
        val after = chart.getPaneOptions("main").single().height!!
        assertEquals(before * 0.5, after, 1.0, "主 pane 高度减半")
        // VOL+MACD 被压缩但仍在最小高度之上（渲染区不为负）
        val vol = chart.getPaneOptions("vol").single().height!!
        val macd = chart.getPaneOptions("macd").single().height!!
        assertTrue(vol > 0 && macd > 0)
        assertTrue(before * 0.5 + vol + macd >= before + 88.0 - 2.0)
    }

    private fun drainFor(chart: LKanvasChart) {
        repeat(100) { mainDispatcher.scheduler.advanceUntilIdle(); Thread.yield() }
    }
}
