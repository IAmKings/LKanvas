package io.lkanvas.sample

import io.lkanvas.core.overlay.OverlayDefinition
import io.lkanvas.core.overlay.OverlayPoint

/**
 * figure 宿主自定义模板样例（T-30 余量收口）：circle/polygon/rect/arc 四种，
 * 经 `createPointFigures` 钩子返回 Figure 原语描述——渲染层把控制点换算成
 * 像素坐标（pixelPoints，上游 coordinates 语义）传入，映射 FigureRenderer 绘制。
 *
 * 控制点约定（两点定界类）：p0 与 p1 的像素坐标构成几何参数；
 * 编程式创建（createOverlay 带 points）后自动完成。
 */
object FigureSamples {

    /** 宿主注册（onCreate 调用一次；同名重复注册由 replace=false 保护）。 */
    fun registerAll() {
        val registry = io.lkanvas.chart.LkanvasRegistry
        registry.registerOverlay(CircleSample)
        registry.registerOverlay(RectSample)
        registry.registerOverlay(PolygonSample)
        registry.registerOverlay(ArcSample)
    }

    /** circle：p0 与 p1 的像素坐标——圆心=中点，半径=距离一半。 */
    object CircleSample : OverlayDefinition {
        override val name = "sample_circle"
        override val pointCount = 2

        override fun createPointFigures(
            points: List<OverlayPoint>,
            boundingWidth: Double,
            boundingHeight: Double,
            pixelPoints: List<List<Double>>,
        ): List<Triple<String, List<List<Double>>, Map<String, String>>> {
            val a = pixelPoints.getOrNull(0) ?: return emptyList()
            val b = pixelPoints.getOrNull(1) ?: return emptyList()
            val cx = (a[0] + b[0]) / 2
            val cy = (a[1] + b[1]) / 2
            val r = kotlin.math.hypot(b[0] - a[0], b[1] - a[1]) / 2
            return listOf(
                Triple("Circle", listOf(listOf(cx, cy, r)), mapOf("color" to "#9D65C9", "lineWidth" to "2")),
            )
        }
    }

    /** rect：p0/p1 像素坐标为对角。 */
    object RectSample : OverlayDefinition {
        override val name = "sample_rect"
        override val pointCount = 2

        override fun createPointFigures(
            points: List<OverlayPoint>,
            boundingWidth: Double,
            boundingHeight: Double,
            pixelPoints: List<List<Double>>,
        ): List<Triple<String, List<List<Double>>, Map<String, String>>> {
            val a = pixelPoints.getOrNull(0) ?: return emptyList()
            val b = pixelPoints.getOrNull(1) ?: return emptyList()
            return listOf(
                Triple(
                    "Rect",
                    listOf(
                        listOf(kotlin.math.min(a[0], b[0])),
                        listOf(kotlin.math.min(a[1], b[1])),
                        listOf(kotlin.math.max(a[0], b[0])),
                        listOf(kotlin.math.max(a[1], b[1])),
                    ),
                    mapOf("color" to "#2DC08E", "lineWidth" to "2"),
                ),
            )
        }
    }

    /** polygon：逐点像素坐标。 */
    object PolygonSample : OverlayDefinition {
        override val name = "sample_polygon"
        override val pointCount = 4

        override fun createPointFigures(
            points: List<OverlayPoint>,
            boundingWidth: Double,
            boundingHeight: Double,
            pixelPoints: List<List<Double>>,
        ): List<Triple<String, List<List<Double>>, Map<String, String>>> =
            listOf(
                Triple(
                    "Polygon",
                    pixelPoints,
                    mapOf("color" to "#F5A623", "lineWidth" to "2"),
                ),
            )
    }

    /** arc：p0/p1 定圆心半径，起止角 0°~270° 演示。 */
    object ArcSample : OverlayDefinition {
        override val name = "sample_arc"
        override val pointCount = 2

        override fun createPointFigures(
            points: List<OverlayPoint>,
            boundingWidth: Double,
            boundingHeight: Double,
            pixelPoints: List<List<Double>>,
        ): List<Triple<String, List<List<Double>>, Map<String, String>>> {
            val a = pixelPoints.getOrNull(0) ?: return emptyList()
            val b = pixelPoints.getOrNull(1) ?: return emptyList()
            val cx = (a[0] + b[0]) / 2
            val cy = (a[1] + b[1]) / 2
            val r = kotlin.math.hypot(b[0] - a[0], b[1] - a[1]) / 2
            return listOf(
                Triple("Arc", listOf(listOf(cx, cy, r, 0.0, 270.0)), mapOf("color" to "#2196F3", "lineWidth" to "2")),
            )
        }
    }
}
