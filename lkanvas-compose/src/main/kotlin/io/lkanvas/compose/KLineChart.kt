package io.lkanvas.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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


    val subPaneH = with(density) { config.subPaneHeight.toPx() }
    val timeAxisH = with(density) { config.timeAxisHeight.toPx() }
    // 右轴留白随系统字体缩放（A-41：字体放大后轴标签仍完整显示；1..2 倍钳制）
    val fontScale = density.fontScale.coerceIn(1f, 2f)
    val gutterW = with(density) { config.rightAxisGutter.toPx() * fontScale }
    val legendTextSizePx = with(density) { config.legendTextSize.toPx() }
    val textSizePx = with(density) { config.axisTextSize.toPx() }

    // 布局回调（尺寸变化）
    val layoutModifier = Modifier.onSizeChanged { size ->
        val contentW = size.width - gutterW
        val mainH = (size.height - 2 * subPaneH - timeAxisH).coerceAtLeast(0f)
        state.onLayout(contentW, ChartState.PaneHeights(mainH, subPaneH, subPaneH, timeAxisH))
    }

    // 无障碍摘要依赖的状态（读取以触发语义更新）
    val a11yRevision = state.viewRevision + state.overlayRevision +
        (state.accessibilitySelectedIndex ?: -1)
    val a11yText = remember(a11yRevision, state.dataSnapshot.candles.size) {
        state.accessibilityText { ts -> ChartText.timeLabel(ts, 60_000L, state.effectiveTimeZone()) }
    }

    // 图例数据（插槽非空或模式变化时随 viewRevision 重组）
    val useCustomLegend = legendSlot != null && state.legendMode == ChartState.LegendMode.VISIBLE
    val legendData = if (useCustomLegend) {
        val rev = state.viewRevision + state.overlayRevision + (state.accessibilitySelectedIndex ?: -1)
        remember(rev) { state.legendData() }
    } else {
        null
    }

    androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(layoutModifier)
                .chartGestures(state)
                .semantics {
                    contentDescription = a11yText
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
                    ),
                    textMeasurer = textMeasurer,
                )
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
