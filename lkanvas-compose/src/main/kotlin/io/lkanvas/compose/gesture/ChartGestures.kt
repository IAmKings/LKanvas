package io.lkanvas.compose.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import io.lkanvas.compose.draw.OverlayRenderer
import io.lkanvas.compose.state.ChartState
import io.lkanvas.compose.state.GestureOwner
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot

private enum class GMode { IDLE, PAN, CROSSHAIR, PINCH, CREATE, POINT_DRAG, WHOLE_DRAG, PANE_ADJUST, AXIS_ZOOM }

/** 模板事件回调族派发（0.3.0 批次 8a/8b：OVL-011/012/014/015/016 消费端）。 */
internal object OverlayEventDispatch {

    /** 模板查询（含内置 16 模板）。 */
    fun templateOf(state: ChartState, instanceId: String?): io.lkanvas.core.overlay.OverlayDefinition? =
        state.overlays.get(instanceId ?: return null)?.let { io.lkanvas.core.extension.overlay.OverlayTemplates[it.templateName] }

    fun onClick(state: ChartState, id: String?, x: Float, y: Float) {
        templateOf(state, id)?.onClick?.invoke(id!!, -1, x.toDouble(), y.toDouble())
    }

    fun onDoubleClick(state: ChartState, id: String?, x: Float, y: Float) {
        templateOf(state, id)?.onDoubleClick?.invoke(id!!, -1, x.toDouble(), y.toDouble())
    }

    fun onRightClick(state: ChartState, id: String?, x: Float, y: Float) {
        // Android 等价映射：onRightClick → 长按命中（矩阵 OVL-014）
        templateOf(state, id)?.onRightClick?.invoke(id!!, -1, x.toDouble(), y.toDouble())
    }

    fun onTouchStart(state: ChartState, id: String?, x: Float, y: Float) {
        templateOf(state, id)?.onTouchStart?.invoke(id!!, -1, x.toDouble(), y.toDouble())
    }

    fun onTouchMove(state: ChartState, id: String?, x: Float, y: Float) {
        templateOf(state, id)?.onTouchMove?.invoke(id!!, -1, x.toDouble(), y.toDouble())
    }

    fun onTouchEnd(state: ChartState, id: String?, x: Float, y: Float) {
        templateOf(state, id)?.onTouchEnd?.invoke(id!!, -1, x.toDouble(), y.toDouble())
    }
}

/**
 * 图表手势状态机（PRD R-10 Alpha 部分 + R-09 覆盖物）：
 * - 单指拖动（超过触距且横向为主）→ 平移（PAN），释放带基础惯性；
 *   纵向为主的位移不消费，外层 LazyColumn/Pager 可正常接管（A-32 基础）
 * - 双指 → 缩放（PINCH，中点为锚）；第二指加入时结束平移/十字线；
 *   双指抬一指降级等待，不再误触发平移
 * - 长按（未移动）→ 十字线（CROSSHAIR），移动吸附 candle 中心，抬起隐藏
 * - 短按无位移 → 点击 candle 事件
 * - 创建模式（CREATE）：点击按磁吸加控制点，补齐自动完成（O-01 segment）
 * - 命中覆盖物：控制点拖动（POINT_DRAG）优先于整体拖动（WHOLE_DRAG），
 *   再优先于图表平移（命中优先级 PRD R-09）
 * - 位移被父容器消费（接管/取消）→ 结束当前交互，无残留状态（A-31 基础）
 */
