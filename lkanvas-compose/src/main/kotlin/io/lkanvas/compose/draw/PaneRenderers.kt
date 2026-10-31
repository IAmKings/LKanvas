package io.lkanvas.compose.draw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import io.lkanvas.compose.state.ChartState
import io.lkanvas.compose.theme.ChartTheme
import io.lkanvas.compose.theme.MainChartType
import io.lkanvas.core.axis.AxisTicks
import io.lkanvas.core.axis.LinearYAxis
import io.lkanvas.core.axis.YAxisMath
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

    /** 主 pane：网格 + candle + MA 线 + 末价线（按 [YAxisSpec] 轴规格映射）。 */
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
        /** 0.4.0 多轴：自定义轴标签列 x 坐标（与 state.extraYAxisMeta 键序一致）。 */
        extraAxisCols: List<Float> = emptyList(),
    ) {
        val candles = state.dataSnapshot.candles
        val range = state.viewport.visibleRange(candles.size)
        if (range.count <= 0) return
        val visible = candles.subList(range.first, range.last + 1)

        val (lo, hi) = LinearYAxis.rangeOfCandles(visible) ?: return
        val spec = state.effectiveMainAxisSpec()
        val (rf, rt) = YAxisMath.realRange(spec, lo, hi)
        val yReal: (Double) -> Double = { r -> LinearYAxis.valueToY(r, rf, rt, top.toDouble(), bottom.toDouble()) }
        // SLA-010：主轴 reverse 与 convert 共用（ChartState.mainAxisReverse，选中不翻转刻度文本）
        val y: (Double) -> Double = { v ->
            val r = yReal(YAxisMath.valueToReal(spec, v))
            if (state.mainAxisReverse) top + bottom - r else r
        }

        // 网格与刻度按轴规格生成（Styles grid.show/横向虚线开关）
        val axisTicks = YAxisMath.ticks(spec, lo, hi, 5)
        if (theme.gridShow) {
            val hEff = if (theme.gridDashed) dashedEffect() else null
            for (t in axisTicks.ticks) {
                val yy = yReal(t.real).toFloat()
                drawLine(theme.gridLine, Offset(contentLeft, yy), Offset(contentRight, yy), 1f, pathEffect = hEff)
            }
            // 竖向网格：可见区四等分（上游竖网格在固定 bar 间隔，此处取等分近似）
            val step = maxOf(1.0, range.count / 4.0)
            val vEff = if (theme.gridDashed) dashedEffect() else null
            var gi = range.first + step.toInt()
            while (gi <= range.last) {
                val xx = state.viewport.candleCenterX(gi.toDouble()).toFloat()
                if (xx >= contentLeft && xx <= contentRight) {
                    drawLine(theme.gridVerticalLine, Offset(xx, top), Offset(xx, bottom), 1f, pathEffect = vEff)
                }
                gi += step.toInt()
            }
        }
        clipRect(left = contentLeft, top = top, right = contentRight, bottom = bottom) {
            drawCandles(state, theme, visible, range.first, y)
            // 主 pane 指标叠加：内置 MA + 用户 price 系实例（批次 2b）
            var paletteIdx = 0
            for (inst in state.mainPaneIndicatorResults()) {
                if (inst.yAxisId != "main") continue // 绑定自定义轴的指标由下方多轴块渲染（0.4.0）
                val res = inst.state ?: continue
                // 批次 8c：模板 draw 钩子非空时绕过 figures 管线（IND-022 消费端）
                val customDraw = inst.definition.draw
                if (customDraw != null) {
                    customDraw(
                        io.lkanvas.core.indicator.IndicatorDrawContext(
                            result = res.result,
                            rangeFirst = range.first,
                            rangeLast = range.last,
                            yOf = y,
                            xOf = { i -> state.viewport.candleCenterX(i.toDouble()) },
                            palette = theme.linePalette,
                            scope = this,
                        ),
                    )
                } else {
                    // T06（第三轮复审 2026-10-30）：主图叠加走统一 figure 管线
                    //（BAR 分发——main 上创建 MACD 柱列不再画折线）
                    drawInstanceFigures(inst, res, state.viewport, range, y, theme, paletteIdx, lastValueMark = theme.lastValueMarkShow, candles = candles, paneTop = top)
                }
                paletteIdx += res.result.keys.size
            }
            if (theme.lastPriceShow) {
                drawLastPriceLine(candles, y, theme, contentLeft, contentRight, text, legendHeight)
            }
            drawHighLowMarkers(state, theme, text, visible, range.first, y, contentRight)
            if (state.mainChartType == MainChartType.AREA) {
                drawAreaFill(state, theme, visible, range.first, y, bottom)
            }
        }
        // 0.4.0 多轴渲染（CH-053~056/IND-003 消费端）：绑定自定义轴的指标用
        // 独立值域映射，轴标签画在各自 gutter 列（reverse 翻转值域方向）
        val extraAxes = state.extraYAxisMeta
        var colIdx = 0
        // T02：仅主 pane 的额外轴参与主图标签/gutter（副 pane 轴由 drawExtraPane 消费）
        for ((ai, axisId) in extraAxes.entries.filter { it.value.paneId == "main" }.map { it.key }.withIndex()) {
            val meta = extraAxes[axisId] ?: continue
            val inPane = meta.inside || meta.positionLeft
            val colX = if (inPane) -1f else extraAxisCols.getOrNull(colIdx++) ?: continue
            val (alo, ahi) = state.extraAxisRange(axisId, range.first, range.last) ?: continue
            val reversed = meta.reverse
            // R06：轴实例生效 spec（custom > 模板 spec > Normal）——与 convert 同源
            val customSpec = state.effectiveExtraAxisSpec(axisId)
            // Custom 变换轴：值经变换投影到 real 域再线性映射；刻度经 customTicks（A-18 净化内建）
            val rlo: Double
            val rhi: Double
            val ay: (Double) -> Double
            var customTicks: io.lkanvas.core.axis.AxisTicks? = null
            if (customSpec != null) {
                val (rf, rt) = YAxisMath.realRange(customSpec, alo, ahi)
                rlo = if (reversed) rt else rf
                rhi = if (reversed) rf else rt
                ay = { v -> LinearYAxis.valueToY(YAxisMath.valueToReal(customSpec, v), rlo, rhi, top.toDouble(), bottom.toDouble()) }
                customTicks = YAxisMath.ticks(customSpec, alo, ahi, 5)
            } else {
                val (ylo, yhi) = if (reversed) ahi to alo else alo to ahi
                rlo = ylo
                rhi = yhi
                ay = { v -> LinearYAxis.valueToY(v, ylo, yhi, top.toDouble(), bottom.toDouble()) }
            }
            clipRect(left = contentLeft, top = top, right = contentRight, bottom = bottom) {
                var axPalette = ai * 3
                for (inst in state.mainPaneIndicatorResults()) {
                    if (inst.yAxisId != axisId) continue
                    val res = inst.state ?: continue
                    val customDraw = inst.definition.draw
                    if (customDraw != null) {
                        customDraw(
                            io.lkanvas.core.indicator.IndicatorDrawContext(
                                result = res.result,
                                rangeFirst = range.first,
                                rangeLast = range.last,
                                yOf = ay,
                                xOf = { i -> state.viewport.candleCenterX(i.toDouble()) },
                                palette = theme.linePalette,
                                scope = this,
                            ),
                        )
                    } else {
                        // T06：主图额外轴入口同管线
                        drawInstanceFigures(inst, res, state.viewport, range, ay, theme, axPalette, lastValueMark = theme.lastValueMarkShow, candles = candles)
                    }
                    axPalette += res.result.keys.size
                }
            }
            // 轴标签（独立 gutter 列；inside/LEFT 轴画 pane 内右缘右对齐）
            val axisColor = theme.linePalette[(ai * 3) % theme.linePalette.size]
            var lastTop = Float.NEGATIVE_INFINITY
            fun labelX(v: String): Float = if (colX < 0f) contentRight - text.measureWidth(v) - 6f else colX
            if (customTicks != null) {
                for (t in customTicks.ticks) {
                    val ty = LinearYAxis.valueToY(t.real, rlo, rhi, top.toDouble(), bottom.toDouble()).toFloat() - text.textHeight / 2f
                    if (ty < lastTop + 2f || ty < legendHeight) continue
                    drawText(text, t.text, Offset(labelX(t.text), ty), axisColor)
                    lastTop = ty
                }
            } else {
                val (ylo, yhi) = if (reversed) ahi to alo else alo to ahi
                val ticks = LinearYAxis.ticks(ylo, yhi, 5)
                for (t in ticks) {
                    val ty = LinearYAxis.valueToY(t, ylo, yhi, top.toDouble(), bottom.toDouble()).toFloat() - text.textHeight / 2f
                    if (ty < lastTop + 2f || ty < legendHeight) continue
                    val s0 = text.price(t)
                    drawText(text, s0, Offset(labelX(s0), ty), axisColor)
                    lastTop = ty
                }
            }
        }
        drawAxisTicks(text, theme, axisTicks, yReal, contentRight, clipTop = legendHeight)
        if (crosshair != null && theme.crosshairShow) {
            drawCrosshair(theme, crosshair, contentLeft, contentRight, top, bottom)
        }
    }

    /** 轴规格刻度标签（文本已按规格格式化；垂直去重叠 + 顶部避让）。 */
    private fun DrawScope.drawAxisTicks(
        text: ChartText,
        theme: ChartTheme,
        ticks: AxisTicks,
        yReal: (Double) -> Double,
        axisRight: Float,
        clipTop: Float,
    ) {
        var lastTop = Float.NEGATIVE_INFINITY
        for (t in ticks.ticks) {
            val top = yReal(t.real).toFloat() - text.textHeight / 2f
            if (top < lastTop + 2f) continue
            if (top < clipTop) continue
            drawText(text, t.text, Offset(axisRight + 6f, top), theme.axisText)
            lastTop = top
        }
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
        // Q05（第四轮复审 2026-10-30）：VOL pane 全实例走统一 figure 管线
        //（涨跌色经模板 styles 回调——上游 volume.ts:33 同模型；删除
        // drawVolumeBars 专用旁路与"平台特例"登记）
        drawIndicatorPaneInstances("vol", state, theme, contentLeft, contentRight, top, bottom, text)
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
        // Q05：MACD pane 全实例走统一 figure 管线（柱色经模板 styles 回调；
        // 删除 drawMacdBars 专用旁路；值域经 paneVisibleRange 与转换同源）
        drawIndicatorPaneInstances("macd", state, theme, contentLeft, contentRight, top, bottom, text)
    }

    /**
     * Q05：内置副 pane（vol/macd）通用入口——遍历该 pane **全部可见实例**经
     * drawInstanceFigures（与 main/额外轴/动态 pane 同一 figure 契约）；
     * 值域 = paneVisibleRange（BAR 基线入域；vol 量纲含 candle volume）。
     */
    private fun DrawScope.drawIndicatorPaneInstances(
        paneId: String,
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
        val insts = state.getIndicators(io.lkanvas.compose.state.ChartState.IndicatorFilter(paneId = paneId))
            .filter { it.visible && it.state != null }
        if (insts.isEmpty()) return
        // M02：与 drawExtraPane 同一选择入口——绑定副轴实例按其轴投影（修复前
        // 固定 pane 忽略 yAxisId，全部实例共用普通线性 y）
        val (lo, hi) = state.paneVisibleRange(paneId) ?: return
        val defaultY: (Double) -> Double = { v -> LinearYAxis.valueToY(v, lo, hi, top.toDouble(), bottom.toDouble()) }
        drawGrid(theme, text, lo, hi, contentLeft, contentRight, top, bottom, tickCount = 3)
        clipRect(left = contentLeft, top = top, right = contentRight, bottom = bottom) {
            var paletteIdx = 0
            for (inst in insts) {
                val res = inst.state ?: continue
                val y = state.instanceBoundYOf(paneId, inst.yAxisId, range.first, range.last, top, bottom)
                    ?: defaultY
                val customDraw = inst.definition.draw
                if (customDraw != null) {
                    customDraw(
                        io.lkanvas.core.indicator.IndicatorDrawContext(
                            result = res.result, rangeFirst = range.first, rangeLast = range.last,
                            yOf = y, xOf = { i -> state.viewport.candleCenterX(i.toDouble()) },
                            palette = theme.linePalette, scope = this,
                        ),
                    )
                } else {
                    drawInstanceFigures(inst, res, state.viewport, range, y, theme, paletteIdx, lastValueMark = false, candles = candles, paneTop = top)
                }
                paletteIdx += res.result.keys.size
            }
        }
        drawYAxisLabels(text, theme, LinearYAxis.ticks(lo, hi, 3), defaultY, contentRight)
    }

    /**
     * 0.5.x 动态副图 pane：绑定该 paneId 的全部指标按各自全量线列绘制
     * （palette 顺序、lastValueMark、网格与 y 轴标签同内置副图口径）。
     */
    fun DrawScope.drawExtraPane(
        state: ChartState,
        theme: ChartTheme,
        text: ChartText,
        paneId: String,
        contentLeft: Float,
        contentRight: Float,
        top: Float,
        bottom: Float,
    ) {
        // R08（复审 2026-10-30）：可见性过滤进管线（隐藏实例不参与值域与绘制）
        val insts = state.getIndicators(io.lkanvas.compose.state.ChartState.IndicatorFilter(paneId = paneId))
            .filter { it.visible }
        val valid = insts.mapNotNull { it.state }
        if (valid.isEmpty()) return
        val candles = state.dataSnapshot.candles
        val range = state.viewport.visibleRange(candles.size)
        if (range.count <= 0) return

        // Q03（第四轮复审 2026-10-30）+ N02/M02（第五/六轮复审 2026-10-31）：值域
        // 单一来源 = paneVisibleRange；轴解析**移入实例循环**——每实例按 yAxisId
        // 经 state.instanceBoundYOf 取专属投影（修复前 firstOrNull 在循环外，
        // 多个有效绑定共用第一条轴；未绑定实例回退普通线性）
        val (lo, hi) = state.paneVisibleRange(paneId) ?: return
        val defaultY: (Double) -> Double = { v -> LinearYAxis.valueToY(v, lo, hi, top.toDouble(), bottom.toDouble()) }

        drawGrid(theme, text, lo, hi, contentLeft, contentRight, top, bottom, tickCount = 3)
        clipRect(left = contentLeft, top = top, right = contentRight, bottom = bottom) {
            var paletteIdx = 0
            for (inst in insts) {
                val res = inst.state ?: continue
                val y = state.instanceBoundYOf(paneId, inst.yAxisId, range.first, range.last, top, bottom)
                    ?: defaultY
                val customDraw = inst.definition.draw
                if (customDraw != null) {
                    customDraw(
                        io.lkanvas.core.indicator.IndicatorDrawContext(
                            result = res.result, rangeFirst = range.first, rangeLast = range.last,
                            yOf = y, xOf = { i -> state.viewport.candleCenterX(i.toDouble()) },
                            palette = theme.linePalette, scope = this,
                        ),
                    )
                } else {
                    // R08：统一 figure 管线——按 PlotDef.type 分发 line/bar（含 baseValue），
                    // attrs/styles 逐行求值；27 显示模板不再依赖专用 renderer
                    drawInstanceFigures(inst, res, state.viewport, range, y, theme, paletteIdx, lastValueMark = theme.lastValueMarkShow, candles = candles, paneTop = top)
                }
                paletteIdx += res.result.keys.size
            }
        }
        drawYAxisLabels(text, theme, LinearYAxis.ticks(lo, hi, 3), defaultY, contentRight)
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
        if (!theme.gridShow) return
        val eff = if (theme.gridDashed) dashedEffect() else null
        for (t in LinearYAxis.ticks(lo, hi, tickCount)) {
            val yy = LinearYAxis.valueToY(t, lo, hi, top.toDouble(), bottom.toDouble()).toFloat()
            drawLine(theme.gridLine, Offset(left, yy), Offset(right, yy), 1f, pathEffect = eff)
        }
    }

    /** Styles 虚线约定（dashedValue 2/2，px）。 */
    private fun dashedEffect() =
        androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 4f))

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
        val type = state.mainChartType
        // SLA-011：candle.bar 回调 → 逐根配色（上游 CandleBarColorCallback 消费）
        val barColorProvider = state.candleBarColorProvider
        for (i in visible.indices) {
            val c = visible[i]
            val x = vp.candleCenterX((firstIndex + i).toDouble())
            val up = c.close > c.open
            val cb = barColorProvider?.invoke(firstIndex + i)
            val color = when {
                cb != null && c.close > c.open -> io.lkanvas.compose.theme.StylesThemeBridge.parse(cb.upColor)
                cb != null && c.close < c.open -> io.lkanvas.compose.theme.StylesThemeBridge.parse(cb.downColor)
                cb != null -> io.lkanvas.compose.theme.StylesThemeBridge.parse(cb.noChangeColor)
                c.close > c.open -> theme.upColor
                c.close < c.open -> theme.downColor
                else -> theme.noChangeColor
            }
            // OHLC bar：竖线 + 左开右收两横
            if (type == MainChartType.OHLC) {
                drawLine(color, Offset(x.toFloat(), y(c.high).toFloat()), Offset(x.toFloat(), y(c.low).toFloat()), 1f)
                drawLine(
                    color,
                    Offset((x - bodyWidth / 2).toFloat(), y(c.open).toFloat()),
                    Offset(x.toFloat(), y(c.open).toFloat()),
                    1f,
                )
                drawLine(
                    color,
                    Offset(x.toFloat(), y(c.close).toFloat()),
                    Offset((x + bodyWidth / 2).toFloat(), y(c.close).toFloat()),
                    1f,
                )
                continue
            }
            // 影线（area 不画影线）
            if (type != MainChartType.AREA) {
                drawLine(
                    color,
                    Offset(x.toFloat(), y(c.high).toFloat()),
                    Offset(x.toFloat(), y(c.low).toFloat()),
                    1f,
                )
            }
            if (type == MainChartType.AREA) continue

            // 实体（最小 1px，开收同价画横线）
            val yOpen = y(c.open)
            val yClose = y(c.close)
            val top = min(yOpen, yClose).toFloat()
            val bottom = max(yOpen, yClose).toFloat()
            val height = max(bottom - top, 1f)
            val topLeft = Offset((x - bodyWidth / 2).toFloat(), top)
            val size = Size(bodyWidth.toFloat(), height)
            when (type) {
                MainChartType.CANDLE_SOLID ->
                    drawRect(color, topLeft = topLeft, size = size)
                MainChartType.CANDLE_STROKE ->
                    drawRect(color, topLeft = topLeft, size = size, style = Stroke(width = 1f))
                // 上涨空心、下跌实心
                MainChartType.CANDLE_UP_STROKE ->
                    if (up) {
                        drawRect(color, topLeft = topLeft, size = size, style = Stroke(width = 1f))
                    } else {
                        drawRect(color, topLeft = topLeft, size = size)
                    }
                // 上涨实心、下跌空心
                MainChartType.CANDLE_DOWN_STROKE ->
                    if (up) {
                        drawRect(color, topLeft = topLeft, size = size)
                    } else {
                        drawRect(color, topLeft = topLeft, size = size, style = Stroke(width = 1f))
                    }
                else -> drawRect(color, topLeft = topLeft, size = size)
            }
        }
    }

    /** area 类型：收盘价折线 + 向下渐变填充（画在 candle 之后、MA 之前由调用序保证）。 */
    private fun DrawScope.drawAreaFill(
        state: ChartState,
        theme: ChartTheme,
        visible: List<Candle>,
        firstIndex: Int,
        y: (Double) -> Double,
        paneBottom: Float,
    ) {
        if (visible.isEmpty()) return
        val vp = state.viewport
        val path = Path()
        val firstX = vp.candleCenterX(firstIndex.toDouble()).toFloat()
        path.moveTo(firstX, y(visible.first().close).toFloat())
        for (i in 1 until visible.size) {
            path.lineTo(
                vp.candleCenterX((firstIndex + i).toDouble()).toFloat(),
                y(visible[i].close).toFloat(),
            )
        }
        val lastX = vp.candleCenterX((firstIndex + visible.size - 1).toDouble()).toFloat()
        val closeLine = Path().apply { addPath(path) }
        path.lineTo(lastX, paneBottom)
        path.lineTo(firstX, paneBottom)
        path.close()
        val lineColor = theme.areaLineColor
        drawPath(
            path,
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.25f), lineColor.copy(alpha = 0.02f)),
                startY = 0f,
                endY = paneBottom,
            ),
        )
        drawPath(closeLine, lineColor, style = Stroke(width = theme.areaLineSizePx))
    }

    /** 可见最高/最低标记（PRD R-07；Styles priceMark.show + high/low.color 消费）。 */
    private fun DrawScope.drawHighLowMarkers(
        state: ChartState,
        theme: ChartTheme,
        text: ChartText,
        visible: List<Candle>,
        firstIndex: Int,
        y: (Double) -> Double,
        contentRight: Float,
    ) {
        if (!theme.priceMarkShow || visible.size < 2) return
        var highIdx = 0
        var lowIdx = 0
        for (i in visible.indices) {
            if (visible[i].high > visible[highIdx].high) highIdx = i
            if (visible[i].low < visible[lowIdx].low) lowIdx = i
        }
        val vp = state.viewport
        val hx = vp.candleCenterX((firstIndex + highIdx).toDouble()).toFloat()
        val hy = y(visible[highIdx].high).toFloat()
        drawText(text, " ${text.price(visible[highIdx].high)}", Offset(hx + 6f, (hy - text.textHeight).coerceAtLeast(0f)), theme.highMarkerColor)

        val lx = vp.candleCenterX((firstIndex + lowIdx).toDouble()).toFloat()
        val ly = y(visible[lowIdx].low).toFloat()
        drawText(text, "${text.price(visible[lowIdx].low)} ", Offset((lx - 6f - text.tagWidth).coerceAtLeast(0f), ly + 2f), theme.lowMarkerColor)
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
        paletteOffset: Int = 0,
        lineSizePx: Float = 1.5f,
        dashed: Boolean = false,
        lastValueMark: Boolean = true,
        styleOverrideOf: ((key: String, index: Int) -> Pair<Color?, Float?>)? = null,
        /** M03：attrs 原样求值（coordinates 点列覆盖）；null=无 attrs 走批量快路径。 */
        attrsOf: ((key: String, row: Int, value: Double) -> Map<*, *>?)? = null,
        paneTop: Float = 0f,
    ) {
        // T-34 第二轮：drawLines(FloatArray) 批量提交替代逐点 Path（B-02 分段定位
        // 指标线占 13ms/帧：65 列 × ~240 点的 Path 构建+drawPath 为 UI 线程大头）。
        // 连续段（NaN 断开）各一次 drawLines；线段重复端点语义与 lineTo 一致。
        val lineKeys = keys ?: result.result.keys.filterNot { it in skipKeys }
        val eff = if (dashed) dashedEffect() else null
        // 预计算恒定项：candleCenterX = barSpacing*(i - scrollIndex + 0.5)
        val barSpacing = vp.barSpacing.toFloat()
        val x0 = ((0 - vp.scrollIndex + 0.5) * vp.barSpacing).toFloat()
        for ((idx, key) in lineKeys.withIndex()) {
            val column = result.result[key]
            val baseColor = palette[(idx + paletteOffset) % palette.size]
            // SLA-013/IND：PlotDef.styles 行级回调**逐行求值**（(row, params) ->
            // {"color":...,"lineWidth":...}；无回调时整列走单组快速路径）
            data class Seg(val color: Color, val width: Float) {
                var arr = FloatArray(64)
                var pos = 0
            }
            val groups = LinkedHashMap<Seg, Unit>()
            fun segFor(color: Color, width: Float): Seg =
                groups.keys.firstOrNull { it.color == color && it.width == width } ?: Seg(color, width).also { groups[it] = Unit }
            val fastSeg = Seg(baseColor, lineSizePx)
            groups[fastSeg] = Unit
            var lastX = Float.NaN
            var lastY = Float.NaN
            var prevWasFinite = false
            for (i in range.first..range.last) {
                val v = column[i]
                if (v.isNaN()) {
                    prevWasFinite = false
                    continue
                }
                val x = x0 + barSpacing * i
                val yy = y(v).toFloat()
                // M03（第六轮复审 2026-10-31）：attrs.coordinates 逐点覆盖（上游
                // IndicatorView.ts:191-196——`coordinates: attrs?.coordinates ?? 计算值`）；
                // 有 coords 的行按点列 drawPath（pane 相对 +paneTop），无 coords 行
                // 维持批量 drawLines 快路径——批量渲染与几何覆盖不互斥
                if (attrsOf != null) {
                    val coords = ((attrsOf(key, i, v) ?: emptyMap<Any?, Any>())["coordinates"] as? List<*>)
                        ?.mapNotNull { p ->
                            (p as? Map<*, *>)?.let { m ->
                                val px = (m["x"] as? Number)?.toFloat()
                                val py = (m["y"] as? Number)?.toFloat()
                                if (px != null && py != null) Offset(px, py + paneTop) else null
                            }
                        }
                    if (!coords.isNullOrEmpty()) {
                        val path = androidx.compose.ui.graphics.Path()
                        coords.forEachIndexed { pi, pt ->
                            if (pi == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                        }
                        val (cc, wc) = if (styleOverrideOf != null) styleOverrideOf(key, i) else null to null
                        drawPath(
                            path,
                            cc ?: baseColor,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = wc ?: lineSizePx),
                        )
                        lastX = coords.last().x
                        lastY = coords.last().y
                        prevWasFinite = true
                        continue
                    }
                }
                if (prevWasFinite) {
                    // 逐行求值样式：线段 (i-1, i) 的样式由后行决定
                    val seg = if (styleOverrideOf != null) {
                        val (c0, w0) = styleOverrideOf(key, i)
                        segFor(c0 ?: baseColor, w0 ?: lineSizePx)
                    } else {
                        fastSeg
                    }
                    if (seg.pos + 4 > seg.arr.size) seg.arr = seg.arr.copyOf(seg.arr.size * 2)
                    seg.arr[seg.pos++] = lastX; seg.arr[seg.pos++] = lastY
                    seg.arr[seg.pos++] = x; seg.arr[seg.pos++] = yy
                }
                lastX = x
                lastY = yy
                prevWasFinite = true
            }
            // T-34：native drawLines 按样式组批量提交（无回调时恰为单组=原快速路径）
            for (seg in groups.keys) {
                if (seg.pos >= 4) {
                    drawIntoCanvas { canvas ->
                        val p2 = android.graphics.Paint().apply {
                            this.strokeWidth = seg.width
                            this.color = seg.color.toArgb()
                            this.style = android.graphics.Paint.Style.STROKE
                            this.strokeCap = android.graphics.Paint.Cap.ROUND
                            eff?.let { this.pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 4f), 0f) }
                        }
                        canvas.nativeCanvas.drawLines(seg.arr, 0, seg.pos, p2)
                    }
                }
            }
            // Styles indicator.lastValueMark.show：线末端值点
            if (lastValueMark && !lastX.isNaN()) {
                val (c0, w0) = if (styleOverrideOf != null) styleOverrideOf(key, range.last) else null to null
                drawCircle(c0 ?: baseColor, radius = 2.5f, center = Offset(lastX, lastY))
            }
        }
    }

    /**
     * R08（复审 2026-10-30）：统一 figure 绘制管线——按 `plotsFor(params)` 的
     * PlotDef.type 分发 LINE（drawLinePlots 批量）/BAR（drawBarPlots，含
     * baseValue 基线），attrs/styles 逐行求值；上游 view/IndicatorView
     * eachFigures 的类型分发等价物。
     */
    fun DrawScope.drawInstanceFigures(
        inst: io.lkanvas.core.indicator.IndicatorInstance,
        res: io.lkanvas.core.indicator.IndicatorResultState,
        vp: io.lkanvas.core.viewport.Viewport,
        range: VisibleRange,
        y: (Double) -> Double,
        theme: ChartTheme,
        paletteOffset: Int,
        lastValueMark: Boolean,
        candles: List<Candle> = emptyList(),
        paneTop: Float = 0f,
    ) {
        val plots = inst.definition.plotsFor(inst.params).associateBy { it.key }
        val override = styleOverrideFor(inst)
        val attrsGeo = attrsOverrideFor(inst)
        val lineKeys = ArrayList<String>()
        val barEntries = ArrayList<Pair<String, Double?>>() // key → baseValue
        for ((key, plot) in plots) {
            if (key !in res.result.keys) continue
            when (plot.type) {
                io.lkanvas.core.indicator.PlotType.BAR -> barEntries += key to plot.baseValue
                else -> lineKeys += key
            }
        }
        // 顺序：先线后柱（与内置 MACD DIF/DEA→柱 口径一致）
        if (lineKeys.isNotEmpty()) {
            val lineAttrs = rawAttrsFor(inst)
            drawLinePlots(res, vp, range, y, theme.linePalette, keys = lineKeys, paletteOffset = paletteOffset, lineSizePx = theme.indicatorLineSizePx, dashed = theme.indicatorLineDashed, lastValueMark = lastValueMark, styleOverrideOf = override?.let { o -> { k: String, r: Int -> o(k, r, res.result[k]?.getOrNull(r) ?: Double.NaN, candles) } }, attrsOf = lineAttrs?.let { la -> { k: String, r: Int, v: Double -> la(k, r, v, candles) } }, paneTop = paneTop)
        }
        if (barEntries.isNotEmpty()) {
            drawBarPlots(res, vp, range, y, barEntries, theme.linePalette, paletteOffset + lineKeys.size, override, attrsGeo, candles, paneTop)
        }
    }

    /**
     * R08：BAR 型 figure 绘制——值→baseValue 的柱体（缺省基线 0），宽随
     * barSpacing gapRatio，颜色按行级 styles/attrs 求值（缺省 palette）。
     */
    private fun DrawScope.drawBarPlots(
        res: io.lkanvas.core.indicator.IndicatorResultState,
        vp: io.lkanvas.core.viewport.Viewport,
        range: VisibleRange,
        y: (Double) -> Double,
        barEntries: List<Pair<String, Double?>>,
        palette: List<Color>,
        paletteOffset: Int,
        override: ((String, Int, Double, List<Candle>) -> Pair<Color?, Float?>)?,
        attrsGeo: ((String, Int, Double, List<Candle>) -> Map<String, Double>)?,
        candles: List<Candle> = emptyList(),
        paneTop: Float = 0f,
    ) {
        val gap = (vp.barSpacing * 0.25).coerceAtMost(vp.barSpacing - 1.0).coerceAtLeast(0.0).toFloat()
        val bodyWidth = (vp.barSpacing - gap).coerceAtLeast(1.0).toFloat()
        for ((bi, entry) in barEntries.withIndex()) {
            val (key, baseValue) = entry
            val col = res.result[key] ?: continue
            val base = baseValue ?: 0.0
            val baseY = y(base).toFloat()
            for (i in range.first..range.last) {
                val v = col[i]
                if (v.isNaN()) continue
                val vy = y(v).toFloat()
                val geo = attrsGeo?.invoke(key, i, v, candles)
                // M03（第六轮复审 2026-10-31）：默认几何与 attrs 按**上游含义**四键合并
                //（上游 IndicatorView.ts:177-185——先算默认 {x: center-barWidth/2,
                // y: min(valueY,baseY), width: barWidth, height}，再 attrs 展开覆盖；
                // x=矩形**左边界**（修复前被当中心再减半宽）、y=顶边、height=柱高
                //（修复前不消费）；pane 相对坐标 +paneTop 语义同 N04）
                val ax = geo?.get("x")?.toFloat()
                    ?: (vp.candleCenterX(i.toDouble()).toFloat() - bodyWidth / 2f)
                val aw = (geo?.get("width")?.toFloat() ?: bodyWidth).coerceAtLeast(1f)
                val top: Float
                val hgt: Float
                when {
                    geo != null && (geo.containsKey("y") || geo.containsKey("height")) -> {
                        top = (geo.get("y")?.toFloat() ?: minOf(vy, baseY)) + paneTop
                        hgt = (geo.get("height")?.toFloat() ?: kotlin.math.abs(vy - baseY))
                            .coerceAtLeast(0.5f)
                    }
                    // N04 既有 bridge 约定（top/bottom pane 相对）保留兼容
                    geo != null && geo.containsKey("top") && geo.containsKey("bottom") -> {
                        val geoTop = geo.getValue("top").toFloat() + paneTop
                        val geoBottom = geo.getValue("bottom").toFloat() + paneTop
                        top = minOf(geoTop, geoBottom)
                        hgt = kotlin.math.abs(geoBottom - geoTop).coerceAtLeast(0.5f)
                    }
                    else -> {
                        top = minOf(vy, baseY)
                        hgt = kotlin.math.abs(vy - baseY).coerceAtLeast(0.5f)
                    }
                }
                val (c0, _) = override?.invoke(key, i, v, candles) ?: (null to null)
                val color = c0 ?: palette[(bi + paletteOffset) % palette.size]
                drawRect(
                    color,
                    topLeft = Offset(ax, top),
                    size = androidx.compose.ui.geometry.Size(aw, hgt),
                )
            }
        }
    }

    /**
     * 实例 PlotDef.styles → drawLinePlots 行级样式回调（SLA-013/IND 消费链）。
     * PlotDef.styles 协议：`(row: Int, params: List<Double>) -> Map<String,String>`
     * 或 `(row: Int) -> Map<String,String>`；null=无覆盖。
     */
    /** styles 求值：支持 (row)/(row,params)/(row,params,value,candles) 函数与 Map。 */
    private fun evaluateStyles(
        value: Any?,
        row: Int,
        params: List<Double>,
        colValue: Double,
        candles: List<Candle>,
    ): Map<*, *>? = when (value) {
        null -> null
        is Function4<*, *, *, *, *> ->
            @Suppress("UNCHECKED_CAST")
            (value as (Int, List<Double>, Double, List<Candle>) -> Any?)(row, params, colValue, candles) as? Map<*, *>
        is Function2<*, *, *> ->
            @Suppress("UNCHECKED_CAST")
            (value as (Int, List<Double>) -> Any?)(row, params) as? Map<*, *>
        is Function1<*, *> ->
            @Suppress("UNCHECKED_CAST")
            (value as (Int) -> Any?)(row) as? Map<*, *>
        is Map<*, *> -> value
        else -> null
    }

    /** Q05：styles 上下文含 value+candles（上游 figure.styles data 语义）。 */
    fun styleOverrideFor(inst: io.lkanvas.core.indicator.IndicatorInstance): ((String, Int, Double, List<Candle>) -> Pair<Color?, Float?>)? {
        val plots = inst.definition.plotsFor(inst.params).associateBy { it.key }
        if (plots.values.all { it.styles == null }) return null
        return { key, row, colValue, candles ->
            val map: Map<*, *> = evaluateStyles(plots[key]?.styles, row, inst.params, colValue, candles) ?: emptyMap<Any?, Any>()
            val color = map["color"]?.toString()?.let { io.lkanvas.compose.theme.StylesThemeBridge.parse(it) }
            val width = map["lineWidth"]?.let { (it as? Number)?.toFloat() ?: it.toString().toFloatOrNull() }
            color to width
        }
    }

    /**
     * T06：PlotDef.attrs → **几何**逐行求值（上游 IndicatorView eachFigures 的
     * figure.attrs 协议：坐标/尺寸等；样式走 [styleOverrideFor]）。
     * BAR 消费键：top/bottom（pane 相对 y）——缺省由管线按值/基线自算。
     */
    /** Q05：attrs 几何求值（含 value+candles 上下文；数值 map）。 */
    fun attrsOverrideFor(inst: io.lkanvas.core.indicator.IndicatorInstance): ((String, Int, Double, List<Candle>) -> Map<String, Double>)? {
        val plots = inst.definition.plotsFor(inst.params).associateBy { it.key }
        if (plots.values.all { it.attrs == null }) return null
        return { key, row, colValue, candles ->
            // N04（第五轮复审 2026-10-31）：attrs 求值传入**真实** value+candles
            //（修复前 NaN/空列表——上游 figure.attrs 的 data 上下文语义）
            val map: Map<*, *> = evaluateStyles(plots[key]?.attrs, row, inst.params, colValue, candles) ?: emptyMap<Any?, Any>()
            buildMap {
                for ((k, v) in map) {
                    (v as? Number)?.let { put(k.toString(), it.toDouble()) }
                }
            }
        }
    }

    /**
     * M03：attrs **原样**求值（LINE coordinates 点列非 Number，数值过滤器会
     * 丢弃——rawAttrsFor 保留完整 map 供 drawLinePlots 提取 coordinates）。
     */
    private fun rawAttrsFor(inst: io.lkanvas.core.indicator.IndicatorInstance): ((String, Int, Double, List<Candle>) -> Map<*, *>?)? {
        val plots = inst.definition.plotsFor(inst.params).associateBy { it.key }
        if (plots.values.all { it.attrs == null }) return null
        return { key, row, colValue, candles ->
            evaluateStyles(plots[key]?.attrs, row, inst.params, colValue, candles)
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
        if (theme.lastPriceLineShow) {
            val eff = if (theme.lastPriceLineDashed) dashedEffect() else null
            drawLine(theme.lastPriceLine, Offset(left, yy), Offset(right, yy), 1f, pathEffect = eff)
        }
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
        val eff = if (theme.crosshairDashed) dashedEffect() else null
        if (theme.crosshairVerticalShow) {
            drawLine(theme.crosshairLine, Offset(crosshair.x, top), Offset(crosshair.x, bottom), theme.crosshairLineSizePx, pathEffect = eff)
        }
        if (theme.crosshairHorizontalShow) {
            drawLine(theme.crosshairLine, Offset(left, crosshair.y), Offset(right, crosshair.y), theme.crosshairLineSizePx, pathEffect = eff)
        }
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
