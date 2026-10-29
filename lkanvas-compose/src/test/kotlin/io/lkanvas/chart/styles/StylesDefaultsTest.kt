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

    /**
     * SLA-016：上游 `extension/styles/light.ts` 全量覆盖路径逐字段 diff
     *（不再只抽查手写常量——覆盖路径全部断言）。
     */
    @Test
    fun `light主题覆盖路径逐字段对齐上游light_ts`() {
        val light = StylesThemes.light
        // grid.horizontal/vertical.color
        assertEquals("#EDEDED", light.grid.horizontal.color)
        assertEquals("#EDEDED", light.grid.vertical.color)
        // candle.priceMark.high/low.color
        assertEquals("#76808F", light.candle.priceMark.high.color)
        assertEquals("#76808F", light.candle.priceMark.low.color)
        // candle.tooltip.rect/title/legend
        assertEquals("#FEFEFE", light.candle.tooltip.rect.color)
        assertEquals("#F2F3F5", light.candle.tooltip.rect.borderColor)
        assertEquals("#76808F", light.candle.tooltip.title.color)
        assertEquals("#76808F", light.candle.tooltip.legend.color)
        // indicator.tooltip.title/legend
        assertEquals("#76808F", light.indicator.tooltip.title.color)
        assertEquals("#76808F", light.indicator.tooltip.legend.color)
        // xAxis/yAxis axisLine/tickText/tickLine
        // xAxis/yAxis 结构一致（分别断言，避免类型推断为 Any）
        assertEquals("#DDDDDD", light.xAxis.axisLine.color)
        assertEquals("#76808F", light.xAxis.tickText.color)
        assertEquals("#DDDDDD", light.xAxis.tickLine.color)
        assertEquals("#DDDDDD", light.yAxis.axisLine.color)
        assertEquals("#76808F", light.yAxis.tickText.color)
        assertEquals("#DDDDDD", light.yAxis.tickLine.color)
        // separator
        assertEquals("#DDDDDD", light.separator.color)
        // crosshair horizontal/vertical line+text
        assertEquals("#76808F", light.crosshair.horizontal.line.color)
        assertEquals("#686D76", light.crosshair.horizontal.text.borderColor)
        assertEquals("#686D76", light.crosshair.horizontal.text.backgroundColor)
        assertEquals("#76808F", light.crosshair.vertical.line.color)
        assertEquals("#686D76", light.crosshair.vertical.text.borderColor)
        assertEquals("#686D76", light.crosshair.vertical.text.backgroundColor)
    }

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
