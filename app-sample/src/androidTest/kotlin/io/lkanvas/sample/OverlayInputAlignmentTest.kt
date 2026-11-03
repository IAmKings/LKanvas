package io.lkanvas.sample

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
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
import io.lkanvas.chart.*
import io.lkanvas.compose.KLineChart
import io.lkanvas.compose.state.ChartState
import io.lkanvas.core.data.Candle
import io.lkanvas.core.extension.overlay.OverlayTemplates
import io.lkanvas.core.extension.overlay.SegmentDefinition
import io.lkanvas.core.overlay.*
import io.lkanvas.core.store.MarketKey
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class OverlayInputAlignmentTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun withChart(block: (ChartState, LKanvasChart, android.graphics.Rect) -> Unit) {
        closeSampleActivities()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("am start -W -n io.lkanvas.sample/.MainActivity")).use { it.readBytes() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val state = ChartState(scope)
        val chart = LKanvasChart.init(state)
        try {
            runBlocking(Dispatchers.Main) {
                state.reset(MarketKey("OVERLAY", "1m"), (0 until 600).map { Candle(it * 60000L, 140.0, 200.0, 100.0, 160.0, 1000.0) })
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                activity.setContent { Box(Modifier.fillMaxSize().padding(top = 48.dp, bottom = 48.dp).semantics { contentDescription = "OVERLAY_CHART" }) { KLineChart(state, Modifier.fillMaxSize()) } }
            }
            val obj = UiDevice.getInstance(instrumentation).findObject(UiSelector().description("OVERLAY_CHART"))
            assertTrue(obj.waitForExists(10000))
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { state.viewport.restoreAnchor(250.0, 8.0, 600) }
            block(state, chart, obj.bounds)
        } finally { LKanvasChart.dispose(chart); scope.cancel(); closeSampleActivities() }
    }

    private fun event(action: Int, x: Float, y: Float, start: Long) {
        val e = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, y, 0)
        e.source = InputDevice.SOURCE_TOUCHSCREEN
        assertTrue(instrumentation.uiAutomation.injectInputEvent(e, true))
        e.recycle()
        instrumentation.waitForIdleSync()
    }
    private fun tap(x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, x, y, t)
        event(MotionEvent.ACTION_UP, x, y, t)
    }

    @Test fun unlockedPointAndBodyTapsDispatchClicksWithoutStartingADragAndDoubleTapSurvivesRelease() = withChart { state, chart, bounds ->
        val clicks = mutableListOf<Int>()
        var doubles = 0
        var starts = 0
        var ends = 0
        var dragStarts = 0
        OverlayTemplates.register(object : OverlayDefinition by SegmentDefinition {
            override val name = "input_click_test"
            override val onClick: ((String, Int, Double, Double) -> Unit) = { _, index, _, _ -> clicks += index }
            override val onDoubleClick: ((String, Int, Double, Double) -> Unit) = { _, _, _, _ -> doubles++ }
            override val onTouchStart: ((String, Int, Double, Double) -> Unit) = { _, _, _, _ -> starts++ }
            override val onTouchEnd: ((String, Int, Double, Double) -> Unit) = { _, _, _, _ -> ends++ }
        }, replace = true)
        var x = 0f; var y = 0f; var id = ""
        instrumentation.runOnMainSync {
            id = chart.createOverlay(OverlayCreate("input_click_test", points = listOf(OverlayPoint(280 * 60000L, 150.0), OverlayPoint(320 * 60000L, 150.0))))!!
            state.overlays.onEvent = { if (it is OverlayEvent.DragStarted) dragStarts++ }
            state.overlays.select(id)
            val p = state.overlayPointToPx(state.overlays.get(id)!!.points[0])!!
            x = p.x + bounds.left; y = p.y + bounds.top
        }
        tap(x, y)
        instrumentation.runOnMainSync {
            assertEquals(listOf(0), clicks)
            assertEquals(0, dragStarts)
            assertEquals(starts, ends)
        }
        tap(x, y)
        instrumentation.runOnMainSync { assertEquals(1, doubles) }
        SystemClock.sleep(450)
        tap(x + 160f, y)
        instrumentation.runOnMainSync { assertEquals(listOf(0, -1), clicks); assertEquals(starts, ends) }
        val t = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, x, y, t)
        event(MotionEvent.ACTION_MOVE, x + 2, y + 2, t)
        instrumentation.runOnMainSync { assertEquals(0, dragStarts) }
        event(MotionEvent.ACTION_MOVE, x + 80, y - 80, t)
        event(MotionEvent.ACTION_UP, x + 80, y - 80, t)
        instrumentation.runOnMainSync {
            assertEquals(1, dragStarts)
            assertEquals(2, clicks.size)
            assertEquals(OverlayPhase.SELECTED, state.overlays.get(id)!!.phase)
            assertEquals(starts, ends)
        }
    }

    @Test fun drawingMoveUpdatesPreviewInvokesTheHookAndReleaseCommitsTheSameGeometry() = withChart { state, chart, bounds ->
        var calls = 0
        var id = ""
        OverlayTemplates.register(object : OverlayDefinition by SegmentDefinition {
            override val name = "input_preview_test"
            override val performEventMoveForDrawing: ((String, OverlayPoint) -> Unit) = { instanceId, point ->
                calls++
                val points = state.overlays.get(instanceId)!!.points
                points[points.lastIndex] = point.copy(value = 155.0)
            }
        }, replace = true)
        instrumentation.runOnMainSync { id = chart.createOverlay(OverlayCreate("input_preview_test"))!! }
        tap(bounds.left + 200f, bounds.top + 300f)
        val t = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, bounds.left + 300f, bounds.top + 400f, t)
        event(MotionEvent.ACTION_MOVE, bounds.left + 400f, bounds.top + 350f, t)
        instrumentation.runOnMainSync {
            assertTrue("drawing hook must run on actual pointer move", calls >= 2)
            assertEquals(2, state.overlays.get(id)!!.points.size)
            assertEquals(155.0, state.overlays.get(id)!!.points.last().value, 0.0)
            assertNotNull(state.overlays.creatingId)
        }
        event(MotionEvent.ACTION_UP, bounds.left + 400f, bounds.top + 350f, t)
        instrumentation.runOnMainSync {
            assertEquals(OverlayPhase.COMPLETED, state.overlays.get(id)!!.phase)
            assertEquals(2, state.overlays.get(id)!!.points.size)
            assertEquals(155.0, state.overlays.get(id)!!.points.last().value, 0.0)
        }
    }
    @Test fun instanceMagnetSettingsAndContinuousBrushReachTheActualInputPipeline() = withChart { state, chart, bounds ->
        var id = ""
        var x = 0f; var y = 0f
        instrumentation.runOnMainSync {
            id = chart.createOverlay(OverlayCreate("segment", points = listOf(OverlayPoint(280 * 60000L, 150.0), OverlayPoint(320 * 60000L, 150.0)), mode = OverlayMode.WEAK_MAGNET, modeSensitivity = 100.0))!!
            state.overlays.select(id)
            val p = state.overlayPointToPx(state.overlays.get(id)!!.points[0])!!
            x = bounds.left + p.x; y = bounds.top + p.y
        }
        val t = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, x, y, t)
        var highY = 0f
        instrumentation.runOnMainSync { highY = bounds.top + state.paneValueToY("main", 200.0)!!.toFloat() }
        event(MotionEvent.ACTION_MOVE, x + 80f, highY - 20f, t)
        event(MotionEvent.ACTION_UP, x + 80f, highY - 20f, t)
        instrumentation.runOnMainSync {
            assertEquals(200.0, state.overlays.get(id)!!.points[0].value, 0.001)
            chart.removeOverlay(OverlayFilter(id = id))
            id = chart.createOverlay(OverlayCreate("brush", mode = OverlayMode.STRONG_MAGNET))!!
        }
        val b = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, bounds.left + 404f, bounds.top + 400f, b)
        event(MotionEvent.ACTION_MOVE, bounds.left + 408f, bounds.top + 400f, b)
        event(MotionEvent.ACTION_UP, bounds.left + 408f, bounds.top + 400f, b)
        instrumentation.runOnMainSync {
            val points = state.overlays.get(id)!!.points
            assertEquals(2, points.size)
            assertEquals(30000L, points[1].timestamp - points[0].timestamp)
            assertEquals(408f, state.overlayPointToPx(points[1], "main", continuous = true)!!.x, 0.01f)
            assertTrue(state.finishCreateOverlay())
        }
    }

    @Test fun lockedOverlayDoubleTapRequiresTheSameInstanceAndCancellationEndsTouchExactlyOnce() = withChart { state, chart, bounds ->
        val clicks = mutableListOf<String>()
        val doubles = mutableListOf<String>()
        var starts = 0; var ends = 0
        OverlayTemplates.register(object : OverlayDefinition by SegmentDefinition {
            override val name = "input_locked_test"
            override val onClick: ((String, Int, Double, Double) -> Unit) = { id, _, _, _ -> clicks += id }
            override val onDoubleClick: ((String, Int, Double, Double) -> Unit) = { id, _, _, _ -> doubles += id }
            override val onTouchStart: ((String, Int, Double, Double) -> Unit) = { _, _, _, _ -> starts++ }
            override val onTouchEnd: ((String, Int, Double, Double) -> Unit) = { _, _, _, _ -> ends++ }
        }, replace = true)
        var x = 0f; var y = 0f; var first = ""; var second = ""
        instrumentation.runOnMainSync {
            y = state.paneValueToY("main", 150.0)!!.toFloat()
            val otherValue = state.paneValueAt("main", y + 30f)!!
            first = chart.createOverlay(OverlayCreate("input_locked_test", points = listOf(OverlayPoint(280 * 60000L, 150.0), OverlayPoint(320 * 60000L, 150.0)), lock = true))!!
            second = chart.createOverlay(OverlayCreate("input_locked_test", points = listOf(OverlayPoint(280 * 60000L, otherValue), OverlayPoint(320 * 60000L, otherValue)), lock = true))!!
            x = bounds.left + state.viewport.candleCenterX(300.0).toFloat()
            y += bounds.top
        }
        tap(x, y)
        tap(x, y + 30f)
        instrumentation.runOnMainSync { assertEquals(listOf(first, second), clicks); assertTrue(doubles.isEmpty()) }
        tap(x, y + 30f)
        instrumentation.runOnMainSync { assertEquals(listOf(second), doubles) }
        SystemClock.sleep(450)
        tap(x, y + 30f)
        instrumentation.runOnMainSync { assertEquals(listOf(first, second, second), clicks) }
        instrumentation.runOnMainSync { chart.overrideOverlay(second, OverlayOverride(lock = false)) }
        val t = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, x, y + 30f, t)
        event(MotionEvent.ACTION_MOVE, x + 80f, y + 110f, t)
        event(MotionEvent.ACTION_CANCEL, x + 80f, y + 110f, t)
        instrumentation.runOnMainSync {
            assertEquals(starts, ends)
            assertEquals(3, clicks.size)
            assertEquals(OverlayPhase.SELECTED, state.overlays.get(second)!!.phase)
        }
    }

}
