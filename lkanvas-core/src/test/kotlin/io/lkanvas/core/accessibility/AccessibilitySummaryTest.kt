package io.lkanvas.core.accessibility

import io.lkanvas.core.data.Candle
import kotlin.test.Test
import kotlin.test.assertTrue

/** A-40 基础：摘要文本包含 OHLCV、方向词与 Missing「—」。 */
class AccessibilitySummaryTest {

    @Test
    fun `candle摘要含OHLCV与方向词`() {
        val c = Candle(1000, 10.0, 11.0, 9.0, 10.5, 1500.0)
        val up = AccessibilitySummary.candleSummary(c, prevClose = 10.2, timeText = "10:30")
        assertTrue(up.contains("10:30"))
        assertTrue(up.contains("开 10"), up)
        assertTrue(up.contains("高 11"))
        assertTrue(up.contains("低 9"))
        assertTrue(up.contains("收 10.5"))
        assertTrue(up.contains("成交量 1500"))
        assertTrue(up.contains("上涨"), "方向词不只靠颜色（R-13）")

        val down = AccessibilitySummary.candleSummary(c, prevClose = 10.8, timeText = "10:30")
        assertTrue(down.contains("下跌"))
        val flat = AccessibilitySummary.candleSummary(c, prevClose = 10.5, timeText = "10:30")
        assertTrue(flat.contains("持平"))
    }

    @Test
    fun `volume缺失不显示成交量段`() {
        val c = Candle(1000, 10.0, 11.0, 9.0, 10.5, null)
        val text = AccessibilitySummary.candleSummary(c, prevClose = null, timeText = "t")
        assertTrue(!text.contains("成交量"))
    }

    @Test
    fun `指标摘要Missing显示破折号`() {
        val text = AccessibilitySummary.indicatorSummary(
            "MA",
            plots = listOf("MA5", "MA10"),
            values = listOf(10.5, Double.NaN),
        )
        assertTrue(text.contains("MA5 10.5"))
        assertTrue(text.contains("MA10 —"), "Missing → 「—」（R-11/R-13）")
    }

    @Test
    fun `图表摘要包含根数与提示`() {
        val text = AccessibilitySummary.chartSummary(selectedText = null, latestText = "收 12.34", candleCount = 500)
        assertTrue(text.contains("500 根"))
        assertTrue(text.contains("上一根"), "提示可用替代动作")
    }
}
