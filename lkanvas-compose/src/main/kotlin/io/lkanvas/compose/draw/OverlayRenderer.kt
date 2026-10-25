package io.lkanvas.compose.draw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import io.lkanvas.compose.state.ChartState
import io.lkanvas.compose.theme.ChartTheme
import io.lkanvas.core.overlay.OverlayPhase
import kotlin.math.hypot

/**
 * 覆盖物渲染（Alpha：segment + 控制点 + 创建预览）。
 * 命中半径与绘制共用常量，保证「所见即所点」。
 */
object OverlayRenderer {

    /** 线命中半径（px）。 */
    const val HIT_RADIUS = 14f

    /** 控制点半径（px）。 */
    const val POINT_RADIUS = 5f

    /** 控制点命中半径（px，扩大可触达区域）。 */
    const val POINT_HIT_RADIUS = 18f

    fun DrawScope.drawOverlays(state: ChartState, theme: ChartTheme, contentRight: Float) {
        val creatingId = state.overlays.creatingId
        for (inst in state.overlays.all()) {
            if (!inst.visible) continue
            val selected = inst.id == state.overlays.selectedId
            val color = if (selected) theme.overlayPoint else theme.overlayLine
            when (inst.templateName) {
                "segment" -> drawSegment(state, inst.points, color, selected, creatingId == inst.id, theme)
            }
        }
    }

    private fun DrawScope.drawSegment(
        state: ChartState,
        points: List<io.lkanvas.core.overlay.OverlayPoint>,
        color: Color,
        selected: Boolean,
        creating: Boolean,
        theme: ChartTheme,
    ) {
        val pxs = points.mapNotNull { state.overlayPointToPx(it) }
        if (pxs.isEmpty()) return
        if (creating) {
            // 创建预览：虚线
            if (pxs.size == 2) {
                drawSegmentLine(pxs[0], pxs[1], color, dashed = true)
            }
            drawControlPoint(pxs.last(), theme.overlayPoint)
            return
        }
        if (pxs.size >= 2) {
            drawSegmentLine(pxs[0], pxs[1], color, dashed = false)
        }
        if (selected) {
            pxs.forEach { drawControlPoint(it, theme.overlayPoint) }
        }
    }

    private fun DrawScope.drawSegmentLine(a: Offset, b: Offset, color: Color, dashed: Boolean) {
        drawLine(
            color,
            a,
            b,
            strokeWidth = 2f,
            cap = StrokeCap.Round,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null,
        )
    }

    private fun DrawScope.drawControlPoint(center: Offset, color: Color) {
        drawCircle(Color.White, radius = POINT_RADIUS + 1.5f, center = center)
        drawCircle(color, radius = POINT_RADIUS, center = center)
    }

    // ---------- 命中测试（优先级：控制点 > 选中对象 > 其他对象，PRD R-09） ----------

    /** 命中结果：实例 ID + 控制点索引（null=线身/整体）。 */
    data class OverlayHit(val instanceId: String, val pointIndex: Int?, val wholeDrag: Boolean)

    fun hitTest(state: ChartState, x: Float, y: Float): OverlayHit? {
        val all = state.overlays.all().filter { it.visible }
        val selectedId = state.overlays.selectedId
        // 1) 控制点（仅选中实例）
        for (inst in all) {
            if (inst.id != selectedId || inst.locked) continue
            inst.points.forEachIndexed { i, p ->
                val px = state.overlayPointToPx(p) ?: return@forEachIndexed
                if (hypot(px.x - x, px.y - y) <= POINT_HIT_RADIUS) return OverlayHit(inst.id, i, false)
            }
        }
        // 2) 选中对象的线身
        for (inst in all) {
            if (inst.id != selectedId) continue
            if (hitSegmentBody(state, inst, x, y)) return OverlayHit(inst.id, null, wholeDrag = !inst.locked)
        }
        // 3) 其他对象（按层级从高到低、稳定次序倒序）
        for (inst in all.sortedWith(compareByDescending<io.lkanvas.core.overlay.OverlayInstance> { it.zIndex })) {
            if (inst.id == selectedId) continue
            if (hitSegmentBody(state, inst, x, y)) return OverlayHit(inst.id, null, wholeDrag = false)
        }
        return null
    }

    private fun hitSegmentBody(
        state: ChartState,
        inst: io.lkanvas.core.overlay.OverlayInstance,
        x: Float,
        y: Float,
    ): Boolean {
        if (inst.templateName != "segment" || inst.phase == OverlayPhase.CREATING) return false
        if (inst.points.size < 2) return false
        val a = state.overlayPointToPx(inst.points[0]) ?: return false
        val b = state.overlayPointToPx(inst.points[1]) ?: return false
        return distanceToSegment(x, y, a, b) <= HIT_RADIUS
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
