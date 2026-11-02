package io.lkanvas.sample

import android.graphics.BitmapFactory
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.lkanvas.compose.export.ChartSnapshotExporter
import io.lkanvas.compose.state.ChartState
import io.lkanvas.core.data.Candle
import io.lkanvas.core.store.MarketKey
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class PngExportTest {
    @Test fun androidPngEncodesARealChartWithoutChangingItsLayout() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val state = ChartState(scope)
        try {
            runBlocking(Dispatchers.Main) {
                state.reset(MarketKey("PNG", "1m"), (0 until 200).map {
                    val close = 100.0 + it % 9
                    Candle(it * 60000L, 103.0, maxOf(104.0, close), minOf(102.0, close), close, 1000.0)
                })
                state.onLayout(900f, ChartState.PaneHeights(800f, 200f, 200f, 60f))
            }
            val oldWidth = state.viewport.contentWidth
            val oldPanes = state.paneHeightsPx
            val output = ByteArrayOutputStream()
            val measurer = TextMeasurer(createFontFamilyResolver(context), Density(3f, 2f), LayoutDirection.Ltr, 256)
            runBlocking(Dispatchers.Main) {
                ChartSnapshotExporter.exportPng(state, measurer, output, widthPx = 1080, heightPx = 1600)
            }
            val bytes = output.toByteArray()
            assertTrue(bytes.take(8).toByteArray().contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)))
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertEquals(1080, bitmap.width); assertEquals(1600, bitmap.height)
            val colors = mutableSetOf<Int>()
            for (x in 0 until bitmap.width step 13) for (y in 0 until bitmap.height step 13) colors += bitmap.getPixel(x, y)
            assertTrue("PNG must contain chart content", colors.size > 10)
            assertEquals(oldWidth, state.viewport.contentWidth, 0.01)
            assertEquals(oldPanes, state.paneHeightsPx)
            File(context.getExternalFilesDir("validation"), "export.png").writeBytes(bytes)
            bitmap.recycle()
        } finally { scope.cancel() }
    }
}
