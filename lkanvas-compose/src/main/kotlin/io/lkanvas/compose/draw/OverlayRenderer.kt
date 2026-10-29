package io.lkanvas.compose.draw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.lkanvas.compose.state.ChartState
import io.lkanvas.compose.theme.ChartTheme
import io.lkanvas.core.overlay.OverlayConstraint
import io.lkanvas.core.figure.Figure
import io.lkanvas.core.overlay.OverlayGeometry
import io.lkanvas.core.overlay.OverlayInstance
import io.lkanvas.core.overlay.OverlayPhase
import io.lkanvas.core.overlay.OverlayPoint
import kotlin.math.hypot

/**
 * 覆盖物渲染与命中（Beta：16 种模板 + 控制点 + 创建预览）。
 * 命中半径与绘制共用常量，保证「所见即所点」。
 */
object OverlayRenderer {

    /** 线命中半径（px）。 */
    const val HIT_RADIUS = 14f

    /** 控制点半径（px）。 */
    const val POINT_RADIUS = 5f

    /** 控制点命中半径（px，扩大可触达区域）。 */
    const val POINT_HIT_RADIUS = 18f

    /** 主 pane 像素边界（bounds 参数由 KLineChart 传入）。 */
    fun DrawScope.drawOverlays(
        state: ChartState,
        theme: ChartTheme,
        contentRight: Float,
        paneBottom: Float,
        text: ChartText,
    ) {
        val bounds = OverlayGeometry.Bounds(0.0, 0.0, contentRight.toDouble(), paneBottom.toDouble())
        val creatingId = state.overlays.creatingId
        for (inst in state.overlays.all()) {
            if (!inst.visible) continue
            if (inst.points.isEmpty()) continue
            val selected = inst.id == state.overlays.selectedId
            val color = if (selected) theme.overlayPoint else theme.overlayLine
            drawTemplate(state, inst, bounds, color, selected, creatingId == inst.id, theme, text)
        }
    }

