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

    /** 已合并的连续线段：保留批次几何，按上游 lineTo 顺序提交同一条路径。 */
    internal class IndicatorLinePath(
        internal val segments: FloatArray,
        internal val segmentSize: Int,
    ) : android.graphics.Path() {
        init {
            incReserve(segmentSize / 4 + 1)
            moveTo(segments[0], segments[1])
            for (offset in 2 until segmentSize step 4) lineTo(segments[offset], segments[offset + 1])
        }
    }

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

        val (lo, hi) = state.axisDisplayRange("main", state.mainPaneRange() ?: return)
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
                paletteIdx += figurePlots(inst).size
            }
            if (theme.lastPriceShow) {
                drawLastPriceLine(candles, y, theme, contentLeft, contentRight, text, legendHeight)
            }
            drawHighLowMarkers(state, theme, text, visible, range.first, y, contentRight, bottom)
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
            val (alo, ahi) = state.axisDisplayRange("main", state.extraAxisRange(axisId, range.first, range.last) ?: continue, axisId)
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
                    axPalette += figurePlots(inst).size
                }
            }
            // 轴标签（独立 gutter 列；inside/LEFT 轴画 pane 内右缘右对齐）
            val axisColor = theme.linePalette[(ai * 3) % theme.linePalette.size]
            fun labelX(v: String): Float = if (colX < 0f) contentRight - text.measureWidth(v) - 6f else colX
            val clipTop = if (colX < 0f) maxOf(top, legendHeight) else top
            if (customTicks != null) {
                val ticks = customTicks.ticks
                val centers = ticks.map { LinearYAxis.valueToY(it.real, rlo, rhi, top.toDouble(), bottom.toDouble()).toFloat() }
                for ((index, ty) in axisLabelPositions(centers, text.textHeight, clipTop, bottom)) {
                    val label = ticks[index].text
                    drawText(text, label, Offset(labelX(label), ty), axisColor)
                }
            } else {
                val (ylo, yhi) = if (reversed) ahi to alo else alo to ahi
                val ticks = LinearYAxis.ticks(ylo, yhi, 5)
                val centers = ticks.map { LinearYAxis.valueToY(it, ylo, yhi, top.toDouble(), bottom.toDouble()).toFloat() }
                for ((index, ty) in axisLabelPositions(centers, text.textHeight, clipTop, bottom)) {
                    val label = text.price(ticks[index])
                    drawText(text, label, Offset(labelX(label), ty), axisColor)
                }
            }
        }
        val tickY: (Double) -> Double = { real ->
            val yy = yReal(real)
            if (state.mainAxisReverse) top + bottom - yy else yy
        }
        drawAxisTicks(text, theme, axisTicks, tickY, contentRight, clipTop = top, clipBottom = bottom)
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
        clipBottom: Float,
    ) {
        val centers = ticks.ticks.map { yReal(it.real).toFloat() }
        for ((index, top) in axisLabelPositions(centers, text.textHeight, clipTop, clipBottom)) {
            drawText(text, ticks.ticks[index].text, Offset(axisRight + 6f, top), theme.axisText)
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
        val (lo, hi) = state.axisDisplayRange(paneId, state.paneVisibleRange(paneId) ?: return)
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
                paletteIdx += figurePlots(inst).size
            }
        }
        drawYAxisLabels(text, theme, LinearYAxis.ticks(lo, hi, 3), defaultY, contentRight, top, bottom)
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
        val (lo, hi) = state.axisDisplayRange(paneId, state.paneVisibleRange(paneId) ?: return)
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
                paletteIdx += figurePlots(inst).size
            }
        }
        drawYAxisLabels(text, theme, LinearYAxis.ticks(lo, hi, 3), defaultY, contentRight, top, bottom)
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
        bottom: Float,
    ) {
        if (!theme.priceMarkShow || visible.size < 2) return
        var highIdx = 0
        var lowIdx = 0
        for (i in visible.indices) {
            if (visible[i].high > visible[highIdx].high) highIdx = i
            if (visible[i].low < visible[lowIdx].low) lowIdx = i
        }
        val exclusions = text.markerExclusions
        val vp = state.viewport
        val hx = vp.candleCenterX((firstIndex + highIdx).toDouble()).toFloat()
        val hy = y(visible[highIdx].high).toFloat()
        val lx = vp.candleCenterX((firstIndex + lowIdx).toDouble()).toFloat()
        val ly = y(visible[lowIdx].low).toFloat()
        val high = text.price(visible[highIdx].high)
        val low = text.price(visible[lowIdx].low)
        // 数字标注用自身布局，避免 CJK 基准行高虚占密集图例下的可用空位。
        val highSize = text.layout(high).size
        val lowSize = text.layout(low).size
        val highBounds = priceMarkerBounds(hx, if (hy < ly) hy - highSize.height - 7f else hy + 7f,
            highSize.width.toFloat(), highSize.height.toFloat(), contentRight, bottom, exclusions)
        val lowBounds = priceMarkerBounds(lx, if (hy < ly) ly + 7f else ly - lowSize.height - 7f,
            lowSize.width.toFloat(), lowSize.height.toFloat(), contentRight, bottom, exclusions + listOfNotNull(highBounds))
        highBounds?.let { drawText(text, high, it.topLeft, theme.highMarkerColor) }
        lowBounds?.let { drawText(text, low, it.topLeft, theme.lowMarkerColor) }
    }



    /** 图例与绘图共用定义顺序，结果 Map 不承诺顺序。 */
    fun figurePlots(inst: io.lkanvas.core.indicator.IndicatorInstance): List<io.lkanvas.core.indicator.PlotDef> =
        inst.definition.plotsFor(inst.params)

    fun figurePaletteOffset(state: ChartState, inst: io.lkanvas.core.indicator.IndicatorInstance): Int {
        val peers = state.getIndicators(ChartState.IndicatorFilter(paneId = inst.paneId))
            .filter { it.visible && it.state != null && (inst.paneId != "main" || it.yAxisId == inst.yAxisId) }
        val base = if (inst.paneId == "main" && inst.yAxisId != "main")
            state.extraYAxisMeta.entries.filter { it.value.paneId == "main" }.indexOfFirst { it.key == inst.yAxisId }.coerceAtLeast(0) * 3 else 0
        return base + peers.takeWhile { it.id != inst.id }.sumOf { figurePlots(it).size }
    }

    fun figureColors(
        plots: List<io.lkanvas.core.indicator.PlotDef>,
        theme: ChartTheme,
        offset: Int,
        instanceLineColors: List<Color> = emptyList(),
    ): Map<String, Color> =
        plots.mapIndexed { i, plot ->
            // 0.16.0-beta02：实例 LINE 槽按 plot 索引覆盖全局调色板（不足/超出回退）
            val color = instanceLineColors.getOrNull(i)
                ?: theme.linePalette[(offset + i) % theme.linePalette.size]
            plot.key to color
        }.toMap()

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
        figureColors: Map<String, Color> = emptyMap(),
    ) {
        // 连续同样式段按上游 mergeLines 合并，再一次 stroke；NaN/样式/坐标断点分批。
        // 保留 FloatArray 收集几何，避免逐段 drawLines 的端帽重叠和重复栅格化。
        val lineKeys = keys ?: result.result.keys.filterNot { it in skipKeys }
        val eff = if (dashed) dashedEffect() else null
        // 与 candle/命中共用 Double 坐标，最后一步转 Float，避免大索引相消丢精度。
        for ((idx, key) in lineKeys.withIndex()) {
            val column = result.result[key]
            val baseColor = figureColors[key] ?: palette[(idx + paletteOffset) % palette.size]
            // SLA-013/IND：PlotDef.styles 行级回调**逐行求值**（(row, params) ->
            // {"color":...,"lineWidth":...}；无回调时整列走单组快速路径）
            data class Seg(val color: Color, val width: Float) {
                var arr = FloatArray(64)
                var pos = 0
            }
            // R10-01（第十轮复审 2026-10-31）：批次容器为**有序 List**（身份语义），
            // 只复用**最近的连续同样式批次**——上游 mergeLines 仅比较最后一项
            //（IndicatorView.ts:232），A→B→A 时后一个 A 是新批次、行序保持
            // [0,1,2]；修复前 Map 按值等价（data class equals）键控——全局查找
            // 复用最早 A 组且新 A 组被合并回原键位（首现序提交 [0,2,1] 甚至丢段）
            val groups = ArrayList<Seg>()
            fun segFor(color: Color, width: Float, x: Float, y: Float): Seg {
                val last = groups.lastOrNull()
                if (last != null && last.color == color && last.width == width &&
                    last.pos >= 4 && last.arr[last.pos - 2] == x && last.arr[last.pos - 1] == y
                ) return last
                return Seg(color, width).also { groups += it }
            }
            // R9-02（第九轮复审 2026-10-31）：**提交顺序 = 行登记顺序**——上游按行
            // 登记段后按原顺序合并绘制；自定义段（覆盖段）晚于其之前的默认段提交。
            // 修复前自定义段立即 drawPath、默认段积累到循环后统一 drawLines——
            // 重合时默认线反盖覆盖线（真实 View 对照确认）。
            fun flushGroups() {
                for (seg in groups) {
                    if (seg.pos >= 4) {
                        drawIntoCanvas { canvas ->
                            val p2 = android.graphics.Paint().apply {
                                this.strokeWidth = seg.width
                                this.color = seg.color.toArgb()
                                this.style = android.graphics.Paint.Style.STROKE
                                // 上游非 smooth 线默认 butt/miter；Canvas 默认 miterLimit=10。
                                this.strokeCap = android.graphics.Paint.Cap.BUTT
                                this.strokeJoin = android.graphics.Paint.Join.MITER
                                this.strokeMiter = 10f
                                eff?.let { this.pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 4f), 0f) }
                            }
                            canvas.nativeCanvas.drawPath(IndicatorLinePath(seg.arr, seg.pos), p2)
                        }
                    }
                }
                groups.clear() // 已提交组复位——后续段入新组，不重复绘制
            }
            var lastX = Float.NaN
            var lastY = Float.NaN
            var prevWasFinite = false
            for (i in range.first..range.last) {
                val v = column[i]
                if (v.isNaN()) {
                    prevWasFinite = false
                    continue
                }
                val x = vp.candleCenterX(i.toDouble()).toFloat()
                val yy = y(v).toFloat()
                // R7-04（第七轮复审 2026-10-31）+ R8-02（第八轮复审 2026-10-31）：
                // **行段归属统一**——默认段 (i-1,i) 属于 row i-1（上游
                // IndicatorView.ts:191-196 segment_k = attrs_k.coordinates ??
                // (P_k,P_{k+1})，同一次 eachFigures(dataIndex) 登记段与 styles——
                // 几何与**行级样式同属前行**，修复前几何归前行、样式取后行）；
                // 样式回调 row/value/candles 亦对应段所属行
                if (prevWasFinite) {
                    val seg = if (styleOverrideOf != null) {
                        val (c0, w0) = styleOverrideOf(key, i - 1)
                        segFor(c0 ?: baseColor, w0 ?: lineSizePx, lastX, lastY)
                    } else {
                        segFor(baseColor, lineSizePx, lastX, lastY)
                    }
                    if (seg.pos + 4 > seg.arr.size) seg.arr = seg.arr.copyOf(seg.arr.size * 2)
                    seg.arr[seg.pos++] = lastX; seg.arr[seg.pos++] = lastY
                    seg.arr[seg.pos++] = x; seg.arr[seg.pos++] = yy
                }
                // attrs.coordinates 行 = **独立段**（完全替换本行默认段）：按点列
                // drawPath（pane 相对 +paneTop）；缺省链自 P_i 断开（prev=false）——
                // 下一默认段从 P_{i+1} 重新起段，不用自定义终点接续（R7-04）。
                // R8-03+R9-01（第九轮复审 2026-10-31）：段存在条件 = 当前行与
                // **下一点均有效**，下一点取**完整结果列** result[i+1]（上游
                // IndicatorView.ts:116 nextData=result[dataIndex+1]，只检查数据
                // 有效性——修复前误加 i<range.last 可见区条件：末个可见行的
                // 合法覆盖段在滚动/缩窗时消失）
                if (attrsOf != null && i + 1 < column.size && !column[i + 1].isNaN()) {
                    val coords = ((attrsOf(key, i, v) ?: emptyMap<Any?, Any>())["coordinates"] as? List<*>)
                        ?.mapNotNull { p ->
                            (p as? Map<*, *>)?.let { m ->
                                val px = (m["x"] as? Number)?.toFloat()
                                val py = (m["y"] as? Number)?.toFloat()
                                if (px != null && py != null) Offset(px, py + paneTop) else null
                            }
                        }
                    if (!coords.isNullOrEmpty()) {
                        // R9-02：先提交此前积累的默认批次（行序），再绘制覆盖段
                        flushGroups()
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
                        lastX = x
                        lastY = yy
                        prevWasFinite = false
                        continue
                    }
                }
                lastX = x
                lastY = yy
                prevWasFinite = true
            }
            // R9-01：末个可见行自己的段（P_last, P_last+1）——下一点取完整结果列，
            // 越过绘制窗口仍登记（上游每可见行登记 segment_k=(P_k,P_{k+1})，无
            // "下一行也在可见列表"要求）；prev=false（末行被自定义替换）或数据
            // 末端/NaN 不登记。lastX/lastY 保持末个可见点（lastValueMark 语义）
            if (prevWasFinite && range.last + 1 < column.size && !column[range.last + 1].isNaN()) {
                val xNext = vp.candleCenterX((range.last + 1).toDouble()).toFloat()
                val yNext = y(column[range.last + 1]).toFloat()
                val seg = if (styleOverrideOf != null) {
                    val (c0, w0) = styleOverrideOf(key, range.last)
                    segFor(c0 ?: baseColor, w0 ?: lineSizePx, lastX, lastY)
                } else {
                    segFor(baseColor, lineSizePx, lastX, lastY)
                }
                if (seg.pos + 4 > seg.arr.size) seg.arr = seg.arr.copyOf(seg.arr.size * 2)
                seg.arr[seg.pos++] = lastX; seg.arr[seg.pos++] = lastY
                seg.arr[seg.pos++] = xNext; seg.arr[seg.pos++] = yNext
            }
            // 按登记顺序提交连续路径批次（保持行级回调与 A→B→A 顺序）。
            flushGroups()
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
        val figures = figurePlots(inst)
        val plots = figures.associateBy { it.key }
        val instanceStyles = IndicatorStyleResolver.normalize(inst.styles)
        val colors = figureColors(figures, theme, paletteOffset, instanceStyles?.lineColors ?: emptyList())
        val override = styleOverrideFor(inst, plots, theme)
        val attrsGeo = attrsOverrideFor(inst, plots)
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
            // 只为这些 LINE 图形绑定实际存在的回调，BAR 的样式不绕入空的逐点包装。
            val lineOverride = if (lineKeys.any { plots[it]?.styles != null }) override else null
            val lineAttrs = if (lineKeys.any { plots[it]?.attrs != null }) rawAttrsFor(inst, plots) else null
            drawLinePlots(res, vp, range, y, theme.linePalette, keys = lineKeys, paletteOffset = paletteOffset, lineSizePx = theme.indicatorLineSizePx, dashed = theme.indicatorLineDashed, lastValueMark = lastValueMark, styleOverrideOf = lineOverride?.let { o -> { k: String, r: Int -> o(k, r, res.result[k]?.getOrNull(r) ?: Double.NaN, candles) } }, attrsOf = lineAttrs?.let { la -> { k: String, r: Int, v: Double -> la(k, r, v, candles) } }, paneTop = paneTop, figureColors = colors)
        }
        if (barEntries.isNotEmpty()) {
            val barOverride = if (barEntries.any { plots[it.first]?.styles != null }) override else null
            val barAttrs = if (barEntries.any { plots[it.first]?.attrs != null }) attrsGeo else null
            drawBarPlots(res, vp, range, y, barEntries, theme.linePalette, paletteOffset + lineKeys.size, barOverride, barAttrs, candles, paneTop, colors, instanceStyles?.barColors)
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
        figureColors: Map<String, Color> = emptyMap(),
        instanceBarColors: IndicatorStyleResolver.BarColors? = null,
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
                // M03（第六轮复审 2026-10-31）+ R7-03（第七轮复审 2026-10-31）：默认
                // 几何与 attrs 按**上游含义**依赖序合成（上游 IndicatorView.ts:177-185
                // ——先算完整默认矩形再 attrs 展开覆盖；几何字段有依赖，非各自独立
                // 覆盖即等价）：
                // 1. 宽度先行——attrs.width ?? barWidth；缺省 x 按**有效宽度**居中
                //   （修复前缺省 x 先按 bodyWidth 定死，width-only 覆盖时柱中心偏移）
                // 2. y 坐标系分明——默认 y=min(vy,baseY) 已是绝对画布坐标（不再加
                //   paneTop）；仅显式 attrs.y 为 pane 相对、平移一次（修复前分支内
                //   无条件 +paneTop——height-only 覆盖把柱体移出 pane 顶 962>481）
                // 3. x=矩形**左边界**、height=柱高（M03）；pane 相对坐标 +paneTop 语义同 N04
                val aw = (geo?.get("width")?.toFloat() ?: bodyWidth).coerceAtLeast(1f)
                val ax = geo?.get("x")?.toFloat()
                    ?: (vp.candleCenterX(i.toDouble()).toFloat() - aw / 2f)
                val top: Float
                val hgt: Float
                when {
                    geo != null && (geo.containsKey("y") || geo.containsKey("height")) -> {
                        top = geo.get("y")?.toFloat()?.plus(paneTop) ?: minOf(vy, baseY)
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
                // 0.16.0-beta02 取色链（上游 全局→实例→figure 动态 对应物）：
                // 动态 provider（含内置 VOL/MACD 经 BarColorsContext 消费实例/全局三色）
                // → 实例三色按值/基线符号规则（无 provider 的自定义 BAR）→ 全局调色板
                val instanceColor = instanceBarColors?.let { b ->
                    when {
                        v > base -> b.up
                        v < base -> b.down
                        else -> b.noChange
                    }
                }
                val color = c0 ?: instanceColor ?: figureColors[key] ?: palette[(bi + paletteOffset) % palette.size]
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
     * PlotDef.styles 协议：`(row: Int, params: List<Double>) -> Map<String,String>`、
     * `(row: Int) -> Map<String,String>`、`ContextPlotStyles`（含有效三色上下文，
     * 0.16.0-beta02）或 Map 常量；null=无覆盖。
     */
    /** styles 求值：支持 (row)/(row,params)/(row,params,value,candles) 函数、ContextPlotStyles 与 Map。 */
    private fun evaluateStyles(
        value: Any?,
        row: Int,
        params: List<Double>,
        colValue: Double,
        candles: List<Candle>,
        barColors: io.lkanvas.core.indicator.BarColorsContext,
    ): Map<*, *>? = when (value) {
        null -> null
        is io.lkanvas.core.indicator.ContextPlotStyles ->
            value.styles(row, params, colValue, candles, barColors) as? Map<*, *>
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
    fun styleOverrideFor(inst: io.lkanvas.core.indicator.IndicatorInstance, theme: ChartTheme): ((String, Int, Double, List<Candle>) -> Pair<Color?, Float?>)? =
        styleOverrideFor(inst, inst.definition.plotsFor(inst.params).associateBy { it.key }, theme)

    private fun styleOverrideFor(
        inst: io.lkanvas.core.indicator.IndicatorInstance,
        plots: Map<String, io.lkanvas.core.indicator.PlotDef>,
        theme: ChartTheme,
    ): ((String, Int, Double, List<Candle>) -> Pair<Color?, Float?>)? {
        if (plots.values.all { it.styles == null }) return null
        val parsedColors = HashMap<String, Color>()
        // 有效三色上下文每实例解析一次（实例覆盖→全局回退）；provider 行内自行选用
        val barColors = IndicatorStyleResolver.barColorsContext(inst, theme)
        return { key, row, colValue, candles ->
            val map: Map<*, *> = evaluateStyles(plots[key]?.styles, row, inst.params, colValue, candles, barColors) ?: emptyMap<Any?, Any>()
            // 仍逐行执行 styles；只复用相同字符串的纯颜色解析，缓存有界。
            val color = map["color"]?.toString()?.let { value ->
                parsedColors[value] ?: io.lkanvas.compose.theme.StylesThemeBridge.parse(value).also {
                    if (parsedColors.size < 64) parsedColors[value] = it
                }
            }
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
    fun attrsOverrideFor(inst: io.lkanvas.core.indicator.IndicatorInstance): ((String, Int, Double, List<Candle>) -> Map<String, Double>)? =
        attrsOverrideFor(inst, inst.definition.plotsFor(inst.params).associateBy { it.key })

    private fun attrsOverrideFor(
        inst: io.lkanvas.core.indicator.IndicatorInstance,
        plots: Map<String, io.lkanvas.core.indicator.PlotDef>,
    ): ((String, Int, Double, List<Candle>) -> Map<String, Double>)? {
        if (plots.values.all { it.attrs == null }) return null
        return { key, row, colValue, candles ->
            // N04（第五轮复审 2026-10-31）：attrs 求值传入**真实** value+candles
            //（修复前 NaN/空列表——上游 figure.attrs 的 data 上下文语义）
            val map: Map<*, *> = evaluateStyles(plots[key]?.attrs, row, inst.params, colValue, candles, io.lkanvas.core.indicator.BarColorsContext()) ?: emptyMap<Any?, Any>()
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
    private fun rawAttrsFor(
        inst: io.lkanvas.core.indicator.IndicatorInstance,
        plots: Map<String, io.lkanvas.core.indicator.PlotDef>,
    ): ((String, Int, Double, List<Candle>) -> Map<*, *>?)? {
        if (plots.values.all { it.attrs == null }) return null
        return { key, row, colValue, candles ->
            evaluateStyles(plots[key]?.attrs, row, inst.params, colValue, candles, io.lkanvas.core.indicator.BarColorsContext())
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
        clipTop: Float,
        clipBottom: Float,
    ) {
        val centers = ticks.map { y(it).toFloat() }
        for ((index, top) in axisLabelPositions(centers, text.textHeight, clipTop, clipBottom)) {
            drawText(text, ticks[index], Offset(axisRight + 6f, top), theme.axisText)
        }
    }
}
