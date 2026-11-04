package io.lkanvas.compose.draw

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import io.lkanvas.chart.styles.CandleTooltipRectStyle
import io.lkanvas.chart.styles.Styles
import io.lkanvas.chart.styles.TooltipFeatureStyle
import kotlin.math.roundToInt
import io.lkanvas.compose.state.ChartState
import io.lkanvas.compose.theme.ChartTheme
import io.lkanvas.compose.theme.StylesThemeBridge
import kotlin.math.max

/**
 * Tooltip 渲染（0.3.0 批次 7）：对齐上游 CandleTooltipView/IndicatorTooltipView。
 *
 * - showRule：`always` 常驻 / `follow_cross` 仅十字线按下时 / `none` 隐藏。
 * - showType：`standard` 行式图例（游标换行）/ `rect` 圆角卡片（title 左对齐、value 右对齐）。
 * - features 三组（left/middle/right）图标绘制并记录命中区（手势端分发三 ActionType）。
 *
 * Styles 字号在 Android 边界映射为 sp；测量、绘制、行距及缓存回放使用同一字号。
 * Canvas/ChartText 的已换算 px 字号不在此处二次转换。
 */
object TooltipRenderer {

    /**
     * T-34：tooltip 绘制操作录制（静态内容录一次，每帧回放）。
     * 实测 tooltip 逐帧 130+ 行文本（measure+draw）占 27ms/帧；录制回放后
     * 仅剩 drawText（TextMeasurer 缓存命中）。零 API 风险（GraphicsLayer
     * record/drawLayer 在 Compose 1.11 的 DrawScope 上下文存在自引用环，弃用）。
     */
    sealed class DrawOp {
        // 上游每个 pane 独立 Canvas；共享 Canvas 的缓存回放须保留相同裁剪范围。
        internal var clipBounds: Rect? = null
        internal var reserveLegendSpace: Boolean = true
        class Text(val text: String, val topLeft: Offset, val sizePx: Float, val color: Color) : DrawOp()
        class RoundRect(val color: Color, val topLeft: Offset, val size: Size, val radius: Float, val strokeWidth: Float) : DrawOp()
        class StrokePath(val path: androidx.compose.ui.graphics.Path, val color: Color, val strokeWidth: Float) : DrawOp()
    }

    /** 上游 isDrawTooltip：showRule 判定。 */
    fun shouldDraw(showRule: String, crosshairActive: Boolean): Boolean =
        showRule == "always" || (showRule == "follow_cross" && crosshairActive)

    /** 数值文本最小契约（[ChartText] 天然实现；JVM 测试可注入替身）。 */
    interface NumericText {
        fun price(v: Double): String
        fun volume(v: Double): String
    }

    /** 行式图例行（title 左 / value 同行；颜色独立）。 */
    data class LegendRow(val title: String, val value: String, val titleColor: Color, val valueColor: Color)

    /** 指标图例组（标题行 + 数值行）。 */
    data class IndicatorGroup(val name: String, val legends: List<LegendRow>, val titleColor: Color = Color.Unspecified)

    /** 上游 _getCandleTooltipLegends：O/H/L/C/V/change（change 按 priceMark.last 着色）。 */
    fun buildCandleLegends(state: ChartState, idx: Int, styles: Styles, text: NumericText): List<LegendRow> {
        val candles = state.dataSnapshot.candles
        if (candles.isEmpty() || idx !in candles.indices) return emptyList()
        val c = candles[idx]
        val lastMark = styles.candle.priceMark.last
        val tColor = StylesThemeBridge.parse(styles.candle.tooltip.legend.color)
        val prev = candles.getOrNull(idx - 1)
        val prevClose = prev?.close ?: c.close
        val change = c.close - prevClose
        val changeColor = when {
            change == 0.0 -> StylesThemeBridge.parse(lastMark.noChangeColor)
            change > 0 -> StylesThemeBridge.parse(lastMark.upColor)
            else -> StylesThemeBridge.parse(lastMark.downColor)
        }
        val changeText = if (prevClose == 0.0) "—" else "%.2f%%".format(change / prevClose * 100)
        return listOf(
            LegendRow("O", text.price(c.open), tColor, tColor),
            LegendRow("H", text.price(c.high), tColor, tColor),
            LegendRow("L", text.price(c.low), tColor, tColor),
            LegendRow("C", text.price(c.close), tColor, tColor),
            LegendRow("V", text.volume(c.volume ?: Double.NaN), tColor, tColor),
            LegendRow("涨跌", changeText, tColor, changeColor),
        )
    }

