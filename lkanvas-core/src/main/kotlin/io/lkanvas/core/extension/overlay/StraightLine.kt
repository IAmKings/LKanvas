package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayConstraint
import io.lkanvas.core.overlay.OverlayDefinition

/** O-02 straightLine：两点确定的无限直线（上游 straightLine.ts 镜像）。 */
object StraightLineDefinition : OverlayDefinition {
    override val name = "straightLine"
    override val pointCount = 2

    /** 上游 straightLine.ts 镜像：两点确定的无限直线（bounding 裁剪，复用 OverlayGeometry）。 */
    override fun createPointFigures(
        points: List<io.lkanvas.core.overlay.OverlayPoint>,
        boundingWidth: Double,
        boundingHeight: Double,
        pixelPoints: List<List<Double>>,
        extendData: Map<String, String>,
    ): List<Triple<String, List<List<Double>>, Map<String, String>>>? {
        if (pixelPoints.size < 2) return emptyList()
        val bounds = io.lkanvas.core.overlay.OverlayGeometry.Bounds(0.0, 0.0, boundingWidth, boundingHeight)
        val (a, b) = pixelPoints
        val clipped = io.lkanvas.core.overlay.OverlayGeometry.clippedLine(
            io.lkanvas.core.overlay.OverlayGeometry.P(a[0], a[1]),
            io.lkanvas.core.overlay.OverlayGeometry.P(b[0], b[1]),
            bounds,
        ) ?: return emptyList()
        return listOf(Triple("Line", listOf(listOf(clipped.first.x, clipped.first.y, clipped.second.x, clipped.second.y)), emptyMap()))
    }
}
