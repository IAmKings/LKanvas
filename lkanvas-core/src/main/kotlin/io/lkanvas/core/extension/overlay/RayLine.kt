package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayConstraint
import io.lkanvas.core.overlay.OverlayDefinition

/** O-03 rayLine：射线（上游 rayLine.ts 镜像）。 */
object RayLineDefinition : OverlayDefinition {
    override val name = "rayLine"
    override val pointCount = 2

    /** 上游 rayLine.ts 镜像：自第一点经第二点单向延伸（bounding 裁剪）。 */
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
        val clipped = io.lkanvas.core.overlay.OverlayGeometry.clippedRay(
            io.lkanvas.core.overlay.OverlayGeometry.P(a[0], a[1]),
            io.lkanvas.core.overlay.OverlayGeometry.P(b[0], b[1]),
            bounds,
        ) ?: return emptyList()
        return listOf(Triple("Line", listOf(listOf(clipped.first.x, clipped.first.y, clipped.second.x, clipped.second.y)), emptyMap()))
    }
}
