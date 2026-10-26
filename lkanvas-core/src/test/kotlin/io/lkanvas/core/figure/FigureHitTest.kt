package io.lkanvas.core.figure

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 基础图形 7 种：命中测试与退化校验（R-09：零长度/重合/近垂直须有限几何）。 */
class FigureHitTest {

    @Test
    fun `circle圆环与圆心命中`() {
        val c = Figure.Circle(100.0, 100.0, 50.0)
        assertTrue(FigureHit.hit(c, 150.0, 100.0), "圆环右侧")
        assertTrue(FigureHit.hit(c, 100.0, 100.0), "圆心（小半径退化可点）")
        assertFalse(FigureHit.hit(c, 100.0, 120.0), "圆内非圆心不命中")
    }

    @Test
    fun `line零长度退化为点命中`() {
        val seg = Figure.Line(50.0, 50.0, 150.0, 50.0)
        assertTrue(FigureHit.hit(seg, 100.0, 58.0))
        assertFalse(FigureHit.hit(seg, 100.0, 70.0))

        val degenerate = Figure.Line(50.0, 50.0, 50.0, 50.0)
        assertTrue(FigureHit.hit(degenerate, 50.0, 50.0), "零长度按点命中")
        assertTrue(FigureValidate.isDegenerate(degenerate))
        assertTrue(FigureValidate.isFinite(degenerate), "退化仍为有限几何")
    }

    @Test
    fun `polygon边命中与内部命中`() {
        val tri = Figure.Polygon(listOf(P(0.0, 0.0), P(100.0, 0.0), P(50.0, 100.0)))
        assertTrue(FigureHit.hit(tri, 50.0, 0.0), "边上")
        assertTrue(FigureHit.hit(tri, 50.0, 30.0), "内部")
        assertFalse(FigureHit.hit(tri, 90.0, 90.0), "外部")
        assertTrue(FigureHit.hit(tri, 200.0, 200.0) == false)
    }

    @Test
    fun `rect边界与内部命中`() {
        val r = Figure.Rect(10.0, 10.0, 110.0, 60.0)
        assertTrue(FigureHit.hit(r, 10.0, 35.0), "左边")
        assertTrue(FigureHit.hit(r, 60.0, 35.0), "内部")
        assertFalse(FigureHit.hit(r, 200.0, 200.0))
    }

    @Test
    fun `text锚点邻近命中`() {
        val t = Figure.Text(100.0, 100.0, "标签")
        assertTrue(FigureHit.hit(t, 105.0, 102.0))
        assertFalse(FigureHit.hit(t, 200.0, 200.0))
    }

    @Test
    fun `arc圆环且角度范围内命中`() {
        // 0°→90°：右下象限（屏幕坐标 y 向下顺时针）
        val a = Figure.Arc(0.0, 0.0, 100.0, 0.0, 90.0)
        assertTrue(FigureHit.hit(a, 100.0, 50.0) || FigureHit.hit(a, 70.7, 70.7), "弧上 45° 附近")
        assertFalse(FigureHit.hit(a, -100.0, 0.0), "180° 在范围外")
        // 全圆：0→360
        val full = Figure.Arc(0.0, 0.0, 100.0, 0.0, 360.0)
        assertTrue(FigureHit.hit(full, -100.0, 0.0))
    }

    @Test
    fun `path折线采样命中与退化`() {
        val path = Figure.Path(listOf(P(0.0, 0.0), P(100.0, 100.0), P(200.0, 0.0)))
        assertTrue(FigureHit.hit(path, 50.0, 50.0), "第一段中点")
        assertTrue(FigureHit.hit(path, 150.0, 50.0), "第二段中点")
        assertFalse(FigureHit.hit(path, 100.0, 0.0))
        assertTrue(FigureValidate.isDegenerate(Figure.Path(listOf(P(0.0, 0.0)))), "单点退化")
    }

    @Test
    fun `非有限坐标校验`() {
        val bad = Figure.Line(0.0, 0.0, Double.NaN, 10.0)
        assertTrue(!FigureValidate.isFinite(bad), "NaN 坐标判非有限")
        val badPoly = Figure.Polygon(listOf(P(0.0, 0.0), P(1.0, 1.0), P(Double.POSITIVE_INFINITY, 0.0)))
        assertTrue(!FigureValidate.isFinite(badPoly))
    }

    @Test
    fun `距离函数精度`() {
        assertEquals(5.0, FigureHit.distanceToSegment(10.0, 5.0, P(0.0, 0.0), P(10.0, 0.0)), 1e-9)
        assertEquals(0.0, FigureHit.distanceToSegment(5.0, 0.0, P(0.0, 0.0), P(10.0, 0.0)), 1e-9)
        // 超出端点按端点距离
        assertEquals(hypot(5.0, 0.0), FigureHit.distanceToSegment(15.0, 0.0, P(0.0, 0.0), P(10.0, 0.0)), 1e-9)
    }
}
