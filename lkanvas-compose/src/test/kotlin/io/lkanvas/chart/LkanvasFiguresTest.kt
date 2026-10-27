package io.lkanvas.chart

import io.lkanvas.core.figure.Figure
import io.lkanvas.core.figure.P
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 批次 4-4：figure 注册 API 与 checkCoordinateOn 同名包装（对齐 index.ts utils）。 */
class LkanvasFiguresTest {

    @Test
    fun `7种内置图形预注册且getSupportedFigures完整`() {
        assertEquals(7, LkanvasFigures.getSupportedFigures().size)
        listOf("circle", "line", "polygon", "rect", "text", "arc", "path").forEach {
            assertNotNull(LkanvasFigures.getFigureFactory(it), "内置 $it")
        }
    }

    @Test
    fun `registerFigure同名冲突与自定义注册`() {
        assertTrue(LkanvasFigures.registerFigure("customFig") { Figure.Path(emptyList()) })
        assertFalse(LkanvasFigures.registerFigure("customFig") { Figure.Path(emptyList()) }, "同名冲突")
        assertFalse(LkanvasFigures.registerFigure("line") { Figure.Path(emptyList()) }, "内置名不可覆盖")
        assertEquals(8, LkanvasFigures.getSupportedFigures().size)
    }

    @Test
    fun `checkCoordinateOn六种包装与FigureHit一致`() {
        assertTrue(LkanvasFigures.checkCoordinateOnLine(0.0, 0.0, 100.0, 0.0, 50.0, 2.0))
        assertFalse(LkanvasFigures.checkCoordinateOnLine(0.0, 0.0, 100.0, 0.0, 50.0, 50.0))
        assertTrue(LkanvasFigures.checkCoordinateOnCircle(0.0, 0.0, 50.0, 50.0, 0.0))
        assertTrue(LkanvasFigures.checkCoordinateOnRect(0.0, 0.0, 100.0, 50.0, 0.0, 25.0))
        assertTrue(LkanvasFigures.checkCoordinateOnPolygon(listOf(P(0.0, 0.0), P(100.0, 0.0), P(50.0, 100.0)), 50.0, 30.0))
        assertTrue(LkanvasFigures.checkCoordinateOnArc(0.0, 0.0, 100.0, 0.0, 360.0, -100.0, 0.0))
        assertTrue(LkanvasFigures.checkCoordinateOnText(100.0, 100.0, "标签", 105.0, 102.0))
    }
}
