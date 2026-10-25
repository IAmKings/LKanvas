package io.lkanvas.compose.theme

import androidx.compose.ui.graphics.Color

/**
 * 图表主题（类型化配置，PRD R-07：不强制宿主依赖某套 Material 主题）。
 * 默认色取自上游内置默认（up #26A69A / down #EF5350 等）。
 */
data class ChartTheme(
    val background: Color = Color.White,
    val gridLine: Color = Color(0xFFF0F0F0),
    val axisText: Color = Color(0xFF9C9C9C),
    val axisLine: Color = Color(0xFFE8E8E8),
    val crosshairLine: Color = Color(0xFF9C9C9C),
    val lastPriceLine: Color = Color(0xFFC6C6C6),
    // 主图 candle
    val upColor: Color = Color(0xFF26A69A),
    val downColor: Color = Color(0xFFEF5350),
    val noChangeColor: Color = Color(0xFF888888),
    // 指标线调色板（MA/VOL 均量线等按序取用）
    val linePalette: List<Color> = listOf(
        Color(0xFFF5A623),
        Color(0xFF9D65C9),
        Color(0xFF2196F3),
        Color(0xFFE91E63),
        Color(0xFF00BCD4),
        Color(0xFF8BC34A),
    ),
    // MACD
    val macdDif: Color = Color(0xFFF5A623),
    val macdDea: Color = Color(0xFF2196F3),
    val macdBarUp: Color = Color(0xFF26A69A),
    val macdBarDown: Color = Color(0xFFEF5350),
    // 图例
    val legendText: Color = Color(0xFF7C7C7C),
    val legendValueUp: Color = Color(0xFF26A69A),
    val legendValueDown: Color = Color(0xFFEF5350),
    val legendValueNeutral: Color = Color(0xFF545454),
    // 覆盖物
    val overlayLine: Color = Color(0xFF2196F3),
    val overlayPoint: Color = Color(0xFFE91E63),
    val overlayText: Color = Color(0xFF2196F3),
) {
    companion object {
        fun dark(): ChartTheme = ChartTheme(
            background = Color(0xFF16162A),
            gridLine = Color(0xFF26263C),
            axisText = Color(0xFF8C8F99),
            axisLine = Color(0xFF2E2E44),
            crosshairLine = Color(0xFF8C8F99),
            lastPriceLine = Color(0xFF3C3C55),
            legendText = Color(0xFF8C8F99),
            legendValueNeutral = Color(0xFFC0C0D0),
            noChangeColor = Color(0xFF777788),
        )
    }
}

/** 尺寸与布局配置（dp，绘制前转换为 px）。 */
data class ChartLayoutConfig(
    val subPaneHeight: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(92f),
    val timeAxisHeight: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(22f),
    val rightAxisGutter: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(48f),
    val legendTextSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp),
    val axisTextSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp),
    val candleGapRatio: Float = 0.25f,
)
