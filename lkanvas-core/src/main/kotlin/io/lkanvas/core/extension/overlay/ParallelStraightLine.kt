package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayConstraint
import io.lkanvas.core.overlay.OverlayDefinition

/** O-10 parallelStraightLine：平行线（上游 parallelStraightLine.ts 镜像）。 */
object ParallelStraightLineDefinition : OverlayDefinition {
    override val name = "parallelStraightLine"
    override val pointCount = 3

    /** 上游 parallelStraightLine.ts 镜像：p0p1 主线 + 过 p2 平行线（复用 OverlayGeometry）。 */
    override fun createPointFigures(
        points: List<io.lkanvas.core.overlay.OverlayPoint>,
        boundingWidth: Double,
        boundingHeight: Double,
        pixelPoints: List<List<Double>>,
        extendData: Map<String, String>,
    ): List<Triple<String, List<List<Double>>, Map<String, String>>>? {
        if (pixelPoints.size < 3) return emptyList()
        val bounds = io.lkanvas.core.overlay.OverlayGeometry.Bounds(0.0, 0.0, boundingWidth, boundingHeight)
        val (a, b, c) = pixelPoints
        val segs = io.lkanvas.core.overlay.OverlayGeometry.parallelGroup(
            io.lkanvas.core.overlay.OverlayGeometry.P(a[0], a[1]),
            io.lkanvas.core.overlay.OverlayGeometry.P(b[0], b[1]),
            io.lkanvas.core.overlay.OverlayGeometry.P(c[0], c[1]),
            bounds,
        )
        return segs.map { (s, e) -> Triple("Line", listOf(listOf(s.x, s.y, e.x, e.y)), emptyMap()) }
    }
}
