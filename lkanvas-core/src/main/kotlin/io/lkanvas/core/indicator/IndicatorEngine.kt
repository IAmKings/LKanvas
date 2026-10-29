package io.lkanvas.core.indicator

import io.lkanvas.core.data.Candle

/**
 * 指标输出：列存 DoubleArray，长度与 candle 数一致。
 *
 * **Missing 约定（PRD R-08）**：NaN 表示未预热/字段缺失/无定义；
 * 绝不与有效数值 0 混淆（差异 D-03），也绝不输出 Infinity（公式必须自行防护）。
 */
class IndicatorResult(val columns: Map<String, DoubleArray>) {
    operator fun get(key: String): DoubleArray = columns.getValue(key)
    val keys: Set<String> get() = columns.keys

    /** 行视图（图例/十字线用）；缺失列为 NaN。 */
    fun row(index: Int): Map<String, Double> = columns.mapValues { it.value[index] }

    fun size(): Int = columns.values.firstOrNull()?.size ?: 0
}

/**
 * 图形定义：每列的展示类型（PRD R-08 plots；上游 figure {key,title,type,baseValue,styles,attrs}）。
 * styles/attrs 为行级回调建模位（Alpha 接口位，绘制端消费随批次 5+）。
 */
data class PlotDef(
    val key: String,
    val title: String,
    val type: PlotType,
    /** bar 基准值（如 MACD 柱以 0 为基线） */
    val baseValue: Double? = null,
    /** 上游 figure.styles：(row, params) -> 样式覆盖（宿主自定义结构）。 */
    val styles: Any? = null,
    /** 上游 figure.attrs：(row, params) -> 附加属性。 */
    val attrs: Any? = null,
)

enum class PlotType { LINE, BAR }

/** 指标序列归属（上游 series 语义）：主图/成交量图/独立副图。 */
enum class IndicatorSeries { PRICE, VOLUME, NORMAL }

/** 数据变更类型（增量推进的输入）。 */
sealed interface ChangeKind {
    data object Reset : ChangeKind
    data object ReplaceLatest : ChangeKind
    data class Append(val count: Int) : ChangeKind
    data class Prepend(val count: Int) : ChangeKind
    data object Patch : ChangeKind
    data object ParamsChanged : ChangeKind
}

/**
 * 指标模板定义（PRD 4.1 IndicatorDefinition）。
 *
 * 计算模型：指标以「滚动状态机」表达——
 * - [initScratch] 给出第 -1 根（空数据）后的初始私有状态；
 * - [stepScratch] 从第 i-1 根状态推进到第 i 根状态；
 * - [rowFromScratch] 由第 i 根状态产出该行输出列（缺失输出 NaN）。
 *
 * 全量计算 = 逐步 fold + 逐行取值；因此**全量与增量共享同一条公式路径**，
 * 满足「每个模板都必须有正确全量路径」且天然保证一致。
 * 末根替换 = 从保存的 n-1 根状态重新 step 一次（PRD R-08：递推指标替换末根
 * 必须从上一根已确定状态重新求值，不得双重递推）。
 * 不适合该模型的复杂模板可返回 [IncrementalCapability] 声明只支持全量。
 */
interface IndicatorDefinition {

    /** 模板名（注册键；同名注册冲突，PRD R-14）。 */
    val name: String

    val shortName: String

    /** 默认参数。 */
    val calcParams: List<Double>

    /** 显示精度（位数）。 */
    val precision: Int

    /** 输出列定义（默认参数下的；实际以 [plotsFor] 为准）。 */
    val plots: List<PlotDef>

    /** 按实例参数生成输出列（如 MA 的 ma1..maN 随参数个数变化）。 */
    fun plotsFor(params: List<Double>): List<PlotDef> = plots

    /** 序列归属（pane 放置策略，PRD R-08）。 */
    val series: IndicatorSeries get() = IndicatorSeries.NORMAL

    /** 增量能力声明（能力清单记录，PRD R-08）。 */
    val incremental: IncrementalCapability get() = IncrementalCapability.FULL_ONLY

    // ---- 上游 Indicator 模板字段对齐（批次 3；默认值与上游 undefined 语义一致） ----

    /** 上游 minValue：固定轴下界（如 VOL=0）；null=自由。 */
    val minValue: Double? get() = null

    /** 上游 maxValue：固定轴上界；null=自由。 */
    val maxValue: Double? get() = null

    /** 上游 shouldOhlc：figures 使用 OHLC 数据源（主图叠加类）。 */
    val shouldOhlc: Boolean get() = false

    /** 上游 shouldFormatBigNumber：数值显示万/亿缩写（如 VOL）。 */
    val shouldFormatBigNumber: Boolean get() = false

    /** 上游 visible：模板默认可见性。 */
    val visible: Boolean get() = true

