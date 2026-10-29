package io.lkanvas.core.common

/**
 * 上游 `common/DataLoader.ts` 的 Kotlin 镜像：数据加载契约。
 * 宿主实现 [getBars]/[subscribeBar]/[unsubscribeBar]；回调可同步或异步调用一次。
 *
 * - [getBars]：图表按 [DataLoadType] 主动拉取，实现方经 [GetBarsParams.callback]
 *   回交数据与 [DataLoadMore]（是否有更多）。
 * - [subscribeBar]/[unsubscribeBar]：init 成功后订阅；切换品种/周期时
 *   退订旧上下文（上游 Store._processDataUnsubscribe 顺序）。
 *
 * 线程：callback 允许在任意线程调用，引擎内部串行化提交。
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
        val callback: (data: List<KLineData>, more: DataLoadMore?) -> Unit,
    )

    data class SubscribeBarParams(val symbol: SymbolInfo, val period: Period, val callback: (KLineData) -> Unit)

    data class UnsubscribeBarParams(val symbol: SymbolInfo, val period: Period)
}
