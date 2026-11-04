package io.lkanvas.chart.styles

/**
 * Styles 树（对齐上游 common/Styles.ts 43 接口，批次 5-1）。
 *
 * 字段名/类型与 TS 逐一对应；默认值取上游 getDefault*Style() 基线
 * （Color 常量：RED=#F92855 GREEN=#2DC08E GREY=#76808F BLUE=#1677FF）。
 * 该树是 setStyles(DeepPartial) 的 schema；绘制层渐进消费（批次 5-4）。
 * Android 不适用项：family/weight 字体族字符串保留（渲染端映射）。
 */

/** 上游 Margin。 */
data class Margin(
    val marginLeft: Double = 0.0,
    val marginTop: Double = 0.0,
    val marginRight: Double = 0.0,
    val marginBottom: Double = 0.0,
)

/** 上游 Padding。 */
data class Padding(
    val paddingLeft: Double = 0.0,
    val paddingTop: Double = 0.0,
    val paddingRight: Double = 0.0,
    val paddingBottom: Double = 0.0,
)

/** 上游 Offset。 */
data class Offset(
    val offsetLeft: Double = 0.0,
    val offsetTop: Double = 0.0,
    val offsetRight: Double = 0.0,
    val offsetBottom: Double = 0.0,
)

/** 上游 LineStyle（style: 'dashed'|'solid'）。 */
data class LineStyle(
    val style: String = "solid",
    val size: Double = 1.0,
    val color: String = "#76808F",
    val dashedValue: List<Double> = listOf(2.0, 2.0),
)

/** 上游 SmoothLineStyle。 */
data class SmoothLineStyle(
    val style: String = "solid",
    val size: Double = 1.0,
    val color: String = "#76808F",
    val dashedValue: List<Double> = listOf(2.0, 2.0),
    val smooth: Boolean = false,
)

/** 上游 StateLineStyle。 */
data class StateLineStyle(
    val show: Boolean = true,
    val style: String = "solid",
    val size: Double = 1.0,
    val color: String? = null,
    val dashedValue: List<Double> = listOf(2.0, 2.0),
)

/** 上游 PathStyle（style: 'stroke'|'fill'）。 */
data class PathStyle(
    val style: String = "fill",
    val color: String = "#76808F",
    val lineWidth: Double = 1.0,
)

/** 上游 PolygonStyle（style: 'stroke'|'fill'|'stroke_fill'）。 */
data class PolygonStyle(
    val style: String = "fill",
    val color: String = "#76808F",
    val borderColor: String = "#76808F",
    val borderSize: Double = 1.0,
    val borderStyle: String = "solid",
    val borderDashedValue: List<Double> = listOf(2.0, 2.0),
)

/** 上游 RectStyle。 */
data class RectStyle(
    val style: String = "fill",
    val color: String = "#76808F",
    val borderColor: String = "#76808F",
    val borderSize: Double = 1.0,
    val borderStyle: String = "solid",
    val borderDashedValue: List<Double> = listOf(2.0, 2.0),
    val borderRadius: Double = 0.0,
)

/** 上游 TextStyle。 */
data class TextStyle(
    val style: String = "fill",
    val color: String = "#76808F",
    val size: Double = 12.0,
    val family: String = "Helvetica Neue",
    val weight: String = "normal",
    val borderStyle: String = "solid",
    val borderDashedValue: List<Double> = listOf(2.0, 2.0),
    val borderSize: Double = 1.0,
    val borderColor: String = "#76808F",
    val borderRadius: Double = 0.0,
    val backgroundColor: String? = null,
    val paddingLeft: Double = 0.0,
    val paddingTop: Double = 0.0,
    val paddingRight: Double = 0.0,
    val paddingBottom: Double = 0.0,
)

/** 上游 StateTextStyle。 */
data class StateTextStyle(
    val show: Boolean = true,
    val style: String = "fill",
    val color: String = "#76808F",
    val size: Double = 12.0,
    val family: String = "Helvetica Neue",
    val weight: String = "normal",
    val borderStyle: String = "solid",
    val borderDashedValue: List<Double> = listOf(2.0, 2.0),
    val borderSize: Double = 1.0,
    val borderColor: String = "#76808F",
    val borderRadius: Double = 0.0,
    val backgroundColor: String? = null,
    val paddingLeft: Double = 0.0,
    val paddingTop: Double = 0.0,
    val paddingRight: Double = 0.0,
    val paddingBottom: Double = 0.0,
)

