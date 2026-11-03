package io.lkanvas.sample

import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import io.lkanvas.chart.styles.Styles
import io.lkanvas.compose.KLineChart
import io.lkanvas.compose.draw.ChartRenderer
import io.lkanvas.compose.state.ChartState
import io.lkanvas.compose.theme.ChartLayoutConfig
import io.lkanvas.compose.theme.ChartTheme
import io.lkanvas.core.data.Candle
import io.lkanvas.core.store.MarketKey
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ChartLayerPixelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @org.junit.Before fun launch() {
        closeSampleActivities()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("am start -W -n io.lkanvas.sample/.MainActivity")).use { it.readBytes() }
    }
    @org.junit.After fun close() { closeSampleActivities() }

    @Test fun composedChartMatchesDirectRendererAndRedrawsAfterPurePan() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var state: ChartState
        val showReference = androidx.compose.runtime.mutableStateOf(false)
        try {
            runBlocking(Dispatchers.Main) {
                state = ChartState(scope)
                state.createIndicator("MA", paneId = "main", isStack = true)
                state.createIndicator("VOL", paneId = "vol")
                state.createIndicator("MACD", paneId = "macd")
                state.reset(MarketKey("LAYER", "1m"), (0 until 600).map { Candle(it * 60000L, 100.0, 104.0, 98.0, 100.0 + it % 3, 1000.0 + it) })
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                activity.setContent {
                    val density = LocalDensity.current
                    val config = ChartLayoutConfig()
                    val theme = ChartTheme()
                    val styles = remember { Styles() }
                    val measurer = rememberTextMeasurer(cacheSize = 256)
                    val cache = remember { ChartRenderer.TooltipKeyHolder() }
                    val gutter = with(density) { config.rightAxisGutter.toPx() } * density.fontScale.coerceIn(1f, 2f)
                    val legend = with(density) { config.legendTextSize.toPx() }
                    val axis = with(density) { config.axisTextSize.toPx() }
                    Box(Modifier.padding(top = 48.dp)) {
                        Box(Modifier.size(180.dp, 280.dp).semantics { contentDescription = "R7_VIEWPORT" }) {
                            KLineChart(state, Modifier.fillMaxSize().drawWithContent {
                                if (!showReference.value) drawContent()
                            }, theme = theme, config = config, styles = styles)
                            // 同一物理位置交替采样，避免 GPU 亚像素覆盖率受画布原点影响。
                            Box(Modifier.matchParentSize()) {
                              Canvas(Modifier.fillMaxSize()) {
                                if (showReference.value) {
                                    @Suppress("UNUSED_VARIABLE") val revision = state.viewRevision + state.overlayRevision
                                    with(ChartRenderer) { render(state, theme, ChartRenderer.Params(size.width, size.height, gutter, legend, axis, false, styles = styles, tooltipCache = cache), measurer) }
                                }
                              }
                            }
                        }
                    }
                }
            }
            val device = UiDevice.getInstance(instrumentation)
            val viewport = device.findObject(UiSelector().description("R7_VIEWPORT"))
            assertTrue(viewport.waitForExists(10000))
            val output = File(instrumentation.targetContext.getExternalFilesDir("validation"), "layer-pixels").apply { mkdirs() }
            fun capture(name: String): IntArray {
                Thread.sleep(400)
                val bounds = viewport.bounds
                val bitmap = instrumentation.uiAutomation.takeScreenshot()!!
                val pixels = IntArray(bounds.width() * bounds.height())
                bitmap.getPixels(pixels, 0, bounds.width(), bounds.left, bounds.top, bounds.width(), bounds.height())
                File(output, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                return pixels
            }
            var first: IntArray? = null
            repeat(4) { frame ->
                val actual = capture("frame-$frame-actual")
                instrumentation.runOnMainSync { showReference.value = true }
                val expected = capture("frame-$frame-reference")
                instrumentation.runOnMainSync { showReference.value = false }
                assertEquals(expected.size, actual.size)
                var maxDelta = 0
                val shifts = intArrayOf(0, 8, 16, 24)
                for (i in expected.indices) for (shift in shifts) {
                    maxDelta = maxOf(maxDelta, kotlin.math.abs(((expected[i] ushr shift) and 255) - ((actual[i] ushr shift) and 255)))
                }
                File(output, "frame-$frame-delta.txt").writeText("maxChannelDelta=$maxDelta\nsamePhysicalOrigin=true\nbounds=${viewport.bounds}\n")
                assertTrue("composed pixels changed: frame=$frame maxChannelDelta=$maxDelta", maxDelta <= 1)
                if (frame == 0) first = actual else assertFalse("state changes must invalidate the chart drawing", first!!.contentEquals(actual))
                if (frame == 0) instrumentation.runOnMainSync {
                    val old = state.viewport.scrollIndex
                    state.panBy(123f)
                    assertNotEquals("fixture must pan away from the latest boundary", old, state.viewport.scrollIndex)
                }
                if (frame == 1) runBlocking(Dispatchers.Main) {
                    state.appendBatch(listOf(Candle(600 * 60000L, 101.0, 106.0, 97.0, 105.0, 2345.0)))
                }
                if (frame == 2) instrumentation.runOnMainSync { state.panBy(47f) }
            }
            // 摘要必须在无须宿主重组的状态变更后更新，不能留下旧的朗读值。
            var previousSummary = ""
            repeat(4) { step ->
                var summary = ""
                instrumentation.runOnMainSync {
                    if (step in 1..2) assertTrue(state.selectPreviousCandle())
                    if (step == 3) assertTrue(state.selectNextCandle())
                    summary = state.accessibilityText { timestamp ->
                        io.lkanvas.compose.draw.ChartText.timeLabel(timestamp, 60_000L, state.effectiveTimeZone())
                    }
                }
                if (step > 0) assertNotEquals("selection must change the announced values", previousSummary, summary)
                assertTrue("semantics must reflect selection $step", device.findObject(UiSelector().descriptionContains(summary)).waitForExists(5000))
                previousSummary = summary
            }
        } finally { scope.cancel() }
    }
}
