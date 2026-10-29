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
        const val VERSION = "0.5.0"

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

    /** 上游 `getVisibleRange()`。 */
    fun getVisibleRange(): VisibleRange {
        val vp = state.viewport
        val from = kotlin.math.floor(vp.scrollIndex).toInt().coerceAtLeast(0)
        val to = kotlin.math.ceil(vp.scrollIndex + vp.visibleCount).toInt()
        return VisibleRange(from = from, to = to, realFrom = vp.scrollIndex, realTo = vp.scrollIndex + vp.visibleCount)
    }

    /** 上游 `scrollByDistance(distance, animationDuration?)`（动画批次 2c 补）。 */
    fun scrollByDistance(distance: Double, animationDuration: Long? = null) {
        state.panBy(distance.toFloat())
    }

    /** 上游 `scrollToRealTime(animationDuration?)`：回到最新。 */
    fun scrollToRealTime(animationDuration: Long? = null) {
        state.viewport.scrollToLatest(state.dataSnapshot.candles.size)
        state.notifyViewportChanged()
    }

    /** 上游 `scrollToDataIndex(dataIndex, animationDuration?)`。 */
    fun scrollToDataIndex(dataIndex: Int, animationDuration: Long? = null) {
        state.viewport.scrollToIndex(dataIndex.toDouble(), state.dataSnapshot.candles.size)
        state.notifyViewportChanged()
    }

    /** 上游 `scrollToTimestamp(timestamp, animationDuration?)`。 */
    fun scrollToTimestamp(timestamp: Long, animationDuration: Long? = null): Boolean {
        val candles = state.dataSnapshot.candles
        val idx = Viewport_indexOfTimestamp(timestamp, candles) ?: return false
        scrollToDataIndex(idx)
        return true
    }

    private fun Viewport_indexOfTimestamp(timestamp: Long, candles: List<Candle>): Int? =
        io.lkanvas.core.viewport.Viewport.indexOfTimestamp(timestamp, candles)

    /** 上游 `zoomAtCoordinate(scale, coordinate?, animationDuration?)`：缺省锚=当前视口中心。 */
    fun zoomAtCoordinate(scale: Double, coordinate: Pair<Double, Double>? = null, animationDuration: Long? = null) {
        val vp = state.viewport
        val anchorX = (coordinate?.first ?: (vp.contentWidth / 2)).toFloat()
        state.zoomAt(scale.toFloat(), anchorX)
    }

    /** 上游 `zoomAtDataIndex(scale, dataIndex, animationDuration?)`。 */
    fun zoomAtDataIndex(scale: Double, dataIndex: Int, animationDuration: Long? = null) {
        val vp = state.viewport
        vp.scrollToIndex(dataIndex.toDouble(), state.dataSnapshot.candles.size)
        val anchorX = vp.candleCenterX(dataIndex.toDouble()).toFloat()
        state.zoomAt(scale.toFloat(), anchorX)
        // scrollToIndex 居中后缩放锚点即该索引中心
    }

    /** 上游 `zoomAtTimestamp(scale, timestamp, animationDuration?)`。 */
    fun zoomAtTimestamp(scale: Double, timestamp: Long, animationDuration: Long? = null): Boolean {
        val idx = Viewport_indexOfTimestamp(timestamp, state.dataSnapshot.candles) ?: return false
        zoomAtDataIndex(scale, idx)
        return true
    }

    /** 上游 `setOffsetRightDistance(distance)`：px→槽位换算（barSpacing 为槽宽）。 */
    fun setOffsetRightDistance(distance: Double) {
        offsetRightDistanceInternal = distance
        val slots = if (state.viewport.barSpacing > 0) (distance / state.viewport.barSpacing).toInt() else 0
        state.viewport.rightOffsetSlots = slots
        state.notifyViewportChanged()
    }

    /** init 重载后按上游 `_addData` init 分支重放（Store.Host.onDataAdjusted 消费）。 */
    private var offsetRightDistanceInternal: Double? = null

    /** 上游 `getOffsetRightDistance()`：按当前 barSpacing 反算 px。 */
    fun getOffsetRightDistance(): Double = state.viewport.rightOffsetSlots * state.viewport.barSpacing

    /** 上游 `setMaxOffsetLeftDistance(distance)`（overscroll 左向，批次 2c 接手势钳制）。 */
    fun setMaxOffsetLeftDistance(distance: Double) {
        state.viewport.overscrollSlots = if (state.viewport.barSpacing > 0) distance / state.viewport.barSpacing else 0.0
    }

    /** 上游 `setMaxOffsetRightDistance(distance)`（与左向共享 overscroll 预算，登记差异）。 */
    fun setMaxOffsetRightDistance(distance: Double) {
        setMaxOffsetLeftDistance(distance)
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

    /** 上游 `setZoomEnabled(enabled)` / `isZoomEnabled()`。 */
    internal var zoomEnabled: Boolean = true

    fun setZoomEnabled(enabled: Boolean) {
        zoomEnabled = enabled
    }

    fun isZoomEnabled(): Boolean = zoomEnabled

    /** 上游 `setScrollEnabled(enabled)` / `isScrollEnabled()`。 */
    internal var scrollEnabled: Boolean = true

    fun setScrollEnabled(enabled: Boolean) {
        scrollEnabled = enabled
    }

    fun isScrollEnabled(): Boolean = scrollEnabled

    /** 上游 `setZoomAnchor(anchor)` / `getZoomAnchor()`。 */
    internal var zoomAnchor: ZoomAnchor = ZoomAnchor()

    fun setZoomAnchor(anchor: ZoomAnchor) {
        zoomAnchor = anchor
    }

    fun getZoomAnchor(): ZoomAnchor = zoomAnchor

    // ---------- 坐标转换（convertToPixel/convertFromPixel） ----------

    /** 上游 `convertToPixel`：数据点→画布像素；filter 限 pane（Alpha 仅主 pane）。 */
    fun convertToPixel(points: List<io.lkanvas.core.overlay.OverlayPoint>): List<Pair<Double, Double>> {
        val vp = state.viewport
        return points.map { p ->
            val idx = io.lkanvas.core.viewport.Viewport.indexOfTimestamp(p.timestamp, state.dataSnapshot.candles)
                ?: return@map Pair(Double.NaN, Double.NaN)
            val x = vp.candleCenterX(idx.toDouble())
            val y = state.mainPaneYOf(p.value) ?: Double.NaN
            Pair(x, y)
        }
    }

    /** 上游 `convertFromPixel`：画布像素→数据点。 */
    fun convertFromPixel(coordinates: List<Pair<Double, Double>>): List<io.lkanvas.core.overlay.OverlayPoint> {
        return coordinates.map { (x, y) ->
            state.overlayPointFromPx(x.toFloat(), y.toFloat())
                ?: io.lkanvas.core.overlay.OverlayPoint(timestamp = -1L, value = Double.NaN)
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
            emit(
                ActionType.ON_CROSSHAIR_CHANGE,
                Crosshair(
                    x = x, y = y, paneId = "main", realX = x,
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
        val type = when (paneId) {
            "main" -> ActionType.ON_CANDLE_TOOLTIP_FEATURE_CLICK
            "vol" -> ActionType.ON_INDICATOR_TOOLTIP_FEATURE_CLICK
            else -> ActionType.ON_CROSSHAIR_FEATURE_CLICK
        }
        emit(type, mapOf("paneId" to paneId, "featureId" to featureId))
    }

    /** 上游 `unsubscribeAction(type, callback?)`；callback 为 null 时清空该类型。 */
    fun unsubscribeAction(type: ActionType, callback: ActionCallback? = null) {
        val list = actionCallbacks.getValue(type)
        if (callback == null) list.clear() else list.remove(callback)
    }

    /** 上游 `executeAction(type, data)`：手动触发事件分发。 */
    fun executeAction(type: ActionType, data: Any?) {
        actionCallbacks.getValue(type).forEach { it.onAction(data) }
    }

    private fun emit(type: ActionType, data: Any?) = executeAction(type, data)

    // ---------- 数据（core Store 数据路径镜像：_processDataLoad/_addData/resetData；SLA-005/006） ----------

    /**
     * 数据路径编排器：拉取触发、more 同方向更新、边界分页、退订顺序与
     * 请求代次门禁全部在 core/store/Store.kt（上游 Store.ts 数据段镜像）；
     * 门面只提供宿主桥（数据应用到内核）与协程串行化。
     */
    private val store: Store = Store(storeHost())

    private fun storeHost() = object : Store.Host {
        override fun applyInit(data: List<Candle>): Boolean {
            scope.launch { state.reset(currentKey(), data) }
            return true
        }

        override fun applyBackward(data: List<Candle>): Boolean {
            scope.launch { state.appendBatch(data) }
            return true
        }

        override fun applyForward(data: List<Candle>): Boolean {
            scope.launch { state.prependHistory(data) }
            return true
        }

        override fun applyUpdate(candle: Candle): Boolean {
            scope.launch { state.upsertLatest(candle) }
            return true
        }

        override fun candleCount(): Int = state.dataSnapshot.candles.size

        override fun firstTimestamp(): Long? = state.dataSnapshot.candles.firstOrNull()?.timestamp

        override fun lastTimestamp(): Long? = state.dataSnapshot.candles.lastOrNull()?.timestamp

        override fun onDataAdjusted(type: DataLoadType) {
            // 上游 _addData 成功分支：_adjustVisibleRange + init 时 setOffsetRightDistance 重放；
            // 本域视口派生与重算由内核在写入路径完成，事件经 onVisibleRangeChanged 自然触发
            if (type == DataLoadType.INIT) {
                offsetRightDistanceInternal?.let { d -> setOffsetRightDistance(d) }
            }
        }
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

    private fun currentKey(): MarketKey =
        MarketKey(store.symbol?.ticker ?: "UNKNOWN", store.period?.toString() ?: "UNKNOWN")

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
    ): Boolean = state.overrideIndicator(
        filter, calcParams, visible, styles, shortName, paneId, yAxisId, zLevel, extendData,
    )

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


    /** 上游 `setStyles(value: string | DeepPartial<Styles>)`。 */
    fun setStyles(value: String) {
        styles = io.lkanvas.chart.styles.StylesMerge.resolveTheme(value)
    }

    fun setStyles(patch: io.lkanvas.chart.styles.Styles) {
        styles = io.lkanvas.chart.styles.StylesMerge.merge(styles, patch) as io.lkanvas.chart.styles.Styles
    }

    /**
     * 严格合并版 setStyles（0.3.0）：字段值非 null 即覆盖（无 skip-defaults 启发式）。
     * 用于宿主把字段**切回默认值**的场景（skip-defaults 会把「==默认值」当未提供，
     * 从 rect 切回 standard、隐藏切回显示会被吞；矩阵差异登记见 StylesMerge 注释）。
     */
    fun setStylesStrict(patch: io.lkanvas.chart.styles.Styles) {
        styles = io.lkanvas.chart.styles.StylesMerge.mergeStrict(styles, patch) as io.lkanvas.chart.styles.Styles
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
        when (options.id) {
            "main" -> {
                // 主 pane 高度变化：剩余空间按当前比例分给 VOL/MACD
                val heightF = (options.height ?: cur.main).toFloat()
                val mainTarget = heightF.coerceIn(60f, (usable * 0.65f))
                val rest = (usable - mainTarget).coerceAtLeast(0f)
                val ratio = if (cur.vol + cur.macd > 0) cur.vol / (cur.vol + cur.macd) else 0.5f
                val vol = rest * ratio
                val macd = rest - vol
                state.setSubPaneHeights(vol, macd)
            }

            "vol" -> {
                val vol = (options.height ?: cur.vol).toFloat()
                state.setSubPaneHeights(vol, usable - vol)
            }

            "macd" -> {
                val macd = (options.height ?: cur.macd).toFloat()
                state.setSubPaneHeights(usable - macd, macd)
            }
        }
    }

    /** 上游 `getPaneOptions(id?)`：单 pane 或全列表。 */
    fun getPaneOptions(id: String? = null): List<PaneOptions> {
        val p = state.paneHeightsPx
        val all = listOf(
            PaneOptions("main", p.main.toDouble()),
            PaneOptions("vol", p.vol.toDouble()),
            PaneOptions("macd", p.macd.toDouble()),
        )
        return if (id == null) all else all.filter { it.id == id }
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
    /** 0.4.0 多轴渲染：把自定义轴（paneId=main）同步到 ChartState（渲染消费源）。 */
    private fun syncExtraAxes() {
        val mains = yAxes.values.filter { it.id != "main" && it.paneId == "main" }
        state.extraYAxisMeta = mains.associate {
            it.id to io.lkanvas.compose.state.ChartState.ExtraAxisMeta(
                reverse = it.reverse,
                inside = it.inside,
                positionLeft = it.position == AxisPosition.LEFT,
            )
        }
        state.extraYAxisCustom = mains.mapNotNull { a -> a.customAxis?.let { a.id to it } }.toMap()
    }

    fun createYAxis(yAxis: YAxisOverride): String? {
        val paneId = yAxis.paneId ?: "main"
        if (paneId == "candle_x") return null // x 轴 pane 不支持 y 轴（上游 PaneIdConstants.X_AXIS）
        val id = yAxis.id ?: "yAxis_${++yAxisSerial}"
        if (yAxes.containsKey(id)) return id
        yAxes[id] = YAxisInfo(
            id = id,
            name = yAxis.name,
            paneId = paneId,
            reverse = yAxis.reverse ?: false,
            inside = yAxis.inside ?: false,
            position = yAxis.position ?: AxisPosition.RIGHT,
            scrollZoomEnabled = true,
            gap = yAxis.gap ?: AxisGap(),
            needWidget = yAxis.needWidget ?: true,
            isDefault = false,
            customAxis = yAxis.customAxis,
        )
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

    /** 上游 `overrideXAxis(xAxis)`：时间轴覆写登记。 */
    fun overrideXAxis(xAxis: XAxisOverride) {
        xAxisState = xAxisState.copy(
            name = xAxis.name ?: xAxisState.name,
            reverse = xAxis.reverse ?: xAxisState.reverse,
            inside = xAxis.inside ?: xAxisState.inside,
            position = xAxis.position ?: xAxisState.position,
            scrollZoomEnabled = xAxis.scrollZoomEnabled ?: xAxisState.scrollZoomEnabled,
            gap = xAxis.gap ?: xAxisState.gap,
        )
        state.notifyViewportChanged()
    }

    // ---------- 视口开关收尾（2c） ----------

    fun resize() {
        state.notifyViewportChanged()
    }
}
