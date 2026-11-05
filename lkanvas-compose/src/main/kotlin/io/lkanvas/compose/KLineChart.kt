package io.lkanvas.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import io.lkanvas.compose.draw.ChartRenderer
import io.lkanvas.compose.draw.ChartText
import io.lkanvas.compose.gesture.chartGestures
import io.lkanvas.compose.state.ChartState
import io.lkanvas.compose.theme.ChartLayoutConfig
import io.lkanvas.compose.theme.ChartTheme

/**
 * K 线图组件（主图 + VOL + MACD 三 pane；六种主图类型、三种轴、覆盖物、画线）。
 *
 * 数据与指标由 [state] 提供；本组件负责组合、布局回调、手势与无障碍语义，
 * 绘制统一委托 [ChartRenderer]（与 PNG 导出共用路径，PRD R-15）。
 * 不为每根 candle 创建 Composable 节点；平移/缩放仅触发 Canvas 重绘。
 */
@Composable
fun KLineChart(
    state: ChartState,
    modifier: Modifier = Modifier,
    theme: ChartTheme = ChartTheme(),
    config: ChartLayoutConfig = ChartLayoutConfig(),
    /** 图例定制插槽（PRD R-11/T-24）：非空时替代内置 Canvas 图例，随选择/十字线重组。 */
    legendSlot: (@Composable (io.lkanvas.compose.state.LegendData) -> Unit)? = null,
    /**
     * Styles 树（0.3.0 批次 7）：非空时 tooltip 消费 showRule/showType/features
     * （门面用户传 `facade.getStyles()` 保持 setStyles 即时生效）；null=ChartTheme 行式图例。
     */
    styles: io.lkanvas.chart.styles.Styles? = null,
) {
    val density = LocalDensity.current
    // T-34：tooltip/轴每帧大量文本测量，默认 TextMeasurer LRU(8) 全 miss 反复布局——放大缓存
    val textMeasurer = rememberTextMeasurer(cacheSize = 256)
    // T-34：tooltip DrawOp 缓存（静态内容录制一次回放，实测省 ~27ms/帧）
    val tooltipCache = remember { ChartRenderer.TooltipKeyHolder() }
    // issue #1：右轴自适应宽度（上游 yAxis.size='auto' 平台移植）——跨帧 settle 状态 per-chart 持有
    val axisWidthPolicy = remember { io.lkanvas.compose.draw.AxisWidthPolicy() }
    // 平台 fling 曲线（OverScroller：RecyclerView 同款 native spline，系统级手感）
    val context = androidx.compose.ui.platform.LocalContext.current
    state.flingScroller = remember { android.widget.OverScroller(context) }


    // 字体放大时同时为副图图例与时间标签保留高度。
    val fontScale = density.fontScale.coerceIn(1f, 2f)
    val subPaneH = with(density) { config.subPaneHeight.toPx() } * fontScale
    val timeAxisH = with(density) { config.timeAxisHeight.toPx() } * fontScale
    // 0.4.0 多轴：每个自定义轴标签列追加 gutter 宽度（44dp，随字体缩放）
    val extraAxisColW = with(density) {
        androidx.compose.ui.unit.Dp(44f).toPx() * fontScale
    }
    val gutterW = with(density) { config.rightAxisGutter.toPx() * fontScale } +
        state.extraYAxisMeta.values.count { !it.inside && !it.positionLeft } * extraAxisColW
    val legendTextSizePx = with(density) { config.legendTextSize.toPx() }
    val textSizePx = with(density) { config.axisTextSize.toPx() }

    // 布局回调（尺寸变化）
    val layoutModifier = Modifier.onSizeChanged { size ->
        val contentW = size.width - gutterW
        // 0.5.x 动态副图：容器高扣除自定义 pane 数 × 默认副图高
        val timeH = timeAxisH.coerceAtMost(size.height * 0.15f)
        val subCount = 2 + state.extraPanes.value.size
        val paneH = subPaneH.coerceAtMost((size.height - timeH) / (subCount + 2))
        val mainH = (size.height - subCount * paneH - timeH).coerceAtLeast(0f)
        state.onLayout(contentW, ChartState.PaneHeights(mainH, paneH, paneH, timeH))
    }

    // 图例数据（插槽非空或模式变化时随 viewRevision 重组）
    val useCustomLegend = legendSlot != null && state.legendMode == ChartState.LegendMode.VISIBLE
    val legendData = if (useCustomLegend) {
        val rev = state.viewRevision + state.overlayRevision
        remember(rev, state.accessibilitySelectedIndex) { state.legendData() }
    } else {
        null
    }

    androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // 将图表重绘限制在自己的层，避免重录整个宿主界面。
                .graphicsLayer()
                .then(layoutModifier)
                .chartGestures(state)
                .semantics {
                    // 在语义阶段订阅摘要，视口平移不必重组整个图表节点。
                    @Suppress("UNUSED_VARIABLE") val a11yRevision = state.viewRevision + state.overlayRevision
                    contentDescription = state.accessibilityText { ts ->
                        // 0.16.0-beta02：语义时间与 tooltip 同场景（formatter 接线）
                        state.formattedTimeLabel(ts, io.lkanvas.compose.state.ChartState.TEMPLATE_TIME, io.lkanvas.compose.state.ChartState.FORMAT_SCENE_TOOLTIP)
                            ?: ChartText.timeLabel(ts, 60_000L, state.effectiveTimeZone())
                    }
                    customActions = listOf(
                        CustomAccessibilityAction(state.locale.actionPrevCandle) { state.selectPreviousCandle() },
                        CustomAccessibilityAction(state.locale.actionNextCandle) { state.selectNextCandle() },
                        CustomAccessibilityAction(state.locale.actionZoomIn) { state.zoomIn() },
                        CustomAccessibilityAction(state.locale.actionZoomOut) { state.zoomOut() },
                        CustomAccessibilityAction(state.locale.actionDeleteOverlay) { state.removeSelectedOverlay() },
                        // 0.3.0 批次 9a：TalkBack 遍历覆盖物列表（循环选中）
                        CustomAccessibilityAction(state.locale.actionNextOverlay) { state.selectNextOverlay() },
                    )
                },
        ) {
            // 订阅视口/覆盖物版本（P1 修复）：纯 scrollIndex/barSpacing 变化
            // 无任何 snapshot state 变化也必须触发重绘（静态数据下平移/缩放）
            @Suppress("UNUSED_VARIABLE") val renderRevision = state.viewRevision + state.overlayRevision
            // T-30 余量：渲染计时（诊断开启时才记录，关闭零开销）
            val renderStart = if (state.diagnostics.isEnabled()) System.nanoTime() else 0L
            with(ChartRenderer) {
                render(
                    state = state,
                    theme = theme,
                    params = ChartRenderer.Params(
                        width = size.width,
                        height = size.height,
                        gutterWidth = gutterW,
                        legendTextSizePx = legendTextSizePx,
                        axisTextSizePx = textSizePx,
                        includeCrosshair = true,
                        drawLegend = legendSlot == null && state.legendMode == ChartState.LegendMode.VISIBLE,
                        styles = styles,
                        tooltipCache = tooltipCache,
                        extraAxisColW = extraAxisColW,
                        axisWidthPolicy = axisWidthPolicy,
                        gutterFeedback = true,
                    ),
                    textMeasurer = textMeasurer,
                )
            }
            if (renderStart != 0L) {
                state.diagnostics.lastRenderNanos.set(System.nanoTime() - renderStart)
                state.diagnostics.renderCount.incrementAndGet()
            }
        }
        // 宿主图例插槽（叠加在图表顶部；R-11「Compose slot 或事件实现宿主定制」）
        if (useCustomLegend && legendData != null) {
            androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
                legendSlot.invoke(legendData)
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
