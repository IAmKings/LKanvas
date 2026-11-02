package io.lkanvas.chart

import io.lkanvas.compose.state.ChartState
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*

class PaneContainmentTest {
    @Test fun extraPanesStayInsideAShortContainer() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = ChartState(scope)
            state.createIndicator("VOL", paneId = "vol")
            state.createIndicator("MACD", paneId = "macd")
            state.onLayout(2400f, ChartState.PaneHeights(300f, 120f, 120f, 60f))
            state.extraPanes.value = (0 until 4).map { "extra$it" }
            state.relayoutExtraPanes()
            assertTrue("time axis must remain inside its 600px chart", state.contentBottom() + state.paneHeightsPx.timeAxis <= 600.01f)
            val panes = listOf("main", "vol", "macd", "extra0", "extra1", "extra2", "extra3").map { state.paneBounds(it)!! }
            panes.forEach { assertTrue(it.first >= 0 && it.second > it.first) }
            panes.zipWithNext().forEach { (a, b) -> assertTrue(a.second <= b.first) }
        } finally { scope.cancel() }
    }
}
