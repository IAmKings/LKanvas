package io.lkanvas.core.axis

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/**
 * 纵轴规格（PRD R-06/T-16、T-17）。
 *
 * - [Normal]：线性（identity 变换）
 * - [Logarithm]：上游带符号对数 sign(v)·log10(1+|v|)（允许零/负/小正值）
 * - [Percentage]：以首可见 candle close 为基准的百分比轴；基准为零按 D-01
 *   （有限稳定像素范围 + 标签「—」，禁止 NaN/Infinity 与虚假涨幅）
 * - [Custom]：宿主自定义变换与刻度（带 A-18 保护）
 */
sealed interface YAxisSpec {
    data object Normal : YAxisSpec
    data object Logarithm : YAxisSpec
    data class Percentage(val base: Double?) : YAxisSpec
    data class Custom(val axis: CustomYAxis) : YAxisSpec
}

/** 宿主自定义轴契约（R-14 扩展点；实现必须无副作用且快速返回）。 */
interface CustomYAxis {
    fun valueToReal(value: Double): Double
    fun realToValue(real: Double): Double

    /** 自定义刻度（real 空间）；返回 null 使用默认 1/2/5。 */
    fun customTicks(realFrom: Double, realTo: Double): List<Double>? = null
}

/** 轴刻度（real 空间位置 + 显示值与文本）。 */
data class AxisTick(val real: Double, val display: Double?, val text: String)

/** 刻度集合与保护结果（A-18：非单调/超量刻度被降级并报告）。 */
data class AxisTicks(
    val ticks: List<AxisTick>,
    /** true = 自定义刻度被拒绝并回退默认。 */
    val degradedFromCustom: Boolean = false,
    val degradeReason: String? = null,
)

/**
 * 轴数学（纯函数）：统一 normal/logarithm/percentage/custom 的
 * 值↔real↔像素映射与刻度生成。全部 Double，Float 转换由绘制层最后一步完成。
 */
object YAxisMath {

    // ---------- 变换 ----------

    fun valueToReal(spec: YAxisSpec, value: Double): Double = when (spec) {
        is YAxisSpec.Normal -> value
        is YAxisSpec.Logarithm ->
            if (value < 0) -log10(1 - value) else log10(1 + value)
        is YAxisSpec.Percentage -> {
            val base = spec.base
            if (base == null || base == 0.0) 0.0 else (value - base) / base * 100
        }
        is YAxisSpec.Custom -> spec.axis.valueToReal(value)
    }

    fun realToValue(spec: YAxisSpec, real: Double): Double = when (spec) {
        is YAxisSpec.Normal -> real
        is YAxisSpec.Logarithm ->
            if (real < 0) 1 - 10.0.pow(-real) else 10.0.pow(real) - 1
        is YAxisSpec.Percentage -> {
            val base = spec.base
            if (base == null || base == 0.0) 0.0 else base * (1 + real / 100)
        }
        is YAxisSpec.Custom -> spec.axis.realToValue(real)
    }

    /** 值域 [from, to] 映射到的 real 区间（净化后有限）。 */
    fun realRange(spec: YAxisSpec, from: Double, to: Double): Pair<Double, Double> {
        val (lo, hi) = LinearYAxis.sanitizeRange(from, to)
        val rf = valueToReal(spec, lo)
        val rt = valueToReal(spec, hi)
        // 变换产生非有限值时回退 identity，保证像素范围始终有限
        if (!rf.isFinite() || !rt.isFinite()) return lo to hi
        return if (rf <= rt) rf to rt else rt to rf
    }

    // ---------- 像素映射 ----------

    fun valueToY(spec: YAxisSpec, value: Double, from: Double, to: Double, topY: Double, bottomY: Double): Double {
        val (rf, rt) = realRange(spec, from, to)
        return LinearYAxis.valueToY(valueToReal(spec, value), rf, rt, topY, bottomY)
    }

    fun yToValue(spec: YAxisSpec, y: Double, from: Double, to: Double, topY: Double, bottomY: Double): Double {
        val (rf, rt) = realRange(spec, from, to)
        return realToValue(spec, LinearYAxis.yToValue(y, rf, rt, topY, bottomY))
    }

    // ---------- 刻度 ----------

    /**
     * 刻度生成：默认在 real 空间取 1/2/5 步长，标签显示变换回的值
     * （logarithm 显示原价、percentage 显示百分比、normal 显示原值）。
     * 自定义刻度经 [sanitizeCustomTicks] 保护（A-18）。
     */
    fun ticks(spec: YAxisSpec, from: Double, to: Double, maxTickCount: Int = 5): AxisTicks {
        val (rf, rt) = realRange(spec, from, to)
        if (spec is YAxisSpec.Custom) {
            val custom = spec.axis.customTicks(rf, rt)
            if (custom != null) {
                val sanitized = sanitizeCustomTicks(custom, rf, rt)
                if (sanitized != null) {
                    return AxisTicks(sanitized.map { it.toTick(spec) })
                }
                val fallback = LinearYAxis.ticks(rf, rt, maxTickCount).map { it.toTick(spec) }
                return AxisTicks(fallback, degradedFromCustom = true, degradeReason = "自定义刻度非单调/含非有限值/超量(${MAX_CUSTOM_TICKS})，已回退默认")
            }
        }
        val baseZero = spec is YAxisSpec.Percentage && (spec.base == null || spec.base == 0.0)
        if (baseZero) {
            // D-01：基准为零 → 有限稳定范围 + 全部标签「—」
            val ticks = LinearYAxis.ticks(-1.0, 1.0, maxTickCount)
            return AxisTicks(ticks.map { AxisTick(it, null, "—") })
        }
        return AxisTicks(LinearYAxis.ticks(rf, rt, maxTickCount).map { it.toTick(spec) })
    }

    private fun Double.toTick(spec: YAxisSpec): AxisTick = when (spec) {
        is YAxisSpec.Percentage -> AxisTick(this, this, formatPercent(this))
        else -> {
            val display = realToValue(spec, this)
            if (display.isFinite()) AxisTick(this, display, formatDefault(display)) else AxisTick(this, null, "—")
        }
    }

    private fun formatPercent(v: Double): String = String.format("%.2f%%", v)

    private fun formatDefault(v: Double): String {
        if (v == v.toLong().toDouble() && abs(v) < 1e12) return v.toLong().toString()
        return if (abs(v) >= 1000) String.format("%,.0f", v) else trimZeros(v)
    }

    private fun trimZeros(v: Double): String {
        var s = String.format("%.6f", v).trimEnd('0').trimEnd('.')
        if (s.isEmpty() || s == "-") s = "0"
        return s
    }

    /** A-18 保护：有限、严格递增、去重、限量；违规返回 null（调用方降级）。 */
    private const val MAX_CUSTOM_TICKS = 30

    private fun sanitizeCustomTicks(raw: List<Double>, rf: Double, rt: Double): List<Double>? {
        if (raw.size > MAX_CUSTOM_TICKS) return null
        val finite = raw.filter { it.isFinite() }
        if (finite.size != raw.size) return null
        val sorted = finite.sorted()
        // 非单调（与排序不一致视为乱序拒绝，交由调用方降级）
        if (finite != sorted) return null
        val deduped = sorted.distinct()
        if (deduped.size < 2) return null
        return deduped
    }
}
