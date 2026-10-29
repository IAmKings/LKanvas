package io.lkanvas.chart.styles

/**
 * 上游 `DeepPartial<Styles>` 的 Kotlin 镜像（R04，复审 2026-10-30）：与 Styles 树
 * 同构的**全字段可空**补丁类型——null=未提供，非 null=已提供（显式默认值照常生效）。
 *
 * 背景：Kotlin data class 全字段非空默认，无法区分「未填」与「显式默认」；
 * 上游 TS 对象字面量天然只携带提供的 key（typeChecks.ts merge 遍历自有属性）。
 * 本类型树补齐该语义：`setStyles(StylesPatch)` 按**提供的路径**合并，
 * 未提供的分支保持 base 原值（局部补丁不重置其他配置）。
 *
 * 生成方式：与 Styles.kt 同构（脚本生成，结构变化时重新生成）；
 * 每个节点提供 `applyTo(base)`：仅覆盖已提供字段，嵌套节点递归。
 */

data class MarginPatch(
    val marginLeft: Double? = null,
    val marginTop: Double? = null,
    val marginRight: Double? = null,
    val marginBottom: Double? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: Margin): Margin = base.copy(
        marginLeft = marginLeft ?: base.marginLeft,
        marginTop = marginTop ?: base.marginTop,
        marginRight = marginRight ?: base.marginRight,
        marginBottom = marginBottom ?: base.marginBottom,
    )
}

data class PaddingPatch(
    val paddingLeft: Double? = null,
    val paddingTop: Double? = null,
    val paddingRight: Double? = null,
    val paddingBottom: Double? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: Padding): Padding = base.copy(
        paddingLeft = paddingLeft ?: base.paddingLeft,
        paddingTop = paddingTop ?: base.paddingTop,
        paddingRight = paddingRight ?: base.paddingRight,
        paddingBottom = paddingBottom ?: base.paddingBottom,
    )
}

data class OffsetPatch(
    val offsetLeft: Double? = null,
    val offsetTop: Double? = null,
    val offsetRight: Double? = null,
    val offsetBottom: Double? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: Offset): Offset = base.copy(
        offsetLeft = offsetLeft ?: base.offsetLeft,
        offsetTop = offsetTop ?: base.offsetTop,
        offsetRight = offsetRight ?: base.offsetRight,
        offsetBottom = offsetBottom ?: base.offsetBottom,
    )
}

data class LineStylePatch(
    val style: String? = null,
    val size: Double? = null,
    val color: String? = null,
    val dashedValue: List<Double>? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: LineStyle): LineStyle = base.copy(
        style = style ?: base.style,
        size = size ?: base.size,
        color = color ?: base.color,
        dashedValue = dashedValue ?: base.dashedValue,
    )
}

data class SmoothLineStylePatch(
    val style: String? = null,
    val size: Double? = null,
    val color: String? = null,
    val dashedValue: List<Double>? = null,
    val smooth: Boolean? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: SmoothLineStyle): SmoothLineStyle = base.copy(
        style = style ?: base.style,
        size = size ?: base.size,
        color = color ?: base.color,
        dashedValue = dashedValue ?: base.dashedValue,
        smooth = smooth ?: base.smooth,
    )
}

data class StateLineStylePatch(
    val show: Boolean? = null,
    val style: String? = null,
    val size: Double? = null,
    val color: String?? = null,
    val dashedValue: List<Double>? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: StateLineStyle): StateLineStyle = base.copy(
        show = show ?: base.show,
        style = style ?: base.style,
        size = size ?: base.size,
        color = color ?: base.color,
        dashedValue = dashedValue ?: base.dashedValue,
    )
}

data class PathStylePatch(
    val style: String? = null,
    val color: String? = null,
    val lineWidth: Double? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: PathStyle): PathStyle = base.copy(
        style = style ?: base.style,
        color = color ?: base.color,
        lineWidth = lineWidth ?: base.lineWidth,
    )
}

