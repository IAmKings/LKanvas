package io.lkanvas.core.store

import io.lkanvas.core.common.DataLoader
import io.lkanvas.core.common.Period
import io.lkanvas.core.common.SymbolInfo
import io.lkanvas.core.data.Candle

/**
 * 数据路径编排（上游 `Store.ts` 数据段的 Kotlin 镜像，批次 06 / SLA-005、006）。
 *
 * 镜像的执行顺序（与上游一致）：
 * - [processDataLoad]：`!loading && loader/symbol/period 有效` 门禁 → loading=true →
 *   backward 取末根时间戳 / forward 取首根时间戳 → `loader.getBars` →
 *   回调内 `loading=false → addData → (init 时) subscribeBar`。
 * - [addData]：init 清空重置并记录双向 more；backward 尾追并更新 **同名方向**
 *   `loadMoreBackward`；forward 头插并更新 `loadMoreForward`（上游 `_addData`
 *   case 'backward'/'forward' 各自更新同名字段）。
 * - [adjustVisibleRangeBoundary]：视口调整末尾的边界触发——`from==0 && more.forward`
 *   → forward 拉取；`to==total && more.backward` → backward 拉取（上游
 *   `_adjustVisibleRange` 末尾；门面分页触发入口，SLA-005）。
 * - [resetData]：**退订旧上下文 → 配置变更 → 清 loading → init 拉取**（上游
 *   `Store.resetData` 顺序，SLA-006）。
 *
 * 本地增强（上游无，D 类登记 SLA-006）：**请求代次门禁**——每次 [processDataLoad]
 * 递增 [generation] 并捕获，回调与订阅推送校验代次，旧代次回调整体丢弃，
 * 在途切品种时旧数据不会写入新会话（上游依赖退订时序，无门禁）。
 *
 * 线程：非线程安全；宿主（门面）负责在单写者上下文中调用（回调可来自任意线程，
 * 由宿主串行化后转发给 [Host] 的应用函数）。
 */
class Store(private val host: Host) {

    /** 串行化锁：回调可能来自任意线程（loader 异步回调/订阅推送）。 */
    private val lock = Any()

    /** 宿主桥：数据应用与快照查询（由内核状态的实现方提供）。 */
    interface Host {
        /** init：清空重置并写入初始数据；返回是否接受。 */
        fun applyInit(data: List<Candle>): Boolean

        /** backward：尾部追加；返回是否接受。 */
        fun applyBackward(data: List<Candle>): Boolean

        /** forward：头部前插；返回是否接受。 */
        fun applyForward(data: List<Candle>): Boolean

        /**
         * update：单根推送——大于末根 push、等于替换、小于忽略（上游 `_addData`
         * 单根分支）；返回是否生效（含 adjustFlag 判定）。
         */
        fun applyUpdate(candle: Candle): Boolean

        /** 当前数据根数（to===total 判定）。 */
        fun candleCount(): Int

        fun firstTimestamp(): Long?
        fun lastTimestamp(): Long?

        /** 成功写入且需要调整视口/重算（上游 `_addData` 尾部 adjustFlag 分支）。 */
        fun onDataAdjusted(type: DataLoader.DataLoadType)
    }

    var dataLoader: DataLoader? = null
        private set
    var symbol: SymbolInfo? = null
        private set
    var period: Period? = null
        private set

    private var loading = false

    /** 上游 `_dataLoadMore = { forward, backward }`（同名字段，各自更新）。 */
    var loadMoreForward = false
        private set
    var loadMoreBackward = false
        private set

    /** 请求代次（本地增强）：每次拉取递增；回调校验不匹配即丢弃。 */
    internal var generation = 0L
        private set

    /** 诊断用：当前请求代次（测试探针）。 */
    val generationForProbe: Long get() = generation

    // ---------- 配置（上游 setSymbol/setPeriod/setDataLoader 经 resetData 变更） ----------

    /** 上游 `setDataLoader(loader)`：resetData(() => this._dataLoader = loader)。 */
    fun setDataLoader(loader: DataLoader) = synchronized(lock) {
        resetData { dataLoader = loader }
    }

    /** 上游 `setSymbol(symbol)`：resetData 内变更（退订旧上下文先行）。 */
    fun setSymbol(value: SymbolInfo) = synchronized(lock) {
        resetData { symbol = value }
    }

    /** 上游 `setPeriod(period)`。 */
    fun setPeriod(value: Period) = synchronized(lock) {
        resetData { period = value }
    }

