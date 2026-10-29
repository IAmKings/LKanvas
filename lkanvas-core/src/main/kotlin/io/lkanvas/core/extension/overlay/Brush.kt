package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayDefinition

/** O-16 brush：毛笔（上游 brush.ts 镜像；连续采样上限 5000 点）。 */
object BrushDefinition : OverlayDefinition {
    override val name = "brush"
    override val pointCount = 0
    override val continuous = true
    override val maxPoints = 5000
    override val supportMagnet = false
    override val totalStep = 2
    override val needDefaultPointFigure = false
    override val needDefaultXAxisFigure = false
    override val needDefaultYAxisFigure = false

    /** 上游 brush.ts 镜像：连续采样折线（Path 原语）。 */
    override fun createPointFigures(
        points: List<io.lkanvas.core.overlay.OverlayPoint>,
        boundingWidth: Double,
        boundingHeight: Double,
        pixelPoints: List<List<Double>>,
        extendData: Map<String, String>,
    ): List<Triple<String, List<List<Double>>, Map<String, String>>>? {
        if (pixelPoints.size < 2) return emptyList()
        return listOf(Triple("Path", pixelPoints.map { p -> listOf(p[0], p[1]) }, emptyMap()))
    }
}
