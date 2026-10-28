package io.lkanvas.chart

/**
 * 多轴操作 API——对齐上游 `Chart.createYAxis/removeYAxis/getYAxes/overrideYAxis/overrideXAxis`
 * 与 `AxisOverride/YAxisOverride/XAxisOverride`（v10 Chart.ts:1003~1104）。
 *
 * 门面建模：轴实例以记录层 [YAxisInfo] 表达（id/paneId/位置/朝向/留白等语义字段），
 * 主轴 id 固定 "main"（与 [IndicatorInstance.yAxisId][io.lkanvas.core.indicator.IndicatorInstance.yAxisId]
 * 默认值一致）。自定义变换（createRange/valueToRealValue 等回调）随批次 5 绘制端
 * 消费开放；当前记录层保证 API 语义（创建/删除保护/过滤/覆写）与上游一致。
 */

/** 上游 `AxisPosition`：'left' | 'right'。 */
enum class AxisPosition { LEFT, RIGHT }

/** 上游 `AxisGap`：轴两侧留白比例（默认 top 0.2 / bottom 0.1）。 */
data class AxisGap(
    val top: Double = 0.2,
    val bottom: Double = 0.1,
)

/**
 * 上游 `YAxisOverride`（AxisOverride & { needWidget }）：
 * createYAxis 入参与 overrideYAxis 覆写共用；可空字段=未提供。
 */
data class YAxisOverride(
    val id: String? = null,
    val name: String? = null,
    val paneId: String? = null,
    val reverse: Boolean? = null,
    val inside: Boolean? = null,
    val position: AxisPosition? = null,
    val scrollZoomEnabled: Boolean? = null,
    val gap: AxisGap? = null,
    val needWidget: Boolean? = null,
)

/** 上游 `YAxisFilter`：paneId/id/name 过滤组合。 */
data class YAxisFilter(
    val paneId: String? = null,
    val id: String? = null,
    val name: String? = null,
)

/** 上游 `XAxisOverride`（时间轴覆写；无 id/paneId——x 轴全局唯一）。 */
data class XAxisOverride(
    val name: String? = null,
    val reverse: Boolean? = null,
    val inside: Boolean? = null,
    val position: AxisPosition? = null,
    val scrollZoomEnabled: Boolean? = null,
    val gap: AxisGap? = null,
)

/** 轴实例只读视图（上游 YAxis 信息面）。 */
data class YAxisInfo(
    val id: String,
    val name: String?,
    val paneId: String,
    val reverse: Boolean,
    val inside: Boolean,
    val position: AxisPosition,
    val scrollZoomEnabled: Boolean,
    val gap: AxisGap,
    val needWidget: Boolean,
    /** 是否 pane 默认轴（主 pane 默认轴不可删除，上游 isDefaultYAxis 语义）。 */
    val isDefault: Boolean,
)