data class PolygonStylePatch(
    val style: String? = null,
    val color: String? = null,
    val borderColor: String? = null,
    val borderSize: Double? = null,
    val borderStyle: String? = null,
    val borderDashedValue: List<Double>? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: PolygonStyle): PolygonStyle = base.copy(
        style = style ?: base.style,
        color = color ?: base.color,
        borderColor = borderColor ?: base.borderColor,
        borderSize = borderSize ?: base.borderSize,
        borderStyle = borderStyle ?: base.borderStyle,
        borderDashedValue = borderDashedValue ?: base.borderDashedValue,
    )
}

data class RectStylePatch(
    val style: String? = null,
    val color: String? = null,
    val borderColor: String? = null,
    val borderSize: Double? = null,
    val borderStyle: String? = null,
    val borderDashedValue: List<Double>? = null,
    val borderRadius: Double? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: RectStyle): RectStyle = base.copy(
        style = style ?: base.style,
        color = color ?: base.color,
        borderColor = borderColor ?: base.borderColor,
        borderSize = borderSize ?: base.borderSize,
        borderStyle = borderStyle ?: base.borderStyle,
        borderDashedValue = borderDashedValue ?: base.borderDashedValue,
        borderRadius = borderRadius ?: base.borderRadius,
    )
}

data class TextStylePatch(
    val style: String? = null,
    val color: String? = null,
    val size: Double? = null,
    val family: String? = null,
    val weight: String? = null,
    val borderStyle: String? = null,
    val borderDashedValue: List<Double>? = null,
    val borderSize: Double? = null,
    val borderColor: String? = null,
    val borderRadius: Double? = null,
    val backgroundColor: String?? = null,
    val paddingLeft: Double? = null,
    val paddingTop: Double? = null,
    val paddingRight: Double? = null,
    val paddingBottom: Double? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: TextStyle): TextStyle = base.copy(
        style = style ?: base.style,
        color = color ?: base.color,
        size = size ?: base.size,
        family = family ?: base.family,
        weight = weight ?: base.weight,
        borderStyle = borderStyle ?: base.borderStyle,
        borderDashedValue = borderDashedValue ?: base.borderDashedValue,
        borderSize = borderSize ?: base.borderSize,
        borderColor = borderColor ?: base.borderColor,
        borderRadius = borderRadius ?: base.borderRadius,
        backgroundColor = backgroundColor ?: base.backgroundColor,
        paddingLeft = paddingLeft ?: base.paddingLeft,
        paddingTop = paddingTop ?: base.paddingTop,
        paddingRight = paddingRight ?: base.paddingRight,
        paddingBottom = paddingBottom ?: base.paddingBottom,
    )
}

data class StateTextStylePatch(
    val show: Boolean? = null,
    val style: String? = null,
    val color: String? = null,
    val size: Double? = null,
    val family: String? = null,
    val weight: String? = null,
    val borderStyle: String? = null,
    val borderDashedValue: List<Double>? = null,
    val borderSize: Double? = null,
    val borderColor: String? = null,
    val borderRadius: Double? = null,
    val backgroundColor: String?? = null,
    val paddingLeft: Double? = null,
    val paddingTop: Double? = null,
    val paddingRight: Double? = null,
    val paddingBottom: Double? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: StateTextStyle): StateTextStyle = base.copy(
        show = show ?: base.show,
        style = style ?: base.style,
        color = color ?: base.color,
        size = size ?: base.size,
        family = family ?: base.family,
        weight = weight ?: base.weight,
        borderStyle = borderStyle ?: base.borderStyle,
        borderDashedValue = borderDashedValue ?: base.borderDashedValue,
        borderSize = borderSize ?: base.borderSize,
        borderColor = borderColor ?: base.borderColor,
        borderRadius = borderRadius ?: base.borderRadius,
        backgroundColor = backgroundColor ?: base.backgroundColor,
        paddingLeft = paddingLeft ?: base.paddingLeft,
        paddingTop = paddingTop ?: base.paddingTop,
        paddingRight = paddingRight ?: base.paddingRight,
        paddingBottom = paddingBottom ?: base.paddingBottom,
    )
}