/** 上游 ChangeColor。 */
data class ChangeColor(
    val upColor: String = "#2DC08E",
    val downColor: String = "#F92855",
    val noChangeColor: String = "#76808F",
)

/** 上游 GradientColor 单点。 */
data class GradientColorPoint(val offset: Double, val color: String)

/** 上游 FeaturePathStyle。 */
data class FeaturePathStyle(val path: List<List<Double>>)

/** 上游 FeatureIconFontStyle。 */
data class FeatureIconFontStyle(val family: String = "klinecharts-icon", val code: String = "")

/** 上游 FeatureStyle（type: 'path'|'icon_font'）。 */
data class FeatureStyle(
    val id: String = "",
    val backgroundColor: String = "#E8E8E8",
    val activeBackgroundColor: String = "#76808F",
    val size: Double = 20.0,
    val color: String = "#545454",
    val activeColor: String = "#FFFFFF",
    val borderRadius: Double = 10.0,
    val type: String = "path",
    val content: Any? = null,
    val marginLeft: Double = 0.0,
    val marginTop: Double = 0.0,
    val marginRight: Double = 0.0,
    val marginBottom: Double = 0.0,
)

/** 上游 GridStyle。 */
data class GridStyle(
    val show: Boolean = true,
    val horizontal: LineStyle = LineStyle(color = "#EDEDED", style = "dashed"),
    val vertical: LineStyle = LineStyle(color = "#EDEDED", style = "dashed"),
)

/** 上游 TooltipLegendChild。 */
data class TooltipLegendChild(val text: String = "", val color: String = "#76808F")

/** 上游 TooltipLegend（legacy candle tooltip 用）。 */
data class TooltipLegend(
    val title: TooltipLegendChild = TooltipLegendChild(),
    val value: TooltipLegendChild = TooltipLegendChild(),
)

/** 上游 TooltipFeatureStyle。 */
data class TooltipFeatureStyle(
    val position: String = "middle",
    val id: String = "",
    val backgroundColor: String = "#E8E8E8",
    val activeBackgroundColor: String = "#76808F",
    val size: Double = 20.0,
    val color: String = "#545454",
    val activeColor: String = "#FFFFFF",
    val borderRadius: Double = 10.0,
    val type: String = "path",
    val content: Any? = null,
    val marginLeft: Double = 0.0,
    val marginTop: Double = 0.0,
    val marginRight: Double = 0.0,
    val marginBottom: Double = 0.0,
)

/** 上游 TooltipTitleStyle（tooltip 标题行）。 */
data class TooltipTitleStyle(
    val show: Boolean = true,
    val showName: Boolean = true,
    val showParams: Boolean = true,
    val color: String = "#76808F",
    val size: Double = 12.0,
    val family: String = "Helvetica Neue",
    val weight: String = "normal",
    val marginLeft: Double = 0.0,
    val marginTop: Double = 4.0,
    val marginRight: Double = 6.0,
    val marginBottom: Double = 4.0,
    val defaultValue: String = "",
)

/** 上游 TooltipLegendStyle。 */
data class TooltipLegendStyle(
    val show: Boolean = true,
    val color: String = "#76808F",
    val size: Double = 12.0,
    val family: String = "Helvetica Neue",
    val weight: String = "normal",
    val marginLeft: Double = 0.0,
    val marginTop: Double = 4.0,
    val marginRight: Double = 6.0,
    val marginBottom: Double = 4.0,
    val defaultValue: String = "",
)

