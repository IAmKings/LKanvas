package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayDefinition

/** O-12 fibonacciLine：斐波那契（上游 fibonacciLine.ts 镜像；比例与上游一致，层级投影差异 D-02）。 */
object FibonacciLineDefinition : OverlayDefinition {
    override val name = "fibonacciLine"
    override val pointCount = 2
    /** 上游 fibonacciLine.ts 的固定比例（自高点到低点）。 */
    val ratios: List<Double> = listOf(1.0, 0.786, 0.618, 0.5, 0.382, 0.236, 0.0)

    /** 上游 fibonacciLine.ts 镜像（D-02：价格空间层级→投影）：层级横线 + 「价格 (xx.x%)」文本。 */
    override fun createPointFigures(
        points: List<io.lkanvas.core.overlay.OverlayPoint>,
        boundingWidth: Double,
        boundingHeight: Double,
        pixelPoints: List<List<Double>>,
        extendData: Map<String, String>,
    ): List<Triple<String, List<List<Double>>, Map<String, String>>>? {
        if (points.size < 2 || pixelPoints.size < 2) return emptyList()
        val bounds = io.lkanvas.core.overlay.OverlayGeometry.Bounds(0.0, 0.0, boundingWidth, boundingHeight)
        val levels = io.lkanvas.core.overlay.OverlayGeometry.fibonacciLevels(points[0].value, points[1].value, ratios)
        val figures = ArrayList<Triple<String, List<List<Double>>, Map<String, String>>>()
        for ((i, value) in levels.withIndex()) {
            // FibLevel：数据空间标记（value + 标签）——渲染层经 pane 轴投影 y
            val label = io.lkanvas.core.common.utils.Format.formatPrecision(value, 2) + " (" + (ratios[i] * 100) + "%)"
            figures += Triple(
                "FibLevel",
                listOf(listOf(i.toDouble(), value)),
                mapOf("label" to label),
            )
        }
        @Suppress("UNCHECKED_CAST")
        return figures as List<Triple<String, List<List<Double>>, Map<String, String>>>
    }
}