    /** 所有 pane 共用图形元数据、实例精度与逐行样式。 */
    fun buildIndicatorGroups(
        state: ChartState, paneId: String, idx: Int, styles: Styles, theme: ChartTheme,
    ): List<IndicatorGroup> {
        val tooltip = styles.indicator.tooltip
        return state.getIndicators(ChartState.IndicatorFilter(paneId = paneId))
            .filter { it.visible && it.state != null }.map { inst ->
                val name = if (tooltip.title.show && tooltip.title.showName) inst.displayName() else ""
                val params = if (tooltip.title.show && tooltip.title.showParams && inst.params.isNotEmpty())
                    "(${inst.params.joinToString(",") { trimNum(it) }})" else ""
                val candles = state.dataSnapshot.candles
                val plots = PaneRenderers.figurePlots(inst)
                val colors = IndicatorStyleResolver.figureColors(inst, theme, plots)
                val override = PaneRenderers.styleOverrideFor(inst, plots.associateBy { it.key }, theme)
                val custom = if (tooltip.legend.show) inst.definition.createTooltipDataSource?.invoke(candles, idx) else null
                val rows = when {
                    !tooltip.legend.show -> emptyList()
                    custom != null -> custom.map { (key, value) ->
                        val color = StylesThemeBridge.parse(tooltip.legend.color)
                        LegendRow(key, value, color, color)
                    }
                    else -> plots.map { plot ->
                        val value = inst.state!!.result.columns[plot.key]?.getOrNull(idx) ?: Double.NaN
                        val color = override?.invoke(plot.key, idx, value, candles)?.first ?: colors.getValue(plot.key)
                        val formatted = indicatorValue(state, inst, value, tooltip.legend.defaultValue)
                        LegendRow(plot.title, formatted, color, color)
                    }
                }
                IndicatorGroup(name + params, rows, StylesThemeBridge.parse(tooltip.title.color))
            }
    }

    /** 保留已有公开入口；主副图消费者共用同一构造路径。 */
    @Suppress("UNUSED_PARAMETER")
    fun buildMainIndicatorGroups(state: ChartState, idx: Int, styles: Styles, theme: ChartTheme, text: NumericText): List<IndicatorGroup> =
        buildIndicatorGroups(state, "main", idx, styles, theme)

    private fun indicatorValue(state: ChartState, inst: io.lkanvas.core.indicator.IndicatorInstance?, value: Double?, default: String): String {
        if (value == null || !value.isFinite()) return default.ifEmpty { "—" }
        val precision = io.lkanvas.core.common.utils.Format.formatPrecision(value, inst?.precisionOverride ?: inst?.definition?.precision ?: 2)
        val formatted = if (inst?.definition?.shouldFormatBigNumber == true) {
            state.formatConfig.formatBigNumber?.invoke(precision.toDouble()) ?: io.lkanvas.core.common.utils.Format.formatBigNumber(precision.toDouble())
        } else precision
        val grouped = if (formatted.toDoubleOrNull() != null) io.lkanvas.core.common.utils.Format.formatThousands(formatted, state.formatConfig.thousandsSign ?: "") else formatted
        return state.formatConfig.decimalFoldThreshold?.let { io.lkanvas.core.common.utils.Format.formatFoldDecimal(grouped, it) } ?: grouped
    }

    private fun trimNum(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    /**
     * 主 pane tooltip 入口（candle + indicator 组合；对齐上游 CandleTooltipView.drawImp）。
     * candle/indicator 的 showRule 独立生效；showType 组合决定行式块与卡片。
     * @param baseTextSizePx tooltip 基准字号（=图例字号 px）
     */
    fun DrawScope.drawCandleTooltip(
        state: ChartState,
        styles: Styles,
        theme: ChartTheme,
        measurer: TextMeasurer,
        baseTextSizePx: Float,
        crosshair: Offset?,
        contentWidth: Float,
        paneBottom: Float,
        recorder: MutableList<DrawOp>? = null,
    ) {
        val firstOp = recorder?.size ?: 0
        val bounds = Rect(0f, 0f, contentWidth, paneBottom)
        clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) {
            drawCandleTooltipImpl(state, styles, theme, measurer, baseTextSizePx, crosshair, contentWidth, paneBottom, recorder, true)
        }
        recorder?.let { for (i in firstOp until it.size) it[i].clipBounds = bounds }
    }

