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
 * 按名称分支；pane 上下文按实例 effectivePaneId；绘制升序/命中降序——同一层级顺序的两个方向）。
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
        // T09（第三轮复审 2026-10-30）：绘制**升序**（低 z 先画、高 z 最后画在
        // 顶层——上游 Store.ts:1570 升序排序 + OverlayView 顺序绘制；
        // 修复前降序使高 z 被低 z 覆盖）
        val ordered = state.overlays.all()
            .filter { it.visible && it.points.isNotEmpty() }
            .sortedWith(compareBy { it.zIndex })
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
        val template = io.lkanvas.core.extension.overlay.OverlayTemplates[inst.templateName] ?: return
        val px = inst.points.mapNotNull { state.overlayPointToPx(it, paneId, continuous = template.continuous) }
        val dash = if (creating) floatArrayOf(8f, 6f) else null
        val lineWidth = stylesLineWidth(inst) ?: 2f

        val figures = template.createPointFigures(
            inst.points, bounds.width, bounds.height,
            pixelPoints = px.map { listOf(it.x.toDouble(), it.y.toDouble() - paneTop) },
            extendData = inst.extensionData,
        ) ?: return
        // 产物为 pane 局部坐标：绘制一次性平移到 pane 绝对位置 + **pane 裁剪**
        //（Q04：越界图形不再干扰其他 pane；边界只在明确边界处理）
        // M04（第六轮复审 2026-10-31）：控制点块在 **clipRect 内、translate 外**，
        // 坐标用绝对值（与命中同坐标系；修复前块在裁剪外且坐标改成了
        // it.y-paneTop 局部值——副图控点画到主图顶部）
        clipRect(left = 0f, top = paneTop, right = contentRight, bottom = paneTop + paneHeight.toFloat()) {
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
                            // T07：rawValue 标记 → 渲染层按状态上下文格式化
                            // （蜡烛 pane=品种精度；指标 pane=pane 内指标精度最大值——
                            // 上游 priceLine.ts:25-33；修复前固定 2 位无分隔/折叠）
                            val textStyles = if (styles.containsKey("rawValue")) {
                                val raw = styles["rawValue"]?.toDoubleOrNull() ?: 0.0
                                styles + mapOf("text" to formatPriceText(state, paneId, raw))
                            } else {
                                styles
                            }
                            val fig = figureFromDescription(figureType, attrs, textStyles) ?: continue
                            with(io.lkanvas.compose.draw.FigureRenderer) {
                                drawFigure(fig, figColor, strokeWidth = strokeWidth)
                            }
                        }
                    }
                }
            }
            // N06：控制点绘制在 pane 裁剪块内（M04：绝对坐标——越界控制点被裁剪）
            if (template.continuous != true && (selected || creating)) {
                px.forEach { drawControlPoint(Offset(it.x, it.y), theme.overlayPoint) }
            }
        }
    }

    /**
     * Q06（第四轮复审 2026-10-30）：价格文本格式链——精度（蜡烛 pane=
     * **state.pricePrecision**（品种设置，修复前写死 2）；指标 pane=pane 内
     * 指标精度最大值）+ 千分位 + 小数折叠（复用 state.formatConfig，
     * 上游 priceLine.ts:24-33 契约；不再在 renderer 建立固定规则）。
     */
    private fun formatPriceText(state: ChartState, paneId: String, raw: Double): String {
        val precision = if (paneId != "main") {
            // N05（第五轮复审 2026-10-31）：实例 precisionOverride 优先于模板 precision
            state.getIndicators(io.lkanvas.compose.state.ChartState.IndicatorFilter(paneId = paneId))
                .maxOfOrNull { it.precisionOverride ?: it.definition.precision } ?: state.pricePrecision
        } else {
            state.pricePrecision
        }
        val cfg = state.formatConfig
        var text = io.lkanvas.core.common.utils.Format.formatPrecision(raw, precision)
        cfg.thousandsSign?.takeIf { it.isNotEmpty() }?.let { sign ->
            text = io.lkanvas.core.common.utils.Format.formatThousands(text, sign)
        }
        // N05：小数折叠消费（上游 priceLine.ts:33 decimalFold 格式链最后一步；
        // Format.kt:62 formatFoldDecimal 现成函数——修复前直接 return 跳过）
        cfg.decimalFoldThreshold?.let { threshold ->
            text = io.lkanvas.core.common.utils.Format.formatFoldDecimal(text, threshold)
        }
        return text
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
     * 命中测试（R09/T09：绘制升序、命中降序（绘制的逆序）；模板产物消费）。
     * M06（第六轮复审 2026-10-31）：**单一 z 降序遍历**——每实例先判控点
     * （选中且非连续模板）再判线身（同实例内控点优先于线身——上游 point
     * figure 后画先命中语义）；跨实例按 z 竞争（z=10 宽线遮住 z=0 控点时
     * 命中高层；上游 Eventful 子对象逆序分发 + fixedZLevel 保留声明层级）。
     * 修复前控制点为独立第一遍全局特权——低层固定控点抢占被高层遮住的位置。
     */
    fun hitTest(state: ChartState, x: Float, y: Float, contentRight: Float, paneBottom: Float): OverlayHit? {
        val all = state.overlays.all().filter { it.visible && it.phase != OverlayPhase.CREATING }
        val selectedId = state.overlays.selectedId
        // T09：命中**降序**（高 z 优先选中——绘制的逆序；上游 hover 置顶语义）
        val ordered = all.sortedWith(compareByDescending { it.zIndex })
        for (inst in ordered) {
            // 控点：仅选中实例；连续模板无控制点编辑；锁定不可编辑
            // N07：fixedZLevel 不排除控制点候选（上游 Overlay.ts:153 只约束层级浮起）
            if (inst.id == selectedId && !inst.locked &&
                io.lkanvas.core.extension.overlay.OverlayTemplates[inst.templateName]?.continuous != true
            ) {
                val paneId = inst.effectivePaneId
                // N06：pane 边界预检（触点不在实例所属 pane 内则跳过控制点）
                val pb = state.paneBounds(paneId)
                if (pb == null || (y >= pb.first && y <= pb.second)) {
                    inst.points.forEachIndexed { i, p ->
                        val px = state.overlayPointToPx(p, paneId) ?: return@forEachIndexed
                        if (hypot(px.x - x, px.y - y) <= POINT_HIT_RADIUS) return OverlayHit(inst.id, i, false)
                    }
                }
            }
            // 线身：Q09——fixedZLevel 的选中对象保留在普通 z 顺序候选中
            //（上游语义=不临时浮起，不禁用命中/编辑）；选中对象 wholeDrag
            val wholeDrag = inst.id == selectedId && !inst.locked
            if (hitBody(state, inst, x, y, contentRight, paneBottom)) {
                return OverlayHit(inst.id, null, wholeDrag = wholeDrag)
            }
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
        // Q04：pane 外查询点不参与该实例命中（修复前副图水平线在主图区仍可命中）
        if (y < paneTop || y > paneTop + paneHeight.toFloat()) return false
        val template = io.lkanvas.core.extension.overlay.OverlayTemplates[inst.templateName] ?: return false
        val px = inst.points.mapNotNull { state.overlayPointToPx(it, paneId, continuous = template.continuous) }
        if (px.isEmpty()) return false
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
                else -> {
                    // T08（第三轮复审 2026-10-30）：命中统一走 core FigureHit
                    //（角度/半径带/端点容差一套实现；修复前 Arc 只判圆盘、
                    // Circle 圆盘膨胀——第二套简化算法绕过已修补丁）
                    val fig = figureFromDescription(type, attrs)
                    if (fig != null && io.lkanvas.core.figure.FigureHit.hit(fig, xLocal.toDouble(), yLocal.toDouble(), tolerance = HIT_RADIUS.toDouble())) return true
                }
            }
        }
        return false
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
