package io.lkanvas.sample

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import io.lkanvas.compose.draw.ChartRenderer
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText as drawComposeText
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.lkanvas.chart.styles.Styles
import io.lkanvas.compose.draw.ChartText
import io.lkanvas.compose.draw.drawText
import io.lkanvas.compose.draw.TooltipRenderer
import io.lkanvas.compose.state.ChartState
import io.lkanvas.compose.theme.ChartTheme
import io.lkanvas.core.data.Candle
import io.lkanvas.core.store.MarketKey
import kotlinx.coroutines.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TypographyLayoutTest {
    @Test fun denseShortLandscapeNeverPaintsPriceMarkersThroughWrappedLegends() = runBlocking(Dispatchers.Main) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val state = ChartState(scope)
        val facade = io.lkanvas.chart.LKanvasChart.init(state)
        try {
            facade.createIndicator("MA", paneId = "main", isStack = true)
            facade.createIndicator("VOL", paneId = "vol")
            facade.createIndicator("MACD", paneId = "macd")
            LocalFeed(scope).startStress(state, StressConfig(1000, 1, 0), facade)
            val styles = sampleStyles(true, facade.getStyles()).let {
                it.copy(candle = it.candle.copy(tooltip = it.candle.tooltip.copy(showRule = "follow_cross")))
            }
            facade.setStyles(styles)
            // PJZ110 font2 landscape: safe area leaves a 3008x572 chart with a short main pane.
            val width = 3008f
            val height = 572f
            val right = 2656f
            val main = 243.1f
            state.onLayout(right, ChartState.PaneHeights(main, 121.55f, 121.55f, 85.8f))
            val density = Density(4f, 2f)
            val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr, 256)
            val theme = facade.resolveTheme().copy(highMarkerColor = Color.Magenta, lowMarkerColor = Color.Cyan)
            val cache = ChartRenderer.TooltipKeyHolder()
            val evidence = File(context.getExternalFilesDir("validation"), "dense-landscape-markers").apply { mkdirs() }
            repeat(2) { frame ->
                val image = ImageBitmap(width.toInt(), height.toInt())
                CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(width, height)) {
                    with(ChartRenderer) {
                        render(state, theme, ChartRenderer.Params(width, height, width - right, 88f, 88f, false, styles = styles, tooltipCache = cache), measurer)
                    }
                }
                val ops = mutableListOf<TooltipRenderer.DrawOp>()
                val legendImage = ImageBitmap(width.toInt(), height.toInt())
                CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(legendImage), Size(width, height)) {
                    with(TooltipRenderer) { drawCandleTooltip(state, styles, theme, measurer, 88f, null, right, main, ops) }
                }
                legendImage.asAndroidBitmap().recycle()
                val bounds = ops.filterIsInstance<TooltipRenderer.DrawOp.Text>().map {
                    val layout = measurer.measure(it.text, TooltipRenderer.textStyle(it.sizePx, it.color))
                    Rect(it.topLeft, Size(layout.size.width.toFloat(), layout.size.height.toFloat()))
                }
                File(evidence, "legend-bounds-$frame.txt").writeText(bounds.joinToString("\n"))
                val bitmap = image.asAndroidBitmap()
                try {
                    File(evidence, "frame-$frame.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    val counts = IntArray(2)
                    for (y in 0 until main.toInt()) for (x in 0 until right.toInt()) {
                        val pixel = bitmap.getPixel(x, y)
                        val marker = when (pixel) { 0xffff00ff.toInt() -> 0; 0xff00ffff.toInt() -> 1; else -> -1 }
                        if (marker >= 0) {
                            counts[marker]++
                            assertFalse("price marker $marker covers wrapped legend at $x,$y frame=$frame", bounds.any { it.contains(Offset(x.toFloat(), y.toFloat())) })
                        }
                    }
                    // 此极矮布局允许无空位时省略价格文字；正常布局的双标注可见性由下方独立测试守护。
                    assertTrue("MA legend must remain present", bounds.isNotEmpty())
                } finally { bitmap.recycle() }
            }
        } finally { io.lkanvas.chart.LKanvasChart.dispose(facade); scope.cancel() }
    }

    @Test fun timeLabelsKeepNeighborIntervalOffsetAndLocaleContextsSeparate() = runBlocking(Dispatchers.Main) {
        val original = java.util.Locale.getDefault()
        val originalFormat = if (android.os.Build.VERSION.SDK_INT >= 24) java.util.Locale.getDefault(java.util.Locale.Category.FORMAT) else null
        val originalDisplay = if (android.os.Build.VERSION.SDK_INT >= 24) java.util.Locale.getDefault(java.util.Locale.Category.DISPLAY) else null
        val timestamp = 1_700_000_000_000L
        val utc = java.util.SimpleTimeZone(0, "R7_LABEL_CONTEXT")
        val shifted = java.util.SimpleTimeZone(3_600_000, "R7_LABEL_CONTEXT")
        try {
            java.util.Locale.setDefault(java.util.Locale.US)
            assertEquals("22:13", ChartText.timeAxisLabel(timestamp, timestamp - 60_000, 60_000, utc))
            assertEquals("first visible label must not inherit an interior time label", "11-14", ChartText.timeAxisLabel(timestamp, null, 60_000, utc))
            assertEquals("daily interval needs the year/date form", "23/11-14", ChartText.timeAxisLabel(timestamp, timestamp - 60_000, 86_400_000, utc))
            assertEquals("same zone ID with changed offset must not reuse old time", "23:13", ChartText.timeAxisLabel(timestamp, timestamp - 60_000, 60_000, shifted))
            assertEquals("22:13", ChartText.timeLabel(timestamp, 60_000, utc))
            assertEquals("23:13", ChartText.timeLabel(timestamp, 60_000, shifted))
            java.util.Locale.setDefault(java.util.Locale("ar", "EG"))
            val expected = "%02d:%02d".format(22, 13)
            assertEquals("axis locale change must invalidate the label", expected, ChartText.timeAxisLabel(timestamp, timestamp - 60_000, 60_000, utc))
            assertEquals("summary locale change must invalidate the label", expected, ChartText.timeLabel(timestamp, 60_000, utc))
        } finally {
            java.util.Locale.setDefault(original)
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                java.util.Locale.setDefault(java.util.Locale.Category.FORMAT, originalFormat!!)
                java.util.Locale.setDefault(java.util.Locale.Category.DISPLAY, originalDisplay!!)
            }
        }
    }
    @Test fun cachedTimeAxisResolvesSystemTimeZoneOncePerRender() = runBlocking(Dispatchers.Main) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val original = java.util.TimeZone.getDefault()
        val clones = java.util.concurrent.atomic.AtomicInteger()
        val zone = object : java.util.SimpleTimeZone(0, "GMT") {
            override fun clone(): Any { clones.incrementAndGet(); return super.clone() }
        }
        try {
            java.util.TimeZone.setDefault(zone)
            val state = ChartState(scope)
            state.reset(MarketKey("TIMEZONE", "1m"), (0 until 600).map { Candle(it * 60000L, 100.0, 102.0, 99.0, 101.0, 1000.0) })
            state.onLayout(800f, ChartState.PaneHeights(600f, 0f, 0f, 80f))
            val density = Density(3f)
            val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr, 256)
            fun frame() {
                val image = ImageBitmap(1000, 800)
                CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(1000f, 800f)) {
                    with(ChartRenderer) { render(state, ChartTheme(), ChartRenderer.Params(1000f, 800f, 200f, 30f, 30f, false, drawLegend = false), measurer) }
                }
                image.asAndroidBitmap().recycle()
            }
            frame()
            clones.set(0)
            frame()
            assertEquals("one frame must share one resolved system timezone across visible candles", 1, clones.get())
        } finally { java.util.TimeZone.setDefault(original); scope.cancel() }
    }

    @Test fun volumeFormattingRetainsConstructionLocaleWhenFirstUseIsLater() = runBlocking(Dispatchers.Main) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val measurer = TextMeasurer(createFontFamilyResolver(context), Density(3f), LayoutDirection.Ltr, 256)
        val original = java.util.Locale.getDefault()
        val originalFormat = if (android.os.Build.VERSION.SDK_INT >= 24) java.util.Locale.getDefault(java.util.Locale.Category.FORMAT) else null
        try {
            for (locale in listOf(java.util.Locale.US, java.util.Locale.FRANCE, java.util.Locale("ar", "EG"), java.util.Locale("hi", "IN"))) {
                java.util.Locale.setDefault(locale)
                val expected = java.text.DecimalFormat().apply { maximumFractionDigits = 3; minimumFractionDigits = 3; isGroupingUsed = false }
                val text = ChartText(measurer, Color.White, 30f, volumePrecision = 3)
                java.util.Locale.setDefault(java.util.Locale.JAPAN)
                for (value in listOf(0.125, 12.345, -5.25, 9999.75)) assertEquals("volume locale=$locale", expected.format(value), text.volume(value))
            }
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                java.util.Locale.setDefault(java.util.Locale.US)
                java.util.Locale.setDefault(java.util.Locale.Category.FORMAT, java.util.Locale.FRANCE)
                val expected = java.text.DecimalFormat().apply { maximumFractionDigits = 2; minimumFractionDigits = 2; isGroupingUsed = false }
                val text = ChartText(measurer, Color.White, 30f)
                java.util.Locale.setDefault(java.util.Locale.Category.FORMAT, java.util.Locale.JAPAN)
                assertEquals(expected.format(1234.5), text.volume(1234.5))
            }
        } finally {
            java.util.Locale.setDefault(original)
            if (android.os.Build.VERSION.SDK_INT >= 24 && originalFormat != null) java.util.Locale.setDefault(java.util.Locale.Category.FORMAT, originalFormat)
        }
    }

    @Test fun shortLandscapeSubPaneLegendStaysInsideItsPaneOnFirstAndCachedFrames() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val state = ChartState(scope)
        try {
            runBlocking(Dispatchers.Main) {
                state.createIndicator("VOL", paneId = "vol")
                // 用实例图形样式区分两副图，避免把共享色板的 MACD 误认成 VOL。
                val macd = io.lkanvas.core.extension.indicator.IndicatorTemplates.get("MACD")!!
                state.registry.register(object : io.lkanvas.core.indicator.IndicatorDefinition by macd {
                    override fun plotsFor(params: List<Double>) = macd.plotsFor(params).map { it.copy(styles = mapOf("color" to "#00FFFF")) }
                })
                state.createIndicator("MACD", paneId = "macd")
                state.reset(MarketKey("LEGEND", "1m"), (0 until 200).map {
                    Candle(it * 60000L, 100.0, 102.0, 99.0, 101.0, 1000.0)
                })
                state.onLayout(2240f, ChartState.PaneHeights(240f, 120f, 120f, 80f))
            }
            val density = Density(4f, 2f)
            val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr, 256)
            val theme = ChartTheme().copy(linePalette = listOf(Color.Magenta), macdDif = Color.Cyan, macdDea = Color.Cyan, macdBarUp = Color.Cyan, macdBarDown = Color.Cyan)
            val styles = Styles().let { it.copy(
                candle = it.candle.copy(tooltip = it.candle.tooltip.copy(showRule = "follow_cross")),
                indicator = it.indicator.copy(tooltip = it.indicator.tooltip.copy(features = listOf(
                    io.lkanvas.chart.styles.TooltipFeatureStyle(id = "font", type = "icon_font", content = "A"),
                    io.lkanvas.chart.styles.TooltipFeatureStyle(id = "path", content = listOf(listOf(0.0, 0.0), listOf(1.0, 1.0))),
                ))),
            ) }
            val cache = ChartRenderer.TooltipKeyHolder()
            val bounds = state.paneBounds("vol")!!
            val background = ImageBitmap(3008, 564)
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(background), Size(3008f, 564f)) {
                with(ChartRenderer) { render(state, theme, ChartRenderer.Params(3008f, 564f, 768f, 88f, 88f, false, drawLegend = false, styles = styles), measurer) }
            }
            val backgroundPixels = IntArray(3008 * 564)
            background.asAndroidBitmap().getPixels(backgroundPixels, 0, 3008, 0, 0, 3008, 564)
            var firstFrame: IntArray? = null
            repeat(2) { frame ->
                val image = ImageBitmap(3008, 564)
                CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(3008f, 564f)) {
                    with(ChartRenderer) { render(state, theme, ChartRenderer.Params(3008f, 564f, 768f, 88f, 88f, false, styles = styles, tooltipCache = cache), measurer) }
                }
                val pixels = IntArray(3008 * 564)
                image.asAndroidBitmap().getPixels(pixels, 0, 3008, 0, 0, 3008, 564)
                if (frame == 0) firstFrame = pixels else assertArrayEquals("pane clipping must survive cached text, rectangle and path replay", firstFrame, pixels)
                assertTrue("invisible VOL features must not intercept the adjacent pane", state.tooltipFeatureHits.none { it.paneId == "vol" })
                for (hit in state.tooltipFeatureHits) {
                    val pane = state.paneBounds(hit.paneId)!!
                    val offset = if (hit.paneId == "macd") 2f else 1f
                    assertTrue("feature hit must stay in its visible pane: $hit", hit.rect.top >= pane.first + offset && hit.rect.bottom <= pane.second + offset)
                }
                var visible = 0
                for (y in bounds.first.toInt() until 564) for (x in 0 until 2240) {
                    val pixel = pixels[y * 3008 + x]
                    if ((pixel ushr 24) != 0 && (pixel and 0xffffff) == 0xff00ff && pixel != backgroundPixels[y * 3008 + x]) {
                        visible++
                        assertTrue("VOL legend escaped into adjacent pane: frame=$frame pixel=$x,$y pane=$bounds", y < bounds.second + 1f)
                    }
                }
                assertTrue("VOL legend glyph pixels required on frame $frame", visible > 100)
            }
        } finally { scope.cancel() }
    }
    @Test fun pixelLayoutReusePreservesConstrainedAndMultilineDrawing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (scale in listOf(1f, 2f)) for (value in listOf("123.45", "12345678901234567890", "12345\n67890")) {
            val density = Density(4f, scale)
            val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr, 256)
            val text = ChartText(measurer, Color.Black, 40f)
            val expected = ImageBitmap(200, 100)
            val actual = ImageBitmap(200, 100)
            val position = Offset(20f, 5f)
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(expected), Size(200f, 100f)) {
                drawComposeText(measurer, value, topLeft = position, style = TextStyle(fontSize = with(this) { 40f.toSp() }, color = Color.Black))
            }
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(actual), Size(200f, 100f)) {
                drawText(text, value, position)
            }
            val oldPixels = IntArray(200 * 100); val newPixels = IntArray(200 * 100)
            expected.asAndroidBitmap().getPixels(oldPixels, 0, 200, 0, 0, 200, 100)
            actual.asAndroidBitmap().getPixels(newPixels, 0, 200, 0, 0, 200, 100)
            assertArrayEquals("wrap/clip must match the original painter: scale=$scale text=$value", oldPixels, newPixels)
        }
    }
    @org.junit.Before fun keepTargetVisible() {
        closeSampleActivities()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("am start -W -n io.lkanvas.sample/.MainActivity")).use { it.readBytes() }
    }
    @org.junit.After fun closeTarget() { closeSampleActivities() }
    @Test fun pixelMeasurementAndDrawingReuseOneTextLayout() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val density = Density(4f)
        val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr, 1)
        val text = ChartText(measurer, Color.Black, 40f)
        val style = TextStyle(fontSize = 40.sp, color = Color.Black)
        val before = measurer.measure("12345.67", style, density = Density(1f))
        val image = ImageBitmap(600, 200)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(600f, 200f)) {
            drawText(text, "12345.67", Offset.Zero)
        }
        val after = measurer.measure("12345.67", style, density = Density(1f))
        assertSame("measurement and drawing must share their pixel layout cache entry", before.multiParagraph, after.multiParagraph)
    }
    @Test fun measuredTextKeepsItsLayoutWhenTheSharedMeasurerEvictsIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val density = Density(4f)
        val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr, 1)
        val text = ChartText(measurer, Color.Black, 40f)
        val style = TextStyle(fontSize = 40.sp, color = Color.Black)
        text.measureWidth("12345.67")
        val unrelated = measurer.measure("OTHER", style, density = Density(1f))
        val image = ImageBitmap(600, 200)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(600f, 200f)) {
            drawText(text, "12345.67", Offset.Zero)
        }
        val pixels = IntArray(600 * 200)
        image.asAndroidBitmap().getPixels(pixels, 0, 600, 0, 0, 600, 200)
        assertTrue("real measured glyphs must still be drawn", pixels.count { (it ushr 24) != 0 } > 100)
        val after = measurer.measure("OTHER", style, density = Density(1f))
        assertSame("drawing measured text must reuse its frame layout without displacing another cache entry", unrelated.multiParagraph, after.multiParagraph)
    }
    @Test fun platformNonlinearFontScalingDrawsInsideMeasuredGlyphBounds() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = android.content.res.Configuration(base.resources.configuration).apply {
            fontScale = 2f
            densityDpi = 640
        }
        val context = base.createConfigurationContext(configuration)
        val density = Density(context)
        val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr, 256)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val state = ChartState(scope)
            val styles = Styles().let { it.copy(indicator = it.indicator.copy(tooltip = it.indicator.tooltip.copy(title = it.indicator.tooltip.title.copy(show = false)))) }
            val ops = mutableListOf<TooltipRenderer.DrawOp>()
            val image = ImageBitmap(1440, 400)
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(1440f, 400f)) {
                with(TooltipRenderer) { drawPaneIndicatorTooltip(state, styles, ChartTheme(), measurer, 48f, null, 1440f, 0f, "vol", "", listOf("value" to 12345.67), listOf(Color.Magenta), ops) }
            }
            val op = ops.filterIsInstance<TooltipRenderer.DrawOp.Text>().single { it.color == Color.Magenta }
            val measured = measurer.measure(op.text, TooltipRenderer.textStyle(op.sizePx, op.color)).size
            val pixels = IntArray(1440 * 400)
            image.asAndroidBitmap().getPixels(pixels, 0, 1440, 0, 0, 1440, 400)
            var visible = 0
            pixels.forEachIndexed { index, pixel ->
                if ((pixel ushr 24) != 0 && (pixel and 0xffffff) == 0xff00ff) {
                    visible++
                    val x = index % 1440; val y = index / 1440
                    assertTrue("painted glyph escaped measured bounds at $x,$y; measured=$measured", x >= op.topLeft.x - 1 && x < op.topLeft.x + measured.width + 1 && y >= op.topLeft.y - 1 && y < op.topLeft.y + measured.height + 1)
                }
            }
            assertTrue("actual glyph pixels required", visible > 100)
        } finally { scope.cancel() }
    }
    @Test fun priceMarkersRemainVisibleWithoutCoveringRealLegendGlyphs() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val state = ChartState(scope)
        try {
            runBlocking(Dispatchers.Main) {
                state.createIndicator("MA", paneId = "main", isStack = true)
                state.reset(MarketKey("MARK", "1m"), (0 until 200).map {
                    Candle(it * 60000L, 100.0, if (it == 190) 120.0 else 101.0, 99.0, 100.0, 1000.0)
                })
            }
            for (reverse in listOf(false, true)) for (scale in listOf(1f, 1.3f, 2f)) for (width in listOf(1220f, 2712f)) {
                state.mainAxisReverse = reverse
                val density = Density(3f, scale)
                val measurer = TextMeasurer(createFontFamilyResolver(ctx), density, LayoutDirection.Ltr, 256)
                val axisPx = with(density) { 10.sp.toPx() }
                val right = width - 192 * scale
                val height = if (width == 1220f) 1100f else 600f
                runBlocking(Dispatchers.Main) { state.onLayout(right, ChartState.PaneHeights(height, 0f, 0f, 60f)) }
                val theme = ChartTheme().copy(highMarkerColor = Color.Magenta, lowMarkerColor = Color.Cyan)
                val styles = Styles()
                val ops = mutableListOf<TooltipRenderer.DrawOp>()
                val image = ImageBitmap(width.toInt(), (height + 100).toInt())
                CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(width, height + 100)) {
                    val cache = ChartRenderer.TooltipKeyHolder()
                    repeat(2) {
                        with(ChartRenderer) { render(state, theme, ChartRenderer.Params(width, height + 100, width - right, axisPx, axisPx, false, styles = styles, tooltipCache = cache), measurer) }
                    }
                    // Use the same real tooltip layout as the full renderer; only its bounds are inspected.
                    with(TooltipRenderer) { drawCandleTooltip(state, styles, theme, measurer, axisPx, null, right, height, ops) }
                }
                val bounds = ops.filterIsInstance<TooltipRenderer.DrawOp.Text>().map {
                    val layout = measurer.measure(it.text, TooltipRenderer.textStyle(it.sizePx, it.color))
                    Rect(it.topLeft, Size(layout.size.width.toFloat(), layout.size.height.toFloat()))
                }
                val bitmap = image.asAndroidBitmap()
                var markerPixels = 0
                for (y in 0 until height.toInt()) for (x in 0 until right.toInt()) {
                    val pixel = bitmap.getPixel(x, y)
                    if ((pixel ushr 16 and 255) > 220 && (pixel and 255) > 220 && (pixel ushr 8 and 255) < 40) {
                        markerPixels++
                        assertFalse("marker overlaps legend: scale=$scale width=$width at $x,$y", bounds.any { it.contains(Offset(x.toFloat(), y.toFloat())) })
                    }
                }
                assertTrue("highest-price marker must remain visible: scale=$scale width=$width", markerPixels > 20)
                bitmap.recycle()
            }
        } finally { scope.cancel() }
    }

    @Test fun firstLayoutAndCachedReplayMatchForStandardRectAndFeatureTooltips() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val state = ChartState(scope)
        try {
            runBlocking(Dispatchers.Main) {
                state.createIndicator("MA", paneId = "main", isStack = true)
                state.reset(MarketKey("REPLAY", "1m"), (0 until 200).map { Candle(it * 60000L, 100.0, 102.0, 99.0, 101.0, 1000.0) })
                state.onLayout(1000f, ChartState.PaneHeights(1000f, 0f, 0f, 60f))
            }
            val density = Density(3f, 1.3f)
            val measurer = TextMeasurer(createFontFamilyResolver(ctx), density, LayoutDirection.Ltr, 256)
            val base = Styles()
            for (type in listOf("standard", "rect")) {
                val styles = base.copy(candle = base.candle.copy(tooltip = base.candle.tooltip.copy(showType = type)),
                    indicator = base.indicator.copy(tooltip = base.indicator.tooltip.copy(features = listOf(
                        io.lkanvas.chart.styles.TooltipFeatureStyle(id = "feature", type = "icon_font", content = "A"),
                        io.lkanvas.chart.styles.TooltipFeatureStyle(id = "path", content = listOf(listOf(0.0, 0.0), listOf(1.0, 1.0))),
                    ))))
                val cache = ChartRenderer.TooltipKeyHolder()
                fun frame(): IntArray {
                    val image = ImageBitmap(1220, 1200)
                    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(1220f, 1200f)) {
                        with(ChartRenderer) { render(state, ChartTheme(), ChartRenderer.Params(1220f, 1200f, 220f, 39f, 39f, false, styles = styles, tooltipCache = cache), measurer) }
                    }
                    val bitmap = image.asAndroidBitmap()
                    return IntArray(1220 * 1200).also { bitmap.getPixels(it, 0, 1220, 0, 0, 1220, 1200); bitmap.recycle() }
                }
                assertArrayEquals("first-frame record/replay must match cache hit: $type", frame(), frame())
            }
        } finally { scope.cancel() }
    }

    @Test fun realGlyphBoundsDoNotOverlapOrEscapePortraitAndLandscapePanes() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val state = ChartState(scope)
        try {
            runBlocking(Dispatchers.Main) {
                state.createIndicator("MA", paneId = "main", isStack = true)
                state.reset(MarketKey("TEST", "1m"), (0 until 200).map { Candle(it * 60000L, 100.0, 102.0, 99.0, 101.0, 1000.0) })
            }
            for (scale in listOf(1f, 2f)) {
                val density = Density(3f, scale)
                val measurer = TextMeasurer(createFontFamilyResolver(ctx), density, LayoutDirection.Ltr, 256)
                val axisPx = with(density) { 10.sp.toPx() }
                val text = ChartText(measurer, Color.Black, axisPx)
                assertEquals(measurer.measure("-1234.56", TextStyle(fontSize = 10.sp)).size.width.toFloat(), text.measureWidth("-1234.56"), 0.01f)
                assertEquals(measurer.measure("Hg国", TextStyle(fontSize = 10.sp)).size.height.toFloat(), text.textHeight, 0.01f)
                assertTrue(text.measureWidth("-1234.56") + 12 < 64 * 3 * scale)
                for (width in listOf(1220f, 2712f)) {
                    val right = width - 64 * 3 * scale
                    val mainHeight = if (width == 1220f) 1000f else 300f
                    val paneHeight = if (width == 1220f) 500f else 150f
                    for (main in listOf(true, false)) {
                        val ops = mutableListOf<TooltipRenderer.DrawOp>()
                        val height = if (main) mainHeight else paneHeight
                        val bitmap = ImageBitmap(width.toInt(), height.toInt())
                        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(width, height)) {
                            with(TooltipRenderer) {
                                if (main) drawCandleTooltip(state, Styles(), ChartTheme(), measurer, axisPx, null, right, height, ops)
                                else drawPaneIndicatorTooltip(state, Styles(), ChartTheme(), measurer, axisPx, null, right, 0f, "vol", "VOL", listOf("volume" to 1000.0, "ma1" to 1000.0, "ma2" to 1000.0, "ma3" to 1000.0), List(4) { Color.Red }, ops)
                            }
                        }
                        val bounds = ops.filterIsInstance<TooltipRenderer.DrawOp.Text>().map {
                            val layout = measurer.measure(it.text, TooltipRenderer.textStyle(it.sizePx, it.color))
                            Rect(it.topLeft, Size(layout.size.width.toFloat(), layout.size.height.toFloat()))
                        }
                        assertTrue(bounds.isNotEmpty())
                        bounds.forEach { assertTrue("out of pane: scale=$scale width=$width main=$main $it", it.left >= 0 && it.top >= 0 && it.right <= right && it.bottom <= height) }
                        bounds.forEachIndexed { i, a -> bounds.drop(i + 1).forEach { b -> assertFalse("text overlap: scale=$scale width=$width main=$main $a $b", a.overlaps(b)) } }
                    }
                }
            }
        } finally { scope.cancel() }
    }
}