data class ChangeColorPatch(
    val upColor: String? = null,
    val downColor: String? = null,
    val noChangeColor: String? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: ChangeColor): ChangeColor = base.copy(
        upColor = upColor ?: base.upColor,
        downColor = downColor ?: base.downColor,
        noChangeColor = noChangeColor ?: base.noChangeColor,
    )
}

data class GradientColorPointPatch(
    val offset: Double? = null,
    val color: String? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: GradientColorPoint): GradientColorPoint = base.copy(
        offset = offset ?: base.offset,
        color = color ?: base.color,
    )
}

data class FeaturePathStylePatch(
    val path: List<List<Double>>? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: FeaturePathStyle): FeaturePathStyle = base.copy(
        path = path ?: base.path,
    )
}

data class FeatureIconFontStylePatch(
    val family: String? = null,
    val code: String? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: FeatureIconFontStyle): FeatureIconFontStyle = base.copy(
        family = family ?: base.family,
        code = code ?: base.code,
    )
}

data class FeatureStylePatch(
    val id: String? = null,
    val backgroundColor: String? = null,
    val activeBackgroundColor: String? = null,
    val size: Double? = null,
    val color: String? = null,
    val activeColor: String? = null,
    val borderRadius: Double? = null,
    val type: String? = null,
    val content: Any?? = null,
    val marginLeft: Double? = null,
    val marginTop: Double? = null,
    val marginRight: Double? = null,
    val marginBottom: Double? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: FeatureStyle): FeatureStyle = base.copy(
        id = id ?: base.id,
        backgroundColor = backgroundColor ?: base.backgroundColor,
        activeBackgroundColor = activeBackgroundColor ?: base.activeBackgroundColor,
        size = size ?: base.size,
        color = color ?: base.color,
        activeColor = activeColor ?: base.activeColor,
        borderRadius = borderRadius ?: base.borderRadius,
        type = type ?: base.type,
        content = content ?: base.content,
        marginLeft = marginLeft ?: base.marginLeft,
        marginTop = marginTop ?: base.marginTop,
        marginRight = marginRight ?: base.marginRight,
        marginBottom = marginBottom ?: base.marginBottom,
    )
}

data class GridStylePatch(
    val show: Boolean? = null,
    val horizontal: LineStylePatch? = null,
    val vertical: LineStylePatch? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: GridStyle): GridStyle = base.copy(
        show = show ?: base.show,
        horizontal = horizontal?.applyTo(base.horizontal) ?: base.horizontal,
        vertical = vertical?.applyTo(base.vertical) ?: base.vertical,
    )
}

data class TooltipLegendChildPatch(
    val text: String? = null,
    val color: String? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: TooltipLegendChild): TooltipLegendChild = base.copy(
        text = text ?: base.text,
        color = color ?: base.color,
    )
}

data class TooltipLegendPatch(
    val title: TooltipLegendChildPatch? = null,
    val value: TooltipLegendChildPatch? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: TooltipLegend): TooltipLegend = base.copy(
        title = title?.applyTo(base.title) ?: base.title,
        value = value?.applyTo(base.value) ?: base.value,
    )
}

