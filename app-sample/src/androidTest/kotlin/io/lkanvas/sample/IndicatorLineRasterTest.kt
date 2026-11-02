package io.lkanvas.sample

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.DashPathEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import io.lkanvas.compose.draw.PaneRenderers.drawInstanceFigures
import io.lkanvas.compose.draw.VisibleRange
import io.lkanvas.compose.theme.ChartTheme
import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.*
import io.lkanvas.core.viewport.Viewport
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class IndicatorLineRasterTest {
    @Test fun mergedLineRasterMatchesUpstreamDefaultsAcrossGapsStylesAndDashes() {
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("validation"), "indicator-line-raster").apply { mkdirs() }
        val alphaBlue = Color(0x803f51b5)
        for (case in 0..3) {
            val values = if (case == 1) listOf(100.0, 110.0, Double.NaN, 120.0, 130.0) else listOf(100.0, 110.0, 80.0, 120.0)
            val rows = mutableListOf<Int>()
            val definition = object : IndicatorDefinition {
                override val name = "RASTER"
                override val shortName = "R"
                override val calcParams = emptyList<Double>()
                override val precision = 2
                override val plots = listOf(PlotDef("v", "V", PlotType.LINE, styles = if (case == 2) { row: Int ->
                    rows += row
                    mapOf("color" to if (row == 1) "#0000FF" else "#FF0000", "lineWidth" to 1.5)
                } else null))
                override fun initScratch(params: List<Double>): Any? = null
                override fun stepScratch(prev: Any?, c: List<Candle>, p: List<Double>, i: Int): Any? = null
                override fun rowFromScratch(s: Any?, c: List<Candle>, p: List<Double>, i: Int) = mapOf("v" to c[i].close)
            }
            val candles = values.mapIndexed { i, v -> Candle(i * 60000L, v, v, v, v, 1000.0) }
            val result = IndicatorEngine.recompute(definition, emptyList(), candles)
            val instance = IndicatorInstance("raster", definition, emptyList(), "main", "main", null)
            val actual = Bitmap.createBitmap(128, 180, Bitmap.Config.ARGB_8888)
            val expected = Bitmap.createBitmap(128, 180, Bitmap.Config.ARGB_8888)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(Canvas(actual)), Size(128f, 180f)) {
                drawInstanceFigures(instance, result, Viewport(), VisibleRange(0, values.lastIndex), { it }, ChartTheme(linePalette = listOf(alphaBlue), indicatorLineSizePx = 1.5f, indicatorLineDashed = case == 3), 0, false, candles)
            }
            // KLineChart v10.1.0 IndicatorView merges adjacent same-style segments;
            // figure/line.ts uses butt/miter for an ordinary unsmoothed line.
            val referenceCanvas = Canvas(expected)
            fun reference(color: Color, vararg points: Pair<Float, Float>) {
                val path = Path().apply { moveTo(points[0].first, points[0].second); for ((x,y) in points.drop(1)) lineTo(x,y) }
                referenceCanvas.drawPath(path, Paint().apply {
                    this.color = color.toArgb(); strokeWidth = 1.5f; style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.BUTT; strokeJoin = Paint.Join.MITER; strokeMiter = 10f
                    if (case == 3) pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
                })
            }
            when (case) {
                1 -> { reference(alphaBlue, 4f to 100f, 12f to 110f); reference(alphaBlue, 28f to 120f, 36f to 130f) }
                2 -> { reference(Color.Red, 4f to 100f, 12f to 110f); reference(Color.Blue, 12f to 110f, 20f to 80f); reference(Color.Red, 20f to 80f, 28f to 120f) }
                else -> reference(alphaBlue, 4f to 100f, 12f to 110f, 20f to 80f, 28f to 120f)
            }
            for ((name, bitmap) in listOf("actual" to actual, "expected" to expected)) File(output, "$case-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val a = IntArray(128 * 180); val b = IntArray(a.size)
            actual.getPixels(a,0,128,0,0,128,180);expected.getPixels(b,0,128,0,0,128,180)
            actual.recycle();expected.recycle()
            assertArrayEquals("upstream line raster case=$case", b, a)
            if (case == 2) assertEquals("row callback order", listOf(0,1,2), rows)
        }
    }
}
