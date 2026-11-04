package io.lkanvas.sample

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.lkanvas.chart.styles.*

@Composable
fun SampleTheme(dark: Boolean, content: @Composable () -> Unit) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF79A8FF), onPrimary = Color(0xFF081426),
        primaryContainer = Color(0xFF233A60), onPrimaryContainer = Color(0xFFD8E6FF),
        secondary = Color(0xFF79A8FF), onSecondary = Color(0xFF081426),
        secondaryContainer = Color(0xFF233A60), onSecondaryContainer = Color(0xFFD8E6FF),
        background = Color(0xFF0B1018), onBackground = Color(0xFFF1F5FC),
        surface = Color(0xFF101823), onSurface = Color(0xFFF1F5FC),
        surfaceVariant = Color(0xFF182331), onSurfaceVariant = Color(0xFFA7B5C9),
        surfaceContainer = Color(0xFF182331), surfaceContainerLow = Color(0xFF101823),
        surfaceContainerHigh = Color(0xFF182331), surfaceContainerHighest = Color(0xFF293648),
        outline = Color(0xFFA7B5C9),
        outlineVariant = Color(0xFF293648), error = Color(0xFFFF738A),
    ) else lightColorScheme(
        primary = Color(0xFF255BC8), onPrimary = Color.White,
        primaryContainer = Color(0xFFDCE8FF), onPrimaryContainer = Color(0xFF163F8B),
        secondary = Color(0xFF255BC8), onSecondary = Color.White,
        secondaryContainer = Color(0xFFDCE8FF), onSecondaryContainer = Color(0xFF163F8B),
        background = Color(0xFFF4F6FA), onBackground = Color(0xFF152238),
        surface = Color.White, onSurface = Color(0xFF152238),
        surfaceVariant = Color(0xFFEAF0F8), onSurfaceVariant = Color(0xFF52627A),
        surfaceContainer = Color(0xFFEAF0F8), surfaceContainerLow = Color.White,
        surfaceContainerHigh = Color(0xFFEAF0F8), surfaceContainerHighest = Color(0xFFD8E0EB),
        outline = Color(0xFF52627A),
        outlineVariant = Color(0xFFD8E0EB), error = Color(0xFFC73551),
    )
    MaterialTheme(colorScheme = colors, content = content)
}

/** Sample 色板通过公开 Styles 应用，保留用户已调整的显隐、参数和图型。 */
fun sampleStyles(dark: Boolean, base: Styles): Styles {
    val surface = if (dark) "#101823" else "#FFFFFF"
    val grid = if (dark) "#1D2938" else "#E9EEF5"
    val text = if (dark) "#A7B5C9" else "#52627A"
    val primary = if (dark) "#79A8FF" else "#255BC8"
    val up = if (dark) "#35C99A" else "#087849"
    val down = if (dark) "#FF738A" else "#C73551"
    val palette = if (dark) listOf("#F2BD63", "#B99AFF", primary, "#E79CD2")
        else listOf("#946200", "#7953BD", primary, "#B23D86")
    val bar = CandleBarColor(
        upColor = up, downColor = down, noChangeColor = text,
        upBorderColor = up, downBorderColor = down, noChangeBorderColor = text,
        upWickColor = up, downWickColor = down, noChangeWickColor = text,
    )
    return base.copy(
        grid = base.grid.copy(horizontal = base.grid.horizontal.copy(color = grid), vertical = base.grid.vertical.copy(color = grid)),
        candle = base.candle.copy(
            bar = bar,
            area = base.candle.area.copy(lineColor = primary),
            priceMark = base.candle.priceMark.copy(
                high = base.candle.priceMark.high.copy(color = text),
                low = base.candle.priceMark.low.copy(color = text),
                last = base.candle.priceMark.last.copy(upColor = up, downColor = down, noChangeColor = text),
            ),
            tooltip = base.candle.tooltip.copy(
                rect = base.candle.tooltip.rect.copy(color = surface, borderColor = grid),
                title = base.candle.tooltip.title.copy(color = text),
                legend = base.candle.tooltip.legend.copy(color = text),
            ),
        ),
        indicator = base.indicator.copy(
            lines = palette.map { SmoothLineStyle(color = it) },
            bars = listOf(PolygonStyle(upColor = up, downColor = down, noChangeColor = text)),
            tooltip = base.indicator.tooltip.copy(
                title = base.indicator.tooltip.title.copy(color = text),
                legend = base.indicator.tooltip.legend.copy(color = text),
            ),
        ),
        xAxis = base.xAxis.copy(axisLine = base.xAxis.axisLine.copy(color = grid), tickLine = base.xAxis.tickLine.copy(color = grid), tickText = base.xAxis.tickText.copy(color = text)),
        yAxis = base.yAxis.copy(axisLine = base.yAxis.axisLine.copy(color = grid), tickLine = base.yAxis.tickLine.copy(color = grid), tickText = base.yAxis.tickText.copy(color = text)),
        separator = base.separator.copy(color = if (base.separator.color == base.xAxis.tickText.color) text else grid),
        crosshair = base.crosshair.copy(
            horizontal = base.crosshair.horizontal.copy(line = base.crosshair.horizontal.line.copy(color = text)),
            vertical = base.crosshair.vertical.copy(line = base.crosshair.vertical.line.copy(color = text)),
        ),
        overlay = base.overlay.copy(
            line = base.overlay.line.copy(color = primary),
            point = base.overlay.point.copy(color = primary, activeColor = primary),
            text = base.overlay.text.copy(color = primary),
        ),
    )
}
