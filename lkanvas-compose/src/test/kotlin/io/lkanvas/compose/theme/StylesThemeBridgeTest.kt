package io.lkanvas.compose.theme

import androidx.compose.ui.graphics.toArgb
import io.lkanvas.chart.styles.CandleBarColor
import androidx.compose.ui.graphics.toArgb
import io.lkanvas.chart.styles.CandleBarColorCallback
import io.lkanvas.chart.styles.CandleStyle
import io.lkanvas.chart.styles.Styles
import io.lkanvas.chart.styles.StylesThemes
import io.lkanvas.core.data.Candle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** 批次 5 余量：Styles→ChartTheme 桥接（含 candle.bar 回调）。 */
class StylesThemeBridgeTest {

    private fun candle(ts: Long, close: Double) =
        Candle(ts, close, close + 1, close - 1, close, 100.0)

    @Test
    fun `light主题桥接_默认色板`() {
        val theme = StylesThemeBridge.toTheme(StylesThemes.light)
        assertEquals(0xFF2DC08E.toInt(), theme.upColor.toArgb())
        assertEquals(0xFFF92855.toInt(), theme.downColor.toArgb())
        assertEquals(0xFFEDEDED.toInt(), theme.gridLine.toArgb())
    }

    @Test
    fun `dark主题桥接_色板切换`() {
        val theme = StylesThemeBridge.toTheme(StylesThemes.dark)
        assertEquals(0xFF33353D.toInt(), theme.gridLine.toArgb())
        assertNotEquals(StylesThemeBridge.toTheme(StylesThemes.light).gridLine, theme.gridLine)
    }

    @Test
    fun `candlebar回调重载_按candle求值`() {
        val styles = Styles(
            candle = CandleStyle(
                bar = CandleBarColorCallback { data, prev ->
                    CandleBarColor(
                        upColor = if (prev != null && data.close > prev.close) "#2DC08E" else "#F92855",
                        downColor = "#F92855", noChangeColor = "#76808F",
                    )
                },
            ),
        )
        val data = listOf(candle(1, 10.0), candle(2, 20.0)) // 第 2 根上涨
        val theme = StylesThemeBridge.toTheme(styles, data, dataIndex = 1)
        assertEquals(0xFF2DC08E.toInt(), theme.upColor.toArgb(), "回调按上涨求值 upColor")
    }

    @Test
    fun `candlebar回调重载_空数据回退默认`() {
        val styles = Styles(
            candle = CandleStyle(bar = CandleBarColorCallback { _, _ -> CandleBarColor() }),
        )
        val theme = StylesThemeBridge.toTheme(styles, emptyList(), null)
        assertEquals(0xFF2DC08E.toInt(), theme.upColor.toArgb(), "空数据回退默认绿")
    }

    @Test
    fun `hex解析_6位与8位`() {
        assertEquals(0xFF2DC08E.toInt(), StylesThemeBridge.parse("#2DC08E").toArgb())
        assertEquals(0x332DC08E.toInt(), StylesThemeBridge.parse("#332DC08E").toArgb(), "AA 前缀")
        assertEquals(androidx.compose.ui.graphics.Color.Black, StylesThemeBridge.parse(null))
        assertEquals(androidx.compose.ui.graphics.Color.Black, StylesThemeBridge.parse("#ZZ"))
    }

    @Test
    fun `细粒度字段桥接_开关与颜色`() {
        val theme = StylesThemeBridge.toTheme(StylesThemes.light)
        // 默认值：全部可见 + 网格/十字线/末价线为虚线
        assertTrue(theme.gridShow && theme.priceMarkShow && theme.lastPriceShow)
        assertTrue(theme.crosshairShow && theme.crosshairHorizontalShow && theme.crosshairVerticalShow)
        assertTrue(theme.gridDashed && theme.lastPriceLineDashed && theme.crosshairDashed)
        assertTrue(!theme.indicatorLineDashed)
        assertTrue(theme.lastValueMarkShow)
        assertEquals(0xFF76808F.toInt(), theme.highMarkerColor.toArgb())
        assertEquals(0xFF1677FF.toInt(), theme.areaLineColor.toArgb())
        assertEquals(0xFFDDDDDD.toInt(), theme.separatorColor.toArgb())
    }

    @Test
    fun `细粒度字段桥接_关闭与定制回显`() {
        val styles = Styles(
            grid = io.lkanvas.chart.styles.GridStyle(
                show = false,
                horizontal = io.lkanvas.chart.styles.LineStyle(color = "#111111", style = "solid"),
                vertical = io.lkanvas.chart.styles.LineStyle(color = "#222222", style = "solid"),
            ),
            separator = io.lkanvas.chart.styles.SeparatorStyle(color = "#ABCDEF", size = 3.0),
            crosshair = io.lkanvas.chart.styles.CrosshairStyle(
                show = false,
                horizontal = io.lkanvas.chart.styles.CrosshairDirectionStyle(
                    show = false,
                    line = io.lkanvas.chart.styles.StateLineStyle(color = "#333333", style = "solid", size = 2.0),
                ),
            ),
            indicator = io.lkanvas.chart.styles.IndicatorStyle(
                lines = listOf(io.lkanvas.chart.styles.SmoothLineStyle(color = "#444444", size = 2.5, style = "dashed")),
                lastValueMark = io.lkanvas.chart.styles.IndicatorLastValueMarkStyle(show = false),
            ),
        )
        val theme = StylesThemeBridge.toTheme(styles)
        assertTrue(!theme.gridShow)
        assertEquals(0xFF111111.toInt(), theme.gridLine.toArgb())
        assertTrue(!theme.gridDashed, "style=solid → 实线")
        assertEquals(0xFF222222.toInt(), theme.gridVerticalLine.toArgb())
        assertEquals(0xFFABCDEF.toInt(), theme.separatorColor.toArgb())
        assertEquals(1f, theme.separatorSizePx, "上游 size 为间隙语义，线宽固定细线")
        assertTrue(!theme.crosshairShow)
        assertTrue(!theme.crosshairHorizontalShow)
        assertTrue(!theme.crosshairDashed)
        assertEquals(2f, theme.crosshairLineSizePx)
        assertTrue(theme.indicatorLineDashed)
        assertEquals(2.5f, theme.indicatorLineSizePx)
        assertTrue(!theme.lastValueMarkShow)
    }

}
