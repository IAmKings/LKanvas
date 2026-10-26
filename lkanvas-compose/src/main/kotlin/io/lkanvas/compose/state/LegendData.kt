package io.lkanvas.compose.state

import io.lkanvas.core.data.Candle

/**
 * 图例/tooltip 数据（PRD R-11/T-24）：宿主通过 [KLineChart] 的 `legendSlot`
 * 拿到此数据自行渲染（Compose 插槽），或使用内置 Canvas 图例。
 *
 * @param index 当前数据索引（十字线跟随或末根）
 * @param candle 当前 candle
 * @param prevClose 前一根收盘（无则 null）
 * @param isCrosshair 是否处于十字线跟随
 * @param timeText 已格式化的时间文本
 * @param maValues MA 各列当前值（NaN = Missing）
 */
data class LegendData(
    val index: Int,
    val candle: Candle,
    val prevClose: Double?,
    val isCrosshair: Boolean,
    val timeText: String,
    val maValues: Map<String, Double>,
)
