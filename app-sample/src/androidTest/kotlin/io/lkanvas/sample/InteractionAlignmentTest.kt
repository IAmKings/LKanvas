package io.lkanvas.sample

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import io.lkanvas.chart.styles.Styles
import io.lkanvas.compose.KLineChart
import io.lkanvas.compose.state.ChartState
import io.lkanvas.core.data.Candle
import io.lkanvas.core.store.MarketKey
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class InteractionAlignmentTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun tapLongPressReleaseDismissAndRightDragUseTheActualPointerPipeline() {
        closeSampleActivities()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("am start -W -n io.lkanvas.sample/.MainActivity")).use { it.readBytes() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val state = ChartState(scope)
        var candleClicks = 0
        state.onCandleClick = { candleClicks++ }
        try {
            runBlocking(Dispatchers.Main) {
                state.createIndicator("MA", paneId = "main", isStack = true)
                state.createIndicator("VOL", paneId = "vol")
                state.createIndicator("MACD", paneId = "macd")
                state.reset(MarketKey("GESTURE", "1m"), (0 until 600).map { Candle(it * 60000L, 150.0, 200.0, 100.0, 150.0, 1000.0 + it % 7) })
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                activity.setContent {
                    Box(Modifier.fillMaxSize().padding(top = 48.dp, bottom = 48.dp).semantics { contentDescription = "ALIGN_CHART" }) {
                        KLineChart(state, Modifier.fillMaxSize(), styles = Styles())
                    }
                }
            }
            val device = UiDevice.getInstance(instrumentation)
            val chart = device.findObject(UiSelector().description("ALIGN_CHART"))
            assertTrue(chart.waitForExists(10000))
            instrumentation.waitForIdleSync()
            val bounds = chart.bounds
            val x = bounds.left + bounds.width() / 3
            val y = bounds.top + bounds.height() / 3
            device.click(x, y)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { assertNotNull("short tap must show crosshair after release", state.crosshairPosition) }
            device.click(x, y)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { assertEquals("repeated short taps retain candle click callbacks", 2, candleClicks) }
            device.click(x + 200, y + 100)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { assertNull("a distant touch dismisses the retained crosshair", state.crosshairPosition) }
            device.swipe(x, y, x, y, 80)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { assertNotNull("long press release retains crosshair", state.crosshairPosition) }
            device.click(x + 200, y + 100)
            instrumentation.waitForIdleSync()
            var before = 0.0
            instrumentation.runOnMainSync {
                assertNull("dismiss before panning", state.crosshairPosition)
                before = state.viewport.scrollIndex
            }
            device.swipe(x, y, x + 200, y, 30)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertTrue("right drag reveals earlier candles: $before -> ${state.viewport.scrollIndex}, crosshair=${state.crosshairPosition}, owner=${state.gestureOwner}", state.viewport.scrollIndex < before)
                assertNull("panning does not activate crosshair", state.crosshairPosition)
            }
        } finally {
            scope.cancel()
            closeSampleActivities()
        }
    }
}