    internal fun DrawScope.recordCandleTooltip(
        state: ChartState, styles: Styles, theme: ChartTheme, measurer: TextMeasurer,
        baseTextSizePx: Float, crosshair: Offset?, contentWidth: Float, paneBottom: Float,
    ): List<DrawOp> {
        val ops = ArrayList<DrawOp>()
        drawCandleTooltipImpl(state, styles, theme, measurer, baseTextSizePx, crosshair, contentWidth, paneBottom, ops, false)
        ops.forEach { it.clipBounds = Rect(0f, 0f, contentWidth, paneBottom) }
        return ops
    }

    private fun DrawScope.drawCandleTooltipImpl(
        state: ChartState,
        styles: Styles,
        theme: ChartTheme,
        measurer: TextMeasurer,
        baseTextSizePx: Float,
        crosshair: Offset?,
        contentWidth: Float,
        paneBottom: Float,
        recorder: MutableList<DrawOp>?,
        drawContent: Boolean,
    ) {
        val candleT = styles.candle.tooltip
        val indT = styles.indicator.tooltip
        val candles = state.dataSnapshot.candles
        val idx = crosshair
            ?.let { state.viewport.logicalIndexAt(it.x.toDouble()).roundToInt().coerceIn(candles.indices) }
            ?: (candles.size - 1)
        val tooltipText = ChartText(measurer, theme.axisText, baseTextSizePx, format = state.formatConfig)
        val groups = if (shouldDraw(indT.showRule, crosshair != null)) buildMainIndicatorGroups(state, idx, styles, theme, tooltipText) else emptyList()
        val drawCandle = shouldDraw(candleT.showRule, crosshair != null)
        val drawInd = shouldDraw(indT.showRule, crosshair != null)
        state.tooltipFeatureHits = emptyList()
        if (!drawCandle && !drawInd) return
        val session = Session(state, measurer, baseTextSizePx, candles, recorder, drawContent, Rect(0f, 0f, contentWidth, paneBottom))
        val candleRows = if (drawCandle && candleT.legend.show) buildCandleLegends(state, idx, styles, tooltipText) else emptyList()
        val visibleGroups = if (drawInd) groups else emptyList()
        val candleRect = candleT.showType == "rect"
        val indicatorRect = indT.showType == "rect"
        var top = candleT.offsetTop.toFloat()
        val left = candleT.offsetLeft.toFloat()
        val right = (contentWidth - candleT.offsetRight.toFloat()).coerceAtLeast(left)
        if (!candleRect || !indicatorRect) {
            top = session.drawStandardBlock(
                this, crosshair, idx, styles, right,
                if (candleRect) emptyList() else candleRows,
                if (indicatorRect) emptyList() else visibleGroups,
                top, left,
            )
            if (drawInd && !indicatorRect) top = session.drawFeatureRow(this, indT.features, "main", Offset(left, top), right, active = false)
        }
        if (candleRect || indicatorRect) {
            session.drawRectCard(
                this, if (candleRect) candleRows else emptyList(), if (indicatorRect) visibleGroups else emptyList(),
                crosshair, contentWidth, paneBottom - candleT.offsetBottom.toFloat(), top,
                candleT.rect, styles,
            )
        }
    }

    /** 按 pane 遍历全部实例，动态副图与内置副图不再使用专用 keys/色板。 */
    fun DrawScope.drawIndicatorTooltip(
        state: ChartState, styles: Styles, theme: ChartTheme, measurer: TextMeasurer,
        baseTextSizePx: Float, crosshair: Offset?, contentWidth: Float, paneTop: Float, paneId: String,
        recorder: MutableList<DrawOp>? = null, drawContent: Boolean = true,
    ) {
        val candles = state.dataSnapshot.candles
        if (candles.isEmpty()) return
        val idx = crosshair?.let { state.viewport.logicalIndexAt(it.x.toDouble()).roundToInt().coerceIn(candles.indices) } ?: candles.lastIndex
        drawIndicatorGroups(state, styles, measurer, baseTextSizePx, crosshair, contentWidth, paneTop, paneId,
            buildIndicatorGroups(state, paneId, idx, styles, theme), recorder, drawContent)
    }

