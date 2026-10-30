package io.lkanvas.compose.draw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipRect
import io.lkanvas.compose.state.ChartState
import io.lkanvas.compose.theme.ChartTheme
import io.lkanvas.core.figure.Figure
import io.lkanvas.core.overlay.OverlayGeometry
import io.lkanvas.core.overlay.OverlayInstance
import io.lkanvas.core.overlay.OverlayPhase
import io.lkanvas.core.overlay.OverlayPoint
import kotlin.math.hypot

/**
 * 覆盖物渲染与命中（R09，复审 2026-10-30：**统一模板执行**——16 内置模板的
 * createPointFigures 为图形生成唯一来源，renderer/命中消费同一产物，不再有
 * 按名称分支；pane 上下文按实例 effectivePaneId；命中序与绘制序共用 zIndex 排序）。
 */
object OverlayRenderer {

    /** 线命中半径（px）。 */
    const val HIT_RADIUS = 14f

    /** 控制点半径（px）。 */
    const val POINT_RADIUS = 5f

    /** 控制点命中半径（px，扩大可触达区域）。 */
    const val POINT_HIT_RADIUS = 18f

    /** 命中结果：实例 ID + 控制点索引（null=线身/整体）。 */
    data class OverlayHit(val instanceId: String, val pointIndex: Int?, val wholeDrag: Boolean)

    /**
     * 覆盖物绘制：可见 + 有点位的实例按 zIndex 序绘制；
     * 图形生成唯一来源 = 模板 createPointFigures（pane 感知）。
     */
    fun DrawScope.drawOverlays(
        state: ChartState,
        theme: ChartTheme,
        contentRight: Float,
        paneBottom: Float,
        text: ChartText,
    ) {
        val creatingId = state.overlays.creatingId
        val ordered = state.overlays.all()
            .filter { it.visible && it.points.isNotEmpty() }
            .sortedWith(compareByDescending { it.zIndex })
        for (inst in ordered) {
            val selected = inst.id == state.overlays.selectedId
            val color = if (selected) theme.overlayPoint else (stylesColor(inst) ?: theme.overlayLine)
            drawTemplate(state, inst, contentRight, paneBottom, color, selected, creatingId == inst.id, theme, text)
        }
    }

