package io.lkanvas.chart.parity

import io.lkanvas.chart.ActionType
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
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * parity-8：视口契约与事件语义差分（SLA-011/014/015 验收）。
 * - zoomAtDataIndex(scale=1) 无位移（上游当前像素锚点；审计发现 14 收口）；
 * - scrollToDataIndex 右侧留白锚点模型（非居中）；
 * - getVisibleRange.to 钳制；左右 maxOffset 独立；rightOffset 保留小数；
 * - scroll/zoomEnabled 共用入口门禁（程序化 + 手势同源）；
 * - executeAction(ON_CROSSHAIR_CHANGE) 写状态且抑制反向广播（审计发现 15 收口）；
 * - 点击 payload 嵌套 data:{prev,current,next}。
 */
class Parity8ViewportEventsTest {

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
                    (0 until 200).map { Candle(it * 60_000L, 100.0 + it, 101.0 + it, 99.0 + it, 100.5 + it, 1000.0 + it) },
                    io.lkanvas.chart.DataLoadMore.NONE,
                )
            }
        })
        drain()
        return chart
    }

    @Test
    fun `zoomAtDataIndex_scale为1不产生位移`() {
        val chart = newChartWithData()
        chart.state.onLayout(800f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        drain()
        val xBefore = chart.state.viewport.candleCenterX(120.0)
        chart.zoomAtDataIndex(1.0, 120)
        drain()
        val xAfter = chart.state.viewport.candleCenterX(120.0)
        assertTrue(abs(xBefore - xAfter) < 0.5, "scale=1 锚点像素不变（审计发现 14 复现收口）：before=$xBefore after=$xAfter")
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `zoomAtDataIndex按当前像素锚定缩放`() {
        val chart = newChartWithData()
        chart.state.onLayout(800f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        drain()
        val xBefore = chart.state.viewport.candleCenterX(120.0)
        chart.zoomAtDataIndex(2.0, 120)
        drain()
        val xAfter = chart.state.viewport.candleCenterX(120.0)
        assertTrue(abs(xBefore - xAfter) < 0.5, "放大后锚点索引像素保持（当前像素锚点模型）")
        assertEquals(8.0 * 2, chart.state.viewport.barSpacing, 1e-9, "间距倍增（默认 8px）")
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `scrollToDataIndex将目标末端置于右边缘`() {
        val chart = newChartWithData()
        chart.state.onLayout(800f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        drain()
        val vp = chart.state.viewport
        chart.scrollToDataIndex(100)
        drain()
        // 上游 scrollToDataIndex：目标 candle 的右边缘对齐内容区右边缘
        val x = vp.candleCenterX(100.0)
        val expect = vp.contentWidth - vp.barSpacing / 2
        assertTrue(abs(x - expect) < vp.barSpacing, "目标 candle 右边缘对齐")
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `getVisibleRange_to按数据量钳制且rightOffset保留小数`() {
        val chart = newChartWithData()
        chart.state.onLayout(800f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        drain()
        chart.scrollToRealTime()
        drain()
        assertTrue(chart.getVisibleRange().to <= 200, "to 不超过数据量")
        // rightOffset 小数：10.5 根的反算距离不取整
        chart.setOffsetRightDistance(10.5 * chart.state.viewport.barSpacing)
        val back = chart.getOffsetRightDistance()
        assertTrue(abs(back - 10.5 * chart.state.viewport.barSpacing) < 1e-6, "rightOffset 距离无损（不丢小数）")
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `左右maxOffset独立设置`() {
        val chart = newChartWithData()
        chart.state.onLayout(800f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        drain()
        chart.setMaxOffsetLeftDistance(30.0 * chart.state.viewport.barSpacing)
        chart.setMaxOffsetRightDistance(60.0 * chart.state.viewport.barSpacing)
        assertEquals(30.0, chart.state.viewport.overscrollSlots, 1e-9, "左向独立")
        assertEquals(60.0, chart.state.viewport.maxOffsetRightSlots, 1e-9, "右向独立（不再共享预算）")
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `scroll与zoom开关在共用入口生效`() {
        val chart = newChartWithData()
        chart.state.onLayout(800f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        drain()
        val vp = chart.state.viewport
        val scrollBefore = vp.scrollIndex
        val barBefore = vp.barSpacing
        chart.setScrollEnabled(false)
        chart.scrollByDistance(200.0)
        drain()
        assertEquals(scrollBefore, vp.scrollIndex, "禁滚动后 scrollByDistance 不移动（审计发现 11 复现收口）")
        chart.setZoomEnabled(false)
        chart.zoomAtCoordinate(2.0)
        drain()
        assertEquals(barBefore, vp.barSpacing, "禁缩放后 zoomAtCoordinate 间距不变")
        chart.setScrollEnabled(true)
        chart.scrollByDistance(200.0)
        drain()
        assertTrue(vp.scrollIndex != scrollBefore, "恢复开关后可滚动")
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `executeAction写十字线状态且抑制反向广播`() {
        val chart = newChartWithData()
        chart.state.onLayout(800f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        drain()
        var broadcast = 0
        chart.subscribeAction(ActionType.ON_CROSSHAIR_CHANGE) { broadcast++ }
        // 手势路径（内部变化）触发订阅者
        chart.state.showCrosshairAt(100f, 100f)
        drain()
        assertTrue(broadcast >= 1, "内部十字线变化广播")
        // executeAction：写状态 + 抑制反向广播（上游 notExecuteAction 语义）
        val before = broadcast
        chart.executeAction(
            ActionType.ON_CROSSHAIR_CHANGE,
            io.lkanvas.core.common.Crosshair(x = 120.0, y = 150.0, dataIndex = 5),
        )
        drain()
        assertEquals(before, broadcast, "executeAction 不反向广播（审计发现 15 复现收口）")
        // 状态已写入
        assertTrue(chart.state.crosshairPosition != null, "十字线状态被设置")
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `点击payload为嵌套data结构`() {
        val chart = newChartWithData()
        var payload: io.lkanvas.chart.VisibleRangeData? = null
        chart.subscribeAction(ActionType.ON_CANDLE_BAR_CLICK) { payload = it as io.lkanvas.chart.VisibleRangeData }
        val candles = chart.getDataList()
        chart.state.onCandleClick?.invoke(candles[10])
        drain()
        val p = payload!!
        assertEquals(10, p.dataIndex)
        assertEquals(candles[10], p.data.current, "payload 嵌套 data.current")
        assertEquals(candles[9], p.data.prev, "payload 嵌套 data.prev")
        assertEquals(candles[11], p.data.next, "payload 嵌套 data.next")
        LKanvasChart.dispose(chart)
    }

    // ---------- 0.7.0 R10：Crosshair 语义 ----------

    @Test
    fun `executeAction_null清除十字线（R10）`() {
        val chart = newChartWithData()
        chart.state.onLayout(800f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        drain()
        chart.state.showCrosshairAt(100f, 100f)
        drain()
        assertTrue(chart.state.crosshairPosition != null, "十字线已显示")
        // 复审 R10 复现：null → 清除（上游 setCrosshair(undefined)）
        chart.executeAction(ActionType.ON_CROSSHAIR_CHANGE, null)
        drain()
        assertTrue(chart.state.crosshairPosition == null, "null 清除十字线")
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `executeAction_xOnly按x推导索引（R10）`() {
        val chart = newChartWithData()
        chart.state.onLayout(800f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        drain()
        val candles = chart.getDataList()
        // 指向第 150 根的 x（仅 x，不提供 dataIndex）——修复前落第 0 根
        val x150 = chart.state.viewport.candleCenterX(150.0)
        chart.executeAction(
            ActionType.ON_CROSSHAIR_CHANGE,
            io.lkanvas.core.common.Crosshair(x = x150, y = 200.0),
        )
        drain()
        val pos = chart.state.crosshairPosition!!
        // 十字线 x 吸附到第 150 根中心（x 推导索引；修复前忽略传入 x 落第 0 根）
        assertEquals(chart.state.viewport.candleCenterX(150.0).toFloat(), pos.x, 0.5f, "x-only 按 x 推导索引（R10）")
        LKanvasChart.dispose(chart)
    }

    // ---------- 0.7.0 R11：动画与布局接口 ----------

    @Test
    fun `scrollToDataIndex带duration动画过渡（R11）`() {
        val chart = newChartWithData()
        chart.state.onLayout(800f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        drain()
        val start = chart.state.viewport.scrollIndex
        chart.scrollToDataIndex(100, 1_000)
        // 动画中途：scrollIndex 已在移动（非瞬移；复审 R11 复现收口）
        mainDispatcher.scheduler.advanceTimeBy(48)
        drain()
        val mid = chart.state.viewport.scrollIndex
        assertTrue(mid != start, "动画中途位置已变化（非瞬移）")
        drain()
        val end = chart.state.viewport.scrollIndex
        assertTrue(end != start, "动画完成到达目标")
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `zoomAtCoordinate带duration动画缩放（R11）`() {
        val chart = newChartWithData()
        chart.state.onLayout(800f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        drain()
        val start = chart.state.viewport.barSpacing
        chart.zoomAtCoordinate(2.0, null, 1_000)
        mainDispatcher.scheduler.advanceTimeBy(48)
        drain()
        val mid = chart.state.viewport.barSpacing
        assertTrue(mid > start && mid < start * 2, "缩放动画中途（增量驱动，非一步到位）")
        drain()
        assertEquals(start * 2, chart.state.viewport.barSpacing, 0.5, "动画完成到达 2 倍")
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `LayoutOptions_barSpaceLimit与paneDragEnabled消费（R11）`() {
        val chart = newChartWithData()
        chart.state.onLayout(800f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 40f, 80f, 20f))
        drain()
        // vol/macd pane 需实例绑定才存在（R07 严格语义）
        chart.createIndicator("VOL", paneId = "vol")
        chart.createIndicator("MACD", paneId = "macd")
        drain()
        chart.setLayoutOptions(
            LKanvasChart.LayoutOptions(
                barSpaceLimit = 2.0 to 20.0,
                paneDragEnabled = mapOf("vol" to false),
            ),
        )
        chart.setBarSpace(40.0)
        assertEquals(20.0, chart.state.viewport.barSpacing, 1e-9, "barSpaceLimit.max 生效")
        chart.state.adjustPane(0, 100f, 100f)
        assertEquals(40f, chart.state.paneHeightsPx.vol, "vol 拖动被禁用（高度不变）")
        chart.state.adjustPane(1, 80f, 100f)
        assertTrue(chart.state.paneHeightsPx.macd != 80f, "macd 拖动仍可用")
        LKanvasChart.dispose(chart)
    }
}
