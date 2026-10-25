package io.lkanvas.core.datasource

import io.lkanvas.core.data.Candle
import io.lkanvas.core.store.MarketKey

/**
 * 历史数据源契约（PRD R-04）：只定义请求语义，不内置 REST/WebSocket 客户端。
 * 由宿主实现；库提供本地确定性实现 [DeterministicDataSource] 作示例与测试。
 */
interface HistorySource {

    /** 初始加载（最新一屏向前的数据）。 */
    suspend fun loadInitial(key: MarketKey): InitialPage

    /** 向前（更早）分页；[beforeTimestamp] 为当前已加载数据的首根时间戳。 */
    suspend fun loadEarlier(key: MarketKey, beforeTimestamp: Long, cursor: String?): Page

    data class InitialPage(
        val candles: List<Candle>,
        val hasMoreEarlier: Boolean,
        val cursorEarlier: String?,
    )

    data class Page(
        val candles: List<Candle>,
        val hasMore: Boolean,
        val cursor: String?,
    )
}

/** 可失败结果（加载失败与空数据、取消必须可区分，PRD R-04/A-10）。 */
sealed interface LoadOutcome<out T> {
    data class Ok<out T>(val value: T) : LoadOutcome<T>
    data class Failed(val error: Throwable) : LoadOutcome<Nothing>
}

/** 实时订阅契约（离开图表/切会话时必须结束订阅，PRD R-04）。 */
interface RealtimeSource {
    fun subscribe(key: MarketKey, onCandle: (Candle) -> Unit, onDisconnected: (cause: Throwable?) -> Unit): Subscription
}

interface Subscription {
    fun unsubscribe()
}

/** 图表加载状态（可区分：初始加载/空/失败/分页中/分页失败/断流，A-10）。 */
sealed interface LoadState {
    data object Idle : LoadState
    data object InitialLoading : LoadState
    data object Ready : LoadState
    data object Empty : LoadState
    data class InitialFailed(val error: Throwable) : LoadState

    /** 分页进行中（图表保持可用）。 */
    data object PagingEarlier : LoadState

    /** 分页失败：已有图表与视口保留（不清空）。 */
    data class PagingFailed(val error: Throwable) : LoadState

    /** 实时断流（历史仍可用；宿主决定重连策略）。 */
    data class RealtimeDisconnected(val cause: Throwable?) : LoadState
}
