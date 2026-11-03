package io.lkanvas.sample

import android.os.Looper
import android.widget.OverScroller
import androidx.compose.runtime.MonotonicFrameClock
import androidx.test.platform.app.InstrumentationRegistry
import io.lkanvas.chart.ActionType
import io.lkanvas.chart.LKanvasChart
import io.lkanvas.compose.state.ChartState
import io.lkanvas.compose.state.GestureOwner
import io.lkanvas.core.data.Candle
import io.lkanvas.core.store.MarketKey
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FlingFrameTest {
    @org.junit.Before fun launch() {
        closeSampleActivities()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("am start -W -n io.lkanvas.sample/.MainActivity")).use { it.readBytes() }
    }
    @org.junit.After fun close() { closeSampleActivities() }

    @Test fun platformFlingUsesDisplayFramesAndNewGestureCancelsCallbacks() = runBlocking(Dispatchers.Main) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        assertNull("external UI scope intentionally has no Compose frame clock", scope.coroutineContext[MonotonicFrameClock])
        val state = ChartState(scope)
        val facade = LKanvasChart.init(state)
        var dispatchMessage = ""
        Looper.getMainLooper().setMessageLogging { message -> dispatchMessage = if (message.startsWith(">>>>>")) message else "" }
        try {
            state.reset(MarketKey("FLING", "1m"), (0 until 3000).map { Candle(it * 60000L, 100.0, 102.0, 99.0, 101.0, 1000.0) })
            state.onLayout(800f, ChartState.PaneHeights(600f, 0f, 0f, 60f))
            state.viewport.scrollToIndex(1500.0, 3000)
            state.flingScroller = OverScroller(context)
            var callbacks = 0
            var outsideFrames = 0
            val messages = mutableSetOf<String>()
            facade.subscribeAction(ActionType.ON_SCROLL) {
                callbacks++
                messages += dispatchMessage
                if (!dispatchMessage.contains("FrameDisplayEventReceiver")) outsideFrames++
            }
            val before = state.viewport.scrollIndex
            state.fling(2500f)
            delay(250)
            val moved = state.viewport.scrollIndex < before
            state.beginGesture(GestureOwner.NONE)
            val stopped = callbacks
            delay(100)
            File(context.getExternalFilesDir("validation"), "fling-frame-context.json").writeText(
                org.json.JSONObject().put("callbacks", callbacks).put("outsideFrames", outsideFrames).put("moved", moved)
                    .put("messages", org.json.JSONArray(messages.toList())).put("cancelledCallbacks", callbacks == stopped).put("scopeHadFrameClock", false).toString(2),
            )
            assertTrue("fling must move a non-boundary viewport", moved)
            assertTrue("real scroll callbacks required", callbacks >= 2)
            assertEquals("new gesture must cancel future fling callbacks", stopped, callbacks)
            assertEquals("platform fling scroll callbacks must run in display frame callbacks", 0, outsideFrames)
        } finally { Looper.getMainLooper().setMessageLogging(null); LKanvasChart.dispose(facade); scope.cancel() }
    }
}