/** 上游 CandleTooltipStyle（candle. tooltip 节）。 */
data class CandleTooltipStyle(
    val showRule: String = "always", // 'always'|'follow_cross'|'none'
    val showType: String = "standard", // 'standard'|'rect'
    val rect: CandleTooltipRectStyle = CandleTooltipRectStyle(),
    val title: TooltipTitleStyle = TooltipTitleStyle(),
    val legend: TooltipLegendStyle = TooltipLegendStyle(),
    val offsetLeft: Double = 4.0,
    val offsetTop: Double = 6.0,
    val offsetRight: Double = 4.0,
    val offsetBottom: Double = 6.0,
)

/** 上游 CandleTooltipRectStyle。 */
data class CandleTooltipRectStyle(
    val position: String = "top", // CandleTooltipRectPosition
    val paddingLeft: Double = 8.0,
    val paddingRight: Double = 8.0,
    val paddingTop: Double = 4.0,
    val paddingBottom: Double = 4.0,
    val offsetLeft: Double = 8.0,
    val offsetTop: Double = 8.0,
    val offsetRight: Double = 8.0,
    val offsetBottom: Double = 8.0,
    val borderWidth: Double = 1.0,
    val borderRadius: Double = 4.0,
    val color: String = "#FEFEFE",
    val borderColor: String = "#F2F3F5",
)

/** 上游 CandleAreaStyle。 */
data class CandleAreaStyle(
    val lineSize: Double = 2.0,
    val lineColor: String = "#1677FF",
    val value: String = "close",
    val smooth: Boolean = false,
    val backgroundColor: List<GradientColorPoint> = listOf(
        GradientColorPoint(0.0, "#1677FF14"),
        GradientColorPoint(1.0, "#1677FF33"),
    ),
    val point: CandleAreaPointStyle = CandleAreaPointStyle(),
)

/** 上游 CandleAreaPointStyle。 */
data class CandleAreaPointStyle(
    val show: Boolean = true,
    val color: String = "#1677FF",
    val radius: Double = 4.0,
    val rippleColor: String = "#1677FF4D",
    val rippleRadius: Double = 8.0,
    val animation: Boolean = true,
    val animationDuration: Double = 1000.0,
)

/** 上游 CandleHighLowPriceMarkStyle。 */
data class CandleHighLowPriceMarkStyle(
    val show: Boolean = true,
    val color: String = "#76808F",
    val textOffset: Double = 5.0,
    val textSize: Double = 10.0,
    val textFamily: String = "Helvetica Neue",
    val textWeight: String = "normal",
)

/** 上游 CandleLastPriceMarkStyle。 */
data class CandleLastPriceMarkStyle(
    val show: Boolean = true,
    val compareRule: String = "current_open", // 'current_open'|'previous_close'
    val upColor: String = "#2DC08E",
    val downColor: String = "#F92855",
    val noChangeColor: String = "#76808F",
    val line: StateLineStyle = StateLineStyle(style = "dashed", dashedValue = listOf(4.0, 4.0), color = null),
    val text: StateTextStyle = StateTextStyle(size = 12.0),
    val extendTexts: Map<String, String> = emptyMap(),
)

/** 上游 CandlePriceMarkStyle。 */
data class CandlePriceMarkStyle(
    val show: Boolean = true,
    val high: CandleHighLowPriceMarkStyle = CandleHighLowPriceMarkStyle(),
    val low: CandleHighLowPriceMarkStyle = CandleHighLowPriceMarkStyle(),
    val last: CandleLastPriceMarkStyle = CandleLastPriceMarkStyle(),
)

/** 上游 CandleBarColor（bar 静态配色）。 */
data class CandleBarColor(
    val compareRule: String = "current_open",
    val upColor: String = "#2DC08E",
    val downColor: String = "#F92855",
    val noChangeColor: String = "#76808F",
    val upBorderColor: String = "#2DC08E",
    val downBorderColor: String = "#F92855",
    val noChangeBorderColor: String = "#76808F",
    val upWickColor: String = "#2DC08E",
    val downWickColor: String = "#F92855",
    val noChangeWickColor: String = "#76808F",
)

/** 上游 CandleBarColorCallback：按 candle 返回配色（支持 compareRule 自定义）。 */
fun interface CandleBarColorCallback {
    fun color(data: io.lkanvas.core.data.Candle, prev: io.lkanvas.core.data.Candle?): CandleBarColor
}