data class TooltipFeatureStylePatch(
    val position: String? = null,
    val id: String? = null,
    val backgroundColor: String? = null,
    val activeBackgroundColor: String? = null,
    val size: Double? = null,
    val color: String? = null,
    val activeColor: String? = null,
    val borderRadius: Double? = null,
    val type: String? = null,
    val content: Any?? = null,
    val marginLeft: Double? = null,
    val marginTop: Double? = null,
    val marginRight: Double? = null,
    val marginBottom: Double? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: TooltipFeatureStyle): TooltipFeatureStyle = base.copy(
        position = position ?: base.position,
        id = id ?: base.id,
        backgroundColor = backgroundColor ?: base.backgroundColor,
        activeBackgroundColor = activeBackgroundColor ?: base.activeBackgroundColor,
        size = size ?: base.size,
        color = color ?: base.color,
        activeColor = activeColor ?: base.activeColor,
        borderRadius = borderRadius ?: base.borderRadius,
        type = type ?: base.type,
        content = content ?: base.content,
        marginLeft = marginLeft ?: base.marginLeft,
        marginTop = marginTop ?: base.marginTop,
        marginRight = marginRight ?: base.marginRight,
        marginBottom = marginBottom ?: base.marginBottom,
    )
}

data class TooltipTitleStylePatch(
    val show: Boolean? = null,
    val showName: Boolean? = null,
    val showParams: Boolean? = null,
    val color: String? = null,
    val size: Double? = null,
    val family: String? = null,
    val weight: String? = null,
    val marginLeft: Double? = null,
    val marginTop: Double? = null,
    val marginRight: Double? = null,
    val marginBottom: Double? = null,
    val defaultValue: String? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: TooltipTitleStyle): TooltipTitleStyle = base.copy(
        show = show ?: base.show,
        showName = showName ?: base.showName,
        showParams = showParams ?: base.showParams,
        color = color ?: base.color,
        size = size ?: base.size,
        family = family ?: base.family,
        weight = weight ?: base.weight,
        marginLeft = marginLeft ?: base.marginLeft,
        marginTop = marginTop ?: base.marginTop,
        marginRight = marginRight ?: base.marginRight,
        marginBottom = marginBottom ?: base.marginBottom,
        defaultValue = defaultValue ?: base.defaultValue,
    )
}

data class TooltipLegendStylePatch(
    val show: Boolean? = null,
    val color: String? = null,
    val size: Double? = null,
    val family: String? = null,
    val weight: String? = null,
    val marginLeft: Double? = null,
    val marginTop: Double? = null,
    val marginRight: Double? = null,
    val marginBottom: Double? = null,
    val defaultValue: String? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: TooltipLegendStyle): TooltipLegendStyle = base.copy(
        show = show ?: base.show,
        color = color ?: base.color,
        size = size ?: base.size,
        family = family ?: base.family,
        weight = weight ?: base.weight,
        marginLeft = marginLeft ?: base.marginLeft,
        marginTop = marginTop ?: base.marginTop,
        marginRight = marginRight ?: base.marginRight,
        marginBottom = marginBottom ?: base.marginBottom,
        defaultValue = defaultValue ?: base.defaultValue,
    )
}

data class CandleTooltipStylePatch(
    val showRule: String? = null,
    val title: TooltipTitleStylePatch? = null,
    val legend: TooltipLegendStylePatch? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: CandleTooltipStyle): CandleTooltipStyle = base.copy(
        showRule = showRule ?: base.showRule,
        title = title?.applyTo(base.title) ?: base.title,
        legend = legend?.applyTo(base.legend) ?: base.legend,
    )
}

data class CandleTooltipRectStylePatch(
    val position: String? = null,
    val paddingRight: Double? = null,
    val paddingTop: Double? = null,
    val paddingBottom: Double? = null,
    val offsetLeft: Double? = null,
    val offsetTop: Double? = null,
    val offsetRight: Double? = null,
    val offsetBottom: Double? = null,
    val borderWidth: Double? = null,
    val borderRadius: Double? = null,
    val color: String? = null,
    val borderColor: String? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: CandleTooltipRectStyle): CandleTooltipRectStyle = base.copy(
        position = position ?: base.position,
        paddingRight = paddingRight ?: base.paddingRight,
        paddingTop = paddingTop ?: base.paddingTop,
        paddingBottom = paddingBottom ?: base.paddingBottom,
        offsetLeft = offsetLeft ?: base.offsetLeft,
        offsetTop = offsetTop ?: base.offsetTop,
        offsetRight = offsetRight ?: base.offsetRight,
        offsetBottom = offsetBottom ?: base.offsetBottom,
        borderWidth = borderWidth ?: base.borderWidth,
        borderRadius = borderRadius ?: base.borderRadius,
        color = color ?: base.color,
        borderColor = borderColor ?: base.borderColor,
    )
}

