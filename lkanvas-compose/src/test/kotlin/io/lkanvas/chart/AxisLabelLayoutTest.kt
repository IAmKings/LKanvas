package io.lkanvas.chart

import io.lkanvas.compose.draw.axisLabelPositions
import org.junit.Test
import org.junit.Assert.*

class AxisLabelLayoutTest {
    @Test fun ascendingValuesWithDescendingScreenCoordinatesKeepAllSeparatedLabels() {
        assertEquals(listOf(2 to 90f, 1 to 190f, 0 to 290f), axisLabelPositions(listOf(300f, 200f, 100f), 20f, 0f, 400f))
    }
    @Test fun reversedAxesAlsoRespectGlyphHeightAndPaneBounds() {
        val positions = axisLabelPositions(listOf(-10f, 20f, 25f, 80f, 110f), 30f, 0f, 100f)
        assertEquals(listOf(1 to 5f, 3 to 65f), positions)
        positions.zipWithNext().forEach { (a, b) -> assertTrue(a.second + 32 <= b.second) }
    }
    @Test fun boundaryLabelsStayVisibleInsideThePane() {
        assertEquals(listOf(0 to 0f, 1 to 40f, 2 to 80f), axisLabelPositions(listOf(0f, 50f, 100f), 20f, 0f, 100f))
    }
    @Test fun aPaneShorterThanOneGlyphDoesNotDrawOutsideItsBounds() {
        assertTrue(axisLabelPositions(listOf(10f), 30f, 0f, 20f).isEmpty())
    }
}
