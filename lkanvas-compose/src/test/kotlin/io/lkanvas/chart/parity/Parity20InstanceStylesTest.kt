package io.lkanvas.chart.parity

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import io.lkanvas.chart.styles.IndicatorBarColors
import io.lkanvas.chart.styles.IndicatorStyleSpec
import io.lkanvas.compose.draw.IndicatorStyleResolver
import io.lkanvas.compose.draw.PaneRenderers.drawInstanceFigures
import io.lkanvas.compose.draw.VisibleRange
import io.lkanvas.compose.theme.ChartTheme
import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.BarColorsContext
import io.lkanvas.core.indicator.ContextPlotStyles
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.IndicatorEngine
import io.lkanvas.core.indicator.IndicatorInstance
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType
import io.lkanvas.core.viewport.Viewport
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * parity-20：实例样式**绘制消费链**差分（0.16.0-beta02，复核文档 §5.2 验收）。
 *
 * 实例 `styles`（IndicatorStyleSpec）经 IndicatorStyleResolver 归一后：
 * 1. `ContextPlotStyles` 行级 provider 收到**实例三色**（不是全局默认）——
 *    内置 VOL/MACD 柱色经此链生效；
 * 2. 无实例样式时 provider 收到全局（ChartTheme 桥接）三色——默认外观不变；
 * 3. LINE 槽 figureColors 被实例索引色覆盖——绘制与 legend 同源；
 * 4. 实例三色的无 provider BAR 符号规则由 resolveBarColors 契约固定。
 */
class Parity20InstanceStylesTest {

    private val received = java.util.concurrent.CopyOnWriteArrayList<BarColorsContext>()

    private fun barTemplate() = object : IndicatorDefinition {
        override val name = "BAR_CTX"
        override val shortName = "BC"
        override val calcParams = emptyList<Double>()
        override val precision = 2
        override val plots = listOf(
            PlotDef(
                "v", "V", PlotType.BAR, baseValue = 0.0,
                styles = ContextPlotStyles { _, _, _, _, barColors ->
                    received += barColors
                    mapOf("color" to (barColors.upColor ?: "#000000"))
                },
            ),
        )
        override fun initScratch(params: List<Double>) = null
        override fun stepScratch(prev: Any?, c: List<Candle>, p: List<Double>, i: Int) = null
        override fun rowFromScratch(s: Any?, c: List<Candle>, p: List<Double>, i: Int) = mapOf("v" to c[i].close)
    }

    private fun candles(n: Int = 3) = (0 until n).map {
        Candle(it * 60_000L, 100.0 + it, 101.0 + it, 99.0 + it, 100.0 + it, 100.0)
    }

    private fun drawFigures(def: IndicatorDefinition, styles: Any?, theme: ChartTheme = ChartTheme()): TestDrawScope {
        received.clear()
        val cs = candles()
        val state = IndicatorEngine.recompute(def, emptyList(), cs)
        val inst = IndicatorInstance("i", def, def.calcParams, "main", "main", styles)
        val fake = TestDrawScope(Size(600f, 600f))
        with(fake) {
            drawInstanceFigures(
                inst, state, Viewport(), VisibleRange(0, cs.size - 1),
                { v -> v }, theme, 0, lastValueMark = false, candles = cs,
            )
        }
        return fake
    }

    @Test
    fun `provider receives instance bar colors not global defaults`() {
        val spec = IndicatorStyleSpec(barColors = IndicatorBarColors("#FF0000", "#00FF00", "#808080"))
        drawFigures(barTemplate(), spec)
        assertTrue(received.size >= candles().size, "provider 应逐行收到上下文")
        for (ctx in received) {
            assertEquals("#FF0000", ctx.upColor)
            assertEquals("#00FF00", ctx.downColor)
            assertEquals("#808080", ctx.noChangeColor)
        }
    }

    @Test
    fun `provider receives styles-configured colors and null slots when unconfigured`() {
        // 默认 Styles() 的 indicator.bars 为空 → 上下文槽位为 null（内置字面量默认生效）
        drawFigures(barTemplate(), null)
        assertTrue(received.isNotEmpty())
        assertEquals(null, received[0].upColor)
        assertEquals(null, received[0].downColor)

        // 全局 styles 显式配置 indicator.bars → 上下文携带配置值（实例未覆盖时生效）
        val styles = io.lkanvas.chart.styles.Styles(
            indicator = io.lkanvas.chart.styles.IndicatorStyle(
                bars = listOf(
                    io.lkanvas.chart.styles.PolygonStyle(color = "#AA0000"),
                    io.lkanvas.chart.styles.PolygonStyle(color = "#00AA00"),
                ),
            ),
        )
        val theme = io.lkanvas.compose.theme.StylesThemeBridge.toTheme(styles)
        drawFigures(barTemplate(), null, theme)
        assertTrue(received.isNotEmpty())
        assertEquals("#AA0000", received[0].upColor)
        assertEquals("#AA0000", received[0].downColor)
    }

    @Test
    fun `instance line colors cycle independently of global palette`() {
        val plots = listOf(
            PlotDef("a", "A", PlotType.LINE),
            PlotDef("b", "B", PlotType.LINE),
        )
        val theme = ChartTheme()
        val inst = IndicatorStyleSpec(lineColors = listOf("#112233"))
        val colors = io.lkanvas.compose.draw.PaneRenderers.figureColors(
            plots, theme, 0, IndicatorStyleResolver.normalize(inst)!!.lineColors,
        )
        assertEquals(androidx.compose.ui.graphics.Color(0xFF112233), colors.getValue("a"))
        // 上游同类型槽循环使用。
        assertEquals(androidx.compose.ui.graphics.Color(0xFF112233), colors.getValue("b"))
    }

    private fun androidx.compose.ui.graphics.Color.toHex(): String = "#%08X".format(toArgb())
}
