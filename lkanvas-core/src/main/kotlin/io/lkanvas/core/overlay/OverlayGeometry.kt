package io.lkanvas.core.overlay

import kotlin.math.abs

/**
 * 覆盖物纯几何（PRD R-09）：在平面（像素或数据空间皆可）内计算线族显示端点。
 * 无状态、任意线程可调用；JVM 测试直接覆盖。
 */
object OverlayGeometry {

    /** 平面点。 */
    data class P(val x: Double, val y: Double)

    /** 矩形边界（内容区）。 */
    data class Bounds(val left: Double, val top: Double, val right: Double, val bottom: Double) {
        val width: Double get() = right - left
        val height: Double get() = bottom - top
    }

    /**
     * 无限直线 a-b 与边界矩形的交线：以超大线段近似无限，再做 Liang-Barsky 线段裁剪。
     * 与矩形无交返回 null；退化（a==b）返回 null。
     */
    fun clippedLine(a: P, b: P, bounds: Bounds): Pair<P, P>? {
        val dx = b.x - a.x
        val dy = b.y - a.y
        if (dx == 0.0 && dy == 0.0) return null
        val far = 1e9
        val start = P(a.x - dx * far, a.y - dy * far)
        val end = P(a.x + dx * far, a.y + dy * far)
        return clipSegment(start, end, bounds)
    }

    /** 线段裁剪（Liang-Barsky，约束 p·t ≤ q）；无交返回 null。 */
    fun clipSegment(a: P, b: P, bounds: Bounds): Pair<P, P>? {
        val dx = b.x - a.x
        val dy = b.y - a.y
        var t0 = 0.0
        var t1 = 1.0
        fun clip(p: Double, q: Double): Boolean {
            if (p == 0.0) return q >= 0
            val r = q / p
            if (p < 0) {
                if (r > t1) return false
                if (r > t0) t0 = r
            } else {
                if (r < t0) return false
                if (r < t1) t1 = r
            }
            return true
        }
        // left ≤ x → -dx·t ≤ a.x-left；x ≤ right → dx·t ≤ right-a.x（y 同理）
        if (!clip(-dx, a.x - bounds.left)) return null
        if (!clip(dx, bounds.right - a.x)) return null
        if (!clip(-dy, a.y - bounds.top)) return null
        if (!clip(dy, bounds.bottom - a.y)) return null
        val p0 = P(a.x + dx * t0, a.y + dy * t0)
        val p1 = P(a.x + dx * t1, a.y + dy * t1)
        return p0 to p1
    }

    /** 射线：自 [from] 经 [through] 单向无限延伸，与边界裁剪。 */
    fun clippedRay(from: P, through: P, bounds: Bounds): Pair<P, P>? {
        val dx = through.x - from.x
        val dy = through.y - from.y
        if (dx == 0.0 && dy == 0.0) return null
        val far = 1e9
        val end = P(from.x + dx * far, from.y + dy * far)
        return clipSegment(from, end, bounds)
    }

    /** 全宽水平线（y 处）。 */
    fun horizontalLine(y: Double, bounds: Bounds): Pair<P, P> =
        P(bounds.left, y) to P(bounds.right, y)

    /** 全高垂直线（x 处）。 */
    fun verticalLine(x: Double, bounds: Bounds): Pair<P, P> =
        P(x, bounds.top) to P(x, bounds.bottom)

    /** 水平线段（y 处，x 区间 [x0,x1] 有序化）。 */
    fun horizontalSegment(y: Double, x0: Double, x1: Double): Pair<P, P> =
        P(minOf(x0, x1), y) to P(maxOf(x0, x1), y)

    /** 垂直线段（x 处，y 区间有序化）。 */
    fun verticalSegment(x: Double, y0: Double, y1: Double): Pair<P, P> =
        P(x, minOf(y0, y1)) to P(x, maxOf(y0, y1))

    /** 水平射线：自 [from] 沿 dirX 方向（dirX>0 向右 / <0 向左 / =0 无）。 */
    fun horizontalRay(from: P, dirX: Double, bounds: Bounds): Pair<P, P>? {
        if (dirX == 0.0) return null
        val target = if (dirX > 0) P(bounds.right + 1, from.y) else P(bounds.left - 1, from.y)
        return clippedRay(from, target, bounds)
    }

    /** 垂直射线：自 [from] 沿 dirY 方向。 */
    fun verticalRay(from: P, dirY: Double, bounds: Bounds): Pair<P, P>? {
        if (dirY == 0.0) return null
        val target = if (dirY > 0) P(from.x, bounds.bottom + 1) else P(from.x, bounds.top - 1)
        return clippedRay(from, target, bounds)
    }

    /**
     * 过 [through] 的直线，平行于 a-b（上游 getLinearSlopeIntercept 口径）。
     * 垂直退化（a.x==b.x）：返回过 through.x 的垂直线。
     */
    fun parallelLine(a: P, b: P, through: P, bounds: Bounds): Pair<P, P>? {
        if (a.x == b.x) return verticalLine(through.x, bounds)
        val k = (b.y - a.y) / (b.x - a.x)
        val intercept = through.y - k * through.x
        // 用直线上远离的两点裁剪（斜率大时端点在矩形外也能正确相交）
        val far = 1e9
        val p0 = P(through.x - far, through.y - k * far)
        val p1 = P(through.x + far, through.y + k * far)
        return clipSegment(p0, p1, bounds)
    }

    /**
     * 平行通道（上游 getParallelLines count=1，priceChannelLine）：
     * 主线（a-b 裁剪）+ 过 through 的平行线 + 沿主/平线间距再延伸 1 条。
     */
    fun priceChannel(a: P, b: P, through: P, bounds: Bounds): List<Pair<P, P>> {
        val main = clippedLine(a, b, bounds)
        val parallel = parallelLine(a, b, through, bounds)
        val out = mutableListOf<Pair<P, P>>()
        if (main != null) out += main
        if (parallel != null) {
            out += parallel
            if (a.x == b.x) {
                // 垂直退化：间距为 x 差，延伸一条
                val d = a.x - through.x
                out += verticalLine(a.x + d, bounds)
            } else {
                val k = (b.y - a.y) / (b.x - a.x)
                val bMain = a.y - k * a.x
                val bPar = through.y - k * through.x
                val bNext = bMain + (bMain - bPar)
                val far = 1e9
                val ref = P(0.0, bNext)
                val ext = clipSegment(P(ref.x - far, ref.y - k * far), P(ref.x + far, ref.y + k * far), bounds)
                if (ext != null) out += ext
            }
        }
        return out
    }

    /**
     * 斐波那契层级值（价格空间计算，D-02：先算数据值再投影到轴）。
     * 上游口径：value = p1 + (p0 - p1) * ratio。
     */
    fun fibonacciLevels(p0Value: Double, p1Value: Double, ratios: List<Double>): List<Double> =
        ratios.map { p1Value + (p0Value - p1Value) * it }

    /** 平行线组（parallelStraightLine：主线 + 过 through 平行线，垂直退化画两条竖线）。 */
    fun parallelGroup(a: P, b: P, through: P, bounds: Bounds): List<Pair<P, P>> {
        val out = mutableListOf<Pair<P, P>>()
        if (a.x == b.x) {
            out += verticalLine(a.x, bounds)
            out += verticalLine(through.x, bounds)
        } else {
            clippedLine(a, b, bounds)?.let { out += it }
            parallelLine(a, b, through, bounds)?.let { out += it }
        }
        return out
    }
}
