package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayConstraint
import io.lkanvas.core.overlay.OverlayDefinition

/** O-11 priceChannelLine：价格通道（上游 priceChannelLine.ts 镜像）。 */
object PriceChannelLineDefinition : OverlayDefinition {
    override val name = "priceChannelLine"
    override val pointCount = 3

    /** 上游 priceChannelLine.ts 镜像：主线 + 平行通道线（getParallelLines(count=1) 等价）。 */
    override fun createPointFigures(
        points: List<io.lkanvas.core.overlay.OverlayPoint>,
        boundingWidth: Double,
        boundingHeight: Double,
        pixelPoints: List<List<Double>>,
        extendData: Map<String, String>,
    ): List<Triple<String, List<List<Double>>, Map<String, String>>>? {
        if (pixelPoints.size < 3) return emptyList()
        val bounds = io.lkanvas.core.overlay.OverlayGeometry.Bounds(0.0, 0.0, boundingWidth, boundingHeight)
        val (a, b, c) = pixelPoints
        val segs = io.lkanvas.core.overlay.OverlayGeometry.priceChannel(
            io.lkanvas.core.overlay.OverlayGeometry.P(a[0], a[1]),
            io.lkanvas.core.overlay.OverlayGeometry.P(b[0], b[1]),
            io.lkanvas.core.overlay.OverlayGeometry.P(c[0], c[1]),
            bounds,
        )
        return segs.map { (s, e) -> Triple("Line", listOf(listOf(s.x, s.y, e.x, e.y)), emptyMap()) }
    }
}
