package io.lkanvas.compose.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.lkanvas.core.compute.AppliedChange
import io.lkanvas.core.compute.ChangeRequest
import io.lkanvas.core.compute.ChartEngine
import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.ChangeKind
import io.lkanvas.core.indicator.IndicatorEngine
import io.lkanvas.core.indicator.IndicatorInstance
import io.lkanvas.core.indicator.IndicatorRegistry
import io.lkanvas.core.indicator.IndicatorResultState
import io.lkanvas.core.indicator.builtin.MacdIndicator
import io.lkanvas.core.indicator.builtin.MovingAverageIndicator
import io.lkanvas.core.indicator.builtin.VolumeIndicator
import io.lkanvas.core.store.ChangeResult
import io.lkanvas.core.store.DataSnapshot
import io.lkanvas.core.store.MarketKey
import io.lkanvas.core.viewport.Viewport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 图表状态门面：把核心引擎（数据/会话/指标）与 Compose 可观察状态桥接。
 *
 * - 数据变更经 [ChartEngine] 串行队列（主线程 immediate 串行上下文），
 *   指标在快照回调中同步推进（Alpha：增量 O(参数)，全量仅初始/前插）。
 * - Compose 侧读取 [dataSnapshot] / [indicatorResults] / [viewRevision] 触发重绘；
 *   平移/缩放只递增 [viewRevision]，不重组节点、不触发指标计算。
 */