    private fun DrawScope.drawIndicatorGroups(
        state: ChartState, styles: Styles, measurer: TextMeasurer, baseTextSizePx: Float,
        crosshair: Offset?, contentWidth: Float, paneTop: Float, paneId: String, groups: List<IndicatorGroup>,
        recorder: MutableList<DrawOp>?, drawContent: Boolean,
    ) {
        val t = styles.indicator.tooltip
        if (!shouldDraw(t.showRule, crosshair != null)) return
        val paneHeight = state.paneBounds(paneId)?.takeIf { paneId in state.paneList() }?.let { it.second - it.first }
        val bottom = minOf(size.height, paneHeight?.let { paneTop + it } ?: size.height)
        val session = Session(state, measurer, baseTextSizePx, state.dataSnapshot.candles, recorder, drawContent, Rect(0f, paneTop, contentWidth, bottom))
        val left = t.offsetLeft.toFloat()
        val flow = Session.Flow((contentWidth - t.offsetRight.toFloat()).coerceAtLeast(left), paneTop + t.offsetTop.toFloat(), left)
        for (group in groups) {
            if (t.title.show && group.name.isNotEmpty()) session.drawFlowRow(this, LegendRow("", group.name, group.titleColor, group.titleColor), flow,
                t.title.marginTop.toFloat(), t.title.marginBottom.toFloat(), t.title.size.toFloat())
            if (t.legend.show) for (row in group.legends) session.drawFlowRow(this, row, flow,
                t.legend.marginTop.toFloat(), t.legend.marginBottom.toFloat(), t.legend.size.toFloat())
            flow.x = left
            flow.y = flow.bottom
        }
        session.drawFeatureRow(this, t.features, paneId, Offset(left, flow.bottom), flow.maxWidth, active = false)
    }

    /**
     * 副图指标 tooltip 行（7b：pane 顶部 name+legends；7c：副图 features 命中区）。
     * @param legendKeys (列名, 值)；值 null/NaN 显示「—」
     */
    fun DrawScope.drawPaneIndicatorTooltip(
        state: ChartState,
        styles: Styles,
        theme: ChartTheme,
        measurer: TextMeasurer,
        baseTextSizePx: Float,
        crosshair: Offset?,
        contentWidth: Float,
        paneTop: Float,
        paneId: String,
        name: String,
        legendKeys: List<Pair<String, Double?>>,
        legendColors: List<Color>,
        recorder: MutableList<DrawOp>? = null,
        drawContent: Boolean = true,
    ) {
        val t = styles.indicator.tooltip
        val inst = state.getIndicators(ChartState.IndicatorFilter(paneId = paneId)).firstOrNull { it.definition.name == name }
        val rows = legendKeys.mapIndexed { i, (key, value) ->
            val color = legendColors.getOrElse(i) { theme.linePalette.first() }
            val formatted = indicatorValue(state, inst, value, t.legend.defaultValue)
            LegendRow(key, formatted, StylesThemeBridge.parse(t.legend.color), color)
        }
        drawIndicatorGroups(state, styles, measurer, baseTextSizePx, crosshair, contentWidth, paneTop, paneId,
            listOf(IndicatorGroup(name, rows, StylesThemeBridge.parse(t.title.color))), recorder, drawContent)
    }

    /** 回放用样式构造（与 Session.style 同一语义）。 */
    fun textStyle(sizePx: Float, color: Color): androidx.compose.ui.text.TextStyle =
        androidx.compose.ui.text.TextStyle(
            fontSize = androidx.compose.ui.unit.TextUnit(sizePx, androidx.compose.ui.unit.TextUnitType.Sp),
            color = color,
        )

