package io.lkanvas.compose.draw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import io.lkanvas.compose.state.ChartState
import io.lkanvas.compose.theme.ChartTheme
import io.lkanvas.core.axis.LinearYAxis
import io.lkanvas.core.axis.YAxisMath

/**
 * 可重放图表渲染器（PRD R-07/R-15）：KLineChart 的 Canvas 与 PNG 导出
 * 共用同一绘制路径，保证「所见即所得」且导出内容与屏幕一致。
 *
 * 单 revision 保证（PRD R-15）：全部输入在 [render] 内一次性读取；
 * 调用方须在主线程同步调用（与快照更新同一线程，同步块内不交错）。
 */
object ChartRenderer {

    /** 渲染参数（全部为 px，与密度无关；由调用方完成 dp→px）。 */
    data class Params(
        val width: Float,
        val height: Float,
        val gutterWidth: Float,
        val legendTextSizePx: Float,
        val axisTextSizePx: Float,
        /** false 时不绘制十字线/交互层（导出选项，PRD R-15）。 */
        val includeCrosshair: Boolean,
        /** false 时跳过内置图例（宿主 legendSlot 定制或隐藏，PRD R-11/T-24）。 */
        val drawLegend: Boolean = true,
    )

    fun DrawScope.render(
        state: ChartState,
        theme: ChartTheme,
        params: Params,
        textMeasurer: TextMeasurer,
    ) {
        val panes = state.paneHeightsPx
        val contentRight = params.width - params.gutterWidth
        val text = ChartText(textMeasurer, theme.axisText, params.axisTextSizePx)

        drawRect(theme.background, size = size)

        if (state.dataSnapshot.candles.isEmpty()) return

        val mainTop = 0f
        val mainBottom = panes.main
        val volTop = mainBottom + 1f
        val volBottom = volTop + panes.vol
        val macdTop = volBottom + 1f
        val macdBottom = macdTop + panes.macd

        // 分隔线（Styles separator.color/size 消费）
        drawLine(theme.separatorColor, Offset(0f, mainBottom), Offset(params.width, mainBottom), theme.separatorSizePx)
        drawLine(theme.separatorColor, Offset(0f, volBottom), Offset(params.width, volBottom), theme.separatorSizePx)

        val crosshair = if (params.includeCrosshair && theme.crosshairShow) state.crosshairPosition else null
        // 主 pane 顶部两行图例（OHLC + MA）的避让高度
        val legendHeight = params.legendTextSizePx * 3.2f
        with(PaneRenderers) {
            drawMainPane(state, theme, 0f, contentRight, mainTop, mainBottom, text, crosshair, legendHeight)
            drawVolumePane(state, theme, 0f, contentRight, volTop, volBottom, text)
            drawMacdPane(state, theme, 0f, contentRight, macdTop, macdBottom, text)
        }
        // 覆盖物（主 pane 区域）
        with(OverlayRenderer) {
            drawOverlays(state, theme, contentRight, mainBottom, text)
        }
        drawTimeAxis(state, theme, text, 0f, contentRight, macdBottom, panes.timeAxis)
        if (params.drawLegend) {
            drawLegend(state, theme, text, params.legendTextSizePx, crosshair, params.gutterWidth, params.width)
        }
        if (crosshair != null) {
            drawCrosshairLabels(state, theme, text, crosshair, contentRight, legendHeight)
        }
    }

    /** 时间轴：按可见范围与间隔取标签（跨日显示日期）；实测宽度防重叠。 */
    internal fun DrawScope.drawTimeAxis(
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
        while (i <= range.last) {
            val c = candles[i]
            val label = ChartText.timeAxisLabel(c.timestamp, prevTs, interval)
            if (label.isNotEmpty()) {
                val x = state.viewport.candleCenterX(i.toDouble()).toFloat()
                val width = text.measureWidth(label)
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
    internal fun DrawScope.drawLegend(
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
            x += text.measureWidth(s) + 10f
        }

        // 主 pane 指标图例（遍历全部 price 系实例，批次 2b）
        var li = 0
        var lx = 8f
        val ly = y + textSizePx * 1.4f
        for (inst in state.mainPaneIndicatorResults()) {
            val res = inst.state ?: continue
            for ((k, key) in res.result.keys.withIndex()) {
                val v = res.result[key].getOrNull(idx) ?: Double.NaN
                val color = theme.linePalette[(li + k) % theme.linePalette.size]
                val label = "${inst.definition.shortName} ${text.price(v)}"
                if (lx > contentWidth - 40f) break
                drawText(text, label, Offset(lx, ly), color)
                li++
                lx += text.measureWidth(label) + 10f
            }
        }
    }

    /** 十字线当前值标签（右侧价格 + 主 pane 值）。 */
    internal fun DrawScope.drawCrosshairLabels(
        state: ChartState,
        theme: ChartTheme,
        text: ChartText,
        crosshair: Offset,
        contentRight: Float,
        legendHeight: Float,
    ) {
        val candles = state.dataSnapshot.candles
        val range = state.viewport.visibleRange(candles.size)
        if (range.count <= 0) return
        val visible = candles.subList(range.first, range.last + 1)
        val (lo, hi) = LinearYAxis.rangeOfCandles(visible) ?: return
        val spec = state.effectiveMainAxisSpec()
        val value = YAxisMath.yToValue(
            spec, crosshair.y.toDouble(), lo, hi, 0.0, state.paneHeightsPx.main.toDouble(),
        )
        val clampedY = crosshair.y.coerceIn(legendHeight + text.tagHeight / 2f, Float.MAX_VALUE)
        drawTag(text, value, clampedY, contentRight, theme.crosshairLine)
    }
}
