package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayConstraint
import io.lkanvas.core.overlay.OverlayDefinition

/** O-06 horizontalStraightLine：全宽水平线（上游 horizontalStraightLine.ts 镜像）。 */
object HorizontalStraightLineDefinition : OverlayDefinition {
    override val name = "horizontalStraightLine"
    override val pointCount = 1
    override val constraint = OverlayConstraint.HORIZONTAL

    /** 上游 horizontalStraightLine.ts 镜像：全宽水平线。 */
    override fun createPointFigures(
        points: List<io.lkanvas.core.overlay.OverlayPoint>,
        boundingWidth: Double,
        boundingHeight: Double,
        pixelPoints: List<List<Double>>,
        extendData: Map<String, String>,
    ): List<Triple<String, List<List<Double>>, Map<String, String>>>? {
        if (pixelPoints.isEmpty()) return emptyList()
        val bounds = io.lkanvas.core.overlay.OverlayGeometry.Bounds(0.0, 0.0, boundingWidth, boundingHeight)
        val seg = io.lkanvas.core.overlay.OverlayGeometry.horizontalLine(pixelPoints[0][1], bounds)
        return listOf(Triple("Line", listOf(listOf(seg.first.x, seg.first.y, seg.second.x, seg.second.y)), emptyMap()))
    }
}
