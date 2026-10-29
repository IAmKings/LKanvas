package io.lkanvas.core.figure

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * 基础图形原语（PRD 6.3 上游 figure 7 种，Beta/1.0）：
 * circle / line / polygon / rect / text / arc / path。
 *
 * 全部为不可变数据描述（坐标为 px 空间，由绘制层映射）；
 * 命中测试在 core 内以纯函数实现（JVM 可测）。
 * path 为受控 Kotlin 点序列（不嵌入 SVG/JS 运行环境，PRD R-09）。
 */
sealed interface Figure {

    data class Circle(val centerX: Double, val centerY: Double, val radius: Double) : Figure

    data class Line(val x0: Double, val y0: Double, val x1: Double, val y1: Double) : Figure

    data class Polygon(val points: List<P>) : Figure

    data class Rect(val left: Double, val top: Double, val right: Double, val bottom: Double) : Figure

    data class Text(val x: Double, val y: Double, val content: String, val baselineBottom: Boolean = true) : Figure

    /** 圆弧：中心、半径、起止角（度，0=右，顺时针为正，屏幕坐标系）。 */
    data class Arc(val centerX: Double, val centerY: Double, val radius: Double, val startAngleDeg: Double, val endAngleDeg: Double) : Figure

    /** 受控路径：折线点序列（采样为线段做命中）。 */
    data class Path(val points: List<P>) : Figure
}

data class P(val x: Double, val y: Double)

/** 图形命中测试（PRD R-09：命中能力）。 */
object FigureHit {

    /** 默认命中半径（px）。 */
    const val HIT_RADIUS = 14.0

    fun hit(figure: Figure, x: Double, y: Double, tolerance: Double = HIT_RADIUS): Boolean = when (figure) {
        is Figure.Circle -> {
            val d = hypot(x - figure.centerX, y - figure.centerY)
            abs(d - figure.radius) <= tolerance || d <= figure.radius.coerceAtMost(tolerance)
        }
        is Figure.Line -> distanceToSegment(x, y, P(figure.x0, figure.y0), P(figure.x1, figure.y1)) <= tolerance
        is Figure.Polygon -> pointNearEdges(figure.points, x, y, tolerance) || pointInPolygon(figure.points, x, y)
        is Figure.Rect -> {
            // 面语义：点在矩形（含边界容差）内即命中
            x >= figure.left - tolerance && x <= figure.right + tolerance &&
                y >= figure.top - tolerance && y <= figure.bottom + tolerance
        }
        is Figure.Text -> {
            // 文本命中以锚点邻近近似（精确文本框由绘制层测量后可替换）
            hypot(x - figure.x, y - figure.y) <= tolerance * 2
        }
        is Figure.Arc -> {
            val d = hypot(x - figure.centerX, y - figure.centerY)
            if (abs(d - figure.radius) <= tolerance && withinAngle(figure, x, y)) return true
            // e829f30e（上游 main 2026-10-30 合入）：弧两端点的线宽容差——
            // 角度略越过起止角但距端点 ≤ tolerance 时命中（R14 采纳）
            fun endpoint(axDeg: Double): P =
                P(
                    figure.radius * kotlin.math.cos(Math.toRadians(axDeg)) + figure.centerX,
                    figure.radius * kotlin.math.sin(Math.toRadians(axDeg)) + figure.centerY,
                )
            val startP = endpoint(figure.startAngleDeg)
            val endP = endpoint(figure.endAngleDeg)
            hypot(x - startP.x, y - startP.y) <= tolerance ||
                hypot(x - endP.x, y - endP.y) <= tolerance
        }
        is Figure.Path -> {
            var i = 0
            val n = figure.points.size
            var hit = false
            while (i + 1 < n && !hit) {
                if (distanceToSegment(x, y, figure.points[i], figure.points[i + 1]) <= tolerance) hit = true
                i++
            }
            hit
        }
    }

