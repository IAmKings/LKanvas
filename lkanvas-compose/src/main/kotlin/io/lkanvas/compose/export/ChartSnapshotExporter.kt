package io.lkanvas.compose.export

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.lkanvas.compose.draw.ChartRenderer
import io.lkanvas.compose.state.ChartState
import io.lkanvas.compose.theme.ChartLayoutConfig
import io.lkanvas.compose.theme.ChartTheme
import java.io.OutputStream

/**
 * 图表快照导出（PRD R-15/T-29，A-43）。
 *
 * - 与屏幕共用 [ChartRenderer] 绘制路径；在主线程同步调用时快照单一
 *   （state 的更新与导出同线程，同步块内不交错，无跨 revision 拼接）。
 * - 尺寸上限 4096×4096（默认）；超限返回 [Result.TooLarge]，不降级不 OOM。
 * - 空数据输出定义明确：仅背景色的位图（不失败）。
 * - 可选择包含十字线/交互层（[includeCrosshair]）。
 * - 由宿主决定存储/分享（本类只写 [OutputStream]，不做磁盘/网络 IO）。
 */
object ChartSnapshotExporter {

    const val MAX_DIMENSION: Int = 4096

    sealed interface Result {
        data class Ok(val bitmap: ImageBitmap, val width: Int, val height: Int) : Result
        data class TooLarge(val width: Int, val height: Int) : Result
    }

    /**
     * 导出当前图表为位图。必须在主线程调用（单 revision 保证）。
     *
     * 内容为屏幕当前布局的**等比缩放**（三 pane 完整呈现，文本随之缩放）；
     * 尚未布局（contentWidth=0）时按目标尺寸直接绘制（仅背景）。
     *
     * @param widthPx/heightPx 输出尺寸（物理 px）
     * @param textMeasurer 由调用方在组合上下文创建（rememberTextMeasurer()）
     * @param theme 须与屏幕显示主题一致（由宿主传入）
     */
    fun export(
        state: ChartState,
        textMeasurer: androidx.compose.ui.text.TextMeasurer,
        theme: ChartTheme = ChartTheme(),
        config: ChartLayoutConfig = ChartLayoutConfig(),
        widthPx: Int,
        heightPx: Int,
        includeCrosshair: Boolean = false,
        styles: io.lkanvas.chart.styles.Styles? = null,
    ): Result {
        if (widthPx <= 0 || heightPx <= 0) {
            throw IllegalArgumentException("导出尺寸必须为正：${widthPx}x$heightPx")
        }
        if (widthPx > MAX_DIMENSION || heightPx > MAX_DIMENSION) {
            return Result.TooLarge(widthPx, heightPx)
        }
        val density = Density(1f)
        val bitmap = ImageBitmap(widthPx, heightPx).apply { prepareToDraw() }
        val canvas = Canvas(bitmap)
        val drawScope = CanvasDrawScope()
        val gutterPx = with(density) { config.rightAxisGutter.toPx() }
        val legendPx = with(density) { config.legendTextSize.toPx() }
        val axisPx = with(density) { config.axisTextSize.toPx() }

        // 屏幕布局尺寸（已布局时等比缩放整个三 pane 布局）
        val panes = state.paneHeightsPx
        val layoutW = if (state.contentWidthPx > 0f) state.contentWidthPx + gutterPx else widthPx.toFloat()
        val layoutH = if (state.contentWidthPx > 0f) {
            (state.extraPaneLayout.lastOrNull()?.second?.second ?: (panes.main + panes.vol + panes.macd)) + panes.timeAxis
        } else {
            heightPx.toFloat()
        }
        val scale = minOf(widthPx / layoutW, heightPx / layoutH)

        // 铺满背景（等比缩放可能在宽或高方向留白，避免透明底）
        drawScope.draw(density, LayoutDirection.Ltr, canvas, Size(widthPx.toFloat(), heightPx.toFloat())) {
            drawRect(theme.background, size = Size(widthPx.toFloat(), heightPx.toFloat()))
        }
        canvas.save()
        canvas.scale(scale, scale)
        val screenLegendHeights = state.legendHeightsPx
        val screenFeatureHits = state.tooltipFeatureHits
        try {
            drawScope.draw(density, LayoutDirection.Ltr, canvas, Size(layoutW, layoutH)) {
                with(ChartRenderer) {
                    render(
                        state = state,
                        theme = theme,
                        params = ChartRenderer.Params(
                            width = layoutW,
                            height = layoutH,
                            gutterWidth = gutterPx,
                            legendTextSizePx = legendPx,
                            axisTextSizePx = axisPx,
                            includeCrosshair = includeCrosshair,
                            styles = styles,
                        ),
                        textMeasurer = textMeasurer,
                    )
                }
            }
        } finally {
            state.legendHeightsPx = screenLegendHeights
            state.tooltipFeatureHits = screenFeatureHits
            canvas.restore()
        }
        return Result.Ok(bitmap, widthPx, heightPx)
    }

    /** 导出并编码为 PNG（宿主提供输出流；编码失败返回 false 不抛出）。 */
    fun exportPng(
        state: ChartState,
        textMeasurer: androidx.compose.ui.text.TextMeasurer,
        output: OutputStream,
        theme: ChartTheme = ChartTheme(),
        config: ChartLayoutConfig = ChartLayoutConfig(),
        widthPx: Int,
        heightPx: Int,
        includeCrosshair: Boolean = false,
        styles: io.lkanvas.chart.styles.Styles? = null,
    ): Boolean = when (val r = export(state, textMeasurer, theme, config, widthPx, heightPx, includeCrosshair, styles)) {
        is Result.TooLarge -> false
        is Result.Ok -> runCatching {
            r.bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        }.getOrDefault(false)
    }
}
