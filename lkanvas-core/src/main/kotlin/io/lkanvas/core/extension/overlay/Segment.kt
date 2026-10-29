package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayConstraint
import io.lkanvas.core.overlay.OverlayDefinition

/** O-01 segment：两点线段（上游 extension/overlay/segment.ts 镜像）。 */
object SegmentDefinition : OverlayDefinition {
    override val name = "segment"
    override val pointCount = 2

    /** 上游 segment.ts createPointFigures 镜像：两点线段（R09：模板=图形生成唯一来源）。 */
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
