package io.lkanvas.sample

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import io.lkanvas.chart.styles.Styles
import io.lkanvas.compose.draw.ChartRenderer
import io.lkanvas.compose.draw.TooltipRenderer
import io.lkanvas.compose.export.ChartSnapshotExporter
import io.lkanvas.compose.state.ChartState
import io.lkanvas.compose.theme.ChartTheme
import io.lkanvas.core.data.Candle
import io.lkanvas.core.store.MarketKey
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LegendAlignmentTest {
    @Test fun realGlyphsClearThePlotsAcrossFontSizesAndCacheAndExportPreserveGeometry() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            runBlocking(Dispatchers.Main) {
                val state = ChartState(scope)
                state.createIndicator("MA", paneId = "main", isStack = true)
                state.createIndicator("VOL", paneId = "vol")
                state.createIndicator("MACD", paneId = "macd")
                state.reset(MarketKey("LEGEND", "1m"), (0 until 600).map {
                    val v = 100.0 + (it % 21)
                    Candle(it * 60000L, v, v + 1, v - 1, v, 1000.0 + it % 7 * 100)
                })
                val styles = Styles().let { it.copy(candle = it.candle.copy(tooltip = it.candle.tooltip.copy(showRule = "follow_cross"))) }
                val theme = ChartTheme()
                for (fontScale in listOf(1f, 1.3f, 2f)) for (width in listOf(1220f, 2712f)) {
                    val density = Density(3f, fontScale)
                    val main = if (width == 1220f) 1000f else 450f
                    val sub = (if (width == 1220f) 300f else 220f) * fontScale
                    val measurer = TextMeasurer(createFontFamilyResolver(ctx), density, LayoutDirection.Ltr, 256)
                    val gutter = 192f * fontScale
                    state.onLayout(width - gutter, ChartState.PaneHeights(main, sub, sub, 60f))
                    state.hideCrosshair()
                    val height = main + sub * 2 + 60f
                    val params = ChartRenderer.Params(width, height, gutter, 33f * fontScale, 30f * fontScale, true, styles = styles)
                    fun render(cache: ChartRenderer.TooltipKeyHolder): ImageBitmap {
                        val bitmap = ImageBitmap(width.toInt(), height.toInt())
                        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(width, height)) {
                            with(ChartRenderer) { render(state, theme, params.copy(tooltipCache = cache), measurer) }
                        }
                        return bitmap
                    }
                    val cache = ChartRenderer.TooltipKeyHolder()
                    val image = render(cache)
                    for (pane in listOf("main", "vol", "macd")) {
                        val (top, bottom) = state.paneBounds(pane)!!
                        val ops = mutableListOf<TooltipRenderer.DrawOp>()
                        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(ImageBitmap(width.toInt(), height.toInt())), Size(width, height)) {
                            with(TooltipRenderer) {
                                if (pane == "main") drawCandleTooltip(state, styles, theme, measurer, 33f, null, width - gutter, bottom, ops)
                                else {
                                    drawIndicatorTooltip(state, styles, theme, measurer, 33f, null, width - gutter, top, pane, ops)
                                }
                            }
                        }
                        val legendBottom = ops.filterIsInstance<TooltipRenderer.DrawOp.Text>().maxOf {
                            it.topLeft.y + measurer.measure(it.text, TooltipRenderer.textStyle(it.sizePx, it.color)).size.height
                        }
                        val high = (if (pane == "main") state.mainPaneRange() else state.paneVisibleRange(pane))!!.second
                        val highestPlotY = top + state.paneValueToY(pane, high)!!
                        assertTrue("$pane glyph/plot overlap: font=$fontScale width=$width glyphBottom=$legendBottom plotTop=$highestPlotY bounds=$top..$bottom ops=${ops.filterIsInstance<TooltipRenderer.DrawOp.Text>().map { it.text to it.topLeft }}", highestPlotY >= legendBottom - 2)
                    }
                    if (fontScale != 1.3f) {
                        val output = File(ctx.getExternalFilesDir("validation"), "alignment-016").apply { mkdirs() }
                        File(output, "font-$fontScale-width-${width.toInt()}.png").outputStream().use {
                            image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                        }
                    }
                    state.showCrosshairAt(state.viewport.candleCenterX(599.0).toFloat(), main / 2)
                    val cached = render(cache)
                    val uncached = render(ChartRenderer.TooltipKeyHolder())
                    assertTrue("same-index crosshair activation must refresh tooltip cache", cached.asAndroidBitmap().sameAs(uncached.asAndroidBitmap()))
                    val yBefore = listOf("main", "vol", "macd").map { state.paneValueToY(it, 150.0) }
                    ChartSnapshotExporter.export(state, measurer, styles = styles, widthPx = 1080, heightPx = 1600)
                    assertEquals("export must preserve screen coordinate geometry", yBefore, listOf("main", "vol", "macd").map { state.paneValueToY(it, 150.0) })
                }
            }
        } finally { scope.cancel() }
    }
}
