package io.lkanvas.compose.draw

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import kotlin.math.roundToInt
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
        /**
         * Styles 树（0.3.0 批次 7）：非空时主图/副图 tooltip 走 [TooltipRenderer]
         * 消费 showRule/showType/features；null 时保持 ChartTheme 行式图例。
         */
        val styles: io.lkanvas.chart.styles.Styles? = null,
        /**
         * T-34 tooltip 缓存：非空时 tooltip 按 (revision, idx, styles) 录制
         * DrawOp 列表并回放（静态数据下 tooltip 逐帧 130+ 行 measure+drawText
         * 实测占 27ms/帧）。键与缓存的读写经 [TooltipKeyHolder]（调用方 remember）。
         */
        val tooltipCache: TooltipKeyHolder? = null,
        /** 0.4.0 多轴：每个自定义轴标签列宽 px（调用方按字体缩放算好）。 */
        val extraAxisColW: Float = 0f,
        /**
         * 右轴自适应宽度策略（issue #1 / 上游 `yAxis.size='auto'` 平台移植）：
         * 非空时槽宽 = max([gutterWidth], 实测刻度/tag 文本所需宽度，settle 防抖)；
         * null 保持固定 [gutterWidth]。屏幕路径由 KLineChart remember 持有
         * （跨帧 settle 状态）；导出路径不注入（直接复用屏幕生效值）。
         */
        val axisWidthPolicy: AxisWidthPolicy? = null,
        /**
         * 自适应槽宽帧末回写 [ChartState.applyAxisGutter]（屏幕渲染 true）。
         * 导出 false——导出按屏幕 contentWidth 等比缩放，不得回写污染手势布局。
         */
        val gutterFeedback: Boolean = false,
    )

    /** tooltip 缓存（KLineChart remember 持有，绘制阶段读写）。 */
    class TooltipKeyHolder {
        internal var key: Any? = null
        internal var ops: List<io.lkanvas.compose.draw.TooltipRenderer.DrawOp>? = null
        internal var mainBounds: List<Rect> = emptyList()
        internal var legendHeights: Map<String, Float> = emptyMap()
    }

    fun DrawScope.render(
        state: ChartState,
        theme: ChartTheme,
        params: Params,
        textMeasurer: TextMeasurer,
    ) {
        val panes = state.paneHeightsPx
        val text = ChartText(textMeasurer, theme.axisText, params.axisTextSizePx, format = state.formatConfig)

        drawRect(theme.background, size = size)

        if (state.dataSnapshot.candles.isEmpty()) return

        // issue #1：右轴槽宽自适应（上游 `yAxis.size='auto'` getAutoSize 平台移植）。
        // 实测全部落入 gutter 的文本（主图/副图刻度 + 末价/十字线 tag），配置
        // gutter 为下限；settle 防抖（立即扩、静默 400ms 后才收窄、4px 网格）。
        val policy = params.axisWidthPolicy
        val effectiveGutter = if (policy == null) params.gutterWidth else {
            var needed = policy.neededWidth(axisGutterTexts(state, text::price), text::measureWidth)
            val tagNeeded = tagReservedWidth(state, text, theme)
            if (tagNeeded > needed) needed = tagNeeded
            if (needed > 0f) maxOf(params.gutterWidth, policy.settle(needed)) else params.gutterWidth
        }
        val contentRight = params.width - effectiveGutter

        val mainTop = 0f
        val mainBottom = panes.main
        val volTop = mainBottom + 1f
        val volBottom = volTop + panes.vol
        val macdTop = volBottom + 1f
        val macdBottom = macdTop + panes.macd

        // 分隔线（Styles separator.color/size 消费）
        drawLine(theme.separatorColor, Offset(0f, mainBottom), Offset(params.width, mainBottom), theme.separatorSizePx)
        drawLine(theme.separatorColor, Offset(0f, volBottom), Offset(params.width, volBottom), theme.separatorSizePx)
        // 0.5.x 动态副图分隔线
        for ((_, bounds) in state.extraPaneLayout) {
            drawLine(theme.separatorColor, Offset(0f, bounds.second), Offset(params.width, bounds.second), theme.separatorSizePx)
        }

        val crosshair = if (params.includeCrosshair && theme.crosshairShow) state.crosshairPosition else null
        // 主 pane 顶部两行图例（OHLC + MA）的避让高度
        val legendHeight = params.legendTextSizePx * 3.2f
        val styles = params.styles
        val cache = params.tooltipCache
        val candles = state.dataSnapshot.candles
        val idx = crosshair?.let { state.viewport.logicalIndexAt(it.x.toDouble()).roundToInt().coerceIn(candles.indices) } ?: candles.lastIndex
        val key = listOf(
            System.identityHashCode(state.dataSnapshot), idx, System.identityHashCode(styles),
            params.width, params.height, effectiveGutter, params.legendTextSizePx,
            density, fontScale, panes, state.extraPaneLayout,
            crosshair != null, if (styles?.candle?.tooltip?.showType == "rect" || styles?.indicator?.tooltip?.showType == "rect") crosshair else null,
            theme, state.formatRevision, System.identityHashCode(state.formatConfig), state.getIndicators().map { listOf(it.id, System.identityHashCode(it.state), it.visible, it.params, it.precisionOverride, it.displayName(), it.yAxisId, System.identityHashCode(it.styles)) },
        )
        val cacheHit = cache != null && cache.key == key && cache.ops != null
        // Layout once before price marks, then replay at the original tooltip layer.
        val mainOps = if (params.drawLegend && styles != null && !cacheHit) {
            with(TooltipRenderer) { recordCandleTooltip(state, styles, theme, textMeasurer, params.legendTextSizePx, crosshair, contentRight, mainBottom) }
        } else emptyList()
        text.markerExclusions = when {
            !params.drawLegend -> emptyList()
            styles == null -> listOf(Rect(0f, 0f, contentRight, 4f + params.legendTextSizePx * 1.4f + text.textHeight))
            cacheHit -> cache!!.mainBounds
            else -> mainOps.map { tooltipBounds(it, textMeasurer) }
        }

        val tooltipOps = if (!params.drawLegend || styles == null) emptyList() else if (cacheHit) cache!!.ops!! else {
            val ops = ArrayList(mainOps)
            drawSubPaneTooltips(state, styles, theme, textMeasurer, params.legendTextSizePx, crosshair, contentRight, recorder = ops, drawContent = false)
            ops
        }
        state.legendHeightsPx = when {
            !params.drawLegend -> emptyMap()
            styles == null -> mapOf("main" to legendHeight)
            cacheHit -> cache!!.legendHeights
            else -> tooltipOps.filter { it.reserveLegendSpace }.groupBy { op ->
                if (op.clipBounds == null) "main" else state.paneList().firstOrNull { pane ->
                    val top = state.paneBounds(pane)?.first ?: return@firstOrNull false
                    kotlin.math.abs(top - op.clipBounds!!.top) <= 1f
                } ?: "main"
            }.mapValues { (pane, ops) ->
                val top = state.paneBounds(pane)?.first ?: 0f
                ops.maxOf { op -> minOf(tooltipBounds(op, textMeasurer).bottom, op.clipBounds?.bottom ?: mainBottom) } - top + 4f
            }
        }

        // 0.4.0 多轴：自定义轴标签列坐标（主轴列右侧依次向右；主轴列宽 = tagWidth+8）
        val extraAxisCols = if (params.extraAxisColW > 0f && state.extraYAxisMeta.isNotEmpty()) {
            val mainCol = text.tagWidth + 8f
            // 0.5.x：仅独立列轴占位（inside/LEFT 轴画 pane 内右缘）
            state.extraYAxisMeta.values
                .filter { !it.inside && !it.positionLeft }
                .mapIndexed { i, _ -> contentRight + mainCol + i * params.extraAxisColW }
        } else emptyList()
        with(PaneRenderers) {
            drawMainPane(state, theme, 0f, contentRight, mainTop, mainBottom, text, crosshair, legendHeight, extraAxisCols = extraAxisCols)
            // R07：vol/macd 按需存在（不在 pane 集合时高度 0 → 跳过绘制）
            if (state.hasBuiltinPane("vol")) {
                drawVolumePane(state, theme, 0f, contentRight, volTop, volBottom, text)
            }
            if (state.hasBuiltinPane("macd")) {
                drawMacdPane(state, theme, 0f, contentRight, macdTop, macdBottom, text)
            }
            // 0.5.x 动态副图：逐 pane 画绑定指标的线列 + 网格 + 标签
            for ((paneId, bounds) in state.extraPaneLayout) {
                drawExtraPane(state, theme, text, paneId, 0f, contentRight, bounds.first, bounds.second)
            }
        }
        // 覆盖物（主 pane 区域）
        with(OverlayRenderer) {
            drawOverlays(state, theme, contentRight, mainBottom, text)
        }
        // 0.5.x 动态副图：时间轴跟随最后一个自定义 pane 的下缘（否则与其重叠）
        val timeAxisTop = state.extraPaneLayout.lastOrNull()?.second?.second ?: macdBottom
        drawTimeAxis(state, theme, text, 0f, contentRight, timeAxisTop, panes.timeAxis)
        if (params.drawLegend) {
            if (styles != null) {
                if (cacheHit) {
                    // T-34：回放缓存的 DrawOp（文本 measure 命中 TextMeasurer 缓存）
                    replayTooltip(tooltipOps, textMeasurer)
                } else {
                    replayTooltip(tooltipOps, textMeasurer)
                    if (cache != null) {
                        cache.key = key
                        cache.ops = tooltipOps
                        cache.mainBounds = text.markerExclusions
                        cache.legendHeights = state.legendHeightsPx
                    }
                }
            } else {
                drawLegend(state, theme, text, params.legendTextSizePx, crosshair, effectiveGutter, params.width)
            }
        }
        if (crosshair != null) {
            drawCrosshairLabels(state, theme, text, crosshair, contentRight, legendHeight)
        }
        // 帧末回写生效槽宽（仅屏幕路径；导出不回写），内容区宽与手势命中保持同源
        if (policy != null && params.gutterFeedback) {
            state.applyAxisGutter(params.width, effectiveGutter)
        }
    }

    /**
     * 右轴 gutter 内全部刻度文本（issue #1 槽宽实测来源；与各 pane 绘制同参同源）：
     * 主图 [YAxisMath.ticks] 规格文本 + vol/macd/动态副图 [LinearYAxis.ticks]
     * 经 [format]（调用方绑定 `ChartText::price`，测试可注入）。
     */
    internal fun axisGutterTexts(state: ChartState, format: (Double) -> String): List<String> {
        if (state.dataSnapshot.candles.isEmpty()) return emptyList()
        val texts = ArrayList<String>(16)
        state.mainPaneRange()?.let { mpr ->
            val (lo, hi) = state.axisDisplayRange("main", mpr)
            for (t in YAxisMath.ticks(state.effectiveMainAxisSpec(), lo, hi, 5).ticks) texts.add(t.text)
        }
        val paneIds = buildList {
            if (state.hasBuiltinPane("vol")) add("vol")
            if (state.hasBuiltinPane("macd")) add("macd")
            for ((pane, _) in state.extraPaneLayout) add(pane)
        }
        for (paneId in paneIds) {
            val pr = state.paneVisibleRange(paneId) ?: continue
            val (lo, hi) = state.axisDisplayRange(paneId, pr)
            for (v in LinearYAxis.ticks(lo, hi, 3)) texts.add(format(v))
        }
        return texts
    }

    /**
     * 末价/十字线 tag 所需槽宽（drawTag 几何：底色块 = contentRight+2 起、宽
     * tagWidth 启发式；实测文本可能更宽）。十字线最坏文本按主区当前量级十位
     * 锚点估算（上游 `widthAnchorValue` 移植）。无 tag 展示时返回 0。
     */
    private fun tagReservedWidth(state: ChartState, text: ChartText, theme: ChartTheme): Float {
        if (!theme.lastPriceShow && !theme.crosshairShow) return 0f
        val candles = state.dataSnapshot.candles
        var measured = 0f
        if (theme.lastPriceShow) {
            measured = maxOf(measured, text.measureWidth(text.price(candles.last().close)))
        }
        if (theme.crosshairShow) {
            val range = state.mainPaneRange()
            if (range != null) {
                val (lo, hi) = state.axisDisplayRange("main", range)
                val anchored = AxisWidthPolicy.widthAnchorValue(lo, hi)
                if (anchored.isFinite()) measured = maxOf(measured, text.measureWidth(text.price(anchored)))
            }
        }
        if (measured <= 0f) return 0f
        return AxisWidthPolicy.TAG_MARGIN_START + maxOf(text.tagWidth, measured)
    }

    private fun tooltipBounds(op: TooltipRenderer.DrawOp, measurer: androidx.compose.ui.text.TextMeasurer): Rect = when (op) {
        is TooltipRenderer.DrawOp.Text -> {
            val layout = measurer.measure(op.text, TooltipRenderer.textStyle(op.sizePx, op.color))
            Rect(op.topLeft, Size(layout.size.width.toFloat(), layout.size.height.toFloat()))
        }
        is TooltipRenderer.DrawOp.RoundRect -> Rect(op.topLeft, op.size)
        is TooltipRenderer.DrawOp.StrokePath -> op.path.getBounds()
    }

    private fun DrawScope.replayTooltip(ops: List<TooltipRenderer.DrawOp>, textMeasurer: TextMeasurer) {
        for (op in ops) {
            val clip = op.clipBounds
            if (clip != null) {
                drawContext.canvas.save()
                drawContext.canvas.clipRect(clip)
            }
            try {
                when (op) {
                    is io.lkanvas.compose.draw.TooltipRenderer.DrawOp.Text ->
                        drawText(textMeasurer, op.text, topLeft = op.topLeft, style = io.lkanvas.compose.draw.TooltipRenderer.textStyle(op.sizePx, op.color))

                    is io.lkanvas.compose.draw.TooltipRenderer.DrawOp.RoundRect ->
                        if (op.strokeWidth > 0f) {
                            drawRoundRect(op.color, topLeft = op.topLeft, size = op.size, cornerRadius = CornerRadius(op.radius, op.radius), style = Stroke(width = op.strokeWidth))
                        } else {
                            drawRoundRect(op.color, topLeft = op.topLeft, size = op.size, cornerRadius = CornerRadius(op.radius, op.radius))
                        }

                    is io.lkanvas.compose.draw.TooltipRenderer.DrawOp.StrokePath ->
                        drawPath(op.path, op.color, style = Stroke(width = op.strokeWidth))
                }
            } finally {
                if (clip != null) drawContext.canvas.restore()
            }
        }
    }

    private fun DrawScope.clipPaneTooltip(
        state: ChartState,
        bounds: Rect,
        recorder: MutableList<TooltipRenderer.DrawOp>?,
        draw: DrawScope.() -> Unit,
    ) {
        if (bounds.isEmpty) return
        val firstOp = recorder?.size ?: 0
        val firstHit = state.tooltipFeatureHits.size
        clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom, block = draw)
        recorder?.let { for (i in firstOp until it.size) it[i].clipBounds = bounds }
        if (state.tooltipFeatureHits.size > firstHit) {
            state.tooltipFeatureHits = state.tooltipFeatureHits.mapIndexedNotNull { i, hit ->
                if (i < firstHit) hit else {
                    val visible = hit.rect.intersect(bounds)
                    if (visible.isEmpty) null else hit.copy(rect = visible)
                }
            }
        }
    }

    /** 副图指标 tooltip（7b：VOL/MACD pane 顶部 name+legends 行）。 */
    internal fun DrawScope.drawSubPaneTooltips(
        state: ChartState,
        styles: io.lkanvas.chart.styles.Styles,
        theme: ChartTheme,
        textMeasurer: androidx.compose.ui.text.TextMeasurer,
        baseTextSizePx: Float,
        crosshair: Offset?,
        contentRight: Float,
        recorder: MutableList<io.lkanvas.compose.draw.TooltipRenderer.DrawOp>? = null,
        drawContent: Boolean = true,
    ) {
        val candles = state.dataSnapshot.candles
        if (candles.isEmpty()) return
        with(TooltipRenderer) {
            for (pane in state.paneList().filter { it != "main" }) {
                val (top, bottom) = state.paneBounds(pane) ?: continue
                val end = bottom - styles.indicator.tooltip.offsetBottom.toFloat()
                clipPaneTooltip(state, Rect(0f, top + 1f, contentRight, end.coerceAtLeast(top + 1f)), recorder) {
                    drawIndicatorTooltip(state, styles, theme, textMeasurer, baseTextSizePx, crosshair, contentRight, top, pane,
                        recorder, drawContent)
                }
            }
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
        val timeZone = state.effectiveTimeZone()
        var prevTs: Long? = null
        var lastLabelRight = Float.NEGATIVE_INFINITY
        var i = range.first
        while (i <= range.last) {
            val c = candles[i]
            val x = state.viewport.candleCenterX(i.toDouble()).toFloat()
            // 中心尚未越过上一标签右缘和间距的候选不会绘出，先跳过格式化。
            // prevTs 仍逐根推进；标签缓存包含上下文，不依赖此前的生成顺序。
            if (lastLabelRight == Float.NEGATIVE_INFINITY || x > lastLabelRight + 6f) {
                // 0.16.0-beta02 formatter 接线（xAxis 场景）：库按默认规则选模板，
                // formatter 未配置/返回 null 时回退默认标签（缓存路径不变）
                val template = ChartText.axisTemplate(c.timestamp, prevTs, interval, timeZone)
                val label = state.formattedTimeLabel(c.timestamp, template, io.lkanvas.compose.state.ChartState.FORMAT_SCENE_X_AXIS)
                    ?: ChartText.timeAxisLabel(c.timestamp, prevTs, interval, timeZone)
                if (label.isNotEmpty()) {
                    val width = text.measureWidth(label)
                    val drawX = (x - width / 2f).coerceIn(left, (right - width).coerceAtLeast(left))
                    if (drawX > lastLabelRight + 6f) {
                        drawText(text, label, Offset(drawX, top + 3f), theme.axisText)
                        lastLabelRight = drawX + width
                    }
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
        val idx = crosshair?.let { vp.logicalIndexAt(it.x.toDouble()).roundToInt().coerceIn(candles.indices) }
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

        // 无 Styles 的 ChartTheme 入口也使用同一份图形顺序与颜色。
        var lx = 8f
        val ly = y + textSizePx * 1.4f
        for (group in TooltipRenderer.buildMainIndicatorGroups(state, idx, io.lkanvas.chart.styles.Styles(), theme, text)) {
            for (row in group.legends) {
                val label = "${row.title} ${row.value}"
                if (lx > contentWidth - 40f) break
                drawText(text, label, Offset(lx, ly), row.valueColor)
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
        // T05（第三轮复审 2026-10-30）：标签按十字线所在 pane 的轴反算
        // （修复前恒 mainPaneValueAt——副 pane 十字线显示主图价格）
        val paneId = state.crosshairPaneId ?: state.paneIdAt(crosshair.y)
        val value = if (paneId == "main") {
            state.mainPaneValueAt(crosshair.y)
        } else {
            state.paneValueAt(paneId, crosshair.y - (state.paneBounds(paneId)?.first ?: 0f))
        } ?: return
        val clampedY = crosshair.y.coerceIn(legendHeight + text.tagHeight / 2f, Float.MAX_VALUE)
        drawTag(text, value, clampedY, contentRight, theme.crosshairLine)
    }
}
