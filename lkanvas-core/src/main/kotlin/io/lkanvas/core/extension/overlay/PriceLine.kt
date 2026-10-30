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
        // T07（第三轮复审 2026-10-30）：镜像上游 priceLine.ts:38——线段从
        // **锚点**到右边界（修复前全宽 0→width）；价格文本带 rawValue 标记，
        // 由渲染层按品种/指标精度 + thousandsSeparator/decimalFold 格式化
        val (ax, ay) = pixelPoints[0]
        val value = points.firstOrNull()?.value ?: 0.0
        return listOf(
            Triple("Line", listOf(listOf(ax, ay, boundingWidth, ay)), emptyMap()),
            Triple(
                "Text",
                listOf(listOf(ax, ay)),
                mapOf("rawValue" to value.toString(), "baseline" to "bottom"),
            ),
        )
    }
}