data class CandleAreaStylePatch(
    val lineSize: Double? = null,
    val lineColor: String? = null,
    val value: String? = null,
    val smooth: Boolean? = null,
    val backgroundColor: List<GradientColorPoint>? = null,
    val point: CandleAreaPointStylePatch? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: CandleAreaStyle): CandleAreaStyle = base.copy(
        lineSize = lineSize ?: base.lineSize,
        lineColor = lineColor ?: base.lineColor,
        value = value ?: base.value,
        smooth = smooth ?: base.smooth,
        backgroundColor = backgroundColor ?: base.backgroundColor,
        point = point?.applyTo(base.point) ?: base.point,
    )
}

data class CandleAreaPointStylePatch(
    val show: Boolean? = null,
    val color: String? = null,
    val radius: Double? = null,
    val rippleColor: String? = null,
    val rippleRadius: Double? = null,
    val animation: Boolean? = null,
    val animationDuration: Double? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: CandleAreaPointStyle): CandleAreaPointStyle = base.copy(
        show = show ?: base.show,
        color = color ?: base.color,
        radius = radius ?: base.radius,
        rippleColor = rippleColor ?: base.rippleColor,
        rippleRadius = rippleRadius ?: base.rippleRadius,
        animation = animation ?: base.animation,
        animationDuration = animationDuration ?: base.animationDuration,
    )
}

data class CandleHighLowPriceMarkStylePatch(
    val show: Boolean? = null,
    val color: String? = null,
    val textOffset: Double? = null,
    val textSize: Double? = null,
    val textFamily: String? = null,
    val textWeight: String? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: CandleHighLowPriceMarkStyle): CandleHighLowPriceMarkStyle = base.copy(
        show = show ?: base.show,
        color = color ?: base.color,
        textOffset = textOffset ?: base.textOffset,
        textSize = textSize ?: base.textSize,
        textFamily = textFamily ?: base.textFamily,
        textWeight = textWeight ?: base.textWeight,
    )
}

data class CandleLastPriceMarkStylePatch(
    val show: Boolean? = null,
    val compareRule: String? = null,
    val downColor: String? = null,
    val noChangeColor: String? = null,
    val line: StateLineStylePatch? = null,
    val text: StateTextStylePatch? = null,
    val extendTexts: Map<String, String>? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: CandleLastPriceMarkStyle): CandleLastPriceMarkStyle = base.copy(
        show = show ?: base.show,
        compareRule = compareRule ?: base.compareRule,
        downColor = downColor ?: base.downColor,
        noChangeColor = noChangeColor ?: base.noChangeColor,
        line = line?.applyTo(base.line) ?: base.line,
        text = text?.applyTo(base.text) ?: base.text,
        extendTexts = extendTexts ?: base.extendTexts,
    )
}

data class CandlePriceMarkStylePatch(
    val show: Boolean? = null,
    val high: CandleHighLowPriceMarkStylePatch? = null,
    val low: CandleHighLowPriceMarkStylePatch? = null,
    val last: CandleLastPriceMarkStylePatch? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: CandlePriceMarkStyle): CandlePriceMarkStyle = base.copy(
        show = show ?: base.show,
        high = high?.applyTo(base.high) ?: base.high,
        low = low?.applyTo(base.low) ?: base.low,
        last = last?.applyTo(base.last) ?: base.last,
    )
}

