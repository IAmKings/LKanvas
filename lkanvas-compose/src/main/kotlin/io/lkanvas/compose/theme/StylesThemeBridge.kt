package io.lkanvas.compose.theme

import io.lkanvas.chart.styles.CandleBarColor
import androidx.compose.ui.graphics.Color
import io.lkanvas.chart.styles.Styles

/**
 * Styles 树 → ChartTheme 桥接（批次 5 余量）：把上游 Styles schema 的
 * 当前值推导为绘制层消费的 ChartTheme 快照。单向（Styles 是数据源），
 * 使 setStyles 的任何合并结果即时反映到渲染。
 */
object StylesThemeBridge {

    /** Styles → ChartTheme。candle.bar 支持 CandleBarColor 或回调产物（批次 5余-3）。 */
    fun toTheme(styles: Styles): ChartTheme = toTheme(styles, null, null)

    /**
     * candle.bar 为回调时的重载：以 dataIndex 处的 candle 求值配色。
     * 回调仅静态求值一次取色板（逐根动态色由绘制层 CandleBarColorCallback 路径处理）。
     */
    fun toTheme(styles: Styles, data: List<io.lkanvas.core.data.Candle>?, dataIndex: Int?): ChartTheme {
        val bar = styles.candle.bar
        val (up, down, noChange) = when (bar) {
            is CandleBarColor -> Triple(bar.upColor, bar.downColor, bar.noChangeColor)
            is io.lkanvas.chart.styles.CandleBarColorCallback -> {
                val candles = data ?: emptyList()
                val i = dataIndex?.coerceIn(candles.indices) ?: candles.lastIndex
                val resolved = if (candles.isEmpty()) null
                else bar.color(candles[i], candles.getOrNull(i - 1))
                Triple(resolved?.upColor ?: "#2DC08E", resolved?.downColor ?: "#F92855", resolved?.noChangeColor ?: "#76808F")
            }

            else -> Triple("#2DC08E", "#F92855", "#76808F")
        }
        return ChartTheme(
            // 背景：上游 Styles 无根背景字段，取 tooltip.rect（light=#FEFEFE）；dark 主题由宿主传 darkStyles
            background = parse(styles.candle.tooltip.rect.color),
            gridLine = parse(styles.grid.horizontal.color),
            axisText = parse(styles.xAxis.tickText.color),
            axisLine = parse(styles.xAxis.axisLine.color),
            crosshairLine = parse(styles.crosshair.horizontal.line.color),
            lastPriceLine = parse(styles.candle.priceMark.last.line.color ?: up),
            upColor = parse(up),
            downColor = parse(down),
            noChangeColor = parse(noChange),
            linePalette = styles.indicator.lines.map { parse(it.color) }.ifEmpty { defaultPalette() },
            macdDif = parse(styles.indicator.lines.getOrNull(0)?.color ?: "#F5A623"),
            macdDea = parse(styles.indicator.lines.getOrNull(1)?.color ?: "#2196F3"),
            macdBarUp = parse(styles.indicator.bars.getOrNull(0)?.color ?: "#2DC08E"),
            macdBarDown = parse(styles.indicator.bars.getOrNull(1)?.color ?: "#EF5350"),
            // 0.16.0-beta02：柱式指标显式三色上下文——保留 styles 的显式性（未配置
            // 槽位为 null，内置指标保持字面量默认），实例 styles 在此之上覆盖
            barColorsContext = io.lkanvas.core.indicator.BarColorsContext(
                upColor = styles.indicator.bars.getOrNull(0)?.color,
                downColor = styles.indicator.bars.getOrNull(1)?.color,
                noChangeColor = styles.indicator.bars.getOrNull(2)?.color,
            ),
            legendText = parse(styles.candle.tooltip.legend.color),
            legendValueUp = parse(up),
            legendValueDown = parse(down),
            legendValueNeutral = parse(styles.candle.tooltip.legend.color),
            overlayLine = parse(styles.overlay.line.color),
            overlayPoint = parse(styles.overlay.point.color),
            overlayText = parse(styles.overlay.text.color),
            // Styles 细粒度消费（批次 5余-2）
            gridShow = styles.grid.show,
            gridVerticalLine = parse(styles.grid.vertical.color),
            gridDashed = styles.grid.horizontal.style == "dashed",
            separatorColor = parse(styles.separator.color),
            // 上游 separator.size 为 pane 间隙（交互热区）语义，非线宽；当前布局无间隙，线宽固定细线
            separatorSizePx = 1f,
            priceMarkShow = styles.candle.priceMark.show,
            highMarkerColor = parse(styles.candle.priceMark.high.color),
            lowMarkerColor = parse(styles.candle.priceMark.low.color),
            lastPriceShow = styles.candle.priceMark.last.show,
            lastPriceLineShow = styles.candle.priceMark.last.line.show,
            lastPriceLineDashed = styles.candle.priceMark.last.line.style == "dashed",
            crosshairShow = styles.crosshair.show,
            crosshairHorizontalShow = styles.crosshair.horizontal.show,
            crosshairVerticalShow = styles.crosshair.vertical.show,
            crosshairDashed = styles.crosshair.horizontal.line.style == "dashed",
            crosshairLineSizePx = styles.crosshair.horizontal.line.size.toFloat().coerceAtLeast(0.5f),
            areaLineColor = parse(styles.candle.area.lineColor),
            areaLineSizePx = styles.candle.area.lineSize.toFloat(),
            indicatorLineSizePx = (styles.indicator.lines.firstOrNull()?.size ?: 1.5).toFloat(),
            indicatorLineDashed = (styles.indicator.lines.firstOrNull()?.style ?: "solid") == "dashed",
            lastValueMarkShow = styles.indicator.lastValueMark.show,
        )
    }

    private fun defaultPalette() = listOf(
        parse("#F5A623"), parse("#9D65C9"), parse("#2196F3"),
        parse("#E91E63"), parse("#00BCD4"), parse("#8BC34A"),
    )

    /** '#RRGGBB[AA]' → Compose Color（null→黑）。 */
    fun parse(hex: String?): Color {
        if (hex == null) return Color.Black
        return parseNonNull(hex)
    }

    /** '#RRGGBB[AA]' → Compose Color。 */
    private fun parseNonNull(hex: String): Color {
        val h = hex.removePrefix("#")
        return when (h.length) {
            6 -> Color(
                red = h.substring(0, 2).toInt(16) / 255f,
                green = h.substring(2, 4).toInt(16) / 255f,
                blue = h.substring(4, 6).toInt(16) / 255f,
                alpha = 1f,
            )

            8 -> Color(
                red = h.substring(2, 4).toInt(16) / 255f,
                green = h.substring(4, 6).toInt(16) / 255f,
                blue = h.substring(6, 8).toInt(16) / 255f,
                alpha = h.substring(0, 2).toInt(16) / 255f,
            )

            else -> Color.Black
        }
    }
}
