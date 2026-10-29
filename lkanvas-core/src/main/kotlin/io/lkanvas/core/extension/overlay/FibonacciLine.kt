package io.lkanvas.core.extension.overlay

import io.lkanvas.core.overlay.OverlayDefinition

/** O-12 fibonacciLine：斐波那契（上游 fibonacciLine.ts 镜像；比例与上游一致，层级投影差异 D-02）。 */
object FibonacciLineDefinition : OverlayDefinition {
    override val name = "fibonacciLine"
    override val pointCount = 2
    /** 上游 fibonacciLine.ts 的固定比例（自高点到低点）。 */
    val ratios: List<Double> = listOf(1.0, 0.786, 0.618, 0.5, 0.382, 0.236, 0.0)
}