/** 上游 CandleStyle。 */
data class CandleStyle(
    val type: String = "candle_solid",
    val bar: Any? = CandleBarColor(),
    val area: CandleAreaStyle = CandleAreaStyle(),
    val priceMark: CandlePriceMarkStyle = CandlePriceMarkStyle(),
    val tooltip: CandleTooltipStyle = CandleTooltipStyle(),
)

/** 上游 IndicatorLastValueMarkStyle。 */
data class IndicatorLastValueMarkStyle(
    val show: Boolean = true,
    val text: StateTextStyle = StateTextStyle(),
)

/** 上游 IndicatorTooltipStyle。 */
data class IndicatorTooltipStyle(
    val title: TooltipTitleStyle = TooltipTitleStyle(),
    val legend: TooltipLegendStyle = TooltipLegendStyle(),
    /** 上游 indicator.tooltip.features：tooltip 功能图标（默认空=不渲染；三事件命中源）。 */
    val features: List<TooltipFeatureStyle> = emptyList(),
    val offsetLeft: Double = 4.0,
    val offsetTop: Double = 6.0,
    val offsetRight: Double = 4.0,
    val offsetBottom: Double = 6.0,
    val showRule: String = "always",
    val showType: String = "standard",
)

/** 上游 IndicatorStyle。 */
data class IndicatorStyle(
    val bars: List<PolygonStyle> = emptyList(),
    val lines: List<SmoothLineStyle> = emptyList(),
    val circles: List<PolygonStyle> = emptyList(),
    val texts: List<TextStyle> = emptyList(),
    val lastValueMark: IndicatorLastValueMarkStyle = IndicatorLastValueMarkStyle(),
    val tooltip: IndicatorTooltipStyle = IndicatorTooltipStyle(),
)

/**
 * 指标柱/圆的**显式三色**表示（hex；上游 bars 的 upColor/downColor/noChangeColor 对应物）。
 * 旧 `List<PolygonStyle>` 形态按位归一为 [0]=涨、[1]=跌、[2]=平，本类型为等价显式写法。
 */
data class IndicatorBarColors(
    val upColor: String,
    val downColor: String,
    val noChangeColor: String,
)

/**
 * 指标实例样式显式规格（`createIndicator/overrideIndicator` 的 `styles: Any?`
 * 认可形态之一，0.16.0-beta02 新增；`IndicatorStyle` 与旧 `List<PolygonStyle>`
 * 形态继续受支持）。
 *
 * - [lineColors]：LINE 槽按 plot 索引取色（不足/超出部分回退全局调色板）；
 * - [barColors]/[circleColors]：BAR/CIRCLE 槽三色，未配置项回退
 *   全局 styles（indicator.bars）再回退内置默认。
 */
data class IndicatorStyleSpec(
    val lineColors: List<String> = emptyList(),
    val barColors: IndicatorBarColors? = null,
    val circleColors: IndicatorBarColors? = null,
)

/** 上游 AxisLineStyle。 */
data class AxisLineStyle(
    val show: Boolean = true,
    val size: Double = 1.0,
    val color: String = "#DDDDDD",
    val dashedValue: List<Double> = listOf(2.0, 2.0),
)

/** 上游 AxisTickLineStyle。 */
data class AxisTickLineStyle(
    val show: Boolean = true,
    val size: Double = 1.0,
    val color: String = "#DDDDDD",
    val dashedValue: List<Double> = listOf(2.0, 2.0),
    val length: Double = 3.0,
)

/** 上游 AxisTickTextStyle。 */
data class AxisTickTextStyle(
    val show: Boolean = true,
    val color: String = "#76808F",
    val size: Double = 10.0,
    val family: String = "Helvetica Neue",
    val weight: String = "normal",
    val marginStart: Double = 6.0,
    val marginEnd: Double = 6.0,
)

/** 上游 AxisStyle。 */
data class AxisStyle(
    val show: Boolean = true,
    val size: Double = 1.0,
    val axisLine: AxisLineStyle = AxisLineStyle(color = "#DDDDDD"),
    val tickLine: AxisTickLineStyle = AxisTickLineStyle(color = "#DDDDDD"),
    val tickText: AxisTickTextStyle = AxisTickTextStyle(),
    val nameTextStyle: StateTextStyle = StateTextStyle(show = false),
)

