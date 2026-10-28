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
import io.lkanvas.chart.styles.CrosshairStyle
import io.lkanvas.chart.styles.GridStyle
import io.lkanvas.chart.styles.LineStyle
import io.lkanvas.chart.styles.Styles

/**
 * Styles 设置面板完整版（批次 5 余量样例）：对齐上游 demo 的样式设置交互——
 * 全部经门面 `setStyles(Styles patch)` 深合并，逐项演示关键样式节点。
 * grid 显隐 / tooltip 规则（always→follow_cross→none）/ crosshair 显隐 / overlay 点色。
 */
@Composable
fun StylesPanel(facade: LKanvasChart, onClose: () -> Unit) {
    var gridOn by remember { androidx.compose.runtime.mutableStateOf(facade.getStyles().grid.show) }
    var crosshairOn by remember { androidx.compose.runtime.mutableStateOf(facade.getStyles().crosshair.show) }
    var tooltipRule by remember { androidx.compose.runtime.mutableStateOf(facade.getStyles().candle.tooltip.showRule) }
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
            facade.setStyles(Styles(grid = GridStyle(show = gridOn)))
        }

        // tooltip 规则循环
        val nextRule = when (tooltipRule) {
            TooltipShowRule.ALWAYS.name -> TooltipShowRule.FOLLOW_CROSS.name
            TooltipShowRule.FOLLOW_CROSS.name -> TooltipShowRule.NONE.name
            else -> TooltipShowRule.ALWAYS.name
        }
        SettingRow("Tooltip 规则", tooltipRule.lowercase()) {
            tooltipRule = nextRule
            val cur = facade.getStyles()
            facade.setStyles(
                cur.copy(candle = cur.candle.copy(tooltip = cur.candle.tooltip.copy(showRule = nextRule))),
            )
        }

        // crosshair 显隐
        SettingRow("十字线", if (crosshairOn) "显示" else "隐藏") {
            crosshairOn = !crosshairOn
            facade.setStyles(Styles(crosshair = CrosshairStyle(show = crosshairOn)))
        }

        // overlay 控制点大小
        SettingRow("画线控制点", if (overlayPointBig) "大" else "小") {
            overlayPointBig = !overlayPointBig
            facade.setStyles(
                Styles(
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
            facade.setStyles(Styles(separator = io.lkanvas.chart.styles.SeparatorStyle(color = next)))
        }
    }
}

private enum class TooltipShowRule { ALWAYS, FOLLOW_CROSS, NONE }

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
