package io.lkanvas.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import io.lkanvas.chart.LKanvasChart
import io.lkanvas.chart.styles.*

@Composable
fun StylesPanel(facade: LKanvasChart) {
    val styles = facade.getStyles()
    Column {
        PanelCaption("通过图表公开 Styles 接口调整，设置即时作用于图表。")
        SettingSwitch("网格", "显示横向与纵向参考线", styles.grid.show) {
            facade.setStyles(StylesPatch(grid = GridStylePatch(show = it)))
        }
        SettingSwitch("十字线", "长按查看对应 K 线", styles.crosshair.show) {
            facade.setStyles(StylesPatch(crosshair = CrosshairStylePatch(show = it)))
        }
        SettingSwitch("图例功能图标", "演示 Tooltip feature 点击事件（写入 logcat）", styles.indicator.tooltip.features.isNotEmpty()) {
            val features = if (it) listOf(TooltipFeatureStyle(id = "demo_flag", type = "path", content = listOf(listOf(.2, .8), listOf(.5, .2), listOf(.8, .8)))) else emptyList()
            facade.setStyles(styles.copy(indicator = styles.indicator.copy(tooltip = styles.indicator.tooltip.copy(features = features))))
        }
        SettingSwitch("加大画线控制点", "将普通与选中控制点半径设为 8", styles.overlay.point.radius > 4) {
            facade.setStyles(styles.copy(overlay = styles.overlay.copy(point = styles.overlay.point.copy(radius = if (it) 8.0 else 4.0, activeRadius = if (it) 8.0 else 4.0))))
        }
        SettingSwitch("高对比度分隔线", "方便检查 pane 边界与拖动区域", styles.separator.color == styles.xAxis.tickText.color) {
            facade.setStyles(StylesPatch(separator = SeparatorStylePatch(color = if (it) styles.xAxis.tickText.color else styles.grid.horizontal.color)))
        }
        PanelHeading("行情 Tooltip 显示时机")
        listOf("跟随十字线" to "follow_cross", "始终显示" to "always", "隐藏" to "none").forEach { (title, rule) ->
            ChoiceRow(title, rule, styles.candle.tooltip.showRule == rule) {
                facade.setStyles(styles.copy(candle = styles.candle.copy(tooltip = styles.candle.tooltip.copy(showRule = rule))))
            }
        }
        PanelHeading("行情 Tooltip 形态")
        listOf("行式图例" to "standard", "浮动卡片" to "rect").forEach { (title, type) ->
            ChoiceRow(title, type, styles.candle.tooltip.showType == type) {
                facade.setStyles(styles.copy(candle = styles.candle.copy(tooltip = styles.candle.tooltip.copy(showType = type))))
            }
        }
    }
}