class ChartState(
    externalScope: CoroutineScope? = null,
    /** 新行情到达时是否在末端自动跟随（PRD R-05）。 */
    var followLatest: Boolean = true,
) {

    private val scope = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val viewport = Viewport()

    val registry = IndicatorRegistry().apply {
        register(MovingAverageIndicator())
        register(VolumeIndicator())
        register(MacdIndicator())
    }

    private val engine = ChartEngine(
        scope = scope,
        computeDispatcher = Dispatchers.Default,
    )

    /** Alpha 默认实例：主图 MA、VOL、MACD。 */
    private val maInstance = IndicatorInstance("builtin-ma", registry.get("MA")!!)
    private val volInstance = IndicatorInstance("builtin-vol", registry.get("VOL")!!)
    private val macdInstance = IndicatorInstance("builtin-macd", registry.get("MACD")!!)

    /** 参与计算的实例（顺序即 pane 顺序：主图 MA 不单独成 pane）。 */
    private val computeInstances = listOf(maInstance, volInstance, macdInstance)

    var dataSnapshot by mutableStateOf(DataSnapshot(0, 0, null, emptyList()))
        private set

    var maResult by mutableStateOf<IndicatorResultState?>(null)
        private set
    var volResult by mutableStateOf<IndicatorResultState?>(null)
        private set
    var macdResult by mutableStateOf<IndicatorResultState?>(null)
        private set

    /** 视口版本：平移/缩放/布局变化递增，驱动 Canvas 重绘。 */
    var viewRevision by mutableIntStateOf(0)
        private set

    /** candle 槽位中间隙占比（0..1）。 */
    var gapRatio: Double = 0.25

    /** 主 pane 纵轴规格（R-06/T-16：normal/logarithm/percentage/custom）。 */
    var mainAxisSpec: io.lkanvas.core.axis.YAxisSpec = io.lkanvas.core.axis.YAxisSpec.Normal

    /** 生效规格：percentage 未指定基准时取首可见 candle close（上游口径）。 */
    fun effectiveMainAxisSpec(): io.lkanvas.core.axis.YAxisSpec {
        val spec = mainAxisSpec
        return if (spec is io.lkanvas.core.axis.YAxisSpec.Percentage && spec.base == null) {
            io.lkanvas.core.axis.YAxisSpec.Percentage(firstVisibleClose())
        } else {
            spec
        }
    }

    private fun firstVisibleClose(): Double? {
        val candles = dataSnapshot.candles
        if (candles.isEmpty()) return null
        val vp = viewport
        val from = kotlin.math.floor(vp.scrollIndex).toInt().coerceIn(candles.indices)
        return candles[from].close
    }

    /** 十字线位置（内容区像素；null=未显示）。手势层更新。 */
    var crosshairPosition by mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)

    /** 覆盖物管理器（领域状态；串行上下文操作）。 */
    val overlays = io.lkanvas.core.overlay.OverlayManager()

    /** 覆盖物版本：增删/拖动后递增驱动重绘。 */
    var overlayRevision by mutableIntStateOf(0)
        private set

    fun notifyOverlayChanged() {
        overlayRevision++
    }

    /** 进入 segment 创建模式（Alpha 工具入口）。 */
    fun beginCreateSegment(): Boolean =
        overlays.beginCreate(io.lkanvas.core.overlay.SegmentDefinition) != null

    fun cancelCreateOverlay() {
        overlays.cancelCreate()
        overlayRevision++
    }

    /** 主 pane 当前可见范围（与绘制共用同一来源，A-12 一致性）。 */
    fun mainPaneRange(): Pair<Double, Double>? {
        val candles = dataSnapshot.candles
        if (candles.isEmpty()) return null
        val vp = viewport
        val from = kotlin.math.floor(vp.scrollIndex).toInt().coerceIn(candles.indices)
        val to = kotlin.math.ceil(vp.scrollIndex + vp.visibleCount).toInt().coerceIn(candles.indices)
        if (to < from) return null
        return io.lkanvas.core.axis.LinearYAxis.rangeOfCandles(candles.subList(from, to + 1))
    }

    /** y 像素 → 主 pane 数值（与绘制同一映射）。 */
    fun mainPaneValueAt(y: Float): Double? {
        val (lo, hi) = mainPaneRange() ?: return null
        return io.lkanvas.core.axis.LinearYAxis.yToValue(
            y.toDouble(), lo, hi, 0.0, paneHeightsPx.main.toDouble(),
        )
    }

    /** 数值 → y 像素（主 pane）。 */
    fun mainPaneYOf(value: Double): Double? {
        val (lo, hi) = mainPaneRange() ?: return null
        return io.lkanvas.core.axis.LinearYAxis.valueToY(
            value, lo, hi, 0.0, paneHeightsPx.main.toDouble(),
        )
    }

    /** 时间戳 → 数据索引（不存在取不大于它的最近一根；早于首根取首根）。 */
    fun indexOfTimestampNearest(ts: Long): Int? {
        val candles = dataSnapshot.candles
        if (candles.isEmpty()) return null
        val exact = io.lkanvas.core.viewport.Viewport.indexOfTimestamp(ts, candles)
        return exact ?: run {
            var lo = 0
            var hi = candles.size - 1
            var ans = 0
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (candles[mid].timestamp <= ts) {
                    ans = mid; lo = mid + 1
                } else hi = mid - 1
            }
            ans
        }
    }

    /** 锚点 → 像素（主 pane）。 */
    fun overlayPointToPx(p: io.lkanvas.core.overlay.OverlayPoint): androidx.compose.ui.geometry.Offset? {
        val idx = indexOfTimestampNearest(p.timestamp) ?: return null
        val x = viewport.candleCenterX(idx.toDouble())
        val y = mainPaneYOf(p.value) ?: return null
        return androidx.compose.ui.geometry.Offset(x.toFloat(), y.toFloat())
    }

    /** 像素 → 锚点（按磁吸模式吸附；PRD R-09）。 */
    fun overlayPointFromPx(x: Float, y: Float): io.lkanvas.core.overlay.OverlayPoint? {
        val candles = dataSnapshot.candles
        if (candles.isEmpty()) return null
        val idx = viewport.logicalIndexAt(x.toDouble()).roundToInt().coerceIn(candles.indices)
        val candle = candles[idx]
        val value = when (overlays.magnetMode) {
            io.lkanvas.core.overlay.MagnetMode.NORMAL ->
                mainPaneValueAt(y) ?: candle.close
            io.lkanvas.core.overlay.MagnetMode.WEAK ->
                mainPaneValueAt(y) ?: candle.close
            io.lkanvas.core.overlay.MagnetMode.STRONG -> {
                val raw = mainPaneValueAt(y) ?: candle.close
                // 吸附到最近 OHLC
                listOf(candle.open, candle.high, candle.low, candle.close).minByOrNull {
                    kotlin.math.abs(it - raw)
                } ?: raw
            }
        }
        return io.lkanvas.core.overlay.OverlayPoint(candle.timestamp, value)
    }

    /** candle 点击事件（PRD R-10）。 */
    var onCandleClick: ((io.lkanvas.core.data.Candle) -> Unit)? = null

    /** 手势所有权：任一时刻至多一个交互所有者（PRD A-33 前置）。 */
    var gestureOwner: GestureOwner = GestureOwner.NONE
        private set

    /** 惯性动画任务（新手势开始时取消）。 */
    private var flingJob: kotlinx.coroutines.Job? = null

    fun setGestureOwner(owner: GestureOwner) {
        gestureOwner = owner
    }

    /** 手势入口统一封装：取消惯性、设置所有者。 */
    fun beginGesture(owner: GestureOwner) {
        flingJob?.cancel()
        flingJob = null
        gestureOwner = owner
    }

    fun endGesture() {
        gestureOwner = GestureOwner.NONE
    }

    /** 平移（px；手势层调用）。 */
    fun panBy(deltaX: Float) {
        viewport.scrollBy(deltaX.toDouble(), dataSnapshot.candles.size)
        viewRevision++
    }

    /** 以触点为锚缩放。 */
    fun zoomAt(scale: Float, anchorX: Float) {
        viewport.zoomBy(scale.toDouble(), anchorX.toDouble(), dataSnapshot.candles.size)
        viewRevision++
    }

    /** 长按进入十字线模式：x 吸附最近 candle 中心。 */
    fun showCrosshairAt(x: Float, y: Float) {
        val candles = dataSnapshot.candles
        if (candles.isEmpty()) return
        val vp = viewport
        val idx = vp.logicalIndexAt(x.toDouble()).toInt().coerceIn(candles.indices)
        crosshairPosition = androidx.compose.ui.geometry.Offset(
            vp.candleCenterX(idx.toDouble()).toFloat(),
            y.coerceIn(0f, paneHeightsPx.main + paneHeightsPx.vol + paneHeightsPx.macd),
        )
    }

    fun moveCrosshair(x: Float, y: Float) = showCrosshairAt(x, y)

    fun hideCrosshair() {
        crosshairPosition = null
    }

    /** 点击：解析命中 candle 并回调。 */
    fun tapCandle(x: Float) {
        val candles = dataSnapshot.candles
        if (candles.isEmpty()) return
        val idx = viewport.logicalIndexAt(x.toDouble()).roundToInt().coerceIn(candles.indices)
        onCandleClick?.invoke(candles[idx])
    }

    /** 基础惯性：指数衰减平移直到速度接近零（取消即中断）。 */
    fun fling(velocityX: Float) {
        if (kotlin.math.abs(velocityX) < 200f) return
        flingJob?.cancel()
        flingJob = scope.launch {
            var velocity = velocityX
            while (kotlin.math.abs(velocity) > 60f) {
                panBy(velocity * 16f / 1000f)
                velocity *= 0.94f
                kotlinx.coroutines.delay(16L)
            }
        }
    }

    /** 布局宽度（px），由 composable 的 onSizeChanged 回填。 */
    var contentWidthPx: Float = 0f
        private set

    /** 可见 pane 高度（px），由 composable 布局后回填。 */
    var paneHeightsPx: PaneHeights = PaneHeights(0f, 0f, 0f, 0f)
        private set

    init {
        engine.onSnapshot = { snap, change ->
            val prevCount = dataSnapshot.candles.size
            dataSnapshot = snap
            viewport.layout(contentWidthPx.toDouble(), snap.candles.size)
            when (change?.op) {
                null -> Unit
                else -> adjustViewport(change, prevCount)
            }
            advanceIndicators(snap, change)
            viewRevision++
        }
        scope.launch { engine.run() }
    }

    private fun adjustViewport(change: AppliedChange, prevCount: Int) {
        when (change.op) {
            io.lkanvas.core.store.DataOp.RESET -> Unit // layout 已按最新定位
            io.lkanvas.core.store.DataOp.PREPEND_HISTORY -> viewport.onPrepended(change.prependedCount)
            else -> if (change.appendedCount > 0) {
                viewport.onAppended(change.appendedCount, prevCount, followLatest)
            }
        }
    }

    private fun advanceIndicators(snap: DataSnapshot, change: AppliedChange?) {
        val kind = when (change?.op) {
            null, io.lkanvas.core.store.DataOp.RESET -> ChangeKind.Reset
            io.lkanvas.core.store.DataOp.REPLACE_LATEST -> ChangeKind.ReplaceLatest
            io.lkanvas.core.store.DataOp.PREPEND_HISTORY -> ChangeKind.Prepend(change.prependedCount)
            io.lkanvas.core.store.DataOp.PATCH_HISTORY -> ChangeKind.Patch
            io.lkanvas.core.store.DataOp.UPSERT_LATEST ->
                if (change.appendedCount > 0) ChangeKind.Append(change.appendedCount) else ChangeKind.ReplaceLatest
            io.lkanvas.core.store.DataOp.APPEND_BATCH -> ChangeKind.Append(change.appendedCount)
        }
        for (inst in computeInstances) {
            if (inst.visible) IndicatorEngine.computeInto(inst, snap.candles, kind)
        }
        maResult = maInstance.state
        volResult = volInstance.state
        macdResult = macdInstance.state
    }

    // ---------- 布局回填 ----------

    fun onLayout(widthPx: Float, panes: PaneHeights) {
        contentWidthPx = widthPx
        paneHeightsPx = panes
        viewport.layout(widthPx.toDouble(), dataSnapshot.candles.size)
        viewRevision++
    }

    fun notifyViewportChanged() {
        viewRevision++
    }

    // ---------- 公开数据操作（挂起经引擎队列；主线程调用） ----------

    suspend fun reset(key: MarketKey, candles: List<Candle>): ChangeResult =
        engine.apply(ChangeRequest.Reset(key, candles))

    suspend fun upsertLatest(candle: Candle): ChangeResult =
        engine.apply(ChangeRequest.UpsertLatest(candle))

    suspend fun appendBatch(candles: List<Candle>): ChangeResult =
        engine.apply(ChangeRequest.AppendBatch(candles))

    suspend fun prependHistory(candles: List<Candle>): ChangeResult =
        engine.apply(ChangeRequest.PrependHistory(candles))

    /** 显式纠正已存在历史值（R-02 PatchHistory；指标后缀重算）。 */
    suspend fun patchHistory(patches: List<Candle>): ChangeResult =
        engine.apply(ChangeRequest.PatchHistory(patches))

    /** pane 高度（px）：主图自适应，VOL/MACD 固定，时间轴固定。 */
    data class PaneHeights(
        val main: Float,
        val vol: Float,
        val macd: Float,
        val timeAxis: Float,
    )
}

/** 手势所有者（PRD R-10：任一时刻只有一个明确操作所有者）。 */
enum class GestureOwner { NONE, PAN, PINCH, CROSSHAIR, OVERLAY_EDIT }
