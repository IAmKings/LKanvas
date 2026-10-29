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
import io.lkanvas.core.extension.indicator.MacdIndicator
import io.lkanvas.core.extension.indicator.MovingAverageIndicator
import io.lkanvas.core.extension.indicator.VolumeIndicator
import io.lkanvas.core.store.ChangeResult
import io.lkanvas.core.store.DataSnapshot
import io.lkanvas.core.store.MarketKey
import io.lkanvas.core.viewport.Viewport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
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
        // 图表级扩展注册表（宿主按图注册；内置 27 模板统一走全局
        // IndicatorTemplates——上游 Store 创建查同一注册入口，SLA-004）
    }

    private val engine = ChartEngine(
        scope = scope,
        computeDispatcher = Dispatchers.Default,
    )

    /**
     * 上游 v10.1.0 严格语义（SLA-007）：图表初始化**不创建**任何默认指标实例
     * （上游 Chart.ts init 仅建 candle/x-axis pane）；示例需要的 MA/VOL/MACD
     * 由宿主/示例显式 createIndicator。
     */
    private val indicatorInstances = CopyOnWriteArrayList<IndicatorInstance>()

    private var indicatorIdSerial = 0
    private var indicatorPaneSerial = 0

    /**
     * 创建指标实例（上游 Chart.createIndicator + Store.addIndicator 语义，SLA-007）：
     * - 未知模板返回 null（模板查图表级 registry → 全局 IndicatorTemplates 统一源）；
     * - paneId 缺省 = 生成新指标 pane id（上游 createId(INDICATOR)）；
     * - isStack 缺省 false（非叠加=先清空目标 pane 再创建，上游 Store.addIndicator）；
     * - 调用方提供 id 且已存在同 id 实例 → 返回 null（上游同名过滤命中即 false）。
     */
    fun createIndicator(
        name: String,
        params: List<Double>? = null,
        paneId: String? = null,
        isStack: Boolean = false,
        yAxisId: String = "main",
        styles: Any? = null,
        id: String? = null,
    ): String? {
        val def = registry.get(name)
            ?: io.lkanvas.core.extension.indicator.IndicatorTemplates.get(name)
            ?: return null
        if (id != null && indicatorInstances.any { it.id == id }) return null
        val resolvedPaneId = paneId ?: "pane_indicator_${++indicatorPaneSerial}"
        if (!isStack) {
            removeIndicator(IndicatorFilter(paneId = resolvedPaneId))
        }
        val instanceId = id ?: "ind_${++indicatorIdSerial}_${name}"
        val inst = IndicatorInstance(instanceId, def, params ?: def.calcParams, resolvedPaneId, yAxisId, styles)
        indicatorInstances.add(inst)
        advanceIndicators(dataSnapshot, null)
        relayout() // R07：paneList 派生（vol/macd 按需出现）需要重算布局
        viewRevision++
        return instanceId
    }

    /** 实例过滤（上游 IndicatorFilter：id/name/paneId 任意组合）。 */
    data class IndicatorFilter(
        val id: String? = null,
        val name: String? = null,
        val paneId: String? = null,
    )

    private fun IndicatorInstance.matches(f: IndicatorFilter): Boolean =
        (f.id == null || id == f.id) &&
            (f.name == null || definition.name == f.name) &&
            (f.paneId == null || paneId == f.paneId)

    /** 上游 getIndicators(filter?)。 */
    fun getIndicators(filter: IndicatorFilter = IndicatorFilter()): List<IndicatorInstance> =
        indicatorInstances.filter { it.matches(filter) }

    /** 上游 getIndicatorByInstanceId(id)。 */
    fun getIndicatorByInstanceId(id: String): IndicatorInstance? =
        indicatorInstances.firstOrNull { it.id == id }

    /**
     * 上游 overrideIndicator（SLA-007 全字段）：按 filter 定位**全部**匹配实例，
     * 非空字段覆盖（params/visible/styles/shortName/paneId/yAxisId/zLevel/extendData）。
     */
    fun overrideIndicator(
        filter: IndicatorFilter,
        params: List<Double>? = null,
        visible: Boolean? = null,
        styles: Any? = null,
        shortName: String? = null,
        paneId: String? = null,
        yAxisId: String? = null,
        zLevel: Int? = null,
        extendData: Map<String, Any?>? = null,
    ): Boolean {
        val targets = indicatorInstances.filter { it.matches(filter) }
        if (targets.isEmpty()) return false
        for (inst in targets) {
            params?.let { inst.updateParams(it) }
            visible?.let { inst.visible = it }
            styles?.let { inst.styles = it }
            shortName?.let { inst.shortNameOverride = it }
            paneId?.let { inst.paneId = it }
            yAxisId?.let { inst.yAxisId = it }
            zLevel?.let { inst.zLevelOverride = it }
            extendData?.let { ext -> inst.extendData.putAll(ext) }
        }
        advanceIndicators(dataSnapshot, null)
        relayout() // R07：paneId 迁移后 paneList 派生刷新
        viewRevision++
        return true
    }

    /** 上游 overrideIndicator(id) 简写（filter=仅 id）。 */
    fun overrideIndicator(id: String, params: List<Double>? = null, visible: Boolean? = null): Boolean =
        overrideIndicator(IndicatorFilter(id = id), params = params, visible = visible)

    /** 上游 removeIndicator(filter)：删除全部匹配（上游 forEach 全删，无内置保护）。 */
    fun removeIndicator(filter: IndicatorFilter): Boolean {
        val targets = indicatorInstances.filter { it.matches(filter) }
        if (targets.isEmpty()) return false
        indicatorInstances.removeAll(targets.toSet())
        advanceIndicators(dataSnapshot, null)
        relayout() // R07：paneList 派生（vol/macd 按需消失）
        viewRevision++
        return true
    }

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

    /** 主图类型（T-18：六形态，默认实心蜡烛）。 */
    var mainChartType: io.lkanvas.compose.theme.MainChartType =
        io.lkanvas.compose.theme.MainChartType.CANDLE_SOLID

    /** 文案（R-11/T-24：中英文切换；默认中文）。 */
    var locale: io.lkanvas.core.i18n.ChartLocale = io.lkanvas.core.i18n.ChartLocale.ZH_CN

    /** 图例模式（PRD R-11：常显跟随 / 隐藏；宿主可用 legendSlot 全面定制）。 */
    var legendMode: LegendMode = LegendMode.VISIBLE

    enum class LegendMode { VISIBLE, HIDDEN }

    /**
     * 当前图例数据（十字线跟随或末根；R-11「常显、跟随十字线」）。
     * 空数据返回 null。
     */
    fun legendData(timeText: (Long) -> String = { ts -> io.lkanvas.compose.draw.ChartText.timeLabel(ts, 60_000L) }): LegendData? {
        val candles = dataSnapshot.candles
        if (candles.isEmpty()) return null
        val crosshair = crosshairPosition
        val idx = if (crosshair != null) {
            viewport.logicalIndexAt(crosshair.x.toDouble()).toInt().coerceIn(candles.indices)
        } else {
            accessibilitySelectedIndex ?: (candles.size - 1)
        }
        val ma = maResult
        val maValues = if (ma != null && idx < ma.result.size()) {
            ma.result.keys.associateWith { ma.result[it][idx] }
        } else {
            emptyMap()
        }
        return LegendData(
            index = idx,
            candle = candles[idx],
            prevClose = candles.getOrNull(idx - 1)?.close,
            isCrosshair = crosshair != null,
            timeText = timeText(candles[idx].timestamp),
            maValues = maValues,
        )
    }

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

    /** 运行诊断（默认关闭零开销；T-30/A-44）。 */
    val diagnostics = io.lkanvas.core.diagnostics.ChartDiagnostics()

    /** 存档恢复门禁（A-37：恢复期抑制保存事件）。 */
    val restoreGate = io.lkanvas.core.archive.RestoreGate()

    /** 存档变脏回调（恢复完成后由数据/覆盖物编辑触发；宿主用它触发持久化）。 */
    var onArchiveDirty: (() -> Unit)? = null

    private fun notifyArchiveDirty(hasContent: Boolean) {
        if (restoreGate.shouldEmitSave(hasContent)) {
            onArchiveDirty?.invoke()
        }
    }

    /**
     * 恢复存档（PRD R-12：宿主控制时机）。流程：
     * 1. beginRestore（期间 onSnapshot 引起的保存事件被抑制）；
     * 2. 写回覆盖物（未知模板条目不写入，保留在结果中）；
     * 3. 指标参数写回；
     * 4. 视口锚点写回（若数据已就绪）。
     * 数据本身由宿主按 archive.marketKey 喂入（Reset 不经本方法门禁语义不变）。
     */
    fun applyRestoredArchive(archive: io.lkanvas.core.archive.ChartArchive.Archive) {
        restoreGate.beginRestore()
        try {
            overlays.restoreFrom(archive.overlays)
            archive.indicatorParams.forEach { (name, params) ->
                getIndicators(IndicatorFilter(name = name)).forEach { it.updateParams(params) }
            }
            val anchor = archive.viewportAnchor
            if (anchor != null && dataSnapshot.candles.isNotEmpty()) {
                viewport.restoreAnchor(anchor.scrollIndex, anchor.barSpacing, dataSnapshot.candles.size)
            }
            // 0.5.x：恢复动态副图 pane 渲染序（宿主按 archive.extraPaneIds 重建指标后，
            // pane 经 ensureExtraPane 生成；此处仅回填排序）
            if (archive.extraPaneIds.isNotEmpty()) {
                extraPanes.value = archive.extraPaneIds
                relayout()
            }
            overlayRevision++
            viewRevision++
        } finally {
            restoreGate.endRestore(success = true)
        }
    }

    /** 覆盖物版本：增删/拖动后递增驱动重绘。 */
    var overlayRevision by mutableIntStateOf(0)
        private set

    fun notifyOverlayChanged() {
        overlayRevision++
    }

    /** 当前画线工具（模板名；默认 segment）。 */
    var currentTool: String by mutableStateOf("segment")

    /** 进入当前工具的创建模式（16 种模板通用入口）。 */
    fun beginCreateOverlay(): Boolean =
        overlays.beginCreate(io.lkanvas.core.extension.overlay.OverlayTemplates[currentTool] ?: io.lkanvas.core.extension.overlay.SegmentDefinition) != null

    /** 连续模板（brush）显式完成创建。 */
    fun finishCreateOverlay(): Boolean {
        val id = overlays.creatingId ?: return false
        return overlays.finishCreate(id)
    }

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

    /**
     * 主轴 reverse（SLA-010/012 统一消费：overrideYAxis(main) 同步；
     * 绘制 y 映射、十字线、覆盖物与门面 convert 共用）。
     */
    var mainAxisReverse: Boolean = false

    /** SLA-011：滚动开关（手势与程序化 panBy 共同遵守；上游 Store.scroll 检查）。 */
    var scrollEnabled: Boolean = true

    /** SLA-011：缩放开关（手势与程序化 zoomAt 共同遵守；上游 Store.zoomAt 检查）。 */
    var zoomEnabled: Boolean = true

    /**
     * SLA-011：逐根 candle 配色回调（上游 candle.bar 回调消费路径）；
     * 由门面 setStyles 接线（参数=数据索引，null=用主题静态配色）。
     */
    var candleBarColorProvider: ((Int) -> io.lkanvas.chart.styles.CandleBarColor?)? = null

    /**
     * y 像素 → 主 pane 数值（SLA-012 统一轴模型：与绘制同用 YAxisMath +
     * effectiveMainAxisSpec，log/percentage/custom 一致；reverse 逆映射）。
     */
    fun mainPaneValueAt(y: Float): Double? {
        val (lo, hi) = mainPaneRange() ?: return null
        val spec = effectiveMainAxisSpec()
        val bottom = paneHeightsPx.main.toDouble()
        val yy = if (mainAxisReverse) bottom - y else y
        return io.lkanvas.core.axis.YAxisMath.yToValue(spec, yy.toDouble(), lo, hi, 0.0, bottom)
    }

    /** 数值 → y 像素（SLA-012 统一轴模型：与绘制同用 YAxisMath；reverse 翻转）。 */
    fun mainPaneYOf(value: Double): Double? {
        val (lo, hi) = mainPaneRange() ?: return null
        val spec = effectiveMainAxisSpec()
        val bottom = paneHeightsPx.main.toDouble()
        val y = io.lkanvas.core.axis.YAxisMath.valueToY(spec, value, lo, hi, 0.0, bottom)
        return if (mainAxisReverse) bottom - y else y
    }

    /**
     * 绑定轴数值 → 主 pane 内 y 像素（SLA-010 convert 感知：与 PaneRenderers
     * 轴绑定绘制同式——extraAxisRange 值域 + custom 变换 + reverse 翻转）。
     * 主轴/未知轴回退 mainPaneYOf；无绑定值域返回 null。
     */
    fun axisValueToY(axisId: String, value: Double): Double? {
        if (axisId == "main") return mainPaneYOf(value)
        val meta = extraYAxisMeta[axisId] ?: return mainPaneYOf(value)
        val (vFrom, vTo) = visibleIndexRange() ?: return null
        val (alo, ahi) = extraAxisRange(axisId, vFrom, vTo) ?: return null
        val spec = effectiveExtraAxisSpec(axisId)
        val bottom = paneHeightsPx.main.toDouble()
        val y = io.lkanvas.core.axis.YAxisMath.valueToY(spec, value, alo, ahi, 0.0, bottom)
        return if (meta.reverse) bottom - y else y
    }

    /** R06：轴实例生效 spec（custom > 模板 spec > Normal）——绘制与转换共用。 */
    fun effectiveExtraAxisSpec(axisId: String): io.lkanvas.core.axis.YAxisSpec =
        extraYAxisCustom[axisId]?.let { io.lkanvas.core.axis.YAxisSpec.Custom(it) }
            ?: extraYAxisSpec[axisId]
            ?: io.lkanvas.core.axis.YAxisSpec.Normal

    /** 绑定轴 y 像素 → 数值（[axisValueToY] 的逆；convertFromPixel 用）。 */
    fun axisValueAt(axisId: String, y: Float): Double? {
        if (axisId == "main") return mainPaneValueAt(y)
        val meta = extraYAxisMeta[axisId] ?: return mainPaneValueAt(y)
        val (vFrom, vTo) = visibleIndexRange() ?: return null
        val (alo, ahi) = extraAxisRange(axisId, vFrom, vTo) ?: return null
        val spec = effectiveExtraAxisSpec(axisId)
        val bottom = paneHeightsPx.main.toDouble()
        val yy = if (meta.reverse) bottom - y else y
        return io.lkanvas.core.axis.YAxisMath.yToValue(spec, yy.toDouble(), alo, ahi, 0.0, bottom)
    }

    /**
     * pane 纵向几何（R05）：内容坐标下 (top, bottom)。main=0..main、
     * vol/macd 依 paneHeightsPx、extras 依 extraPaneLayout；未知 pane 返回 null。
     */
    fun paneBounds(paneId: String): Pair<Float, Float>? {
        val h = paneHeightsPx
        return when (paneId) {
            "main" -> 0f to h.main
            "vol" -> h.main to h.main + h.vol
            "macd" -> h.main + h.vol to h.main + h.vol + h.macd
            else -> extraPaneLayout.firstOrNull { it.first == paneId }?.second
        }
    }

    /**
     * pane 数值 → pane 相对 y（R05：与各 pane 渲染器同式——vol 0..maxVol、
     * macd/extra 可见区公共值域）；未知 pane/无数据返回 null。
     */
    fun paneValueToY(paneId: String, value: Double): Double? {
        val (top, bottom) = paneBounds(paneId) ?: return null
        val candles = dataSnapshot.candles
        if (candles.isEmpty()) return null
        val (vFrom, vTo) = visibleIndexRange() ?: return null
        val loHi: Pair<Double, Double>? = when (paneId) {
            "main" -> return mainPaneYOf(value)?.let { it - top }
            "vol" -> {
                val result = volumePaneResult()
                val visible = candles.subList(vFrom, vTo + 1)
                val maxVol = maxOf(
                    visible.maxOf { it.volume ?: Double.NaN }.takeIf { it.isFinite() } ?: 0.0,
                    result?.result?.columns
                        ?.filterKeys { it.startsWith("ma") }
                        ?.values
                        ?.flatMap { arr -> arr.slice(vFrom..vTo).filter { it.isFinite() } }
                        ?.maxOrNull() ?: 0.0,
                    1e-12,
                )
                0.0 to maxVol
            }
            else -> {
                val result = if (paneId == "macd") macdPaneResult()
                else indicatorInstances.firstOrNull { it.paneId == paneId && it.visible }?.state
                val cols = result?.result ?: return null
                var lo = Double.NaN
                var hi = Double.NaN
                for ((_, arr) in cols.columns) {
                    for (i in vFrom..vTo) {
                        val v = arr[i]
                        if (v.isNaN()) continue
                        if (lo.isNaN() || v < lo) lo = v
                        if (hi.isNaN() || v > hi) hi = v
                    }
                }
                if (lo.isNaN()) return null
                if (lo == hi) lo - 1.0 to hi + 1.0 else lo to hi
            }
        }
        val (lo, hi) = loHi ?: return null
        return io.lkanvas.core.axis.LinearYAxis.valueToY(value, lo, hi, top.toDouble(), bottom.toDouble()) - top
    }

    /** [paneValueToY] 的逆（pane 相对 y → 数值）。 */
    fun paneValueAt(paneId: String, yRelative: Float): Double? {
        val (top, bottom) = paneBounds(paneId) ?: return null
        val candles = dataSnapshot.candles
        if (candles.isEmpty()) return null
        val (vFrom, vTo) = visibleIndexRange() ?: return null
        val absY = yRelative + top
        return when (paneId) {
            "main" -> mainPaneValueAt(yRelative)
            "vol" -> {
                val result = volumePaneResult()
                val visible = candles.subList(vFrom, vTo + 1)
                val maxVol = maxOf(
                    visible.maxOf { it.volume ?: Double.NaN }.takeIf { it.isFinite() } ?: 0.0,
                    result?.result?.columns
                        ?.filterKeys { it.startsWith("ma") }
                        ?.values
                        ?.flatMap { arr -> arr.slice(vFrom..vTo).filter { it.isFinite() } }
                        ?.maxOrNull() ?: 0.0,
                    1e-12,
                )
                io.lkanvas.core.axis.LinearYAxis.yToValue(absY.toDouble(), 0.0, maxVol, top.toDouble(), bottom.toDouble())
            }
            else -> {
                val result = if (paneId == "macd") macdPaneResult()
                else indicatorInstances.firstOrNull { it.paneId == paneId && it.visible }?.state
                val cols = result?.result ?: return null
                var lo = Double.NaN
                var hi = Double.NaN
                for ((_, arr) in cols.columns) {
                    for (i in vFrom..vTo) {
                        val v = arr[i]
                        if (v.isNaN()) continue
                        if (lo.isNaN() || v < lo) lo = v
                        if (hi.isNaN() || v > hi) hi = v
                    }
                }
                if (lo.isNaN()) return null
                val (l, h2) = if (lo == hi) lo - 1.0 to hi + 1.0 else lo to hi
                io.lkanvas.core.axis.LinearYAxis.yToValue(absY.toDouble(), l, h2, top.toDouble(), bottom.toDouble())
            }
        }
    }

    /** 当前可见数据索引范围（与 mainPaneRange 同源；轴转换取值域用）。 */
    fun visibleIndexRange(): Pair<Int, Int>? {
        val candles = dataSnapshot.candles
        if (candles.isEmpty()) return null
        val from = kotlin.math.floor(viewport.scrollIndex).toInt().coerceIn(candles.indices)
        val to = kotlin.math.ceil(viewport.scrollIndex + viewport.visibleCount).toInt().coerceIn(candles.indices)
        if (to < from) return null
        return from to to
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
    fun overlayPointToPx(p: io.lkanvas.core.overlay.OverlayPoint): androidx.compose.ui.geometry.Offset? =
        overlayPointToPx(p, "main")

    /** 锚点 → 像素（R09：按实例 pane 的值映射与几何）。 */
    fun overlayPointToPx(p: io.lkanvas.core.overlay.OverlayPoint, paneId: String): androidx.compose.ui.geometry.Offset? {
        val idx = indexOfTimestampNearest(p.timestamp) ?: return null
        val x = viewport.candleCenterX(idx.toDouble())
        val y = paneValueToY(paneId, p.value) ?: return null
        return androidx.compose.ui.geometry.Offset(x.toFloat(), (y + (paneBounds(paneId)?.first ?: 0f)).toFloat())
    }

    /**
     * 像素 → 锚点（按磁吸模式吸附；PRD R-09）。
     * [sensitivityPx]：磁吸灵敏度（上游 modeSensitivity，默认 8px）——WEAK 模式下
     * 仅当目标像素与最近 OHLC 像素距离 ≤ 灵敏度时吸附（SLA-013 消费）。
     */
    fun overlayPointFromPx(
        x: Float,
        y: Float,
        sensitivityPx: Double = 8.0,
    ): io.lkanvas.core.overlay.OverlayPoint? {
        val candles = dataSnapshot.candles
        if (candles.isEmpty()) return null
        val idx = viewport.logicalIndexAt(x.toDouble()).roundToInt().coerceIn(candles.indices)
        val candle = candles[idx]
        val value = when (overlays.magnetMode) {
            io.lkanvas.core.overlay.MagnetMode.NORMAL ->
                mainPaneValueAt(y) ?: candle.close
            io.lkanvas.core.overlay.MagnetMode.WEAK -> {
                val raw = mainPaneValueAt(y) ?: candle.close
                // 弱磁吸：与最近 OHLC 的像素距离在灵敏度内才吸附
                val nearest = listOf(candle.open, candle.high, candle.low, candle.close).minByOrNull {
                    kotlin.math.abs(it - raw)
                } ?: raw
                val nearestPx = mainPaneYOf(nearest)
                val rawPx = mainPaneYOf(raw)
                if (nearestPx != null && rawPx != null &&
                    kotlin.math.abs(nearestPx - rawPx) <= sensitivityPx
                ) {
                    nearest
                } else {
                    raw
                }
            }
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

    /**
     * SLA-015：executeAction(ON_CROSSHAIR_CHANGE) 的状态写入（上游
     * setCrosshair(data, { notExecuteAction: true }) —— 写状态、抑制反向广播）。
     */
    /** R10：y 像素 → 实际 pane id（main/vol/macd 按需存在 + 动态 pane 布局）。 */
    fun paneIdAt(y: Float): String {
        val h = paneHeightsPx
        return when {
            y <= h.main -> "main"
            y <= h.main + h.vol && "vol" in paneList() -> "vol"
            y <= h.main + h.vol + h.macd && "macd" in paneList() -> "macd"
            else -> extraPaneLayout.lastOrNull { y >= it.second.first && y <= it.second.second }?.first
                ?: "macd"
        }
    }

    /**
     * R10（复审 2026-10-30）：executeAction 十字线写入——上游顺序
     * （Store.ts:1285-1310）：x 为数字时**按 x 推导数据索引**（coordinateToDataIndex），
     * x 缺省回退**最后一根**（非 0）； dataIndex 显式提供时仅在 x 缺省下使用。
     */
    fun applyCrosshairAction(x: Double, y: Double, dataIndex: Int) {
        val candles = dataSnapshot.candles
        if (candles.isEmpty()) return
        if (x.isNaN() || y.isNaN()) {
            crosshairPosition = null
            viewRevision++
            return
        }
        val idx = if (!x.isNaN()) {
            viewport.logicalIndexAt(x).roundToInt().coerceIn(candles.indices)
        } else {
            dataIndex.coerceIn(candles.indices)
        }
        val px = viewport.candleCenterX(idx.toDouble()).toFloat()
        crosshairPosition = androidx.compose.ui.geometry.Offset(
            px,
            y.toFloat().coerceIn(0f, paneHeightsPx.main + paneHeightsPx.vol + paneHeightsPx.macd),
        )
        viewRevision++
    }

    /** candle 点击事件（PRD R-10）。 */
    var onCandleClick: ((io.lkanvas.core.data.Candle) -> Unit)? = null

    /** 无障碍当前选择索引（null=跟随最新；A-40）。 */
    var accessibilitySelectedIndex: Int? = null
        private set

    /** 无障碍动作：切换到上一根（返回是否成功）。 */
    fun selectPreviousCandle(): Boolean {
        val candles = dataSnapshot.candles
        if (candles.isEmpty()) return false
        val next = ((accessibilitySelectedIndex ?: (candles.size - 1)) - 1).coerceIn(candles.indices)
        accessibilitySelectedIndex = next
        showCrosshairAt(
            viewport.candleCenterX(next.toDouble()).toFloat(),
            paneHeightsPx.main / 2f,
        )
        viewRevision++
        return true
    }

    /** 无障碍动作：切换到下一根。 */
    fun selectNextCandle(): Boolean {
        val candles = dataSnapshot.candles
        if (candles.isEmpty()) return false
        val next = ((accessibilitySelectedIndex ?: (candles.size - 1)) + 1).coerceIn(candles.indices)
        accessibilitySelectedIndex = next
        showCrosshairAt(
            viewport.candleCenterX(next.toDouble()).toFloat(),
            paneHeightsPx.main / 2f,
        )
        viewRevision++
        return true
    }

    /** 无障碍动作：放大/缩小（以最新为锚）。 */
    fun zoomIn(): Boolean {
        zoomAt(1.3f, paneHeightsPx.main / 2f)
        return true
    }

    fun zoomOut(): Boolean {
        zoomAt(1f / 1.3f, paneHeightsPx.main / 2f)
        return true
    }

    /** 无障碍动作：删除选中覆盖物（无选中返回 false）。 */
    fun removeSelectedOverlay(): Boolean {
        val id = overlays.selectedId ?: return false
        overlays.remove(id)
        notifyOverlayChanged()
        return true
    }

    /** 当前无障碍摘要文本（TalkBack contentDescription；供 semantics 层调用）。 */
    fun accessibilityText(timeText: (Long) -> String): String {
        val candles = dataSnapshot.candles
        if (candles.isEmpty()) return locale.noData
        val idx = accessibilitySelectedIndex ?: (candles.size - 1)
        val c = candles[idx]
        val prevClose = candles.getOrNull(idx - 1)?.close
        val candleText = io.lkanvas.core.accessibility.AccessibilitySummary.candleSummary(
            c, prevClose, timeText(c.timestamp), locale,
        ) { v -> String.format("%.2f", v) }
        val maText = maResult?.let { ma ->
            io.lkanvas.core.accessibility.AccessibilitySummary.indicatorSummary(
                "MA",
                ma.result.keys.toList(),
                ma.result.keys.map { ma.result[it][idx] },
                locale,
            ) { v -> String.format("%.2f", v) }
        }
        return buildString {
            append(candleText)
            if (maText != null) append("。").append(maText)
            // 0.3.0 批次 9a：覆盖物列表摘要（数量 + 选中项）
            val visibleOverlays = overlays.all().filter { it.visible }
            if (visibleOverlays.isNotEmpty()) {
                append("。").append(locale.overlaySummary(visibleOverlays.size))
                val selected = visibleOverlays.firstOrNull { it.id == overlays.selectedId }
                if (selected != null) {
                    append("。").append(
                        io.lkanvas.core.accessibility.AccessibilitySummary.overlaySummary(
                            selected.templateName, selected.points.size, selected.currentStep, locale,
                        ),
                    )
                }
            }
        }
    }

    /** 无障碍动作：选中下一画线（TalkBack 遍历覆盖物；循环）。 */
    fun selectNextOverlay(): Boolean {
        val list = overlays.all()
        if (list.isEmpty()) return false
        val curIdx = list.indexOfFirst { it.id == overlays.selectedId }
        val next = list[(curIdx + 1).mod(list.size)]
        overlays.select(next.id)
        notifyOverlayChanged()
        return true
    }

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

    /** 事件钩子（门面 LKanvasChart 注入；保持内核与门面解耦）。 */
    internal var onScrolled: ((distancePx: Double) -> Unit)? = null
    internal var onZoomed: ((scale: Double) -> Unit)? = null
    internal var onVisibleRangeChanged: ((from: Int, to: Int, realFrom: Double, realTo: Double) -> Unit)? = null
    internal var onCrosshairChanged: ((x: Double, y: Double, dataIndex: Int, timestamp: Long) -> Unit)? = null
    internal var onPaneDragged: ((paneId: String) -> Unit)? = null

    /** tooltip feature 点击钩子（手势命中图标后调用；门面映射三种 ActionType）。 */
    var onTooltipFeatureClick: ((paneId: String, featureId: String, x: Double, y: Double) -> Unit)? = null

    /** IANA 时区（0.3.0 批次 8e：门面 setTimezone 注入；空=系统时区）。 */
    var timezoneId: String = ""

    /**
     * 格式配置（上游 Options 的 thousandsSeparator/decimalFold/formatter 消费端，
     * SLA-011 格式链接线）：ChartText.price/volume 与时间标签统一读取。
     * thousandsSign=null 不分组；decimalFoldThreshold=null 不折叠；
     * formatDate/formatBigNumber 自定义钩子未提供时用内置实现。
     */
    var formatConfig: FormatConfig = FormatConfig()

    data class FormatConfig(
        val thousandsSign: String? = null,
        val decimalFoldThreshold: Int? = null,
        val formatDate: ((timestamp: Long, template: String) -> String)? = null,
        val formatBigNumber: ((value: Double) -> String)? = null,
    )

    /**
     * 0.4.0 多轴渲染（CH-053~056 消费端）：自定义 Y 轴（id→reverse）。
     * 门面 createYAxis/removeYAxis/overrideYAxis 同步；仅 paneId=main 的轴进入渲染。
     * 绑定该轴的指标（IndicatorInstance.yAxisId）用独立值域映射并获独立标签列。
     */
    var extraYAxisMeta by androidx.compose.runtime.mutableStateOf(emptyMap<String, ExtraAxisMeta>())

    /** 0.5.x 多轴完整版：自定义轴渲染元数据（position/inside 消费标签列位置）。 */
    data class ExtraAxisMeta(
        val reverse: Boolean = false,
        /** 上游 inside：标签画 pane 内右缘（右对齐）而非独立 gutter 列。 */
        val inside: Boolean = false,
        /** 上游 position LEFT：标签画 pane 内左缘（right 轴位置与独立列等价，登记）。 */
        val positionLeft: Boolean = false,
    )

    /** 0.5.x 多轴完整版：自定义变换轴（axisId → CustomYAxis；渲染按 Custom spec 映射）。 */
    var extraYAxisCustom by androidx.compose.runtime.mutableStateOf(emptyMap<String, io.lkanvas.core.axis.CustomYAxis>())

    /** R06：轴实例 spec 表（axisId → 模板构造的 spec；custom 之外的实际投影来源）。 */
    var extraYAxisSpec by androidx.compose.runtime.mutableStateOf(emptyMap<String, io.lkanvas.core.axis.YAxisSpec>())

    /** R06 余项消费：overrideXAxis(scrollZoomEnabled) 门禁轴区缩放（门面 overrideXAxis 同步）。 */
    var xAxisScrollZoomEnabled: Boolean = true

    /**
     * 自定义轴 [axisId] 的独立值域：绑定该轴的全部指标在可见区 [from,to] 的
     * 值 min/max（NaN 跳过）。无绑定/无有效值返回 null（渲染跳过该轴）。
     */
    fun extraAxisRange(axisId: String, from: Int, to: Int): Pair<Double, Double>? {
        var lo = Double.NaN
        var hi = Double.NaN
        for (inst in indicatorInstances) {
            if (inst.yAxisId != axisId) continue
            val res = inst.state ?: continue
            for (key in res.result.keys) {
                val col = res.result[key] ?: continue
                for (i in from..to) {
                    val v = col[i]
                    if (v.isNaN()) continue
                    if (lo.isNaN() || v < lo) lo = v
                    if (hi.isNaN() || v > hi) hi = v
                }
            }
        }
        return if (lo.isNaN()) null else lo to hi
    }

    /** 生效时区（非法名回退系统时区；JVM getTimeZone 对无效 ID 返回 GMT，需显式校验）。 */
    fun effectiveTimeZone(): java.util.TimeZone {
        if (timezoneId.isBlank()) return java.util.TimeZone.getDefault()
        val tz = java.util.TimeZone.getTimeZone(timezoneId)
        val invalid = tz.id == "GMT" && timezoneId != "GMT" &&
            !java.util.TimeZone.getAvailableIDs().contains(timezoneId)
        return if (invalid) java.util.TimeZone.getDefault() else tz
    }

    /** 渲染端记录的 feature 图标命中区（paneId, featureId, rect）；每次重绘重置。 */
    var tooltipFeatureHits: List<TooltipFeatureHit> = emptyList()

    data class TooltipFeatureHit(val paneId: String, val featureId: String, val rect: androidx.compose.ui.geometry.Rect)

    /** 手势端查询：命中 feature 返回 (paneId, featureId)，未命中 null。 */
    fun tooltipFeatureAt(x: Float, y: Float): Pair<String, String>? =
        tooltipFeatureHits.firstOrNull { it.rect.contains(androidx.compose.ui.geometry.Offset(x, y)) }
            ?.let { it.paneId to it.featureId }

    private var lastBarSpacing = 0.0

    /** 平移（px；手势层调用）。 */
    fun panBy(deltaX: Float) {
        if (!scrollEnabled) return // SLA-011：开关在共用执行入口检查
        viewport.scrollBy(deltaX.toDouble(), dataSnapshot.candles.size)
        viewRevision++
        emitRangeChange()
        onScrolled?.invoke(deltaX.toDouble())
    }

    /** 以触点为锚缩放。 */
    fun zoomAt(scale: Float, anchorX: Float) {
        if (!zoomEnabled) return // SLA-011：开关在共用执行入口检查
        val prev = viewport.barSpacing
        viewport.zoomBy(scale.toDouble(), anchorX.toDouble(), dataSnapshot.candles.size)
        viewRevision++
        emitRangeChange()
        val realScale = viewport.barSpacing / prev
        if (realScale != 1.0) onZoomed?.invoke(realScale)
    }

    private fun emitRangeChange() {
        val from = kotlin.math.floor(viewport.scrollIndex).toInt().coerceAtLeast(0)
        // 上游 _adjustVisibleRange：to 超出数据量时钳制（SLA-005 边界判定与
        // getVisibleRange 同口径）
        val to = kotlin.math.ceil(viewport.scrollIndex + viewport.visibleCount).toInt()
            .coerceAtMost(dataSnapshot.candles.size)
        onVisibleRangeChanged?.invoke(from, to, viewport.scrollIndex, viewport.scrollIndex + viewport.visibleCount)
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
        onCrosshairChanged?.invoke(
            vp.candleCenterX(idx.toDouble()),
            y.toDouble(),
            idx,
            candles[idx].timestamp,
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
    /**
     * 平台 fling 曲线（T-34 余量收口）：KLineChart 注入平台 [android.widget.OverScroller]
     * （RecyclerView/ScrollView 同款 native spline 曲线，密度/摩擦系统级一致）；
     * null 回退旧指数衰减（JVM 测试可用）。
     */
    var flingScroller: android.widget.OverScroller? = null

    fun fling(velocityX: Float) {
        if (kotlin.math.abs(velocityX) < 200f) return
        flingJob?.cancel()
        val scroller = flingScroller
        if (scroller != null) {
            // 平台 spline fling：逐帧 computeScrollOffset 取位移增量（与 View 体系同源）
            scroller.fling(
                0, 0,
                velocityX.toInt(), 0,
                Int.MIN_VALUE, Int.MAX_VALUE,
                0, 0,
            )
            var lastX = 0
            flingJob = scope.launch {
                while (!scroller.isFinished) {
                    scroller.computeScrollOffset()
                    val x = scroller.currX
                    panBy((x - lastX).toFloat())
                    lastX = x
                    kotlinx.coroutines.delay(16L)
                }
            }
        } else {
            flingJob = scope.launch {
                var velocity = velocityX
                while (kotlin.math.abs(velocity) > 60f) {
                    panBy(velocity * 16f / 1000f)
                    velocity *= 0.94f
                    kotlinx.coroutines.delay(16L)
                }
            }
        }
    }

    /** 布局宽度（px），由 composable 的 onSizeChanged 回填。 */
    var contentWidthPx: Float = 0f
        private set

    /** 可见 pane 高度（px），由 composable 布局后回填。 */
    var paneHeightsPx: PaneHeights = PaneHeights(0f, 0f, 0f, 0f)
        private set

    /**
     * 0.5.x 动态副图 pane（Alpha）：createIndicator 的 paneId 非内置
     * （main/vol/macd）时自动创建；pane 内全部指标删除后自动移除。
     * 渲染按 [extraPaneLayout] 逐 pane 画绑定指标的线列。
     */
    val extraPanes = androidx.compose.runtime.mutableStateOf(emptyList<String>())

    /** 自定义副图布局（paneId → (topPx, bottomPx)，relayout 计算）。 */
    var extraPaneLayout by androidx.compose.runtime.mutableStateOf(emptyList<Pair<String, Pair<Float, Float>>>())
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
            // R03（复审 2026-10-30）：镜像上游 _addData 尾部 adjustFlag →
            // _adjustVisibleRange——数据**实际提交**并调整视口后统一发出范围
            // 变化，经 onVisibleRangeChanged → Store.adjustVisibleRangeBoundary
            // 形成自动续拉链（loading/more 门禁防重入；INIT 1 根+more=true
            // 无手势也会填满窗口）
            emitRangeChange()
            notifyArchiveDirty(hasContent = snap.candles.isNotEmpty() || overlays.all().isNotEmpty())
        }
        scope.launch { engine.run() }
    }

    private fun adjustViewport(change: AppliedChange, prevCount: Int) {
        when (change.op) {
            // Reset（init/切会话）：回到最新（上游 init 定位语义；layout 的
            // wasAtLatest 启发式对"旧 scrollIndex + 新数据量"不成立，不能依赖）
            io.lkanvas.core.store.DataOp.RESET -> viewport.onReset(dataSnapshot.candles.size)
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
        for (inst in indicatorInstances.toList()) {
            if (inst.visible) {
                diagnostics.timeCompute(snap.sessionId, snap.revision, { inst.id }) {
                    IndicatorEngine.computeInto(inst, snap.candles, kind)
                }
            }
        }
        maResult = resultOfName("MA")
        volResult = resultOfName("VOL")
        macdResult = resultOfName("MACD")
        indicatorResultsRevision++
    }

    /** 第一个可见的同名模板实例结果（缺省实例取消后由宿主创建的实例顶替）。 */
    private fun resultOfName(name: String): IndicatorResultState? =
        indicatorInstances.firstOrNull { it.visible && it.definition.name == name }?.state

    /** 用户实例结果版本（渲染层读取触发叠加重绘）。 */
    var indicatorResultsRevision by mutableIntStateOf(0)
        private set

    /**
     * 主 pane 叠加的指标实例结果（SLA-003：paneId=="main" 的**全部**可见实例，
     * 不再按 series 过滤——上游允许任意指标叠加 candle pane）。
     */
    fun mainPaneIndicatorResults(): List<IndicatorInstance> =
        indicatorInstances.filter {
            it.visible && it.paneId == "main" && it.state != null
        }

    /** VOL pane 实例结果（严格 paneId=="vol"；R07 删按名回退——避免重复归属）。 */
    fun volumePaneResult(): IndicatorResultState? =
        indicatorInstances.firstOrNull {
            it.visible && it.paneId == "vol" &&
                it.definition.series == io.lkanvas.core.indicator.IndicatorSeries.VOLUME && it.state != null
        }?.state

    /** MACD pane 实例结果（严格 paneId=="macd"；R07 删按名回退）。 */
    fun macdPaneResult(): IndicatorResultState? =
        indicatorInstances.firstOrNull {
            it.visible && it.paneId == "macd" && it.state != null
        }?.state

    // ---------- 布局回填 ----------

    /** 容器与子 pane 尺寸状态（分隔条拖动调整，T-23/A-33）。 */
    private var containerHeightPx = 0f
    private var timeAxisHeightPx = 0f
    private var defaultSubPanePx = 0f
    private var volPaneOverride = 0f
    private var macdPaneOverride = 0f

    /** 最小 pane 高度（避免绘图区为负，PRD R-06）。 */
    private val minSubPanePx = 40f
    private val minMainPanePx = 60f

    fun onLayout(widthPx: Float, panes: PaneHeights) {
        contentWidthPx = widthPx
        containerHeightPx = panes.main + panes.vol + panes.macd + panes.timeAxis +
            extraPanes.value.size * defaultSubPanePx
        timeAxisHeightPx = panes.timeAxis
        if (defaultSubPanePx == 0f) {
            defaultSubPanePx = panes.vol
            volPaneOverride = panes.vol
            macdPaneOverride = panes.macd
        }
        relayout()
    }

    /** 0.5.x 动态副图：每 pane 高度覆写（拖动分隔条写入；px）。 */
    private val extraPaneHeightOverride = HashMap<String, Float>()

    /**
     * R07（复审 2026-10-30）：pane 集合**单一来源**——main 恒在；vol/macd 仅当
     * 有可见实例绑定（paneId=="vol"/"macd"）时存在；extras 随建删。
     * 查询（getPaneOptions）、布局（relayout）、覆写（setPaneOptions/override）、
     * 渲染（ChartRenderer 跳过零高 pane）与删除全部消费本函数。
     */
    fun paneList(): List<String> {
        val boundPanes = indicatorInstances
            .filter { it.visible && it.paneId in setOf("vol", "macd") }
            .map { it.paneId }
            .toSet()
        return buildList {
            add("main")
            if ("vol" in boundPanes) add("vol")
            if ("macd" in boundPanes) add("macd")
            addAll(extraPanes.value)
        }
    }

    /** 内置副图 pane 是否在 pane 集合中。 */
    fun hasBuiltinPane(paneId: String): Boolean = paneId in paneList()

    private fun relayout() {
        // 0.5.x 动态 pane：自定义副图排在内置 VOL/MACD 之后、时间轴之前，
        // 高度 = 覆写或默认副图高（main 相应压缩，保底规则不变）
        // R07：vol/macd 不在 pane 集合时高度为 0（渲染端跳过、分隔条命中退化无效）
        var extraH = 0f
        for (pane in extraPanes.value) extraH += (extraPaneHeightOverride[pane] ?: defaultSubPanePx)
        val usable = (containerHeightPx - timeAxisHeightPx - extraH).coerceAtLeast(minSubPanePx * 2)
        // 主 pane 保底 35% 可用高（A-41：横屏/大字体下关键绘图区不被挤没）
        val mainMin = (usable * 0.35f).coerceAtLeast(minMainPanePx)
        val maxSub = ((usable - mainMin) / 2f).coerceAtLeast(minSubPanePx)
        val hasVol = hasBuiltinPane("vol")
        val hasMacd = hasBuiltinPane("macd")
        val vol = if (hasVol) volPaneOverride.coerceIn(minSubPanePx, maxSub) else 0f
        val macd = if (hasMacd) macdPaneOverride.coerceIn(minSubPanePx, maxSub) else 0f
        // R07：mainPaneOverride（setPaneOptions("main") 直接设定；null=派生）
        val main = mainPaneOverride
            ?.coerceIn(minMainPanePx, usable)
            ?: (usable - vol - macd).coerceAtLeast(minMainPanePx)
        paneHeightsPx = PaneHeights(main, vol, macd, timeAxisHeightPx)
        // 自定义副图布局（top/bottom 从 macd 底开始顺序排布）
        var cursor = main + vol + macd
        extraPaneLayout = extraPanes.value.map { pane ->
            val top = cursor + 1f
            val bottom = top + (extraPaneHeightOverride[pane] ?: defaultSubPanePx)
            cursor = bottom
            pane to (top to bottom)
        }
        viewport.layout(contentWidthPx.toDouble(), dataSnapshot.candles.size)
        viewRevision++
    }

    /** 动态副图变更后重新布局（门面调用；relayout 的公开入口）。 */
    fun relayoutExtraPanes() = relayout()

    /**
     * 0.5.x pane 排序：把 [paneId] 移动到 [targetIndex]（列表序即渲染序，
     * 从上到下）。越界钳制到有效范围；未知 paneId 忽略。
     */
    fun moveExtraPane(paneId: String, targetIndex: Int) {
        val current = extraPanes.value
        val from = current.indexOf(paneId)
        if (from < 0) return
        val to = targetIndex.coerceIn(0, current.size - 1)
        if (from == to) return
        val mutable = current.toMutableList()
        mutable.removeAt(from)
        mutable.add(to, paneId)
        extraPanes.value = mutable
        relayout()
    }

    /**
     * 分隔条命中（A-33：手势层判定）；返回 0=主|VOL 分隔、1=VOL|MACD 分隔、null=未命中。
     * @param tolerancePx 判定半径
     */
    fun paneSeparatorAt(y: Float, tolerancePx: Float): Int? {
        val p = paneHeightsPx
        return when {
            kotlin.math.abs(y - p.main) <= tolerancePx -> 0
            kotlin.math.abs(y - (p.main + p.vol)) <= tolerancePx -> 1
            else -> null
        }
    }

    /** 绝对值设置 pane 高度（setPaneOptions 门面入口）：同时接受 VOL/MACD 目标值。 */
    fun adjustPaneByAbsolute(id: String, vol: Float, macd: Float) {
        if (id == "vol") volPaneOverride = vol else macdPaneOverride = macd
        relayout()
    }

    /** 同时设置 VOL/MACD 绝对高度（main = 剩余）。 */
    fun setSubPaneHeights(vol: Float, macd: Float) {
        volPaneOverride = vol
        macdPaneOverride = macd
        relayout()
    }

    /**
     * 0.5.x 动态副图分隔命中：返回命中下边界分隔线的 paneId（拖动该 pane 高度），
     * 未命中 null。在 [paneSeparatorAt] 之后调用（内置分隔优先）。
     */
    fun extraPaneSeparatorAt(y: Float, tolerancePx: Float): String? =
        extraPaneLayout.firstOrNull { kotlin.math.abs(y - it.second.second) <= tolerancePx }?.first

    /** 拖动动态副图分隔条：调整 [paneId] 高度（[startValue] 为拖动起始高度，dy 累计）。 */
    fun adjustExtraPane(paneId: String, startValue: Float, dyTotal: Float) {
        extraPaneHeightOverride[paneId] = (startValue + dyTotal).coerceAtLeast(minSubPanePx)
        relayout()
    }

    /** R07：setPaneOptions 路由——直接设置动态 pane 高度。 */
    fun setExtraPaneHeight(paneId: String, heightPx: Float) {
        extraPaneHeightOverride[paneId] = heightPx.coerceAtLeast(minSubPanePx)
        relayout()
    }

    /** R07：setPaneOptions("main") 的直接高度设定（null=按剩余空间派生）。 */
    internal var mainPaneOverride: Float? = null

    /** 拖动分隔条：[which]=0 调整 VOL pane 高度，1 调整 MACD pane 高度（dy 累计）。 */
    fun adjustPane(which: Int, startValue: Float, dyTotal: Float) {
        if (which == 0) {
            volPaneOverride = startValue + dyTotal
        } else {
            macdPaneOverride = startValue + dyTotal
        }
        relayout()
        onPaneDragged?.invoke(if (which == 0) "main" else "vol")
    }

    /** 轴区拖动缩放（右 gutter 垂直拖动；dy 累计，向下放大间距）。 */
    fun axisZoomBy(dyTotal: Float) {
        // R06 余项消费：overrideXAxis(scrollZoomEnabled=false) 门禁轴区缩放
        // （x 轴覆写的其余字段 reverse/gap 等为平台差异登记，structure-map 台账承载）
        if (!xAxisScrollZoomEnabled) return
        val scale = 1.0 + dyTotal / 200.0
        viewport.zoomBy(scale, viewport.candleCenterX((dataSnapshot.candles.size - 1).coerceAtLeast(0).toDouble()), dataSnapshot.candles.size)
        viewRevision++
    }

    /** 是否命中右轴 gutter 区（x > 内容右缘）。 */
    fun isInAxisGutter(x: Float): Boolean = contentWidthPx > 0f && x > contentWidthPx

    fun notifyViewportChanged() {
        viewRevision++
        // SLA-005：视口调整末尾统一发出范围变化（上游 _adjustVisibleRange 末尾
        // 的边界分页判定依赖该事件；手势路径 panBy/zoomAt 已各自发出）
        emitRangeChange()
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
enum class GestureOwner { NONE, PAN, PINCH, CROSSHAIR, OVERLAY_EDIT, PANE_ADJUST, AXIS_ZOOM }
