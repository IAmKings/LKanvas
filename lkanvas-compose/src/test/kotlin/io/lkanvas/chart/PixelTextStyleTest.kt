package io.lkanvas.chart

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import io.lkanvas.compose.draw.ChartText
import org.junit.Test
import kotlin.test.assertEquals

class PixelTextStyleTest {
    @Test fun `canvas pixels are not scaled a second time by text drawing`() {
        for (scale in listOf(1f, 2f)) {
            val density = Density(3f, scale)
            val requestedPx = 36f * scale
            val style = ChartText.pixelTextStyle(requestedPx, density, Color.White)
            assertEquals(requestedPx, with(density) { style.fontSize.toPx() }, 0.01f)
        }
    }
}