data class CandleBarColorPatch(
    val compareRule: String? = null,
    val upColor: String? = null,
    val downColor: String? = null,
    val noChangeColor: String? = null,
    val upBorderColor: String? = null,
    val downBorderColor: String? = null,
    val noChangeBorderColor: String? = null,
    val upWickColor: String? = null,
    val downWickColor: String? = null,
    val noChangeWickColor: String? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: CandleBarColor): CandleBarColor = base.copy(
        compareRule = compareRule ?: base.compareRule,
        upColor = upColor ?: base.upColor,
        downColor = downColor ?: base.downColor,
        noChangeColor = noChangeColor ?: base.noChangeColor,
        upBorderColor = upBorderColor ?: base.upBorderColor,
        downBorderColor = downBorderColor ?: base.downBorderColor,
        noChangeBorderColor = noChangeBorderColor ?: base.noChangeBorderColor,
        upWickColor = upWickColor ?: base.upWickColor,
        downWickColor = downWickColor ?: base.downWickColor,
        noChangeWickColor = noChangeWickColor ?: base.noChangeWickColor,
    )
}

data class CandleStylePatch(
    val type: String? = null,
    val bar: Any? = null,
    val area: CandleAreaStylePatch? = null,
    val priceMark: CandlePriceMarkStylePatch? = null,
    val tooltip: CandleTooltipStylePatch? = null,
) {

    private fun mergeBar(baseBar: Any?): Any? = when {
        bar is CandleBarColorPatch && baseBar is CandleBarColor -> bar.applyTo(baseBar)
        bar != null -> bar
        else -> baseBar
    }
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: CandleStyle): CandleStyle = base.copy(
        type = type ?: base.type,
        bar = mergeBar(base.bar),
        area = area?.applyTo(base.area) ?: base.area,
        priceMark = priceMark?.applyTo(base.priceMark) ?: base.priceMark,
        tooltip = tooltip?.applyTo(base.tooltip) ?: base.tooltip,
    )
}

data class IndicatorLastValueMarkStylePatch(
    val show: Boolean? = null,
    val text: StateTextStylePatch? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: IndicatorLastValueMarkStyle): IndicatorLastValueMarkStyle = base.copy(
        show = show ?: base.show,
        text = text?.applyTo(base.text) ?: base.text,
    )
}

data class IndicatorTooltipStylePatch(
    val title: TooltipTitleStylePatch? = null,
    val legend: TooltipLegendStylePatch? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: IndicatorTooltipStyle): IndicatorTooltipStyle = base.copy(
        title = title?.applyTo(base.title) ?: base.title,
        legend = legend?.applyTo(base.legend) ?: base.legend,
    )
}

data class IndicatorStylePatch(
    val bars: List<PolygonStyle>? = null,
    val lines: List<SmoothLineStyle>? = null,
    val circles: List<PolygonStyle>? = null,
    val texts: List<TextStyle>? = null,
    val lastValueMark: IndicatorLastValueMarkStylePatch? = null,
    val tooltip: IndicatorTooltipStylePatch? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: IndicatorStyle): IndicatorStyle = base.copy(
        bars = bars ?: base.bars,
        lines = lines ?: base.lines,
        circles = circles ?: base.circles,
        texts = texts ?: base.texts,
        lastValueMark = lastValueMark?.applyTo(base.lastValueMark) ?: base.lastValueMark,
        tooltip = tooltip?.applyTo(base.tooltip) ?: base.tooltip,
    )
}

data class AxisLineStylePatch(
    val show: Boolean? = null,
    val size: Double? = null,
    val color: String? = null,
    val dashedValue: List<Double>? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: AxisLineStyle): AxisLineStyle = base.copy(
        show = show ?: base.show,
        size = size ?: base.size,
        color = color ?: base.color,
        dashedValue = dashedValue ?: base.dashedValue,
    )
}

