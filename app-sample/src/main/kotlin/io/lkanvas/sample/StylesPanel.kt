package io.lkanvas.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lkanvas.chart.LKanvasChart
import io.lkanvas.chart.styles.Styles
import io.lkanvas.chart.styles.CrosshairStylePatch
import io.lkanvas.chart.styles.GridStylePatch
import io.lkanvas.chart.styles.SeparatorStylePatch
import io.lkanvas.chart.styles.StylesPatch

/**
 * Styles 设置面板完整版（批次 5 余量样例）：对齐上游 demo 的样式设置交互——
 * 局部项经门面 `setStyles(StylesPatch)`（未提供字段保持不变，R04）；
 * 全量场景经 `setStyles(Styles)` 主题替换。
 * grid 显隐 / tooltip 规则（always→follow_cross→none）/ crosshair 显隐 / overlay 点色。
 */
@Composable
fun StylesPanel(facade: LKanvasChart, onClose: () -> Unit) {
    var gridOn by remember { androidx.compose.runtime.mutableStateOf(facade.getStyles().grid.show) }
    var crosshairOn by remember { androidx.compose.runtime.mutableStateOf(facade.getStyles().crosshair.show) }
    var tooltipRule by remember { androidx.compose.runtime.mutableStateOf(facade.getStyles().candle.tooltip.showRule) }
    var tooltipType by remember { androidx.compose.runtime.mutableStateOf(facade.getStyles().candle.tooltip.showType) }
    var featureOn by remember { androidx.compose.runtime.mutableStateOf(facade.getStyles().indicator.tooltip.features.isNotEmpty()) }
    var overlayPointBig by remember { androidx.compose.runtime.mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xCC101020))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text("样式设置", color = Color(0xFFD0D0E0), fontSize = 12.sp)
            Spacer0()
            Text(
                "关闭",
                color = Color(0xFF9295A5),
                fontSize = 12.sp,
                modifier = Modifier.clickable { onClose() },
            )
        }

        // grid 显隐
        SettingRow("网格", if (gridOn) "显示" else "隐藏") {
            gridOn = !gridOn
            facade.setStyles(StylesPatch(grid = GridStylePatch(show = gridOn)))
        }

        // tooltip 规则循环（0.3.0：库枚举；always→follow_cross→none，渲染端已消费）
        val nextRule = when (io.lkanvas.chart.TooltipShowRule.entries.firstOrNull { it.value == tooltipRule }) {
            io.lkanvas.chart.TooltipShowRule.ALWAYS -> io.lkanvas.chart.TooltipShowRule.FOLLOW_CROSS.value
            io.lkanvas.chart.TooltipShowRule.FOLLOW_CROSS -> io.lkanvas.chart.TooltipShowRule.NONE.value
            else -> io.lkanvas.chart.TooltipShowRule.ALWAYS.value
        }
        SettingRow("Tooltip 规则", tooltipRule) {
            tooltipRule = nextRule
            val cur = facade.getStyles()
            facade.setStyles(cur.copy(candle = cur.candle.copy(tooltip = cur.candle.tooltip.copy(showRule = nextRule))))
        }

        // tooltip 形态切换（0.3.0：standard 行式 ↔ rect 卡片，渲染端已消费）
        SettingRow("Tooltip 形态", tooltipType) {
            val next = if (tooltipType == "standard") "rect" else "standard"
            tooltipType = next
            val cur = facade.getStyles()
            facade.setStyles(cur.copy(candle = cur.candle.copy(tooltip = cur.candle.tooltip.copy(showType = next))))
        }

        // 功能图标演示（0.3.0：indicator.tooltip.features；点击图标经门面发三 ActionType）
        SettingRow("功能图标", if (featureOn) "显示" else "隐藏") {
            featureOn = !featureOn
            val cur = facade.getStyles()
            val features = if (featureOn) listOf(
                io.lkanvas.chart.styles.TooltipFeatureStyle(
                    id = "demo_flag",
                    type = "path",
                    content = listOf(listOf(0.2, 0.8), listOf(0.5, 0.2), listOf(0.8, 0.8)),
                ),
            ) else emptyList()
            facade.setStyles(cur.copy(indicator = cur.indicator.copy(tooltip = cur.indicator.tooltip.copy(features = features))))
        }

        // crosshair 显隐
        SettingRow("十字线", if (crosshairOn) "显示" else "隐藏") {
            crosshairOn = !crosshairOn
            facade.setStyles(StylesPatch(crosshair = CrosshairStylePatch(show = crosshairOn)))
        }

        // overlay 控制点大小
        SettingRow("画线控制点", if (overlayPointBig) "大" else "小") {
            overlayPointBig = !overlayPointBig
            facade.setStyles(
                facade.getStyles().copy(
                    overlay = facade.getStyles().overlay.copy(
                        point = facade.getStyles().overlay.point.copy(
                            radius = if (overlayPointBig) 8.0 else 4.0,
                            activeRadius = if (overlayPointBig) 8.0 else 4.0,
                        ),
                    ),
                ),
            )
        }

        // 分隔线颜色演示：#76808F ↔ #33353D 循环
        SettingRow("分隔线色", "#76808F↔#33353D") {
            val cur = facade.getStyles().separator.color
            val next = if (cur == "#76808F") "#33353D" else "#76808F"
            facade.setStyles(StylesPatch(separator = io.lkanvas.chart.styles.SeparatorStylePatch(color = next)))
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String, onTap: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = Color(0xFFD0D0E0), fontSize = 12.sp)
        Spacer0()
        Text(
            value,
            color = Color(0xFF2DC08E),
            fontSize = 12.sp,
            modifier = Modifier.clickable { onTap() },
        )
    }
}

@Composable
private fun RowScope.Spacer0() {
    Spacer(Modifier.weight(1f))
}
