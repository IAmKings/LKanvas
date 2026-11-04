package io.lkanvas.chart

import io.lkanvas.compose.draw.TooltipRenderer
import io.lkanvas.compose.state.ChartState
import io.lkanvas.compose.theme.ChartTheme
import io.lkanvas.chart.styles.Styles
import io.lkanvas.core.data.Candle
import io.lkanvas.core.overlay.OverlayPoint
import io.lkanvas.core.store.MarketKey
import io.lkanvas.core.viewport.Viewport
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Before
import org.junit.After
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class OtherModuleAlignmentTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private fun state(): ChartState = ChartState().also { state ->
        runBlocking { state.reset(MarketKey("OTHER", "1m"), (0 until 600).map {
            Candle(it * 60000L, 140.0, 200.0, 100.0, 160.0, 1000.0 + it)
        }) }
        state.onLayout(800f, ChartState.PaneHeights(600f, 100f, 100f, 20f))
        state.viewport.restoreAnchor(100.0, 8.0, 600)
    }

    @Test fun timestampLookupUsesNearestWithEarlierTieAndClampedEnds() {
        val data = listOf(Candle(0, 1.0, 1.0, 1.0, 1.0), Candle(60000, 1.0, 1.0, 1.0, 1.0))
        for ((ts, idx) in listOf(-1L to 0, 0L to 0, 29999L to 0, 30000L to 0, 30001L to 1, 59000L to 1, 60001L to 1)) {
            assertEquals(idx, Viewport.indexOfTimestamp(ts, data), "timestamp=$ts")
        }
        assertNull(Viewport.indexOfTimestamp(0, emptyList()))
    }

    @Test fun brushRetainsSubBarPrecisionInBothDirections() {
        val state = state()
        state.currentTool = "brush"
        state.beginCreateOverlay()
        val a = state.overlayPointFromPx(404f, 250f)!!
        val b = state.overlayPointFromPx(406f, 250f)!!
        assertEquals(15000L, b.timestamp - a.timestamp)
        val px = state.overlayPointToPx(b, "main", continuous = true)!!
        assertEquals(406f, px.x, 0.001f)
        state.cancelCreateOverlay()
        state.currentTool = "segment"
        state.beginCreateOverlay()
        assertEquals(state.overlayPointFromPx(404f, 250f)!!.timestamp, state.overlayPointFromPx(406f, 250f)!!.timestamp)
    }

    @Test fun mainIndicatorLegendUsesFigureOrderTitlesAndPaletteAcrossInstances() {
        val state = state()
        state.createIndicator("MA", paneId = "main", isStack = true)
        state.createIndicator("EMA", paneId = "main", isStack = true)
        dispatcher.scheduler.advanceUntilIdle()
        val theme = ChartTheme()
        val groups = TooltipRenderer.buildMainIndicatorGroups(state, 599, Styles(), theme, object : TooltipRenderer.NumericText {
            override fun price(v: Double) = v.toString()
            override fun volume(v: Double) = v.toString()
        })
        assertEquals(listOf("MA5: ", "MA10: ", "MA30: ", "MA60: "), groups[0].legends.map { it.title })
        assertEquals(theme.linePalette[0], groups[0].legends[0].valueColor)
        assertEquals(theme.linePalette[0], groups[1].legends[0].valueColor)
    }
    @Test fun instanceMagnetModeAndSensitivityAreConsumedWithoutAffectingPeers() {
        val state = state()
        val chart = LKanvasChart.init(state)
        try {
            val weak = chart.createOverlay(OverlayCreate("segment", points = listOf(OverlayPoint(0, 150.0), OverlayPoint(60000, 150.0)), mode = OverlayMode.WEAK_MAGNET, modeSensitivity = 100.0))!!
            val normal = chart.createOverlay(OverlayCreate("segment", points = listOf(OverlayPoint(0, 150.0), OverlayPoint(60000, 150.0)), mode = OverlayMode.NORMAL))!!
            chart.overlayMode = OverlayMode.STRONG_MAGNET
            val highY = state.paneValueToY("main", 200.0)!!.toFloat()
            val y = highY - 20f
            assertEquals(200.0, state.overlayPointFromPx(404f, y, instanceId = weak)!!.value)
            assertTrue(state.overlayPointFromPx(404f, y, instanceId = normal)!!.value > 200.0)
            assertTrue(chart.overrideOverlay(weak, OverlayOverride(modeSensitivity = 8.0)))
            assertTrue(state.overlayPointFromPx(404f, y, instanceId = weak)!!.value > 200.0)
            assertTrue(chart.overrideOverlay(weak, OverlayOverride(mode = OverlayMode.STRONG_MAGNET)))
            assertEquals(200.0, state.overlayPointFromPx(404f, y, instanceId = weak)!!.value)
            assertEquals(OverlayMode.STRONG_MAGNET, chart.getOverlays(OverlayFilter(id = weak)).single().mode)
            assertFalse(chart.overrideOverlay(weak, OverlayOverride(modeSensitivity = Double.NaN)))
            assertNull(chart.createOverlay(OverlayCreate("segment", modeSensitivity = -1.0)))
            // 上游 weak 在 candle 范围内总吸附。
            chart.overrideOverlay(weak, OverlayOverride(mode = OverlayMode.WEAK_MAGNET))
            for ((raw, expected) in listOf(181.0 to 200.0, 149.0 to 140.0, 119.0 to 100.0)) {
                val point = state.overlayPointFromPx(404f, state.paneValueToY("main", raw)!!.toFloat(), instanceId = weak)!!
                assertEquals(expected, point.value, 0.001)
            }
        } finally { LKanvasChart.dispose(chart) }
    }

    @Test fun interpolationSurvivesPrependAndCoversIrregularTimesAndEmptyData() {
        val data = listOf(0L, 60000L, 180000L).map { Candle(it, 1.0, 2.0, 0.0, 1.0) }
        for ((idx, timestamp) in listOf(-0.5 to -30000L, 0.25 to 15000L, 1.25 to 90000L, 2.5 to 240000L)) {
            assertEquals(timestamp, Viewport.floatIndexToTimestamp(idx, data))
            assertEquals(idx, Viewport.timestampToFloatIndex(timestamp, data)!!, 1e-9)
        }
        val prepended = listOf(Candle(-60000, 1.0, 2.0, 0.0, 1.0)) + data
        assertEquals(2.25, Viewport.timestampToFloatIndex(90000, prepended))
        assertNull(Viewport.floatIndexToTimestamp(0.0, emptyList()))
        assertNull(Viewport.timestampToFloatIndex(0, emptyList()))
        assertEquals(0L, Viewport.floatIndexToTimestamp(1.5, data.take(1)))
    }

    @Test fun tooltipPatchCarriesRulesTypesOffsetsAndExplicitDefaults() {
        val chart = LKanvasChart.init(state())
        try {
            chart.setStyles(io.lkanvas.chart.styles.StylesPatch(
                indicator = io.lkanvas.chart.styles.IndicatorStylePatch(tooltip = io.lkanvas.chart.styles.IndicatorTooltipStylePatch(
                    showRule = "follow_cross", showType = "rect", offsetLeft = 31.0, offsetTop = 23.0, offsetRight = 41.0, offsetBottom = 17.0)),
            ))
            val t = chart.getStyles().indicator.tooltip
            assertEquals(listOf(31.0, 23.0, 41.0, 17.0), listOf(t.offsetLeft, t.offsetTop, t.offsetRight, t.offsetBottom))
            assertEquals("follow_cross", t.showRule)
            assertEquals("rect", t.showType)
            chart.setStyles(io.lkanvas.chart.styles.StylesPatch(indicator = io.lkanvas.chart.styles.IndicatorStylePatch(
                tooltip = io.lkanvas.chart.styles.IndicatorTooltipStylePatch(showRule = "always", showType = "standard", offsetTop = 6.0))))
            assertEquals("always", chart.getStyles().indicator.tooltip.showRule)
            assertEquals("standard", chart.getStyles().indicator.tooltip.showType)
            assertEquals(6.0, chart.getStyles().indicator.tooltip.offsetTop)
            assertEquals(31.0, chart.getStyles().indicator.tooltip.offsetLeft)
        } finally { LKanvasChart.dispose(chart) }
    }

    @Test fun barLegendConsumesTheSameDynamicStyleAndTitlesAsThePlotInEveryPane() {
        val state = state()
        for (pane in listOf("main", "vol", "macd")) {
            state.createIndicator("VOL", paneId = pane, isStack = true)
            state.createIndicator("MA", paneId = pane, isStack = true)
        }
        dispatcher.scheduler.advanceUntilIdle()
        for (pane in listOf("main", "vol", "macd")) {
            val groups = TooltipRenderer.buildIndicatorGroups(state, pane, 599, Styles(), ChartTheme())
            val volume = groups.first().legends.last()
            assertEquals("VOLUME: ", volume.title)
            assertEquals(io.lkanvas.compose.theme.StylesThemeBridge.parse("#2DC08E"), volume.valueColor)
            assertEquals(volume.valueColor, volume.titleColor)
            assertEquals(ChartTheme().linePalette[0], groups[1].legends.first().valueColor)
        }
    }

    @Test fun figureLegendsPreserveTheFormatChainAndInstancePrecision() {
        val state = state()
        runBlocking { state.reset(MarketKey("FORMAT", "1m"), (0 until 600).map { Candle(it * 60000L, 12345.0, 12346.0, 12344.0, 12345.0, 12345.0) }) }
        val chart = LKanvasChart.init(state)
        try {
            state.createIndicator("MA", paneId = "main", isStack = true)
            state.createIndicator("VOL", paneId = "vol")
            dispatcher.scheduler.advanceUntilIdle()
            chart.setThousandsSeparator(ThousandsSeparator(sign = ","))
            state.getIndicators(ChartState.IndicatorFilter(name = "MA")).single().precisionOverride = 1
            val ma = TooltipRenderer.buildIndicatorGroups(state, "main", 599, Styles(), ChartTheme()).single()
            assertEquals("12,345.0", ma.legends.first().value)
            chart.setFormatter(Formatter(formatBigNumber = { "V=$it" }))
            val volume = TooltipRenderer.buildIndicatorGroups(state, "vol", 599, Styles(), ChartTheme()).single()
            assertTrue(volume.legends.all { it.value == "V=12345.0" })
        } finally { LKanvasChart.dispose(chart) }
    }

    @Test fun priceMarkerCanUseADiagonalGapWhenBothSingleAxisMovesAreBlocked() {
        val exclusions = listOf(androidx.compose.ui.geometry.Rect(0f, 0f, 100f, 50f), androidx.compose.ui.geometry.Rect(0f, 50f, 50f, 100f))
        val rect = io.lkanvas.compose.draw.priceMarkerBounds(0f, 5f, 10f, 10f, 100f, 100f, exclusions)!!
        assertTrue(rect.left >= 50f && rect.top >= 50f)
        assertTrue(exclusions.none(rect::overlaps))
        assertNull(io.lkanvas.compose.draw.priceMarkerBounds(0f, 5f, 10f, 10f, 100f, 100f, listOf(androidx.compose.ui.geometry.Rect(0f, 0f, 100f, 100f))))
    }

}
