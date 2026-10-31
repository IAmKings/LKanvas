package io.lkanvas.chart

import io.lkanvas.compose.state.ChartState
import io.lkanvas.core.data.Candle
import io.lkanvas.core.store.MarketKey
import io.lkanvas.core.store.Store
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 图表门面——与上游 KLineChart v10 `Chart.ts` 公开 API 同名同语义
 * （PRD 对齐批次 2a；WebView 迁移者按上游文档调用，仅初始化方式不同）。
 *
 * 架构：门面镜像上游 API 面，内部委托现有内核（ChartState/ChartEngine），
 * 不改内核实现（对齐计划 §0 决策）。语义对照：
 * - 单根 update：大于末根 push、等于替换、小于**静默忽略**（与上游 _addData 一致；
 *   与内核 upsertLatest 的显式拒绝相比，门面层吞掉 Reject 以镜像上游行为，
 *   严格校验语义仍保留在内核层，差异登记于 gap-matrix）。
 * - getBars('init'|'forward'|'backward') 触发时机与 more 语义与上游一致：
 *   init 后自动 subscribeBar；视口到达边界且 more 为 true 时再拉取。
 *
 * Compose 集成：门面持有 [state]，宿主把 state 交给 `KLineChart(state)` 即可。
 */
class LKanvasChart private constructor(
    val state: ChartState,
    private val scope: CoroutineScope,
) {

    companion object {
        const val VERSION = "0.11.0"

        /** 上游 `init(ds, options?)`：Android 无 DOM，直接构造（同一 state 重复 init 返回既有实例）。 */
        private val liveCharts = java.util.concurrent.ConcurrentHashMap<ChartState, LKanvasChart>()

        fun init(state: ChartState = ChartState()): LKanvasChart =
            liveCharts.getOrPut(state) { LKanvasChart(state, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) }

        /** 上游 `dispose(dcs)`。 */
        fun dispose(chart: LKanvasChart) {
            liveCharts.remove(stateOf(chart))
            chart.destroy()
        }

        /** 上游 `version()`。 */
        fun version(): String = VERSION

        private fun stateOf(chart: LKanvasChart) = chart.state
    }

    // ---------- 品种/周期（Store 持有；上游 Store.setSymbol/setPeriod 经 resetData 变更） ----------

    internal val symbol: SymbolInfo? get() = store.symbol

    internal val period: Period? get() = store.period

    /** 上游 `setSymbol`：切品种（退订旧上下文 → 变更 → 清 loading → init 重载）。 */
    fun setSymbol(symbol: SymbolInfo) {
        state.pricePrecision = symbol.pricePrecision // Q06：格式链消费源
        scope.launch { store.setSymbol(symbol) }
    }

    /** 上游 `getSymbol`。 */
    fun getSymbol(): SymbolInfo? = store.symbol

    /** 上游 `setPeriod`：切周期（同上顺序）。 */
    fun setPeriod(period: Period) {
        scope.launch { store.setPeriod(period) }
    }

    /** 上游 `getPeriod`。 */
    fun getPeriod(): Period? = store.period

    // ---------- 视口（对齐 Chart.ts 视口组） ----------

    /** 上游 `setBarSpace(space)`。 */
    fun setBarSpace(space: Double) {
        state.viewport.barSpacing = space.coerceIn(state.viewport.minBarSpacing, state.viewport.maxBarSpacing)
        state.notifyViewportChanged()
    }

    /** 上游 `getBarSpace()`：BarSpace{bar,halfBar,gapBar,halfGapBar}（gap 比例 0.25 与绘制一致）。 */
    fun getBarSpace(): BarSpace {
        val bar = state.viewport.barSpacing
        val gap = bar * state.gapRatio
        return BarSpace(bar = bar, halfBar = bar / 2, gapBar = gap, halfGapBar = gap / 2)
    }

    /** 上游 `getVisibleRange()`：to 按数据量钳制（上游 Store._adjustVisibleRange）。 */
    fun getVisibleRange(): VisibleRange {
        val vp = state.viewport
        val from = kotlin.math.floor(vp.scrollIndex).toInt().coerceAtLeast(0)
        val to = kotlin.math.ceil(vp.scrollIndex + vp.visibleCount).toInt()
            .coerceAtMost(state.dataSnapshot.candles.size)
        return VisibleRange(from = from, to = to, realFrom = vp.scrollIndex, realTo = vp.scrollIndex + vp.visibleCount)
    }

    /**
     * 视口动画（上游 Animation 逐帧语义的等价实现）：固定 16ms 帧步进，
     * 线性插值增量；duration 为空/≤0 或增量为 0 时立即完成。
     */
    private fun animate(totalDelta: Double, animationDuration: Long?, apply: (Double) -> Unit) {
        if (animationDuration == null || animationDuration <= 0 || totalDelta == 0.0) {
            apply(totalDelta)
            return
        }
        scope.launch {
            val frames = (animationDuration / 16L).coerceIn(1L, 240L)
            var applied = 0.0
            repeat(frames.toInt()) { f ->
                val target = totalDelta * (f + 1) / frames
                apply(target - applied)
                applied = target
                kotlinx.coroutines.delay(16)
            }
            if (applied != totalDelta) apply(totalDelta - applied)
        }
    }

    /** 上游 `scrollByDistance(distance, animationDuration?)`。 */
    fun scrollByDistance(distance: Double, animationDuration: Long? = null) {
        animate(distance, animationDuration) { d -> state.panBy(d.toFloat()) }
    }

    /** 上游 `scrollToRealTime(animationDuration?)`：回到最新（按距离差动画）。 */
    fun scrollToRealTime(animationDuration: Long? = null) {
        val vp = state.viewport
        val total = state.dataSnapshot.candles.size
        val delta = (vp.maxScrollFor(total) - vp.scrollIndex) * vp.barSpacing
        scrollByDistance(delta, animationDuration)
    }

    /**
     * 上游 `scrollToDataIndex(dataIndex, animationDuration?)`（R11，复审
     * 2026-10-30）：按上游 Chart.ts:1171-1173 距离模型——目标落点与当前的
     * 距离差经 [scrollByDistance] 动画（panBy 增量驱动同一状态入口）。
     */
    fun scrollToDataIndex(dataIndex: Int, animationDuration: Long? = null) {
        val vp = state.viewport
        val total = state.dataSnapshot.candles.size
        // 目标：右锚模型落点（dataIndex 距右缘 rightOffset 处）
        val targetScroll = dataIndex - vp.visibleCount + vp.effectiveRightSlotsFor()
        val distance = (targetScroll - vp.scrollIndex) * vp.barSpacing
        scrollByDistance(distance, animationDuration)
    }

    /** 上游 `scrollToTimestamp(timestamp, animationDuration?)`。 */
    fun scrollToTimestamp(timestamp: Long, animationDuration: Long? = null): Boolean {
        val candles = state.dataSnapshot.candles
        val idx = Viewport_indexOfTimestamp(timestamp, candles) ?: return false
        scrollToDataIndex(idx, animationDuration)
        return true
    }

    private fun Viewport_indexOfTimestamp(timestamp: Long, candles: List<Candle>): Int? =
        io.lkanvas.core.viewport.Viewport.indexOfTimestamp(timestamp, candles)

    /**
     * 上游 `zoomAtCoordinate(scale, coordinate?, animationDuration?)`：缺省锚按
     * zoomAnchor 解析（SLA-011 消费：LAST_BAR=末根中心；CURSOR=视口中心）。
     */
    fun zoomAtCoordinate(scale: Double, coordinate: Pair<Double, Double>? = null, animationDuration: Long? = null) {
        val vp = state.viewport
        val anchorX = when {
            coordinate != null -> coordinate.first.toFloat()
            zoomAnchor.main == io.lkanvas.chart.ZoomAnchor.Anchor.LAST_BAR ->
                vp.candleCenterX((state.dataSnapshot.candles.size - 1).coerceAtLeast(0).toDouble()).toFloat()
            else -> vp.contentWidth.toFloat() / 2f
        }
        if (animationDuration != null && animationDuration > 0) {
            // 上游公式：barSpace 线性插值；animate 传**每帧增量**（px），帧 scale = (prev+delta)/prev
            val startBar = vp.barSpacing
            val difSpace = startBar * scale - startBar
            animate(difSpace, animationDuration) { deltaPx ->
                val prev = vp.barSpacing
                if (prev > 0) {
                    val frameScale = ((prev + deltaPx) / prev).toFloat()
                    if (frameScale != 1f) state.zoomAt(frameScale, anchorX)
                }
            }
        } else {
            state.zoomAt(scale.toFloat(), anchorX)
        }
    }

    /** 上游 `zoomAtDataIndex`：以该索引**当前像素**为缩放锚点（不先滚动；scale=1 无位移）。 */
    fun zoomAtDataIndex(scale: Double, dataIndex: Int, animationDuration: Long? = null) {
        val vp = state.viewport
        val anchorX = vp.candleCenterX(dataIndex.toDouble()).toFloat()
        zoomAtCoordinate(scale, Pair(anchorX.toDouble(), 0.0), animationDuration)
    }

    /** 上游 `zoomAtTimestamp(scale, timestamp, animationDuration?)`。 */
    fun zoomAtTimestamp(scale: Double, timestamp: Long, animationDuration: Long? = null): Boolean {
        val idx = Viewport_indexOfTimestamp(timestamp, state.dataSnapshot.candles) ?: return false
        zoomAtDataIndex(scale, idx, animationDuration)
        return true
    }

    /** 上游 `setOffsetRightDistance(distance)`：px→槽位（保留小数，SLA-014）。 */
    fun setOffsetRightDistance(distance: Double) {
        offsetRightDistanceInternal = distance
        state.viewport.rightOffsetSlots =
            if (state.viewport.barSpacing > 0) distance / state.viewport.barSpacing else 0.0
        state.notifyViewportChanged()
    }

    /** init 重载后按上游 `_addData` init 分支重放（Store.Host.onDataAdjusted 消费）。 */
    private var offsetRightDistanceInternal: Double? = null

    /** 上游 `getOffsetRightDistance()`：按当前 barSpacing 反算 px。 */
    fun getOffsetRightDistance(): Double = state.viewport.rightOffsetSlots * state.viewport.barSpacing

    /** 上游 `setMaxOffsetLeftDistance(distance)`：左向越界（独立）。 */
    fun setMaxOffsetLeftDistance(distance: Double) {
        state.viewport.overscrollSlots = if (state.viewport.barSpacing > 0) distance / state.viewport.barSpacing else 0.0
    }

    /** 上游 `setMaxOffsetRightDistance(distance)`：右向越界（独立于左向/初始留白，SLA-014）。 */
    fun setMaxOffsetRightDistance(distance: Double) {
        state.viewport.maxOffsetRightSlots =
            if (state.viewport.barSpacing > 0) distance / state.viewport.barSpacing else 0.0
        state.notifyViewportChanged()
    }

    /** 上游 `setLeftMinVisibleBarCount(count)`：滚动下界（可见根数钳制，clamp 内强制）。 */
    fun setLeftMinVisibleBarCount(count: Int) {
        state.viewport.leftMinVisibleBarCount = count.coerceAtLeast(0)
        state.notifyViewportChanged()
    }

    /** 上游 `setRightMinVisibleBarCount(count)`：滚动上界（可见根数钳制，clamp 内强制）。 */
    fun setRightMinVisibleBarCount(count: Int) {
        state.viewport.rightMinVisibleBarCount = count.coerceAtLeast(0)
        state.notifyViewportChanged()
    }

    /** 上游 `getLeftMinVisibleBarCount()` / `getRightMinVisibleBarCount()`。 */
    fun getLeftMinVisibleBarCount(): Int = state.viewport.leftMinVisibleBarCount
    fun getRightMinVisibleBarCount(): Int = state.viewport.rightMinVisibleBarCount

    /** 上游 `setZoomEnabled(enabled)` / `isZoomEnabled()`（SLA-011：状态/手势共同消费）。 */
    fun setZoomEnabled(enabled: Boolean) {
        state.zoomEnabled = enabled
    }

    fun isZoomEnabled(): Boolean = state.zoomEnabled

    /** 上游 `setScrollEnabled(enabled)` / `isScrollEnabled()`（SLA-011）。 */
    fun setScrollEnabled(enabled: Boolean) {
        state.scrollEnabled = enabled
    }

    fun isScrollEnabled(): Boolean = state.scrollEnabled

    /** 上游 `setZoomAnchor(anchor)` / `getZoomAnchor()`。 */
    internal var zoomAnchor: ZoomAnchor = ZoomAnchor()

    fun setZoomAnchor(anchor: ZoomAnchor) {
        zoomAnchor = anchor
    }

    fun getZoomAnchor(): ZoomAnchor = zoomAnchor

    // ---------- 坐标转换（convertToPixel/convertFromPixel） ----------

    /**
     * 上游 ConvertFilter（Chart.ts:60-64）原义（R05 纠正，复审 2026-10-30）：
     * - [paneId] 选择目标 pane（默认 candle/main pane），决定该 pane 的坐标
     *   边界（bounding.top）与值映射；x 轴 pane 不参与；
     * - [yAxisId] 在 pane 内选择轴（缺省 pane 默认轴；Alpha 仅主 pane 多轴）；
     * - [absolute] **只控制 y 的参考系**——false（缺省）返回 pane 相对坐标，
     *   true 返回画布绝对坐标（+pane bounding.top；from 侧相应先减）。
     *   timestamp 恒为时间戳、dataIndex 恒为索引，与 absolute 无关。
     */
    data class ConvertFilter(
        val paneId: String? = null,
        val yAxisId: String? = null,
        val absolute: Boolean? = null,
    )

    /** 上游 `convertToPixel`（Chart.ts:1201-1230 镜像）。 */
    fun convertToPixel(
        points: List<io.lkanvas.core.overlay.OverlayPoint>,
        filter: ConvertFilter? = null,
    ): List<Pair<Double, Double>> {
        val paneId = filter?.paneId ?: "main"
        val candles = state.dataSnapshot.candles
        // Q02：yAxisId 分支的偏移用**轴所属 pane**（axisValueToY 返回该 pane 的
        // 相对 y；修复前把绝对当相对、absolute 再加一次 top）
        val axisPaneId = filter?.yAxisId?.takeIf { it != "main" }
            ?.let { state.extraYAxisMeta[it]?.paneId } ?: paneId
        val yOf: (Double) -> Double? = when {
            filter?.yAxisId != null && filter.yAxisId != "main" ->
                { value -> state.axisValueToY(filter.yAxisId, value) }
            else -> { value -> state.paneValueToY(paneId, value) }
        }
        // absolute=true：y 加 pane bounding.top（Chart.ts:1223）——仅此一次
        val paneTop = if (filter?.absolute == true) (state.paneBounds(axisPaneId)?.first?.toDouble() ?: 0.0) else 0.0
        return points.map { p ->
            // timestamp 恒为时间戳（优先）；仅缺 timestamp 时用 dataIndex（Chart.ts:1216-1220）
            val idx = when {
                p.timestamp >= 0 -> io.lkanvas.core.viewport.Viewport.indexOfTimestamp(p.timestamp, candles)
                p.dataIndex != null -> p.dataIndex!!.coerceIn(0, candles.size - 1)
                else -> null
            } ?: return@map Pair(Double.NaN, Double.NaN)
            val x = state.viewport.candleCenterX(idx.toDouble())
            val yRel = yOf(p.value) ?: Double.NaN
            Pair(x, yRel + paneTop)
        }
    }

    /** 上游 `convertFromPixel`（Chart.ts:1232-1262 镜像；absolute 时 y 先减 pane top）。 */
    fun convertFromPixel(
        coordinates: List<Pair<Double, Double>>,
        filter: ConvertFilter? = null,
    ): List<io.lkanvas.core.overlay.OverlayPoint> {
        val paneId = filter?.paneId ?: "main"
        val candles = state.dataSnapshot.candles
        if (candles.isEmpty()) {
            return coordinates.map { io.lkanvas.core.overlay.OverlayPoint(timestamp = -1L, value = Double.NaN) }
        }
        val axisPaneId2 = filter?.yAxisId?.takeIf { it != "main" }
            ?.let { state.extraYAxisMeta[it]?.paneId } ?: paneId
        val paneTop = if (filter?.absolute == true) (state.paneBounds(axisPaneId2)?.first?.toDouble() ?: 0.0) else 0.0
        val valueAt: (Float) -> Double? = when {
            filter?.yAxisId != null && filter.yAxisId != "main" ->
                { y -> state.axisValueAt(filter.yAxisId, y) }
            else -> { y -> state.paneValueAt(paneId, y) }
        }
        return coordinates.map { (x, y) ->
            val idx = state.viewport.logicalIndexAt(x).roundToInt().coerceIn(candles.indices)
            val yRel = (y - paneTop).toFloat()
            val value = valueAt(yRel) ?: Double.NaN
            io.lkanvas.core.overlay.OverlayPoint(timestamp = candles[idx].timestamp, value = value, dataIndex = idx)
        }
    }

    // ---------- 事件（subscribeAction 体系） ----------

    private val actionCallbacks = ActionType.entries.associateWith { CopyOnWriteArrayList<ActionCallback>() }

    init {
        // 手势/状态钩子 → 上游事件语义接线
        state.onScrolled = { distance -> emit(ActionType.ON_SCROLL, mapOf("distance" to distance)) }
        state.onZoomed = { scale -> emit(ActionType.ON_ZOOM, mapOf("scale" to scale)) }
        // tooltip feature 点击（0.3.0 批次 7c：手势命中图标 → 三种 ActionType）
        state.onTooltipFeatureClick = { paneId, featureId, _, _ ->
            executeTooltipFeatureClick(featureId, paneId)
        }
        state.onVisibleRangeChanged = { from, to, realFrom, realTo ->
            // SLA-005 分页触发入口：视口调整末尾的边界判定（上游 _adjustVisibleRange 末尾）
            scope.launch { store.adjustVisibleRangeBoundary(from, to) }
            emit(ActionType.ON_VISIBLE_RANGE_CHANGE, VisibleRange(from, to, realFrom, realTo))
        }
        state.onCrosshairChanged = { x, y, dataIndex, timestamp ->
            val candles = state.dataSnapshot.candles
            // R10：paneId 来自实际 pane 布局（main/vol/macd 按需存在 + 动态 pane，R07 集合）
            val paneId = state.paneIdAt(y.toFloat())
            emit(
                ActionType.ON_CROSSHAIR_CHANGE,
                Crosshair(
                    x = x, y = y, paneId = paneId, realX = x,
                    timestamp = timestamp, kLineData = candles.getOrNull(dataIndex),
                    dataIndex = dataIndex, realDataIndex = dataIndex.toDouble(),
                ),
            )
        }
        state.onPaneDragged = { paneId -> emit(ActionType.ON_PANE_DRAG, mapOf("paneId" to paneId)) }
        state.onCandleClick = { candle ->
            val idx = state.dataSnapshot.candles.indexOf(candle)
            emit(
                ActionType.ON_CANDLE_BAR_CLICK,
                // 上游 payload 嵌套结构：data:{prev,current,next}（common/Data.ts VisibleRangeData）
                VisibleRangeData(
                    dataIndex = idx,
                    x = state.viewport.candleCenterX(idx.toDouble()),
                    data = NeighborData(
                        prev = state.dataSnapshot.candles.getOrNull(idx - 1),
                        current = candle,
                        next = state.dataSnapshot.candles.getOrNull(idx + 1),
                    ),
                ),
            )
        }
    }

    /** 上游 `subscribeAction(type, callback)`。 */
    fun subscribeAction(type: ActionType, callback: ActionCallback) {
        actionCallbacks.getValue(type).add(callback)
    }

    /**
     * tooltip feature 点击（上游 onCandleTooltipFeatureClick/onIndicatorTooltipFeatureClick/
     * onCrosshairFeatureClick 三种；由宿主 UI 图标点击时调用，映射上游 feature 点击语义）。
     */
    fun executeTooltipFeatureClick(featureId: String, paneId: String = "main") {
        // R10：按 pane 类型分类（candle pane=主图；真实指标 pane=INDICATOR；
        // "crosshair" 为十字线区哨兵（非真实 pane，保持上游三事件面））
        val type = when {
            paneId == "main" -> ActionType.ON_CANDLE_TOOLTIP_FEATURE_CLICK
            paneId == "crosshair" -> ActionType.ON_CROSSHAIR_FEATURE_CLICK
            else -> ActionType.ON_INDICATOR_TOOLTIP_FEATURE_CLICK
        }
        emit(type, mapOf("paneId" to paneId, "featureId" to featureId))
    }

    /** 上游 `unsubscribeAction(type, callback?)`；callback 为 null 时清空该类型。 */
    fun unsubscribeAction(type: ActionType, callback: ActionCallback? = null) {
        val list = actionCallbacks.getValue(type)
        if (callback == null) list.clear() else list.remove(callback)
    }

    /**
     * 上游 `executeAction(type, data)`（SLA-015）：ON_CROSSHAIR_CHANGE 写十字线
     * 状态并以 notExecuteAction 语义抑制反向广播；其余类型分发给订阅者。
     */
    fun executeAction(type: ActionType, data: Any?) {
        if (type == ActionType.ON_CROSSHAIR_CHANGE) {
            // R10（复审 2026-10-30）：null/非 Crosshair → 清除十字线
            // （上游 setCrosshair(undefined) 语义，Chart.ts:1263-1270）
            val crosshair = data as? Crosshair
            state.applyCrosshairAction(
                crosshair?.x ?: Double.NaN,
                crosshair?.y ?: Double.NaN,
                crosshair?.dataIndex ?: -1,
                crosshair?.paneId,
            )
            return
        }
        actionCallbacks.getValue(type).forEach { it.onAction(data) }
    }

    /**
     * 内部事件广播：直发订阅者（上游内部变化经 executeAction 分发；
     * 公共 [executeAction] 的 ON_CROSSHAIR_CHANGE 拦截（写状态+抑制广播）
     * 仅作用于宿主手动触发，内部变化不受影响）。
     */
    private fun emit(type: ActionType, data: Any?) {
        actionCallbacks.getValue(type).forEach { it.onAction(data) }
    }

    // ---------- 数据（core Store 数据路径镜像：_processDataLoad/_addData/resetData；SLA-005/006） ----------

    /**
     * 数据路径编排器：拉取触发、more 同方向更新、边界分页、退订顺序与
     * 请求代次门禁全部在 core/store/Store.kt（上游 Store.ts 数据段镜像）；
     * 门面只提供宿主桥（数据应用到内核）与协程串行化。
     */
    private val store: Store = Store(storeHost())

    /**
     * R02（复审 2026-10-30）：应用动作使用 Store 在检查点解析的 [key]——
     * 协程执行期**不得**重读 currentKey()（排队中的切换会把旧回调数据
     * 标记成新品种）。应用为"已受理即返回 true"；数据落库后的范围调整/
     * 边界续拉由 [ChartState] 的 onSnapshot 统一驱动（R03，镜像上游
     * _addData → _adjustVisibleRange 提交后链）。
     */
    /**
     * Q01（第四轮复审 2026-10-30）：**接受时点会话身份**——dataEpoch 在
     * applyInit 被 Store 锁内调用的瞬间递增（新会话建立点），其余动作在
     * 接受时捕获该值、在协程执行前复核。与读协程运行时引擎状态不同，
     * 这是发起请求时的不可变身份：ABA 旧动作（旧 epoch）被拒；INIT 后的
     * 合法首推（新 epoch，即使 Reset 协程尚未执行）被接纳。
     */
    private val dataEpoch = java.util.concurrent.atomic.AtomicLong(0)

    /**
     * R7-01（第七轮复审 2026-10-31）：**拒绝水位**——INIT 最终建会话失败的
     * 代次下界；该代次（含更早）的后续动作在协程**执行时**（引擎提交前）丢弃。
     * 不回写 [dataEpoch]（M01 单调语义保持——审计明确"仅在 rejected continuation
     * 再改一次全局 epoch 不能保证拦住已进入引擎队列的请求"）：B 失败 Reset 先入队、
     * 已受理订阅的首推随后入队，Reset 拒绝时同步抬升水位，同一串行队列中排在其
     * 后的 Update 据此被拒——失败会话的已入队推送不再串写旧会话快照。
     */
    private val lastRejectedEpoch = java.util.concurrent.atomic.AtomicLong(0)

    private fun storeHost() = object : Store.Host {
        override fun applyInit(key: MarketKey, data: List<Candle>): Boolean {
            // M01（第六轮复审 2026-10-31）：捕获**自身身份**——异步失败清理前
            // 复核仍属当前会话；代次单调不回退（修复前 decrementAndGet 改写
            // 全局代次 + 无归属 unsubscribeData 退订较新有效会话）。
            // 归属复核双门：dataEpoch（无更新的 INIT 受理）+ store.generation
            // （无任何会话切换/退订）。仅 epoch 不够——审计复现中 C 的回调
            // 被代次门禁丢弃、epoch 未递增，而 setSymbol(C) 已推进 generation：
            // B 失败延续据此识别自身已非当前会话，不清理、不干扰 C 在途 INIT。
            val myEpoch = dataEpoch.incrementAndGet()
            val myGen = store.generationForProbe
            scope.launch {
                val result = state.reset(key, data, myEpoch)
                // N01：真实提交结果反馈到订阅与会话身份
                if (result is io.lkanvas.core.store.ChangeResult.Rejected) {
                    // R7-01：先抬升拒绝水位（同步于 Reset 拒绝点、先于同队列后续
                    // 动作执行）——本代次的已入队 Update/Backward/Forward 在其
                    // 协程执行时被拒，不再串写旧会话
                    lastRejectedEpoch.updateAndGet { prev -> maxOf(prev, myEpoch) }
                    // 归属复核：仅当本 INIT 仍是当前会话时才清理订阅
                    // （审计 M01 复现：B 失败延续不得退订较新 C 会话）
                    if (dataEpoch.get() == myEpoch && store.generationForProbe == myGen) {
                        store.unsubscribeData()
                    }
                    return@launch
                }
                // init 重载后按上游 _addData init 分支重放右侧留白（应用完成后）
                offsetRightDistanceInternal?.let { d -> setOffsetRightDistance(d) }
            }
            return true
        }

        override fun applyBackward(key: MarketKey, data: List<Candle>): Boolean {
            val epoch = dataEpoch.get() // 接受时点捕获
            scope.launch {
                // R7-01：epoch 复核 + 拒绝水位（执行时判定——失败 INIT 已入队动作
                // 在引擎提交前丢弃；合法 INIT 同步首推在 Reset 成功路径不受影响）
                if (epoch != dataEpoch.get() || epoch <= lastRejectedEpoch.get()) return@launch
                state.appendBatch(data, epoch)
            }
            return true
        }

        override fun applyForward(key: MarketKey, data: List<Candle>): Boolean {
            val epoch = dataEpoch.get()
            scope.launch {
                if (epoch != dataEpoch.get() || epoch <= lastRejectedEpoch.get()) return@launch
                state.prependHistory(data, epoch)
            }
            return true
        }

        override fun applyUpdate(key: MarketKey, candle: Candle): Boolean {
            val epoch = dataEpoch.get()
            scope.launch {
                if (epoch != dataEpoch.get() || epoch <= lastRejectedEpoch.get()) return@launch
                state.upsertLatest(candle, epoch)
            }
            return true
        }

        override fun candleCount(): Int = state.dataSnapshot.candles.size

        override fun firstTimestamp(): Long? = state.dataSnapshot.candles.firstOrNull()?.timestamp

        override fun lastTimestamp(): Long? = state.dataSnapshot.candles.lastOrNull()?.timestamp
    }

    /** 上游 `setDataLoader(loader)`：经 Store.resetData（退订 → 变更 → 清 loading → init）。 */
    fun setDataLoader(loader: DataLoader) {
        scope.launch { store.setDataLoader(loader) }
    }

    /** 上游 `getDataList()`。 */
    fun getDataList(): List<Candle> = state.dataSnapshot.candles

    /** 上游 `resetData()`：清空数据并触发 loader init 重载。 */
    fun resetData() {
        scope.launch { store.resetData() }
    }

    // ---------- 生命周期 ----------

    /** 释放订阅与协程；state 可复用（同一 state 再次 init 返回新实例）。 */
    fun destroy() {
        store.unsubscribeData()
        scope.cancel()
    }

    /** 触发一次全量重绘（等价上游 resize 的重绘部分；尺寸由 Compose onLayout 自适应）。 */
    // ---------- 指标（对齐 Chart.ts 指标组） ----------

    /**
     * 上游 `createIndicator(value, isStack?)`（SLA-007 严格语义）：
     * - paneId 缺省 = 自动生成新指标 pane（并自动建 pane，上游 _createPane）；
     * - isStack 缺省 false（替换目标 pane 既有指标，上游 addIndicator）；
     * - id 提供且已存在同 id 实例 → 返回 null（上游同名过滤去重）。
     */
    fun createIndicator(
        name: String,
        calcParams: List<Double>? = null,
        paneId: String? = null,
        isStack: Boolean = false,
        yAxisId: String = "main",
        styles: Any? = null,
        id: String? = null,
    ): String? {
        val created = state.createIndicator(name, calcParams, paneId, isStack, yAxisId, styles, id)
        // 0.5.x 动态副图：解析后的 paneId 非内置时自动创建 pane（布局随 relayout 扩展）
        val resolvedPaneId = created?.let { state.getIndicatorByInstanceId(it)?.paneId }
        if (resolvedPaneId != null && resolvedPaneId !in setOf("main", "vol", "macd")) {
            ensureExtraPane(resolvedPaneId)
        }
        return created
    }

    /**
     * 上游 `overrideIndicator`（SLA-007 全字段）：按 filter 定位全部匹配实例，
     * 非空字段覆盖。
     */
    fun overrideIndicator(
        filter: ChartState.IndicatorFilter,
        calcParams: List<Double>? = null,
        visible: Boolean? = null,
        styles: Any? = null,
        shortName: String? = null,
        paneId: String? = null,
        yAxisId: String? = null,
        zLevel: Int? = null,
        extendData: Map<String, Any?>? = null,
    ): Boolean {
        val changed = state.overrideIndicator(
            filter, calcParams, visible, styles, shortName, paneId, yAxisId, zLevel, extendData,
        )
        if (changed) maintainExtraPanesAfterMove(paneId)
        return changed
    }

    /**
     * R07：paneId 迁移后维护 pane 集合——目标为用户 extras 时确保存在，
     * 原属用户 extras 空了自动回收（vol/macd 由 paneList 按绑定派生）。
     */
    private fun maintainExtraPanesAfterMove(paneId: String?) {
        if (paneId != null && paneId !in setOf("main", "vol", "macd") && paneId !in state.extraPanes.value) {
            state.extraPanes.value = state.extraPanes.value + paneId
            state.relayoutExtraPanes()
        }
        val emptyExtras = state.extraPanes.value.filter { extra ->
            state.getIndicators(ChartState.IndicatorFilter(paneId = extra)).isEmpty()
        }
        if (emptyExtras.isNotEmpty()) {
            state.extraPanes.value = state.extraPanes.value - emptyExtras.toSet()
            state.relayoutExtraPanes()
        }
    }

    /** 上游 `overrideIndicator` 按 id 简写。 */
    fun overrideIndicator(id: String, calcParams: List<Double>? = null, visible: Boolean? = null): Boolean =
        state.overrideIndicator(id, calcParams, visible)

    /** 上游 `getIndicators(filter?)`。 */
    fun getIndicators(filter: ChartState.IndicatorFilter = ChartState.IndicatorFilter()) =
        state.getIndicators(filter)

    /** 上游 `getIndicatorByInstanceId(id)`。 */
    fun getIndicatorByInstanceId(id: String) = state.getIndicatorByInstanceId(id)

    /** 0.5.x 动态副图：确保自定义 pane 存在（去重）。 */
    private fun ensureExtraPane(paneId: String) {
        if (paneId !in state.extraPanes.value) {
            state.extraPanes.value = state.extraPanes.value + paneId
            state.relayoutExtraPanes()
        }
    }

    /** 0.5.x pane 排序：移动自定义 pane 到目标渲染序位（透出 ChartState）。 */
    fun moveExtraPane(paneId: String, targetIndex: Int) = state.moveExtraPane(paneId, targetIndex)

    /** 0.5.x 动态副图：pane 内无实例时移除 pane。 */
    private fun pruneEmptyExtraPanes() {
        val empty = state.extraPanes.value.filter { pane ->
            state.getIndicators(ChartState.IndicatorFilter(paneId = pane)).isEmpty()
        }
        if (empty.isNotEmpty()) {
            state.extraPanes.value = state.extraPanes.value - empty.toSet()
            state.relayoutExtraPanes()
        }
    }

    /** 上游 `removeIndicator(filter)`；动态副图 pane 空了自动移除。 */
    fun removeIndicator(filter: ChartState.IndicatorFilter = ChartState.IndicatorFilter()): Boolean {
        val removed = state.removeIndicator(filter)
        if (removed) pruneEmptyExtraPanes()
        return removed
    }

    // ---------- 样式（对齐 setStyles/getStyles，批次 5） ----------

    /** 当前样式（getStyles 返回值）；Compose 可观察——组合期读取 resolveTheme 的宿主随变化重组。 */
    internal var styles: io.lkanvas.chart.styles.Styles by androidx.compose.runtime.mutableStateOf(
        io.lkanvas.chart.styles.StylesThemes.light,
    )


    /** 上游 `setStyles(value: string)`：按注册主题名整体替换。 */
    fun setStyles(value: String) {
        styles = io.lkanvas.chart.styles.StylesMerge.resolveTheme(value)
        applyStyleConsumers()
    }

    /**
     * 上游 `setStyles(patch: DeepPartial<Styles>)`（R04，复审 2026-10-30）：
     * [io.lkanvas.chart.styles.StylesPatch] 为全字段可空补丁——null=未提供，
     * **未提供的分支保持当前值不变**（局部补丁不重置其他配置）；
     * 非 null=已提供（显式设回默认值照常生效）。
     */
    fun setStyles(patch: io.lkanvas.chart.styles.StylesPatch) {
        styles = patch.applyTo(styles)
        applyStyleConsumers()
    }

    /**
     * 主题全量替换（非补丁）：用 [value] 的全部字段覆盖当前样式。
     * 局部修改请使用 [setStyles] 的 [io.lkanvas.chart.styles.StylesPatch] 重载。
     */
    fun setStyles(value: io.lkanvas.chart.styles.Styles) {
        styles = value
        applyStyleConsumers()
    }

    /**
     * SLA-011 样式消费接线：candle.type → 实际主图形态（mainChartType）；
     * candle.bar 回调 → 逐根配色提供者（drawCandles 消费）。
     */
    private fun applyStyleConsumers() {
        state.mainChartType = when (styles.candle.type) {
            "candle_stroke" -> io.lkanvas.compose.theme.MainChartType.CANDLE_STROKE
            "candle_up_stroke" -> io.lkanvas.compose.theme.MainChartType.CANDLE_UP_STROKE
            "candle_down_stroke" -> io.lkanvas.compose.theme.MainChartType.CANDLE_DOWN_STROKE
            "ohlc" -> io.lkanvas.compose.theme.MainChartType.OHLC
            "area" -> io.lkanvas.compose.theme.MainChartType.AREA
            else -> io.lkanvas.compose.theme.MainChartType.CANDLE_SOLID
        }
        state.candleBarColorProvider = when (val bar = styles.candle.bar) {
            is io.lkanvas.chart.styles.CandleBarColorCallback -> {
                { i ->
                    val candles = state.dataSnapshot.candles
                    if (candles.isEmpty() || i !in candles.indices) null
                    else bar.color(candles[i], candles.getOrNull(i - 1))
                }
            }
            else -> null
        }
    }

    /** 上游 `getStyles()`。 */
    fun getStyles(): io.lkanvas.chart.styles.Styles = styles

    /** Styles → ChartTheme 快照（KLineChart theme 参数可用此值消费 Styles）。 */
    fun resolveTheme(): io.lkanvas.compose.theme.ChartTheme =
        io.lkanvas.compose.theme.StylesThemeBridge.toTheme(styles)

    // ---------- 覆盖物（对齐 Chart.ts 覆盖物组） ----------

    /** 磁吸模式（上游 OverlayMode）。 */
    var overlayMode: OverlayMode = OverlayMode.WEAK_MAGNET
        set(value) {
            field = value
            state.overlays.magnetMode = when (value) {
                OverlayMode.NORMAL -> io.lkanvas.core.overlay.MagnetMode.NORMAL
                OverlayMode.WEAK_MAGNET -> io.lkanvas.core.overlay.MagnetMode.WEAK
                OverlayMode.STRONG_MAGNET -> io.lkanvas.core.overlay.MagnetMode.STRONG
            }
        }

    private fun overlayToInfo(id: String): OverlayInfo? {
        val inst = state.overlays.get(id) ?: return null
        return OverlayInfo(
            id = inst.id,
            groupId = inst.groupId,
            name = inst.templateName,
            paneId = inst.effectivePaneId,
            points = inst.points.toList(),
            locked = inst.locked,
            visible = inst.visible,
            zLevel = inst.zIndex,
            extendData = inst.extensionData.toMap(),
            currentStep = inst.currentStep,
            fixedZLevel = inst.fixedZLevel,
            modeSensitivity = inst.modeSensitivity,
            styles = inst.styles,
        )
    }

    /**
     * 上游 `createOverlay(value, paneId?)`：
     * - name+points → 编程式直接创建（返回 id）；
     * - 仅 name → 进入交互创建（返回 id，用户点选控制点）；
     * - 数组 → 批量，逐一返回（失败位为 null）。
     */
    fun createOverlay(value: OverlayCreate, paneId: String = "main"): String? {
        // 模板解析：core 内置 16 表优先，回退宿主注册表（registerOverlay 注册的自定义模板）
        val template = io.lkanvas.core.extension.overlay.OverlayTemplates[value.name]
            ?: LkanvasRegistry.getOverlayDefinition(value.name)
            ?: return null
        if (value.points.isEmpty()) {
            // 交互式（同门面 currentTool 流程）；SLA-013：创建配置**先行应用**到
            // 创建中实例（上游 createOverlay 无 points 分支同样应用全部字段），
            // 交互完成（或取消）后配置保持生效
            val id = state.overlays.beginCreate(template) ?: return null
            val inst = state.overlays.get(id) ?: return null
            inst.paneIdOverride = paneId
            value.groupId?.let { inst.groupId = it }
            value.extendData.forEach { (k, v) -> inst.extensionData[k] = v }
            inst.locked = value.lock
            inst.visible = value.visible
            inst.zIndex = value.zLevel
            inst.modeSensitivity = value.modeSensitivity
            value.styles?.let { inst.styles = it }
            state.notifyOverlayChanged()
            return id
        }
        // 编程式：begin+逐点+显式完成（continuous）或自动完成（定步数）
        val id = state.overlays.beginCreate(template) ?: return null
        for (p in value.points) {
            state.overlays.addCreatePoint(id, p)
        }
        if (template.continuous && state.overlays.get(id)?.phase == io.lkanvas.core.overlay.OverlayPhase.CREATING) {
            state.overlays.finishCreate(id)
        }
        val inst = state.overlays.get(id) ?: return null
        inst.paneIdOverride = paneId
        value.groupId?.let { inst.groupId = it }
        value.extendData.forEach { (k, v) -> inst.extensionData[k] = v }
        inst.locked = value.lock
        inst.visible = value.visible
        inst.zIndex = value.zLevel
        inst.modeSensitivity = value.modeSensitivity
        value.styles?.let { inst.styles = it }
        state.notifyOverlayChanged()
        return id
    }

    /** 数组重载：逐一创建。 */
    fun createOverlay(values: List<OverlayCreate>, paneId: String = "main"): List<String?> =
        values.map { createOverlay(it, paneId) }

    /** 上游 `getOverlays(filter?)`。 */
    fun getOverlays(filter: OverlayFilter? = null): List<OverlayInfo> =
        state.overlays.all()
            .mapNotNull { overlayToInfo(it.id) }
            .filter { info ->
                filter == null || filter.matches(info.id, info.name, info.paneId, info.groupId)
            }

    /** 上游 `overrideOverlay(override)`：以 `override.name+filter` 定位。 */
    fun overrideOverlay(id: String, override: OverlayOverride): Boolean {
        val inst = state.overlays.get(id) ?: return false
        override.points?.let { pts ->
            inst.points.clear()
            inst.points.addAll(pts)
        }
        override.groupId?.let { inst.groupId = it }
        override.extendData?.let { ext -> inst.extensionData.putAll(ext) }
        override.lock?.let { inst.locked = it }
        override.visible?.let { inst.visible = it }
        override.zLevel?.let { inst.zIndex = it }
        override.fixedZLevel?.let { inst.fixedZLevel = it }
        override.modeSensitivity?.let { inst.modeSensitivity = it }
        override.styles?.let { inst.styles = it }
        state.notifyOverlayChanged()
        return true
    }

    /** 上游 `removeOverlay(filter)`：按 filter 批量删除，返回是否有删除。 */
    fun removeOverlay(filter: OverlayFilter): Boolean {
        val targets = getOverlays(filter).map { it.id }
        var removed = false
        for (id in targets) {
            state.overlays.remove(id)
            removed = true
        }
        state.notifyOverlayChanged()
        return removed
    }

    /** 上游 `removeOverlay(id)` 简写（filter=仅 id）。 */
    fun removeOverlay(id: String): Boolean = removeOverlay(OverlayFilter(id = id))

    // ---------- 配置组（对齐 Chart.ts 配置方法） ----------

    /** 上游 `setLocale(locale)`：'zh-CN'/'en-US'（未知名回退 zh-CN，差异登记：上游回退 en-US）。 */
    fun setLocale(locale: String) {
        state.locale = when (locale) {
            "en-US" -> io.lkanvas.core.i18n.ChartLocale.EN_US
            else -> io.lkanvas.core.i18n.ChartLocale.ZH_CN
        }
    }

    /** 上游 `getLocale()`。 */
    fun getLocale(): String = when (state.locale) {
        io.lkanvas.core.i18n.ChartLocale.EN_US -> "en-US"
        else -> "zh-CN"
    }

    /** 上游 `setTimezone(tz)`：IANA 名（空=系统时区）。 */
    fun setTimezone(timezone: String) {
        this.timezone = timezone
        state.timezoneId = timezone // 批次 8e：时间标签渲染消费
    }

    /** 上游 `getTimezone()`：空字符串=系统时区（上游默认行为一致）。 */
    fun getTimezone(): String = timezone

    internal var timezone: String = ""

    /** 上游 `setThousandsSeparator(o)` / `getThousandsSeparator()`。 */
    internal var thousandsSeparator = ThousandsSeparator()
        set(value) {
            field = value
            syncFormatConfig()
        }

    fun setThousandsSeparator(separator: ThousandsSeparator) {
        thousandsSeparator = separator
    }

    fun getThousandsSeparator(): ThousandsSeparator = thousandsSeparator

    /** 上游 `setDecimalFold(o)` / `getDecimalFold()`。 */
    internal var decimalFold = DecimalFold()
        set(value) {
            field = value
            syncFormatConfig()
        }

    fun setDecimalFold(fold: DecimalFold) {
        decimalFold = fold
    }

    fun getDecimalFold(): DecimalFold = decimalFold

    /** 上游 `setFormatter(f)` / `getFormatter()`。 */
    internal var formatter: Formatter? = null
        set(value) {
            field = value
            syncFormatConfig()
        }

    fun setFormatter(formatter: Formatter?) {
        this.formatter = formatter
    }

    fun getFormatter(): Formatter? = formatter

    /**
     * SLA-011 格式链接线：把 thousandsSeparator/decimalFold/formatter 同步到
     * state.formatConfig，由 ChartText.price/volume 统一消费。
     */
    private fun syncFormatConfig() {
        state.formatConfig = ChartState.FormatConfig(
            thousandsSign = thousandsSeparator.sign,
            decimalFoldThreshold = decimalFold.threshold,
            formatDate = formatter?.let { f ->
                { ts: Long, tpl: String -> f.formatDate?.invoke(ts, tpl, null) ?: io.lkanvas.core.common.utils.Format.formatTimestampByTemplate(ts, tpl) }
            },
            formatBigNumber = formatter?.formatBigNumber,
        )
    }

    /** 上游 `setHotkey(hotkey)` / `getHotkey()`（平台占位）。 */
    internal var hotkey = Hotkey()

    fun setHotkey(hotkey: Hotkey) {
        this.hotkey = hotkey
    }

    fun getHotkey(): Hotkey = hotkey

    /** 上游 `setPaneOptions(options)`：按 paneId 调整高度（main/vol/macd）。 */
    fun setPaneOptions(options: PaneOptions) {
        val cur = state.paneHeightsPx
        val usable = cur.main + cur.vol + cur.macd
        if (usable <= 0f) return // 无布局（Compose 未挂载）：无意义
        // R07：动态 pane → extraPaneHeightOverride 路由（可设高）
        if (options.id !in setOf("main", "vol", "macd")) {
            if (options.id in state.extraPanes.value && options.height != null) {
                state.setExtraPaneHeight(options.id, options.height!!.toFloat())
            }
            return
        }
        when (options.id) {
            "main" -> {
                // R07：主 pane 高度直接设定（上游 candle_pane height 语义）；
                // 剩余空间按当前比例分给**存在的**副图
                val heightF = (options.height ?: cur.main).toFloat()
                val rest = (usable - heightF).coerceAtLeast(0f)
                if (cur.vol + cur.macd > 0) {
                    val ratio = if (cur.vol + cur.macd > 0) cur.vol / (cur.vol + cur.macd) else 0.5f
                    val vol = rest * ratio
                    state.setSubPaneHeights(vol, rest - vol)
                }
                state.mainPaneOverride = heightF
            }

            "vol" -> {
                val vol = (options.height ?: cur.vol).toFloat()
                state.mainPaneOverride = null
                state.setSubPaneHeights(vol, usable - vol)
            }

            "macd" -> {
                val macd = (options.height ?: cur.macd).toFloat()
                state.mainPaneOverride = null
                state.setSubPaneHeights(usable - macd, macd)
            }
        }
        state.relayoutExtraPanes() // 触发 relayout 应用覆写
    }

    /** 上游 `getPaneOptions(id?)`：遍历实际 pane 集合（R07——动态 pane 可查询）。 */
    fun getPaneOptions(id: String? = null): List<PaneOptions> {
        val p = state.paneHeightsPx
        val layout = state.extraPaneLayout.associate { it.first to it.second }
        val all = state.paneList().map { paneId ->
            val height = when (paneId) {
                "main" -> p.main.toDouble()
                "vol" -> p.vol.toDouble()
                "macd" -> p.macd.toDouble()
                else -> layout[paneId]?.let { (top, bottom) -> (bottom - top).toDouble() } ?: 0.0
            }
            PaneOptions(paneId, height)
        }
        return if (id == null) all else all.filter { it.id == id }
    }

    /**
     * 布局选项（R11，复审 2026-10-30：对齐上游 Options.ts:88-92 Layout 字段面）。
     */
    data class LayoutOptions(
        /** 各 pane 基准高度（px；null=默认布局）。 */
        val paneHeights: Map<String, Double>? = null,
        /** 上游 Layout.barSpaceLimit：bar 间距上下限（px）。 */
        val barSpaceLimit: Pair<Double, Double>? = null,
        /** 上游 Layout.pane：分隔条拖动开关（按 paneId；缺省 true）。 */
        val paneDragEnabled: Map<String, Boolean>? = null,
    )

    /** 上游 `setLayoutOptions(options)`：应用 pane 基准高度/bar 间距限制/拖动开关。 */
    fun setLayoutOptions(options: LayoutOptions) {
        options.barSpaceLimit?.let { (min, max) ->
            state.viewport.minBarSpacing = min
            state.viewport.maxBarSpacing = max
        }
        options.paneDragEnabled?.let { drag ->
            drag["vol"]?.let { state.volDragEnabled = it }
            drag["macd"]?.let { state.macdDragEnabled = it }
            for ((pane, enabled) in drag) {
                // Q07：单一状态（ChartState.disabledExtraDrags）——setter/getter/手势共用
                if (pane in state.extraPanes.value) {
                    if (enabled) state.disabledExtraDrags.remove(pane)
                    else if (pane !in state.disabledExtraDrags) state.disabledExtraDrags.add(pane)
                }
            }
        }
        val heights = options.paneHeights ?: return
        val cur = state.paneHeightsPx
        val usable = cur.main + cur.vol + cur.macd
        if (usable <= 0f) return
        // Q07：遍历实际 pane 集合（动态 pane 高度经 setPaneOptions 统一路由，
        // 修复前 {paneId:123} 被忽略）
        for ((paneId, height) in heights) {
            setPaneOptions(PaneOptions(paneId, height))
        }
    }

    /** 上游 `getLayoutOptions()`（T10：存取同源——返回全部已配置状态）。 */
    fun getLayoutOptions(): LayoutOptions {
        val p = state.paneHeightsPx
        val limit = state.viewport.minBarSpacing to state.viewport.maxBarSpacing
        // paneDragEnabled 回读（未显式配置过=缺省 true，不进 map——与 set 对称）
        val drag = buildMap {
            if (!state.volDragEnabled) put("vol", false)
            if (!state.macdDragEnabled) put("macd", false)
            for (pane in state.extraPanes.value) {
                if (pane in state.disabledExtraDrags) put(pane, false)
            }
        }
        val heights = buildMap {
            if (p.main > 0f) {
                put("main", p.main.toDouble())
                if (state.hasBuiltinPane("vol")) put("vol", p.vol.toDouble())
                if (state.hasBuiltinPane("macd")) put("macd", p.macd.toDouble())
                // T10：动态 pane 高度也在回读 map 中
                putAll(state.extraPaneHeights())
            }
        }
        return LayoutOptions(
            paneHeights = heights.ifEmpty { null },
            barSpaceLimit = limit,
            paneDragEnabled = drag.ifEmpty { null },
        )
    }

    private val disabledExtraDrags = java.util.concurrent.CopyOnWriteArrayList<String>()

    /**
     * 上游 `getConvertPictureUrl` 等价入口：经 [io.lkanvas.compose.export.ChartSnapshotExporter]
     * 导出图片字节并编码 data URL（宿主需提供渲染位图源；平台差异登记 gap-matrix）。
     */
    fun getConvertPictureUrl(
        bitmapProvider: () -> android.graphics.Bitmap?,
        format: String = "png",
        backgroundColor: Int = android.graphics.Color.WHITE,
    ): String? {
        // T10（第三轮复审 2026-10-30）：bmp 平台不支持——显式返回 null 并以
        // 日志登记（上游 'png'|'jpeg'|'bmp'；平台差异登记 gap-matrix/compatibility）
        if (format.equals("bmp", ignoreCase = true)) {
            android.util.Log.w("LKanvasChart", "getConvertPictureUrl: bmp not supported on this platform, returning null")
            return null
        }
        val type = if (format.equals("jpeg", ignoreCase = true) || format.equals("jpg", ignoreCase = true)) "jpeg" else "png"
        val source = bitmapProvider() ?: return null
        // T10：backgroundColor **无条件合成**（上游 Chart.ts:1299-1300 fillRect
        // 语义——默认值不等同"未提供"；修复前 WHITE 跳过合成、透明区保留）
        val bitmap = android.graphics.Bitmap.createBitmap(source.width, source.height, android.graphics.Bitmap.Config.ARGB_8888).also { out ->
            out.eraseColor(backgroundColor)
            val canvas = android.graphics.Canvas(out)
            canvas.drawBitmap(source, 0f, 0f, null)
        }
        val bytes = java.io.ByteArrayOutputStream().use { out ->
            bitmap.compress(if (type == "jpeg") android.graphics.Bitmap.CompressFormat.JPEG else android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        val mime = if (type == "jpeg") "image/jpeg" else "image/png"
        return "data:$mime;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    // ---------- 多轴（对齐 Chart.ts:1003~1104） ----------

    /** 主轴默认视图（id 与 IndicatorInstance.yAxisId 默认一致）。 */
    private val defaultMainYAxis = YAxisInfo(
        id = "main",
        name = null,
        paneId = "main",
        reverse = false,
        inside = false,
        position = AxisPosition.RIGHT,
        scrollZoomEnabled = true,
        gap = AxisGap(),
        needWidget = true,
        isDefault = true,
    )

    /** 轴实例表（主轴预置；含主轴）。 */
    private val yAxes = java.util.concurrent.ConcurrentHashMap<String, YAxisInfo>().apply {
        put("main", defaultMainYAxis)
    }

    private var yAxisSerial = 0

    /** x 轴覆写记录（overrideXAxis 语义位；绘制端消费随批次 5+）。 */
    internal var xAxisState = XAxisOverride()

    /** 上游 `createYAxis(yAxis)`：登记轴实例，返回 id（重复 id 幂等返回）。 */
    /**
     * R06（复审 2026-10-30）：轴实例表 → ChartState 执行映射的**唯一**同步点。
     * - 主轴 reverse 仍走 mainAxisReverse（candle 主轴消费）；
     * - **全部 pane** 的非主轴实例（含动态 pane）同步 reverse/inside/spec/custom，
     *   绘制（多轴块）与转换（axisValueToY/axisValueAt）共用同一来源；
     * - 创建额外轴不再改写 mainAxisSpec（额外轴有自己的 spec）。
     */
    private fun syncExtraAxes() {
        yAxes["main"]?.let { state.mainAxisReverse = it.reverse }
        val extras = yAxes.values.filter { it.id != "main" }
        state.extraYAxisMeta = extras.associate {
            it.id to io.lkanvas.compose.state.ChartState.ExtraAxisMeta(
                reverse = it.reverse,
                inside = it.inside,
                positionLeft = it.position == AxisPosition.LEFT,
                paneId = it.paneId,
            )
        }
        state.extraYAxisCustom = extras.mapNotNull { a -> a.customAxis?.let { a.id to it } }.toMap()
        state.extraYAxisSpec = extras.mapNotNull { a -> a.templateSpec?.let { a.id to it } }.toMap()
    }

    fun createYAxis(yAxis: YAxisOverride): String? {
        val paneId = yAxis.paneId ?: "main"
        if (paneId == "candle_x") return null // x 轴 pane 不支持 y 轴（上游 PaneIdConstants.X_AXIS）
        // SLA-010：目标 pane 必须存在（上游 getDrawPaneById !isValid → null）
        val knownPanes = setOf("main", "vol", "macd") + state.extraPanes.value
        if (paneId !in knownPanes) return null
        val id = yAxis.id ?: "yAxis_${++yAxisSerial}"
        if (yAxes.containsKey(id)) return id
        // SLA-010：注册轴模板用于构造轴（extension/yaxis 注册表；customAxis 优先）
        val template = yAxis.template
            ?.let { io.lkanvas.core.extension.yaxis.YAxisTemplates.get(it) }
        val templateSpec = template?.createSpec()
        // T02：模板默认字段作缺省、override 参数覆盖（修复前 reverse/inside 等
        // 模板默认被丢弃，全部落 false/RIGHT）
        yAxes[id] = YAxisInfo(
            id = id,
            name = yAxis.name,
            paneId = paneId,
            reverse = yAxis.reverse ?: template?.reverse ?: false,
            inside = yAxis.inside ?: template?.inside ?: false,
            position = yAxis.position ?: if (template?.positionLeft == true) AxisPosition.LEFT else AxisPosition.RIGHT,
            scrollZoomEnabled = yAxis.scrollZoomEnabled ?: template?.scrollZoomEnabled ?: true,
            gap = yAxis.gap ?: AxisGap(
                top = template?.gapValues?.getOrNull(0) ?: AxisGap().top,
                bottom = template?.gapValues?.getOrNull(1) ?: AxisGap().bottom,
            ),
            needWidget = yAxis.needWidget ?: true,
            isDefault = false,
            customAxis = yAxis.customAxis,
            templateSpec = templateSpec,
        )
        // R06：额外轴不再改写主轴 spec（其自身 spec 经 syncExtraAxes 进入执行映射）
        syncExtraAxes()
        state.notifyViewportChanged()
        return id
    }

    /**
     * 上游 `removeYAxis(filter)`：主 pane 默认轴与有指标绑定的轴受保护
     * （上游 isDefaultYAxis + indicator.yAxisId 检查语义）。
     */
    fun removeYAxis(filter: YAxisFilter): Boolean {
        if (filter.id == null && filter.name == null) return false
        var removed = false
        for (axis in getYAxes(filter)) {
            if (axis.isDefault && axis.paneId == "main") continue
            val bound = state.getIndicators().any { it.yAxisId == axis.id }
            if (bound) continue
            yAxes.remove(axis.id)
            removed = true
        }
        if (removed) syncExtraAxes()
        if (removed) state.notifyViewportChanged()
        return removed
    }

    /** 上游 `getYAxes(filter)`。 */
    fun getYAxes(filter: YAxisFilter): List<YAxisInfo> =
        yAxes.values
            .filter { filter.paneId == null || it.paneId == filter.paneId }
            .filter { filter.id == null || it.id == filter.id }
            .filter { filter.name == null || it.name == filter.name }
            .sortedBy { it.id }

    /** 上游 `overrideYAxis(yAxis)`：按 paneId+id 定位后非空字段覆盖。 */
    fun overrideYAxis(yAxis: YAxisOverride) {
        val targets = getYAxes(YAxisFilter(paneId = yAxis.paneId, id = yAxis.id))
        for (axis in targets) {
            yAxes[axis.id] = axis.copy(
                name = yAxis.name ?: axis.name,
                reverse = yAxis.reverse ?: axis.reverse,
                inside = yAxis.inside ?: axis.inside,
                position = yAxis.position ?: axis.position,
                scrollZoomEnabled = yAxis.scrollZoomEnabled ?: axis.scrollZoomEnabled,
                gap = yAxis.gap ?: axis.gap,
                needWidget = yAxis.needWidget ?: axis.needWidget,
            )
        }
        if (targets.isNotEmpty()) {
            syncExtraAxes()
            state.notifyViewportChanged()
        }
    }

    /** 上游 `overrideXAxis(xAxis)`：覆写登记 + scrollZoomEnabled 消费（R06 余项）。 */
    fun overrideXAxis(xAxis: XAxisOverride) {
        xAxisState = xAxisState.copy(
            name = xAxis.name ?: xAxisState.name,
            reverse = xAxis.reverse ?: xAxisState.reverse,
            inside = xAxis.inside ?: xAxisState.inside,
            position = xAxis.position ?: xAxisState.position,
            scrollZoomEnabled = xAxis.scrollZoomEnabled ?: xAxisState.scrollZoomEnabled,
            gap = xAxis.gap ?: xAxisState.gap,
        )
        xAxisState.scrollZoomEnabled?.let { state.xAxisScrollZoomEnabled = it }
        state.notifyViewportChanged()
    }

    // ---------- 视口开关收尾（2c） ----------

    fun resize() {
        state.notifyViewportChanged()
    }
}
