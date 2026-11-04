package io.lkanvas.sample

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import io.lkanvas.chart.styles.IndicatorBarColors
import io.lkanvas.chart.styles.IndicatorStyleSpec
import io.lkanvas.compose.draw.PaneRenderers.drawInstanceFigures
import io.lkanvas.compose.draw.VisibleRange
import io.lkanvas.compose.theme.ChartTheme
import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.BarColorsContext
import io.lkanvas.core.indicator.IndicatorDefinition
import io.lkanvas.core.indicator.IndicatorEngine
import io.lkanvas.core.indicator.IndicatorInstance
import io.lkanvas.core.indicator.PlotDef
import io.lkanvas.core.indicator.PlotType
import io.lkanvas.core.viewport.Viewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 实例样式**像素级**消费证据（0.16.0-beta02，复核文档 §5.3"绘制已消费"）：
 * - 无 provider 的自定义 BAR + 实例三色：按值/基线符号规则上色（正=涨色、负=跌色）；
 * - ContextPlotStyles provider（内置 MACD 同款链路）+ 全局 styles 桥接上下文：
 *   配置色替换默认色；
 * - 实例 LINE 索引色覆盖全局调色板。
 */
class InstanceStylesRasterTest {

    private fun countColor(bitmap: Bitmap, target: Int): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { it == target }
    }

    private fun argb(c: Color): Int = c.toArgb()

    private fun barDef() = object : IndicatorDefinition {
        override val name = "RASTER_BAR"
        override val shortName = "RB"
        override val calcParams = emptyList<Double>()
        override val precision = 2
        override val plots = listOf(PlotDef("v", "V", PlotType.BAR, baseValue = 0.0))
        override fun initScratch(params: List<Double>): Any? = null
        override fun stepScratch(prev: Any?, c: List<Candle>, p: List<Double>, i: Int): Any? = null
        override fun rowFromScratch(s: Any?, c: List<Candle>, p: List<Double>, i: Int) = mapOf("v" to c[i].close)
    }

    private fun render(def: IndicatorDefinition, styles: Any?, theme: ChartTheme = ChartTheme()): Bitmap {
        val candles = listOf(
            Candle(0L, 100.0, 101.0, 99.0, 100.0, 1000.0),
            Candle(60_000L, 100.0, 101.0, 99.0, 100.0, 1000.0),
        )
        val result = IndicatorEngine.recompute(def, emptyList(), candles)
        val instance = IndicatorInstance("r", def, def.calcParams, "main", "main", styles)
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(Canvas(bitmap)), Size(256f, 256f)) {
            // y(v) = 128 - v*10：正值柱在基线上方、负值柱在下方
            drawInstanceFigures(instance, result, Viewport(), VisibleRange(0, 1), { v -> 128.0 - v * 10.0 }, theme, 0, false, candles)
        }
        return bitmap
    }

    @Test fun instanceBarColorsApplySignRuleWithoutProvider() {
        val def = barDef()
        val rows = mapOf("v" to listOf(5.0, -5.0)) // 行 0 正值、行 1 负值
        val signedDef = object : IndicatorDefinition by def {
            override fun rowFromScratch(s: Any?, c: List<Candle>, p: List<Double>, i: Int) = mapOf("v" to rows["v"]!![i])
            override fun calc(candles: List<Candle>, params: List<Double>) = candles.mapIndexed { i, _ -> mapOf("v" to rows["v"]!![i]) }
        }
        val red = Color(0xFFE04545)
        val green = Color(0xFF1FA592)
        val gray = Color(0xFF76808F)
        val bitmap = render(
            signedDef,
            IndicatorStyleSpec(barColors = IndicatorBarColors("#E04545", "#1FA592", "#76808F")),
        )
        assertTrue("正值柱应为实例涨色", countColor(bitmap, argb(red)) > 0)
        assertTrue("负值柱应为实例跌色", countColor(bitmap, argb(green)) > 0)
        // 未出现的零值色不得渲染
        assertEquals("零值色不应出现（无零值行）", 0, countColor(bitmap, argb(gray)))
    }

    @Test fun providerConsumesThemeBridgeContextLikeBuiltins() {
        // MACD 同款链路：PlotDef.styles=ContextPlotStyles；全局 styles 桥接
        // ChartTheme.barColorsContext 提供三色，provider 按值符号取配置色
        val received = mutableListOf<BarColorsContext>()
        val providerDef = object : IndicatorDefinition {
            override val name = "RASTER_CTX"
            override val shortName = "RC"
            override val calcParams = emptyList<Double>()
            override val precision = 2
            override val plots = listOf(
                PlotDef(
                    "v", "V", PlotType.BAR, baseValue = 0.0,
                    styles = io.lkanvas.core.indicator.ContextPlotStyles { _, _, value, _, ctx ->
                        received += ctx
                        mapOf("color" to if (value > 0) ctx.upColor ?: "#2DC08E" else ctx.downColor ?: "#F92855")
                    },
                ),
            )
            override fun initScratch(params: List<Double>): Any? = null
            override fun stepScratch(prev: Any?, c: List<Candle>, p: List<Double>, i: Int): Any? = null
            override fun rowFromScratch(s: Any?, c: List<Candle>, p: List<Double>, i: Int) = mapOf("v" to c[i].close)
        }
        val rows = listOf(5.0, -5.0)
        val signedDef = object : IndicatorDefinition by providerDef {
            override fun calc(candles: List<Candle>, params: List<Double>) = candles.mapIndexed { i, _ -> mapOf("v" to rows[i]) }
        }
        val red = Color(0xFFE04545)
        val green = Color(0xFF1FA592)
        // 桥接形态：全局 styles 显式三色（宿主 A 股配色场景）
        val theme = ChartTheme(barColorsContext = BarColorsContext("#E04545", "#1FA592", "#76808F"))
        val bitmap = render(signedDef, null, theme)
        assertTrue("provider 应逐行收到上下文", received.isNotEmpty())
        assertEquals("#E04545", received[0].upColor)
        assertEquals("#1FA592", received[0].downColor)
        assertTrue("正/负柱应按上下文配置色渲染", countColor(bitmap, argb(red)) > 0 && countColor(bitmap, argb(green)) > 0)
    }

    @Test fun instanceLineColorOverridesPaletteInRaster() {
        val rows = listOf(1.0, 2.0) // 小值保持 y(v)=128-v*10 在画布内
        val lineDef = object : IndicatorDefinition {
            override val name = "RASTER_LINE"
            override val shortName = "RL"
            override val calcParams = emptyList<Double>()
            override val precision = 2
            override val plots = listOf(PlotDef("v", "V", PlotType.LINE))
            override fun initScratch(params: List<Double>): Any? = null
            override fun stepScratch(prev: Any?, c: List<Candle>, p: List<Double>, i: Int): Any? = null
            override fun rowFromScratch(s: Any?, c: List<Candle>, p: List<Double>, i: Int) = mapOf("v" to rows[i])
            override fun calc(candles: List<Candle>, params: List<Double>) = candles.mapIndexed { i, _ -> mapOf("v" to rows[i]) }
        }
        val blue = Color(0xFF2196F3)
        val palette = listOf(Color(0xFFF5A623)) // 全局调色板只有一个橙——实例索引色必须覆盖
        val bitmap = render(lineDef, IndicatorStyleSpec(lineColors = listOf("#2196F3")), ChartTheme(linePalette = palette, indicatorLineSizePx = 3f))
        // 诊断：列出帧内全部非透明颜色
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val colors = pixels.filter { it != 0 }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(6)
        assertTrue("帧内应有绘制内容", colors.isNotEmpty())
        val blueCount = countColor(bitmap, argb(blue))
        val orangeCount = countColor(bitmap, argb(Color(0xFFF5A623)))
        assertTrue("实例线色应替换全局调色板（蓝=$blueCount 橙=$orangeCount colors=$colors）", blueCount > 0)
    }
}
