package io.lkanvas.core.figure

import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * 演练 5（R13/R14）：上游真实补丁 e829f30e「hit-test arc figure by angular sweep」
 * 的移植回归——before/after 证据（修复前失败、应用本地最小 diff 后通过）。
 *
 * 审计复现：半径 100、0°→90°，点位于 -0.5°（距起点约 0.873px），
 * 上游新函数命中 true；本地（角扫描无端点容差）2px 容差下 false。
 */
class ArcEndpointDrillTest {

    private val tolerance = 2.0

    @Test
    fun `端点容差_起点外负角度命中`() {
        // 点：半径 100、角度 -0.5°（= 359.5°），距起点 (100, 0) 约 0.873px
        val angle = Math.toRadians(-0.5)
        val x = 100.0 * kotlin.math.cos(angle)
        val y = 100.0 * kotlin.math.sin(angle)
        val dist = kotlin.math.hypot(x - 100.0, y - 0.0)
        println("DRILL before-check: point=(${"%.3f".format(x)}, ${"%.3f".format(y)}) dist-to-start=${"%.3f".format(dist)}")
        assertTrue(abs(dist - 0.873) < 0.01, "复现点距起点 0.873px")
        // 补丁后预期：命中（端点容差）；修复前：withinAngle(359.5 ∉ 0..90) → false
        assertTrue(
            FigureHit.hit(Figure.Arc(0.0, 0.0, 100.0, 0.0, 90.0), x, y, tolerance),
            "e829f30e 端点容差命中（after）",
        )
    }

    @Test
    fun `端点容差_终点外侧命中且弧外点不误命中`() {
        // 终点 90° 外侧 +0.5°
        val angle = Math.toRadians(90.5)
        val x = 100.0 * kotlin.math.cos(angle)
        val y = 100.0 * kotlin.math.sin(angle)
        assertTrue(
            FigureHit.hit(Figure.Arc(0.0, 0.0, 100.0, 0.0, 90.0), x, y, tolerance),
            "终点外侧容差命中",
        )
        // 弧中部角度外侧 45°、半径外 5px（>容差）→ 不命中
        val mid = Math.toRadians(45.0)
        val mx = 105.0 * kotlin.math.cos(mid)
        val my = 105.0 * kotlin.math.sin(mid)
        assertFalse(
            FigureHit.hit(Figure.Arc(0.0, 0.0, 100.0, 0.0, 90.0), mx, my, tolerance),
            "半径带外不误命中",
        )
        // 180°（远离端点）弧外点 → 不命中
        val far = 100.0 * kotlin.math.cos(Math.toRadians(180.0))
        assertFalse(
            FigureHit.hit(Figure.Arc(0.0, 0.0, 100.0, 0.0, 90.0), far, 0.0, tolerance),
            "远离端点的角度外不命中",
        )
    }
}