    /** 上游 zLevel：绘制层级（默认 0）。 */
    val zLevel: Int get() = 0

    /** 上游 extendData：模板扩展数据（宿主自定义）。 */
    val extendData: Any? get() = null

    /** 上游 shouldUpdate：增量是否推进（默认每次已接受变更推进，PRD R-08 禁止“仅收盘更新”规避）。 */
    val shouldUpdate: Boolean get() = true

    /**
     * 上游 createTooltipDataSource：自定义 tooltip 数据源回调。
     * 签名：(dataList, crosshairDataIndex) -> Map<key, displayValue>；
     * null=使用 figures 默认数据（绘制端消费）。
     */
    val createTooltipDataSource: (suspend (List<io.lkanvas.core.data.Candle>, Int) -> Map<String, String>)? get() = null

    /**
     * 上游 draw：完全自定义绘制回调（绕过 figures 渲染管线）。
     * 上下文 [IndicatorDrawContext] 的 scope 由宿主层注入（compose 端为 DrawScope）；
     * palette 为宿主层颜色对象列表（compose 端为 Color）。
     */
    val draw: ((IndicatorDrawContext) -> Unit)? get() = null

    /**
     * 上游模板 `calc(dataList, indicator)` 的规范全量入口（SLA-002 结构对齐核心）：
     * 逐根产出输出行（figure key → 值；未预热/缺失**省略键**，即 NaN），
     * 返回列表长度必须等于 [candles].size。
     *
     * 引擎全量路径（[IndicatorEngine.recompute]）执行本函数——上游公式补丁
     * 可按行直接移植；输出键顺序以 [plotsFor]`(params)` 为准。
     *
     * 默认实现 = Scratch 折叠（宿主自定义模板未覆写 calc 时仍可用）；
     * 内置 27 模板逐一覆写为上游镜像，calc ≡ Scratch 折叠的一致性由
     * DualPathParityTest 在全部 27 模板上验证（SLA-002 验收）。
     */
    fun calc(candles: List<Candle>, params: List<Double>): List<Map<String, Double>> {
        var scratch = initScratch(params)
        val rows = ArrayList<Map<String, Double>>(candles.size)
        for (i in candles.indices) {
            scratch = stepScratch(scratch, candles, params, i)
            rows.add(rowFromScratch(scratch, candles, params, i))
        }
        return rows
    }

    /** 空数据的初始私有状态。 */
    fun initScratch(params: List<Double>): Any?

    /**
     * 从第 i-1 根状态推进到第 i 根（i 为 0-based 数据索引）。
     * 返回新状态（不可复用传入对象，避免共享可变状态）。
     */
    fun stepScratch(prev: Any?, candles: List<Candle>, params: List<Double>, i: Int): Any?

    /** 第 i 根（状态为推进到 i 后）的输出行；无定义输出 NaN。 */
    fun rowFromScratch(scratch: Any?, candles: List<Candle>, params: List<Double>, i: Int): Map<String, Double>
}

/** 增量能力（能力清单，PRD R-08：是否增量逐模板记录）。 */
enum class IncrementalCapability {
    /** 仅全量（Prepend/Patch/参数变化等场景一律全量）。 */
    FULL_ONLY,

    /** 支持末根替换与追加的 O(参数) 增量；Prepend/Patch 回退全量。 */
    REPLACE_APPEND,
}

/**
 * draw 钩子绘制上下文（0.3.0 批次 8c）：core 不依赖 Compose，
 * scope/palette 由宿主层注入（compose 端 scope=DrawScope、palette=Color）。
 */
class IndicatorDrawContext(
    val result: IndicatorResult,
    val rangeFirst: Int,
    val rangeLast: Int,
    /** 数据值 → y 像素映射。 */
    val yOf: (Double) -> Double,
    /** 数据索引 → x 像素映射。 */
    val xOf: (Int) -> Double,
    /** 宿主层颜色调色板（compose 端 Color）。 */
    val palette: List<Any?>,
    /** 宿主绘制作用域（compose 端 DrawScope）。 */
    val scope: Any?,
)

/**
 * 指标实例（PRD 4.1）：携带参数、独立计算状态；实例之间不共享可变状态。
 * paneId/yAxisId/styles 与 shortName/precision/zLevel/extendData 支持实例级
 * 覆盖（上游 overrideIndicator 字段面，SLA-007）。
 */
