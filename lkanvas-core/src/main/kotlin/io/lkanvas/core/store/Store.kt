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
 * 本地增强（上游无，D 类登记 SLA-006/R01/R02）：
 * - **请求代次门禁**：每次 [processDataLoad] 递增 [generation]，仅丢弃同向在途请求回调；
 * - **订阅会话代次** [subscriptionGeneration]：仅退订/重置递增——普通分页不使
 *   同品种实时订阅失效（R01，上游订阅跨分页存活 Store.ts:779）；
 * - **检查点 key 绑定**：应用动作携带接受时解析的 [MarketKey]（R02），
 *   宿主异步提交不得重读可变当前上下文。
 *
 * 线程：非线程安全；宿主（门面）负责在单写者上下文中调用（回调可来自任意线程，
 * 由宿主串行化后转发给 [Host] 的应用函数）。
 */
class Store(private val host: Host) {

    /** 串行化锁：回调可能来自任意线程（loader 异步回调/订阅推送）。 */
    private val lock = Any()

    /**
     * 宿主桥：数据应用与快照查询（由内核状态的实现方提供）。
     *
     * R02（复审 2026-10-30）：[key] 由 [Store] 在**接受检查的同一锁临界区内**
     * 解析并随动作传递——宿主的异步应用必须使用该 key，不得在执行期重新读取
     * 可变当前上下文（否则排队中的切换会把旧回调数据标记成新品种）。
     */
    interface Host {
        /** init：清空重置并写入初始数据；返回是否接受。 */
        fun applyInit(key: MarketKey, data: List<Candle>): Boolean

        /** backward：尾部追加；返回是否接受。 */
        fun applyBackward(key: MarketKey, data: List<Candle>): Boolean

        /** forward：头部前插；返回是否接受。 */
        fun applyForward(key: MarketKey, data: List<Candle>): Boolean

        /**
         * update：单根推送——大于末根 push、等于替换、小于忽略（上游 `_addData`
         * 单根分支）；返回是否生效（含 adjustFlag 判定）。
         */
        fun applyUpdate(key: MarketKey, candle: Candle): Boolean

        /** 当前数据根数（to===total 判定）。 */
        fun candleCount(): Int

        fun firstTimestamp(): Long?
        fun lastTimestamp(): Long?
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

    /**
     * 请求代次（本地增强）：每次拉取递增；**仅**用于丢弃同向在途请求的回调——
     * FORWARD/BACKWARD 递增不得影响实时订阅（R01，复审 2026-10-30：上游分页
     * 不替换/废弃同会话订阅，Store.ts:779）。
     */
    internal var generation = 0L
        private set

    /**
     * 订阅会话代次（R01 修复）：仅在退订/重置（[processDataUnsubscribe]）时递增；
     * 实时订阅回调校验本代次——普通分页请求的 [generation] 递增不再使
     * 同品种订阅推送失效。
     */
    internal var subscriptionGeneration = 0L
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
            generation++ // 使在途回调失效（本地代次门禁）
            subscriptionGeneration++ // R01：订阅会话代次——仅退订/重置时递增
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
        val subGen = subscriptionGeneration
        // R02：请求时会话快照——回调的应用动作绑定该 key（检查点解析，
        // 不在宿主协程执行期重读可变上下文）
        val requestKey = MarketKey(s.ticker, p.toString())
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
                    addData(data, type, more, requestKey)
                    if (type == DataLoader.DataLoadType.INIT) {
                        l.subscribeBar(
                            DataLoader.SubscribeBarParams(s, p) { candle ->
                                // R01：校验**订阅会话**代次——分页请求的
                                // generation 递增不影响同品种实时订阅
                                if (subGen != subscriptionGeneration) return@SubscribeBarParams
                                addUpdate(candle, requestKey)
                            },
                        )
                    }
                },
            ),
        )
    }

    // ---------- 写入（上游 _addData 数组分支） ----------

    fun addData(
        data: List<Candle>,
        type: DataLoader.DataLoadType,
        more: DataLoader.DataLoadMore?,
        requestKey: MarketKey? = null,
    ) = synchronized(lock) {
        // R02：key 在接受检查的同一锁临界区内确定——排队中的切换在本块
        // 之后才能改 symbol，应用动作使用检查点绑定的会话 key
        val key = requestKey ?: MarketKey(symbol?.ticker ?: "UNKNOWN", period?.toString() ?: "UNKNOWN")
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
                success = host.applyInit(key, data)
                loadMoreBackward = realMoreBackward
                loadMoreForward = realMoreForward
                adjustFlag = true
            }
            DataLoader.DataLoadType.BACKWARD -> {
                success = host.applyBackward(key, data)
                // 上游 case 'backward'：更新同名 _dataLoadMore.backward
                loadMoreBackward = realMoreBackward
                adjustFlag = data.isNotEmpty()
            }
            DataLoader.DataLoadType.FORWARD -> {
                success = host.applyForward(key, data)
                // 上游 case 'forward'：更新同名 _dataLoadMore.forward
                loadMoreForward = realMoreForward
                adjustFlag = data.isNotEmpty()
            }
            DataLoader.DataLoadType.UPDATE -> {
                // 数组 update（上游 v10 数组分支无 update case；按单根逐条）
                data.forEach { adjustFlag = addUpdate(it, key) || adjustFlag }
                success = true
            }
        }
        // R03：上游 _addData 尾部 adjustFlag → _adjustVisibleRange 的范围调整
        // 与边界续拉，由宿主在数据**实际提交完成**后统一执行（onSnapshot 驱动），
        // 此处不再回调 onDataAdjusted（提交前时机不可靠）
    }

    /** 上游 `_addData` 单根分支：大于末根 push（末根右侧留白随动）、等于替换、小于忽略。 */
    fun addUpdate(candle: Candle, requestKey: MarketKey? = null): Boolean = synchronized(lock) {
        val key = requestKey ?: MarketKey(symbol?.ticker ?: "UNKNOWN", period?.toString() ?: "UNKNOWN")
        host.applyUpdate(key, candle)
    }

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

