package io.lkanvas.chart

import io.lkanvas.core.figure.Figure
import io.lkanvas.core.figure.FigureHit

/**
 * figure 注册 API——对齐上游 `index.ts` 的 getSupportedFigures/getFigureClass/
 * registerFigure 与 `utils.checkCoordinateOn*`（批次 4-4）。
 *
 * 内置 7 种图形（circle/line/polygon/rect/text/arc/path）预注册；
 * 命中测试统一委托 [FigureHit]（与绘制同一几何，批次 4 建立）。
 */
object LkanvasFigures {

    /** 内置图形名→构造工厂（对齐上游 extension/figure 七文件）。 */
    val figures: LinkedHashMap<String, () -> Figure> = linkedMapOf(
        "circle" to { Figure.Circle(0.0, 0.0, 0.0) },
        "line" to { Figure.Line(0.0, 0.0, 0.0, 0.0) },
        "polygon" to { Figure.Polygon(emptyList()) },
        "rect" to { Figure.Rect(0.0, 0.0, 0.0, 0.0) },
        "text" to { Figure.Text(0.0, 0.0, "") },
        "arc" to { Figure.Arc(0.0, 0.0, 0.0, 0.0, 0.0) },
        "path" to { Figure.Path(emptyList()) },
    )

    private val custom = LinkedHashMap<String, () -> Figure>()

    /** 上游 `registerFigure(figure)`：自定义图形工厂注册（同名冲突返回 false）。 */
    fun registerFigure(name: String, factory: () -> Figure): Boolean {
        if (figures.containsKey(name) || custom.containsKey(name)) return false
        custom[name] = factory
        return true
    }

    /** 上游 `getSupportedFigures()`：内置 + 自定义。 */
    fun getSupportedFigures(): List<String> = (figures.keys + custom.keys).toList()

    /** 上游 `getFigureClass(name)` 等价：返回图形工厂。 */
    fun getFigureFactory(name: String): (() -> Figure)? = figures[name] ?: custom[name]

    /** 上游 `utils.checkCoordinateOnCircle(circle, coordinate)` 等价包装。 */
    fun checkCoordinateOnCircle(centerX: Double, centerY: Double, radius: Double, x: Double, y: Double): Boolean =
        FigureHit.hit(Figure.Circle(centerX, centerY, radius), x, y)

    fun checkCoordinateOnLine(x0: Double, y0: Double, x1: Double, y1: Double, x: Double, y: Double): Boolean =
        FigureHit.hit(Figure.Line(x0, y0, x1, y1), x, y)

    fun checkCoordinateOnPolygon(points: List<io.lkanvas.core.figure.P>, x: Double, y: Double): Boolean =
        FigureHit.hit(Figure.Polygon(points), x, y)

    fun checkCoordinateOnRect(left: Double, top: Double, right: Double, bottom: Double, x: Double, y: Double): Boolean =
        FigureHit.hit(Figure.Rect(left, top, right, bottom), x, y)

    fun checkCoordinateOnArc(centerX: Double, centerY: Double, radius: Double, startAngleDeg: Double, endAngleDeg: Double, x: Double, y: Double): Boolean =
        FigureHit.hit(Figure.Arc(centerX, centerY, radius, startAngleDeg, endAngleDeg), x, y)

    fun checkCoordinateOnText(x: Double, y: Double, content: String, px: Double, py: Double): Boolean =
        FigureHit.hit(Figure.Text(x, y, content), px, py)
}