fun Modifier.chartGestures(state: ChartState): Modifier = this.pointerInput(state) {
    val slop = viewConfiguration.touchSlop
    val longPressMillis = viewConfiguration.longPressTimeoutMillis

    awaitEachGesture {
        val down = awaitFirstDown()
        val downNanos = System.nanoTime()
        state.beginGesture(GestureOwner.NONE)
        var mode = GMode.IDLE
        var moved = false
        var pinchDist: Float? = null
        val velocity = VelocityTracker()
        // 覆盖物拖动上下文
        var dragInstanceId: String? = null
        // T04：被拖实例所属 pane（编辑输入反算用）
        var dragPaneId: String = "main"
        var dragPointIndex: Int? = null
        var lastAnchor: io.lkanvas.core.overlay.OverlayPoint? = null
        // brush 连续采样位置
        var lastBrushX = 0f
        var lastBrushY = 0f
        // 分隔条拖动上下文（paneWhich=-1 时 extraPaneId 生效，0.5.x 动态副图）
        var paneWhich = 0
        var extraPaneId: String? = null
        var paneStartValue = 0f
        var paneStartY = 0f
        var axisStartY = 0f

        // 轻点/触控回调上下文（批次 8a）
        var tapHitInstanceId: String? = null
        var lastTapNanos = 0L
        var lastTapX = 0f
        var lastTapY = 0f

        // 创建模式优先接管本次手势
        if (state.overlays.creatingId != null) {
            mode = GMode.CREATE
            state.setGestureOwner(GestureOwner.OVERLAY_EDIT)
            // Q04：创建上下文按 creating 实例的 pane 初始化（修复前 dragPaneId
            // 从默认 main 开始，副图创建的 brush 采样/加点用错 pane）
            state.overlays.creatingId?.let { cid ->
                state.overlays.get(cid)?.effectivePaneId?.let { dragPaneId = it }
            }
            // brush：按下即采首点，随后 Move 连续采样
            if (state.currentTool == "brush") {
                lastBrushX = down.position.x
                lastBrushY = down.position.y
                // T04：创建/采样按触点所在 pane 反算
                val anchor = state.overlayPointFromPx(down.position.x, down.position.y, paneId = state.paneIdAt(down.position.y))
                val id = state.overlays.creatingId
                if (anchor != null && id != null) {
                    state.overlays.addCreatePoint(id, anchor)
                    state.notifyOverlayChanged()
                }
            }
        } else {
            val hit = OverlayRenderer.hitTest(state, down.position.x, down.position.y, size.width.toFloat(), size.height.toFloat())
            if (hit != null) {
                tapHitInstanceId = hit.instanceId
                OverlayEventDispatch.onTouchStart(state, hit.instanceId, down.position.x, down.position.y)
                state.overlays.select(hit.instanceId)
                state.notifyOverlayChanged()
                if (hit.pointIndex != null && state.overlays.beginDrag(hit.instanceId, hit.pointIndex)) {
                    mode = GMode.POINT_DRAG
                    dragInstanceId = hit.instanceId
                    dragPointIndex = hit.pointIndex
                    // T04：编辑输入按被拖实例所属 pane 反算
                    dragPaneId = state.overlays.get(hit.instanceId)?.effectivePaneId ?: "main"
                    state.setGestureOwner(GestureOwner.OVERLAY_EDIT)
                } else if (hit.wholeDrag && state.overlays.beginDrag(hit.instanceId, null)) {
                    mode = GMode.WHOLE_DRAG
                    dragInstanceId = hit.instanceId
                    dragPaneId = state.overlays.get(hit.instanceId)?.effectivePaneId ?: "main"
                    // T04：编辑输入按被拖实例所属 pane 反算
                    lastAnchor = state.overlayPointFromPx(down.position.x, down.position.y, paneId = dragPaneId)
                    state.setGestureOwner(GestureOwner.OVERLAY_EDIT)
                }
            }
            // 分隔条与轴区（未命中覆盖物时判定；同一时刻仅一个所有者，A-33）
            if (mode == GMode.IDLE) {
                val sep = state.paneSeparatorAt(down.position.y, tolerancePx = 14f)
                // 0.5.x 动态副图分隔（内置分隔优先）
                val extraSep = if (sep == null) state.extraPaneSeparatorAt(down.position.y, tolerancePx = 14f) else null
                if (sep != null) {
                    paneWhich = sep
                    extraPaneId = null
                    paneStartValue = if (sep == 0) state.paneHeightsPx.vol else state.paneHeightsPx.macd
                    paneStartY = down.position.y
                    mode = GMode.PANE_ADJUST
                    state.setGestureOwner(GestureOwner.PANE_ADJUST)
                } else if (extraSep != null) {
                    paneWhich = -1
                    extraPaneId = extraSep
                    paneStartValue = state.extraPaneLayout.firstOrNull { it.first == extraSep }?.second?.second ?: 0f
                    paneStartY = down.position.y
                    mode = GMode.PANE_ADJUST
                    state.setGestureOwner(GestureOwner.PANE_ADJUST)
                } else if (state.isInAxisGutter(down.position.x)) {
                    axisStartY = down.position.y
                    mode = GMode.AXIS_ZOOM
                    state.setGestureOwner(GestureOwner.AXIS_ZOOM)
                }
            }
        }

        while (true) {
            val awaitingLongPress = mode == GMode.IDLE && !moved
            val event = if (awaitingLongPress) {
                val remaining = longPressMillis - (System.nanoTime() - downNanos) / 1_000_000
                withTimeoutOrNull(remaining.coerceAtLeast(1)) {
                    awaitPointerEvent(PointerEventPass.Main)
                }
            } else {
                awaitPointerEvent(PointerEventPass.Main)
            }

            // 长按到期且未移动未抬指 → 进入十字线（命中覆盖物时先派发 onRightClick，批次 8a）
            if (event == null) {
                if (tapHitInstanceId != null) {
                    OverlayEventDispatch.onRightClick(state, tapHitInstanceId, down.position.x, down.position.y)
                }
                mode = GMode.CROSSHAIR
                state.setGestureOwner(GestureOwner.CROSSHAIR)
                state.showCrosshairAt(down.position.x, down.position.y)
                continue
            }

            when (event.type) {
                PointerEventType.Press -> {
                    if (event.changes.count { it.pressed } >= 2 && mode != GMode.CREATE &&
                        mode != GMode.POINT_DRAG && mode != GMode.WHOLE_DRAG
                    ) {
                        mode = GMode.PINCH
                        state.setGestureOwner(GestureOwner.PINCH)
                        state.hideCrosshair()
                        pinchDist = null
                    }
                }

                PointerEventType.Move -> {
                    // 位移被父容器消费（接管/取消）：结束当前交互
                    if (event.changes.any { it.isConsumed && it.positionChanged() }) {
                        state.hideCrosshair()
                        break
                    }
                    when (mode) {
                        GMode.PINCH -> {
                            val ps = event.changes.map { it.position }
                            if (ps.size >= 2) {
                                val d = hypot(ps[0].x - ps[1].x, ps[0].y - ps[1].y)
                                val prev = pinchDist
                                if (prev != null && prev > 1f) {
                                    state.zoomAt(d / prev, (ps[0].x + ps[1].x) / 2f)
                                    event.changes.forEach { it.consume() }
                                }
                                pinchDist = d
                            }
                        }

                        GMode.CROSSHAIR -> {
                            val c = event.changes.firstOrNull() ?: continue
                            state.moveCrosshair(c.position.x, c.position.y)
                            event.changes.forEach { it.consume() }
                        }

                        GMode.PAN -> {
                            val c = event.changes.firstOrNull() ?: continue
                            velocity.addPosition(c.uptimeMillis, c.position)
                            state.panBy(c.position.x - c.previousPosition.x)
                            event.changes.forEach { it.consume() }
                        }

                        GMode.IDLE -> {
                            val c = event.changes.firstOrNull() ?: continue
                            val dxTotal = c.position.x - down.position.x
                            val dyTotal = c.position.y - down.position.y
                            if (abs(dxTotal) > slop || abs(dyTotal) > slop) {
                                moved = true
                                if (abs(dxTotal) >= abs(dyTotal)) {
                                    mode = GMode.PAN
                                    state.setGestureOwner(GestureOwner.PAN)
                                    velocity.addPosition(c.uptimeMillis, c.position)
                                }
                                // 纵向为主：不消费不进入平移，父容器接管
                            }
                        }

                        GMode.POINT_DRAG -> {
                            val c = event.changes.firstOrNull() ?: continue
                            val anchor = state.overlayPointFromPx(c.position.x, c.position.y, paneId = dragPaneId)
                            if (anchor != null) {
                                // 批次 8b：模板提供 performEventPressedMove 时委托（自定义拖动定位）
                                val hook = OverlayEventDispatch.templateOf(state, dragInstanceId)?.performEventPressedMove
                                if (hook != null) {
                                    hook(dragInstanceId!!, dragPointIndex ?: -1, anchor)
                                    state.notifyOverlayChanged()
                                } else {
                                    state.overlays.drag(dragInstanceId, dragPointIndex, to = anchor)
                                    state.notifyOverlayChanged()
                                }
                            }
                            OverlayEventDispatch.onTouchMove(state, dragInstanceId, c.position.x, c.position.y)
                            event.changes.forEach { it.consume() }
                        }

                        GMode.WHOLE_DRAG -> {
                            val c = event.changes.firstOrNull() ?: continue
                            val anchor = state.overlayPointFromPx(c.position.x, c.position.y, paneId = dragPaneId)
                            val last = lastAnchor
                            if (anchor != null && last != null) {
                                state.overlays.drag(
                                    dragInstanceId,
                                    pointIndex = null,
                                    deltaTimestamp = anchor.timestamp - last.timestamp,
                                    deltaValue = anchor.value - last.value,
                                )
                                lastAnchor = anchor
                                state.notifyOverlayChanged()
                            }
                            OverlayEventDispatch.onTouchMove(state, dragInstanceId, c.position.x, c.position.y)
                            event.changes.forEach { it.consume() }
                        }

                        GMode.CREATE -> {
                            // brush：按住拖动连续采样（4px 去抖）；其余模板点击在 Release 加点
                            if (state.currentTool == "brush") {
                                val c = event.changes.firstOrNull() ?: continue
                                if (c.positionChanged()) {
                                    val dx = c.position.x - lastBrushX
                                    val dy = c.position.y - lastBrushY
                                    if (hypot(dx, dy) >= 4f) {
                                        lastBrushX = c.position.x
                                        lastBrushY = c.position.y
                                        val anchor = state.overlayPointFromPx(c.position.x, c.position.y, paneId = dragPaneId)
                                        val id = state.overlays.creatingId
                                        if (anchor != null && id != null) {
                                            state.overlays.addCreatePoint(id, anchor)
                                            state.notifyOverlayChanged()
                                        }
                                    }
                                }
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }

                        GMode.PANE_ADJUST -> {
                            val c = event.changes.firstOrNull() ?: continue
                            if (paneWhich >= 0) {
                                state.adjustPane(paneWhich, paneStartValue, c.position.y - paneStartY)
                            } else if (extraPaneId != null) {
                                state.adjustExtraPane(extraPaneId!!, paneStartValue, c.position.y - paneStartY)
                            }
                            event.changes.forEach { it.consume() }
                        }

                        GMode.AXIS_ZOOM -> {
                            val c = event.changes.firstOrNull() ?: continue
                            state.axisZoomBy(c.position.y - axisStartY)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }

                PointerEventType.Release -> {
                    val stillPressed = event.changes.count { it.pressed }
                    if (mode == GMode.PINCH && stillPressed == 1) {
                        // 双指抬一指：降级等待，避免残指误平移
                        mode = GMode.IDLE
                        moved = true
                        pinchDist = null
                        continue
                    }
                    if (stillPressed > 0) continue
                    when (mode) {
                        GMode.CROSSHAIR -> state.hideCrosshair()
                        GMode.PAN -> state.fling(velocity.calculateVelocity().x)
                        GMode.IDLE -> if (!moved) {
                            // 批次 7c：tooltip feature 命中优先（手势端分发三 ActionType）
                            val feature = state.tooltipFeatureAt(down.position.x, down.position.y)
                            when {
                                feature != null ->
                                    state.onTooltipFeatureClick?.invoke(feature.first, feature.second, down.position.x.toDouble(), down.position.y.toDouble())

                                tapHitInstanceId != null -> {
                                    // 批次 8a：双击检测（<400ms 内同位两次）→ onDoubleClick，否则 onClick
                                    val now = System.nanoTime()
                                    val isDouble = lastTapNanos != 0L &&
                                        (now - lastTapNanos) < 400_000_000L &&
                                        hypot(down.position.x - lastTapX, down.position.y - lastTapY) < 40f
                                    if (isDouble) {
                                        OverlayEventDispatch.onDoubleClick(state, tapHitInstanceId, down.position.x, down.position.y)
                                        lastTapNanos = 0L
                                    } else {
                                        OverlayEventDispatch.onClick(state, tapHitInstanceId, down.position.x, down.position.y)
                                        lastTapNanos = now
                                        lastTapX = down.position.x
                                        lastTapY = down.position.y
                                    }
                                }

                                else -> state.tapCandle(down.position.x)
                            }
                        }
                        GMode.PINCH -> Unit

                        GMode.CREATE -> {
                            // brush 已在按下/移动中采样，抬起只结束本次手势（宿主按完成/取消收尾）
                            if (state.currentTool != "brush") {
                                val c = event.changes.firstOrNull() ?: break
                                val anchor = state.overlayPointFromPx(c.position.x, c.position.y, paneId = dragPaneId)
                                if (anchor != null) {
                                    val id = state.overlays.creatingId
                                    if (id != null) {
                                        state.overlays.addCreatePoint(id, anchor)
                                        state.notifyOverlayChanged()
                                    }
                                }
                            }
                        }
                        GMode.POINT_DRAG, GMode.WHOLE_DRAG -> {
                            state.overlays.endDrag(dragInstanceId, dragPointIndex)
                            state.notifyOverlayChanged()
                            OverlayEventDispatch.onTouchEnd(state, dragInstanceId, down.position.x, down.position.y)
                        }
                        GMode.PANE_ADJUST, GMode.AXIS_ZOOM -> Unit
                    }
                    break
                }
            }
        }
        state.endGesture()
    }
}
