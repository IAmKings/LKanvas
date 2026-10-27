package io.lkanvas.chart

/**
 * 配置组类型——对齐上游 `Options.ts` 的 ThousandsSeparator/DecimalFold/Formatter/Hotkey
 * 与 `Chart.ts` 配置方法签名（批次 2c）。
 */

import io.lkanvas.core.data.Candle

/** 上游 `ThousandsSeparator{sign}`；format 由门面按 sign 生成。 */
data class ThousandsSeparator(val sign: String = ",")

/** 上游 `DecimalFold{threshold}`：小数折叠阈值。 */
data class DecimalFold(val threshold: Int = 3)

/** 上游 `Formatter{formatDate, formatBigNumber, formatExtendText}`（全部可选，未提供用内置）。 */
data class Formatter(
    val formatDate: ((timestamp: Long, template: String?, type: String?) -> String)? = null,
    val formatBigNumber: ((value: Double) -> String)? = null,
    val formatExtendText: ((data: Candle, defaultText: String) -> String)? = null,
)

/** 上游 `Hotkey{enabled, exclude[]}`（浏览器键鼠；Android 平台占位）。 */
data class Hotkey(val enabled: Boolean = true, val exclude: List<String> = emptyList())

/** 上游 `PaneOptions`：pane 高度与轴状态。 */
data class PaneOptions(
    val id: String,
    val height: Double? = null,
)
