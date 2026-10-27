package io.lkanvas.chart.styles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批次 5-2：Styles 默认值对照（上游 getDefaultStyles() + Color 常量基线）。
 * 来源：third_party/klinechart-v10.1.0/src/common/Styles.ts（默认值）与
 * extension/styles/{light,dark}.ts（主题覆盖）。
 */
class StylesDefaultsTest {

    @Test
    fun `根节点8节结构对齐上游`() {
        val s = Styles()
        // 上游 Styles 根：grid, candle, indicator, xAxis, yAxis, separator, crosshair, overlay
        assertNotNullField(s.grid)
        assertNotNullField(s.candle)
        assertNotNullField(s.indicator)
        assertNotNullField(s.xAxis)
        assertNotNullField(s.yAxis)
        assertNotNullField(s.separator)
        assertNotNullField(s.crosshair)
        assertNotNullField(s.overlay)
    }

    private fun assertNotNullField(any: Any) = assertTrue(any != null)

    @Test
    fun `candle默认值对齐上游getDefaultCandleStyle`() {
        val c = CandleStyle()
        assertEquals("candle_solid", c.type)
        val bar = c.bar as CandleBarColor
        assertEquals("current_open", bar.compareRule)
        assertEquals("#2DC08E", bar.upColor, "上游 Color.GREEN")
        assertEquals("#F92855", bar.downColor, "上游 Color.RED")
        assertEquals("#76808F", bar.noChangeColor, "上游 Color.GREY")
        // area
        assertEquals("close", c.area.value)
        assertEquals(2.0, c.area.lineSize)
        // priceMark.last.line 虚线
        assertEquals("dashed", c.priceMark.last.line.style)
        assertEquals(listOf(4.0, 4.0), c.priceMark.last.line.dashedValue)
    }

    @Test
    fun `grid与axis默认色对齐light主题`() {
        assertEquals("#EDEDED", Styles().grid.horizontal.color)
        assertEquals("#DDDDDD", Styles().xAxis.axisLine.color)
        assertEquals("#76808F", Styles().xAxis.tickText.color)
        assertEquals("#DDDDDD", Styles().separator.color)
    }

    @Test
    fun `tooltip默认规则对齐上游`() {
        val t = CandleTooltipStyle()
        assertEquals("always", t.showRule, "TooltipShowRule 默认 always")
        assertEquals("standard", t.showType, "TooltipShowType 默认 standard")
    }

    @Test
    fun `crosshair默认对齐`() {
        val c = CrosshairStyle()
        assertTrue(c.show)
        assertEquals("#76808F", c.horizontal.line.color)
    }
}
