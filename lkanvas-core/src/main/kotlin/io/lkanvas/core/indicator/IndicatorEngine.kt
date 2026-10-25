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

/** 图形定义：每列的展示类型（PRD R-08 plots）。 */
data class PlotDef(
    val key: String,
    val title: String,
    val type: PlotType,
    /** bar 基准值（如 MACD 柱以 0 为基线） */
    val baseValue: Double? = null,
)

enum class PlotType { LINE, BAR }

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

    /** 输出列定义。 */
    val plots: List<PlotDef>

    /** 增量能力声明（能力清单记录，PRD R-08）。 */
    val incremental: IncrementalCapability get() = IncrementalCapability.FULL_ONLY

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
 * 指标实例（PRD 4.1）：携带参数、独立计算状态；实例之间不共享可变状态。
 */
class IndicatorInstance(
    val id: String,
    val definition: IndicatorDefinition,
    params: List<Double> = definition.calcParams,
) {
    var params: List<Double> = params
        private set

    var visible: Boolean = true

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

    /** 全量计算（fold 模型，公式与上游逐位对照由 fixtures 保证）。 */
    fun recompute(definition: IndicatorDefinition, params: List<Double>, candles: List<Candle>): IndicatorResultState {
        val plots = definition.plots
        val columns = HashMap<String, DoubleArray>(plots.size * 2)
        for (p in plots) columns[p.key] = DoubleArray(candles.size) { Double.NaN }
        var scratch: Any? = definition.initScratch(params)
        var scratchPrev: Any? = null
        for (i in candles.indices) {
            scratchPrev = scratch
            scratch = definition.stepScratch(scratch, candles, params, i)
            val row = definition.rowFromScratch(scratch, candles, params, i)
            for ((k, v) in row) columns.getValue(k)[i] = v
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