/** 上游 CrosshairDirectionStyle。 */
data class CrosshairDirectionStyle(
    val show: Boolean = true,
    val line: StateLineStyle = StateLineStyle(color = "#76808F", style = "dashed"),
    val text: StateTextStyle = StateTextStyle(
        show = true,
        size = 12.0,
        color = "#FFFFFF",
        backgroundColor = "#505050",
        borderRadius = 4.0,
        paddingLeft = 4.0,
        paddingTop = 4.0,
        paddingRight = 4.0,
        paddingBottom = 4.0,
    ),
)

/** 上游 CrosshairStyle。 */
data class CrosshairStyle(
    val show: Boolean = true,
    val horizontal: CrosshairDirectionStyle = CrosshairDirectionStyle(),
    val vertical: CrosshairDirectionStyle = CrosshairDirectionStyle(),
)

/** 上游 OverlayPointStyle（六态中 active* 为选中态）。 */
data class OverlayPointStyle(
    val color: String = "#1677FF",
    val borderColor: String = "#FFFFFF",
    val borderSize: Double = 1.0,
    val radius: Double = 4.0,
    val activeColor: String = "#1677FF",
    val activeBorderColor: String = "#FFFFFF",
    val activeBorderSize: Double = 1.0,
    val activeRadius: Double = 4.0,
)

/** 上游 OverlayStyle。 */
data class OverlayStyle(
    val point: OverlayPointStyle = OverlayPointStyle(),
    val line: SmoothLineStyle = SmoothLineStyle(color = "#1677FF"),
    val rect: RectStyle = RectStyle(color = "#1677FF33", borderColor = "#1677FF"),
    val polygon: PolygonStyle = PolygonStyle(color = "#1677FF33", borderColor = "#1677FF"),
    val circle: PolygonStyle = PolygonStyle(color = "#1677FF33", borderColor = "#1677FF"),
    val arc: LineStyle = LineStyle(color = "#1677FF"),
    val text: TextStyle = TextStyle(
        color = "#1677FF",
        paddingLeft = 4.0,
        paddingTop = 4.0,
        paddingRight = 4.0,
        paddingBottom = 4.0,
    ),
)

/** 上游 SeparatorStyle。 */
data class SeparatorStyle(
    val size: Double = 6.0,
    val color: String = "#DDDDDD",
    val fill: Boolean = false,
    val activeBackgroundColor: String = "#B8C1CC",
)

/** 上游 XAxisStyle/YAxisStyle（结构相同）。 */
data class XAxisStyle(
    val show: Boolean = true,
    val size: Double = 1.0,
    val axisLine: AxisLineStyle = AxisLineStyle(color = "#DDDDDD"),
    val tickLine: AxisTickLineStyle = AxisTickLineStyle(color = "#DDDDDD"),
    val tickText: AxisTickTextStyle = AxisTickTextStyle(),
    val nameTextStyle: StateTextStyle = StateTextStyle(show = false),
)

data class YAxisStyle(
    val show: Boolean = true,
    val size: Double = 1.0,
    val axisLine: AxisLineStyle = AxisLineStyle(color = "#DDDDDD"),
    val tickLine: AxisTickLineStyle = AxisTickLineStyle(color = "#DDDDDD"),
    val tickText: AxisTickTextStyle = AxisTickTextStyle(),
    val nameTextStyle: StateTextStyle = StateTextStyle(show = false),
)

/** 上游根 Styles。 */
data class Styles(
    val grid: GridStyle = GridStyle(),
    val candle: CandleStyle = CandleStyle(),
    val indicator: IndicatorStyle = IndicatorStyle(),
    val xAxis: XAxisStyle = XAxisStyle(),
    val yAxis: YAxisStyle = YAxisStyle(),
    val separator: SeparatorStyle = SeparatorStyle(),
    val crosshair: CrosshairStyle = CrosshairStyle(),
    val overlay: OverlayStyle = OverlayStyle(),
)
