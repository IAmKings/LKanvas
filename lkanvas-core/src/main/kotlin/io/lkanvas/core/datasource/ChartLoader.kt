package io.lkanvas.core.datasource

import io.lkanvas.core.compute.ChangeRequest
import io.lkanvas.core.compute.ChartEngine
import io.lkanvas.core.data.Candle
import io.lkanvas.core.store.ChangeResult
import io.lkanvas.core.store.MarketKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 数据加载协调器（PRD R-04）：DataSource 与直接数据变更不能同时成为同一图表的
 * 独立写入者——本类是唯一经 [ChartEngine] 提交变更的入口（初始 Reset + 分页
 * PrependHistory）。
 *
 * - 两个方向 hasMore 独立管理；同方向在途请求不重复发起（A-11）
 * - 分页失败保留已有图表与视口；重试恢复（A-10）
 * - 状态可区分：InitialLoading/Ready/Empty/InitialFailed/PagingEarlier/PagingFailed
 */
class ChartLoader(
    private val engine: ChartEngine,
    private val source: HistorySource,
) {

    /** 加载状态（单写读；UI 层轮询或经 [onStateChange] 观察）。 */
    var state: LoadState = LoadState.Idle
        private set

    var onStateChange: ((LoadState) -> Unit)? = null

    private var hasMoreEarlier = false
    private var earlierCursor: String? = null
    private var earlierInFlight = false
    private val mutex = Mutex()

    private fun setState(next: LoadState) {
        state = next
        onStateChange?.invoke(next)
    }

    /** 开始/重试初始加载。 */
    suspend fun start(key: MarketKey) = mutex.withLock {
        setState(LoadState.InitialLoading)
        when (val outcome = runCatching { source.loadInitial(key) }.toOutcome()) {
            is LoadOutcome.Failed -> setState(LoadState.InitialFailed(outcome.error))
            is LoadOutcome.Ok -> {
                val page = outcome.value
                when {
                    page.candles.isEmpty() -> {
                        engine.apply(ChangeRequest.Reset(key, emptyList()))
                        hasMoreEarlier = false
                        setState(LoadState.Empty)
                    }
                    else -> {
                        val result = engine.apply(ChangeRequest.Reset(key, page.candles))
                        if (result is ChangeResult.Rejected) {
                            setState(LoadState.InitialFailed(IllegalStateException("数据被拒绝: ${result.reason}")))
                        } else {
                            hasMoreEarlier = page.hasMoreEarlier
                            earlierCursor = page.cursorEarlier
                            setState(LoadState.Ready)
                        }
                    }
                }
            }
        }
    }

    /**
     * 加载更早一页（视口拖到左缘时由 UI 调用）。
     * 在途/已耗尽时直接返回不重复请求（A-11）；失败保留图表与视口。
     */
    suspend fun loadMoreEarlier() = mutex.withLock {
        if (earlierInFlight || !hasMoreEarlier || state is LoadState.InitialLoading) return
        val firstTs = engine.snapshot.candles.firstOrNull()?.timestamp ?: return
        earlierInFlight = true
        setState(LoadState.PagingEarlier)
        try {
            when (val outcome = runCatching { source.loadEarlier(currentKey(), firstTs, earlierCursor) }.toOutcome()) {
                is LoadOutcome.Failed -> setState(LoadState.PagingFailed(outcome.error))
                is LoadOutcome.Ok -> {
                    val page = outcome.value
                    if (page.candles.isNotEmpty()) {
                        engine.apply(ChangeRequest.PrependHistory(page.candles))
                    }
                    hasMoreEarlier = page.hasMore
                    earlierCursor = page.cursor
                    setState(LoadState.Ready)
                }
            }
        } finally {
            earlierInFlight = false
        }
    }

    private fun currentKey(): MarketKey = engine.snapshot.marketKey ?: MarketKey("unknown", "1m")

    private fun <T> Result<T>.toOutcome(): LoadOutcome<T> =
        fold({ LoadOutcome.Ok(it) }, { LoadOutcome.Failed(it) })
}

/**
 * 本地确定性数据源（固定种子随机行情）：分页与实时联调的示例实现（PRD R-04）。
 * 同一 seed+symbol 总产生相同序列。
 */
class DeterministicDataSource(
    private val seed: Int = 2026,
    private val pageSize: Int = 200,
    private val initialSize: Int = 500,
    private val periodMs: Long = 60_000L,
    /** 注入失败（测试 A-10/A-11 用）。 */
    private var failInitial: Boolean = false,
    private var failEarlierAfter: Int = Int.MAX_VALUE,
) : HistorySource {

    private var earlierCalls = 0
    private val anchorTs = 1_800_000_000_000L / periodMs * periodMs // 固定锚点，序列可复现

    /** 生成 [0, count) 的 candle：时间戳 anchor - (count-i)*period，价格由 seed 派生。 */
    private fun generate(symbolSeed: Int, count: Int, endExclusiveTs: Long): List<Candle> {
        val rand = java.util.Random((symbolSeed * 1_000_003 + count).toLong() + seed)
        var price = 100.0
        val startTs = endExclusiveTs - count * periodMs
        return (0 until count).map { i ->
            val open = price
            val close = open + (rand.nextDouble() - 0.5) * 2
            val high = maxOf(open, close) + rand.nextDouble() * 0.8
            val low = minOf(open, close) - rand.nextDouble() * 0.8
            price = close
            Candle(
                timestamp = startTs + i * periodMs,
                open = open, high = high, low = low, close = close,
                volume = rand.nextDouble() * 5000 + 100,
            )
        }
    }

    private fun symbolSeed(key: MarketKey) = key.symbol.hashCode()

    override suspend fun loadInitial(key: MarketKey): HistorySource.InitialPage {
        if (failInitial) {
            failInitial = false
            throw IllegalStateException("初始加载失败（测试注入）")
        }
        val candles = generate(symbolSeed(key), initialSize, anchorTs)
        return HistorySource.InitialPage(
            candles = candles,
            hasMoreEarlier = true,
            cursorEarlier = "page-0",
        )
    }

    override suspend fun loadEarlier(key: MarketKey, beforeTimestamp: Long, cursor: String?): HistorySource.Page {
        earlierCalls++
        if (earlierCalls > failEarlierAfter) throw IllegalStateException("分页失败（测试注入）")
        val candles = generate(symbolSeed(key) + earlierCalls, pageSize, beforeTimestamp)
        return HistorySource.Page(
            candles = candles,
            hasMore = earlierCalls < 3, // 3 页后耗尽（测试 A-11）
            cursor = "page-$earlierCalls",
        )
    }
}
