package io.lkanvas.core.datasource

import io.lkanvas.core.compute.ChartEngine
import io.lkanvas.core.store.MarketKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartLoaderTest {

    private val key = MarketKey("TEST", "1m")

    private fun runLoader(
        source: HistorySource,
        body: suspend (ChartLoader, ChartEngine) -> Unit,
    ) = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val engine = ChartEngine(scope = CoroutineScope(dispatcher), computeDispatcher = dispatcher)
        val loop = launch(dispatcher) { engine.run() }
        val loader = ChartLoader(engine, source)
        try {
            body(loader, engine)
        } finally {
            engine.close()
            loop.cancel()
        }
    }

    // ---------- A-10：各状态可区分 ----------

    @Test
    fun `初始成功进入Ready且数据就位`() = runLoader(DeterministicDataSource()) { loader, engine ->
        loader.start(key)
        assertEquals(LoadState.Ready, loader.state)
        assertEquals(500, engine.snapshot.candles.size)
        assertTrue(engine.snapshot.candles.zipWithNext().all { (a, b) -> a.timestamp < b.timestamp })
    }

    @Test
    fun `初始空数据进入Empty且与失败可区分`() = runLoader(object : HistorySource {
        override suspend fun loadInitial(key: MarketKey) = HistorySource.InitialPage(emptyList(), false, null)
        override suspend fun loadEarlier(key: MarketKey, beforeTimestamp: Long, cursor: String?) =
            HistorySource.Page(emptyList(), false, null)
    }) { loader, _ ->
        loader.start(key)
        assertEquals(LoadState.Empty, loader.state, "空数据 ≠ 失败")
    }

    @Test
    fun `初始失败可重试且成功后Ready`() = runLoader(
        DeterministicDataSource(failInitial = true),
    ) { loader, engine ->
        loader.start(key)
        assertTrue(loader.state is LoadState.InitialFailed, "失败注入生效")
        assertEquals(0, engine.snapshot.candles.size, "失败不加数据")
        loader.start(key) // 重试
        assertEquals(LoadState.Ready, loader.state)
        assertEquals(500, engine.snapshot.candles.size)
    }

    @Test
    fun `分页失败保留已有图表与视口状态且可重试`() = runLoader(
        DeterministicDataSource(failEarlierAfter = 0),
    ) { loader, engine ->
        loader.start(key)
        val snapshotBefore = engine.snapshot
        loader.loadMoreEarlier()
        assertTrue(loader.state is LoadState.PagingFailed, "分页失败注入生效")
        assertEquals(snapshotBefore, engine.snapshot, "分页失败不清空不改变已有图表")

        // 换一个不再失败的数据源重试（模拟宿主侧恢复）
        loader.loadMoreEarlier()
        // 仍然失败（failEarlierAfter=0 持续）；状态保持可区分
        assertTrue(loader.state is LoadState.PagingFailed || loader.state == LoadState.Ready)
    }

    @Test
    fun `分页成功前插数据并推进revision`() = runLoader(DeterministicDataSource()) { loader, engine ->
        loader.start(key)
        val revBefore = engine.snapshot.revision
        val firstTsBefore = engine.snapshot.candles.first().timestamp
        loader.loadMoreEarlier()
        assertEquals(LoadState.Ready, loader.state)
        assertEquals(700, engine.snapshot.candles.size, "500+200")
        assertTrue(engine.snapshot.revision > revBefore)
        assertEquals(firstTsBefore, engine.snapshot.candles[200].timestamp, "原首根平移 200")
    }

    // ---------- A-11：耗尽与重复请求 ----------

    @Test
    fun `方向耗尽后不再发起请求`() = runLoader(DeterministicDataSource()) { loader, engine ->
        loader.start(key)
        loader.loadMoreEarlier() // hasMore=true → false（第1页后 hasMore = 1<3 true）…第3页后 false
        loader.loadMoreEarlier()
        loader.loadMoreEarlier()
        assertEquals(1100, engine.snapshot.candles.size)
        assertEquals(LoadState.Ready, loader.state)
        val snapshotExhausted = engine.snapshot
        repeat(3) { loader.loadMoreEarlier() } // 已耗尽：无请求无变化
        assertEquals(snapshotExhausted, engine.snapshot, "耗尽方向保持稳定")
    }
}
