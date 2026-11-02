package io.lkanvas.chart

import io.lkanvas.compose.state.ChartState
import io.lkanvas.core.axis.LinearYAxis
import io.lkanvas.core.data.Candle
import io.lkanvas.core.store.MarketKey
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.floor

class MainPaneRangeTest {
    @Test fun projectingOverlayPointsObservesEachSnapshotOnce() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = ChartState(scope)
            state.reset(MarketKey("OVERLAY", "1m"), (0 until 200).map {
                Candle(it * 60000L, 100.0, 101.0, 99.0, 100.0, 1.0)
            })
            state.onLayout(400f, ChartState.PaneHeights(500f, 0f, 0f, 50f))
            val values = List(200) { 99.0 + (it % 7) / 3.0 }
            val expected = values.map { state.mainPaneYOf(it) }
            var reads = 0
            val actual = androidx.compose.runtime.snapshots.Snapshot.observe(readObserver = { reads++ }) {
                values.map { state.paneValueToY("main", it) }
            }
            assertEquals(expected, actual)
            assertEquals("each overlay point must use the shared range's single snapshot observation", values.size, reads)
        } finally { scope.cancel() }
    }

    @Test fun coordinateRangeFollowsPanZoomCorrectionsAndMarketReplacement() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = ChartState(scope)
            val candles = (0 until 200).map { Candle(it * 60000L, 100.0, 101.0 + it, 99.0 - it / 3.0, 100.0, 1.0) }
            fun verify() {
                val data = state.dataSnapshot.candles
                if (data.isEmpty()) { assertNull(state.mainPaneRange()); return }
                val from = floor(state.viewport.scrollIndex).toInt().coerceIn(data.indices)
                val to = ceil(state.viewport.scrollIndex + state.viewport.visibleCount).toInt().coerceIn(data.indices)
                val expected = LinearYAxis.rangeOfCandles(data.subList(from, to + 1))!!
                repeat(5) { assertEquals(expected, state.mainPaneRange()) }
                val y = state.mainPaneYOf(100.0)!!
                assertEquals(100.0, state.mainPaneValueAt(y.toFloat())!!, 0.001)
            }
            state.reset(MarketKey("FIRST", "1m"), candles)
            state.onLayout(400f, ChartState.PaneHeights(500f, 0f, 0f, 50f))
            verify()
            state.panBy(150f); verify()
            state.zoomAt(1.5f, 200f); verify()
            val index = floor(state.viewport.scrollIndex).toInt().coerceIn(candles.indices)
            state.patchHistory(listOf(candles[index].copy(high = 1000.0))); verify()
            state.mainAxisReverse = true; verify()
            state.reset(MarketKey("SECOND", "1m"), candles.map { it.copy(high = it.high + 500) }); verify()
            state.reset(MarketKey("EMPTY", "1m"), emptyList()); verify()
            state.reset(MarketKey("THIRD", "1m"), candles); verify()
        } finally { scope.cancel() }
    }
}
