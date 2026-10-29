package io.lkanvas.chart.parity

import io.lkanvas.chart.LKanvasChart
import io.lkanvas.chart.Period
import io.lkanvas.chart.SymbolInfo
import io.lkanvas.chart.YAxisOverride
import io.lkanvas.chart.YAxisFilter
import io.lkanvas.core.data.Candle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * parity-6：轴与坐标转换差分（SLA-010/012 验收）。
 * - log 轴下 convertToPixel 与绘制映射逐点一致（审计发现 12 收口）；
 * - overrideYAxis(main, reverse=true) 后坐标实际变化且 convert 同步（审计发现 10 收口）；
 * - ConvertFilter（yAxisId/absolute）；createYAxis pane 存在性校验。
 */
class Parity6AxisConvertTest {

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

    private fun newChartWithLog(): LKanvasChart {
        val chart = LKanvasChart.init()
        chart.setSymbol(SymbolInfo("A"))
        chart.setPeriod(Period.ofMinutes(1))
        chart.setDataLoader(object : io.lkanvas.chart.DataLoader {
            override fun getBars(params: io.lkanvas.chart.GetBarsParams) {
                // 价格跨数量级（log 变换可辨）
                params.callback(
                    (0 until 80).map {
                        Candle(it * 60_000L, 1.0 + it * it.toDouble(), 2.0 + it * it, 0.5, 1.5 + it * it, 100.0)
                    },
                    io.lkanvas.chart.DataLoadMore.NONE,
                )
            }
        })
        drain()
        chart.state.mainAxisSpec = io.lkanvas.core.axis.YAxisSpec.Logarithm
        chart.state.notifyViewportChanged()
        drain()
        return chart
    }

    // ---------- SLA-012：log 轴 convert 与绘制一致性 ----------

