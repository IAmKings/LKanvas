package io.lkanvas.compose.draw

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import io.lkanvas.chart.styles.CandleTooltipRectStyle
import io.lkanvas.chart.styles.Styles
import io.lkanvas.chart.styles.TooltipFeatureStyle
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
 * 字号语义：Styles 的 size 为 canvas px 语义；此处直接以 px→sp 文本绘制
 * （Compose 文本测量与密度解耦），颜色/边距/开关全量消费。
 */
object TooltipRenderer {

    /**
     * T-34：tooltip 绘制操作录制（静态内容录一次，每帧回放）。
     * 实测 tooltip 逐帧 130+ 行文本（measure+draw）占 27ms/帧；录制回放后
     * 仅剩 drawText（TextMeasurer 缓存命中）。零 API 风险（GraphicsLayer
     * record/drawLayer 在 Compose 1.11 的 DrawScope 上下文存在自引用环，弃用）。
     */
    sealed class DrawOp {
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
    data class IndicatorGroup(val name: String, val legends: List<LegendRow>)

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

    /** 主 pane 指标组：name(shortName(参数)) + 每 key 行值（palette 取色）。 */
    fun buildMainIndicatorGroups(
        state: ChartState,
        idx: Int,
        styles: Styles,
        theme: ChartTheme,
        text: NumericText,
    ): List<IndicatorGroup> {
        val groups = ArrayList<IndicatorGroup>()
        for (inst in state.mainPaneIndicatorResults()) {
            val res = inst.state ?: continue
            val titleStyle = styles.indicator.tooltip.title
            val name = if (titleStyle.show && titleStyle.showName) inst.definition.shortName else ""
            val params = if (titleStyle.show && titleStyle.showParams && inst.params.isNotEmpty()) {
                "(${inst.params.joinToString(",") { trimNum(it) }})"
            } else ""
            val legendColor = StylesThemeBridge.parse(styles.indicator.tooltip.legend.color)
            val rows = res.result.keys.mapIndexed { k, key ->
                val v = res.result[key].getOrNull(idx) ?: Double.NaN
                LegendRow(key, text.price(v), legendColor, theme.linePalette[k % theme.linePalette.size])
            }
            groups += IndicatorGroup(name + params, rows)
        }
        return groups
    }

