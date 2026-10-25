package io.lkanvas.compose.draw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import io.lkanvas.compose.state.ChartState
import io.lkanvas.compose.theme.ChartTheme
import io.lkanvas.core.axis.LinearYAxis
import io.lkanvas.core.data.Candle
import io.lkanvas.core.indicator.IndicatorResultState
import io.lkanvas.core.indicator.PlotType
import io.lkanvas.core.viewport.Viewport
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** 可见窗口（数据索引范围）。 */
data class VisibleRange(val first: Int, val last: Int) {
    val count: Int get() = last - first + 1
}

fun Viewport.visibleRange(dataCount: Int): VisibleRange {
    if (dataCount == 0) return VisibleRange(0, -1)
    val from = floor(scrollIndex).toInt().coerceAtLeast(0)
    val to = ceil(scrollIndex + visibleCount).toInt().coerceAtMost(dataCount - 1)
    return VisibleRange(from, max(from, to))
}

/**
 * 各 pane 绘制器（PRD R-07：只绘可见区间；Double 计算最后一步转 Float；
 * 无逐 candle Composable 节点）。
 */
object PaneRenderers {

    /** 主 pane：网格 + candle + MA 线 + 末价线。[legendHeight] 为顶部图例避让区。 */
    fun DrawScope.drawMainPane(
        state: ChartState,
        theme: ChartTheme,
        contentLeft: Float,
        contentRight: Float,
        top: Float,
        bottom: Float,
        text: ChartText,
        crosshair: Offset?,
        legendHeight: Float = 0f,
    ) {
        val candles = state.dataSnapshot.candles
        val range = state.viewport.visibleRange(candles.size)
        if (range.count <= 0) return
        val visible = candles.subList(range.first, range.last + 1)

        val (lo, hi) = LinearYAxis.rangeOfCandles(visible) ?: return
        val y: (Double) -> Double = { v -> LinearYAxis.valueToY(v, lo, hi, top.toDouble(), bottom.toDouble()) }

        drawGrid(theme, text, lo, hi, contentLeft, contentRight, top, bottom)
        clipRect(left = contentLeft, top = top, right = contentRight, bottom = bottom) {
            drawCandles(state, theme, visible, range.first, y)
            state.maResult?.let { drawLinePlots(it, state.viewport, range, y, theme.linePalette) }
            drawLastPriceLine(candles, y, theme, contentLeft, contentRight, text, legendHeight)
        }
        drawYAxisLabels(text, theme, LinearYAxis.ticks(lo, hi), y, contentRight, clipTop = legendHeight)
        if (crosshair != null) drawCrosshair(theme, crosshair, contentLeft, contentRight, top, bottom)
    }

    /** VOL pane：成交量柱 + 均量线。 */
    fun DrawScope.drawVolumePane(
        state: ChartState,
        theme: ChartTheme,
        contentLeft: Float,
        contentRight: Float,
        top: Float,
        bottom: Float,
        text: ChartText,
    ) {
        val candles = state.dataSnapshot.candles
        val range = state.viewport.visibleRange(candles.size)
        if (range.count <= 0) return
        val visible = candles.subList(range.first, range.last + 1)
        val result = state.volResult ?: return

        val maxVol = maxOf(
            visible.maxOf { it.volume ?: Double.NaN }.takeIf { it.isFinite() } ?: 0.0,
            result.result.columns.filterKeys { it.startsWith("ma") }.values
                .flatMap { arr -> arr.slice(range.first..range.last).filter { it.isFinite() } }
                .maxOrNull() ?: 0.0,
            1e-12,
        )
        val y: (Double) -> Double = { v -> LinearYAxis.valueToY(v, 0.0, maxVol, top.toDouble(), bottom.toDouble()) }

        drawGrid(theme, text, 0.0, maxVol, contentLeft, contentRight, top, bottom, tickCount = 3)
        clipRect(left = contentLeft, top = top, right = contentRight, bottom = bottom) {
            drawVolumeBars(state, theme, result, range, y)
            drawLinePlots(result, state.viewport, range, y, theme.linePalette, skipKeys = listOf("volume"))
        }
        drawYAxisLabels(text, theme, LinearYAxis.ticks(0.0, maxVol, 3), y, contentRight)
    }