    /**
     * 上游 `resetData(fn?)`：
     * 退订旧上下文 → fn 配置变更 → loading=false → init 拉取。
     */
    fun resetData(fn: (() -> Unit)? = null) = synchronized(lock) {
        processDataUnsubscribe()
        fn?.invoke()
        loading = false
        processDataLoad(DataLoader.DataLoadType.INIT)
    }

    /** 释放数据订阅（上游 dispose → _processDataUnsubscribe；门面 destroy 调用）。 */
    fun unsubscribeData() = synchronized(lock) { processDataUnsubscribe() }

    // ---------- 拉取（上游 _processDataLoad/_processDataUnsubscribe） ----------

    private fun processDataUnsubscribe() {
        val l = dataLoader
        val s = symbol
        val p = period
        if (l != null && s != null && p != null) {
            generation++ // 使在途回调/旧订阅推送失效（本地代次门禁）
            l.unsubscribeBar(DataLoader.UnsubscribeBarParams(s, p))
        }
    }

    private fun processDataLoad(type: DataLoader.DataLoadType) {
        val l = dataLoader ?: return
        val s = symbol ?: return
        val p = period ?: return
        if (loading) return
        loading = true
        val gen = ++generation
        val timestamp = when (type) {
            DataLoader.DataLoadType.BACKWARD -> host.lastTimestamp()
            DataLoader.DataLoadType.FORWARD -> host.firstTimestamp()
            else -> null
        }
        l.getBars(
            DataLoader.GetBarsParams(
                type = type,
                timestamp = timestamp,
                symbol = s,
                period = p,
                callback = { data, more ->
                    if (gen != generation) return@GetBarsParams // 旧代次回调丢弃
                    loading = false
                    addData(data, type, more)
                    if (type == DataLoader.DataLoadType.INIT) {
                        l.subscribeBar(
                            DataLoader.SubscribeBarParams(s, p) { candle ->
                                if (gen != generation) return@SubscribeBarParams // 旧订阅推送丢弃
                                addUpdate(candle)
                            },
                        )
                    }
                },
            ),
        )
    }

    // ---------- 写入（上游 _addData 数组分支） ----------

    fun addData(data: List<Candle>, type: DataLoader.DataLoadType, more: DataLoader.DataLoadMore?) = synchronized(lock) {
        val realMoreBackward = when {
            more == null -> false
            else -> more.backward
        }
        val realMoreForward = when {
            more == null -> false
            else -> more.forward
        }
        var success = false
        var adjustFlag = false
        when (type) {
            DataLoader.DataLoadType.INIT -> {
                success = host.applyInit(data)
                loadMoreBackward = realMoreBackward
                loadMoreForward = realMoreForward
                adjustFlag = true
            }
            DataLoader.DataLoadType.BACKWARD -> {
                success = host.applyBackward(data)
                // 上游 case 'backward'：更新同名 _dataLoadMore.backward
                loadMoreBackward = realMoreBackward
                adjustFlag = data.isNotEmpty()
            }
            DataLoader.DataLoadType.FORWARD -> {
                success = host.applyForward(data)
                // 上游 case 'forward'：更新同名 _dataLoadMore.forward
                loadMoreForward = realMoreForward
                adjustFlag = data.isNotEmpty()
            }
            DataLoader.DataLoadType.UPDATE -> {
                // 数组 update（上游 v10 数组分支无 update case；按单根逐条）
                data.forEach { adjustFlag = addUpdate(it) || adjustFlag }
                success = true
            }
        }
        if (success && adjustFlag) {
            host.onDataAdjusted(type)
        }
    }

    /** 上游 `_addData` 单根分支：大于末根 push（末根右侧留白随动）、等于替换、小于忽略。 */
    fun addUpdate(candle: Candle): Boolean = synchronized(lock) { host.applyUpdate(candle) }

    // ---------- 边界触发（上游 _adjustVisibleRange 末尾） ----------

    /**
     * 视口变化后由宿主调用：`from==0 && more.forward` → forward 拉取；
     * `to==total && more.backward` → backward 拉取。loading 门禁在
     * [processDataLoad] 内（上游一致：拉取中不重入）。
     */
    fun adjustVisibleRangeBoundary(from: Int, to: Int) = synchronized(lock) {
        if (from == 0 && loadMoreForward) {
            processDataLoad(DataLoader.DataLoadType.FORWARD)
        } else if (to == host.candleCount() && loadMoreBackward) {
            processDataLoad(DataLoader.DataLoadType.BACKWARD)
        }
    }
}

