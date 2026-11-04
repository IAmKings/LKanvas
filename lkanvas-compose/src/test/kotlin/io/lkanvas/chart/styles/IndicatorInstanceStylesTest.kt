package io.lkanvas.chart.styles

import androidx.compose.ui.graphics.toArgb
import io.lkanvas.compose.draw.IndicatorStyleResolver
import io.lkanvas.compose.theme.ChartTheme
import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.BarColorsContext
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.IndicatorInstance
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 指标实例样式归一化与有效三色上下文契约（0.16.0-beta02，复核文档 §5.2）：
 * - 认可形态：IndicatorStyleSpec（显式）、IndicatorStyle（槽位）、旧 List<PolygonStyle>
 *   （每项为一个单色图形槽）；无法识别形态静默为 null；
 * - barColorsContext：实例覆盖全局（ChartTheme 桥接的 indicator.bars）；实例未配置
 *   的槽位由全局兜底；两实例互不污染；
 * - LINE 槽按同类型图形序号循环。
 */
class IndicatorInstanceStylesTest {

    private fun dummyDef() = object : IndicatorDefinition {
        override val name = "DUMMY"
        override val shortName = "D"
        override val calcParams = emptyList<Double>()
        override val precision = 2
        override val plots = listOf(PlotDef("v", "V", PlotType.BAR, baseValue = 0.0))
        override fun initScratch(params: List<Double>) = null
        override fun stepScratch(prev: Any?, c: List<Candle>, p: List<Double>, i: Int) = null
        override fun rowFromScratch(s: Any?, c: List<Candle>, p: List<Double>, i: Int) = mapOf("v" to c[i].close)
    }

    private fun inst(styles: Any?) = IndicatorInstance("i", dummyDef(), emptyList(), "main", "main", styles)

    private fun ctxOf(styles: Any?): BarColorsContext =
        IndicatorStyleResolver.barColorsContext(inst(styles), ChartTheme())

    @Test
    fun `spec form carries explicit three colors over global`() {
        val c = ctxOf(
            IndicatorStyleSpec(barColors = IndicatorBarColors("#FF0000", "#00FF00", "#808080")),
        )
        assertEquals("#FF0000", c.upColor)
        assertEquals("#00FF00", c.downColor)
        assertEquals("#808080", c.noChangeColor)
    }

    @Test
    fun `legacy IndicatorStyle bars preserve per-figure monochrome slots`() {
        val c = ctxOf(
            IndicatorStyle(
                bars = listOf(
                    PolygonStyle(color = "#111111"),
                    PolygonStyle(color = "#222222"),
                    PolygonStyle(color = "#333333"),
                ),
            ),
        )
        assertEquals("#111111", c.upColor)
        assertEquals("#111111", c.downColor)
        assertEquals("#111111", c.noChangeColor)
    }

    @Test
    fun `bare PolygonStyle list preserves first monochrome slot`() {
        // 两个元素分别属于两个 BAR 图形，不是涨跌两个方向。
        val c = ctxOf(listOf(PolygonStyle(color = "#111111"), PolygonStyle(color = "#222222")))
        assertEquals("#111111", c.upColor)
        assertEquals("#111111", c.downColor)
        assertEquals("#111111", c.noChangeColor)
    }

    @Test
    fun `unrecognized form is ignored`() {
        // 无法识别的实例形态静默忽略；默认 ChartTheme 未携带全局显式配置
        // （barColorsContext=null）→ 上下文槽位为 null，内置字面量默认生效
        val c = ctxOf("not-a-style")
        assertNull(c.upColor)
        assertNull(c.downColor)
        assertNull(c.noChangeColor)
    }

    @Test
    fun `unconfigured slots fall back to global theme colors`() {
        // 实例只配了 barColors；上下文仍完整（全局兜底），各槽不混入 null
        val c = ctxOf(
            IndicatorStyleSpec(barColors = IndicatorBarColors("#AA0000", "#00AA00", "#777777")),
        )
        assertEquals("#AA0000", c.upColor)
        assertEquals("#00AA00", c.downColor)
        assertEquals("#777777", c.noChangeColor)
    }

    @Test
    fun `two instances with different specs do not contaminate each other`() {
        val a = inst(IndicatorStyleSpec(barColors = IndicatorBarColors("#FF0000", "#00FF00", "#808080")))
        val b = inst(IndicatorStyleSpec(barColors = IndicatorBarColors("#123456", "#654321", "#ABCDEF")))
        val ca = IndicatorStyleResolver.barColorsContext(a, ChartTheme())
        val cb = IndicatorStyleResolver.barColorsContext(b, ChartTheme())
        assertEquals("#FF0000", ca.upColor)
        assertEquals("#123456", cb.upColor)
        assertEquals("#00FF00", ca.downColor)
        assertEquals("#654321", cb.downColor)
    }

    @Test
    fun `line colors cycle by line index`() {
        val styles = IndicatorStyleSpec(lineColors = listOf("#010203"))
        val n = IndicatorStyleResolver.normalize(styles)!!
        assertEquals(1, n.lineColors.size)
        val fallback = ChartTheme().linePalette[0]
        assertEquals(n.lineColors[0], IndicatorStyleResolver.lineColor(styles, 0, fallback))
        assertEquals(n.lineColors[0], IndicatorStyleResolver.lineColor(styles, 1, fallback))
        assertEquals(fallback, IndicatorStyleResolver.lineColor(null, 0, fallback))
    }

    private fun androidx.compose.ui.graphics.Color.toHex(): String = "#%08X".format(toArgb())
}
