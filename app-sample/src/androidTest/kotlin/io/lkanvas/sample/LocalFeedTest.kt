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
    private suspend fun load(feed: LocalFeed, period: io.lkanvas.core.common.Period, type: DataLoader.DataLoadType = DataLoader.DataLoadType.INIT, timestamp: Long? = null): Pair<List<Candle>, DataLoader.DataLoadMore?> {
        val result = CompletableDeferred<Pair<List<Candle>, DataLoader.DataLoadMore?>>()
        feed.getBars(DataLoader.GetBarsParams(type, timestamp, SymbolInfo("DEMO"), period) { bars, more -> result.complete(bars to more) })
        return withTimeout(15_000) { result.await() }
    }

    private fun assertAggregates(bar: Candle, group: List<Candle>) {
        assertEquals(group.first().open, bar.open, 1e-9)
        assertEquals(group.maxOf { it.high }, bar.high, 1e-9)
        assertEquals(group.minOf { it.low }, bar.low, 1e-9)
        assertEquals(group.last().close, bar.close, 1e-9)
        assertEquals(group.sumOf { it.volume!! }, bar.volume!!, 1e-6)
    }

    @Test fun periodsAggregateSameMarketRatherThanRetimestampingPrices() = runBlocking(Dispatchers.Main) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val feed = LocalFeed(scope)
            val data = samplePeriods.map { (_, period) -> load(feed, period).first }
            for (i in 1 until samplePeriods.size) {
                val fine = data[i - 1]
                val coarse = data[i]
                val fineStep = samplePeriods[i - 1].second.toMillis()
                val coarseStep = samplePeriods[i].second.toMillis()
                assertTrue("${samplePeriods[i].first} must have different prices", fine.map { it.close } != coarse.map { it.close })
                val complete = coarse.mapNotNull { bar ->
                    val group = fine.filter { it.timestamp >= bar.timestamp && it.timestamp < bar.timestamp + coarseStep }
                    if (group.size == (coarseStep / fineStep).toInt() && group.last().timestamp < fine.last().timestamp) bar to group else null
                }.takeLast(5)
                assertTrue("overlapping complete buckets for ${samplePeriods[i].first}", complete.isNotEmpty())
                for ((bar, group) in complete) assertAggregates(bar, group)
                val historyFeed = LocalFeed(scope)
                val history = load(historyFeed, samplePeriods[i].second, DataLoader.DataLoadType.FORWARD, coarse.first().timestamp).first
                val fineHistory = load(historyFeed, samplePeriods[i - 1].second, DataLoader.DataLoadType.FORWARD, history.last().timestamp + coarseStep).first
                for (bar in history.takeLast(5)) {
                    val group = fineHistory.filter { it.timestamp >= bar.timestamp && it.timestamp < bar.timestamp + coarseStep }
                    assertEquals((coarseStep / fineStep).toInt(), group.size)
                    assertAggregates(bar, group)
                }
            }
        } finally { scope.cancel() }
    }

    @Test fun everyPeriodHasContiguousInitialDataAndThreeBoundedHistoryPages() = runBlocking(Dispatchers.Main) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val feed = LocalFeed(scope)
            for ((_, period) in samplePeriods) {
                var data = emptyList<Candle>()
                var more: DataLoader.DataLoadMore? = null
                suspend fun load(type: DataLoader.DataLoadType, timestamp: Long? = null) {
                    val result = load(feed, period, type, timestamp)
                    data = result.first
                    more = result.second
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
            val initial = load(feed, period).first
            val events = mutableListOf<Candle>()
            feed.subscribeBar(DataLoader.SubscribeBarParams(symbol, period) { events += it })
            withTimeout(8_000) { while (events.none { it.timestamp > initial.last().timestamp }) delay(50) }
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

    @Test fun livePartialBucketsAgreeWithMinuteReloadAfterSubscriptionReplacement() = runBlocking(Dispatchers.Main) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val feed = LocalFeed(scope)
            val symbol = SymbolInfo("DEMO")
            val minute = samplePeriods[0].second
            val five = samplePeriods[1].second
            val old = mutableListOf<Candle>()
            feed.subscribeBar(DataLoader.SubscribeBarParams(symbol, minute) { old += it })
            withTimeout(3_000) { while (old.isEmpty()) delay(25) }
            feed.unsubscribeBar(DataLoader.UnsubscribeBarParams(symbol, minute))
            val oldCount = old.size
            val events = mutableListOf<Candle>()
            feed.subscribeBar(DataLoader.SubscribeBarParams(symbol, five) {
                events += it
                if (events.size == 2) feed.paused = true
            })
            withTimeout(3_000) { while (events.size < 2) delay(25) }
            feed.paused = true
            val coarse = load(feed, five).first.last()
            val fine = load(feed, minute).first.filter { it.timestamp >= coarse.timestamp }
            assertEquals("the emitted live bar must equal the same-time reload", events.last(), coarse)
            assertAggregates(coarse, fine)
            assertEquals(oldCount, old.size)
            val count = events.size
            delay(450)
            assertEquals(count, events.size)
        } finally { scope.cancel() }
    }
}
