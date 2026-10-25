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

    /** 十字线位置（内容区像素；null=未显示）。手势层更新（T-08）。 */
    var crosshairPosition by mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)

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

    /** pane 高度（px）：主图自适应，VOL/MACD 固定，时间轴固定。 */
    data class PaneHeights(
        val main: Float,
        val vol: Float,
        val macd: Float,
        val timeAxis: Float,
    )
}
