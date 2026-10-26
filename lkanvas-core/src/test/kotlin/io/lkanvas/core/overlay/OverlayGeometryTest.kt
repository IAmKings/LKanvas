package io.lkanvas.core.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-20/T-21/T-22：16 种覆盖物模板登记 + 纯几何（裁剪/射线/平行/斐波那契价格层级）。 */
class OverlayGeometryTest {

    private val bounds = OverlayGeometry.Bounds(0.0, 0.0, 400.0, 300.0)

    // ---------- 模板登记（与上游 totalStep-1 控制点数一致） ----------

    @Test
    fun `16种模板全部登记且控制点数与上游一致`() {
        assertEquals(16, OverlayTemplates.all.size)
        val expectPoints = mapOf(
            "segment" to 2,
            "straightLine" to 2,
            "rayLine" to 2,
            "horizontalRayLine" to 2,
            "horizontalSegment" to 2,
            "horizontalStraightLine" to 1,
            "verticalRayLine" to 2,
            "verticalSegment" to 2,
            "verticalStraightLine" to 1,
            "parallelStraightLine" to 3,
            "priceChannelLine" to 3,
            "fibonacciLine" to 2,
            "priceLine" to 1,
            "simpleAnnotation" to 1,
            "simpleTag" to 1,
            "brush" to 0,
        )
        for ((name, points) in expectPoints) {
            val def = OverlayTemplates[name]
            assertNotNull(def, "模板 $name 已登记")
            assertEquals(points, def.pointCount, "$name 控制点数")
        }
        assertTrue(OverlayTemplates["brush"]!!.continuous)
        assertEquals(5000, OverlayTemplates["brush"]!!.maxPoints, "brush 上限 5000（PRD R-09）")
        assertEquals(OverlayConstraint.HORIZONTAL, OverlayTemplates["horizontalSegment"]!!.constraint)
        assertEquals(OverlayConstraint.VERTICAL, OverlayTemplates["verticalSegment"]!!.constraint)
    }

    // ---------- 直线/射线裁剪 ----------

    @Test
    fun `无限直线双向延伸裁剪到边界`() {
        // 斜线 y=x 贯穿 (0,0)-(400,300)：裁剪结果端点在边界上
        val line = OverlayGeometry.clippedLine(
            OverlayGeometry.P(100.0, 100.0),
            OverlayGeometry.P(200.0, 200.0),
            bounds,
        )
        assertNotNull(line)
        // 线 y=x 与矩形 (0,0,400,300) 的交为 (0,0)-(300,300)
        assertEquals(0.0, line.first.x, 1e-9)
        assertEquals(0.0, line.first.y, 1e-9)
        assertEquals(300.0, line.second.x, 1e-9)
        assertEquals(300.0, line.second.y, 1e-9)
    }

    @Test
    fun `射线单向延伸不越过头端`() {
        // 从 (100,150) 向右的射线：起点在头端
        val ray = OverlayGeometry.clippedRay(
            OverlayGeometry.P(100.0, 150.0),
            OverlayGeometry.P(200.0, 150.0),
            bounds,
        )
        assertNotNull(ray)
        assertEquals(100.0, ray.first.x, 1e-9, "头端不动")
        assertEquals(400.0, ray.second.x, 1e-9, "向右延伸到右边界")
    }

    @Test
    fun `水平射线方向由第二点符号决定`() {
        val p = OverlayGeometry.P(200.0, 100.0)
        val right = OverlayGeometry.horizontalRay(p, dirX = 1.0, bounds = bounds)!!
        assertEquals(400.0, right.second.x, 1e-9)
        val left = OverlayGeometry.horizontalRay(p, dirX = -1.0, bounds = bounds)!!
        assertEquals(0.0, left.second.x, 1e-9)
        assertNull(OverlayGeometry.horizontalRay(p, dirX = 0.0, bounds = bounds), "零方向无射线")
    }

    @Test
    fun `垂直射线方向由第二点符号决定`() {
        val p = OverlayGeometry.P(200.0, 150.0)
        assertEquals(300.0, OverlayGeometry.verticalRay(p, 1.0, bounds)!!.second.y, 1e-9)
        assertEquals(0.0, OverlayGeometry.verticalRay(p, -1.0, bounds)!!.second.y, 1e-9)
    }

    @Test
    fun `水平垂直线段与全线`() {
        val seg = OverlayGeometry.horizontalSegment(50.0, 300.0, 100.0)
        assertEquals(100.0, seg.first.x, 1e-9, "区间有序化")
        assertEquals(300.0, seg.second.x, 1e-9)
        assertEquals(50.0, seg.first.y, 1e-9)
        assertEquals(50.0, seg.second.y, 1e-9, "水平约束同 y")

        val vseg = OverlayGeometry.verticalSegment(60.0, 250.0, 100.0)
        assertEquals(100.0, vseg.first.y, 1e-9)
        assertEquals(250.0, vseg.second.y, 1e-9)
        assertEquals(60.0, vseg.first.x, 1e-9)

        val h = OverlayGeometry.horizontalLine(75.0, bounds)
        assertEquals(0.0, h.first.x, 1e-9)
        assertEquals(400.0, h.second.x, 1e-9)
        val v = OverlayGeometry.verticalLine(75.0, bounds)
        assertEquals(300.0, v.second.y, 1e-9)
    }

