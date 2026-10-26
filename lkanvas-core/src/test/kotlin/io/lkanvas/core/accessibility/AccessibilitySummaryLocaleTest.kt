package io.lkanvas.core.accessibility

import io.lkanvas.core.data.Candle
import io.lkanvas.core.i18n.ChartLocale
import kotlin.test.Test
import kotlin.test.assertTrue

/** A-40/A-35：摘要文本中英文词条化（R-11 本地化扩展）。 */
class AccessibilitySummaryLocaleTest {

    @Test
    fun `中文默认方向词与成交量`() {
        val c = Candle(1000, 10.0, 11.0, 9.0, 10.5, 1500.0)
        val up = AccessibilitySummary.candleSummary(c, prevClose = 10.2, timeText = "10:30")
        assertTrue(up.contains("上涨") && up.contains("成交量 1500"), up)
    }

    @Test
    fun `英文词条完整可用`() {
        val c = Candle(1000, 10.0, 11.0, 9.0, 10.5, 1500.0)
        val en = ChartLocale.EN_US
        val up = AccessibilitySummary.candleSummary(c, prevClose = 10.2, timeText = "10:30", locale = en)
        assertTrue(up.contains("up vs previous candle"), "direction: $up")
        assertTrue(up.contains("Volume 1500"), "volume: $up")
        assertTrue(!up.contains("上涨"))

        val summary = AccessibilitySummary.chartSummary(null, "close 10.50", 500, locale = en)
        assertTrue(summary.contains("Candlestick chart, 500 candles."), "summary: $summary")
        assertTrue(summary.contains("Previous candle"), "actions: $summary")

        val indicator = AccessibilitySummary.indicatorSummary(
            "MA", listOf("MA5", "MA10"), listOf(Double.NaN, 12.0), locale = en,
        )
        assertTrue(indicator.contains("MA5 —"), "missing: $indicator")
        assertTrue(indicator.contains("MA10 12"), "value: $indicator")
    }

    @Test
    fun `方向词模板可整体替换`() {
        val c = Candle(1000, 10.0, 11.0, 9.0, 10.5, 1500.0)
        val custom = ChartLocale(versusPrevTemplate = " | delta %s |", volume = "量")
        val text = AccessibilitySummary.candleSummary(c, prevClose = 10.2, timeText = "t", locale = custom)
        assertTrue(text.contains("delta 上涨"), "模板中的 %s 由方向词填充")
        assertTrue(text.contains("量 1500"))
    }
}
