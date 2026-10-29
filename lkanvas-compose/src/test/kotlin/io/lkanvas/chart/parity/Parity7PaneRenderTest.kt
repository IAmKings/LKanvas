package io.lkanvas.chart.parity

import io.lkanvas.chart.LKanvasChart
import io.lkanvas.chart.Period
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
 * parity-7：pane 渲染收敛差分（SLA-003 余项验收）。
 * - `createIndicator("RSI")` 上游缺省语义：自动生成新指标 pane 并进入该 pane 渲染集合；
 * - 主 pane 不再按 series 过滤：NORMAL 系指标显式叠加 main 时可见（上游 candle pane 叠加语义）；
 * - vol/macd pane 接受任意绑定实例（pane 绑定优先）。
 */
class Parity7PaneRenderTest {

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

    private fun newChartWithData(): LKanvasChart {
        val chart = LKanvasChart.init()
        chart.setSymbol(SymbolInfo("A"))
        chart.setPeriod(Period.ofMinutes(1))
        chart.setDataLoader(object : io.lkanvas.chart.DataLoader {
            override fun getBars(params: io.lkanvas.chart.GetBarsParams) {
                params.callback(
                    (0 until 120).map { Candle(it * 60_000L, 100.0 + it, 101.0 + it, 99.0 + it, 100.5 + it, 1000.0 + it) },
                    io.lkanvas.chart.DataLoadMore.NONE,
                )
            }
        })
        drain()
        return chart
    }

    @Test
    fun `RSI缺省创建自动生成新pane并进入其渲染集合`() {
        val chart = newChartWithData()
        val id = chart.createIndicator("RSI")!!
        drain()
        val inst = chart.getIndicatorByInstanceId(id)!!
        val paneId = inst.paneId
        assertTrue(paneId.startsWith("pane_indicator_"), "上游缺省：自动生成指标 pane id")
        // 动态 pane 已建（extraPanes 含该 pane）且渲染集合（drawExtraPane 数据源）含实例
        assertTrue(chart.state.extraPanes.value.contains(paneId), "自动建 pane")
        assertEquals(
            listOf(id),
            chart.state.getIndicators(io.lkanvas.compose.state.ChartState.IndicatorFilter(paneId = paneId)).map { it.id },
            "RSI 进入新 pane 渲染集合（审计发现 3：普通副图指标通用渲染路径）",
        )
        // 有计算结果（渲染可见）
        assertTrue(inst.state != null && inst.error == null, "RSI 计算就绪")
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `NORMAL系指标显式叠加main时进入主pane渲染集合`() {
        val chart = newChartWithData()
        val before = chart.state.mainPaneIndicatorResults().size
        // 上游语义：isStack=true 显式叠加 candle pane；series==NORMAL 不再被过滤
        val id = chart.createIndicator("KDJ", paneId = "main", isStack = true)!!
        drain()
        val results = chart.state.mainPaneIndicatorResults()
        assertEquals(before + 1, results.size, "NORMAL 系实例进入主 pane")
        assertTrue(results.any { it.id == id }, "KDJ 实例在主 pane 渲染集合")
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `任意实例可绑定vol与macd的pane渲染集合`() {
        val chart = newChartWithData()
        // 上游缺省不预置实例：宿主显式创建 VOL/MACD 于内置 pane
        val volId = chart.createIndicator("VOL", paneId = "vol")!!
        val macdId = chart.createIndicator("MACD", paneId = "macd")!!
        drain()
        assertEquals(volId, chart.state.volumePaneResult()?.let { r ->
            chart.getIndicators(io.lkanvas.compose.state.ChartState.IndicatorFilter(paneId = "vol"))
                .firstOrNull { it.state === r }?.id
        }, "vol pane 渲染绑定实例")
        assertEquals(macdId, chart.state.macdPaneResult()?.let { r ->
            chart.getIndicators(io.lkanvas.compose.state.ChartState.IndicatorFilter(paneId = "macd"))
                .firstOrNull { it.state === r }?.id
        }, "macd pane 渲染绑定实例")
        LKanvasChart.dispose(chart)
    }
}