data class AxisTickLineStylePatch(
    val show: Boolean? = null,
    val size: Double? = null,
    val color: String? = null,
    val dashedValue: List<Double>? = null,
    val length: Double? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: AxisTickLineStyle): AxisTickLineStyle = base.copy(
        show = show ?: base.show,
        size = size ?: base.size,
        color = color ?: base.color,
        dashedValue = dashedValue ?: base.dashedValue,
        length = length ?: base.length,
    )
}

data class AxisTickTextStylePatch(
    val show: Boolean? = null,
    val color: String? = null,
    val size: Double? = null,
    val family: String? = null,
    val weight: String? = null,
    val marginStart: Double? = null,
    val marginEnd: Double? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: AxisTickTextStyle): AxisTickTextStyle = base.copy(
        show = show ?: base.show,
        color = color ?: base.color,
        size = size ?: base.size,
        family = family ?: base.family,
        weight = weight ?: base.weight,
        marginStart = marginStart ?: base.marginStart,
        marginEnd = marginEnd ?: base.marginEnd,
    )
}

data class AxisStylePatch(
    val show: Boolean? = null,
    val size: Double? = null,
    val axisLine: AxisLineStylePatch? = null,
    val tickLine: AxisTickLineStylePatch? = null,
    val tickText: AxisTickTextStylePatch? = null,
    val nameTextStyle: StateTextStylePatch? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: AxisStyle): AxisStyle = base.copy(
        show = show ?: base.show,
        size = size ?: base.size,
        axisLine = axisLine?.applyTo(base.axisLine) ?: base.axisLine,
        tickLine = tickLine?.applyTo(base.tickLine) ?: base.tickLine,
        tickText = tickText?.applyTo(base.tickText) ?: base.tickText,
        nameTextStyle = nameTextStyle?.applyTo(base.nameTextStyle) ?: base.nameTextStyle,
    )
}

data class CrosshairDirectionStylePatch(
    val show: Boolean? = null,
    val line: StateLineStylePatch? = null,
    val text: StateTextStylePatch? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: CrosshairDirectionStyle): CrosshairDirectionStyle = base.copy(
        show = show ?: base.show,
        line = line?.applyTo(base.line) ?: base.line,
        text = text?.applyTo(base.text) ?: base.text,
    )
}

data class CrosshairStylePatch(
    val show: Boolean? = null,
    val horizontal: CrosshairDirectionStylePatch? = null,
    val vertical: CrosshairDirectionStylePatch? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: CrosshairStyle): CrosshairStyle = base.copy(
        show = show ?: base.show,
        horizontal = horizontal?.applyTo(base.horizontal) ?: base.horizontal,
        vertical = vertical?.applyTo(base.vertical) ?: base.vertical,
    )
}

data class OverlayPointStylePatch(
    val color: String? = null,
    val borderColor: String? = null,
    val borderSize: Double? = null,
    val radius: Double? = null,
    val activeColor: String? = null,
    val activeBorderColor: String? = null,
    val activeBorderSize: Double? = null,
    val activeRadius: Double? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: OverlayPointStyle): OverlayPointStyle = base.copy(
        color = color ?: base.color,
        borderColor = borderColor ?: base.borderColor,
        borderSize = borderSize ?: base.borderSize,
        radius = radius ?: base.radius,
        activeColor = activeColor ?: base.activeColor,
        activeBorderColor = activeBorderColor ?: base.activeBorderColor,
        activeBorderSize = activeBorderSize ?: base.activeBorderSize,
        activeRadius = activeRadius ?: base.activeRadius,
    )
}

