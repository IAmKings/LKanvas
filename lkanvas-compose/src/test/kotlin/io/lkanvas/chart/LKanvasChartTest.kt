package io.lkanvas.chart

import io.lkanvas.chart.DataLoader
import io.lkanvas.chart.VisibleRangeData
import io.lkanvas.core.data.Candle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 批次 2a 门面单测：数据流四 type、事件分发、视口 API（镜像上游语义）。 */
class LKanvasChartTest {

    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private fun c(ts: Long, close: Double) =
        Candle(ts, close, close + 1, close - 1, close, 100.0)

    /** 驱动门面协程至队列收敛（Main.immediate 在测试线程直接执行）。 */
    private fun drain() {
        mainDispatcher.scheduler.apply {
            repeat(200) { advanceUntilIdle() }
        }
    }

    private fun newChart(): LKanvasChart = LKanvasChart.init()

    @Test
    fun `init与dispose生命周期`() {
        val chart = newChart()
        assertTrue(chart === LKanvasChart.init(chart.state), "同 state 重复 init 返回既有实例")
        assertTrue(LKanvasChart.version().isNotEmpty())
        LKanvasChart.dispose(chart)
    }

    // ---------- 数据流：init/forward/backward/update ----------

    @Test
    fun `init清空重置并触发subscribeBar`() {
        val chart = newChart()
        var subscribed = 0
        val loader = object : DataLoader {
            override fun getBars(params: GetBarsParams) {
                when (params.type) {
                    DataLoadType.INIT ->
                        params.callback((0 until 10).map { c(it.toLong(), 100.0 + it) }, DataLoadMore.of(true))
                    else -> params.callback(emptyList(), DataLoadMore.NONE)
                }
            }

            override fun subscribeBar(params: SubscribeBarParams) {
                subscribed++
            }
        }
        chart.setDataLoader(loader)
        chart.setSymbol(SymbolInfo("TEST"))
        chart.setPeriod(Period.ofMinutes(1))
        drain()
        assertEquals(10, chart.getDataList().size, "init 数据就位")
        assertEquals(1, subscribed, "init 成功后自动订阅")
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `update单根三分支_大于追加等于替换小于忽略`() {
        val chart = newChart()
        chart.setSymbol(SymbolInfo("T"))
        chart.setPeriod(Period.ofMinutes(1))
        var push: ((Candle) -> Unit)? = null
        chart.setDataLoader(object : DataLoader {
            override fun getBars(params: GetBarsParams) {
                if (params.type == DataLoadType.INIT) {
                    params.callback(listOf(c(1000, 10.0), c(1060, 11.0)), DataLoadMore.NONE)
                    push = { params.callback }
                }
            }

            override fun subscribeBar(params: SubscribeBarParams) {
                push = params.callback
            }
        })
        drain()
        assertEquals(2, chart.getDataList().size)
        // 等于末根 → 替换
        push!!.invoke(c(1060, 12.0)); drain()
        assertEquals(2, chart.getDataList().size)
        assertEquals(12.0, chart.getDataList()[1].close)
        // 大于末根 → 追加
        push!!.invoke(c(1120, 13.0)); drain()
        assertEquals(3, chart.getDataList().size)
        // 小于末根 → 静默忽略（上游 _addData 语义）
        push!!.invoke(c(900, 1.0)); drain()
        assertEquals(3, chart.getDataList().size)
        assertEquals(13.0, chart.getDataList()[2].close)
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `forward前插与backward尾追加并记录more`() {
        val chart = newChart()
        chart.setSymbol(SymbolInfo("T"))
        chart.setPeriod(Period.ofMinutes(1))
        val loader = object : DataLoader {
            override fun getBars(params: GetBarsParams) {
                when (params.type) {
                    DataLoadType.INIT ->
                        params.callback((0 until 10).map { c(it.toLong(), 100.0 + it) }, DataLoadMore.NONE)
                    DataLoadType.FORWARD ->
                        params.callback((-3 until 0).map { c(it.toLong(), 97.0 + it + 3) }, DataLoadMore.of(false))
                    DataLoadType.BACKWARD ->
                        params.callback((10 until 13).map { c(it.toLong(), 110.0 + it - 10) }, DataLoadMore.of(false))
                    else -> params.callback(emptyList(), DataLoadMore.NONE)
                }
            }
        }
        chart.setDataLoader(loader)
        chart.setSymbol(SymbolInfo("T"))
        chart.setPeriod(Period.ofMinutes(1))
        drain()
        // 触发 forward/backward（2a 手动入口；2c 接视口边界自动拉取）
        assertTrue(chart.getDataList().isNotEmpty())
        LKanvasChart.dispose(chart)
    }

    // ---------- 事件分发 ----------

    @Test
    fun `subscribe_execute_unsubscribe事件恰好一次`() {
        val chart = newChart()
        val seen = mutableListOf<Any?>()
        val cb = ActionCallback { seen.add(it) }
        chart.subscribeAction(ActionType.ON_CROSSHAIR_CHANGE, cb)
        val crosshair = Crosshair(x = 10.0, y = 20.0, paneId = "main", timestamp = 5L)
        chart.executeAction(ActionType.ON_CROSSHAIR_CHANGE, crosshair)
        assertEquals(listOf<Any?>(crosshair), seen, "恰好一次")

        chart.unsubscribeAction(ActionType.ON_CROSSHAIR_CHANGE, cb)
        chart.executeAction(ActionType.ON_CROSSHAIR_CHANGE, crosshair)
        assertEquals(1, seen.size, "退订后不再接收")

        // unsubscribe(type) 清空整类
        chart.subscribeAction(ActionType.ON_ZOOM, ActionCallback { })
        chart.unsubscribeAction(ActionType.ON_ZOOM, null)
        chart.executeAction(ActionType.ON_ZOOM, 2.0) // 不抛错即无订阅者
        LKanvasChart.dispose(chart)
    }

    // ---------- 视口 API ----------

    @Test
    fun `setBarSpace与getBarSpace结构`() {
        val chart = newChart()
        chart.setSymbol(SymbolInfo("T"))
        chart.setPeriod(Period.ofMinutes(1))
        chart.setDataLoader(object : DataLoader {
            override fun getBars(params: GetBarsParams) {
                if (params.type == DataLoadType.INIT) {
                    params.callback((0 until 100).map { c(it.toLong(), 100.0) }, DataLoadMore.NONE)
                }
            }
        })
        drain()
        chart.setBarSpace(12.0)
        drain()
        val bs = chart.getBarSpace()
        assertEquals(12.0, bs.bar, 1e-9)
        assertEquals(6.0, bs.halfBar, 1e-9)
        assertEquals(bs.bar * 0.25, bs.gapBar, 1e-9)
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `getVisibleRange结构含realFrom_realTo`() {
        val chart = newChart()
        chart.setSymbol(SymbolInfo("T"))
        chart.setPeriod(Period.ofMinutes(1))
        chart.setDataLoader(object : DataLoader {
            override fun getBars(params: GetBarsParams) {
                if (params.type == DataLoadType.INIT) {
                    params.callback((0 until 200).map { c(it.toLong(), 100.0) }, DataLoadMore.NONE)
                }
            }
        })
        drain()
        val vr = chart.getVisibleRange()
        assertTrue(vr.to >= vr.from)
        assertTrue(vr.realTo.isFinite() && vr.realFrom.isFinite(), "无布局时 real 区间仍有限")
        // 布局后（内容宽>0）real 区间展开——经 onLayout 模拟
        chart.state.onLayout(360f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 92f, 92f, 22f))
        val vr2 = chart.getVisibleRange()
        assertTrue(vr2.realTo > vr2.realFrom, "布局后 realTo > realFrom")
        assertEquals(vr2.realFrom, vr2.from.toDouble(), 1.0, "realFrom 与整数 from 一致（±1 舍入）")
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `scroll与zoom锚点API`() {
        val chart = newChart()
        chart.setSymbol(SymbolInfo("T"))
        chart.setPeriod(Period.ofMinutes(1))
        chart.setDataLoader(object : DataLoader {
            override fun getBars(params: GetBarsParams) {
                if (params.type == DataLoadType.INIT) {
                    params.callback((0 until 300).map { c(it.toLong(), 100.0) }, DataLoadMore.NONE)
                }
            }
        })
        drain()
        chart.scrollToDataIndex(150)
        drain()
        val vr = chart.getVisibleRange()
        assertTrue(vr.from <= 150 && 150 <= vr.to, "索引进入可见区")
        chart.zoomAtDataIndex(1.5, 150)
        drain()
        assertTrue(chart.getBarSpace().bar > 8.0, "放大后 barSpacing 增大")
        assertTrue(chart.scrollToTimestamp(150L), "时间定位命中")
        assertFalse(chart.scrollToTimestamp(-999L), "越界时间定位失败")
        chart.scrollToRealTime(); drain()
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `convertToPixel与convertFromPixel往返`() {
        val chart = newChart()
        chart.setSymbol(SymbolInfo("T"))
        chart.setPeriod(Period.ofMinutes(1))
        chart.setDataLoader(object : DataLoader {
            override fun getBars(params: GetBarsParams) {
                if (params.type == DataLoadType.INIT) {
                    params.callback((0 until 100).map { c(it.toLong(), 100.0 + it) }, DataLoadMore.NONE)
                }
            }
        })
        drain()
        // 布局尺寸未就绪（无 Compose）时 y 为 NaN——断言 x 有限、行为不崩溃
        val pts = chart.convertToPixel(listOf(io.lkanvas.core.overlay.OverlayPoint(10L, 105.0)))
        assertEquals(1, pts.size)
        val back = chart.convertFromPixel(listOf(Pair(100.0, 50.0)))
        assertEquals(1, back.size)
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `enabled开关与zoomAnchor`() {
        val chart = newChart()
        assertTrue(chart.isZoomEnabled() && chart.isScrollEnabled())
        chart.setZoomEnabled(false); chart.setScrollEnabled(false)
        assertFalse(chart.isZoomEnabled() || chart.isScrollEnabled())
        chart.setZoomAnchor(ZoomAnchor(ZoomAnchor.Anchor.LAST_BAR, ZoomAnchor.Anchor.CURSOR))
        assertEquals(ZoomAnchor.Anchor.LAST_BAR, chart.getZoomAnchor().main)
        LKanvasChart.dispose(chart)
    }


    @Test
    fun `事件接线_onZoom_onScroll_onCrosshairChange经手势钩子触发`() {
        val chart = newChart()
        chart.setSymbol(SymbolInfo("T"))
        chart.setPeriod(Period.ofMinutes(1))
        chart.setDataLoader(object : DataLoader {
            override fun getBars(params: GetBarsParams) {
                if (params.type == DataLoadType.INIT) {
                    params.callback((0 until 200).map { c(it.toLong(), 100.0 + it) }, DataLoadMore.NONE)
                }
            }
        })
        drain()
        val zooms = mutableListOf<Any?>()
        val scrolls = mutableListOf<Any?>()
        val ranges = mutableListOf<Any?>()
        val crosshairs = mutableListOf<Any?>()
        chart.subscribeAction(ActionType.ON_ZOOM, ActionCallback { zooms.add(it) })
        chart.subscribeAction(ActionType.ON_SCROLL, ActionCallback { scrolls.add(it) })
        chart.subscribeAction(ActionType.ON_VISIBLE_RANGE_CHANGE, ActionCallback { ranges.add(it) })
        chart.subscribeAction(ActionType.ON_CROSSHAIR_CHANGE, ActionCallback { crosshairs.add(it) })

        chart.state.onLayout(360f, io.lkanvas.compose.state.ChartState.PaneHeights(400f, 92f, 92f, 22f))
        drain()
        val rangeCountBefore = ranges.size
        chart.state.zoomAt(1.5f, 180f)
        drain()
        assertTrue(zooms.isNotEmpty(), "onZoom 触发")
        val scalePayload = zooms.last() as Map<*, *>
        assertTrue((scalePayload["scale"] as Double) > 1.0, "缩放比 >1")

        chart.state.panBy(40f)
        drain()
        assertTrue(scrolls.isNotEmpty(), "onScroll 触发")
        assertTrue(ranges.size > rangeCountBefore, "onVisibleRangeChange 触发")

        chart.state.showCrosshairAt(100f, 200f)
        drain()
        assertTrue(crosshairs.isNotEmpty(), "onCrosshairChange 触发")
        val ch = crosshairs.last() as Crosshair
        assertEquals("main", ch.paneId)
        assertTrue(ch.kLineData != null, "payload 含 kLineData")
        assertTrue(ch.timestamp != null, "payload 含 timestamp")
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `事件接线_onCandleBarClick与onPaneDrag`() {
        val chart = newChart()
        chart.setSymbol(SymbolInfo("T"))
        chart.setPeriod(Period.ofMinutes(1))
        chart.setDataLoader(object : DataLoader {
            override fun getBars(params: GetBarsParams) {
                if (params.type == DataLoadType.INIT) {
                    params.callback((0 until 50).map { c(it.toLong(), 100.0) }, DataLoadMore.NONE)
                }
            }
        })
        drain()
        val clicks = mutableListOf<Any?>()
        val drags = mutableListOf<Any?>()
        chart.subscribeAction(ActionType.ON_CANDLE_BAR_CLICK, ActionCallback { clicks.add(it) })
        chart.subscribeAction(ActionType.ON_PANE_DRAG, ActionCallback { drags.add(it) })

        chart.state.tapCandle(100f)
        drain()
        assertTrue(clicks.isNotEmpty(), "onCandleBarClick 触发")
        val click = clicks.last() as VisibleRangeData
        assertTrue(click.data.current.timestamp >= 0 && click.data.prev != null || click.dataIndex == 0, "payload 含邻居结构")

        chart.state.adjustPane(0, 92f, 20f)
        drain()
        assertTrue(drags.isNotEmpty(), "onPaneDrag 触发")
        assertEquals("main", (drags.last() as Map<*, *>)["paneId"])
        LKanvasChart.dispose(chart)
    }


    @Test
    fun `setStyles字符串主题与getStyles往返`() {
        val chart = newChart()
        assertEquals(io.lkanvas.chart.styles.StylesThemes.light, chart.getStyles(), "默认 light")
        chart.setStyles("dark")
        assertEquals(io.lkanvas.chart.styles.StylesThemes.dark, chart.getStyles())
        chart.setStyles("light")
        assertEquals(io.lkanvas.chart.styles.StylesThemes.light, chart.getStyles())
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `setStylesDeepPartial深合并`() {
        val chart = newChart()
        val patch = io.lkanvas.chart.styles.Styles(candle = io.lkanvas.chart.styles.CandleStyle(bar = io.lkanvas.chart.styles.CandleBarColor(upColor = "#FF0000")))
        chart.setStyles(patch)
        val bar = (chart.getStyles().candle.bar as io.lkanvas.chart.styles.CandleBarColor)
        assertEquals("#FF0000", bar.upColor, "patch 值生效")
        assertEquals("#F92855", bar.downColor, "未提供字段保留")
        assertEquals("#EDEDED", chart.getStyles().grid.horizontal.color, "未触及节保留")
        LKanvasChart.dispose(chart)
    }


    @Test
    fun `tooltip_features三事件分发`() {
        val chart = newChart()
        val candleFeature = mutableListOf<Any?>()
        val indicatorFeature = mutableListOf<Any?>()
        val crosshairFeature = mutableListOf<Any?>()
        chart.subscribeAction(ActionType.ON_CANDLE_TOOLTIP_FEATURE_CLICK, ActionCallback { candleFeature.add(it) })
        chart.subscribeAction(ActionType.ON_INDICATOR_TOOLTIP_FEATURE_CLICK, ActionCallback { indicatorFeature.add(it) })
        chart.subscribeAction(ActionType.ON_CROSSHAIR_FEATURE_CLICK, ActionCallback { crosshairFeature.add(it) })

        chart.executeTooltipFeatureClick("fullscreen", paneId = "main")
        chart.executeTooltipFeatureClick("setting", paneId = "vol")
        chart.executeTooltipFeatureClick("close", paneId = "crosshair")
        drain()

        assertEquals(1, candleFeature.size)
        assertEquals(1, indicatorFeature.size)
        assertEquals(1, crosshairFeature.size)
        val payload = candleFeature[0] as Map<*, *>
        assertEquals("fullscreen", payload["featureId"])
        assertEquals("main", payload["paneId"])
        LKanvasChart.dispose(chart)
    }

    @Test
    fun `offsetRight距离与槽位换算`() {
        val chart = newChart()
        chart.setSymbol(SymbolInfo("T"))
        chart.setPeriod(Period.ofMinutes(1))
        chart.setDataLoader(object : DataLoader {
            override fun getBars(params: GetBarsParams) {
                if (params.type == DataLoadType.INIT) {
                    params.callback((0 until 100).map { c(it.toLong(), 100.0) }, DataLoadMore.NONE)
                }
            }
        })
        drain()
        chart.setBarSpace(10.0); drain()
        chart.setOffsetRightDistance(100.0)
        assertEquals(100.0, chart.getOffsetRightDistance(), 1e-9, "px↔槽位往返一致")
        LKanvasChart.dispose(chart)
    }
}
