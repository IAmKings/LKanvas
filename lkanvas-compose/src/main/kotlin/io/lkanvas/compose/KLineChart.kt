package io.lkanvas.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import io.lkanvas.compose.draw.ChartText
import io.lkanvas.compose.draw.PaneRenderers
import io.lkanvas.compose.draw.drawTag
import io.lkanvas.compose.draw.drawText
import io.lkanvas.compose.draw.visibleRange
import io.lkanvas.compose.gesture.chartGestures
import io.lkanvas.compose.state.ChartState
import io.lkanvas.compose.theme.ChartLayoutConfig
import io.lkanvas.compose.theme.ChartTheme

/**
 * K 线图组件（Alpha：主图 candle_solid + VOL + MACD 三 pane）。
 *
 * 数据与指标由 [state] 提供；本组件只负责布局、绘制与手势（PRD R-07/R-10）。
 * 不为每根 candle 创建 Composable 节点；平移/缩放仅触发 Canvas 重绘。
 */
@Composable
fun KLineChart(
    state: ChartState,
    modifier: Modifier = Modifier,
    theme: ChartTheme = ChartTheme(),
    config: ChartLayoutConfig = ChartLayoutConfig(),
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textSizePx = with(density) { config.axisTextSize.toPx() }

    val subPaneH = with(density) { config.subPaneHeight.toPx() }
    val timeAxisH = with(density) { config.timeAxisHeight.toPx() }
    val gutterW = with(density) { config.rightAxisGutter.toPx() }
    val legendTextSizePx = with(density) { config.legendTextSize.toPx() }

    // 布局回调（尺寸变化）
    val layoutModifier = Modifier.onSizeChanged { size ->
        val contentW = size.width - gutterW
        val mainH = (size.height - 2 * subPaneH - timeAxisH).coerceAtLeast(0f)
        state.onLayout(contentW, ChartState.PaneHeights(mainH, subPaneH, subPaneH, timeAxisH))
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(layoutModifier)
            .chartGestures(state),
    ) {
        // 读取 viewRevision 触发重绘
        val rev = state.viewRevision
        val panes = state.paneHeightsPx
        val contentLeft = 0f
        val contentRight = size.width - gutterW
        val text = ChartText(textMeasurer, theme.axisText, textSizePx)

        drawRect(theme.background, size = size)

        if (state.dataSnapshot.candles.isEmpty()) return@Canvas

        val mainTop = 0f
        val mainBottom = panes.main
        val volTop = mainBottom + 1f
        val volBottom = volTop + panes.vol
        val macdTop = volBottom + 1f
        val macdBottom = macdTop + panes.macd

        // 分隔线
        drawLine(theme.axisLine, Offset(0f, mainBottom), Offset(size.width, mainBottom), 1f)
        drawLine(theme.axisLine, Offset(0f, volBottom), Offset(size.width, volBottom), 1f)

        val crosshair = state.crosshairPosition
        // 主 pane 顶部两行图例（OHLC + MA）的避让高度
        val legendHeight = legendTextSizePx * 3.2f
        with(PaneRenderers) {
            drawMainPane(state, theme, contentLeft, contentRight, mainTop, mainBottom, text, crosshair, legendHeight)
            drawVolumePane(state, theme, contentLeft, contentRight, volTop, volBottom, text)
            drawMacdPane(state, theme, contentLeft, contentRight, macdTop, macdBottom, text)
        }
        // 覆盖物（主 pane 区域；读取 overlayRevision 触发重绘）
        with(io.lkanvas.compose.draw.OverlayRenderer) {
            drawOverlays(state, theme, contentRight)
        }
        drawTimeAxis(state, theme, text, contentLeft, contentRight, macdBottom, panes.timeAxis)
        drawLegend(state, theme, text, legendTextSizePx, crosshair, gutterW, size.width)
        if (crosshair != null) {
            // 十字线当前值标签（避让图例区）
            drawCrosshairLabels(state, theme, text, crosshair, contentRight, legendHeight)
        }
    }
}

