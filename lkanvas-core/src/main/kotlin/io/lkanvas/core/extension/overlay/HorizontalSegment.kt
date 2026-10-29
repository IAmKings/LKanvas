package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayConstraint
import io.lkanvas.core.overlay.OverlayDefinition

/** O-05 horizontalSegment：水平线段（上游 horizontalSegment.ts 镜像）。 */
object HorizontalSegmentDefinition : OverlayDefinition {
    override val name = "horizontalSegment"
    override val pointCount = 2
    override val constraint = OverlayConstraint.HORIZONTAL

    /** 上游 horizontalSegment.ts 镜像：两端同 y 的水平线段。 */
    override fun createPointFigures(
        points: List<io.lkanvas.core.overlay.OverlayPoint>,
        boundingWidth: Double,
        boundingHeight: Double,
        pixelPoints: List<List<Double>>,
        extendData: Map<String, String>,
    ): List<Triple<String, List<List<Double>>, Map<String, String>>>? {
        if (pixelPoints.size < 2) return emptyList()
        val (a, b) = pixelPoints
        return listOf(Triple("Line", listOf(listOf(a[0], a[1], b[0], b[1])), emptyMap()))
    }
}
