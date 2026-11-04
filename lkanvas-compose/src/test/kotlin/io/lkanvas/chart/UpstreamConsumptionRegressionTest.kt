package io.lkanvas.chart

import androidx.compose.ui.graphics.Color
import io.lkanvas.chart.styles.*
import io.lkanvas.chart.parity.TestDrawScope
import io.lkanvas.compose.draw.VisibleRange
import io.lkanvas.core.viewport.Viewport
import androidx.compose.ui.geometry.Size
import io.lkanvas.compose.draw.PaneRenderers
import io.lkanvas.compose.draw.TooltipRenderer
import io.lkanvas.compose.state.ChartState
import io.lkanvas.compose.theme.ChartTheme
import io.lkanvas.core.common.utils.Format
import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.*
import io.lkanvas.core.store.MarketKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class UpstreamConsumptionRegressionTest {
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `mixed figures count line slots independently and cycle`() {
        val plots = listOf(
            PlotDef("bar", "B", PlotType.BAR),
            PlotDef("a", "A", PlotType.LINE),
            PlotDef("b", "B", PlotType.LINE),
            PlotDef("c", "C", PlotType.LINE),
        )
        val colors = PaneRenderers.figureColors(plots, ChartTheme(), 0, listOf(Color.Blue, Color.Red))
        assertEquals(Color.Blue, colors["a"])
        assertEquals(Color.Red, colors["b"])
        assertEquals(Color.Blue, colors["c"])
    }

    private fun definition(name: String, plots: List<PlotDef>) = object : IndicatorDefinition {
        override val name = name
        override val shortName = name
        override val precision = 2
        override val calcParams = emptyList<Double>()
        override val plots = plots
        override fun calc(candles: List<Candle>, params: List<Double>) = candles.map { plots.associate { it.key to 1.0 } }
        override fun initScratch(params: List<Double>): Any? = null
        override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, index: Int): Any? = null
        override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, index: Int) = plots.associate { it.key to 1.0 }
    }

    @Test fun `each bar has its own complete three color style slot`() {
        val seen = mutableListOf<Pair<String, BarColorsContext>>()
        fun bar(key: String) = PlotDef(key, key, PlotType.BAR, styles = ContextPlotStyles { _, _, _, _, context ->
            seen += key to context
            mapOf("color" to context.upColor!!)
        })
        val def = definition("UPSTREAM_BAR_SLOTS", listOf(bar("a"), PlotDef("line", "L", PlotType.LINE), bar("b"), bar("c")))
        val styles = mapOf("bars" to listOf(
            mapOf("upColor" to "#FF0000", "downColor" to "#00FF00", "noChangeColor" to "#808080"),
            mapOf("upColor" to "#0000FF", "downColor" to "#FFFF00", "noChangeColor" to "#FFFFFF"),
        ))
        val inst = IndicatorInstance("slots", def, emptyList(), "main", "main", styles)
        val provider = PaneRenderers.styleOverrideFor(inst, ChartTheme())!!
        listOf("a", "b", "c").forEach { provider(it, 0, 1.0, emptyList()) }
        assertEquals(listOf("#FF0000", "#0000FF", "#FF0000"), seen.map { it.second.upColor })
        assertEquals(listOf("#00FF00", "#FFFF00", "#00FF00"), seen.map { it.second.downColor })
    }

    @Test fun `partial line style retains its array position`() {
        val def = definition("PARTIAL_LINE_SLOTS", listOf(
            PlotDef("a", "A", PlotType.LINE), PlotDef("b", "B", PlotType.LINE), PlotDef("c", "C", PlotType.LINE),
        ))
        val instance = IndicatorInstance("partial", def, styles = mapOf(
            "lines" to listOf(mapOf("size" to 2), mapOf("color" to "#FF0000")),
        ))
        val theme = ChartTheme(linePalette = listOf(Color.Blue, Color.Green))
        val colors = io.lkanvas.compose.draw.IndicatorStyleResolver.figureColors(instance, theme)
        assertEquals(Color.Black, colors["a"])
        assertEquals(Color.Red, colors["b"])
        assertEquals(Color.Black, colors["c"])
        val barInstance = IndicatorInstance("partial-bar", def, styles = mapOf(
            "bars" to listOf(emptyMap<String, String>(), mapOf("noChangeColor" to "#FF0000")),
        ))
        val barTheme = theme.copy(barColorsContext = BarColorsContext("#00FF00", "#00FF00", "#00FF00"))
        assertEquals("#000000", io.lkanvas.compose.draw.IndicatorStyleResolver.barColorsContext(barInstance, barTheme, 0).noChangeColor)
        assertEquals("#FF0000", io.lkanvas.compose.draw.IndicatorStyleResolver.barColorsContext(barInstance, barTheme, 1).noChangeColor)
    }

    @Test fun `plain bar uses neutral slot color for plot and legend`() {
        val def = definition("UPSTREAM_NEUTRAL_BAR", listOf(PlotDef("v", "V", PlotType.BAR)))
        LkanvasRegistry.registerIndicator(def, replace = true)
        val state = ChartState()
        runBlocking { state.reset(MarketKey("REGRESSION", "1m"), listOf(Candle(0L, 1.0, 2.0, 0.5, 1.0, 100.0))) }
        val id = state.createIndicator(def.name, paneId = "main", styles = IndicatorStyleSpec(
            barColors = IndicatorBarColors("#FF0000", "#00FF00", "#808080"),
        ))!!
        val group = TooltipRenderer.buildIndicatorGroups(state, "main", 0, Styles(), ChartTheme()).single()
        assertEquals(Color(0xFF808080), group.legends.single().valueColor)
        val instance = state.getIndicatorByInstanceId(id)!!
        val canvas = TestDrawScope(Size(200f, 200f))
        with(canvas) { with(PaneRenderers) { drawInstanceFigures(instance, instance.state!!, Viewport(), VisibleRange(0, 0), { it }, ChartTheme(), 0, false) } }
        assertEquals(listOf(group.legends.single().valueColor), canvas.rects.map { it.color })
    }

    @Test fun `instance style patch is consumed`() {
        val def = definition("UPSTREAM_PATCH", listOf(PlotDef("line", "L", PlotType.LINE)))
        LkanvasRegistry.registerIndicator(def, replace = true)
        val state = ChartState()
        runBlocking { state.reset(MarketKey("PATCH", "1m"), listOf(Candle(0L, 1.0, 2.0, 0.5, 1.0))) }
        state.createIndicator(def.name, paneId = "main", styles = IndicatorStylePatch(lines = listOf(SmoothLineStyle(color = "#0000FF"))))
        assertEquals(Color.Blue, TooltipRenderer.buildIndicatorGroups(state, "main", 0, Styles(), ChartTheme()).single().legends.single().valueColor)
    }

    @Test fun `typed global bar slots preserve three colors independently`() {
        val first = PolygonStyle(upColor = "#FF0000", downColor = "#00FF00", noChangeColor = "#808080")
        val second = PolygonStyle(upColor = "#0000FF", downColor = "#FFFF00", noChangeColor = "#FFFFFF")
        val theme = io.lkanvas.compose.theme.StylesThemeBridge.toTheme(Styles(indicator = IndicatorStyle(bars = listOf(first, second))))
        val definition = definition("TYPED_SLOTS", emptyList())
        val instance = IndicatorInstance("typed", definition)
        val a = io.lkanvas.compose.draw.IndicatorStyleResolver.barColorsContext(instance, theme, 0)
        val b = io.lkanvas.compose.draw.IndicatorStyleResolver.barColorsContext(instance, theme, 1)
        assertEquals("#FF0000", a.upColor)
        assertEquals("#00FF00", a.downColor)
        assertEquals("#0000FF", b.upColor)
        assertEquals("#FFFF00", b.downColor)
        val patched = PolygonStylePatch(upColor = "#123456").applyTo(first)
        assertEquals("#123456", patched.upColor)
        assertEquals(first.downColor, patched.downColor)
    }

    @Test fun `formatter templates are accepted by library date utility`() {
        val state = ChartState()
        val chart = LKanvasChart.init(state)
        try {
            chart.setFormatter(Formatter(formatDate = { ts, template, _ -> Format.formatTimestampByTemplate(ts, template!!, "Asia/Shanghai") }))
            assertEquals("2026-11-03", state.formattedTimeLabel(1793635200000L, ChartState.TEMPLATE_DATE_YEAR, ChartState.FORMAT_SCENE_X_AXIS))
        } finally { LKanvasChart.dispose(chart) }
    }

    @Test fun `timezone and locale changes invalidate static rendering`() {
        val state = ChartState()
        val chart = LKanvasChart.init(state)
        try {
            chart.setTimezone("UTC")
            val before = state.viewRevision
            chart.setTimezone("Asia/Shanghai")
            assertTrue(state.viewRevision > before)
            val next = state.viewRevision
            chart.setLocale("en-US")
            assertTrue(state.viewRevision > next)
        } finally { LKanvasChart.dispose(chart) }
    }

    @Test fun `period templates match fixed upstream tokens for all scenes`() {
        val state = ChartState()
        val chart = LKanvasChart.init(state)
        try {
            chart.setFormatter(Formatter(formatDate = { _, template, _ -> template!! }))
            val cases = listOf(
                Triple(PeriodType.SECOND, "HH:mm:ss", "HH:mm:ss"),
                Triple(PeriodType.MINUTE, "HH:mm", "YYYY-MM-DD HH:mm"),
                Triple(PeriodType.HOUR, "MM-DD HH:mm", "YYYY-MM-DD HH:mm"),
                Triple(PeriodType.DAY, "YYYY-MM-DD", "YYYY-MM-DD"),
                Triple(PeriodType.WEEK, "YYYY-MM-DD", "YYYY-MM-DD"),
                Triple(PeriodType.MONTH, "YYYY-MM", "YYYY-MM"),
                Triple(PeriodType.YEAR, "YYYY", "YYYY"),
            )
            for ((type, axis, tooltip) in cases) {
                chart.setPeriod(Period(type))
                assertEquals(axis, state.formattedTimeLabel(0L, ChartState.TEMPLATE_TIME, ChartState.FORMAT_SCENE_X_AXIS))
                assertEquals(tooltip, state.formattedTimeLabel(0L, ChartState.TEMPLATE_TIME, ChartState.FORMAT_SCENE_TOOLTIP))
            }
        } finally { LKanvasChart.dispose(chart) }
    }

    @Test fun `legacy two argument format config remains usable`() {
        val state = ChartState()
        state.formatConfig = ChartState.FormatConfig(formatDate = { _, _ -> "legacy" })
        assertEquals("legacy", state.formattedTimeLabel(0L, ChartState.TEMPLATE_TIME, ChartState.FORMAT_SCENE_TOOLTIP))
    }
}