    private fun DrawScope.drawTemplate(
        state: ChartState,
        inst: OverlayInstance,
        bounds: OverlayGeometry.Bounds,
        color: Color,
        selected: Boolean,
        creating: Boolean,
        theme: ChartTheme,
        text: ChartText,
    ) {
        val px = inst.points.mapNotNull { state.overlayPointToPx(it) }
        val dash = if (creating) floatArrayOf(8f, 6f) else null
        fun line(a: Offset, b: Offset) = drawLine(
            color, a, b, strokeWidth = 2f, cap = StrokeCap.Round,
            pathEffect = dash?.let { PathEffect.dashPathEffect(it) },
        )
        fun seg(s: Pair<Offset, Offset>) = line(s.first, s.second)

        when (inst.templateName) {
            "segment" -> if (px.size >= 2) seg(px[0] to px[1])
            "straightLine" -> if (px.size >= 2) {
                OverlayGeometry.clippedLine(px[0].toP(), px[1].toP(), bounds)?.let { seg(it.toOffset()) }
            }
            "rayLine" -> if (px.size >= 2) {
                OverlayGeometry.clippedRay(px[0].toP(), px[1].toP(), bounds)?.let { seg(it.toOffset()) }
            }
            "horizontalRayLine" -> if (px.size >= 2 && px[1].x != px[0].x) {
                OverlayGeometry.horizontalRay(px[0].toP(), (px[1].x - px[0].x).toDouble(), bounds)?.let { seg(it.toOffset()) }
            }
            "horizontalSegment" -> if (px.size >= 2) {
                seg(OverlayGeometry.horizontalSegment(px[0].y.toDouble(), px[0].x.toDouble(), px[1].x.toDouble()).toOffset())
            }
            "horizontalStraightLine", "priceLine" -> {
                seg(OverlayGeometry.horizontalLine(px[0].y.toDouble(), bounds).toOffset())
                if (inst.templateName == "priceLine" && !creating) {
                    drawPriceLabel(state, text, theme, inst.points[0], px[0].y, bounds)
                }
            }
            "verticalRayLine" -> if (px.size >= 2 && px[1].y != px[0].y) {
                OverlayGeometry.verticalRay(px[0].toP(), (px[1].y - px[0].y).toDouble(), bounds)?.let { seg(it.toOffset()) }
            }
            "verticalSegment" -> if (px.size >= 2) {
                seg(OverlayGeometry.verticalSegment(px[0].x.toDouble(), px[0].y.toDouble(), px[1].y.toDouble()).toOffset())
            }
            "verticalStraightLine" -> seg(OverlayGeometry.verticalLine(px[0].x.toDouble(), bounds).toOffset())
            "parallelStraightLine" -> if (px.size >= 3) {
                OverlayGeometry.parallelGroup(px[0].toP(), px[1].toP(), px[2].toP(), bounds)
                    .forEach { seg(it.toOffset()) }
            }
            "priceChannelLine" -> if (px.size >= 3) {
                OverlayGeometry.priceChannel(px[0].toP(), px[1].toP(), px[2].toP(), bounds)
                    .forEach { seg(it.toOffset()) }
            }
            "fibonacciLine" -> if (px.size >= 2) {
                drawFibonacci(state, text, theme, inst.points, bounds, creating)
            }
            "simpleAnnotation", "simpleTag" -> {
                drawTextOverlay(state, text, theme, inst, px[0], selected)
            }
            "brush" -> if (px.size >= 2) {
                val path = Path()
                path.moveTo(px[0].x, px[0].y)
                for (i in 1 until px.size) path.lineTo(px[i].x, px[i].y)
                drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f, pathEffect = dash?.let { PathEffect.dashPathEffect(it) }))
            }
            // 0.4.x figure 样例：宿主自定义模板经 createPointFigures 钩子返回
            // Figure 原语描述（UpperCamelCase 类型名 → core Figure 映射）
            else -> {
                val def = io.lkanvas.core.overlay.OverlayTemplates[inst.templateName]
                    ?: io.lkanvas.chart.LkanvasRegistry.getOverlayDefinition(inst.templateName)
                val figures = def?.createPointFigures(
                    inst.points, bounds.width, bounds.height,
                    pixelPoints = px.map { listOf(it.x.toDouble(), it.y.toDouble()) },
                )
                if (figures != null) {
                    for ((figureType, attrs, styles) in figures) {
                        val figColor = parseFigureColor(styles["color"]) ?: color
                        val fig = figureFromDescription(figureType, attrs) ?: continue
                        with(io.lkanvas.compose.draw.FigureRenderer) {
                            drawFigure(fig, figColor, strokeWidth = styles["lineWidth"]?.toFloatOrNull() ?: 2f)
                        }
                    }
                }
            }
        }
        // 控制点（非连续模板；选中或创建中）
        val def = io.lkanvas.core.overlay.OverlayTemplates[inst.templateName]
            ?: io.lkanvas.chart.LkanvasRegistry.getOverlayDefinition(inst.templateName)
        if (def?.continuous != true && (selected || creating)) {
            px.forEach { drawControlPoint(it, theme.overlayPoint) }
        }
    }

    /** styles["color"]（'#RRGGBB'）→ Compose Color；非法返回 null（回退主题色）。 */
    private fun parseFigureColor(hex: String?): Color? {
        val h = hex?.removePrefix("#") ?: return null
        if (h.length != 6) return null
        return runCatching {
            Color(
                red = h.substring(0, 2).toInt(16) / 255f,
                green = h.substring(2, 4).toInt(16) / 255f,
                blue = h.substring(4, 6).toInt(16) / 255f,
                alpha = 1f,
            )
        }.getOrNull()
    }

    /** 钩子描述 → core Figure 原语（attrs 按各原语字段序取 Double；缺字段返回 null）。 */
    private fun figureFromDescription(figureType: String, attrs: List<List<Double>>): Figure? {
        // 兼容两种 attrs 布局：每字段一子 list（[[v0],[v1],…]）或单子 list 全字段（[[v0,v1,…]]）
        fun d(i: Int): Double? =
            attrs.getOrNull(i)?.getOrNull(0) ?: attrs.getOrNull(0)?.getOrNull(i)
        return when (figureType) {
            "Circle" -> Figure.Circle(d(0) ?: return null, d(1) ?: return null, d(2) ?: return null)
            "Line" -> Figure.Line(d(0) ?: return null, d(1) ?: return null, d(2) ?: return null, d(3) ?: return null)
            "Rect" -> Figure.Rect(d(0) ?: return null, d(1) ?: return null, d(2) ?: return null, d(3) ?: return null)
            "Polygon" -> Figure.Polygon(attrs.mapNotNull { p ->
                if (p.size >= 2) io.lkanvas.core.figure.P(p[0], p[1]) else null
            })
            "Arc" -> Figure.Arc(d(0) ?: return null, d(1) ?: return null, d(2) ?: return null, d(3) ?: return null, d(4) ?: return null)
            "Text" -> Figure.Text(d(0) ?: return null, d(1) ?: return null, attrs.getOrNull(2)?.joinToString(",") { v -> v.toString() } ?: "")
            else -> null
        }
    }

    /** 斐波那契：价格空间先算层级再投影（D-02），全宽横线 + 「价格 (xx.x%)」标签。 */
    private fun DrawScope.drawFibonacci(
        state: ChartState,
        text: ChartText,
        theme: ChartTheme,
        points: List<OverlayPoint>,
        bounds: OverlayGeometry.Bounds,
        creating: Boolean,
    ) {
        if (points.size < 2) return
        val levels = OverlayGeometry.fibonacciLevels(points[0].value, points[1].value, io.lkanvas.core.overlay.FibonacciLineDefinition.ratios)
        val ratios = io.lkanvas.core.overlay.FibonacciLineDefinition.ratios
        for ((i, value) in levels.withIndex()) {
            val y = state.mainPaneYOf(value) ?: continue
            if (y < bounds.top || y > bounds.bottom) continue
            val seg = OverlayGeometry.horizontalLine(y, bounds).toOffset()
            drawLine(
                theme.overlayLine,
                seg.first, seg.second,
                strokeWidth = 1.5f,
                pathEffect = if (creating) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null,
            )
            drawText(
                text,
                "${text.price(value)} (${(ratios[i] * 100).toFixed1()}%)",
                Offset(seg.first.x + 4f, y.toFloat() - text.textHeight),
                theme.overlayText,
            )
        }
    }

    private fun Double.toFixed1(): String = String.format("%.1f", this)

    /** priceLine 右侧价格标签。 */
    private fun DrawScope.drawPriceLabel(
        state: ChartState,
        text: ChartText,
        theme: ChartTheme,
        point: OverlayPoint,
        y: Float,
        bounds: OverlayGeometry.Bounds,
    ) {
        drawText(text, text.price(point.value), Offset(bounds.right.toFloat() - 64f, y.toFloat() - text.textHeight), theme.overlayText)
    }

    /** annotation（点旁文本）/ tag（带底色标签）。 */
    private fun DrawScope.drawTextOverlay(
        state: ChartState,
        text: ChartText,
        theme: ChartTheme,
        inst: OverlayInstance,
        px: Offset,
        selected: Boolean,
    ) {
        val content = inst.extensionData["text"] ?: ""
        if (inst.templateName == "simpleTag") {
            val w = text.measureWidth(content) + 12f
            val h = text.tagHeight
            drawRect(
                theme.overlayLine.copy(alpha = 0.85f),
                topLeft = Offset(px.x, px.y - h),
                size = androidx.compose.ui.geometry.Size(w, h),
            )
            drawText(text, content, Offset(px.x + 6f, px.y - h + (h - text.textHeight) / 2f), Color.White)
        } else {
            drawText(text, content, Offset(px.x + 8f, px.y - text.textHeight), theme.overlayText)
        }
        if (selected) drawControlPoint(px, theme.overlayPoint)
    }

    private fun DrawScope.drawControlPoint(center: Offset, color: Color) {
        drawCircle(Color.White, radius = POINT_RADIUS + 1.5f, center = center)
        drawCircle(color, radius = POINT_RADIUS, center = center)
    }

    // ---------- 命中测试（优先级：控制点 > 选中对象 > 其他对象，PRD R-09） ----------

    /** 命中结果：实例 ID + 控制点索引（null=线身/整体）。 */
    data class OverlayHit(val instanceId: String, val pointIndex: Int?, val wholeDrag: Boolean)

    fun hitTest(state: ChartState, x: Float, y: Float, contentRight: Float, paneBottom: Float): OverlayHit? {
        val bounds = OverlayGeometry.Bounds(0.0, 0.0, contentRight.toDouble(), paneBottom.toDouble())
        val all = state.overlays.all().filter { it.visible && it.phase != OverlayPhase.CREATING }
        val selectedId = state.overlays.selectedId
        // 1) 控制点（仅选中实例；连续模板无控制点编辑）
        for (inst in all) {
            if (inst.id != selectedId || inst.locked) continue
            if (io.lkanvas.core.overlay.OverlayTemplates[inst.templateName]?.continuous == true) continue
            inst.points.forEachIndexed { i, p ->
                val px = state.overlayPointToPx(p) ?: return@forEachIndexed
                if (hypot(px.x - x, px.y - y) <= POINT_HIT_RADIUS) return OverlayHit(inst.id, i, false)
            }
        }
        // 2) 选中对象的线身
        for (inst in all) {
            if (inst.id != selectedId) continue
            if (hitBody(state, inst, x, y, bounds)) {
                return OverlayHit(inst.id, null, wholeDrag = !inst.locked)
            }
        }
        // 3) 其他对象（层级从高到低）
        for (inst in all.sortedWith(compareByDescending<OverlayInstance> { it.zIndex })) {
            if (inst.id == selectedId) continue
            if (hitBody(state, inst, x, y, bounds)) return OverlayHit(inst.id, null, wholeDrag = false)
        }
        return null
    }

    /** 线身命中：按模板计算显示线段，取最小距离。 */
    private fun hitBody(
        state: ChartState,
        inst: OverlayInstance,
        x: Float,
        y: Float,
        bounds: OverlayGeometry.Bounds,
    ): Boolean {
        val px = inst.points.mapNotNull { state.overlayPointToPx(it) }
        if (px.isEmpty()) return false
        val segments: List<Pair<Offset, Offset>> = when (inst.templateName) {
            "segment" -> if (px.size >= 2) listOf(px[0] to px[1]) else emptyList()
            "straightLine" -> if (px.size >= 2) {
                OverlayGeometry.clippedLine(px[0].toP(), px[1].toP(), bounds)?.let { listOf(it.toOffset()) } ?: emptyList()
            } else emptyList()
            "rayLine" -> if (px.size >= 2) {
                OverlayGeometry.clippedRay(px[0].toP(), px[1].toP(), bounds)?.let { listOf(it.toOffset()) } ?: emptyList()
            } else emptyList()
            "horizontalRayLine" -> if (px.size >= 2 && px[1].x != px[0].x) {
                OverlayGeometry.horizontalRay(px[0].toP(), (px[1].x - px[0].x).toDouble(), bounds)
                    ?.let { listOf(it.toOffset()) } ?: emptyList()
            } else emptyList()
            "horizontalSegment" -> if (px.size >= 2) {
                listOf(OverlayGeometry.horizontalSegment(px[0].y.toDouble(), px[0].x.toDouble(), px[1].x.toDouble()).toOffset())
            } else emptyList()
            "horizontalStraightLine", "priceLine" ->
                listOf(OverlayGeometry.horizontalLine(px[0].y.toDouble(), bounds).toOffset())
            "verticalRayLine" -> if (px.size >= 2 && px[1].y != px[0].y) {
                OverlayGeometry.verticalRay(px[0].toP(), (px[1].y - px[0].y).toDouble(), bounds)
                    ?.let { listOf(it.toOffset()) } ?: emptyList()
            } else emptyList()
            "verticalSegment" -> if (px.size >= 2) {
                listOf(OverlayGeometry.verticalSegment(px[0].x.toDouble(), px[0].y.toDouble(), px[1].y.toDouble()).toOffset())
            } else emptyList()
            "verticalStraightLine" -> listOf(OverlayGeometry.verticalLine(px[0].x.toDouble(), bounds).toOffset())
            "parallelStraightLine" -> if (px.size >= 3) {
                OverlayGeometry.parallelGroup(px[0].toP(), px[1].toP(), px[2].toP(), bounds).map { it.toOffset() }
            } else emptyList()
            "priceChannelLine" -> if (px.size >= 3) {
                OverlayGeometry.priceChannel(px[0].toP(), px[1].toP(), px[2].toP(), bounds).map { it.toOffset() }
            } else emptyList()
            "fibonacciLine" -> {
                if (px.size < 2) emptyList() else {
                    fibonacciHitLines(state, inst, bounds)
                }
            }
            "simpleAnnotation", "simpleTag" -> {
                // 文本覆盖物按锚点邻近命中
                return hypot(px[0].x - x, px[0].y - y) <= HIT_RADIUS
            }
            "brush" -> {
                (0 until px.size - 1).map { px[it] to px[it + 1] }
            }
            else -> emptyList()
        }
        return segments.any { (a, b) -> distanceToSegment(x, y, a, b) <= HIT_RADIUS }
    }

    private fun fibonacciHitLines(
        state: ChartState,
        inst: OverlayInstance,
        bounds: OverlayGeometry.Bounds,
    ): List<Pair<Offset, Offset>> {
        if (inst.points.size < 2) return emptyList()
        val levels = OverlayGeometry.fibonacciLevels(inst.points[0].value, inst.points[1].value, io.lkanvas.core.overlay.FibonacciLineDefinition.ratios)
        return levels.mapNotNull { value ->
            val y = state.mainPaneYOf(value) ?: return@mapNotNull null
            if (y < bounds.top || y > bounds.bottom) return@mapNotNull null
            OverlayGeometry.horizontalLine(y, bounds).toOffset()
        }
    }

    private fun Offset.toP() = OverlayGeometry.P(x.toDouble(), y.toDouble())

    private fun Pair<OverlayGeometry.P, OverlayGeometry.P>.toOffset(): Pair<Offset, Offset> =
        Offset(first.x.toFloat(), first.y.toFloat()) to Offset(second.x.toFloat(), second.y.toFloat())

    /** 点到线段距离。 */
    fun distanceToSegment(px: Float, py: Float, a: Offset, b: Offset): Float {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val len2 = abx * abx + aby * aby
        if (len2 == 0f) return hypot(px - a.x, py - a.y)
        var t = ((px - a.x) * abx + (py - a.y) * aby) / len2
        t = t.coerceIn(0f, 1f)
        return hypot(px - (a.x + t * abx), py - (a.y + t * aby))
    }
}
