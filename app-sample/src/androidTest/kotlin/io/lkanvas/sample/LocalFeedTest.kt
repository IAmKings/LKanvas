package io.lkanvas.sample

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.lkanvas.core.common.DataLoader
import io.lkanvas.core.common.SymbolInfo
import io.lkanvas.core.data.Candle
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalFeedTest {
    @Test fun everyPeriodHasContiguousInitialDataAndThreeBoundedHistoryPages() = runBlocking(Dispatchers.Main) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val feed = LocalFeed(scope)
            for ((_, period) in samplePeriods) {
                var data = emptyList<Candle>()
                var more: DataLoader.DataLoadMore? = null
                fun load(type: DataLoader.DataLoadType, timestamp: Long? = null) {
                    feed.getBars(DataLoader.GetBarsParams(type, timestamp, SymbolInfo("DEMO"), period) { bars, next -> data = bars; more = next })
                }
                load(DataLoader.DataLoadType.INIT)
                assertEquals(500, data.size)
                val initial = data
                assertTrue(more!!.forward)
                assertFalse(more!!.backward)
                repeat(3) { page ->
                    val before = data.first().timestamp
                    load(DataLoader.DataLoadType.FORWARD, before)
                    assertEquals(200, data.size)
                    assertEquals(before - period.toMillis(), data.last().timestamp)
                    assertEquals(page < 2, more!!.forward)
                    assertTrue(data.all { it.high >= maxOf(it.open, it.close) && it.low <= minOf(it.open, it.close) })
                    assertTrue(data.zipWithNext().all { (a, b) -> b.timestamp - a.timestamp == period.toMillis() })
                }
                load(DataLoader.DataLoadType.FORWARD, data.first().timestamp)
                assertTrue(data.isEmpty())
                load(DataLoader.DataLoadType.INIT)
                assertEquals(initial.map { it.close }, data.map { it.close })
                assertTrue(data.zipWithNext().all { (a, b) -> b.timestamp - a.timestamp == period.toMillis() })
            }
        } finally { scope.cancel() }
    }

    @Test fun realtimeRespectsPeriodPauseUnsubscribeAndReplacementWicks() = runBlocking(Dispatchers.Main) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val feed = LocalFeed(scope)
            val symbol = SymbolInfo("DEMO")
            val period = samplePeriods[1].second
            var initial = emptyList<Candle>()
            feed.getBars(DataLoader.GetBarsParams(DataLoader.DataLoadType.INIT, null, symbol, period) { bars, _ -> initial = bars })
            val events = mutableListOf<Candle>()
            feed.subscribeBar(DataLoader.SubscribeBarParams(symbol, period) { events += it })
            withTimeout(5_000) { while (events.none { it.timestamp > initial.last().timestamp }) delay(50) }
            assertEquals(initial.last().timestamp + period.toMillis(), events.first { it.timestamp > initial.last().timestamp }.timestamp)
            (listOf(initial.last()) + events).zipWithNext().filter { (a, b) -> a.timestamp == b.timestamp }.forEach { (a, b) ->
                assertTrue(b.high >= a.high)
                assertTrue(b.low <= a.low)
            }
            feed.paused = true
            val count = events.size
            delay(450)
            assertEquals(count, events.size)
            feed.unsubscribeBar(DataLoader.UnsubscribeBarParams(symbol, period))
            feed.paused = false
            delay(450)
            assertEquals(count, events.size)
        } finally { scope.cancel() }
    }
}
