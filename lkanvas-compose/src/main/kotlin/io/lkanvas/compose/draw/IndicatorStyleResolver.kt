package io.lkanvas.compose.draw

import androidx.compose.ui.graphics.Color
import io.lkanvas.chart.styles.*
import io.lkanvas.compose.theme.ChartTheme
import io.lkanvas.compose.theme.StylesThemeBridge
import io.lkanvas.core.indicator.BarColorsContext
import io.lkanvas.core.indicator.IndicatorInstance
import io.lkanvas.core.indicator.PlotType

/** 上游 prepareIndicatorFigures：各图形类型独立计数，实例数组覆盖全局数组。 */
internal object IndicatorStyleResolver {
    data class Normalized(
        val lineColors: List<Color?> = emptyList(),
        val bars: List<BarColorsContext> = emptyList(),
    )

    fun normalize(styles: Any?): Normalized? = when (styles) {
        is IndicatorStyleSpec -> Normalized(
            styles.lineColors.map(StylesThemeBridge::parse),
            styles.bars.ifEmpty { listOfNotNull(styles.barColors) }.map { BarColorsContext(it.upColor, it.downColor, it.noChangeColor) },
        )
        is IndicatorStyle -> Normalized(
            styles.lines.map { StylesThemeBridge.parse(it.color) },
            barSlots(styles.bars),
        )
        is IndicatorStylePatch -> normalize(styles.applyTo(IndicatorStyle()))
        is Map<*, *> -> Normalized(
            (styles["lines"] as? List<*>)?.map { line ->
                (line as? Map<*, *>)?.get("color")?.let { StylesThemeBridge.parse(it.toString()) }
            }.orEmpty(),
            (styles["bars"] as? List<*>)?.map { bar ->
                val slot = bar as? Map<*, *>
                BarColorsContext(slot?.get("upColor") as? String ?: "#000000", slot?.get("downColor") as? String ?: "#000000", slot?.get("noChangeColor") as? String ?: "#000000")
            }.orEmpty(),
        )
        is List<*> -> Normalized(bars = barSlots(styles.filterIsInstance<PolygonStyle>()))
        else -> null
    }

    fun barSlots(bars: List<PolygonStyle>): List<BarColorsContext> = bars.map {
        if (it.upColor != null || it.downColor != null || it.noChangeColor != null)
            BarColorsContext(it.upColor, it.downColor, it.noChangeColor)
        else BarColorsContext(it.color, it.color, it.color)
    }

    private fun <T> List<T>.slot(index: Int): T? = if (isEmpty()) null else get(index % size)

    fun lineColor(styles: Any?, index: Int, fallback: Color): Color =
        normalize(styles)?.lineColors?.slot(index) ?: fallback

    fun barColorsContext(instance: IndicatorInstance, theme: ChartTheme, index: Int = 0): BarColorsContext =
        barColorsContext(normalize(instance.styles), theme, index)

    private fun barColorsContext(styles: Normalized?, theme: ChartTheme, index: Int): BarColorsContext {
        val local = styles?.bars?.slot(index)
        val global = theme.indicatorBarStyles.slot(index) ?: theme.barColorsContext
        return BarColorsContext(
            local?.upColor ?: global?.upColor,
            local?.downColor ?: global?.downColor,
            local?.noChangeColor ?: global?.noChangeColor,
        )
    }

    fun figureColors(instance: IndicatorInstance, theme: ChartTheme, plots: List<io.lkanvas.core.indicator.PlotDef> = PaneRenderers.figurePlots(instance)): Map<String, Color> {
        val styles = normalize(instance.styles)
        val colors = PaneRenderers.figureColors(plots, theme, 0, styles?.lineColors.orEmpty()).toMutableMap()
        var barIndex = 0
        for (plot in plots) if (plot.type == PlotType.BAR) {
            colors[plot.key] = StylesThemeBridge.parse(barColorsContext(styles, theme, barIndex++).noChangeColor ?: "#76808F")
        }
        return colors
    }
}