    private fun trimNum(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    /**
     * 主 pane tooltip 入口（candle + indicator 组合；对齐上游 CandleTooltipView.drawImp）。
     * indicator tooltip 恒行式（Kotlin 树 indicator.tooltip 无 showType，上游 standard 语义）。
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
        val candleT = styles.candle.tooltip
        val indT = styles.indicator.tooltip
        val candles = state.dataSnapshot.candles
        val idx = crosshair
            ?.let { state.viewport.logicalIndexAt(it.x.toDouble()).toInt().coerceIn(candles.indices) }
            ?: (candles.size - 1)
        val tooltipText = ChartText(measurer, theme.axisText, baseTextSizePx)
        val groups = buildMainIndicatorGroups(state, idx, styles, theme, tooltipText)
        val drawCandle = shouldDraw(candleT.showRule, crosshair != null)
        val drawInd = groups.any { it.name.isNotEmpty() || it.legends.any { r -> r.value != "—" } }
        state.tooltipFeatureHits = emptyList()
        if (!drawCandle && !drawInd) return
        val session = Session(state, measurer, baseTextSizePx, candles, recorder)

        val candleRows = if (drawCandle) buildCandleLegends(state, idx, styles, tooltipText) else emptyList()
        if (candleT.showType == "rect") {
            // rect 卡片：candle 行 + 指标组合并单卡（上游 both-rect 语义）
            session.drawRectCard(
                this, candleRows, if (drawInd) groups else emptyList(),
                crosshair, contentWidth, paneBottom, 0f, candleT.rect,
                candleT.legend.size.toFloat(),
            )
        } else {
            val top = session.drawStandardBlock(this, crosshair, idx, styles, contentWidth, candleRows, if (drawInd) groups else emptyList())
            // features 图标（left/middle/right 顺序），命中区记录到 state
            session.drawFeatureRow(this, indT.features, "main", Offset(8f, top), contentWidth, active = false)
        }
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
    ) {
        val indT = styles.indicator.tooltip
        if (!indT.title.show && !indT.legend.show) return
        if (indT.features.isEmpty() && legendKeys.isEmpty()) return
        val session = Session(state, measurer, baseTextSizePx, state.dataSnapshot.candles, recorder)
        val titleColor = StylesThemeBridge.parse(indT.title.color)
        val legendColor = StylesThemeBridge.parse(indT.legend.color)
        var y = paneTop + 2f
        if (indT.title.show && name.isNotEmpty()) {
            y = session.drawRow(
                this, LegendRow("", name, titleColor, titleColor),
                8f, y, contentWidth, indT.title.marginTop.toFloat(), indT.title.marginBottom.toFloat(), indT.title.size.toFloat(),
            )
        }
        for ((key, v) in legendKeys) {
            val color = legendColors.getOrElse(key.hashCode() % legendColors.size) { theme.linePalette.first() }
            y = session.drawRow(
                this,
                LegendRow(key, if (v == null || v.isNaN()) "—" else "%.2f".format(v), legendColor, color),
                8f, y, contentWidth, indT.legend.marginTop.toFloat(), indT.legend.marginBottom.toFloat(), indT.legend.size.toFloat(),
            )
        }
        // 副图 features（paneId 独立命中区）
        session.drawFeatureRow(this, indT.features, paneId, Offset(8f, y + 2f), contentWidth, active = false)
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
    ) {
        // Styles size 语义按 sp 渲染（12 ≈ 上游 DPR3 的 36 物理像素）
        private fun style(sizePx: Float) = TextStyle(fontSize = TextUnit(sizePx, TextUnitType.Sp))

        /** 实际渲染像素（行高计算用；sp×density）。 */
        private fun DrawScope.renderPx(sizePx: Float) = sizePx * this.density

        private fun DrawScope.measure(s: String, sizePx: Float): Float {
            if (s.isEmpty()) return 0f
            return measurer.measure(s, style = style(sizePx)).size.width.toFloat()
        }

        private fun DrawScope.drawAt(s: String, topLeft: Offset, sizePx: Float, color: Color) {
            if (s.isEmpty()) return
            // 录制模式：录制 + 绘制并行（录制帧本身要呈现；否则「最后一帧无 tooltip」）
            drawText(measurer, s, topLeft = topLeft, style = style(sizePx).copy(color = color))
            recorder?.add(DrawOp.Text(s, topLeft, sizePx, color))
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
        ): Float {
            var y = 4f
            val titleStyle = styles.candle.tooltip.title
            if (crosshair != null && titleStyle.show) {
                val c = candles.getOrNull(idx)
                if (c != null) {
                    val ts = ChartText.timeLabel(c.timestamp, 60_000L, state.effectiveTimeZone())
                    y = drawRow(
                        scope,
                        LegendRow("", ts, Color.Transparent, StylesThemeBridge.parse(titleStyle.color)),
                        8f, y, contentWidth,
                        titleStyle.marginTop.toFloat(), titleStyle.marginBottom.toFloat(), titleStyle.size.toFloat(),
                    )
                }
            }
            val legendStyle = styles.candle.tooltip.legend
            for (r in candleRows) {
                y = drawRow(scope, r, 8f, y, contentWidth, legendStyle.marginTop.toFloat(), legendStyle.marginBottom.toFloat(), legendStyle.size.toFloat())
            }
            val indTitle = styles.indicator.tooltip.title
            val indLegend = styles.indicator.tooltip.legend
            for (g in groups) {
                if (g.name.isNotEmpty()) {
                    y = drawRow(scope, LegendRow("", g.name, Color.Transparent, StylesThemeBridge.parse(indTitle.color)), 8f, y, contentWidth, indTitle.marginTop.toFloat(), indTitle.marginBottom.toFloat(), indTitle.size.toFloat())
                }
                for (r in g.legends) {
                    y = drawRow(scope, r, 8f, y, contentWidth, indLegend.marginTop.toFloat(), indLegend.marginBottom.toFloat(), indLegend.size.toFloat())
                }
            }
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
            val h = marginTop + scope.run { renderPx(sizePx) } * 1.25f + marginBottom
            val x = if (x0 + w > maxWidth) 8f else x0
            val y = if (x == 8f && x0 != 8f) y0 + sizePx * 1.5f else y0
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
            legendSizeSp: Float,
        ) {
            // 合并行：candle 行 + 指标组（name 行 + legends 行），空值行跳过
            val indTitleColor = StylesThemeBridge.parse(Styles().indicator.tooltip.title.color)
            val rows = (candleRows + groups.flatMap { g ->
                (if (g.name.isNotEmpty()) listOf(LegendRow("", g.name, indTitleColor, indTitleColor)) else emptyList()) + g.legends
            }).filter { it.value.isNotEmpty() }
            if (rows.isEmpty()) return
            val sizePx = legendSizeSp
            val rowH = scope.run { renderPx(sizePx) } * 1.25f
            var maxTextW = 0f
            for (r in rows) {
                val w = max(scope.run { measure(r.title, sizePx) }, scope.run { measure(r.value, sizePx) }) + 8f
                maxTextW = max(maxTextW, w)
            }
            val borderPx = rect.borderWidth.toFloat()
            val rectW = maxTextW + borderPx * 2 + rect.paddingLeft.toFloat() + rect.paddingRight.toFloat()
            val rectH = rowH * rows.size + rect.paddingTop.toFloat() + rect.paddingBottom.toFloat() + borderPx * 2
            if (rectW <= 0f || rectH <= 0f) return

            val centerX = contentWidth / 2f
            val isLeft = (crosshair?.x ?: 0f) > centerX
            val rectX: Float
            val rectY: Float
            if (rect.position == "pointer" && crosshair != null) {
                rectX = if (isLeft) crosshair.x - rect.offsetRight.toFloat() - rectW
                else crosshair.x + rect.offsetLeft.toFloat()
                rectY = (crosshair.y - rectH / 2f).coerceIn(
                    rect.offsetTop.toFloat(),
                    (paneBottom - rect.offsetBottom.toFloat() - rectH).coerceAtLeast(rect.offsetTop.toFloat()),
                )
            } else {
                rectX = if (isLeft) rect.offsetLeft.toFloat() else contentWidth - rect.offsetRight.toFloat() - rectW
                rectY = topOverride + rect.offsetTop.toFloat()
            }

            val radius = rect.borderRadius.toFloat()
            scope.run {
                drawRoundRect(
                    StylesThemeBridge.parse(rect.color),
                    topLeft = Offset(rectX, rectY), size = Size(rectW, rectH), cornerRadius = CornerRadius(radius, radius),
                )
                recorder?.add(DrawOp.RoundRect(StylesThemeBridge.parse(rect.color), Offset(rectX, rectY), Size(rectW, rectH), radius, 0f))
                if (borderPx > 0f) {
                    drawRoundRect(
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
            for (r in rows) {
                scope.run {
                    drawAt(r.title, Offset(textLeft, ty), sizePx, r.titleColor)
                    val vw = measure(r.value, sizePx)
                    drawAt(r.value, Offset(valueRight - vw, ty), sizePx, r.valueColor)
                }
                ty += rowH
            }
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
                    drawRoundRect(bg, topLeft = rect.topLeft, size = Size(size, size), cornerRadius = CornerRadius(f.borderRadius.toFloat(), f.borderRadius.toFloat()))
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
                                drawPath(path, fg, style = Stroke(width = 1.5f))
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
