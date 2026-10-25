package io.lkanvas.compose.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import io.lkanvas.compose.state.ChartState
import io.lkanvas.compose.state.GestureOwner
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot

private enum class GMode { IDLE, PAN, CROSSHAIR, PINCH }

/**
 * 图表手势状态机（PRD R-10 Alpha 部分）：
 * - 单指拖动（超过触距且横向为主）→ 平移（PAN），释放带基础惯性；
 *   纵向为主的位移不消费，外层 LazyColumn/Pager 可正常接管（A-32 基础）
 * - 双指 → 缩放（PINCH，中点为锚）；第二指加入时结束平移/十字线；
 *   双指抬一指降级等待，不再误触发平移
 * - 长按（未移动）→ 十字线（CROSSHAIR），移动吸附 candle 中心，抬起隐藏
 * - 短按无位移 → 点击 candle 事件
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

            // 长按到期且未移动未抬指 → 进入十字线
            if (event == null) {
                mode = GMode.CROSSHAIR
                state.setGestureOwner(GestureOwner.CROSSHAIR)
                state.showCrosshairAt(down.position.x, down.position.y)
                continue
            }

            when (event.type) {
                PointerEventType.Press -> {
                    if (event.changes.count { it.pressed } >= 2) {
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
                        GMode.IDLE -> if (!moved) state.tapCandle(down.position.x)
                        GMode.PINCH -> Unit
                    }
                    break
                }
            }
        }
        state.endGesture()
    }
}
