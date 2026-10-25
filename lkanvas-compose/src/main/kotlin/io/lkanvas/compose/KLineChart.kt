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
        with(PaneRenderers) {
            drawMainPane(state, theme, contentLeft, contentRight, mainTop, mainBottom, text, crosshair)
            drawVolumePane(state, theme, contentLeft, contentRight, volTop, volBottom, text)
            drawMacdPane(state, theme, contentLeft, contentRight, macdTop, macdBottom, text)
        }
        drawTimeAxis(state, theme, text, contentLeft, contentRight, macdBottom, panes.timeAxis)
        drawLegend(state, theme, text, legendTextSizePx, crosshair, gutterW, size.width)
        if (crosshair != null) {
            // 十字线当前值标签
            drawCrosshairLabels(state, theme, text, crosshair, contentRight)
        }
    }
}

/** 时间轴：按可见范围与间隔取标签（跨日显示日期）。 */
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
    // 标签间隔：约每 90px 一个
    val step = maxOf(1, (90.0 / state.viewport.barSpacing).toInt())
    var prevTs: Long? = null
    var i = range.first
    while (i <= range.last) {
        val c = candles[i]
        val label = ChartText.timeAxisLabel(c.timestamp, prevTs, interval)
        if (label.isNotEmpty()) {
            val x = state.viewport.candleCenterX(i.toDouble()).toFloat()
            drawText(text, label, Offset((x - 18f).coerceIn(left, right - 40f), top + 3f), theme.axisText)
        }
        prevTs = c.timestamp
        i += step
    }
}

/** 图例：主 pane 顶部 OHLC 摘要 + MA/VOL/MACD 值（跟随十字线或末根）。 */
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
    var x = 8f
    val y = 4f
    val prev = candles.getOrNull(idx - 1)
    val priceColor = when {
        prev == null -> theme.legendValueNeutral
        c.close > prev.close -> theme.legendValueUp
        c.close < prev.close -> theme.legendValueDown
        else -> theme.legendValueNeutral
    }
    drawText(text, "O ${text.price(c.open)}", Offset(x, y), theme.legendText); x += 84f
    drawText(text, "H ${text.price(c.high)}", Offset(x, y), theme.legendText); x += 84f
    drawText(text, "L ${text.price(c.low)}", Offset(x, y), theme.legendText); x += 84f
    drawText(text, "C ${text.price(c.close)}", Offset(x, y), priceColor)

    // MA 图例（列序 = 参数序，titles 由参数推导）
    state.maResult?.let { ma ->
        var lx = 8f
        val ly = y + textSizePx * 1.3f
        for ((k, key) in ma.result.keys.withIndex()) {
            val v = ma.result[key].getOrNull(idx) ?: Double.NaN
            val color = theme.linePalette[k % theme.linePalette.size]
            val period = ma.params.getOrNull(k)?.toInt() ?: (k + 1)
            drawText(text, "MA$period ${text.price(v)}", Offset(lx, ly), color)
            lx += textSizePx * 9f
        }
    }
}

/** 十字线当前值标签（右侧价格 + 底部时间）。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCrosshairLabels(
    state: ChartState,
    theme: ChartTheme,
    text: ChartText,
    crosshair: Offset,
    contentRight: Float,
) {
    // 右侧标签由各 pane 的轴体系决定；Alpha 在主 pane 区域显示十字线 y 对应值
    val candles = state.dataSnapshot.candles
    val range = state.viewport.visibleRange(candles.size)
    if (range.count <= 0) return
    val visible = candles.subList(range.first, range.last + 1)
    val (lo, hi) = io.lkanvas.core.axis.LinearYAxis.rangeOfCandles(visible) ?: return
    val value = io.lkanvas.core.axis.LinearYAxis.yToValue(
        crosshair.y.toDouble(), lo, hi, 0.0, state.paneHeightsPx.main.toDouble(),
    )
    drawTag(text, value, crosshair.y, contentRight, theme.crosshairLine)
}
