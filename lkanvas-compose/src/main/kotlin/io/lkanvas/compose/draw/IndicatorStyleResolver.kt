package io.lkanvas.compose.draw

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.lkanvas.chart.styles.IndicatorBarColors
import io.lkanvas.chart.styles.IndicatorStyleSpec
import io.lkanvas.compose.theme.ChartTheme
import io.lkanvas.compose.theme.StylesThemeBridge
import io.lkanvas.core.indicator.BarColorsContext
import io.lkanvas.core.indicator.IndicatorInstance

/**
 * 指标实例有效样式解析（复核文档 §5.2 单一入口）：
 *
 * 解析顺序 = **全局 styles（ChartTheme 已含合并）→ 实例覆盖 → figure 动态覆盖**：
 * - [normalize]：实例 `styles: Any?` 的认可形态归一——
 *   `IndicatorStyleSpec`（显式三色/按索引线色）、`IndicatorStyle`（bars/lines/
 *   circles 槽位）、旧 `List<PolygonStyle>`（BAR/CIRCLE 按位 [0]=涨 [1]=跌 [2]=平、
 *   LINE 按 plot 索引）；无法识别的形态按 null 处理（不抛错、不误读）。
 * - LINE：实例 lineColors 按索引覆盖全局调色板；
 * - BAR/CIRCLE：实例三色覆盖全局 macdBar*，再回退内置默认（由 provider 字面量兜底）；
 * - figure 动态（PlotDef.styles/ContextPlotStyles）按行覆盖前两级（内置 VOL/MACD
 *   的 provider 经 [barColorsContext] 消费前两级解析结果）。
 *
 * 图形绘制与 legend/tooltip 共用本对象结果，禁止按指标名称在 renderer 打补丁。
 */
internal object IndicatorStyleResolver {

    /** 归一化实例三色（颜色已解析；hex 供 core BarColorsContext）。 */
    internal data class BarColors(
        val up: Color,
        val down: Color,
        val noChange: Color,
        val upHex: String,
        val downHex: String,
        val noChangeHex: String,
    )

    internal data class Normalized(
        val lineColors: List<Color> = emptyList(),
        val barColors: BarColors? = null,
        val circleColors: BarColors? = null,
    )

    /** 无法识别的形态返回 null（契约：静默忽略，不抛错）。 */
    fun normalize(styles: Any?): Normalized? = when (styles) {
        null -> null
        is IndicatorStyleSpec -> Normalized(
            lineColors = styles.lineColors.map { StylesThemeBridge.parse(it) },
            barColors = styles.barColors?.let { toBarColors(it) },
            circleColors = styles.circleColors?.let { toBarColors(it) },
        )
        is io.lkanvas.chart.styles.IndicatorStyle -> Normalized(
            lineColors = styles.lines.map { StylesThemeBridge.parse(it.color) },
            barColors = positionalBarColors(styles.bars),
            circleColors = positionalBarColors(styles.circles),
        )
        is List<*> -> {
            val polygons = styles.filterIsInstance<io.lkanvas.chart.styles.PolygonStyle>()
            Normalized(barColors = positionalBarColors(polygons))
        }
        else -> null
    }

    private fun toBarColors(b: IndicatorBarColors): BarColors = BarColors(
        up = StylesThemeBridge.parse(b.upColor),
        down = StylesThemeBridge.parse(b.downColor),
        noChange = StylesThemeBridge.parse(b.noChangeColor),
        upHex = b.upColor,
        downHex = b.downColor,
        noChangeHex = b.noChangeColor,
    )

    /** 旧 List<PolygonStyle> 按位归一：[0]=涨 [1]=跌 [2]=平（<2 项时缺失位为 null）。 */
    private fun positionalBarColors(polygons: List<io.lkanvas.chart.styles.PolygonStyle>): BarColors? {
        if (polygons.isEmpty()) return null
        val up = polygons.getOrNull(0)?.color ?: return null
        val down = polygons.getOrNull(1)?.color ?: up
        val noChange = polygons.getOrNull(2)?.color ?: down
        return toBarColors(IndicatorBarColors(up, down, noChange))
    }

    /**
     * LINE 槽有效色：实例 lineColors 按 plot 索引覆盖全局 palette（不足/超出回退）。
     * 与 [PaneRenderers.figureColors] 配合使用。
     */
    fun lineColor(instanceStyles: Any?, plotIndex: Int, fallback: Color): Color {
        val n = normalize(instanceStyles) ?: return fallback
        return n.lineColors.getOrNull(plotIndex) ?: fallback
    }

    /** 全局 BAR 三色（theme.barColorsContext 由桥接层从全局 styles 构造；null=未配置）。 */
    private fun globalBarContext(theme: ChartTheme): BarColorsContext =
        theme.barColorsContext ?: BarColorsContext()

    /**
     * ContextPlotStyles 的柱色上下文：实例三色覆盖全局 styles 显式配置；两级均未
     * 配置的槽位保持 null → 内置默认字面量生效（默认外观不因主题默认色漂移）。
     */
    fun barColorsContext(instance: IndicatorInstance, theme: ChartTheme): BarColorsContext {
        val n = normalize(instance.styles)
        val g = globalBarContext(theme)
        val b = n?.barColors
        val c = n?.circleColors
        return BarColorsContext(
            upColor = b?.upHex ?: c?.upHex ?: g.upColor,
            downColor = b?.downHex ?: c?.downHex ?: g.downColor,
            noChangeColor = b?.noChangeHex ?: c?.noChangeHex ?: g.noChangeColor,
        )
    }

    /** 实例 BAR 槽三色（Color 形态，供无动态 provider 的自定义 BAR 图形回退）。 */
    fun barColors(instance: IndicatorInstance): BarColors? = normalize(instance.styles)?.barColors

    private fun Color.toHex(): String = "#%08X".format(toArgb())
}
