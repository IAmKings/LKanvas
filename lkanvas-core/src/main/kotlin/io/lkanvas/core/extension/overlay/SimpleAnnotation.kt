package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayDefinition

/** O-14 simpleAnnotation：简单注解（上游 simpleAnnotation.ts 镜像）。 */
object SimpleAnnotationDefinition : OverlayDefinition {
    override val name = "simpleAnnotation"
    override val pointCount = 1
    override val needDefaultPointFigure = false
    override val needDefaultXAxisFigure = false
    override val needDefaultYAxisFigure = false

    /** 上游 simpleAnnotation.ts 镜像：竖线 + 箭头 + extendData 文本。 */
    override fun createPointFigures(
        points: List<io.lkanvas.core.overlay.OverlayPoint>,
        boundingWidth: Double,
        boundingHeight: Double,
        pixelPoints: List<List<Double>>,
        extendData: Map<String, String>,
    ): List<Triple<String, List<List<Double>>, Map<String, String>>>? {
        if (pixelPoints.isEmpty()) return emptyList()
        val text = extendData["text"] ?: ""
        val startX = pixelPoints[0][0]
        val startY = pixelPoints[0][1] - 6
        val lineEndY = startY - 50
        val arrowEndY = lineEndY - 5
        return listOf(
            Triple("Line", listOf(listOf(startX, startY, startX, lineEndY)), emptyMap()),
            Triple(
                "Polygon",
                listOf(
                    listOf(startX, lineEndY),
                    listOf(startX - 4, arrowEndY),
                    listOf(startX + 4, arrowEndY),
                ),
                emptyMap(),
            ),
            Triple("Text", listOf(listOf(startX, arrowEndY)), mapOf("text" to text, "align" to "center", "baseline" to "bottom")),
        )
    }
}