class IndicatorInstance(
    val id: String,
    val definition: IndicatorDefinition,
    params: List<Double> = definition.calcParams,
    /** 所属 pane（上游 Indicator.paneId；创建时由门面解析，缺省为新指标 pane）。 */
    var paneId: String = "main",
    /** 绑定 Y 轴（上游 Indicator.yAxisId；默认主轴 main，依赖多轴 CH-053~057）。 */
    var yAxisId: String = "main",
    /** 上游 Indicator.styles：实例级样式覆盖（宿主自定义结构，绘制端消费）。 */
    var styles: Any? = null,
) {
    var params: List<Double> = params
        private set

    var visible: Boolean = definition.visible

    /** 上游 Indicator.shortName 实例级覆盖（null=用模板默认；图例/tooltip 消费）。 */
    var shortNameOverride: String? = null

    /** 上游 Indicator.precision 实例级覆盖（null=用模板默认）。 */
    var precisionOverride: Int? = null

    /** 上游 Indicator.zLevel 实例级覆盖（null=用模板默认）。 */
    var zLevelOverride: Int? = null

    /** 上游 Indicator.extendData：实例级扩展数据（宿主自定义）。 */
    val extendData: LinkedHashMap<String, Any?> = LinkedHashMap()

    /** 显示名（上游 displayName 语义：shortName 覆盖优先）。 */
    fun displayName(): String = shortNameOverride ?: definition.shortName

    /** 最近一次计算结果（版本由引擎外部的门禁管理）。 */
    var state: IndicatorResultState? = null
        internal set

    /** 实例错误状态：抛错模板置错、保留图表可操作（PRD R-08）。 */
    var error: Throwable? = null
        internal set

    val status: InstanceStatus
        get() = when {
            error != null -> InstanceStatus.ERROR
            state == null -> InstanceStatus.PENDING
            else -> InstanceStatus.READY
        }

    fun updateParams(newParams: List<Double>) {
        params = newParams
        state = null // 参数变化使缓存失效（PRD R-08）
    }
}

enum class InstanceStatus { PENDING, READY, ERROR }

/** 引擎侧的实例计算状态。 */
class IndicatorResultState(
    val definitionName: String,
    val params: List<Double>,
    val result: IndicatorResult,
    /** 已计算根数。 */
    val computedCount: Int,
    /** 第 n-1 根（确定前缀末尾）后的私有状态；末根替换从此重算。 */
    val scratchPrev: Any?,
    /** 第 n 根后的私有状态。 */
    val scratchCur: Any?,
    /** 本次结果产生方式（诊断/工作量证明）。 */
    val mode: ComputeMode,
)

enum class ComputeMode { FULL, INCREMENTAL_REPLACE, INCREMENTAL_APPEND, FULL_FALLBACK }

/**
 * 指标引擎：全量与增量的统一入口。
 *
 * 一致性契约（PRD A-24）：对任意变更序列，[advance] 的增量结果必须与
 * [recompute] 的全量结果逐位一致（NaN 位置一致）；不一致视为 S0 缺陷。
 */
object IndicatorEngine {

    /**
     * 全量计算：输出列由规范入口 [IndicatorDefinition.calc] 产出（上游模板
     * calc 镜像，公式补丁可直接移植）；Scratch 折叠仅为增量推进建立边界状态
     * （SLA-002：calc ≡ Scratch 双路径互验由 DualPathParityTest 保证）。
     */
    fun recompute(definition: IndicatorDefinition, params: List<Double>, candles: List<Candle>): IndicatorResultState {
        // PRD R-08：实际输出列以 plotsFor(params) 为准（实例参数个数可与默认不同；
        // 用 definition.plots 会在自定义参数实例上留下永久 NaN 假列）
        val plots = definition.plotsFor(params)
        val columns = HashMap<String, DoubleArray>(plots.size * 2)
        for (p in plots) columns[p.key] = DoubleArray(candles.size) { Double.NaN }
        val rows = definition.calc(candles, params)
        require(rows.size == candles.size) {
            "calc 行数(${rows.size}) 必须等于数据根数(${candles.size})：${definition.name}"
        }
        for ((i, row) in rows.withIndex()) {
            for ((k, v) in row) columns.getValue(k)[i] = v
        }
        // Scratch 边界状态（增量 advance 的折叠起点）
        var scratch: Any? = definition.initScratch(params)
        var scratchPrev: Any? = null
        for (i in candles.indices) {
            scratchPrev = scratch
            scratch = definition.stepScratch(scratch, candles, params, i)
        }
        return IndicatorResultState(
            definitionName = definition.name,
            params = params.toList(),
            result = IndicatorResult(columns),
            computedCount = candles.size,
            scratchPrev = scratchPrev,
            scratchCur = scratch,
            mode = ComputeMode.FULL,
        )
    }

