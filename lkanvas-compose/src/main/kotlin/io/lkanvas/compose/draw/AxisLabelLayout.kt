package io.lkanvas.compose.draw

/** Returned indices refer to the original ticks; positions are text tops in pixels. */
internal fun axisLabelPositions(centers: List<Float>, height: Float, top: Float, bottom: Float): List<Pair<Int, Float>> {
    if (height <= 0f || height > bottom - top) return emptyList()
    val out = mutableListOf<Pair<Int, Float>>()
    var lastBottom = top - 2f
    for ((index, center) in centers.withIndex().sortedBy { it.value }) {
        if (!center.isFinite() || center < top || center > bottom) continue
        val y = (center - height / 2f).coerceIn(top, bottom - height)
        if (y < lastBottom + 2f) continue
        out += index to y
        lastBottom = y + height
    }
    return out
}