    /** MACD pane：DIF/DEA 线 + 柱。 */
    fun DrawScope.drawMacdPane(
        state: ChartState,
        theme: ChartTheme,
        contentLeft: Float,
        contentRight: Float,
        top: Float,
        bottom: Float,
        text: ChartText,
    ) {
        val candles = state.dataSnapshot.candles
        val range = state.viewport.visibleRange(candles.size)
        if (range.count <= 0) return
        val result = state.macdResult ?: return

        val values = sequenceOf("dif", "dea").flatMap { k ->
            result.result[k].slice(range.first..range.last).filter { it.isFinite() }
        } + sequenceOf(0.0)
        val bars = result.result["macd"].slice(range.first..range.last).filter { it.isFinite() }
        var lo = min(values.minOrNull() ?: 0.0, bars.minOrNull() ?: 0.0)
        var hi = max(values.maxOrNull() ?: 0.0, bars.maxOrNull() ?: 0.0)
        if (lo == hi) { lo -= 1.0; hi += 1.0 }
        val y: (Double) -> Double = { v -> LinearYAxis.valueToY(v, lo, hi, top.toDouble(), bottom.toDouble()) }

        drawGrid(theme, text, lo, hi, contentLeft, contentRight, top, bottom, tickCount = 3)
        clipRect(left = contentLeft, top = top, right = contentRight, bottom = bottom) {
            drawMacdBars(state, theme, result, range, y)
            drawLinePlots(result, state.viewport, range, y, listOf(theme.macdDif, theme.macdDea), keys = listOf("dif", "dea"))
        }
        drawYAxisLabels(text, theme, LinearYAxis.ticks(lo, hi, 3), y, contentRight)
    }

    // ---------- 内部 ----------

    private fun DrawScope.drawGrid(
        theme: ChartTheme,
        text: ChartText,
        lo: Double,
        hi: Double,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        tickCount: Int = 5,
    ) {
        for (t in LinearYAxis.ticks(lo, hi, tickCount)) {
            val yy = LinearYAxis.valueToY(t, lo, hi, top.toDouble(), bottom.toDouble()).toFloat()
            drawLine(theme.gridLine, Offset(left, yy), Offset(right, yy), 1f)
        }
    }

    private fun DrawScope.drawCandles(
        state: ChartState,
        theme: ChartTheme,
        visible: List<Candle>,
        firstIndex: Int,
        y: (Double) -> Double,
    ) {
        val vp = state.viewport
        val gap = (vp.barSpacing * state.gapRatio).coerceAtMost(vp.barSpacing - 1.0).coerceAtLeast(0.0)
        val bodyWidth = (vp.barSpacing - gap).coerceAtLeast(1.0)
        for (i in visible.indices) {
            val c = visible[i]
            val x = vp.candleCenterX((firstIndex + i).toDouble())
            val color = when {
                c.close > c.open -> theme.upColor
                c.close < c.open -> theme.downColor
                else -> theme.noChangeColor
            }
            // 影线
            drawLine(
                color,
                Offset(x.toFloat(), y(c.high).toFloat()),
                Offset(x.toFloat(), y(c.low).toFloat()),
                1f,
            )
            // 实体（最小 1px，开收同价画横线）
            val yOpen = y(c.open)
            val yClose = y(c.close)
            val top = min(yOpen, yClose).toFloat()
            val bottom = max(yOpen, yClose).toFloat()
            val height = max(bottom - top, 1f)
            drawRect(
                color,
                topLeft = Offset((x - bodyWidth / 2).toFloat(), top),
                size = Size(bodyWidth.toFloat(), height),
            )
        }
    }

    private fun DrawScope.drawVolumeBars(
        state: ChartState,
        theme: ChartTheme,
        result: IndicatorResultState,
        range: VisibleRange,
        y: (Double) -> Double,
    ) {
        val vp = state.viewport
        val vols = result.result["volume"]
        val candles = state.dataSnapshot.candles
        val bodyWidth = (vp.barSpacing * (1.0 - state.gapRatio)).coerceAtLeast(1.0)
        for (i in range.first..range.last) {
            val v = vols[i]
            if (v.isNaN()) continue
            val c = candles[i]
            val color = when {
                c.close > c.open -> theme.upColor
                c.close < c.open -> theme.downColor
                else -> theme.noChangeColor
            }
            val x = vp.candleCenterX(i.toDouble())
            val topY = y(v)
            val bottomY = y(0.0)
            drawRect(
                color,
                topLeft = Offset((x - bodyWidth / 2).toFloat(), topY.toFloat()),
                size = Size(bodyWidth.toFloat(), max(bottomY.toFloat() - topY.toFloat(), 1f)),
            )
        }
    }

