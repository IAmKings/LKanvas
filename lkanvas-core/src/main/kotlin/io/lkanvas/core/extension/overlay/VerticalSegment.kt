package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayConstraint
import io.lkanvas.core.overlay.OverlayDefinition

/** O-08 verticalSegment：垂直线段（上游 verticalSegment.ts 镜像）。 */
object VerticalSegmentDefinition : OverlayDefinition {
    override val name = "verticalSegment"
    override val pointCount = 2
    override val constraint = OverlayConstraint.VERTICAL

    /** 上游 verticalSegment.ts 镜像：两端同 x 的垂直线段。 */
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