data class OverlayStylePatch(
    val point: OverlayPointStylePatch? = null,
    val line: SmoothLineStylePatch? = null,
    val rect: RectStylePatch? = null,
    val polygon: PolygonStylePatch? = null,
    val circle: PolygonStylePatch? = null,
    val arc: LineStylePatch? = null,
    val text: TextStylePatch? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: OverlayStyle): OverlayStyle = base.copy(
        point = point?.applyTo(base.point) ?: base.point,
        line = line?.applyTo(base.line) ?: base.line,
        rect = rect?.applyTo(base.rect) ?: base.rect,
        polygon = polygon?.applyTo(base.polygon) ?: base.polygon,
        circle = circle?.applyTo(base.circle) ?: base.circle,
        arc = arc?.applyTo(base.arc) ?: base.arc,
        text = text?.applyTo(base.text) ?: base.text,
    )
}

data class SeparatorStylePatch(
    val size: Double? = null,
    val color: String? = null,
    val fill: Boolean? = null,
    val activeBackgroundColor: String? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: SeparatorStyle): SeparatorStyle = base.copy(
        size = size ?: base.size,
        color = color ?: base.color,
        fill = fill ?: base.fill,
        activeBackgroundColor = activeBackgroundColor ?: base.activeBackgroundColor,
    )
}

data class XAxisStylePatch(
    val show: Boolean? = null,
    val size: Double? = null,
    val axisLine: AxisLineStylePatch? = null,
    val tickLine: AxisTickLineStylePatch? = null,
    val tickText: AxisTickTextStylePatch? = null,
    val nameTextStyle: StateTextStylePatch? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: XAxisStyle): XAxisStyle = base.copy(
        show = show ?: base.show,
        size = size ?: base.size,
        axisLine = axisLine?.applyTo(base.axisLine) ?: base.axisLine,
        tickLine = tickLine?.applyTo(base.tickLine) ?: base.tickLine,
        tickText = tickText?.applyTo(base.tickText) ?: base.tickText,
        nameTextStyle = nameTextStyle?.applyTo(base.nameTextStyle) ?: base.nameTextStyle,
    )
}

data class YAxisStylePatch(
    val show: Boolean? = null,
    val size: Double? = null,
    val axisLine: AxisLineStylePatch? = null,
    val tickLine: AxisTickLineStylePatch? = null,
    val tickText: AxisTickTextStylePatch? = null,
    val nameTextStyle: StateTextStylePatch? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: YAxisStyle): YAxisStyle = base.copy(
        show = show ?: base.show,
        size = size ?: base.size,
        axisLine = axisLine?.applyTo(base.axisLine) ?: base.axisLine,
        tickLine = tickLine?.applyTo(base.tickLine) ?: base.tickLine,
        tickText = tickText?.applyTo(base.tickText) ?: base.tickText,
        nameTextStyle = nameTextStyle?.applyTo(base.nameTextStyle) ?: base.nameTextStyle,
    )
}

data class StylesPatch(
    val grid: GridStylePatch? = null,
    val candle: CandleStylePatch? = null,
    val indicator: IndicatorStylePatch? = null,
    val xAxis: XAxisStylePatch? = null,
    val yAxis: YAxisStylePatch? = null,
    val separator: SeparatorStylePatch? = null,
    val crosshair: CrosshairStylePatch? = null,
    val overlay: OverlayStylePatch? = null,
) {
    /** 仅覆盖已提供字段（嵌套递归）。 */
    fun applyTo(base: Styles): Styles = base.copy(
        grid = grid?.applyTo(base.grid) ?: base.grid,
        candle = candle?.applyTo(base.candle) ?: base.candle,
        indicator = indicator?.applyTo(base.indicator) ?: base.indicator,
        xAxis = xAxis?.applyTo(base.xAxis) ?: base.xAxis,
        yAxis = yAxis?.applyTo(base.yAxis) ?: base.yAxis,
        separator = separator?.applyTo(base.separator) ?: base.separator,
        crosshair = crosshair?.applyTo(base.crosshair) ?: base.crosshair,
        overlay = overlay?.applyTo(base.overlay) ?: base.overlay,
    )
}
