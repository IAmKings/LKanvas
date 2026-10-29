package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayConstraint
import io.lkanvas.core.overlay.OverlayDefinition

/** O-07 verticalRayLine：垂直射线（上游 verticalRayLine.ts 镜像）。 */
object VerticalRayLineDefinition : OverlayDefinition {
    override val name = "verticalRayLine"
    override val pointCount = 2
    override val constraint = OverlayConstraint.VERTICAL

    /** 上游 verticalRayLine.ts 镜像：垂直射线（方向随第二点 y）。 */
    override fun createPointFigures(
        points: List<io.lkanvas.core.overlay.OverlayPoint>,
        boundingWidth: Double,
        boundingHeight: Double,
        pixelPoints: List<List<Double>>,
        extendData: Map<String, String>,
    ): List<Triple<String, List<List<Double>>, Map<String, String>>>? {
        if (pixelPoints.size < 2) return emptyList()
        val bounds = io.lkanvas.core.overlay.OverlayGeometry.Bounds(0.0, 0.0, boundingWidth, boundingHeight)
        val a = pixelPoints[0]
        val dirY = if (a[1] < pixelPoints[1][1]) boundingHeight else 0.0
        val clipped = io.lkanvas.core.overlay.OverlayGeometry.verticalRay(
            io.lkanvas.core.overlay.OverlayGeometry.P(a[0], a[1]), dirY, bounds,
        ) ?: return emptyList()
        return listOf(Triple("Line", listOf(listOf(clipped.first.x, clipped.first.y, clipped.second.x, clipped.second.y)), emptyMap()))
    }
}
