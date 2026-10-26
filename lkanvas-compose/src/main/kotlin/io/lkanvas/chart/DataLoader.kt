package io.lkanvas.chart

import io.lkanvas.core.data.Candle

/**
 * 数据加载契约——与上游 KLineChart v10 `common/DataLoader.ts` 同名同语义
 * （PRD 对齐批次 2a；WebView 迁移者按上游文档直接实现本接口）。
 *
 * - [getBars]：图表按 [DataLoadType] 主动拉取；实现方通过 [GetBarsParams.callback]
 *   回交数据与 [DataLoadMore]（是否有更多），回调可同步或异步调用一次。
 * - [subscribeBar]/[unsubscribeBar]：init 成功后图表自动订阅/切会话自动退订
 *   （上游 Store._processDataLoad 行为一致）。
 *
 * 线程：callback 允许在任意线程调用，门面内部串行化提交（与内核引擎单写者一致）。
 */
interface DataLoader {

    fun getBars(params: GetBarsParams)

    fun subscribeBar(params: SubscribeBarParams) {}

    fun unsubscribeBar(params: UnsubscribeBarParams) {}

    /** `type: 'init' | 'forward' | 'backward' | 'update'`。 */
    enum class DataLoadType { INIT, FORWARD, BACKWARD, UPDATE }

    /**
     * 是否还有更多数据；布尔等价于双向同值（上游 `DataLoadMore = boolean | {backward, forward}`）。
     */
    data class DataLoadMore(val backward: Boolean, val forward: Boolean) {
        companion object {
            fun of(all: Boolean) = DataLoadMore(all, all)
            val NONE = DataLoadMore(false, false)
        }
    }

    data class GetBarsParams(
        val type: DataLoadType,
        /** forward=当前首根时间戳；backward=当前末根时间戳；init/update=null（上游一致）。 */
        val timestamp: Long?,
        val symbol: SymbolInfo,
        val period: Period,
        val callback: (data: List<Candle>, more: DataLoadMore?) -> Unit,
    )

    data class SubscribeBarParams(val symbol: SymbolInfo, val period: Period, val callback: (Candle) -> Unit)

    data class UnsubscribeBarParams(val symbol: SymbolInfo, val period: Period)
}
