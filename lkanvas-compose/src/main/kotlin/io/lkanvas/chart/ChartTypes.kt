package io.lkanvas.chart

import io.lkanvas.core.common.NeighborData as CoreNeighborData
import io.lkanvas.core.common.VisibleRangeData as CoreVisibleRangeData

/**
 * 事件与视口类型——领域定义已镜像至 core/common（上游 `common/{Data,Action,
 * Crosshair,VisibleRange,BarSpace}.ts` 同名文件）；本文件保留同包别名，
 * 使 `io.lkanvas.chart` 包内既有引用与上游补丁检索路径同时有效。
 */

typealias ActionType = io.lkanvas.core.common.ActionType

/** 上游 `VisibleRange`：可见窗口（realFrom/realTo 为含未来留白的实际范围）。 */
typealias VisibleRange = io.lkanvas.core.common.VisibleRange

/** 上游 `BarSpace`：槽位四值。 */
typealias BarSpace = io.lkanvas.core.common.BarSpace

/** 上游 `NeighborData`：点击/命中 payload 邻居三元组。 */
typealias NeighborData<D> = CoreNeighborData<D>

/** 上游 `VisibleRangeData`：单根 candle 的命中/点击数据（嵌套 `data:{prev,current,next}`）。 */
typealias VisibleRangeData = CoreVisibleRangeData

/** 上游 `Crosshair`：十字线状态（全字段可选，与上游 Partial 语义一致）。 */
typealias Crosshair = io.lkanvas.core.common.Crosshair

/** 事件回调（上游 `ActionCallback = (data?) => void`）。 */
typealias ActionCallback = io.lkanvas.core.common.ActionCallback

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