/** 时间轴：按可见范围与间隔取标签（跨日显示日期）；实测宽度防重叠。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTimeAxis(
    state: ChartState,
    theme: ChartTheme,
    text: ChartText,
    left: Float,
    right: Float,
    top: Float,
    height: Float,
) {
    val candles = state.dataSnapshot.candles
    val range = state.viewport.visibleRange(candles.size)
    if (range.count <= 0) return
    val interval = if (candles.size > 1) candles[1].timestamp - candles[0].timestamp else 60_000L
    var prevTs: Long? = null
    var lastLabelRight = Float.NEGATIVE_INFINITY
    var i = range.first
    // 逐根尝试：跨日标签优先绘制，普通时间标签与已绘制区域重叠则跳过
    while (i <= range.last) {
        val c = candles[i]
        val label = ChartText.timeAxisLabel(c.timestamp, prevTs, interval)
        if (label.isNotEmpty()) {
            val x = state.viewport.candleCenterX(i.toDouble()).toFloat()
            val width = measureLabelWidth(text, label)
            val drawX = (x - width / 2f).coerceIn(left, (right - width).coerceAtLeast(left))
            if (drawX > lastLabelRight + 6f) {
                drawText(text, label, Offset(drawX, top + 3f), theme.axisText)
                lastLabelRight = drawX + width
            }
        }
        prevTs = c.timestamp
        i++
    }
}

/** 图例：主 pane 顶部 OHLC 摘要 + MA 值（跟随十字线或末根）；实测宽度排布防重叠。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLegend(
    state: ChartState,
    theme: ChartTheme,
    text: ChartText,
    textSizePx: Float,
    crosshair: Offset?,
    gutterW: Float,
    totalWidth: Float,
) {
    val candles = state.dataSnapshot.candles
    if (candles.isEmpty()) return
    val vp = state.viewport
    val idx = crosshair?.let { vp.logicalIndexAt(it.x.toDouble()).toInt().coerceIn(candles.indices) }
        ?: (candles.size - 1)
    val c = candles[idx]
    val contentWidth = totalWidth - gutterW
    var x = 8f
    val y = 4f
    val prev = candles.getOrNull(idx - 1)
    val priceColor = when {
        prev == null -> theme.legendValueNeutral
        c.close > prev.close -> theme.legendValueUp
        c.close < prev.close -> theme.legendValueDown
        else -> theme.legendValueNeutral
    }
    val items = listOf(
        "O ${text.price(c.open)}" to theme.legendText,
        "H ${text.price(c.high)}" to theme.legendText,
        "L ${text.price(c.low)}" to theme.legendText,
        "C ${text.price(c.close)}" to priceColor,
    )
    for ((s, color) in items) {
        if (x > contentWidth - 40f) break
        drawText(text, s, Offset(x, y), color)
        x += measureLabelWidth(text, s) + 10f
    }

    // MA 图例（列序 = 参数序）
    state.maResult?.let { ma ->
        var lx = 8f
        val ly = y + textSizePx * 1.4f
        for ((k, key) in ma.result.keys.withIndex()) {
            val v = ma.result[key].getOrNull(idx) ?: Double.NaN
            val color = theme.linePalette[k % theme.linePalette.size]
            val period = ma.params.getOrNull(k)?.toInt() ?: (k + 1)
            val s = "MA$period ${text.price(v)}"
            if (lx > contentWidth - 40f) break
            drawText(text, s, Offset(lx, ly), color)
            lx += measureLabelWidth(text, s) + 10f
        }
    }
}

/** 测量文本宽度（图例/时间轴排布用；TextMeasurer 结果缓存于 ChartText）。 */
private fun measureLabelWidth(text: ChartText, s: String): Float = text.measureWidth(s)

/** 十字线当前值标签（右侧价格 + 底部时间）。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCrosshairLabels(
    state: ChartState,
    theme: ChartTheme,
    text: ChartText,
    crosshair: Offset,
    contentRight: Float,
    legendHeight: Float,
) {
    // 右侧标签由各 pane 的轴体系决定；在主 pane 区域显示十字线 y 对应值（按轴规格）
    val candles = state.dataSnapshot.candles
    val range = state.viewport.visibleRange(candles.size)
    if (range.count <= 0) return
    val visible = candles.subList(range.first, range.last + 1)
    val (lo, hi) = io.lkanvas.core.axis.LinearYAxis.rangeOfCandles(visible) ?: return
    val spec = state.effectiveMainAxisSpec()
    val value = io.lkanvas.core.axis.YAxisMath.yToValue(
        spec, crosshair.y.toDouble(), lo, hi, 0.0, state.paneHeightsPx.main.toDouble(),
    )
    val clampedY = crosshair.y.coerceIn(legendHeight + text.tagHeight / 2f, Float.MAX_VALUE)
    drawTag(text, value, clampedY, contentRight, theme.crosshairLine)
}
