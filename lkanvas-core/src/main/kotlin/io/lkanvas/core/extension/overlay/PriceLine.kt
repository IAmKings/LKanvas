package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayConstraint
import io.lkanvas.core.overlay.OverlayDefinition

/** O-13 priceLine：价格线（上游 priceLine.ts 镜像）。 */
object PriceLineDefinition : OverlayDefinition {
    override val name = "priceLine"
    override val pointCount = 1
    override val constraint = OverlayConstraint.HORIZONTAL

    /** 上游 priceLine.ts 镜像：该点价格处水平线 + 价格标签（文本值经 core Format）。 */
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
        val value = points.firstOrNull()?.value ?: 0.0
        val text = io.lkanvas.core.common.utils.Format.formatPrecision(value, 2)
        return listOf(
            Triple("Line", listOf(listOf(seg.first.x, seg.first.y, seg.second.x, seg.second.y)), emptyMap()),
            Triple("Text", listOf(listOf(pixelPoints[0][0], pixelPoints[0][1])), mapOf("text" to text, "baseline" to "bottom")),
        )
    }
}