    /**
     * 统一模板执行：pane 上下文按 [OverlayInstance.effectivePaneId]（pane 边界 +
     * 值映射）；图形产物经 [FigureRenderer] 绘制；"FibLevel" 数据标记由渲染层
     * 经 pane 轴投影（D-02 模板侧层级逻辑 + 渲染侧投影）。
     */
    private fun DrawScope.drawTemplate(
        state: ChartState,
        inst: OverlayInstance,
        contentRight: Float,
        paneBottom: Float,
        color: Color,
        selected: Boolean,
        creating: Boolean,
        theme: ChartTheme,
        text: ChartText,
    ) {
        val paneId = inst.effectivePaneId
        val paneBoundsPair = state.paneBounds(paneId)
        val paneTop = paneBoundsPair?.first ?: 0f
        val paneHeight = ((paneBoundsPair?.second ?: paneBottom) - paneTop).toDouble()
        // T04（第三轮复审 2026-10-30）：模板统一 **pane 局部坐标**——bounds =
        // (0,0,width,paneHeight)、pixelPoints y 减 pane top（修复前 bounds 底是
        // 绝对坐标、verticalStraightLine 从整图顶画穿主图）
        val bounds = OverlayGeometry.Bounds(0.0, 0.0, contentRight.toDouble(), paneHeight)
        val px = inst.points.mapNotNull { state.overlayPointToPx(it, paneId) }
        val dash = if (creating) floatArrayOf(8f, 6f) else null
        val lineWidth = stylesLineWidth(inst) ?: 2f

        val template = io.lkanvas.core.extension.overlay.OverlayTemplates[inst.templateName] ?: return
        val figures = template.createPointFigures(
            inst.points, bounds.width, bounds.height,
            pixelPoints = px.map { listOf(it.x.toDouble(), it.y.toDouble() - paneTop) },
            extendData = inst.extensionData,
        ) ?: return
        // 产物为 pane 局部坐标：绘制一次性平移到 pane 绝对位置（边界一次转换）
        translate(left = 0f, top = paneTop) {
            for ((figureType, attrs, styles) in figures) {
                val figColor = parseFigureColor(styles["color"]) ?: color
                val strokeWidth = styles["lineWidth"]?.toFloatOrNull() ?: lineWidth
                when (figureType) {
                    "FibLevel" -> {
                        val value = attrs.getOrNull(0)?.getOrNull(1) ?: continue
                        // pane 局部 y（paneValueToY 即相对值）
                        val y = state.paneValueToY(paneId, value) ?: continue
                        if (y < bounds.top || y > bounds.bottom) continue
                        val seg = OverlayGeometry.horizontalLine(y, bounds).toOffset()
                        drawLine(
                            figColor, seg.first, seg.second,
                            strokeWidth = if (creating) 1.5f else strokeWidth,
                            pathEffect = dash?.let { PathEffect.dashPathEffect(it) },
                        )
                        val label = styles["label"] ?: ""
                        drawText(
                            text, label,
                            Offset(seg.first.x + 4f, y.toFloat() - text.textHeight),
                            theme.overlayText,
                        )
                    }
                    else -> {
                        val fig = figureFromDescription(figureType, attrs, styles) ?: continue
                        with(io.lkanvas.compose.draw.FigureRenderer) {
                            drawFigure(fig, figColor, strokeWidth = strokeWidth)
                        }
                    }
                }
            }
        }
        // 控制点（非连续模板；选中或创建中）
        if (template.continuous != true && (selected || creating)) {
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

    /** 实例级 styles 协议：Map 的 "color"（'#RRGGBB'）（SLA-013 消费）。 */
    internal fun stylesColor(inst: OverlayInstance): Color? {
        val map = inst.styles as? Map<*, *> ?: return null
        val raw = map["color"]?.toString() ?: return null
        return parseFigureColor(raw)
    }

    /** 实例级 styles 协议：Map 的 "lineWidth"（SLA-013 消费）。 */
    internal fun stylesLineWidth(inst: OverlayInstance): Float? {
        val map = inst.styles as? Map<*, *> ?: return null
        val raw = map["lineWidth"] ?: return null
        return (raw as? Number)?.toFloat() ?: raw.toString().toFloatOrNull()
    }

    /** 钩子描述 → core Figure 原语（attrs 按各原语字段序取 Double；Text 载荷经 styles）。 */
    private fun figureFromDescription(
        figureType: String,
        attrs: List<List<Double>>,
        styles: Map<String, String> = emptyMap(),
    ): Figure? {
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
            "Path" -> Figure.Path(attrs.mapNotNull { p ->
                if (p.size >= 2) io.lkanvas.core.figure.P(p[0], p[1]) else null
            })
            "Text" -> Figure.Text(d(0) ?: return null, d(1) ?: return null, styles["text"] ?: "")
            else -> null
        }
    }

    private fun DrawScope.drawControlPoint(center: Offset, color: Color) {
        drawCircle(Color.White, radius = POINT_RADIUS + 1.5f, center = center)
        drawCircle(color, radius = POINT_RADIUS, center = center)
    }

    private fun Offset.toP() = OverlayGeometry.P(x.toDouble(), y.toDouble())

    private fun Pair<OverlayGeometry.P, OverlayGeometry.P>.toOffset(): Pair<Offset, Offset> =
        Offset(first.x.toFloat(), first.y.toFloat()) to Offset(second.x.toFloat(), second.y.toFloat())

    /**
     * 命中测试（R09：命中序与绘制序共用 zIndex 排序；模板产物消费）。
     * 优先级：控制点 > 选中对象 > 其他对象（PRD R-09）。
     */
    fun hitTest(state: ChartState, x: Float, y: Float, contentRight: Float, paneBottom: Float): OverlayHit? {
        val all = state.overlays.all().filter { it.visible && it.phase != OverlayPhase.CREATING }
        val selectedId = state.overlays.selectedId
        val ordered = all.sortedWith(compareByDescending { it.zIndex })
        // 1) 控制点（仅选中实例；连续模板无控制点编辑；fixedZLevel 不随选中浮起）
        for (inst in ordered) {
            if (inst.id != selectedId || inst.locked || inst.fixedZLevel) continue
            if (io.lkanvas.core.extension.overlay.OverlayTemplates[inst.templateName]?.continuous == true) continue
            val paneId = inst.effectivePaneId
            inst.points.forEachIndexed { i, p ->
                val px = state.overlayPointToPx(p, paneId) ?: return@forEachIndexed
                if (hypot(px.x - x, px.y - y) <= POINT_HIT_RADIUS) return OverlayHit(inst.id, i, false)
            }
        }
        // 2) 选中对象的线身（fixedZLevel=true 层级固定：不享受选中优先，SLA-013）
        for (inst in ordered) {
            if (inst.id != selectedId || inst.fixedZLevel) continue
            if (hitBody(state, inst, x, y, contentRight, paneBottom)) {
                return OverlayHit(inst.id, null, wholeDrag = !inst.locked)
            }
        }
        // 3) 其他对象（层级从高到低；与绘制遍历同一排序）
        for (inst in ordered) {
            if (inst.id == selectedId) continue
            if (hitBody(state, inst, x, y, contentRight, paneBottom)) return OverlayHit(inst.id, null, wholeDrag = false)
        }
        return null
    }

    private fun hitBody(
        state: ChartState,
        inst: OverlayInstance,
        x: Float,
        y: Float,
        contentRight: Float,
        paneBottom: Float,
    ): Boolean {
        val paneId = inst.effectivePaneId
        val paneBoundsPair = state.paneBounds(paneId)
        val paneTop = paneBoundsPair?.first ?: 0f
        val paneHeight = ((paneBoundsPair?.second ?: paneBottom) - paneTop).toDouble()
        // T04：命中与绘制同坐标系——pane 局部 bounds + pixelPoints 减 top + 查询点减 top
        val bounds = OverlayGeometry.Bounds(0.0, 0.0, contentRight.toDouble(), paneHeight)
        val px = inst.points.mapNotNull { state.overlayPointToPx(it, paneId) }
        if (px.isEmpty()) return false
        val template = io.lkanvas.core.extension.overlay.OverlayTemplates[inst.templateName] ?: return false
        if (!template.checkEventOn) return false
        val figures = template.createPointFigures(
            inst.points, bounds.width, bounds.height,
            pixelPoints = px.map { listOf(it.x.toDouble(), it.y.toDouble() - paneTop) },
            extendData = inst.extensionData,
        ) ?: return false
        val xLocal = x
        val yLocal = y - paneTop
        // R09：命中 = 模板产物的图形距离判定（与绘制同一钩子产物，pane 局部）
        for ((type, attrs, styles) in figures) {
            when (type) {
                "FibLevel" -> {
                    val value = attrs.getOrNull(0)?.getOrNull(1) ?: continue
                    val fy = state.paneValueToY(paneId, value) ?: continue
                    if (fy < bounds.top || fy > bounds.bottom) continue
                    val seg = OverlayGeometry.horizontalLine(fy, bounds).toOffset()
                    if (distanceToSegment(xLocal, yLocal, seg.first, seg.second) <= HIT_RADIUS) return true
                }
                "Text" -> {
                    val d0 = attrs.getOrNull(0)
                    val tx = d0?.getOrNull(0)?.toFloat() ?: continue
                    val ty = d0.getOrNull(1)?.toFloat() ?: continue
                    if (hypot(xLocal - tx, yLocal - ty) <= HIT_RADIUS) return true
                }
                else -> if (hitFigureShape(type, attrs, xLocal, yLocal)) return true
            }
        }
        return false
    }

    /** 图形形状距离判定（Line/Rect/Polygon/Circle/Arc/Path）。 */
    private fun hitFigureShape(type: String, attrs: List<List<Double>>, x: Float, y: Float): Boolean {
        fun d(i: Int): Float? =
            (attrs.getOrNull(i)?.getOrNull(0) ?: attrs.getOrNull(0)?.getOrNull(i))?.toFloat()
        when (type) {
            "Line" -> {
                val a = d(0) ?: return false
                val b = d(1) ?: return false
                val c = d(2) ?: return false
                val e = d(3) ?: return false
                return distanceToSegment(x, y, Offset(a, b), Offset(c, e)) <= HIT_RADIUS
            }
            "Circle" -> {
                val cx = d(0) ?: return false
                val cy = d(1) ?: return false
                val r = d(2) ?: return false
                return hypot(x - cx, y - cy) <= r + HIT_RADIUS
            }
            "Rect" -> {
                val l = d(0) ?: return false
                val t = d(1) ?: return false
                val r = d(2) ?: return false
                val b = d(3) ?: return false
                val inX = x >= minOf(l, r) - HIT_RADIUS && x <= maxOf(l, r) + HIT_RADIUS
                val inY = y >= minOf(t, b) - HIT_RADIUS && y <= maxOf(t, b) + HIT_RADIUS
                return inX && inY
            }
            "Polygon" -> {
                val edges = ArrayList<Pair<Offset, Offset>>(attrs.size)
                for (k in attrs.indices) {
                    val p0 = attrs[k]
                    val p1 = attrs[(k + 1) % attrs.size]
                    if (p0.size >= 2 && p1.size >= 2) {
                        edges.add(Offset(p0[0].toFloat(), p0[1].toFloat()) to Offset(p1[0].toFloat(), p1[1].toFloat()))
                    }
                }
                return edges.any { (a, b) -> distanceToSegment(x, y, a, b) <= HIT_RADIUS }
            }
            "Arc" -> {
                val cx = d(0) ?: return false
                val cy = d(1) ?: return false
                val r = d(2) ?: return false
                return hypot(x - cx, y - cy) <= r + HIT_RADIUS
            }
            "Path" -> {
                val pts = attrs.mapNotNull { p -> if (p.size >= 2) Offset(p[0].toFloat(), p[1].toFloat()) else null }
                for (k in 0 until pts.size - 1) {
                    if (distanceToSegment(x, y, pts[k], pts[k + 1]) <= HIT_RADIUS) return true
                }
                return false
            }
            else -> return false
        }
    }

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
