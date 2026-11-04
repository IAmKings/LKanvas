package io.lkanvas.core.indicator

import io.lkanvas.core.data.Candle
import io.lkanvas.core.extension.indicator.MacdIndicator
import io.lkanvas.core.extension.indicator.VolumeIndicator
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 内置 VOL/MACD 柱色的 BarColorsContext 消费契约（0.16.0-beta02）：
 * - 上下文三色存在时按比较规则取上下文色（VOL=收/开、MACD=值正负、零/缺失=平色）；
 * - 上下文缺省（null）时保持既有字面量默认（全局/实例均未配置的回退语义）。
 */
class BuiltInBarStylesContextTest {

    private val up = Candle(0L, open = 10.0, high = 11.0, low = 9.0, close = 12.0, volume = 1.0) // 收>开
    private val down = Candle(1L, open = 12.0, high = 12.0, low = 9.0, close = 10.0, volume = 1.0) // 收<开
    private val flat = Candle(2L, open = 10.0, high = 11.0, low = 9.0, close = 10.0, volume = 1.0) // 收=开

    private fun volStyles() =
        VolumeIndicator().plotsFor(listOf(5.0, 10.0, 20.0)).last().styles as ContextPlotStyles

    private fun macdStyles() =
        MacdIndicator().plots.first { it.key == "macd" }.styles as ContextPlotStyles

    private fun ctx(up: String?, down: String?, noChange: String?) =
        BarColorsContext(up, down, noChange)

    @Test
    fun `VOL uses context colors by close-open rule`() {
        val s = volStyles()
        val c = ctx("#FF0000", "#00FF00", "#808080")
        assertEquals("#FF0000", s.styles(0, emptyList(), 0.0, listOf(up), c)?.get("color"))
        assertEquals("#00FF00", s.styles(1, emptyList(), 0.0, listOf(up, down), c)?.get("color"))
        assertEquals("#808080", s.styles(2, emptyList(), 0.0, listOf(up, down, flat), c)?.get("color"))
    }

    @Test
    fun `VOL keeps literal defaults when context is empty`() {
        val s = volStyles()
        val none = BarColorsContext()
        assertEquals("#2DC08E", s.styles(0, emptyList(), 0.0, listOf(up), none)?.get("color"))
        assertEquals("#F92855", s.styles(1, emptyList(), 0.0, listOf(up, down), none)?.get("color"))
        assertEquals("#76808F", s.styles(2, emptyList(), 0.0, listOf(up, down, flat), none)?.get("color"))
    }

    @Test
    fun `VOL missing candle falls back to noChange color`() {
        val s = volStyles()
        assertEquals("#808080", s.styles(9, emptyList(), 0.0, emptyList(), ctx(null, null, "#808080"))?.get("color"))
        assertEquals("#76808F", s.styles(9, emptyList(), 0.0, emptyList(), BarColorsContext())?.get("color"))
    }

    @Test
    fun `MACD uses context colors by value sign`() {
        val s = macdStyles()
        val c = ctx("#FF0000", "#00FF00", "#808080")
        assertEquals("#FF0000", s.styles(0, listOf(12.0, 26.0, 9.0), 0.5, emptyList(), c)?.get("color"))
        assertEquals("#00FF00", s.styles(1, listOf(12.0, 26.0, 9.0), -0.5, emptyList(), c)?.get("color"))
        assertEquals("#808080", s.styles(2, listOf(12.0, 26.0, 9.0), 0.0, emptyList(), c)?.get("color"))
    }

    @Test
    fun `MACD keeps literal defaults when context is empty`() {
        val s = macdStyles()
        val none = BarColorsContext()
        assertEquals("#2DC08E", s.styles(0, listOf(12.0, 26.0, 9.0), 0.5, emptyList(), none)?.get("color"))
        assertEquals("#F92855", s.styles(1, listOf(12.0, 26.0, 9.0), -0.5, emptyList(), none)?.get("color"))
        assertEquals("#76808F", s.styles(2, listOf(12.0, 26.0, 9.0), 0.0, emptyList(), none)?.get("color"))
    }

    @Test
    fun `partial context falls back per-slot`() {
        // 仅配置涨色：跌/平槽保持默认
        val s = volStyles()
        val partial = ctx("#FF0000", null, null)
        assertEquals("#FF0000", s.styles(0, emptyList(), 0.0, listOf(up), partial)?.get("color"))
        assertEquals("#F92855", s.styles(1, emptyList(), 0.0, listOf(up, down), partial)?.get("color"))
    }
}
