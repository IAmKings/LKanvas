package io.lkanvas.core.indicator

import io.lkanvas.core.data.Candle

/**
 * 上游 figure.styles 的**有效柱色上下文**（对齐 Indicator.ts instance.styles ×
 * 全局 styles 的合并结果）：实例 styles 覆盖 → 全局 styles 回退后的三色解析值，
 * null = 两级均未配置，由内置指标用自身默认色兜底。
 *
 * 颜色为 hex 字符串（core 无 Compose 依赖）；比较规则由各柱式指标
 * （VOL=收/开、MACD=值正负）在 provider 内自行保持，绘制端不按名称打补丁。
 */
data class BarColorsContext(
    val upColor: String? = null,
    val downColor: String? = null,
    val noChangeColor: String? = null,
)

/**
 * 携带 [BarColorsContext] 的 figure.styles 提供者（`PlotDef.styles: Any?` 的
 * 认可形态之一；旧 Function1/2/4 与 Map 形态继续受支持，见 compose evaluateStyles）。
 * [value] 为本行图形值（LINE 段可能为 NaN，按各指标语义处理）。
 */
fun interface ContextPlotStyles {
    fun styles(row: Int, params: List<Double>, value: Double, candles: List<Candle>, barColors: BarColorsContext): Map<String, String>?
}
