package io.lkanvas.compose.draw

import androidx.compose.ui.geometry.Rect
import kotlin.math.abs

/** Keep the upstream left/right and high/low anchors; only move text when its real box collides. */
internal fun priceMarkerBounds(
    anchorX: Float, preferredTop: Float, width: Float, height: Float,
    right: Float, bottom: Float, exclusions: List<Rect>,
): Rect {
    val x = (if (anchorX > right / 2f) anchorX - 6f - width else anchorX + 6f)
        .coerceIn(0f, (right - width).coerceAtLeast(0f))
    val top = preferredTop.coerceIn(0f, (bottom - height).coerceAtLeast(0f))
    val candidates = listOf(top) + exclusions.flatMap { listOf(it.bottom + 2f, it.top - height - 2f) }
    val boxes = candidates.map { Rect(x, it, x + width, it + height) }
    val vertical = boxes.filter { it.top >= 0f && it.bottom <= bottom && exclusions.none(it::overlaps) }
        .minByOrNull { abs(it.top - top) }
    if (vertical != null) return vertical
    // 换行图例占满左侧高度时，保留价格高度并尝试相邻的水平空位。
    val horizontal = exclusions.flatMap { listOf(it.left - width - 2f, it.right + 2f) }
        .map { Rect(it, top, it + width, top + height) }
        .filter { it.left >= 0f && it.right <= right && it.bottom <= bottom && exclusions.none(it::overlaps) }
        .minByOrNull { abs(it.left - x) }
    // shortcut: if neither direction has space, retain the clamped anchor; crowded panes need a separate policy.
    return horizontal ?: Rect(x, top, x + width, top + height)
}