    /**
     * 增量推进。前置条件：[state] 的 computedCount 与变更类型匹配；否则回退全量。
     * Prepend/Patch/参数变化总是回退全量（递推指标后缀失效，PRD R-08）。
     */
    fun advance(
        definition: IndicatorDefinition,
        state: IndicatorResultState,
        candles: List<Candle>,
        change: ChangeKind,
    ): IndicatorResultState {
        val canIncremental = definition.incremental == IncrementalCapability.REPLACE_APPEND
        return when {
            !canIncremental -> fullFallback(definition, state.params, candles)
            change is ChangeKind.ReplaceLatest && candles.size == state.computedCount ->
                advanceReplace(definition, state, candles)
            change is ChangeKind.Append && candles.size == state.computedCount + change.count ->
                advanceAppend(definition, state, candles, change.count)
            else -> fullFallback(definition, state.params, candles)
        }
    }

    private fun fullFallback(
        definition: IndicatorDefinition,
        params: List<Double>,
        candles: List<Candle>,
    ): IndicatorResultState {
        val full = recompute(definition, params, candles)
        return IndicatorResultState(
            full.definitionName, full.params, full.result, full.computedCount,
            full.scratchPrev, full.scratchCur, ComputeMode.FULL_FALLBACK,
        )
    }

    /** 拷贝列并保证新区域为 NaN（Missing 语义不被 0 污染）。 */
    private fun copyColumns(state: IndicatorResultState, newSize: Int): HashMap<String, DoubleArray> {
        val out = HashMap<String, DoubleArray>(state.result.columns.size * 2)
        for ((k, arr) in state.result.columns) {
            val copy = DoubleArray(newSize) { Double.NaN }
            arr.copyInto(copy, 0, 0, minOf(arr.size, newSize))
            out[k] = copy
        }
        return out
    }

    private fun writeRow(
        columns: Map<String, DoubleArray>,
        row: Map<String, Double>,
        index: Int,
    ) {
        // 先全部置 NaN：row 中缺失的键必须表达 Missing（预热等）
        for (arr in columns.values) arr[index] = Double.NaN
        for ((k, v) in row) columns.getValue(k)[index] = v
    }

    private fun advanceReplace(
        definition: IndicatorDefinition,
        state: IndicatorResultState,
        candles: List<Candle>,
    ): IndicatorResultState {
        val i = candles.size - 1
        val scratch = definition.stepScratch(state.scratchPrev, candles, state.params, i)
        val columns = copyColumns(state, candles.size)
        writeRow(columns, definition.rowFromScratch(scratch, candles, state.params, i), i)
        return IndicatorResultState(
            definitionName = state.definitionName,
            params = state.params,
            result = IndicatorResult(columns),
            computedCount = candles.size,
            scratchPrev = state.scratchPrev,
            scratchCur = scratch,
            mode = ComputeMode.INCREMENTAL_REPLACE,
        )
    }

    private fun advanceAppend(
        definition: IndicatorDefinition,
        state: IndicatorResultState,
        candles: List<Candle>,
        count: Int,
    ): IndicatorResultState {
        val columns = copyColumns(state, candles.size)
        var scratchPrev = state.scratchPrev
        var scratchCur = state.scratchCur
        for (offset in 0 until count) {
            val i = state.computedCount + offset
            scratchPrev = scratchCur
            scratchCur = definition.stepScratch(scratchCur, candles, state.params, i)
            writeRow(columns, definition.rowFromScratch(scratchCur, candles, state.params, i), i)
        }
        return IndicatorResultState(
            definitionName = state.definitionName,
            params = state.params,
            result = IndicatorResult(columns),
            computedCount = candles.size,
            scratchPrev = scratchPrev,
            scratchCur = scratchCur,
            mode = ComputeMode.INCREMENTAL_APPEND,
        )
    }

    /**
     * 实例级计算入口：异常被捕获并记入 [IndicatorInstance.error]（实例置 Error、
     * 其余实例不受影响，PRD R-08）；成功清空错误。
     */
    fun computeInto(
        instance: IndicatorInstance,
        candles: List<Candle>,
        change: ChangeKind,
    ): IndicatorResultState? = try {
        val prev = instance.state
        val next = if (prev == null) {
            recompute(instance.definition, instance.params, candles)
        } else {
            advance(instance.definition, prev, candles, change)
        }
        instance.error = null
        instance.state = next
        next
    } catch (t: Throwable) {
        instance.error = t
        instance.state = null
        null
    }
}

/**
 * 指标模板注册表：同名注册冲突必须显式替换（PRD R-14/A-42 前置）。
 * 实例级注册（默认给每个图表独立 registry，避免隐式跨图污染）。
 */
class IndicatorRegistry {
    private val defs = LinkedHashMap<String, IndicatorDefinition>()

    fun register(definition: IndicatorDefinition, replace: Boolean = false): Boolean {
        if (!replace && defs.containsKey(definition.name)) return false
        defs[definition.name] = definition
        return true
    }

    fun get(name: String): IndicatorDefinition? = defs[name]

    fun unregister(name: String): Boolean = defs.remove(name) != null

    fun all(): List<IndicatorDefinition> = defs.values.toList()
}
