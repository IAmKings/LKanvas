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
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val subPaneH = with(density) { config.subPaneHeight.toPx() }
    val timeAxisH = with(density) { config.timeAxisHeight.toPx() }
    val gutterW = with(density) { config.rightAxisGutter.toPx() }
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
        state.accessibilityText { ts -> ChartText.timeLabel(ts, 60_000L) }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(layoutModifier)
            .chartGestures(state)
            .semantics {
                contentDescription = a11yText
                customActions = listOf(
                    CustomAccessibilityAction("上一根") { state.selectPreviousCandle() },
                    CustomAccessibilityAction("下一根") { state.selectNextCandle() },
                    CustomAccessibilityAction("放大") { state.zoomIn() },
                    CustomAccessibilityAction("缩小") { state.zoomOut() },
                    CustomAccessibilityAction("删除选中画线") { state.removeSelectedOverlay() },
                )
            },
    ) {
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
                ),
                textMeasurer = textMeasurer,
            )
        }
    }
}
