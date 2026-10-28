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

}