    private fun withinAngle(arc: Figure.Arc, x: Double, y: Double): Boolean {
        // 全圆/跨度 ≥360°：任意角度均在内（先于取模判定，避免 360%360 坍缩）
        if (abs(arc.endAngleDeg - arc.startAngleDeg) >= 360.0) return true
        var angle = Math.toDegrees(kotlin.math.atan2(y - arc.centerY, x - arc.centerX))
        if (angle < 0) angle += 360.0
        var start = arc.startAngleDeg % 360.0
        var end = arc.endAngleDeg % 360.0
        if (start < 0) start += 360.0
        if (end < 0) end += 360.0
        return if (start <= end) angle in start..end else angle >= start || angle <= end
    }

    /** 点到线段距离。 */
    fun distanceToSegment(px: Double, py: Double, a: P, b: P): Double {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val len2 = abx * abx + aby * aby
        if (len2 == 0.0) return hypot(px - a.x, py - a.y)
        var t = ((px - a.x) * abx + (py - a.y) * aby) / len2
        t = t.coerceIn(0.0, 1.0)
        return hypot(px - (a.x + t * abx), py - (a.y + t * aby))
    }

    /** 点到多边形边的最小距离 ≤ tolerance。 */
    fun pointNearEdges(points: List<P>, x: Double, y: Double, tolerance: Double): Boolean {
        if (points.size < 2) {
            return points.firstOrNull()?.let { hypot(x - it.x, y - it.y) <= tolerance } ?: false
        }
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            if (distanceToSegment(x, y, a, b) <= tolerance) return true
        }
        return false
    }

    /** 射线法点在多边形内。 */
    fun pointInPolygon(points: List<P>, x: Double, y: Double): Boolean {
        if (points.size < 3) return false
        var inside = false
        var j = points.size - 1
        for (i in points.indices) {
            val a = points[i]
            val b = points[j]
            if ((a.y > y) != (b.y > y) &&
                x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /** 弧上采样点（绘制/测试辅助）。 */
    fun arcSample(arc: Figure.Arc, steps: Int = 24): List<P> {
        val out = ArrayList<P>(steps + 1)
        val sweep = arc.endAngleDeg - arc.startAngleDeg
        for (i in 0..steps) {
            val deg = Math.toRadians(arc.startAngleDeg + sweep * i / steps)
            out += P(arc.centerX + arc.radius * cos(deg), arc.centerY + arc.radius * sin(deg))
        }
        return out
    }

    /** 矩形最小边（退化判定用）。 */
    fun rectMinSide(r: Figure.Rect): Double = min(r.right - r.left, r.bottom - r.top)
}

/** 零尺寸/退化输入的合法性与有限性校验（R-09：零长度线、重合点、近垂直须有限几何）。 */
object FigureValidate {

    fun isFinite(f: Figure): Boolean = when (f) {
        is Figure.Circle -> f.centerX.isFinite() && f.centerY.isFinite() && f.radius.isFinite()
        is Figure.Line -> f.x0.isFinite() && f.y0.isFinite() && f.x1.isFinite() && f.y1.isFinite()
        is Figure.Polygon -> f.points.all { it.x.isFinite() && it.y.isFinite() }
        is Figure.Rect -> f.left.isFinite() && f.top.isFinite() && f.right.isFinite() && f.bottom.isFinite()
        is Figure.Text -> f.x.isFinite() && f.y.isFinite()
        is Figure.Arc -> f.centerX.isFinite() && f.centerY.isFinite() && f.radius.isFinite() &&
            f.startAngleDeg.isFinite() && f.endAngleDeg.isFinite()
        is Figure.Path -> f.points.all { it.x.isFinite() && it.y.isFinite() }
    }

    /** 零长度线段/单点多边形等退化输入仍为合法有限几何（允许绘制，命中按点处理）。 */
    fun isDegenerate(f: Figure): Boolean = when (f) {
        is Figure.Line -> f.x0 == f.x1 && f.y0 == f.y1
        is Figure.Polygon -> f.points.size < 3 || f.points.toSet().size < 3
        is Figure.Path -> f.points.size < 2
        is Figure.Circle -> f.radius == 0.0
        is Figure.Rect -> f.left == f.right || f.top == f.bottom
        else -> false
    }
}
