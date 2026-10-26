package io.lkanvas.chart

import io.lkanvas.core.data.Candle

/**
 * 事件类型与 payload——对齐上游 `common/Action.ts` 的 9 种 ActionType
 * 与各分发点 payload（源位置见 docs/alignment/ts-inventory.md §3）。
 */
enum class ActionType {
    ON_ZOOM,
    ON_SCROLL,
    ON_VISIBLE_RANGE_CHANGE,
    ON_CANDLE_TOOLTIP_FEATURE_CLICK,
    ON_INDICATOR_TOOLTIP_FEATURE_CLICK,
    ON_CROSSHAIR_FEATURE_CLICK,
    ON_CROSSHAIR_CHANGE,
    ON_CANDLE_BAR_CLICK,
    ON_PANE_DRAG,
}

/** 上游 `VisibleRange`：可见窗口（realFrom/realTo 为含未来留白的实际范围）。 */
data class VisibleRange(
    val from: Int,
    val to: Int,
    val realFrom: Double,
    val realTo: Double,
)

/** 上游 `BarSpace`：槽位四值。 */
data class BarSpace(val bar: Double, val halfBar: Double, val gapBar: Double, val halfGapBar: Double)

/** 上游 `VisibleRangeData`：单根 candle 的命中/点击数据（含前后邻居）。 */
data class VisibleRangeData(
    val dataIndex: Int,
    val x: Double,
    val prev: Candle?,
    val data: Candle,
    val next: Candle?,
)

/** 上游 `Crosshair`：十字线状态（全字段可选，与上游 Partial 语义一致）。 */
data class Crosshair(
    val x: Double? = null,
    val y: Double? = null,
    val paneId: String? = null,
    val realX: Double? = null,
    val timestamp: Long? = null,
    val kLineData: Candle? = null,
    val dataIndex: Int? = null,
    val realDataIndex: Double? = null,
)

/** 事件回调（上游 `ActionCallback = (data?) => void`）。 */
fun interface ActionCallback {
    fun onAction(data: Any?)
}

/** 上游 `ZoomAnchor`：缩放锚点（'cursor' | 'last_bar'）。 */
data class ZoomAnchor(
    val main: Anchor = Anchor.CURSOR,
    val xAxis: Anchor = Anchor.CURSOR,
) {
    enum class Anchor { CURSOR, LAST_BAR }
}