    /** 会话封装（测量器 + 游标；DrawScope 经参数显式传入）。recorder 非空=录制模式。 */
    private class Session(
        val state: ChartState,
        val measurer: TextMeasurer,
        val baseSizePx: Float,
        val candles: List<io.lkanvas.core.data.Candle>,
        val recorder: MutableList<DrawOp>? = null,
        val drawContent: Boolean = true,
        val bounds: Rect,
    ) {
        // Styles size 语义按 sp 渲染（12 ≈ 上游 DPR3 的 36 物理像素）
        private fun style(sizePx: Float) = TextStyle(fontSize = TextUnit(sizePx, TextUnitType.Sp))

        /** 使用实际字体度量，包含系统字体缩放与中英文字形高度。 */
        private fun lineHeight(sizeSp: Float) =
            measurer.measure("Hg国", style = style(sizeSp)).size.height.toFloat()

        private fun DrawScope.measure(s: String, sizePx: Float): Float {
            if (s.isEmpty()) return 0f
            return measurer.measure(s, style = style(sizePx)).size.width.toFloat()
        }

        private fun DrawScope.drawAt(s: String, topLeft: Offset, sizePx: Float, color: Color) {
            if (s.isEmpty()) return
            val glyph = measurer.measure(s, style = style(sizePx)).size
            // 后续整行放不下时停止；pane 比一行还矮时由 pane 裁剪保留可见文字。
            if (topLeft.x < bounds.left || topLeft.y < bounds.top || topLeft.y >= bounds.bottom || topLeft.x + glyph.width > bounds.right ||
                (glyph.height <= bounds.height && topLeft.y + glyph.height > bounds.bottom)) return
            // 录制模式：录制 + 绘制并行（录制帧本身要呈现；否则「最后一帧无 tooltip」）
            if (drawContent) drawText(measurer, s, topLeft = topLeft, style = style(sizePx).copy(color = color))
            recorder?.add(DrawOp.Text(s, topLeft, sizePx, color))
        }

        class Flow(val maxWidth: Float, var y: Float, val left: Float = 8f) {
            var x = left
            var bottom = y
        }

        fun drawFlowRow(
            scope: DrawScope, row: LegendRow, flow: Flow,
            marginTop: Float, marginBottom: Float, size: Float,
        ) {
            val full = if (row.title.isEmpty()) row.value else "${row.title} ${row.value}"
            val width = scope.run { measure(full, size) } + 8f
            if (flow.x > flow.left && flow.x + width > flow.maxWidth) {
                flow.x = flow.left
                flow.y = flow.bottom
            }
            flow.bottom = max(flow.bottom, drawRow(scope, row, flow.x, flow.y, flow.maxWidth, marginTop, marginBottom, size))
            flow.x += width
        }

        /** 行式块：标题行 + candle 行 + 指标组行，返回块底 y。 */
        fun drawStandardBlock(
            scope: DrawScope,
            crosshair: Offset?,
            idx: Int,
            styles: Styles,
            contentWidth: Float,
            candleRows: List<LegendRow>,
            groups: List<IndicatorGroup>,
            top: Float = 4f,
            left: Float = 8f,
        ): Float {
            var y = top
            val titleStyle = styles.candle.tooltip.title
            if (candleRows.isNotEmpty() && crosshair != null && titleStyle.show) {
                val c = candles.getOrNull(idx)
                if (c != null) {
                    // 0.16.0-beta02：tooltip 场景 formatter 接线（未配置回退默认+有效时区）
                    val ts = state.formattedTimeLabel(c.timestamp, io.lkanvas.compose.state.ChartState.TEMPLATE_TIME, io.lkanvas.compose.state.ChartState.FORMAT_SCENE_TOOLTIP)
                        ?: ChartText.timeLabel(c.timestamp, 60_000L, state.effectiveTimeZone())
                    y = drawRow(
                        scope,
                        LegendRow("", ts, Color.Transparent, StylesThemeBridge.parse(titleStyle.color)),
                        left, y, contentWidth,
                        titleStyle.marginTop.toFloat(), titleStyle.marginBottom.toFloat(), titleStyle.size.toFloat(),
                    )
                }
            }
            val flow = Flow(contentWidth, y, left)
            val legendStyle = styles.candle.tooltip.legend
            for (r in candleRows) {
                drawFlowRow(scope, r, flow, legendStyle.marginTop.toFloat(), legendStyle.marginBottom.toFloat(), legendStyle.size.toFloat())
            }
            val indTitle = styles.indicator.tooltip.title
            val indLegend = styles.indicator.tooltip.legend
            for (g in groups) {
                if (g.name.isNotEmpty()) {
                    drawFlowRow(scope, LegendRow("", g.name, Color.Transparent, StylesThemeBridge.parse(indTitle.color)), flow, indTitle.marginTop.toFloat(), indTitle.marginBottom.toFloat(), indTitle.size.toFloat())
                }
                for (r in g.legends) {
                    drawFlowRow(scope, r, flow, indLegend.marginTop.toFloat(), indLegend.marginBottom.toFloat(), indLegend.size.toFloat())
                }
            }
            y = flow.bottom
            return y
        }

        /** 单行（游标换行；返回新 y）。对齐上游 drawStandardTooltipLegends。 */
        fun drawRow(
            scope: DrawScope,
            r: LegendRow,
            x0: Float,
            y0: Float,
            maxWidth: Float,
            marginTop: Float,
            marginBottom: Float,
            sizePxOverride: Float?,
        ): Float {
            val sizePx = sizePxOverride ?: baseSizePx
            val full = if (r.title.isEmpty()) r.value else "${r.title} ${r.value}"
            val w = scope.run { measure(full, sizePx) }
            val h = marginTop + lineHeight(sizePx) + marginBottom
            val x = x0
            val y = y0
            var cx = x + 2f
            if (r.title.isNotEmpty()) {
                scope.run { drawAt(r.title, Offset(cx, y + marginTop), sizePx, r.titleColor) }
                cx += scope.run { measure(r.title, sizePx) } + 2f
            }
            scope.run { drawAt(r.value, Offset(cx, y + marginTop), sizePx, r.valueColor) }
            return y + h
        }

        /** rect 卡片（上游 _drawRectTooltip；rect.position: top=固定顶部 / pointer=跟随十字线）。 */
        fun drawRectCard(
            scope: DrawScope,
            candleRows: List<LegendRow>,
            groups: List<IndicatorGroup>,
            crosshair: Offset?,
            contentWidth: Float,
            paneBottom: Float,
            topOverride: Float,
            rect: CandleTooltipRectStyle,
            styles: Styles,
        ) {
            val firstOp = recorder?.size ?: 0
            // 上游 rect 只显示 legends；各组保留自己的字号和边距。
            val rows = candleRows.map { it to styles.candle.tooltip.legend } +
                groups.flatMap { it.legends }.map { it to styles.indicator.tooltip.legend }
            if (rows.isEmpty()) return
            var maxTextW = 0f
            for ((row, style) in rows) {
                val size = style.size.toFloat()
                val width = scope.run { measure(row.title, size) + measure(row.value, size) } +
                    style.marginLeft.toFloat() + style.marginRight.toFloat() + 16f
                maxTextW = max(maxTextW, width)
            }
            val borderPx = rect.borderWidth.toFloat()
            val rectW = maxTextW + borderPx * 2 + rect.paddingLeft.toFloat() + rect.paddingRight.toFloat()
            val rectH = rows.sumOf { (_, style) -> lineHeight(style.size.toFloat()).toDouble() + style.marginTop + style.marginBottom }.toFloat() + rect.paddingTop.toFloat() + rect.paddingBottom.toFloat() + borderPx * 2
            if (rectW <= 0f || rectH <= 0f) return

            val centerX = contentWidth / 2f
            val isLeft = (crosshair?.x ?: 0f) > centerX
            val rectX: Float
            val rectY: Float
            if (rect.position == "pointer" && crosshair != null) {
                rectX = if (isLeft) crosshair.x - rect.offsetRight.toFloat() - rectW
                else crosshair.x + rect.offsetLeft.toFloat()
                val minY = topOverride + rect.offsetTop.toFloat()
                rectY = (crosshair.y - rectH / 2f).coerceIn(minY, (paneBottom - rect.offsetBottom.toFloat() - rectH).coerceAtLeast(minY))
            } else {
                rectX = if (isLeft) styles.candle.tooltip.offsetLeft.toFloat() + rect.offsetLeft.toFloat() else contentWidth - styles.candle.tooltip.offsetRight.toFloat() - rect.offsetRight.toFloat() - rectW
                rectY = topOverride + rect.offsetTop.toFloat()
            }

            val radius = rect.borderRadius.toFloat()
            scope.run {
                if (drawContent) drawRoundRect(
                    StylesThemeBridge.parse(rect.color),
                    topLeft = Offset(rectX, rectY), size = Size(rectW, rectH), cornerRadius = CornerRadius(radius, radius),
                )
                recorder?.add(DrawOp.RoundRect(StylesThemeBridge.parse(rect.color), Offset(rectX, rectY), Size(rectW, rectH), radius, 0f))
                if (borderPx > 0f) {
                    if (drawContent) drawRoundRect(
                        StylesThemeBridge.parse(rect.borderColor),
                        topLeft = Offset(rectX, rectY), size = Size(rectW, rectH), cornerRadius = CornerRadius(radius, radius),
                        style = Stroke(width = borderPx),
                    )
                    recorder?.add(DrawOp.RoundRect(StylesThemeBridge.parse(rect.borderColor), Offset(rectX, rectY), Size(rectW, rectH), radius, borderPx))
                }
            }
            var ty = rectY + borderPx + rect.paddingTop.toFloat()
            val textLeft = rectX + borderPx + rect.paddingLeft.toFloat()
            val valueRight = rectX + rectW - borderPx - rect.paddingRight.toFloat()
            for ((row, style) in rows) {
                val size = style.size.toFloat()
                ty += style.marginTop.toFloat()
                scope.run {
                    drawAt(row.title, Offset(textLeft + style.marginLeft.toFloat(), ty), size, row.titleColor)
                    val vw = measure(row.value, size)
                    drawAt(row.value, Offset(valueRight - style.marginRight.toFloat() - vw, ty), size, row.valueColor)
                }
                ty += lineHeight(size) + style.marginBottom.toFloat()
            }
            recorder?.let { for (i in firstOp until it.size) it[i].reserveLegendSpace = false }
        }

        /** features 三组图标绘制（path 画比例折线、icon_font 画字符）并记录命中区。 */
        fun drawFeatureRow(
            scope: DrawScope,
            features: List<TooltipFeatureStyle>,
            paneId: String,
            start: Offset,
            maxWidth: Float,
            active: Boolean,
        ): Float {
            if (features.isEmpty()) return start.y
            val hits = ArrayList(state.tooltipFeatureHits)
            var x = start.x
            var y = start.y
            var rowBottom = start.y
            for (f in features) {
                val size = f.size.toFloat()
                val bg = StylesThemeBridge.parse(if (active) f.activeBackgroundColor else f.backgroundColor)
                val fg = StylesThemeBridge.parse(if (active) f.activeColor else f.color)
                val rect = Rect(
                    x + f.marginLeft.toFloat(), y + f.marginTop.toFloat(),
                    x + f.marginLeft.toFloat() + size, y + f.marginTop.toFloat() + size,
                )
                scope.run {
                    if (drawContent) drawRoundRect(bg, topLeft = rect.topLeft, size = Size(size, size), cornerRadius = CornerRadius(f.borderRadius.toFloat(), f.borderRadius.toFloat()))
                    recorder?.add(DrawOp.RoundRect(bg, rect.topLeft, Size(size, size), f.borderRadius.toFloat(), 0f))
                    val content = f.content
                    when {
                        f.type == "icon_font" && content is String ->
                            drawAt(content, Offset(rect.left + size * 0.2f, rect.top + size * 0.15f), size * 0.7f, fg)

                        content is List<*> && content.isNotEmpty() -> {
                            // path 比例点列 [[x,y],...]（0..1 归一 → 图标框内折线）
                            val pts = content.filterIsInstance<List<*>>().mapNotNull { pair ->
                                val nums = pair.filterIsInstance<Number>()
                                if (nums.size >= 2) Offset(rect.left + nums[0].toFloat() * size, rect.top + nums[1].toFloat() * size) else null
                            }
                            if (pts.size >= 2) {
                                val path = Path()
                                path.moveTo(pts.first().x, pts.first().y)
                                pts.drop(1).forEach { path.lineTo(it.x, it.y) }
                                if (drawContent) drawPath(path, fg, style = Stroke(width = 1.5f))
                                recorder?.add(DrawOp.StrokePath(path, fg, 1.5f))
                            }
                        }
                    }
                }
                hits += ChartState.TooltipFeatureHit(paneId, f.id, rect)
                x += size + f.marginLeft.toFloat() + f.marginRight.toFloat()
                rowBottom = max(rowBottom, rect.bottom)
                if (x > maxWidth) {
                    x = start.x
                    y = rowBottom + f.marginBottom.toFloat()
                }
            }
            state.tooltipFeatureHits = hits
            return rowBottom + 4f
        }
    }
}