    // ---------- 平行线组与价格通道 ----------

    @Test
    fun `平行线组过第三点且斜率一致`() {
        val a = OverlayGeometry.P(0.0, 100.0)
        val b = OverlayGeometry.P(100.0, 200.0) // k=1
        val c = OverlayGeometry.P(50.0, 0.0)
        val group = OverlayGeometry.parallelGroup(a, b, c, bounds)
        assertEquals(2, group.size)
        // 平行线过 c：y = x - 50 → 在 x=50 处 y=0
        val par = group[1]
        assertEquals(0.0, par.first.y, 1e-9, "平行线经过第三点")
    }

    @Test
    fun `垂直退化的平行组为两条竖线`() {
        val a = OverlayGeometry.P(100.0, 50.0)
        val b = OverlayGeometry.P(100.0, 250.0)
        val c = OverlayGeometry.P(200.0, 150.0)
        val group = OverlayGeometry.parallelGroup(a, b, c, bounds)
        assertEquals(2, group.size)
        assertEquals(100.0, group[0].first.x, 1e-9)
        assertEquals(200.0, group[1].first.x, 1e-9)
    }

    @Test
    fun `价格通道为主线加平行线加延伸通道线`() {
        val a = OverlayGeometry.P(0.0, 100.0)
        val b = OverlayGeometry.P(100.0, 200.0)
        val c = OverlayGeometry.P(50.0, 0.0) // b1 = -50，主线 b=100 → 间距 150 → 延伸线 b=-200
        val lines = OverlayGeometry.priceChannel(a, b, c, bounds)
        assertEquals(3, lines.size, "主线+平行线+通道延伸线")
        // 延伸线截距 = 100 + (100 - (-50)) = 250
        val ext = lines[2]
        assertEquals(250.0, ext.first.y, 1e-9)
    }

    // ---------- 斐波那契（D-02：价格空间先算层级） ----------

    @Test
    fun `斐波那契层级在价格空间计算`() {
        val levels = OverlayGeometry.fibonacciLevels(200.0, 100.0, FibonacciLineDefinition.ratios)
        // value = 100 + (200-100)*ratio
        assertEquals(listOf(200.0, 178.6, 161.8, 150.0, 138.2, 123.6, 100.0), levels.map { kotlin.math.round(it * 10) / 10 })
        // 反向取点：p0 < p1
        val reversed = OverlayGeometry.fibonacciLevels(100.0, 200.0, FibonacciLineDefinition.ratios)
        assertEquals(100.0, reversed.first(), 1e-9)
        assertEquals(200.0, reversed.last(), 1e-9)
    }

    // ---------- brush 连续创建生命周期 ----------

    private val events = mutableListOf<OverlayEvent>()
    private val manager = OverlayManager().apply { onEvent = { events += it } }

    @Test
    fun `brush连续采样完成与取消`() {
        val id = manager.beginCreate(BrushDefinition)!!
        manager.addCreatePoint(id, OverlayPoint(100, 10.0))
        manager.addCreatePoint(id, OverlayPoint(101, 10.5))
        manager.addCreatePoint(id, OverlayPoint(102, 11.0))
        assertTrue(!manager.finishCreate("不存在"), "未知 id 拒绝")
        assertTrue(manager.finishCreate(id))
        assertEquals(1, events.count { it is OverlayEvent.Created })
        assertEquals(3, manager.get(id)!!.points.size)
        assertEquals(OverlayPhase.COMPLETED, manager.get(id)!!.phase)
    }

    @Test
    fun `brush点数不足时不能完成`() {
        val id = manager.beginCreate(BrushDefinition)!!
        manager.addCreatePoint(id, OverlayPoint(100, 10.0))
        assertTrue(!manager.finishCreate(id), "单点不构成笔画")
        manager.cancelCreate()
    }

    @Test
    fun `brush达到上限终止采样并恰好报告一次`() {
        // 小上限模板便于测试（真实 brush 为 5000）
        val small = object : OverlayDefinition {
            override val name = "brush-small"
            override val pointCount = 0
            override val continuous = true
            override val maxPoints = 7
        }
        val id = manager.beginCreate(small)!!
        repeat(10) { manager.addCreatePoint(id, OverlayPoint(100L + it, it.toDouble())) }
        assertEquals(1, events.count { it is OverlayEvent.SampleLimitReached }, "恰好一次报告")
        assertEquals(7, manager.get(id)!!.points.size, "超过上限的采样被拒")
        assertTrue(manager.finishCreate(id))
    }
}
