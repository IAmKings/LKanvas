package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayDefinition

/** O-15 simpleTag：简单标签（上游 simpleTag.ts 镜像）。 */
object SimpleTagDefinition : OverlayDefinition {
    override val name = "simpleTag"
    override val pointCount = 1
    override val needDefaultPointFigure = false
    override val needDefaultXAxisFigure = false
    override val needDefaultYAxisFigure = false

    /** 上游 simpleTag.ts 镜像：全宽水平线 + extendData 文本标签。 */
    override fun createPointFigures(
        points: List<io.lkanvas.core.overlay.OverlayPoint>,
        boundingWidth: Double,
        boundingHeight: Double,
        pixelPoints: List<List<Double>>,
        extendData: Map<String, String>,
    ): List<Triple<String, List<List<Double>>, Map<String, String>>>? {
        if (pixelPoints.isEmpty()) return emptyList()
        val text = extendData["text"] ?: ""
        return listOf(
            Triple("Line", listOf(listOf(0.0, pixelPoints[0][1], boundingWidth, pixelPoints[0][1])), emptyMap()),
            Triple("Text", listOf(listOf(pixelPoints[0][0], pixelPoints[0][1])), mapOf("text" to text, "baseline" to "bottom")),
        )
    }
}
