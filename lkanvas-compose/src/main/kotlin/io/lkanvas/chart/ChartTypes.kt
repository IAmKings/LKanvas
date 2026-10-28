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

// ---------- 上游类型别名同名化（TYP-010；TS string literal → Kotlin 枚举） ----------
// Styles 树内字段当前沿用字符串常量（"dashed"/"solid" 等），本组枚举提供
// 同名 API 面与值域约束；树内字段迁移随批次 5+ 绘制端消费统一。

/** 上游 `LineType = 'dashed' | 'solid'`。 */
enum class LineType(val value: String) { DASHED("dashed"), SOLID("solid") }

/** 上游 `PathType = 'stroke' | 'fill'`。 */
enum class PathType(val value: String) { STROKE("stroke"), FILL("fill") }

/** 上游 `PolygonType = PathType | 'stroke_fill'`。 */
enum class PolygonType(val value: String) { STROKE("stroke"), FILL("fill"), STROKE_FILL("stroke_fill") }

/** 上游 `TooltipShowRule = 'always' | 'follow_cross' | 'none'`。 */
enum class TooltipShowRule(val value: String) { ALWAYS("always"), FOLLOW_CROSS("follow_cross"), NONE("none") }

/** 上游 `TooltipShowType = 'standard' | 'rect'`。 */
enum class TooltipShowType(val value: String) { STANDARD("standard"), RECT("rect") }

/** 上游 `FeatureType = 'path' | 'icon_font'`。 */
enum class FeatureType(val value: String) { PATH("path"), ICON_FONT("icon_font") }

/** 上游 `TooltipFeaturePosition = 'left' | 'middle' | 'right'`。 */
enum class TooltipFeaturePosition(val value: String) { LEFT("left"), MIDDLE("middle"), RIGHT("right") }

/** 上游 `CandleType`：主图六形态。 */
enum class CandleType(val value: String) {
    CANDLE_SOLID("candle_solid"),
    CANDLE_STROKE("candle_stroke"),
    CANDLE_UP_STROKE("candle_up_stroke"),
    CANDLE_DOWN_STROKE("candle_down_stroke"),
    OHLC("ohlc"),
    AREA("area"),
}

/** 上游 `FormatDateType = 'tooltip' | 'crosshair' | 'xAxis'`。 */
enum class FormatDateType(val value: String) { TOOLTIP("tooltip"), CROSSHAIR("crosshair"), X_AXIS("xAxis") }

/** 上游 `ZoomAnchorType = 'cursor' | 'last_bar'`。 */
enum class ZoomAnchorType(val value: String) { CURSOR("cursor"), LAST_BAR("last_bar") }

/** 上游 `OverlayDrawingMode = 'step' | 'continuous'`。 */
enum class OverlayDrawingMode(val value: String) { STEP("step"), CONTINUOUS("continuous") }

/** 上游 `DataLoadType = 'init' | 'forward' | 'backward' | 'update'`（DataLoader.kt 亦有同名）。 */
enum class DataLoadType(val value: String) { INIT("init"), FORWARD("forward"), BACKWARD("backward"), UPDATE("update") }
