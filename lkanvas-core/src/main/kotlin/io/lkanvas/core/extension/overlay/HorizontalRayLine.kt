package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayConstraint
import io.lkanvas.core.overlay.OverlayDefinition

/** O-04 horizontalRayLine：水平射线（上游 horizontalRayLine.ts 镜像）。 */
object HorizontalRayLineDefinition : OverlayDefinition {
    override val name = "horizontalRayLine"
    override val pointCount = 2
    override val constraint = OverlayConstraint.HORIZONTAL

    /** 上游 horizontalRayLine.ts 镜像：水平射线（方向随第二点 x）。 */
    override fun createPointFigures(
        points: List<io.lkanvas.core.overlay.OverlayPoint>,
        boundingWidth: Double,
        boundingHeight: Double,
        pixelPoints: List<List<Double>>,
        extendData: Map<String, String>,
    ): List<Triple<String, List<List<Double>>, Map<String, String>>>? {
        if (pixelPoints.isEmpty()) return emptyList()
        val bounds = io.lkanvas.core.overlay.OverlayGeometry.Bounds(0.0, 0.0, boundingWidth, boundingHeight)
        val a = pixelPoints[0]
        val dirX = if (pixelPoints.size >= 2 && a[0] < pixelPoints[1][0]) boundingWidth else 0.0
        val clipped = io.lkanvas.core.overlay.OverlayGeometry.horizontalRay(
            io.lkanvas.core.overlay.OverlayGeometry.P(a[0], a[1]), dirX, bounds,
        ) ?: return emptyList()
        return listOf(Triple("Line", listOf(listOf(clipped.first.x, clipped.first.y, clipped.second.x, clipped.second.y)), emptyMap()))
    }
}
