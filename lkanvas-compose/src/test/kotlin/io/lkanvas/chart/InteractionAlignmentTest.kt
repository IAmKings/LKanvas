package io.lkanvas.chart

import io.lkanvas.compose.state.ChartState
import io.lkanvas.core.data.Candle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.*

class InteractionAlignmentTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private fun drain() = dispatcher.scheduler.advanceUntilIdle()

    private fun chart(): LKanvasChart {
        val chart = LKanvasChart.init()
        chart.setSymbol(SymbolInfo("ALIGN"))
        chart.setPeriod(Period.ofMinutes(1))
        chart.setDataLoader(object : DataLoader {
            override fun getBars(params: GetBarsParams) {
                params.callback((0 until 600).map { Candle(it * 60000L, 150.0, 200.0, 100.0, 150.0, 1000.0) }, DataLoadMore.NONE)
            }
        })
        drain()
        chart.state.onLayout(800f, ChartState.PaneHeights(200f, 100f, 100f, 20f))
        drain()
        return chart
    }

    @Test fun positiveContentDistanceRevealsHistoryAndReportsClampedDistance() {
        val chart = chart()
        try {
            val vp = chart.state.viewport
            vp.restoreAnchor(300.0, 8.0, 600)
            val x = vp.candleCenterX(350.0)
            val distances = mutableListOf<Double>()
            chart.state.onScrolled = { distances.add(it) }
            chart.scrollByDistance(80.0)
            assertEquals(290.0, vp.scrollIndex)
            assertEquals(x + 80.0, vp.candleCenterX(350.0))
            chart.scrollByDistance(-80.0)
            assertEquals(300.0, vp.scrollIndex)
            assertEquals(listOf(80.0, -80.0), distances)
            chart.scrollByDistance(1e6)
            assertEquals(2400.0, distances.last())
            val size = distances.size
            chart.scrollByDistance(80.0)
            assertEquals(size, distances.size, "no onScroll for a clamped no-op")
            chart.scrollToRealTime(160)
            drain()
            assertTrue(vp.isAtLatest(600))
            chart.scrollToDataIndex(350, 160)
            drain()
            assertEquals(vp.contentWidth - vp.barSpacing / 2, vp.candleCenterX(350.0), 0.001)
        } finally { LKanvasChart.dispose(chart) }
    }

    @Test fun defaultAndOverriddenGapAffectProjectionAndRemainInvertible() {
        val chart = chart()
        try {
            val state = chart.state
            assertEquals(state.paneHeightsPx.main * 20.0 / 130, state.mainPaneYOf(200.0)!!, 0.001)
            chart.overrideYAxis(YAxisOverride(id = "main", gap = AxisGap(0.5, 0.2)))
            assertEquals(state.paneHeightsPx.main * 50.0 / 170, state.mainPaneYOf(200.0)!!, 0.001)
            for (reverse in listOf(false, true)) {
                chart.overrideYAxis(YAxisOverride(id = "main", reverse = reverse, gap = AxisGap(40.0, 20.0)))
                for (v in listOf(100.0, 150.0, 200.0)) {
                    assertEquals(v, state.mainPaneValueAt(state.mainPaneYOf(v)!!.toFloat())!!, 0.001)
                }
            }
        } finally { LKanvasChart.dispose(chart) }
    }

    @Test fun snappedFloatCoordinatesKeepTheSelectedCandleAtFractionalSpacing() {
        val chart = chart()
        try {
            val state = chart.state
            state.viewport.zoomBy(1.1, 514.0, 600)
            state.viewport.restoreAnchor(320.123456, 8.8, 600)
            for (index in 330..400) {
                state.showCrosshairAt(state.viewport.candleCenterX(index.toDouble()).toFloat(), 100f)
                assertEquals(index * 60000L, state.legendData()!!.candle.timestamp)
            }
        } finally { LKanvasChart.dispose(chart) }
    }

    @Test fun marketOrPeriodResetClearsTheRetainedCrosshair() {
        val chart = chart()
        try {
            chart.state.showCrosshairAt(200f, 100f)
            assertNotNull(chart.state.crosshairPosition)
            chart.setPeriod(Period.ofMinutes(5))
            drain()
            assertNull(chart.state.crosshairPosition)
            assertNull(chart.state.crosshairPaneId)
        } finally { LKanvasChart.dispose(chart) }
    }

    @Test fun updatedLayoutDefaultsResizeSubPanesWithoutDiscardingExplicitOverrides() {
        val chart = chart()
        try {
            chart.createIndicator("VOL", paneId = "vol")
            chart.createIndicator("MACD", paneId = "macd")
            chart.state.onLayout(800f, ChartState.PaneHeights(1000f, 200f, 200f, 60f))
            chart.state.onLayout(800f, ChartState.PaneHeights(1000f, 400f, 400f, 60f))
            assertEquals(400f, chart.state.paneHeightsPx.vol)
            assertEquals(400f, chart.state.paneHeightsPx.macd)
            chart.setPaneOptions(PaneOptions(id = "vol", height = 300.0))
            chart.state.onLayout(800f, ChartState.PaneHeights(1000f, 500f, 500f, 60f))
            assertEquals(300f, chart.state.paneHeightsPx.vol)
        } finally { LKanvasChart.dispose(chart) }
    }

    @Test fun historicalMovingAverageIsIncludedAndVisibilityInvalidatesTheRange() {
        val chart = chart()
        try {
            chart.setDataLoader(object : DataLoader {
                override fun getBars(params: GetBarsParams) {
                    params.callback((0 until 600).map {
                        val price = if (it < 60) 1000.0 else 100.0
                        Candle(it * 60000L, price, price + 1, price - 1, price, 1000.0)
                    }, DataLoadMore.NONE)
                }
            })
            drain()
            val id = chart.createIndicator("MA", paneId = "main", calcParams = listOf(60.0))!!
            drain()
            chart.state.viewport.restoreAnchor(60.0, 8.0, 600)
            assertEquals(985.0, chart.state.mainPaneRange()!!.second, 0.001)
            assertTrue(chart.state.mainPaneYOf(985.0)!! >= 0)
            chart.overrideIndicator(id, visible = false)
            drain()
            assertEquals(101.0, chart.state.mainPaneRange()!!.second)
        } finally { LKanvasChart.dispose(chart) }
    }
}
