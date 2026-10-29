package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayConstraint
import io.lkanvas.core.overlay.OverlayDefinition

/** O-09 verticalStraightLine：全高垂直线（上游 verticalStraightLine.ts 镜像）。 */
object VerticalStraightLineDefinition : OverlayDefinition {
    override val name = "verticalStraightLine"
    override val pointCount = 1
    override val constraint = OverlayConstraint.VERTICAL

    /** 上游 verticalStraightLine.ts 镜像：全高垂直线。 */
    override fun createPointFigures(
        points: List<io.lkanvas.core.overlay.OverlayPoint>,
        boundingWidth: Double,
        boundingHeight: Double,
        pixelPoints: List<List<Double>>,
        extendData: Map<String, String>,
    ): List<Triple<String, List<List<Double>>, Map<String, String>>>? {
        if (pixelPoints.isEmpty()) return emptyList()
        val bounds = io.lkanvas.core.overlay.OverlayGeometry.Bounds(0.0, 0.0, boundingWidth, boundingHeight)
        val seg = io.lkanvas.core.overlay.OverlayGeometry.verticalLine(pixelPoints[0][0], bounds)
        return listOf(Triple("Line", listOf(listOf(seg.first.x, seg.first.y, seg.second.x, seg.second.y)), emptyMap()))
    }
}