    private fun DrawScope.drawMacdBars(
        state: ChartState,
        theme: ChartTheme,
        result: IndicatorResultState,
        range: VisibleRange,
        y: (Double) -> Double,
    ) {
        val vp = state.viewport
        val bars = result.result["macd"]
        val bodyWidth = (vp.barSpacing * (1.0 - state.gapRatio)).coerceAtLeast(1.0)
        val zero = y(0.0).toFloat()
        for (i in range.first..range.last) {
            val v = bars[i]
            if (v.isNaN()) continue
            val color = if (v >= 0) theme.macdBarUp else theme.macdBarDown
            val x = vp.candleCenterX(i.toDouble())
            val vy = y(v).toFloat()
            drawRect(
                color,
                topLeft = Offset((x - bodyWidth / 2).toFloat(), min(vy, zero)),
                size = Size(bodyWidth.toFloat(), max(kotlin.math.abs(vy - zero), 1f)),
            )
        }
    }

    /** 指标线绘制（跳过 NaN；palette 按 key 序取色）。 */
    private fun DrawScope.drawLinePlots(
        result: IndicatorResultState,
        vp: Viewport,
        range: VisibleRange,
        y: (Double) -> Double,
        palette: List<Color>,
        skipKeys: List<String> = emptyList(),
        keys: List<String>? = null,
    ) {
        val lineKeys = keys ?: result.result.keys.filterNot { it in skipKeys }
        for ((idx, key) in lineKeys.withIndex()) {
            val column = result.result[key]
            val color = palette[idx % palette.size]
            val path = Path()
            var started = false
            for (i in range.first..range.last) {
                val v = column[i]
                if (v.isNaN()) {
                    started = false
                    continue
                }
                val x = vp.candleCenterX(i.toDouble()).toFloat()
                val yy = y(v).toFloat()
                if (!started) {
                    path.moveTo(x, yy)
                    started = true
                } else {
                    path.lineTo(x, yy)
                }
            }
            drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
        }
    }

    private fun DrawScope.drawLastPriceLine(
        candles: List<Candle>,
        y: (Double) -> Double,
        theme: ChartTheme,
        left: Float,
        right: Float,
        text: ChartText,
        legendHeight: Float = 0f,
    ) {
        val last = candles.lastOrNull() ?: return
        // 末价标签避让图例区
        val yy = y(last.close).toFloat().coerceIn(legendHeight + text.tagHeight, Float.MAX_VALUE)
        drawLine(theme.lastPriceLine, Offset(left, yy), Offset(right, yy), 1f)
        drawTag(text,
            last.close,
            yy,
            right,
            if (last.close >= last.open) theme.upColor else theme.downColor,
        )
    }

    private fun DrawScope.drawCrosshair(
        theme: ChartTheme,
        crosshair: Offset,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
    ) {
        drawLine(theme.crosshairLine, Offset(crosshair.x, top), Offset(crosshair.x, bottom), 1f)
        drawLine(theme.crosshairLine, Offset(left, crosshair.y), Offset(right, crosshair.y), 1f)
    }

    private fun DrawScope.drawYAxisLabels(
        text: ChartText,
        theme: ChartTheme,
        ticks: List<Double>,
        y: (Double) -> Double,
        axisRight: Float,
        clipTop: Float = 0f,
    ) {
        var lastTop = Float.NEGATIVE_INFINITY
        for (t in ticks) {
            val top = y(t).toFloat() - text.textHeight / 2f
            if (top < lastTop + 2f) continue // 相邻刻度垂直重叠时跳过
            if (top < clipTop) continue // 避让顶部图例区
            drawText(text,
                t,
                Offset(axisRight + 6f, top),
                theme.axisText,
            )
            lastTop = top
        }
    }
}
