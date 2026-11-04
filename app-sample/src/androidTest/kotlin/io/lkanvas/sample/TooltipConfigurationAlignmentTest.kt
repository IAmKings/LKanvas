package io.lkanvas.sample

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import io.lkanvas.chart.styles.Styles
import io.lkanvas.compose.draw.ChartRenderer
import io.lkanvas.compose.draw.TooltipRenderer
import io.lkanvas.compose.state.ChartState
import io.lkanvas.compose.theme.ChartTheme
import io.lkanvas.core.data.Candle
import io.lkanvas.core.store.MarketKey
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TooltipConfigurationAlignmentTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private fun withState(test: (ChartState, TextMeasurer) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            runBlocking(Dispatchers.Main) {
                val state = ChartState(scope)
                state.createIndicator("MA", paneId = "main", isStack = true)
                state.createIndicator("VOL", paneId = "vol")
                state.createIndicator("MACD", paneId = "macd")
                val chart = io.lkanvas.chart.LKanvasChart.init(state)
                chart.createIndicator("RSI", paneId = "extra")
                state.reset(MarketKey("TOOLTIPS", "1m"), (0 until 600).map {
                    val v = 100.0 + it % 21
                    Candle(it * 60000L, v - 0.5, v + 1, v - 1, v, 1000.0 + it % 7 * 100)
                })
                state.onLayout(1000f, ChartState.PaneHeights(900f, 240f, 240f, 60f))
                try { test(state, TextMeasurer(createFontFamilyResolver(ctx), Density(1f), LayoutDirection.Ltr, 256)) }
                finally { io.lkanvas.chart.LKanvasChart.dispose(chart) }
            }
        } finally { scope.cancel() }
    }

    private fun capture(state: ChartState, measurer: TextMeasurer, styles: Styles, pane: String, crosshair: Offset?): List<TooltipRenderer.DrawOp> {
        val ops = mutableListOf<TooltipRenderer.DrawOp>()
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(1200, 1800)), Size(1200f, 1800f)) {
            with(TooltipRenderer) {
                if (pane == "main") drawCandleTooltip(state, styles, ChartTheme(), measurer, 12f, crosshair, 1000f, state.paneBounds("main")!!.second, ops)
                else drawIndicatorTooltip(state, styles, ChartTheme(), measurer, 12f, crosshair, 1000f, state.paneBounds(pane)!!.first, pane, ops)
            }
        }
        return ops
    }

    @Test fun indicatorVisibilityAndOffsetsAreConsumedByMainBuiltinAndDynamicPanes() = withState { state, measurer ->
        val base = Styles().let { it.copy(candle = it.candle.copy(tooltip = it.candle.tooltip.copy(showRule = "none"))) }
        for (pane in listOf("main", "vol", "macd", "extra")) {
            for (rule in listOf("none", "always", "follow_cross")) for (active in listOf(false, true)) {
                val styles = base.copy(indicator = base.indicator.copy(tooltip = base.indicator.tooltip.copy(showRule = rule)))
                val ops = capture(state, measurer, styles, pane, if (active) Offset(404f, 200f) else null)
                assertEquals("pane=$pane rule=$rule active=$active", rule == "always" || rule == "follow_cross" && active, ops.isNotEmpty())
            }
            if (pane != "main") {
                val original = capture(state, measurer, base, pane, null).filterIsInstance<TooltipRenderer.DrawOp.Text>().first()
                val shifted = base.copy(indicator = base.indicator.copy(tooltip = base.indicator.tooltip.copy(offsetLeft = 34.0, offsetTop = 30.0)))
                val moved = capture(state, measurer, shifted, pane, null).filterIsInstance<TooltipRenderer.DrawOp.Text>().first()
                assertEquals(30f, moved.topLeft.x - original.topLeft.x, 0.01f)
                assertEquals(24f, moved.topLeft.y - original.topLeft.y, 0.01f)
            }
        }
        val hidden = base.copy(indicator = base.indicator.copy(tooltip = base.indicator.tooltip.copy(showRule = "none")))
        val shown = io.lkanvas.compose.export.ChartSnapshotExporter.export(state, measurer, styles = base, widthPx = 1200, heightPx = 1800) as io.lkanvas.compose.export.ChartSnapshotExporter.Result.Ok
        val invisible = io.lkanvas.compose.export.ChartSnapshotExporter.export(state, measurer, styles = hidden, widthPx = 1200, heightPx = 1800) as io.lkanvas.compose.export.ChartSnapshotExporter.Result.Ok
        assertFalse("export must consume the configured indicator visibility", shown.bitmap.asAndroidBitmap().sameAs(invisible.bitmap.asAndroidBitmap()))
    }

    @Test fun mixedRectAndStandardModesKeepTheVisibleRowsAndRefreshSameCandleCache() = withState { state, measurer ->
        val theme = ChartTheme()
        val base = Styles()
        val height = 1700f
        for (candleType in listOf("standard", "rect")) for (indicatorType in listOf("standard", "rect")) {
            val styles = base.copy(candle = base.candle.copy(tooltip = base.candle.tooltip.copy(showType = candleType, rect = base.candle.tooltip.rect.copy(position = "pointer"))),
                indicator = base.indicator.copy(tooltip = base.indicator.tooltip.copy(showType = indicatorType)))
            val ops = capture(state, measurer, styles, "main", Offset(404f, 200f))
            assertTrue(ops.filterIsInstance<TooltipRenderer.DrawOp.Text>().any { it.text == "MA5: " })
            assertTrue(ops.filterIsInstance<TooltipRenderer.DrawOp.Text>().any { it.text == "O" })
            assertEquals(if (candleType == "rect" || indicatorType == "rect") 2 else 0, ops.filterIsInstance<TooltipRenderer.DrawOp.RoundRect>().size)
            val cache = ChartRenderer.TooltipKeyHolder()
            val params = ChartRenderer.Params(1200f, height, 200f, 12f, 12f, true, styles = styles)
            fun render(holder: ChartRenderer.TooltipKeyHolder): ImageBitmap {
                val image = ImageBitmap(1200, height.toInt())
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(1200f, height)) {
                    with(ChartRenderer) { render(state, theme, params.copy(tooltipCache = holder), measurer) }
                }
                return image
            }
            state.showCrosshairAt(state.viewport.candleCenterX(599.0).toFloat(), 200f)
            render(cache)
            state.moveCrosshair(state.viewport.candleCenterX(599.0).toFloat(), 260f)
            val cached = render(cache)
            assertTrue(cached.asAndroidBitmap().sameAs(render(ChartRenderer.TooltipKeyHolder()).asAndroidBitmap()))
            val extraOps = capture(state, measurer, styles, "extra", null).filterIsInstance<TooltipRenderer.DrawOp.Text>()
            assertTrue("dynamic pane legend must be rendered", extraOps.any { it.text.startsWith("RSI(") })
            val dir = File(ctx.getExternalFilesDir("validation"), "other-modules").apply { mkdirs() }
            File(dir, "$candleType-$indicatorType.png").outputStream().use {
                cached.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
    @Test fun mixedFigureOrderAndDynamicBarLegendColorsMatchActualRasterizedPlots() = withState { state, _ ->
        val definition = object : io.lkanvas.core.indicator.IndicatorDefinition {
            override val name = "MIXED_COLOR_TEST"
            override val shortName = "MIX"
            override val calcParams = emptyList<Double>()
            override val precision = 2
            override val plots = listOf(
                io.lkanvas.core.indicator.PlotDef("bar", "BAR: ", io.lkanvas.core.indicator.PlotType.BAR, baseValue = 0.0,
                    styles = { row: Int -> mapOf("color" to if (row % 2 == 0) "#00FF00" else "#FF0000") }),
                io.lkanvas.core.indicator.PlotDef("line", "LINE: ", io.lkanvas.core.indicator.PlotType.LINE),
            )
            override fun initScratch(params: List<Double>): Any? = null
            override fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any? = null
            override fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int) =
                linkedMapOf("line" to 50.0, "bar" to 20.0)
        }
        state.registry.register(definition)
        val id = state.createIndicator(definition.name, paneId = "main", isStack = true)!!
        val inst = state.getIndicators(ChartState.IndicatorFilter(id = id)).single()
        val theme = ChartTheme(indicatorLineSizePx = 2f)
        val offset = io.lkanvas.compose.draw.PaneRenderers.figurePaletteOffset(state, inst)
        val bitmap = ImageBitmap(200, 120)
        val viewport = io.lkanvas.core.viewport.Viewport().apply { layout(200.0, 600); restoreAnchor(0.0, 40.0, 600) }
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(200f, 120f)) {
            with(io.lkanvas.compose.draw.PaneRenderers) {
                drawInstanceFigures(inst, inst.state!!, viewport, io.lkanvas.compose.draw.VisibleRange(0, 3), { 100.0 - it }, theme, offset, false, state.dataSnapshot.candles)
            }
        }
        val group = TooltipRenderer.buildIndicatorGroups(state, "main", 1, Styles(), theme).last()
        assertEquals(listOf("BAR: ", "LINE: "), group.legends.map { it.title })
        assertEquals(group.legends[0].valueColor.toArgb(), bitmap.asAndroidBitmap().getPixel(60, 90))
        assertEquals(group.legends[1].valueColor.toArgb(), bitmap.asAndroidBitmap().getPixel(80, 50))
        val next = TooltipRenderer.buildIndicatorGroups(state, "main", 2, Styles(), theme).last()
        assertEquals(next.legends[0].valueColor.toArgb(), bitmap.asAndroidBitmap().getPixel(100, 90))
        assertNotEquals(group.legends[0].valueColor, next.legends[0].valueColor)
    }

    @Test fun formatterIsConsumedByRasterAndExportAndTimezoneInvalidatesCache() = withState { state, measurer ->
        val chart = io.lkanvas.chart.LKanvasChart.init(state)
        val seen = mutableSetOf<String?>()
        chart.setTimezone("UTC")
        chart.setFormatter(io.lkanvas.chart.Formatter(formatDate = { timestamp, template, type ->
            seen += type
            io.lkanvas.core.common.utils.Format.formatTimestampByTemplate(timestamp, template!!, chart.getTimezone())
        }))
        state.showCrosshairAt(state.viewport.candleCenterX(599.0).toFloat(), 200f)
        val styles = Styles()
        val cache = ChartRenderer.TooltipKeyHolder()
        fun render(holder: ChartRenderer.TooltipKeyHolder): android.graphics.Bitmap {
            val image = ImageBitmap(1200, 1700)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(1200f, 1700f)) {
                with(ChartRenderer) {
                    render(state, ChartTheme(), ChartRenderer.Params(1200f, 1700f, 200f, 12f, 12f, true, styles = styles, tooltipCache = holder), measurer)
                }
            }
            return image.asAndroidBitmap()
        }
        val utc = render(cache)
        assertTrue("actual raster must invoke xAxis and tooltip scenes", seen.containsAll(listOf("xAxis", "tooltip")))
        chart.setTimezone("Asia/Shanghai")
        val changed = render(cache)
        assertFalse("timezone change must alter the rendered date text", utc.sameAs(changed))
        assertTrue("cached tooltip must match a fresh render after timezone change", changed.sameAs(render(ChartRenderer.TooltipKeyHolder())))
        seen.clear()
        val exported = io.lkanvas.compose.export.ChartSnapshotExporter.export(state, measurer, styles = styles, widthPx = 1200, heightPx = 1700)
        assertTrue(exported is io.lkanvas.compose.export.ChartSnapshotExporter.Result.Ok)
        assertTrue("PNG must consume the same date formatter", "xAxis" in seen)
    }
}