    @Test
    fun `log轴下convertToPixel与绘制映射逐点一致`() {
        val chart = newChartWithLog()
        val candles = chart.getDataList()
        // 与绘制同一来源（mainPaneRange 同值域）：非空布局需要 paneHeightsPx>0
        // 无 Compose 挂载环境：经布局回填入口设置 pane 高度
        chart.state.onLayout(600f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        drain()
        // convert 与状态映射必须一致（两者已是同一函数：mainPaneYOf）
        val y1 = chart.state.mainPaneYOf(50.0)
        val y2 = chart.state.mainPaneYOf(5000.0)
        assertNotNull(y1)
        assertNotNull(y2)
        // log 轴可辨识：值 50 与 5000 的 y 差距显著小于线性（log 压缩）
        assertTrue(abs(y1 - y2) > 1.0, "log 映射区分数量级")
        // 往返一致（y→value→y）
        val v = chart.state.mainPaneValueAt(y1.toFloat())
        assertNotNull(v)
        val y3 = chart.state.mainPaneYOf(v)
        assertTrue(abs(y3!! - y1) < 1e-2, "log 轴 y→value→y 往返一致（Float 像素精度）")
        // 门面 convert 与状态映射同源
        val pts = chart.convertToPixel(listOf(io.lkanvas.core.overlay.OverlayPoint(candles[40].timestamp, 50.0)))
        assertEquals(y1, pts[0].second, 1e-9, "convert 与绘制映射同源")
        LKanvasChart.dispose(chart)
    }

    // ---------- SLA-010：主轴 reverse 进入执行链 ----------

    @Test
    fun `overrideYAxis主轴reverse后convert与状态映射坐标变化`() {
        val chart = newChartWithLog()
        // 无 Compose 挂载环境：经布局回填入口设置 pane 高度
        chart.state.onLayout(600f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        drain()
        val before = chart.state.mainPaneYOf(100.0)!!
        // 主轴覆写 reverse=true（此前只改记录：坐标不变，审计发现 10 复现）
        chart.overrideYAxis(YAxisOverride(paneId = "main", id = "main", reverse = true))
        drain()
        assertTrue(chart.state.mainAxisReverse, "主轴 reverse 同步到统一映射")
        val after = chart.state.mainPaneYOf(100.0)!!
        assertTrue(abs(before - after) > 1.0, "reverse 后坐标实际变化")
        // convert 同步（同一映射）
        val pt = chart.convertToPixel(listOf(io.lkanvas.core.overlay.OverlayPoint(chart.getDataList()[10].timestamp, 100.0)))
        assertEquals(after, pt[0].second, 1e-9, "convert 感知 reverse")
        // 恢复
        chart.overrideYAxis(YAxisOverride(paneId = "main", id = "main", reverse = false))
        assertEquals(before, chart.state.mainPaneYOf(100.0)!!, 1e-9, "reverse=false 恢复")
        LKanvasChart.dispose(chart)
    }

    // ---------- SLA-010：ConvertFilter 与 pane 校验 ----------

    @Test
    fun `absolute只控制y参考系且timestamp恒为时间戳（R05）`() {
        val chart = newChartWithLog()
        val candles = chart.getDataList()
        chart.state.onLayout(600f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        drain()
        // 相同时间戳：absolute 切换只改 y（±pane top），不改 x（复审 R05 复现收口）
        val rel = chart.convertToPixel(
            listOf(io.lkanvas.core.overlay.OverlayPoint(timestamp = candles[40].timestamp, value = 5000.0)),
            LKanvasChart.ConvertFilter(absolute = false),
        )[0]
        val abs = chart.convertToPixel(
            listOf(io.lkanvas.core.overlay.OverlayPoint(timestamp = candles[40].timestamp, value = 5000.0)),
            LKanvasChart.ConvertFilter(absolute = true),
        )[0]
        assertEquals(rel.first, abs.first, 1e-9, "absolute 不改变 x（timestamp 恒为时间戳）")
        val mainTop = chart.state.paneBounds("main")!!.first.toDouble()
        assertEquals(rel.second + mainTop, abs.second, 1e-9, "absolute = 相对 y + pane top")
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `paneId参与转换且不同pane坐标不同（R05）`() {
        val chart = newChartWithLog()
        chart.state.onLayout(600f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        drain()
        // 创建 VOL 实例于 vol pane（有数据映射）
        chart.createIndicator("VOL", paneId = "vol")
        drain()
        val volValue = chart.getDataList().first().volume ?: 100.0
        val mainPt = chart.convertToPixel(
            listOf(io.lkanvas.core.overlay.OverlayPoint(timestamp = chart.getDataList()[5].timestamp, value = volValue)),
            LKanvasChart.ConvertFilter(paneId = "main"),
        )[0]
        val volPt = chart.convertToPixel(
            listOf(io.lkanvas.core.overlay.OverlayPoint(timestamp = chart.getDataList()[5].timestamp, value = volValue)),
            LKanvasChart.ConvertFilter(paneId = "vol"),
        )[0]
        assertTrue(kotlin.math.abs(mainPt.second - volPt.second) > 1.0, "main 与 vol pane 转换不同（复审 R05 复现收口）")
        assertEquals(mainPt.first, volPt.first, 1e-9, "x 与 pane 无关")
        // absolute=true 的 vol 坐标 = 相对 + vol pane top
        val volAbs = chart.convertToPixel(
            listOf(io.lkanvas.core.overlay.OverlayPoint(timestamp = chart.getDataList()[5].timestamp, value = volValue)),
            LKanvasChart.ConvertFilter(paneId = "vol", absolute = true),
        )[0]
        val volTop = chart.state.paneBounds("vol")!!.first.toDouble()
        assertEquals(volPt.second + volTop, volAbs.second, 1e-9, "absolute=vol top 偏移")
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `convertFromPixel不做磁吸且支持absolute逆变换`() {
        val chart = newChartWithLog()
        chart.state.onLayout(600f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        drain()
        val candles = chart.getDataList()
        // convertFromPixel 不做磁吸（STRONG 模式下 overlay 输入才吸附）
        chart.overlayMode = io.lkanvas.chart.OverlayMode.STRONG_MAGNET
        val raw = chart.convertFromPixel(listOf(Pair(chart.state.viewport.candleCenterX(10.0), 200.0)))
        assertTrue(raw[0].timestamp == candles[10].timestamp, "convertFromPixel 锚定索引")
        // absolute 逆变换：to(+top) 后 from(-top) 往返
        val pt = chart.convertToPixel(
            listOf(io.lkanvas.core.overlay.OverlayPoint(timestamp = candles[20].timestamp, value = 100.0)),
            LKanvasChart.ConvertFilter(absolute = true),
        )[0]
        val back = chart.convertFromPixel(listOf(pt), LKanvasChart.ConvertFilter(absolute = true))
        assertEquals(100.0, back[0].value, 1e-4, "absolute 往返一致（Float 像素精度）")
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `createYAxis校验目标pane存在`() {
        val chart = newChartWithLog()
        assertNull(chart.createYAxis(YAxisOverride(id = "ax_no", paneId = "no_such_pane")), "pane 不存在返回 null（上游 getDrawPaneById 语义）")
        assertNotNull(chart.createYAxis(YAxisOverride(id = "ax_main", paneId = "main")), "main pane 有效")
        LKanvasChart.dispose(chart)
    }
}
